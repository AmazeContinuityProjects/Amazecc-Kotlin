package com.amazecc.app.shared.domain

/**
 * Port of AmazeCC's `src/lib/milestoneDates.ts` (with its `analyzeCalendar` milestone
 * matcher). The predictor's four ceilings all come from the canonical `importantEvents`
 * keys, never from a substring of a display string: `"cat i"` is a prefix of `"cat ii"`,
 * so a naive match made the answer depend on which month arrived first.
 *
 * All dates are epoch-millis at local midnight, the same unit [AttendancePredictor]
 * compares against grid cells. No particular semester's dates live here.
 */

private val FULL_MONTH_NAMES = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)

private val MONTH_ABBREVIATIONS = listOf(
    "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "dec",
)

/** Canonical keys from `analyzeCalendar.IMPORTANT_EVENTS`, in the web's order. */
private val IMPORTANT_EVENT_KEYS = listOf(
    "cat i",
    "cat ii",
    "lid for laboratory classes",
    "lid for theory classes",
    "mid term test",
)

/** Web aliases, searched alongside the canonical keys. */
private val IMPORTANT_EVENT_ALIASES = mapOf(
    "lid for laboratory classes" to listOf("lid for lab"),
)

private val ALL_NEEDLES = IMPORTANT_EVENT_KEYS + IMPORTANT_EVENT_ALIASES.values.flatten()

private val ROMAN = mapOf("iii" to "3", "ii" to "2", "iv" to "4", "i" to "1", "v" to "5")
private val STANDALONE_ROMAN = Regex("""\b(iii|ii|iv|i|v)\b""")
private val ATTACHED_ROMAN = Regex("([a-z])(iii|ii|iv)$")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9\\s]")
private val WHITESPACE = Regex("\\s+")

/** `looseNormalise` from `examSeries.ts`: lowercase, punctuation to spaces, collapse runs. */
private fun looseNormalise(raw: String?): String =
    raw.orEmpty().lowercase()
        .let { NON_ALPHANUMERIC.replace(it, " ") }
        .let { WHITESPACE.replace(it, " ") }
        .trim()

/** `canonicalSeriesName` from `examSeries.ts`: `"CAT - I"` and `"CAT I"` both become `"cat1"`. */
private fun canonicalSeriesName(raw: String?): String {
    var s = looseNormalise(raw)
    if (s.isEmpty()) return ""
    s = STANDALONE_ROMAN.replace(s) { ROMAN[it.value] ?: it.value }
    s = ATTACHED_ROMAN.replace(s) { m -> m.groupValues[1] + (ROMAN[m.groupValues[2]] ?: m.groupValues[2]) }
    return WHITESPACE.replace(s, "")
}

/** `longestCanonicalMatch`: length-wins, so `"lidforlab"` cannot beat `"lidforlaboratoryclasses"`. */
private fun longestCanonicalMatch(text: String?): String? {
    val normalised = canonicalSeriesName(text)
    if (normalised.isEmpty()) return null
    var best: String? = null
    var bestLength = 0
    for (needle in ALL_NEEDLES) {
        val candidate = canonicalSeriesName(needle)
        if (candidate.isNotEmpty() && normalised.contains(candidate) && candidate.length > bestLength) {
            best = needle
            bestLength = candidate.length
        }
    }
    return best
}

private fun matchImportantText(text: String?): String? {
    if (looseNormalise(text).isEmpty()) return null
    val best = longestCanonicalMatch(text) ?: return null
    return IMPORTANT_EVENT_KEYS.firstOrNull { key ->
        key == best || IMPORTANT_EVENT_ALIASES[key]?.contains(best) == true
    }
}

/**
 * `matchImportantEvent`: the fields are searched separately, never concatenated, so a
 * `text` of "CAT" and a `category` of "II" cannot invent a CAT II VTOP never wrote.
 * Both stored shapes are accepted — VTOP's text/category and the enriched title/detail.
 */
private fun matchImportantEvent(event: PredictorDayEvent): String? =
    matchImportantText(event.text)
        ?: matchImportantText(event.category)
        ?: matchImportantText(event.title)
        ?: matchImportantText(event.detail)

/** `year` (first) and zero-based month (second), mirroring `analyzeCalendar`'s label parsing. */
private fun parseMonthYear(label: String): Pair<Int, Int> {
    val now = today()
    val trimmed = label.trim()
    val lower = trimmed.lowercase()
    val year = trimmed.split(WHITESPACE).lastOrNull()?.toIntOrNull() ?: now.year
    val month0 = FULL_MONTH_NAMES.indexOfFirst { lower.contains(it) }
        .takeIf { it >= 0 }
        ?: MONTH_ABBREVIATIONS.indexOfFirst { lower.contains(it) }
            .takeIf { it >= 0 }
        ?: (now.monthNumber - 1)
    return year to month0
}

/**
 * Pull the four predictor milestones out of a raw calendar payload's months.
 *
 * Empty input resolves to all-null dates: the predictor reads that as "no ceiling and
 * no lock", not as an error. Within and across months the **first** match per key wins,
 * which is what makes CAT I the first day of a multi-day exam window.
 */
fun buildMilestoneDates(calendarMonths: List<CalendarMonth>?): MilestoneDates {
    if (calendarMonths.isNullOrEmpty()) return MilestoneDates()
    val found = mutableMapOf<String, Long>()
    for (month in calendarMonths) {
        val (year, month0) = parseMonthYear(month.label)
        // VTOP's day list is date-major in the web analyser; sort so a shuffled payload
        // cannot change which day of a multi-day milestone is kept.
        for (day in month.days.sortedBy { it.date }) {
            for (event in day.events) {
                val key = matchImportantEvent(
                    PredictorDayEvent(text = event.text, category = event.category)
                ) ?: continue
                if (found.containsKey(key)) continue
                found[key] = AttendancePredictor.makeTimestamp(year, month0, day.date)
            }
        }
    }
    return MilestoneDates(
        cat1Date = found["cat i"],
        cat2Date = found["cat ii"],
        lidLabDate = found["lid for laboratory classes"],
        lidTheoryDate = found["lid for theory classes"],
    )
}

