package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.GradeComponent
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.overhaul.M3EGradeAssessmentGroup
import com.amazecc.app.shared.domain.overhaul.M3EGradeBreakdownRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeDistributionRow
import com.amazecc.app.shared.domain.overhaul.M3EGradePointTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermScore
import com.amazecc.app.shared.domain.overhaul.M3EGradeTypeAverage
import com.amazecc.app.shared.model.GradeBreakdown
import kotlin.math.round

/**
 * Pure, headless-testable arithmetic for the grade-history pages.
 *
 * This is the Kotlin counterpart of the web app's `lib/gradeHistory.ts` helpers that
 * `MarksHistoryTab.tsx` uses for tone, ordering and distribution. Nothing here reads
 * AppState, Compose or a clock: every input is a snapshot value copied by the host.
 */
object M3EGradeComputations {

    /** Parse VTOP's string numbers without ever inventing a zero for a blank cell. */
    fun num(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val parsed = value.trim().toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() }
    }

    fun round2(value: Double): Double = round(value * 100.0) / 100.0

    /** VTOP's number, or the em dash the web page prints when it published nothing. */
    fun numOrDash(value: String?): String {
        val parsed = num(value) ?: return "—"
        val rounded = round2(parsed)
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    /** `N` means "not graded yet" — it must not render as 0/10 in the UI badge. */
    fun gradePoints(grade: String?): Int? =
        if (grade?.trim()?.equals("N", ignoreCase = true) == true) null
        else GradeHistory.gradePoints(grade)

    /** The `points / 10` badge on the course subpage. */
    fun gradePointsLabel(grade: String?): String =
        gradePoints(grade)?.let { "$it / 10" } ?: "—"

    /**
     * Grade -> semantic hue. Coarser than a one-hue-per-letter ladder on purpose:
     * E and F share red, and N/P collapse to grey, matching the web's `GRADE_TONE`.
     */
    fun toneForGrade(grade: String?): M3EGradeTone = when (grade?.trim()?.uppercase()) {
        "S" -> M3EGradeTone.AMBER
        "A" -> M3EGradeTone.GREEN
        "B" -> M3EGradeTone.BLUE
        "C" -> M3EGradeTone.CYAN
        "D" -> M3EGradeTone.VIOLET
        "E", "F" -> M3EGradeTone.RED
        else -> M3EGradeTone.GREY
    }

    fun isEmbedded(courseType: String?): Boolean = GradeHistory.isEmbeddedCourse(courseType)

    /**
     * GPA mini-bar hue. A term with no published GPA gets no bar at all; the other
     * three thresholds are the web's `gpaBarTone`.
     */
    fun pointTone(gpa: Double): M3EGradePointTone? = when {
        gpa <= 0.0 -> null
        gpa >= 9.0 -> M3EGradePointTone.SUCCESS
        gpa >= 8.0 -> M3EGradePointTone.PRIMARY
        else -> M3EGradePointTone.WARNING
    }

    /** The "This term" insight slide uses the GPA threshold tones, not the letter ladder. */
    fun insightTone(gpa: Double): M3EGradeTone = when {
        gpa >= 9.0 -> M3EGradeTone.GREEN
        gpa >= 8.0 -> M3EGradeTone.BLUE
        else -> M3EGradeTone.AMBER
    }

    /** Best grade first; ungraded rows sort last, ties broken by recorded score. */
    fun sortByGrade(rows: List<M3EGradeCourseRow>): List<M3EGradeCourseRow> =
        rows.sortedWith(
            compareByDescending<M3EGradeCourseRow> { gradePoints(it.grade) ?: -1 }
                .thenByDescending { it.score ?: -1.0 }
        )

    /**
     * Distribution across every graded course. `N` and `P` are excluded because they
     * are not grades the student received; an unknown letter is dropped rather than
     * bucketed into a column nobody recognises.
     */
    fun distribution(rows: List<M3EGradeCourseRow>): List<M3EGradeDistributionRow> {
        val counts = mutableMapOf<String, Int>()
        for (row in rows) {
            val letter = row.grade.trim().uppercase()
            if (!GradeHistory.isGradedLetter(letter)) continue
            counts[letter] = (counts[letter] ?: 0) + 1
        }
        val total = counts.values.sum()
        if (total == 0) return emptyList()
        return GradeHistory.GRADE_ORDER.filter { counts.containsKey(it) }.map { letter ->
            val count = counts.getValue(letter)
            M3EGradeDistributionRow(
                grade = letter,
                count = count,
                share = round2(count.toDouble() / total * 100.0),
                tone = toneForGrade(letter)
            )
        }
    }

    /** Mean score by course type, scored rows only; unscored rows cannot skew the mean. */
    fun typeAverages(rows: List<M3EGradeCourseRow>): List<M3EGradeTypeAverage> {
        val groups = mutableMapOf<String, MutableList<Double>>()
        for (row in rows) {
            val score = row.score ?: continue
            val type = row.courseType.trim().ifEmpty { "Other" }
            groups.getOrPut(type) { mutableListOf() }.add(score)
        }
        return groups
            .map { (type, scores) ->
                M3EGradeTypeAverage(type, round2(scores.sum() / scores.size), scores.size)
            }
            .sortedByDescending { it.avg }
    }

    fun termScores(rows: List<M3EGradeTermRow>): List<M3EGradeTermScore> =
        rows.map { M3EGradeTermScore(it.termId, it.label, it.avgScore, it.scoredCount) }

    /** Heaviest assessment first, as in the web's flat breakdown fallback. */
    fun byWeightage(rows: List<M3EGradeBreakdownRow>): List<M3EGradeBreakdownRow> =
        rows.sortedByDescending { it.weightage }

    /**
     * DTO -> domain shape so the already-tested [GradeHistory.weightedTotal] /
     * [GradeHistory.weightedTotalDiffers] can be used on the published effective-grade row.
     * `weightageMark` is VTOP's weighted contribution; it is *not* the raw score.
     */
    fun gradeComponents(details: List<GradeBreakdown>?): List<GradeComponent>? =
        details?.takeIf { it.isNotEmpty() }?.map { d ->
            GradeComponent(
                name = d.component,
                score = d.scoredMark,
                maxMark = d.maxMark,
                weightagePercent = d.weightagePercent,
                status = d.status,
                weightage = d.weightageMark,
            )
        }

    /**
     * Port of the web's `groupAssessmentsByType`: VTOP publishes one "Mark Title" table per
     * component, and the AmazeCC route tags each row with the table it came from. When no row
     * carries a tag (frozen legacy route) this returns null and the caller keeps the flat list.
     * Order: Theory, Lab, `Component N` ascending, then the rest alphabetically.
     */
    fun groupAssessmentsByType(rows: List<M3EGradeBreakdownRow>): List<M3EGradeAssessmentGroup>? {
        if (rows.isEmpty() || rows.none { it.type != null }) return null
        val grouped = LinkedHashMap<String, MutableList<M3EGradeBreakdownRow>>()
        for (row in rows) {
            val key = row.type?.trim().takeUnless { it.isNullOrEmpty() } ?: "Other"
            grouped.getOrPut(key) { mutableListOf() }.add(row)
        }
        val rank: (String) -> Int = { name ->
            when {
                name.contains("theory", ignoreCase = true) -> 0
                name.contains("lab", ignoreCase = true) -> 1
                else -> Regex("^component\\s+(\\d+)$", RegexOption.IGNORE_CASE)
                    .matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
                    ?.let { 100 + it } ?: 1000
            }
        }
        return grouped.keys
            .sortedWith(compareBy(rank).thenBy { it.lowercase() })
            .map { type ->
                M3EGradeAssessmentGroup(
                    type = type,
                    assessments = grouped.getValue(type).sortedByDescending { it.weightage }
                )
            }
    }

    /**
     * Reconciliation note the web prints when the weights do not add up to the published total.
     * VTOP's recorded total stays authoritative; the note only explains the discrepancy.
     */
    fun reconciliationNote(grandTotal: String?, breakdown: List<GradeBreakdown>?): String? {
        val components = gradeComponents(breakdown) ?: return null
        if (!GradeHistory.weightedTotalDiffers(grandTotal, components)) return null
        val weighted = GradeHistory.weightedTotal(components) ?: return null
        return "The weightage above sums to ${round2(weighted)}, while VTOP records " +
            "${numOrDash(grandTotal)}%. The recorded total is shown here."
    }
}

/**
 * Defensive seam for the assessment table tag. The AmazeCC route sets `type` on every
 * `GradeBreakdown`; the frozen legacy route does not. When that field lands on the DTO the real
 * member wins over this extension (members always beat extensions), so grouping switches on
 * without a second edit. Until then it reports "untagged" and the flat breakdown is used.
 */
val GradeBreakdown.type: String? get() = null
