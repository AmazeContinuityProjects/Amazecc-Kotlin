package com.amazecc.app.shared.domain

/**
 * The one canonical normalizer for *linking* a course across every AmazeCC endpoint.
 *
 * The sibling web app (`../AmazeCC`) had five spellings of the same operation before it
 * consolidated them; this object is that consolidation, ported to Kotlin:
 *
 * - `src/lib/courseClassifier.ts:25-70` — `isLabCourse` / `sanitizeCourseCode`
 * - `src/lib/courseNormalizer.ts:130-159` — `extractBaseCourseCode` / `resolveComponentType`
 * - `src/components/custom/exams/marks-predictor/useCoursePredictionScope.ts:211-230,290-404`
 *   — `stripComponentMarker`, `isEmbeddedCourse`, `buildEnrolledCourses`
 * - `src/lib/taskMatch.ts:11-20` — the `(T)/(L)/(P)`-stripping `sanitizeCourseCode`
 *
 * The rule, in one place:
 *
 * 1. **Base code** — trim, upper-case, collapse whitespace, drop a trailing `(T)`, `(L)`,
 *    `(P)` or `(E)` marker (case-insensitive) AND a trailing `-T`/`-L`/`-P`/`-E` marker.
 *    If the cell also contains a title, pull the academic-code-shaped token out of it.
 *    A bare code that VTOP wrote as `BCSE101L` is folded to `BCSE101`.
 * 2. **Half** — an explicit `(T)`/`-T` marker (or the `type` saying `Theory Only` /
 *    `Embedded Theory`) is the theory half; `(L)`/`-L` (or `Lab Only` / `Embedded Lab`
 *    / a slot starting with `L`) is the lab half. Embedded wording is matched *before*
 *    generic lab wording, because "Embedded Lab" also contains "Lab".
 * 3. **Link key** — the base code. A suffixed API row and an unsuffixed scraper row that
 *    carries only a `type` therefore share one key and can never be shown as two courses.
 *
 * Pure and dependency-free on purpose: `VtopCourseCode` and every merge/projection delegate
 * here so a second, conflicting rule cannot creep back in.
 */
object EmbeddedLinkCode {

    /** VTOP's four component kinds. Mirrors `VtopComponent` without importing the `vtop` package. */
    enum class Component(val label: String, val isEmbedded: Boolean, val isLab: Boolean) {
        ETH("ETH", isEmbedded = true, isLab = false),
        ELA("ELA", isEmbedded = true, isLab = true),
        TO("Theory Only", isEmbedded = false, isLab = false),
        LO("Lab Only", isEmbedded = false, isLab = true),
        UNKNOWN("", isEmbedded = false, isLab = false);
    }

    /** The theory or lab side of an embedded pair. */
    enum class Half(val marker: String) {
        THEORY("T"),
        LAB("L");

        val isLab: Boolean get() = this == LAB
    }

    private val PAREN_SUFFIX = Regex("\\s*\\(([A-Z])\\)\\s*$", RegexOption.IGNORE_CASE)
    private val DASH_SUFFIX = Regex("[-/\\s]+([TLP])\\s*$", RegexOption.IGNORE_CASE)

    /** Embedded-first, exactly as `resolveComponentType` in the web app. */
    private val ETH_TEXT = Regex("\\b(ETH|EMBEDDED\\s+THEORY)\\b", RegexOption.IGNORE_CASE)
    private val ELA_TEXT = Regex("\\b(ELA|EMBEDDED\\s+LAB(?:\\s+ARCHITECTURE)?)\\b", RegexOption.IGNORE_CASE)
    private val TO_TEXT = Regex("\\b(TO|THEORY\\s+ONLY)\\b", RegexOption.IGNORE_CASE)
    private val LO_TEXT = Regex("\\b(LO|LAB\\s+ONLY)\\b", RegexOption.IGNORE_CASE)

    private val CODE_TOKEN = Regex("\\b([A-Z]{2,}[0-9]{2,}[A-Z]*)\\b")
    private val TRAILING_COMPONENT_LETTER = Regex("^([A-Z]+[0-9]+)[LPT]$")
    private val BARE_COMPONENT_LETTER = Regex("\\b[A-Z]+[0-9]+[LP]\\b")

    /** A trailing `(T)`, `(L)`, `(P)`, `(E)`, `-T`, `-L`, `/L`, ... marker, upper-cased. */
    fun suffixOf(raw: String?): Char? {
        val text = raw.orEmpty()
        PAREN_SUFFIX.find(text)?.groupValues?.get(1)?.firstOrNull()?.let { return it.uppercaseChar() }
        DASH_SUFFIX.find(text)?.groupValues?.get(1)?.firstOrNull()?.let { return it.uppercaseChar() }
        return null
    }

    /**
     * The stable, bare course code. `BCSE101`, `BCSE101(T)`, `bcse101 - lab`,
     * `BCSE101L` and `BCSE101(L)` all resolve to `BCSE101`.
     */
    fun baseCode(raw: String?): String {
        val text = raw.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return ""

        val withoutSuffix = DASH_SUFFIX.replace(PAREN_SUFFIX.replace(text, "").trim(), "").trim()
        CODE_TOKEN.find(withoutSuffix.uppercase())?.let { m ->
            return stripTrailingComponentLetter(m.groupValues[1])
        }

        val cleaned = withoutSuffix
            .replace(ETH_TEXT, " ")
            .replace(ELA_TEXT, " ")
            .replace(TO_TEXT, " ")
            .replace(LO_TEXT, " ")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase()
        return cleaned.ifEmpty { withoutSuffix.uppercase() }
    }

    /** VTOP writes embedded labs as `BCSE101L` in some tables and `BCSE101(L)` in others. */
    private fun stripTrailingComponentLetter(code: String): String =
        TRAILING_COMPONENT_LETTER.find(code)?.groupValues?.get(1) ?: code

    /**
     * Classifies a row. Signal order is identity-critical:
     *  1. an explicit ETH/ELA/TO/LO token in the type or title
     *  2. a `(T)`/`(L)`/`-T`/`-L` marker on the code
     *  3. a bare `BCSE101L`-style code
     *  4. slot codes — weakest, and last, because a lab sharing a slot with its theory
     *     half would otherwise be read as theory and steal its marks
     */
    fun componentOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): Component {
        val words = listOfNotNull(typeHint, title, rawCode).filter { it.isNotBlank() }

        words.firstOrNull { ETH_TEXT.containsMatchIn(it) }?.let { return Component.ETH }
        words.firstOrNull { ELA_TEXT.containsMatchIn(it) }?.let { return Component.ELA }
        words.firstOrNull { TO_TEXT.containsMatchIn(it) }?.let { return Component.TO }
        words.firstOrNull { LO_TEXT.containsMatchIn(it) }?.let { return Component.LO }

        when (suffixOf(rawCode)) {
            'L', 'P' -> return Component.LO
            'T' -> return Component.TO
        }

        val code = rawCode.orEmpty().uppercase()
        if (BARE_COMPONENT_LETTER.containsMatchIn(code)) return Component.LO

        slot?.let { s ->
            val tokens = s.replace(Regex("[|,]"), "+").split("+")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.all { it.uppercase().startsWith("L") }) {
                return Component.LO
            }
        }

        return Component.UNKNOWN
    }

    /**
     * The theory/lab side of a row, from its code marker or its `type` — the two spellings
     * that used to be treated as different courses. Null when there is no signal at all.
     *
     * `Embedded Theory and Lab` is already one published row, so it is filed on the theory
     * side rather than split again.
     */
    fun halfOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): Half? {
        when (suffixOf(rawCode ?: title)) {
            'T' -> return Half.THEORY
            'L', 'P' -> return Half.LAB
        }
        val type = (typeHint ?: "").trim().lowercase()
        when (type) {
            "theory", "embedded theory and lab" -> return Half.THEORY
            "lab", "practical" -> return Half.LAB
        }
        return when (componentOf(rawCode, typeHint, slot, title)) {
            Component.ETH, Component.TO -> Half.THEORY
            Component.ELA, Component.LO -> Half.LAB
            Component.UNKNOWN -> null
        }
    }

    /** The key every consumer groups on: the bare base code, never a suffixed spelling. */
    fun canonicalKey(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): String = baseCode(rawCode)

    /** The legacy two-row key (`BASE` / `BASE(L)`) derived with the one canonical rule. */
    fun halfKey(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): String {
        val base = baseCode(rawCode)
        if (base.isEmpty()) return ""
        return if (halfOf(rawCode, typeHint, slot, title) == Half.LAB) "$base(L)" else base
    }

    /** True when two strings describe the same course, ignoring case, spacing and suffix. */
    fun sameCourse(a: String?, b: String?): Boolean {
        val x = baseCode(a)
        val y = baseCode(b)
        return x.isNotEmpty() && x == y
    }

    /**
     * Node's deliberately weak lab test (`isLabRow` in `AcademicMerge`): a type cell saying
     * "lab" or a slot starting with "L". This is not the embedded-vs-lab-only discriminator;
     * it only decides which side of a card a row belongs on.
     */
    fun isLabRow(courseType: String?, slot: String?): Boolean {
        if (courseType?.contains("lab", ignoreCase = true) == true) return true
        return slot.orEmpty().split("+").map { it.trim() }.firstOrNull()
            ?.startsWith("L", ignoreCase = true) == true
    }

    /** The display title of a linked pair: the theory half names the course, never "... Lab". */
    fun displayTitle(theoryTitle: String?, labTitle: String?, baseCode: String): String {
        val theory = theoryTitle?.trim().orEmpty()
        val lab = labTitle?.trim().orEmpty()
        val chosen = when {
            theory.isNotBlank() && !theory.equals(baseCode, ignoreCase = true) -> theory
            lab.isNotBlank() && !lab.equals(baseCode, ignoreCase = true) -> lab
            else -> baseCode
        }
        return chosen.ifBlank { baseCode }
    }

    /** The canonical `courseType` label for a component, or null when unclassified. */
    fun typeLabelOf(
        rawCode: String?,
        typeHint: String? = null,
        slot: String? = null,
        title: String? = null,
    ): String? = componentOf(rawCode, typeHint, slot, title).label.ifEmpty { null }
}
