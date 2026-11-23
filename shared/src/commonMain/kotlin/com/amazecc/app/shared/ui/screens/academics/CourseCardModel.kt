package com.amazecc.app.shared.ui.screens.academics

import com.amazecc.app.shared.domain.CourseGroup
import com.amazecc.app.shared.utils.FacultyUtils
import com.amazecc.app.shared.utils.ParsedFaculty

/**
 * What one card on the course dashboard shows, decided outside the composable.
 *
 * This used to be a run of `val`s inside `CourseDetailCard`: whether a course is embedded, which
 * badge it gets, what each half's attendance is, and which of three colours that percentage earns.
 * None of it could be tested, and the first two are now load-bearing — an embedded course has only
 * existed in this app's data since the halves stopped being merged at ingest, so the branch that
 * draws two rows had never run before.
 *
 * The colour bands are stated once here. `CourseDetailCard` used two separate `when`s at the same
 * 85/75 boundaries and painted the results from two different palettes (success/warning/danger on
 * the headline chip, chart1/chart3/chart5 on a half row); both are now one [CardTone] that each
 * element maps to its own palette, which removes the chance of them drifting apart.
 *
 * Note these bands are the dashboard's own, deliberately *not*
 * [com.amazecc.app.shared.domain.Projections.attendanceStatus]: that one is target-relative
 * (75 + 5) and drives the predictor, while these are fixed at the institute's 75/85 lines.
 */

/** Which half of an embedded course a row is showing. */
enum class CourseHalf(val badge: String, val display: String) {
    THEORY("TH", "Theory"),
    LAB("LO", "Lab"),
}

/** Which of the card's attendance colours a percentage earns. */
enum class CardTone { GOOD, MID, LOW, NEUTRAL }

/** Honest copy for a course/half whose percentage was never published (D-189). */
const val ATTENDANCE_NOT_PUBLISHED = "Attendance not published"

/**
 * The fixed 85/75 lines, in one place. See the file note on why these are not `attendanceStatus`.
 *
 * D-189: null is NOT zero. A merged embedded course carries no published percentage at all, so it
 * earns the neutral tone rather than LOW — LOW would paint an unpublished figure as a real 0%.
 */
fun cardTone(percentage: Double?): CardTone = when {
    percentage == null -> CardTone.NEUTRAL
    percentage >= 85.0 -> CardTone.GOOD
    percentage >= 75.0 -> CardTone.MID
    else -> CardTone.LOW
}

/**
 * VTOP sends the percentage as pre-rendered text, sometimes with a trailing `%`.
 *
 * The honest parse: null means "nothing was published". A merged embedded course's combined
 * attendance has a blank percentage by design, and a real zero ("0") is a different, published
 * fact — collapsing both to 0.0 is what used to paint an unpublished pair as LOW/critical.
 */
fun attendancePctOrNull(raw: String?): Double? =
    raw?.replace("%", "")?.trim()?.toDoubleOrNull()

/**
 * Legacy numeric shape for callers that need a Double. 0 here means "nothing to show" (see
 * [CourseCardModel.headlineVisible]); card toning and copy use [attendancePctOrNull] instead.
 */
fun attendancePctOf(raw: String?): Double =
    attendancePctOrNull(raw) ?: 0.0

/** One half's row: its badge, its own attendance, and the details that go beside it. */
data class CourseHalfRow(
    val half: CourseHalf,
    /** Null when this half published no percentage — never a fabricated 0.0. */
    val attendancePct: Double?,
    val tone: CardTone,
    val faculty: ParsedFaculty?,
    val slot: String?,
) {
    /** `Theory (F1+TF1)`, or just `Theory` when the half has no slot on file. */
    val label: String
        get() = if (slot.isNullOrBlank()) half.display else "${half.display} ($slot)"

    /** What a card draws for this half: the real figure or honest absence, never "0%". */
    val attendanceLabel: String
        get() = attendancePct?.let { "$it%" } ?: ATTENDANCE_NOT_PUBLISHED
}

/** Everything `CourseDetailCard` draws, with no Compose in it. */
data class CourseCardModel(
    val isEmbedded: Boolean,
    /** `EMB` for a pair, else `TH`/`LO`, unless the title carried an explicit marker. */
    val badge: String,
    /** Attendance for a non-embedded card; null when embedded or when nothing was published. */
    val headlinePct: Double?,
    val headlineTone: CardTone,
    /** Faculty for a non-embedded card, where the halves cannot both claim it. */
    val faculty: ParsedFaculty?,
    val halves: List<CourseHalfRow>,
) {
    /** What a card draws for the headline: the real figure or honest absence, never "0%". */
    val headlineLabel: String
        get() = headlinePct?.let { "$it%" } ?: ATTENDANCE_NOT_PUBLISHED

    /** False when there is no attendance to report - a course nobody has sat or a two-half pair. */
    val headlineVisible: Boolean
        get() = !isEmbedded && headlinePct?.let { it > 0.0 } == true
}

/**
 * A course is embedded exactly when it has both halves.
 *
 * Attendance decides this, not marks. VTOP published an `Embedded Theory` row with no matching
 * `Embedded Lab` row for three of four embedded courses in CH20262701, so a marks-only course
 * would read as a lone theory card and hide the lab half again - which is the bug this keeps shut.
 */
fun CourseGroup.cardModel(): CourseCardModel {
    val cleaned = cleanCourseTitle(courseTitle)
    val theoryAtt = theoryAtt
    val labAtt = labAtt
    val isEmbedded = (theory != null && lab != null) || (theoryAtt != null && labAtt != null)

    val badge = cleaned.typeBadgeOverride ?: if (isEmbedded) {
        "EMB"
    } else {
        // Attendance counts too, not just marks. A Lab Only course has *no* marks half and never
        // had a theory one, so checking `theory`/`lab` alone badges it "TH" - which is what
        // happened as soon as the halves stopped being merged and the lab-only row stopped
        // masquerading as the theory one.
        val anyLab = listOfNotNull(theory?.courseType, lab?.courseType, theoryAtt?.courseType, labAtt?.courseType)
            .any { it.contains("lab", ignoreCase = true) }
        if (anyLab) CourseHalf.LAB.badge else CourseHalf.THEORY.badge
    }

    val headlinePct = attendancePctOrNull(theoryAtt?.attendancePercentage ?: labAtt?.attendancePercentage)
    val rawFaculty = theory?.faculty?.ifBlank { null }
        ?: lab?.faculty?.ifBlank { null }
        ?: theoryAtt?.faculty?.ifBlank { null }
        ?: labAtt?.faculty?.ifBlank { null }

    fun row(half: CourseHalf, marks: com.amazecc.app.shared.domain.CourseMarks?, att: com.amazecc.app.shared.domain.CourseAttendance?): CourseHalfRow {
        val pct = attendancePctOrNull(att?.attendancePercentage)
        return CourseHalfRow(
            half = half,
            attendancePct = pct,
            tone = cardTone(pct),
            faculty = FacultyUtils.parseFaculty(marks?.faculty?.ifBlank { null } ?: att?.faculty?.ifBlank { null }),
            slot = marks?.slot?.ifBlank { null } ?: att?.slotName?.ifBlank { null },
        )
    }

    return CourseCardModel(
        isEmbedded = isEmbedded,
        badge = badge,
        headlinePct = headlinePct,
        headlineTone = cardTone(headlinePct),
        faculty = FacultyUtils.parseFaculty(rawFaculty),
        halves = if (isEmbedded) {
            listOf(row(CourseHalf.THEORY, theory, theoryAtt), row(CourseHalf.LAB, lab, labAtt))
        } else {
            emptyList()
        },
    )
}

// D-140: relocated from the now-deleted legacy CourseDashboard.kt. This pure title/badge
// normalisation is still consumed by CourseCardModel.cardModel() and its tests; the legacy
// composable itself had no live call site after the unified academics hub took over.
data class CleanedTitle(
    val cleanTitle: String,
    val typeBadgeOverride: String? = null
)

fun cleanCourseTitle(rawTitle: String): CleanedTitle {
    var title = rawTitle.trim()
    var badge: String? = null

    if (title.contains("(Project)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Project\\s*\\)"), "").trim()
        badge = "PJ"
    } else if (title.contains("(Online Course)", ignoreCase = true) || title.contains("(Online)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Online\\s*(Course)?\\s*\\)"), "").trim()
        badge = "OC"
    } else if (title.contains("(Theory)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Theory\\s*\\)"), "").trim()
        badge = "TH"
    } else if (title.contains("(Lab)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Lab\\s*\\)"), "").trim()
        badge = "LO"
    }

    title = title.replace(Regex("\\s+"), " ").trim()
    return CleanedTitle(cleanTitle = title, typeBadgeOverride = badge)
}