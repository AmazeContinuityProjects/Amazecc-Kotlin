package com.amazecc.app.shared.domain

/**
 * D-187 — the *pure* semantics of a two-half embedded course.
 *
 * This is deliberately not a merge. [EmbeddedLinkMerge] folds the two halves into one `Course`
 * for the ordinary screens; here the halves stay separate so a surface that needs the raw
 * theory/lab numbers can show them without ever reconstructing a blended attendance percentage
 * or an invented grade letter.
 *
 * The rules come verbatim from the sibling web app, and the numbers agree with it:
 *
 *  - `gradeHistory.ts:543-572` — an embedded course's halves are the two `Embedded Theory` /
 *    `Embedded Lab` rows of the marks payload, split by *courseType text*, never by code. The
 *    code is unremarkable (`BAEEE101`, `BACHY107`) and is only the link key.
 *  - `gradeHistory.ts:415-429` — `creditWeightedTotal` multiplies every half's own score by its
 *    own credits; a missing credit makes the blend `null` instead of assuming a 1:1 ratio.
 *  - `marksPredictor.ts:576-608` — the per-component weighting is applied only to embedded
 *    courses; each half keeps its own credit, never a shared weight.
 *  - `useCoursePredictionScope.ts:216-230` — the one `isEmbedded` rule: the label says embedded,
 *    *or* both theory and lab payloads exist.
 *  - `gradeHistory.ts:599-618` — a published grade is reconciled against the credit-weighted
 *    blend; a half without credits cannot be reconciled.
 *
 * The user's D-187 clarification is the contract that matters most: attendance and grades are
 * NOT combined, and the credit split is never assumed. It is `3+1`, `2+2`, `2+1`, `1+1` — whatever
 * the two halves actually carry — and a half with no credit leaves the blend honestly `null`.
 *
 * Every function is pure: no state, no clock, no storage, no Compose.
 */
enum class EmbeddedHalf {
    THEORY,
    LAB,
}

/** One half of an embedded course, exactly as the marks payload published it. */
data class EmbeddedEntry(
    val code: String,
    val half: EmbeddedHalf,
    val type: String = "",
    /** The half's OWN credit value. `0.0` means "not published", never "weight zero". */
    val credits: Double = 0.0,
    /** This half's own attendance percentage. Never averaged with the other half. */
    val attendancePercent: Double? = null,
    /** This half's earned total out of 100. */
    val earnedMarks: Double? = null,
    /** This half's released (recorded outcome) total out of 100. */
    val releasedMarks: Double? = null,
    /** This half's own published letter, or null when VTOP published none. */
    val grade: String? = null,
    val title: String = "",
)

/**
 * One embedded course: two halves, held side by side rather than folded.
 *
 * There is intentionally **no** aggregate attendance field. [attendanceEntries] carries both
 * halves with their own percentages; any caller that wants a single number would have to invent
 * one, which is exactly what this type refuses to do.
 *
 * [earnedBlend] and [releasedBlend] are the one pair of derived numbers the contract allows:
 * each half's own score is multiplied by that half's own credits and the two are released
 * together. A half without credits, or without that score, makes the corresponding blend null.
 *
 * [gradeEntries] likewise keeps both halves' letters. [combinedGrade] is the only combined
 * letter, and it is populated only when both halves published the SAME real letter. A blend is a
 * number, not a grade: an embedded course receives one curved letter on its credit-weighted total
 * (`gradeBoundaries.ts:166-198`), so no letter is inferred from [releasedBlend] here. Otherwise
 * [combinedGrade] is null.
 */
data class EmbeddedGroup(
    val baseCode: String,
    val title: String,
    /** Web-exact: "Embedded Theory & Lab", "Embedded Theory" or "Embedded Lab". */
    val label: String,
    val theory: EmbeddedEntry?,
    val lab: EmbeddedEntry?,
    /** Sum of the present halves' OWN credits — `3+1`, `2+2`, `2+1`, `1+1`, ... never a default. */
    val totalCredits: Double,
    /** BOTH halves with their own percentages. No aggregate, no mean. */
    val attendanceEntries: List<EmbeddedEntry>,
    val earnedBlend: Double?,
    val releasedBlend: Double?,
    /** BOTH halves' grades — no half dropped, no merged letter fabricated here. */
    val gradeEntries: List<EmbeddedEntry>,
    /** Both halves publishing the same real letter; never inferred from a blend. */
    val combinedGrade: String?,
)

object EmbeddedCourseSemantics {

    /** The canonical labels, spelled exactly as the surfaces this domain backs display them. */
    const val LABEL_COMBINED = "Embedded Theory & Lab"
    const val LABEL_THEORY = "Embedded Theory"
    const val LABEL_LAB = "Embedded Lab"

    /**
     * Normalize a courseType (or an FFCS `TYPE`) into one of the three canonical labels.
     *
     * The combined spelling arrives in several disguises: VTOP's `Embedded Theory and Lab`, the
     * Kotlin app's `Embedded Theory & Lab`, and the timetable's `ETH+ELA` / `ETL`. All of them are
     * one course with two halves. A single-half spelling is classified through
     * [EmbeddedLinkCode.componentOf] — the one canonical component classifier — so no second
     * regex for "is this the lab half" can creep in here.
     *
     * Unclassified input gets the empty string, not a definitive embedded label: [isEmbedded] is
     * false for the same input, so claiming "Embedded Theory & Lab" would mislabel data that
     * carries no type signal at all.
     */
    fun labelFor(type: String?): String {
        val raw = type?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val upper = raw.uppercase()
        if (upper == "ETL" ||
            upper.contains("ETH+ELA") ||
            upper.contains("ELA+ETH") ||
            upper.contains("EMBEDDED THEORY AND LAB") ||
            upper.contains("EMBEDDED THEORY & LAB")
        ) {
            return LABEL_COMBINED
        }
        return when (EmbeddedLinkCode.componentOf(rawCode = null, typeHint = raw)) {
            EmbeddedLinkCode.Component.ELA, EmbeddedLinkCode.Component.LO -> LABEL_LAB
            EmbeddedLinkCode.Component.ETH, EmbeddedLinkCode.Component.TO -> LABEL_THEORY
            EmbeddedLinkCode.Component.UNKNOWN -> ""
        }
    }

    /**
     * The predictor's single definition of "embedded" (`useCoursePredictionScope.ts:229-230`):
     * the label says embedded, OR both theory and lab payloads exist. Neither signal alone is
     * enough to change the other — a "Lab Only" course with both payloads is still embedded to
     * the credit math, and a merged "Embedded Theory" item with no lab payload still is too.
     */
    fun isEmbedded(type: String?, hasTheory: Boolean, hasLab: Boolean): Boolean =
        type?.contains("embedded", ignoreCase = true) == true || (hasTheory && hasLab)

    /**
     * Group halves by [EmbeddedLinkCode.baseCode], preserving the order in which base codes first
     * appear. One theory and one lab per group; duplicates of the same half are resolved by
     * keeping the first row rather than summing two reports of one half.
     *
     * A lone half is still a group (`"Embedded Lab"` / `"Embedded Theory"`) with honest nulls:
     * it gets no blended marks and no combined grade, because there is no pair to blend.
     */
    fun group(entries: List<EmbeddedEntry>): List<EmbeddedGroup> {
        if (entries.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<String, MutableList<EmbeddedEntry>>()
        for (entry in entries) {
            val key = baseCodeOf(entry)
            buckets.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return buckets.values.map(::buildGroup)
    }

    /**
     * `gradeHistory.ts:415-429`: `sum(score * credits) / sum(credits)`.
     *
     * Every usable entry must carry its own positive credit — a missing one returns `null`
     * rather than assuming an equal ratio — and the credit total must be positive. Scores that
     * are not finite are ignored. [group] additionally requires BOTH halves to have published
     * the score before it calls this, so a half is never silently dropped.
     */
    fun creditWeighted(
        entries: List<EmbeddedEntry>,
        selector: (EmbeddedEntry) -> Double?,
    ): Double? {
        val scored = entries.mapNotNull { entry ->
            selector(entry)?.takeIf { it.isFinite() }?.let { score -> entry to score }
        }
        if (scored.isEmpty()) return null

        val weights = scored.map { (entry, _) -> entry.credits }
        if (weights.any { !it.isFinite() || it <= 0.0 }) return null
        val totalCredits = weights.sum()
        if (totalCredits <= 0.0) return null

        val weighted = scored.sumOf { (entry, score) -> score * entry.credits }
        // No rounding here on purpose: 3+1 over 87.5/93 is exactly 88.875, and a caller that
        // wants a display string can round. Rounding the domain number would lose that precision.
        return weighted / totalCredits
    }

    // ── internals ──────────────────────────────────────────────────────────────

    /** The one canonical base-code rule; the fallback only keeps malformed rows from colliding. */
    private fun baseCodeOf(entry: EmbeddedEntry): String =
        EmbeddedLinkCode.baseCode(entry.code)
            .takeIf { it.isNotEmpty() }
            ?: entry.code.trim().uppercase().ifEmpty { entry.title.trim().uppercase() }

    private fun buildGroup(rows: List<EmbeddedEntry>): EmbeddedGroup {
        val theory = rows.firstOrNull { it.half == EmbeddedHalf.THEORY }
        val lab = rows.firstOrNull { it.half == EmbeddedHalf.LAB }
        val base = baseCodeOf(rows.first())

        val earnedBlend = blendPair(theory, lab) { it.earnedMarks }
        val releasedBlend = blendPair(theory, lab) { it.releasedMarks }

        return EmbeddedGroup(
            baseCode = base,
            title = EmbeddedLinkCode.displayTitle(theory?.title, lab?.title, base),
            label = when {
                theory != null && lab != null -> labelFor("ETH+ELA")
                lab != null -> labelFor("ELA")
                else -> labelFor("ETH")
            },
            theory = theory,
            lab = lab,
            totalCredits = listOfNotNull(theory, lab).sumOf { entry ->
                if (entry.credits.isFinite() && entry.credits > 0.0) entry.credits else 0.0
            },
            attendanceEntries = listOfNotNull(theory, lab),
            earnedBlend = earnedBlend,
            releasedBlend = releasedBlend,
            gradeEntries = listOfNotNull(theory, lab),
            // A blend is a number, not a letter. No absolute-ladder inference: the web grades an
            // embedded course once, on a curved ladder we do not have here, from its weighted total.
            combinedGrade = if (theory == null || lab == null) {
                null
            } else {
                publishedSharedGrade(theory, lab)
            },
        )
    }

    /**
     * Blend only when both halves published a FINITE score. One half with marks is not a blend —
     * it is a half with marks, and the other half must not be dropped from the arithmetic. The
     * finite check matters: `creditWeighted` filters non-finite scores, so without it a NaN half
     * would silently leave a one-entry list and the survivor would be reported as a "blend".
     */
    private fun blendPair(
        theory: EmbeddedEntry?,
        lab: EmbeddedEntry?,
        selector: (EmbeddedEntry) -> Double?,
    ): Double? {
        if (theory == null || lab == null) return null
        val theoryScore = selector(theory)
        val labScore = selector(lab)
        if (theoryScore == null || labScore == null) return null
        if (!theoryScore.isFinite() || !labScore.isFinite()) return null
        return creditWeighted(listOf(theory, lab), selector)
    }

    /**
     * A combined letter is real only when both halves independently published the same graded
     * letter. `N` and any unknown token are not grades the student received, so they never
     * become a combined grade.
     */
    private fun publishedSharedGrade(theory: EmbeddedEntry, lab: EmbeddedEntry): String? {
        val t = theory.grade?.trim()?.uppercase()
        val l = lab.grade?.trim()?.uppercase()
        if (t.isNullOrEmpty() || l.isNullOrEmpty() || t != l) return null
        return t.takeIf { it in GradeHistory.GRADE_ORDER }
    }
}
