package com.amazecc.app.shared.vtop

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
 * Embedded Theory / Embedded Lab are merged into one course with credit-weighted marks.
 * Theory Only / Lab Only are standalone and never merged.
 */
object VtopCourseCode {

    /** Trailing component marker on a code, e.g. `BCSE101(L)`. */
    private val SUFFIX = Regex("\\s*\\(([LTP])\\)\\s*$", RegexOption.IGNORE_CASE)

    /**
     * Wording is matched embedded-first: "Embedded Lab" also contains "Lab", so a generic lab
     * pattern would misclassify every embedded course as a standalone Lab Only.
     */
    private val ETH_TEXT = Regex("\\b(ETH|EMBEDDED\\s+THEORY)\\b", RegexOption.IGNORE_CASE)
    private val ELA_TEXT = Regex("\\b(ELA|EMBEDDED\\s+LAB(?:\\s+ARCHITECTURE)?)\\b", RegexOption.IGNORE_CASE)
    private val TO_TEXT = Regex("\\b(TO|THEORY\\s+ONLY)\\b", RegexOption.IGNORE_CASE)
    private val LO_TEXT = Regex("\\b(LO|LAB\\s+ONLY)\\b", RegexOption.IGNORE_CASE)

    private val CODE_TOKEN = Regex("\\b([A-Z]{2,}[0-9]{2,}[A-Z]*)\\b")

    /**
     * The bare course code, e.g. `BCSE101`.
     *
     * Strips a `(L)`/`(T)`/`(P)` marker, then pulls the first academic-code-shaped token out of
     * the string. Falls back to the text with words and punctuation stripped, so a malformed cell
     * still produces something stable rather than nothing.
     */
    fun base(raw: String?): String {
        val text = raw.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return ""

        val withoutSuffix = SUFFIX.replace(text, "").trim()
        CODE_TOKEN.find(withoutSuffix.uppercase())?.let { m ->
            return stripTrailingComponentLetter(m.groupValues[1])
        }

        val cleaned = withoutSuffix
            .replace(ETH_TEXT, " ").replace(ELA_TEXT, " ")
            .replace(TO_TEXT, " ").replace(LO_TEXT, " ")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase()
        return cleaned.ifEmpty { withoutSuffix.uppercase() }
    }

    /** VTOP writes embedded labs as `BCSE101L` in some tables and `BCSE101(L)` in others. */
    private fun stripTrailingComponentLetter(code: String): String =
        Regex("^([A-Z]+[0-9]+)[LPT]$").find(code)?.groupValues?.get(1) ?: code

    /**
     * Classifies a row.
     *
     * Signal order matters:
     *  1. an explicit ETH/ELA/TO/LO token in the type or title — the only reliable source
     *  2. `Embedded` plus a lab/theory word
     *  3. a `(L)`/`(P)`/`(T)` suffix on the code
     *  4. slot codes — weakest, and last, because a lab sharing a slot with its theory half
     *     would otherwise be read as theory and steal its marks
     */
    fun componentOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null
    ): VtopComponent {
        val words = listOfNotNull(typeHint, title, rawCode).filter { it.isNotBlank() }

        words.firstOrNull { ETH_TEXT.containsMatchIn(it) }?.let { return VtopComponent.ETH }
        words.firstOrNull { ELA_TEXT.containsMatchIn(it) }?.let { return VtopComponent.ELA }
        words.firstOrNull { TO_TEXT.containsMatchIn(it) }?.let { return VtopComponent.TO }
        words.firstOrNull { LO_TEXT.containsMatchIn(it) }?.let { return VtopComponent.LO }

        SUFFIX.find(rawCode.orEmpty())?.let { m ->
            return when (m.groupValues[1].uppercase()) {
                "L", "P" -> VtopComponent.LO
                else -> VtopComponent.TO
            }
        }
        val code = rawCode.orEmpty().uppercase()
        if (Regex("\\b[A-Z]+[0-9]+[LP]\\b").containsMatchIn(code)) return VtopComponent.LO

        slot?.let { s ->
            val tokens = s.replace(Regex("[|,]"), "+").split("+")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.all { it.uppercase().startsWith("L") }) {
                return VtopComponent.LO
            }
        }

        return VtopComponent.UNKNOWN
    }

    /** The canonical key: the bare base code, always. */
    fun canonical(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null
    ): String = base(rawCode)

    /** The `courseType` label for a row, or null when unclassified. */
    fun typeLabelOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null
    ): String? = componentOf(rawCode, typeHint, slot, title).label.ifEmpty { null }

    /**
     * Classifies a row from a stored `courseType` that a previous pass already resolved.
     *
     * Used by the merge, which sees the app's own labels rather than VTOP's raw wording.
     */
    fun fromTypeLabel(courseType: String?): VtopComponent = when {
        courseType == null -> VtopComponent.UNKNOWN
        ETH_TEXT.containsMatchIn(courseType) -> VtopComponent.ETH
        ELA_TEXT.containsMatchIn(courseType) -> VtopComponent.ELA
        TO_TEXT.containsMatchIn(courseType) -> VtopComponent.TO
        LO_TEXT.containsMatchIn(courseType) -> VtopComponent.LO
        else -> VtopComponent.UNKNOWN
    }

    /** True when two keys describe the same course, ignoring case, spacing and any suffix. */
    fun sameCourse(a: String?, b: String?): Boolean {
        val x = base(a)
        val y = base(b)
        return x.isNotEmpty() && x == y
    }

    /** The `(L)`/`(T)`/`(P)` marker on [code], if any. Legacy rows may still carry one. */
    fun suffixOf(code: String?): String? =
        SUFFIX.find(code.orEmpty())?.groupValues?.get(1)?.uppercase()
}
