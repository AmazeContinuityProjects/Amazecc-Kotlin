package com.amazecc.app.shared.ffcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-174: faculty-priority sanitising/scoring, stable option order and Vitfac aggregation. */
class FfcsFacultyPriorityTest {

    private fun assertDouble(expected: Double, actual: Double) =
        assertEquals(expected, actual, 1e-9)

    private data class Option(val name: String, val faculty: String)

    private fun reportOptions() = listOf("D", "B", "A", "C").map { Option(it, "Prof $it") }

    private fun abPriority() = mapOf("CSE1001" to listOf("Prof A", "Prof B"))

    // ─── splitting / sanitising ─────────────────────────────────────────────

    @Test
    fun splitFacultyNames_splitsOnSlashTrimsAndDropsEmpties() {
        assertEquals(listOf("Dr. A", "Dr. B"), splitFacultyNames(" Dr. A /  / Dr. B "))
        assertEquals(listOf("Dr. A"), splitFacultyNames("Dr. A"))
        assertEquals(emptyList<String>(), splitFacultyNames(null))
        assertEquals(emptyList<String>(), splitFacultyNames(" /   / "))
    }

    @Test
    fun sanitizeFacultyPriority_dedupesCaseInsensitivelyKeepingFirst() {
        // Golden from the drift register: "Prof A" wins over its casing variants.
        val raw = mapOf<String, Any?>("CSE1001" to listOf("Prof A", "PROF A", "prof a"))
        assertEquals(mapOf("CSE1001" to listOf("Prof A")), sanitizeFacultyPriority(raw))
    }

    @Test
    fun sanitizeFacultyPriority_fallsBackPerEntryKeepsUnknownCodesDropsMalformed() {
        val raw = mapOf<String, Any?>(
            "CSE1001" to listOf("Prof A", "PROF A", "prof a"),
            "  MATH1001  " to listOf("  Dr. X  ", "", 42, "dr. x", "Dr. Y"),
            "UNKNOWN_CODE" to listOf("Someone"),
            "EMPTY" to emptyList<String>(),
            "BAD" to "not a list",
            "" to listOf("Ghost"),
        )
        assertEquals(
            mapOf(
                "CSE1001" to listOf("Prof A"),
                "MATH1001" to listOf("Dr. X", "Dr. Y"),
                "UNKNOWN_CODE" to listOf("Someone"),
            ),
            sanitizeFacultyPriority(raw),
        )
        assertEquals(emptyMap<String, List<String>>(), sanitizeFacultyPriority("not a map"))
        assertEquals(emptyMap<String, List<String>>(), sanitizeFacultyPriority(null))
        assertEquals(emptyMap<String, List<String>>(), sanitizeFacultyPriority(listOf("CSE1001")))
    }

    // ─── null vs 0 ──────────────────────────────────────────────────────────

    @Test
    fun facultyPreferenceScore_noOrderingIsNullNeverZero() {
        assertNull(facultyPreferenceScore(null, "Dr. Rao"))
        assertNull(facultyPreferenceScore(emptyList(), "Dr. Rao"))
    }

    @Test
    fun facultyPreferenceScore_listedCourseWithAbsentFacultyIsZero() {
        assertEquals(0.0, facultyPreferenceScore(listOf("Dr. Rao", "Dr. Menon"), "Dr. Unknown"))
        assertEquals(0.0, facultyPreferenceScore(listOf("Dr. Rao"), ""))
    }

    @Test
    fun facultyPreferenceScore_rankScoreAndCombinedFacultyCell() {
        val priority = listOf("Dr. Rao", "Dr. Menon", "Prof. Iyer")
        assertDouble(1.0, facultyPreferenceScore(priority, "Dr. Rao / Dr. Smith")!!)
        assertDouble(2.0 / 3.0, facultyPreferenceScore(priority, "Dr. Menon")!!)
        assertDouble(1.0 / 3.0, facultyPreferenceScore(priority, "Prof. Iyer")!!)
        assertDouble(1.0, facultyPreferenceScore(listOf("prof a"), "PROF A")!!)
    }

    @Test
    fun timetablePreferenceScore_averagesOnlyRankedCourses() {
        val map = mapOf(
            "CSE1001" to listOf("Dr. Rao", "Dr. Menon"),
            "MATH1001" to listOf("Dr. X"),
        )
        val courses = listOf(
            FacultyPreferenceCourse("CSE1001", "Dr. Menon"), // 0.5
            FacultyPreferenceCourse("MATH1001", "Dr. X"),    // 1.0
            FacultyPreferenceCourse("PHY1001", "Dr. Nobody") // null — excluded
        )
        assertDouble(0.75, timetablePreferenceScore(courses, map)!!)
        assertNull(timetablePreferenceScore(courses, null))
        assertNull(
            timetablePreferenceScore(listOf(FacultyPreferenceCourse("PHY1001", "X")), map)
        )
    }

    // ─── stable option order / never filters / precedence ───────────────────

    @Test
    fun sortOptionsByFacultyPriority_stableGoldenOrder() {
        val sorted = sortOptionsByFacultyPriority(reportOptions(), abPriority(), "CSE1001") { it.faculty }
        assertEquals(listOf("A", "B", "D", "C"), sorted.map { it.name })
        assertEquals(listOf("Prof A", "Prof B", "Prof D", "Prof C"), sorted.map { it.faculty })
    }

    @Test
    fun sortOptionsByFacultyPriority_neverFiltersOptions() {
        val options = reportOptions()
        assertEquals(options, sortOptionsByFacultyPriority(options, null, "CSE1001") { it.faculty })
        assertEquals(
            options,
            sortOptionsByFacultyPriority(options, emptyMap(), "CSE1001") { it.faculty },
        )
        // A priority with no matching faculty still keeps every option, in report order.
        val noMatch = sortOptionsByFacultyPriority(
            options, mapOf("CSE1001" to listOf("Nobody")), "CSE1001"
        ) { it.faculty }
        assertEquals(4, noMatch.size)
        assertEquals(options, noMatch)
    }

    @Test
    fun sortOptionsByFacultyPriority_lockFilterRunsBeforeRanking() {
        // The lock removes "Prof A" first; ranking then only ever sees the survivors.
        val allowedByLock = setOf("Prof B", "Prof D", "Prof C")
        val lockedOptions = reportOptions().filter { it.faculty in allowedByLock }
        val ranked = sortOptionsByFacultyPriority(lockedOptions, abPriority(), "CSE1001") { it.faculty }

        assertEquals(listOf("B", "D", "C"), ranked.map { it.name })
        assertEquals(lockedOptions.toSet(), ranked.toSet(), "ranking must not resurrect a locked-out option")
        assertFalse(ranked.any { it.faculty == "Prof A" })
    }

    @Test
    fun hasFacultyPriority_reflectsUsableOrderings() {
        assertFalse(hasFacultyPriority(null))
        assertFalse(hasFacultyPriority(emptyMap()))
        assertTrue(hasFacultyPriority(abPriority()))
    }

    // ─── name keys ──────────────────────────────────────────────────────────

    @Test
    fun facultyIdentity_normalizesAndPrefersEmployeeId() {
        assertEquals(listOf("id:E1", "name:rao"), facultyRatingKeys(FacultyRatingPerson(" E1 ", "Dr. Rao")))
        assertEquals(listOf("name:rao"), facultyRatingKeys(FacultyRatingPerson(null, "Prof. Rao")))
        assertEquals(emptyList<String>(), facultyRatingKeys(FacultyRatingPerson(null, "")))
        assertEquals("RAO", normalizeFacultyName("Dr. Rao (VIT)"))
        assertEquals("RAO", facultyGroupKey("Dr. Rao"))
        assertEquals(facultyGroupKey("K. Rao"), facultyGroupKey("Rao K."))
    }

    // ─── Vitfac aggregation ─────────────────────────────────────────────────

    @Test
    fun buildFacultyRatingLookup_clampsScoresAndSkipsMissingRatings() {
        val master = listOf(FacultyRatingPerson("E1", "Dr. Rao"), FacultyRatingPerson(null, "Dr. Menon"))
        val lookup = buildFacultyRatingLookup(master) { person ->
            if (person.employeeId == "E1") {
                FacultyRating(wPct = 150.0, lecture = 9.0, da = -2.0, vibe = 4.0, reviews = -5)
            } else {
                FacultyRating(wPct = 80.0, lecture = 4.0, da = 3.0, vibe = 5.0, reviews = 10)
            }
        }
        assertEquals(setOf("id:E1", "name:rao", "name:menon"), lookup.keys)
        val clamped = lookup.getValue("id:E1")
        assertDouble(100.0, clamped.wPct!!)
        assertDouble(5.0, clamped.lecture!!)
        assertDouble(0.0, clamped.da!!)
        assertEquals(0, clamped.reviews)

        val missing = buildFacultyRatingLookup(master) { person ->
            if (person.employeeId == "E1") FacultyRating() else null
        }
        assertEquals(setOf("id:E1", "name:rao"), missing.keys, "no rating must never invent an entry")
    }

    @Test
    fun facultyRankingSummary_withoutRatingsReturnsNullsNotZeros() {
        val summary = facultyRankingSummary(listOf(RatedCourseRef("CSE1001", "Dr. Nobody")), emptyMap())
        assertNull(summary.averageWPct)
        assertNull(summary.averageLecture)
        assertNull(summary.averageDa)
        assertNull(summary.averageVibe)
        assertNull(summary.facultyScore)
        assertEquals(0, summary.ratedFacultyCount)
        assertEquals(1, summary.unratedFacultyCount)
        assertEquals(1, summary.totalFacultyCount)
        assertNotNull(summary.courses.single().also { assertNull(it.rating) })

        val empty = facultyRankingSummary(emptyList<RatedCourseRef>(), emptyMap())
        assertTrue(empty.courses.isEmpty())
        assertEquals(0, empty.totalFacultyCount)
        assertNull(empty.facultyScore)
        assertTrue(passesFacultyScoreFilters(empty, FacultyScoreFilters(minWPct = 90.0)))
    }

    @Test
    fun facultyRankingSummary_usesDocumentedWeightsAndCoverage() {
        val lookup = mapOf("name:rao" to FacultyRating(80.0, 4.0, 3.0, 5.0, 10))
        val courses = listOf(RatedCourseRef("CSE1001", "Dr. Rao"), RatedCourseRef("MATH1001", "Dr. Unknown"))
        val summary = facultyRankingSummary(courses, lookup)

        assertDouble(80.0, summary.averageWPct!!)
        assertDouble(4.0, summary.averageLecture!!)
        assertDouble(3.0, summary.averageDa!!)
        assertDouble(5.0, summary.averageVibe!!)
        assertEquals(1, summary.ratedFacultyCount)
        assertEquals(1, summary.unratedFacultyCount)
        assertEquals(2, summary.totalFacultyCount)
        // Ratings 4:2:2:2 on 0.8/0.8/0.6/1.0 => 80; coverage 1/2 => 40.
        assertDouble(40.0, summary.facultyScore!!)

        val reweighted = facultyRankingSummary(
            courses, lookup, FacultyMetricWeights(wPct = 0.0, lecture = 1.0, da = 1.0, vibe = 0.0)
        )
        assertDouble(35.0, reweighted.facultyScore!!)
    }

    @Test
    fun facultyRankingSummary_weightsAveragesByClampedReviewCount() {
        val lookup = mapOf(
            "name:rao" to FacultyRating(100.0, 5.0, 5.0, 5.0, 1),
            "name:menon" to FacultyRating(0.0, 0.0, 0.0, 0.0, 20),
        )
        val summary = facultyRankingSummary(
            listOf(RatedCourseRef("C1", "Dr. Rao"), RatedCourseRef("C2", "Dr. Menon")), lookup
        )
        assertDouble(100.0 / 21.0, summary.averageWPct!!)
        assertDouble(100.0 / 21.0, summary.facultyScore!!)
    }

    @Test
    fun passesFacultyScoreFilters_activeMinimumsIgnoreAbsentScores() {
        val lookup = mapOf(
            "name:rao" to FacultyRating(90.0, 4.0, 4.0, 4.0, 10),
            "name:menon" to FacultyRating(40.0, 2.0, 2.0, 2.0, 5),
        )
        val summary = facultyRankingSummary(
            listOf(RatedCourseRef("C1", "Dr. Rao"), RatedCourseRef("C2", "Dr. Menon")), lookup
        )
        assertTrue(passesFacultyScoreFilters(summary, FacultyScoreFilters.DEFAULT))
        assertFalse(passesFacultyScoreFilters(summary, FacultyScoreFilters(minWPct = 50.0)))
        assertFalse(passesFacultyScoreFilters(summary, FacultyScoreFilters(minLecture = 3.0)))
        assertFalse(passesFacultyScoreFilters(summary, FacultyScoreFilters(minDa = 3.0)))
        assertFalse(passesFacultyScoreFilters(summary, FacultyScoreFilters(minVibe = 3.0)))

        val withNullMetric = summary.copy(
            courses = listOf(
                summary.courses[0],
                summary.courses[1].copy(rating = summary.courses[1].rating!!.copy(wPct = null)),
            ),
        )
        assertTrue(passesFacultyScoreFilters(withNullMetric, FacultyScoreFilters(minWPct = 50.0)))
    }
}
