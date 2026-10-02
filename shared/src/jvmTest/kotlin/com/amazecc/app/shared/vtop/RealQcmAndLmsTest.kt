package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.state.AppSanitizers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.amazecc.app.shared.vtop.Vtop
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * QCM and LMS against **real** responses.
 *
 * QCM: `getStudentLoginForQcm` was found empirically, not from the docs. Stage 1
 * (`QCMStudentLogin`) is a JS-required form with a `semesterSubId` select and zero tables, so
 * AmazeCC-API's `parseVtopHtml` can never return a row from it. Stage 2 exists and works, but
 * needs `semSubId` (not `semesterSubId`) plus `paramReturnId=getStudentLoginForQcm`; omitting
 * `semSubId` yields "This menu is not available at present" rather than an error.
 *
 * LMS: shape taken from a live Moodle login
 * (`AmazeCC-API/scripts/lms-dump.mjs`), which is also where the old DTO mismatch was found.
 */
class RealQcmAndLmsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fixtures/$name.json")) {
            "Missing fixture /fixtures/$name.json - run gen-fixtures.mjs / lms-dump.mjs"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    // ── QCM ─────────────────────────────────────────────────────────────────

    /** The stage-2 table shape, built from the real 11-column header. */
    private fun qcmRow(
        course: String = "BAECE203",
        qcmNo: String = "QCM-1",
        action: String = "Open",
    ): JsonObject = buildJsonObject {
        put("semesterCode", "FALLSEM2026-27")
        put("courseCode", course)
        put("courseTitle", "Analog Electronics")
        put("courseType", "Theory Only")
        put("classNbr", "CH2026270100123")
        put("faculty", "50863 KARTHIK R CPS")
        put("qcmNo", qcmNo)
        put("action", action)
        put("suggestions", "Add more solved examples")
        put("facultyReply", "Will do in the next cycle")
        put("hodComments", "Noted")
    }

    @Test
    fun qcmRowsSurviveTheSanitizerWithEveryColumn() {
        val payload = JsonArray(
            listOf(
                buildJsonObject {
                    put("caption", "CH20262701")
                    put("semester", "CH20262701")
                    put("semesterId", "CH20262701")
                    put("rows", JsonArray(listOf(qcmRow())))
                }
            )
        )
        val tables = AppSanitizers.decodeQcmTables(payload)
        assertEquals(1, tables.size)
        val row = tables.first().rows.first()
        assertEquals("QCM-1", row.qcmNo)
        // "Action" is the literal column header; the server called it actionTaken.
        assertEquals("Open", row.action)
        assertEquals("BAECE203", row.courseCode)
        assertEquals("Analog Electronics", row.courseTitle)
        assertEquals("Theory Only", row.courseType)
        assertEquals("CH2026270100123", row.classNbr)
        assertEquals("Noted", row.hodComments)
    }

    @Test
    fun qcmIsTwoStagesAndStageOneHasNoTables() {
        // Stage 1 is only a form. Asserting this keeps a future refactor from "simplifying" the
        // flow back to a single request, which cannot ever produce rows.
        val stage1 = VtopPage.parse(fixture("page-qcm"))
        assertTrue(stage1.ok)
        assertTrue(stage1.tables.isEmpty(), "stage 1 must not contain the QCM table")
        assertEquals(
            // The real select: 7 options, the first an empty placeholder.
            listOf(
                "CH20262701", "CH20252605", "CH20252601",
                "CH20242505", "CH20242501", "CH20222323",
            ),
            stage1.selectOptions["semesterSubId"]?.map { it.value }?.filter { it.isNotBlank() },
            "the semester select is the only payload stage 1 offers",
        )
        assertEquals(
            "Fall Semester 2026-27 - CHN",
            stage1.selectOptions["semesterSubId"]!!.first { it.value == "CH20262701" }.text,
            "option text is readable, so it can caption each table",
        )
        val qcmOptions = stage1.selectOptions["semesterSubId"]!!
        assertEquals(
            "-- Select --",
            qcmOptions.first().text,
            "stage 1 opens on a placeholder, not a semester",
        )
        assertTrue(
            qcmOptions.none { it.value.isNotBlank() && it.selected },
            "no semester is preselected, so all of them have to be fetched - not just the first",
        )
    }

    @Test
    fun qcmScriptSendsTheParamsThatActuallyWork() {
        // The three details that make stage 2 return data rather than an error page.
        val js = VtopScripts.fetchQcmForSemester("/vtop/getStudentLoginForQcm", "a=1", "CH20262701")
        assertTrue(js.contains("semSubId"), "must send semSubId")
        assertTrue(js.contains("paramReturnId=getStudentLoginForQcm"), "must send paramReturnId")
        assertTrue(js.contains("toUTCString"), "x is a UTC date string here, not epoch millis")
        assertTrue(!js.contains("semesterSubId"), "semesterSubId is the stage-1 name and is wrong here")
    }

    @Test
    fun lmsCalendarReadsMonthFromTheDayLinkNotTheEventLink() {
        // Regression: the `a[data-action="view-event"]` carries no data-month/data-year, only
        // href/title/id. Reading them there gave empty strings, so every assignment arrived with a
        // null due date and no reminder ever fired - a silent failure, not a visible error.
        //
        // The real JS is exercised by scripts/vtop-conformance.mjs (it needs a DOM and the engine
        // is Android-only). What is asserted here is the markup the selector choice depends on.
        val html = fixtureHtml("lms-dashboard.html")

        val eventLink = Regex("""<a[^>]*data-action="view-event"[^>]*>""").find(html)!!.value
        assertTrue(
            !eventLink.contains("data-month") && !eventLink.contains("data-year"),
            "the event link has no date attributes, so this is why the old selector was wrong: $eventLink",
        )

        val dayLink = Regex("""<a[^>]*data-action="view-day-link"[^>]*>""").find(html)!!.value
        assertTrue(dayLink.contains("data-month") && dayLink.contains("data-year"))
        val month = Regex("""data-month="(\d+)"""").find(dayLink)!!.groupValues[1].toInt()
        val year = Regex("""data-year="(\d+)"""").find(dayLink)!!.groupValues[1].toInt()
        assertEquals(10, month)
        assertEquals(2026, year)

        // The cell itself carries the day number.
        val cell = Regex("""<td class="[^"]*hasevent[^"]*"[^>]*>""").find(html)!!.value
        assertTrue(cell.contains("data-day"), "the day number lives on the td: $cell")

        // Month paging needs a `time` epoch, so the calendar's own arrows are the only way in.
        assertTrue(html.contains("calendar/view.php?view=month&amp;time="), "paging links present")
        assertTrue(html.contains("arrow_link previous") && html.contains("arrow_link next"))
    }

    @Test
    fun lmsEventPageShapeMatchesTheCapturedAssignment() {
        val html = fixtureHtml("lms-event.html")
        // course code + name come from the breadcrumb anchor's text and title=...
        assertTrue(html.contains("breadcrumb-item"), "breadcrumb drives courseCode/courseTitle")
        assertTrue(html.contains("<h1 class=\"h2"), "h1.h2 is the assignment title")
        assertTrue(html.contains("activity-dates"), "the Due: label lives in div.activity-dates strong")
    }

    private fun fixtureHtml(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream("/vtop-snapshots/$name")) {
            "Missing /vtop-snapshots/$name - run lms-dump.mjs"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun qcmStage2TableParsesAllElevenRealColumns() {
        // Against the real stage-2 response. The header row is <td> (not <th>) and there is no
        // <thead>, which is why a th-only walk yields nothing at all.
        val page = VtopPage.parse(fixture("page-qcm-detail-ch20262701"))
        assertTrue(page.ok)
        val table = page.tables.single()
        assertEquals(
            listOf(
                "Sem Code", "Course Code", "Course Title", "Course Type", "Class Nbr", "Faculty",
                "QCM No.", "Action", "Suggestions", "Faculty Reply", "HOD Comments",
            ),
            table.headers,
        )
        assertEquals(3, table.rowCount(), "three QCM forms in Fall Semester 2026-27")
    }

    @Test
    fun qcmStage2RowsSurviveTheSanitizerWithRealValues() {
        // Runs the real captured table through the store sanitizer, keyed by header text exactly
        // as VtopScripts.fetchQcmForSemester emits it.
        val page = VtopPage.parse(fixture("page-qcm-detail-ch20262701"))
        val table = page.tables.single()
        val idx = { name: String -> table.headers.indexOfFirst { it.equals(name, ignoreCase = true) } }

        val rows = table.rows.map { r ->
            buildJsonObject {
                table.headers.forEachIndexed { i, h -> put(h, r.getOrElse(i) { "" }) }
            }
        }
        val stored = AppSanitizers.decodeQcmTables(
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("caption", "Fall Semester 2026-27 - CHN")
                        put("semesterId", "CH20262701")
                        put("rows", JsonArray(rows))
                    }
                )
            )
        )

        val first = stored.single().rows.first()
        assertEquals("BAECE203", first.courseCode)
        assertEquals("Analog Electronics", first.courseTitle)
        assertEquals("FALLSEM2026-27", first.semesterCode)
        assertEquals("CH2026270102001", first.classNbr)
        assertEquals("BINDU B", first.faculty)
        assertEquals("1", first.qcmNo)
        // The cell is <span>Faculty and HOD</span>, so the text has to be unwrapped.
        assertEquals("Faculty and HOD", first.action)
        assertTrue(
            first.suggestions!!.contains("teach at a slower pace"),
            "free-text suggestions must survive: ${first.suggestions}",
        )
        assertEquals(3, stored.single().rows.size)
    }

    @Test
    fun qcmCourseTypesAreThreeLetterCodesNotNames() {
        // The live rows carry ETH / ELA / LO. An earlier assumption that they read "Embedded
        // Theory" / "Theory Only" was wrong and would have matched nothing.
        val page = VtopPage.parse(fixture("page-qcm-detail-ch20252601"))
        val table = page.tables.single()
        val iType = table.headerIndex { it.equals("Course Type", ignoreCase = true) }
        assertTrue(iType >= 0)
        val types = table.rows.mapIndexed { i, _ -> table.cell(i, iType) }.filter { it.isNotBlank() }.toSet()
        assertEquals(setOf("ETH", "ELA"), types)
        assertTrue(types.none { it.contains(' ') }, "raw codes, not spelled-out names: $types")
    }

    @Test
    fun qcmSemesterWithNoFormsYieldsNothingUsable() {
        // Older semesters answer 200 with the header row and no data. `VtopPage` drops a table
        // with no rows, so the semester is simply absent rather than an empty table - and the
        // data source must not turn that into a failure.
        val page = VtopPage.parse(fixture("page-qcm-detail-ch20242505"))
        assertTrue(page.ok)
        assertTrue(
            page.tables.isEmpty(),
            "header-only semester has no rows to show, so no table is emitted",
        )
    }

    // ── LMS ─────────────────────────────────────────────────────────────────

    private fun lmsAssignments() =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LmsRow.serializer()), fixture("lms-assignments"))

    @kotlinx.serialization.Serializable
    private data class LmsRow(
        val name: String = "",
        val courseCode: String = "",
        val courseTitle: String = "",
        val assignmentTitle: String = "",
        val due: String = "",
        val done: Boolean = false,
        val day: Int? = null,
        val month: Int? = null,
        val year: Int? = null,
        val url: String? = null,
        val teachers: List<String> = emptyList(),
    )

    @Test
    fun lmsResponseDeserialisesIntoTheDto() {
        val rows = lmsAssignments()
        // The live capture had at least one event; if the month is empty this is still a valid
        // pass but worth seeing in the output.
        for (r in rows) {
            val a = com.amazecc.app.shared.model.LMSAssignment(
                name = r.name,
                courseCode = r.courseCode,
                courseTitle = r.courseTitle,
                assignmentTitle = r.assignmentTitle,
                due = r.due,
                done = r.done,
                day = r.day, month = r.month, year = r.year,
                url = r.url, teachers = r.teachers,
            )
            assertTrue(a.name.contains('/'), "composite name is code/course/assignment: ${a.name}")
            assertNotNull(a.url)
            assertTrue(a.url.startsWith("http"))
        }
    }

    @Test
    fun lmsCourseCodeIsNarrowedToTheBareVtOpCode() {
        val a = com.amazecc.app.shared.model.LMSAssignment(
            name = "BAMAT209_FALL26-27/Mathematical Foundations for Computation(BAMAT209)/Digital_Assignment_2",
            courseCode = "BAMAT209_FALL26-27",
            courseTitle = "Mathematical Foundations for Computation(BAMAT209)",
            assignmentTitle = "Digital_Assignment_2",
        )
        // VTOP spells course codes bare, so the LMS form has to be narrowed to join on.
        assertEquals("BAMAT209", a.shortCourseCode)
        assertEquals("Mathematical Foundations for Computation", a.readableCourseTitle)
    }

    @Test
    fun lmsDueDateBuildsFromTheCalendarCellNotTheProse() {
        // Real value: due = "Sunday, 4 October 2026, 12:00 AM", day/month/year = 4/10/2026.
        // Moodle writes midnight as 12:00 AM, which must not become 12:00 PM.
        val a = com.amazecc.app.shared.model.LMSAssignment(
            name = "BAMAT209_FALL26-27/x/Digital_Assignment_2",
            courseCode = "BAMAT209_FALL26-27",
            assignmentTitle = "Digital_Assignment_2",
            due = "Sunday, 4 October 2026, 12:00 AM",
            day = 4, month = 10, year = 2026,
        )
        val instant = assertNotNull(a.dueInstant(kotlinx.datetime.TimeZone.UTC))
        val ldt = instant.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        assertEquals(2026, ldt.year)
        assertEquals(10, ldt.monthNumber)
        assertEquals(4, ldt.dayOfMonth)
        assertTrue(ldt.hour == 0, "12:00 AM is midnight, not noon, but was ${ldt.hour}")
        assertEquals(0, ldt.minute)
        assertEquals("2026-10-04", a.dueDateOrNull())
    }

    @Test
    fun lmsDueIsNullWhenTheCalendarCellIsMissing() {
        val a = com.amazecc.app.shared.model.LMSAssignment(due = "Sunday, 4 October 2026, 12:00 AM")
        assertEquals(null, a.dueInstant(kotlinx.datetime.TimeZone.UTC))
    }

    @Test
    fun lmsSanitizerKeepsTheRealShape() {
        val res = AppSanitizers.sanitizeLms(
            com.amazecc.app.shared.model.LMSRes(
                assignments = listOf(
                    com.amazecc.app.shared.model.LMSAssignment(
                        name = "BAMAT209_FALL26-27/x/Digital_Assignment_2",
                        courseCode = "  BAMAT209_FALL26-27  ",
                        due = "  ",
                        url = "  https://lms.vit.ac.in/mod/assign/view.php?id=7  ",
                    )
                )
            )
        )
        val a = assertNotNull(res).assignments.single()
        assertEquals("BAMAT209_FALL26-27", a.courseCode, "whitespace trimmed")
        assertEquals("https://lms.vit.ac.in/mod/assign/view.php?id=7", a.url)
        // A row with neither url nor name is dropped - it cannot be de-duplicated across syncs.
        val dropped = AppSanitizers.sanitizeLms(
            com.amazecc.app.shared.model.LMSRes(assignments = listOf(com.amazecc.app.shared.model.LMSAssignment()))
        )
        assertEquals(emptyList(), assertNotNull(dropped).assignments)
    }

    @Test
    fun domainSnapshotCarriesLmsAsTheRealShape() {
        // The old DTO could not deserialise this payload at all: nothing in the server produced
        // maxMarks/status/score, so the app silently showed empty assignments.
        val payload = fixture("lms-assignments")
        val res = com.amazecc.app.shared.model.LMSRes(
            assignments = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.amazecc.app.shared.model.LMSAssignment.serializer()),
                payload,
            )
        )
        assertTrue(res.assignments.all { it.name.isNotBlank() }, "every row needs a composite name")
        assertTrue(
            res.assignments.all { it.stableId.isNotBlank() },
            "every row needs a stable id so re-syncing cannot duplicate it",
        )
    }
}
