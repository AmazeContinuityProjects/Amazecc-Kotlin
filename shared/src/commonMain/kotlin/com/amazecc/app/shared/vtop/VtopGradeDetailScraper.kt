package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.SemesterGradeResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Headless per-semester grade-detail sweep for LOCAL mode.
 *
 * This is the Kotlin half of the port of `amazecc-api-hono/src/lib/grades/fetchAllGradesHistory.ts`.
 * The HTML parsing lives in [VtopScripts] (the WebView is the only HTML parser available in
 * `commonMain`); everything here is either a pure JSON->model seam or a thin coroutine fan-out
 * around a caller-supplied `evaluate` function. No network, session or WebView is touched by the
 * parsers, so the JVM tests can drive them with the reference JSON verbatim.
 *
 * Fail-closed by construction: a semester with no grade rows, a row without a real course code
 * or grade letter, and a course without a `courseId` are all omitted rather than padded with
 * invented values. Credits on an embedded course come only from its aggregate row — the hidden
 * ETL/ETH/ELA halves never have a grade letter, so they cannot double count.
 */
object VtopGradeDetailScraper {

    /** One real row of `doStudentGradeView`, in the page's own cell order. */
    data class SemesterGradeRow(
        val slNo: String,
        val courseCode: String,
        val courseTitle: String,
        val courseType: String,
        val l: String,
        val p: String,
        val j: String,
        val c: String,
        val gradingType: String,
        val grandTotal: String,
        val grade: String,
        val courseId: String?
    )

    /** One semester's published GPA plus its real grade rows. */
    data class SemesterGradeView(
        val gpa: String?,
        val grades: List<SemesterGradeRow>
    )

    /** The extra richness one `getGradeViewDetails` response adds to a course. */
    data class GradeDetail(
        val range: GradeRange?,
        val details: List<GradeBreakdown>?
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val gradeLetter = Regex("^[SABCDEF]$", RegexOption.IGNORE_CASE)
    private const val DETAIL_CONCURRENCY = 3

    /**
     * Every semester a student could have attended, in the reference's order (01, 07, 05 per
     * year). The first two digits of `authorizedID` are the admission year (e.g. `25BLC1081`);
     * when absent, 2024 is used — the same fallback `fetchAllGradesHistory.ts:26-29` applies.
     */
    fun semesterIdsFor(authorizedID: String, currentYear: Int): List<String> {
        val startYear = authorizedID.take(2).toIntOrNull()?.let { 2000 + it }
            ?.takeIf { it in 2000..currentYear } ?: 2024
        val result = mutableListOf<String>()
        for (year in startYear..currentYear) {
            val next = (year + 1).toString().takeLast(2)
            result += "CH$year${next}01"
            result += "CH$year${next}07"
            result += "CH$year${next}05"
        }
        return result
    }

    /** Pure parser for the JSON emitted by [VtopScripts.fetchSemesterGradeView]. */
    fun parseSemesterGradeView(raw: String): SemesterGradeView? {
        val root = raw.asJsonObject() ?: return null
        if (!root.isOk()) return null
        val rows = (root["grades"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val code = row.string("courseCode")?.trim().orEmpty()
            val grade = row.string("grade")?.trim().orEmpty()
            if (code.isEmpty() || !gradeLetter.matches(grade)) return@mapNotNull null
            SemesterGradeRow(
                slNo = row.string("slNo").orEmpty(),
                courseCode = code,
                courseTitle = row.string("courseTitle").orEmpty(),
                courseType = row.string("courseType").orEmpty(),
                l = row.string("l").orEmpty(),
                p = row.string("p").orEmpty(),
                j = row.string("j").orEmpty(),
                c = row.string("c").orEmpty(),
                gradingType = row.string("gradingType").orEmpty(),
                grandTotal = row.string("grandTotal").orEmpty(),
                grade = grade,
                courseId = row.string("courseId")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        return SemesterGradeView(
            gpa = root.string("gpa")?.takeIf { it.isNotBlank() },
            grades = rows
        )
    }

    /** Pure parser for the JSON emitted by [VtopScripts.fetchGradeViewDetails]. */
    fun parseGradeDetail(raw: String): GradeDetail? {
        val root = raw.asJsonObject() ?: return null
        if (!root.isOk()) return null

        val range = (root["range"] as? JsonObject)?.let { r ->
            GradeRange(
                S = r.string("S").orEmpty(),
                A = r.string("A").orEmpty(),
                B = r.string("B").orEmpty(),
                C = r.string("C").orEmpty(),
                D = r.string("D").orEmpty(),
                E = r.string("E").orEmpty(),
                F = r.string("F").orEmpty(),
                N = r.string("N").orEmpty()
            )
        }

        val details = (root["details"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val component = row.string("component")?.trim().orEmpty()
            val maxMark = row.string("maxMark")?.trim().orEmpty()
            // A header / grand-total row has no component or max mark; drop it rather than
            // emitting an empty assessment the UI would have to guess about.
            if (component.isEmpty() || maxMark.isEmpty()) return@mapNotNull null
            GradeBreakdown(
                slNo = row.string("slNo").orEmpty(),
                component = component,
                maxMark = maxMark,
                weightagePercent = row.string("weightagePercent").orEmpty(),
                status = row.string("status").orEmpty(),
                scoredMark = row.string("scoredMark").orEmpty(),
                weightageMark = row.string("weightageMark").orEmpty(),
                type = row.string("type")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        return GradeDetail(range = range, details = details.takeIf { it.isNotEmpty() })
    }

    /**
     * Sweeps every semester, then every course detail, with a bounded fan-out of
     * [DETAIL_CONCURRENCY]. Both phases run in chunked batches, so at most three WebView scripts
     * are in flight at once and the two phases never overlap.
     *
     * @param evaluate the WebView seam supplied by `VtopDataSource`; `evaluateJavascript` is
     *   posted to the WebView's main thread, so concurrent calls are serialized there and each
     *   carries its own result callback.
     * @return only semesters that published at least one real grade row.
     */
    suspend fun scrape(
        authorizedID: String,
        csrf: String,
        semesterViewPath: String,
        detailPath: String,
        currentYear: Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
        evaluate: suspend (String) -> String
    ): Map<String, SemesterGradeResult> = coroutineScope {
        if (authorizedID.isBlank() || csrf.isBlank()) return@coroutineScope emptyMap()

        val semesterIds = semesterIdsFor(authorizedID, currentYear)

        val views = mutableListOf<SemesterGradeView?>()
        for (batch in semesterIds.chunked(DETAIL_CONCURRENCY)) {
            val deferred = batch.map { semesterId ->
                async {
                    runCatching {
                        parseSemesterGradeView(
                            evaluate(
                                VtopScripts.fetchSemesterGradeView(
                                    semesterViewPath,
                                    semesterBody(authorizedID, csrf, semesterId)
                                )
                            )
                        )
                    }.getOrNull()
                }
            }
            views.addAll(deferred.awaitAll())
        }

        // Detail jobs carry their own courseId and are keyed by (semester index, grade index) so
        // results can be reattached without relying on course codes, which repeat across a degree.
        data class DetailJob(val semesterIndex: Int, val gradeIndex: Int, val courseId: String)
        val jobs = mutableListOf<DetailJob>()
        views.forEachIndexed { semesterIndex, view ->
            view?.grades?.forEachIndexed { gradeIndex, row ->
                val courseId = row.courseId
                if (!courseId.isNullOrBlank()) {
                    jobs += DetailJob(semesterIndex, gradeIndex, courseId)
                }
            }
        }
        val details = mutableListOf<GradeDetail?>()
        for (batch in jobs.chunked(DETAIL_CONCURRENCY)) {
            val deferred = batch.map { job ->
                async {
                    runCatching {
                        parseGradeDetail(
                            evaluate(
                                VtopScripts.fetchGradeViewDetails(
                                    detailPath,
                                    detailBody(
                                        authorizedID,
                                        csrf,
                                        semesterIds[job.semesterIndex],
                                        job.courseId
                                    )
                                )
                            )
                        )
                    }.getOrNull()
                }
            }
            details.addAll(deferred.awaitAll())
        }
        val detailByGrade = jobs.mapIndexed { index, job ->
            (job.semesterIndex to job.gradeIndex) to details[index]
        }.toMap()

        val result = linkedMapOf<String, SemesterGradeResult>()
        views.forEachIndexed { semesterIndex, view ->
            if (view == null || view.grades.isEmpty()) return@forEachIndexed
            val items = view.grades.mapIndexed { gradeIndex, row ->
                val detail = detailByGrade[semesterIndex to gradeIndex]
                GradeItem(
                    courseCode = row.courseCode,
                    courseTitle = row.courseTitle,
                    courseType = row.courseType,
                    credits = row.c.takeIf { it.isNotBlank() },
                    grandTotal = row.grandTotal,
                    grade = row.grade,
                    courseId = row.courseId,
                    l = row.l.takeIf { it.isNotBlank() },
                    p = row.p.takeIf { it.isNotBlank() },
                    j = row.j.takeIf { it.isNotBlank() },
                    details = detail?.details,
                    range = detail?.range
                )
            }
            result[semesterIds[semesterIndex]] = SemesterGradeResult(
                gpa = view.gpa,
                grades = items
            )
        }
        result
    }

    private fun semesterBody(authorizedID: String, csrf: String, semesterId: String): String =
        "authorizedID=$authorizedID&semesterSubId=$semesterId&_csrf=$csrf&x=${nowMs()}"

    private fun detailBody(
        authorizedID: String,
        csrf: String,
        semesterId: String,
        courseId: String
    ): String =
        "authorizedID=$authorizedID&x=${nowMs()}&semesterSubId=$semesterId&courseId=$courseId&_csrf=$csrf"

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun String.asJsonObject(): JsonObject? =
        (try {
            json.parseToJsonElement(ifBlank { "{}" })
        } catch (_: Exception) {
            null
        }) as? JsonObject

    private fun JsonObject.isOk(): Boolean = when (val value = this["ok"]) {
        is JsonPrimitive -> value.content.equals("true", ignoreCase = true)
        else -> false
    }

    /** Reads a string field, treating explicit JSON null as absent. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
