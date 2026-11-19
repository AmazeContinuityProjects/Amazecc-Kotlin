package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.domain.EmbeddedLinkCode

/**
 * The four kinds of course component VIT Chennai reports, plus an unknown fallback.
 *
 * Embedded pairs are merged into one course and their marks combined by credit weight.
 * Standalone components stay on their own.
 */
enum class VtopComponent(val label: String, val isEmbedded: Boolean, val isLab: Boolean) {
    /** Embedded Theory — pairs with [ELA]. */
    ETH("ETH", isEmbedded = true, isLab = false),

    /** Embedded Lab (Architecture) — pairs with [ETH]. */
    ELA("ELA", isEmbedded = true, isLab = true),

    /** Theory Only — a standalone course. */
    TO("Theory Only", isEmbedded = false, isLab = false),

    /** Lab Only — a standalone course. */
    LO("Lab Only", isEmbedded = false, isLab = true),

    /** Could not be classified. Treated as standalone. */
    UNKNOWN("", isEmbedded = false, isLab = false)
}

/**
 * Canonicalises VTOP course codes so the same course joins up across every endpoint.
 *
 * VTOP does not present one code per course. Depending on the page you get some combination of:
 *
 * ```
 * timetable course-info   "BCSE101L Programming"             -> BCSE101
 * attendance row          "BCSE101" + slot "L31+2"           -> BCSE101
 * marks row               "BCSE101" + type "Embedded Lab"    -> BCSE101
 * grades row              "BCSE101"                          -> BCSE101
 * ```
 *
 * The key is always the **bare code**. Which kind of component a row is comes back separately
 * from [componentOf] and travels in the row's `courseType`, so the code never has to encode it.
 *
 * Every rule here delegates to [EmbeddedLinkCode], the single normalizer ported from the web
 * app, so `(T)`/`(L)`/`-T`/`-L` suffixes and `type`-only rows can never be judged by two
 * conflicting implementations. Public signatures are kept for all existing callers.
 */
object VtopCourseCode {

    private fun map(c: EmbeddedLinkCode.Component): VtopComponent = when (c) {
        EmbeddedLinkCode.Component.ETH -> VtopComponent.ETH
        EmbeddedLinkCode.Component.ELA -> VtopComponent.ELA
        EmbeddedLinkCode.Component.TO -> VtopComponent.TO
        EmbeddedLinkCode.Component.LO -> VtopComponent.LO
        EmbeddedLinkCode.Component.UNKNOWN -> VtopComponent.UNKNOWN
    }

    /** The bare course code, e.g. `BCSE101`. */
    fun base(raw: String?): String = EmbeddedLinkCode.baseCode(raw)

    /**
     * Classifies a row. Signal order matters: an explicit component label, then a `(T)`/`(L)`
     * marker, then a bare `BCSE101L` code, then the slot (weakest). The canonical rule is in
     * [EmbeddedLinkCode.componentOf].
     */
    fun componentOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): VtopComponent = map(EmbeddedLinkCode.componentOf(rawCode, typeHint, slot, title))

    /** The canonical key: the bare base code, always. */
    fun canonical(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): String = EmbeddedLinkCode.canonicalKey(rawCode, typeHint, slot, title)

    /** The `courseType` label for a row, or null when unclassified. */
    fun typeLabelOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): String? = EmbeddedLinkCode.typeLabelOf(rawCode, typeHint, slot, title)

    /**
     * Classifies a row from a stored `courseType` that a previous pass already resolved.
     *
     * Used by the merge, which sees the app's own labels rather than VTOP's raw wording.
     */
    fun fromTypeLabel(courseType: String?): VtopComponent =
        if (courseType == null) VtopComponent.UNKNOWN
        else map(EmbeddedLinkCode.componentOf(rawCode = null, typeHint = courseType))

    /** True when two keys describe the same course, ignoring case, spacing and any suffix. */
    fun sameCourse(a: String?, b: String?): Boolean = EmbeddedLinkCode.sameCourse(a, b)

    /** The `(L)`/`(T)`/`(P)` marker on [code], if any. Legacy rows may still carry one. */
    fun suffixOf(code: String?): String? = EmbeddedLinkCode.suffixOf(code)?.toString()
}
