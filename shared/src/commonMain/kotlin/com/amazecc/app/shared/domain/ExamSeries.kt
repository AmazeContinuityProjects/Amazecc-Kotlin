package com.amazecc.app.shared.domain

/**
 * Exam series names, canonicalised.
 *
 * The same series reaches this app spelled several ways. The academic calendar
 * writes `"CAT - II"`, the exam schedule has been seen with both `"CAT II"` and
 * `"CAT2"` as keys, and a milestone can turn up as `"Continuous Assessment
 * Test 2"`. All of them name one thing.
 *
 * Left alone, that produces two visible events for one exam: a milestone row
 * reading "CAT II" beside a paper row reading "CAT2". The cause is the
 * punctuation, because stripping `"- "` from `"CAT - II"` leaves `"cat   ii"`
 * with three spaces, and every name is compared as a substring.
 *
 * So: lowercase, strip punctuation, collapse whitespace, rewrite the numeral to
 * an arabic digit, and drop all remaining spaces. `"CAT - II"`, `"CAT II"`,
 * `"CAT-2"`, `"CAT2"` and `"CATII"` all land on `"cat2"`.
 *
 * Ported from `../AmazeCC/src/lib/examSeries.ts`, which is self-contained on
 * purpose - `analyzeCalendar` and `calendarDay` both need it and neither should
 * have to import the other to get it. Here that role is played by putting it in
 * its own file for the same reason.
 */
object ExamSeries {

    private val ROMAN = mapOf(
        "i" to "1",
        "ii" to "2",
        "iii" to "3",
        "iv" to "4",
        "v" to "5",
    )

    /**
     * Lower-case, strip punctuation, collapse whitespace.
     *
     * The one normaliser. `CalendarClassifier.normalize` is this function under
     * its older name, because every holiday and day-type keyword is matched
     * against the output and two copies of a rule that decides whether a working
     * day is a day off is two chances to disagree.
     */
    fun looseNormalise(raw: String?): String =
        (raw ?: "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun canonicalSeriesName(raw: String?): String {
        var s = looseNormalise(raw)
        if (s.isEmpty()) return ""

        // A standalone roman numeral: "cat i" -> "cat 1". Alternation order is
        // load-bearing - `iii` has to be tried before `i`, or "cat iii" becomes
        // "cat 1ii".
        s = Regex("\\b(iii|ii|iv|i|v)\\b").replace(s) { m -> ROMAN[m.value] ?: m.value }

        // An attached one: "catii" -> "cat2". Only runs of two or more, so a
        // word that merely ends in "i" ("aarti") is left alone.
        s = Regex("([a-z])(iii|ii|iv)$").replace(s) { m ->
            m.groupValues[1] + (ROMAN[m.groupValues[2]] ?: m.groupValues[2])
        }

        return s.replace(Regex("\\s+"), "")
    }

    /** Whether two spellings name the same series. */
    fun sameSeries(a: String?, b: String?): Boolean {
        val ca = canonicalSeriesName(a)
        return ca.isNotEmpty() && ca == canonicalSeriesName(b)
    }

    /**
     * The longest canonical name that is a substring of [text].
     *
     * Length-wins rather than first-wins. `"cat1"` is not a substring of
     * `"cat2"`, so the CAT I / CAT II collision that a plain scan gets wrong
     * cannot happen here - but `"lidforlab"` is a substring of
     * `"lidforlaboratoryclasses"`, and the specific one is the correct answer.
     */
    fun longestCanonicalMatch(text: String?, candidates: List<String>): String? {
        val t = canonicalSeriesName(text)
        if (t.isEmpty()) return null
        var best: String? = null
        var bestLen = 0
        for (candidate in candidates) {
            val c = canonicalSeriesName(candidate)
            if (c.isNotEmpty() && t.contains(c) && c.length > bestLen) {
                best = candidate
                bestLen = c.length
            }
        }
        return best
    }
}
