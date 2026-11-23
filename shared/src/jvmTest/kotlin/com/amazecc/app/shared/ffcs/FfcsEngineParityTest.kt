package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-180 — acceptance tests for the ported pure generation engine.
 *
 * Golden behaviours come from `reference/04-generation-engine.md` §§1-6 and
 * the framework-free TS port's `ffcs.test.ts` generation section. The legacy
 * [FfcsEngine.generateTimetables] comparator is asserted separately: the old
 * `FfcsParityTest` results must survive the rewrite unchanged.
 */
class FfcsEngineParityTest {

    private fun offering(
        code: String,
        slot: String,
        faculty: String = "Dr. A",
        room: String = "AB1-101",
        type: String = "TH",
    ) = ParsedCourse(
        code = code,
        title = "Course $code",
        type = type,
        credits = "3",
        room = room,
        slot = slot,
        faculty = faculty,
    )

    private fun lock(
        code: String,
        allowedSlots: List<String> = emptyList(),
        allowedFaculty: List<String> = emptyList(),
        offerings: List<String> = emptyList(),
    ) = FfcsLock(code, code, allowedSlots, allowedFaculty, offerings)

    private fun request(
        courses: List<ParsedCourse>,
        locks: List<FfcsLock>,
        resultCap: Int? = 20,
        block: FfcsGenerationParams.() -> FfcsGenerationParams = { this },
    ): FfcsGenerationParams = FfcsGenerationParams(
        masterCourses = courses,
        courseLocks = locks,
        resultCap = resultCap,
    ).block()

    private fun layout(option: FfcsOption): String = option.timetable.courses
        .joinToString(" ") { "${it.code}[${it.slots.joinToString("+")}]" }

    private fun expectError(result: FfcsEngineResult, code: FfcsErrorCode): FfcsEngineResult.Error {
        assertTrue(result is FfcsEngineResult.Error, "expected error $code, got $result")
        assertEquals(code, result.code)
        return result
    }

    // ── typed error results ────────────────────────────────────────────────

    @Test
    fun no_courses_selected_isATypedError() = runTest {
        val result = FfcsEngine.generate(FfcsGenerationParams())
        expectError(result, FfcsErrorCode.NO_COURSES_SELECTED)
    }

    @Test
    fun no_valid_slots_carries_the_subject_code() = runTest {
        val course = offering("CSE1001", "A1")
        val result = FfcsEngine.generate(
            request(listOf(course), listOf(lock("CSE1001", allowedFaculty = listOf("NOBODY"))))
        )
        val error = expectError(result, FfcsErrorCode.NO_VALID_SLOTS)
        assertEquals("CSE1001", error.subjectCode)
    }

    @Test
    fun no_conflict_free_when_every_week_clashes() = runTest {
        val courses = listOf(offering("CSE1001", "A1"), offering("MAT1001", "A1"))
        val result = FfcsEngine.generate(request(courses, listOf(lock("CSE1001"), lock("MAT1001"))))
        expectError(result, FfcsErrorCode.NO_CONFLICT_FREE)
    }

    @Test
    fun below_min_half_days_rejects_a_week_that_clears_none() = runTest {
        // A1 is MON 08:00 and WED 08:55 → two occupied mornings, eight free halves.
        val course = offering("LABC101", "A1")
        val clear = FfcsEngine.generate(
            request(listOf(course), listOf(lock("LABC101")), block = { copy(minHalfDays = 8) })
        )
        assertTrue(clear is FfcsEngineResult.Ok, "a cleared floor keeps the week")
        assertEquals(8, clear.timetables.first().timetable.metrics.halfDays)

        val rejected = FfcsEngine.generate(
            request(listOf(course), listOf(lock("LABC101")), block = { copy(minHalfDays = 9) })
        )
        expectError(rejected, FfcsErrorCode.BELOW_MIN_HALF_DAYS)
    }

    @Test
    fun lab_rules_produce_their_own_typed_codes() = runTest {
        val monday = offering("LABM101", "L1", type = "LO")
        val alsoMonday = offering("LABN101", "L2", type = "LO")
        val otherDay = offering("LABN101", "L7", type = "LO")

        val plain = FfcsEngine.generate(request(listOf(monday, alsoMonday), listOf(lock("LABM101"), lock("LABN101"))))
        assertTrue(plain is FfcsEngineResult.Ok, "adjacent labs do not overlap")

        val separate = FfcsEngine.generate(
            request(
                listOf(monday, alsoMonday), listOf(lock("LABM101"), lock("LABN101")),
                block = { copy(separateLabDays = true) },
            )
        )
        expectError(separate, FfcsErrorCode.NO_SEPARATE_LABS)

        val differentDay = FfcsEngine.generate(
            request(
                listOf(monday, otherDay), listOf(lock("LABM101"), lock("LABN101")),
                block = { copy(separateLabDays = true) },
            )
        )
        assertTrue(differentDay is FfcsEngineResult.Ok, "Tuesday lab is a different day")

        val beforeLunch = offering("LABP101", "L6", type = "LO")
        val afterLunch = offering("LABQ101", "L31", type = "LO")
        val pairs = listOf(lock("LABP101"), lock("LABQ101"))
        val consecutive = FfcsEngine.generate(
            request(listOf(beforeLunch, afterLunch), pairs, block = { copy(avoidConsecutiveLabs = true) })
        )
        expectError(consecutive, FfcsErrorCode.NO_CONSECUTIVE_LABS)
        val splitByLunch = FfcsEngine.generate(
            request(
                listOf(beforeLunch, afterLunch), pairs,
                block = { copy(avoidConsecutiveLabs = true, lunchBreakSeparatesLabs = true) },
            )
        )
        assertTrue(splitByLunch is FfcsEngineResult.Ok, "the lunch marker separates the pair")
    }

    @Test
    fun faculty_score_gate_yields_below_min_faculty_scores() = runTest {
        val course = offering("CSE4001", "A1", faculty = "P A")
        val base = request(
            listOf(course), listOf(lock("CSE4001")),
            block = {
                copy(
                    facultyRatingsAvailable = true,
                    facultyRatings = mapOf(
                        "name:pa" to FacultyRating(wPct = 40.0, lecture = 3.0, da = 3.0, vibe = 3.0, reviews = 10),
                    ),
                )
            },
        )
        val rejected = FfcsEngine.generate(base.copy(facultyScoreFilters = FacultyScoreFilters(minWPct = 50.0)))
        expectError(rejected, FfcsErrorCode.BELOW_MIN_FACULTY_SCORES)
        val passed = FfcsEngine.generate(base.copy(facultyScoreFilters = FacultyScoreFilters(minWPct = 20.0)))
        assertTrue(passed is FfcsEngineResult.Ok)
        assertTrue((passed.timetables.first().facultyScore ?: 0.0) > 0.0)
    }

    // ── settled results ────────────────────────────────────────────────────

    @Test
    fun groupVariants_names_options_and_variants() = runTest {
        val offerings = listOf(
            offering("CSE1001", "A1", faculty = "P A", room = "AB1-101"),
            offering("CSE1001", "A1", faculty = "P B", room = "AB1-101"),
            offering("MAT1001", "B1", faculty = "Q A", room = "AB1-102"),
            offering("MAT1001", "B1", faculty = "Q B", room = "AB1-102"),
        )
        val result = FfcsEngine.generate(request(offerings, listOf(lock("CSE1001"), lock("MAT1001"))))
        assertTrue(result is FfcsEngineResult.Ok)
        assertEquals(1, result.timetables.size, "four weeks share one physical slot layout")
        assertEquals("Option 1", result.timetables.first().timetable.name)
        assertEquals(4, result.timetables.first().variants.size)
        assertEquals(
            listOf("Variant 1", "Variant 2", "Variant 3", "Variant 4"),
            result.timetables.first().variants.map { it.timetable.name },
        )
    }

    @Test
    fun result_cap_50_reports_capped_honestly() = runTest {
        val courses = (0 until 60).map { i -> offering("CSE1001", "A1+$i", faculty = "P $i") }
        val result = FfcsEngine.generate(
            request(courses, listOf(lock("CSE1001")), resultCap = 50)
        )
        assertTrue(result is FfcsEngineResult.Ok)
        assertEquals(50, result.timetables.size)
        assertTrue(result.capped, "the search stopped because it reached the finite limit")
        assertEquals(50, result.found)
    }

    @Test
    fun layouts_are_deterministic_without_an_rng_seed() = runTest {
        val courses = listOf(
            offering("CSE1001", "A1"), offering("CSE1001", "B1"),
            offering("MAT1001", "A2"), offering("MAT1001", "B2"),
        )
        val locks = listOf(lock("CSE1001"), lock("MAT1001"))
        val a = FfcsEngine.generate(request(courses, locks)) as FfcsEngineResult.Ok
        val b = FfcsEngine.generate(request(courses, locks)) as FfcsEngineResult.Ok
        assertEquals(a.timetables.map(::layout), b.timetables.map(::layout))
    }

    @Test
    fun result_order_equals_the_report_order_oracle_in_order() = runTest {
        // A1/B1/C1 each occupy two morning halves and have no gaps, so every
        // balanced score ties and the stable sort preserves DFS/report order.
        val courses = listOf(
            offering("CSE1001", "A1"), offering("CSE1001", "B1"), offering("CSE1001", "C1"),
        )
        val result = FfcsEngine.generate(request(courses, listOf(lock("CSE1001")))) as FfcsEngineResult.Ok

        // Independent oracle: one course, so exactly one offering is chosen;
        // enumerate in report order and reject an open-interval overlap through
        // the D-172 clash primitive. No ranking or scoring is reimplemented.
        val optionsForCourse = listOf(courses)
        val oracle = ArrayList<String>()
        fun oracleWalk(index: Int, chosen: List<ParsedCourse>, periods: List<SlotPeriod>) {
            if (index == optionsForCourse.size) {
                oracle += chosen.joinToString(" ") { "${it.code}[${FfcsSlotSchema.splitSlots(it.slot).joinToString("+")}]" }
                return
            }
            for (option in optionsForCourse[index]) {
                val windows = FfcsSlotSchema.splitSlots(option.slot).flatMap { FfcsSlotSchema.periodsForSlot(it) }
                if (FfcsSlotSchema.periodListsClash(windows, periods)) continue
                oracleWalk(index + 1, chosen + option, periods + windows)
            }
        }
        oracleWalk(0, emptyList(), emptyList())

        assertEquals(oracle, result.timetables.map(::layout))
    }

    @Test
    fun generate_does_not_mutate_its_parameters() = runTest {
        val courses = mutableListOf(
            offering("CSE1001", "A1"), offering("CSE1001", "B1"),
        )
        val locks = mutableListOf(lock("CSE1001"))
        val coursesBefore = courses.map { it.toString() }
        val locksBefore = locks.map { it.toString() }
        val params = request(courses, locks)

        FfcsEngine.generate(params)

        assertEquals(coursesBefore, courses.map { it.toString() })
        assertEquals(locksBefore, locks.map { it.toString() })
        assertEquals(2, params.masterCourses.size)
        assertEquals(1, params.courseLocks.size)
        assertFalse(params.noLimit)
    }

    @Test
    fun factor_list_and_legacy_blend_are_mutually_exclusive() = runTest {
        val courses = listOf(offering("CSE1001", "A1"), offering("MAT1001", "B1"))
        val locks = listOf(lock("CSE1001"), lock("MAT1001"))
        val factors = RankingFactorsConfig(order = listOf(RankingFactor.GAPS, RankingFactor.HALFDAYS))
        val onlyFactors = FfcsEngine.generate(
            request(courses, locks, block = { copy(rankingFactors = factors) })
        ) as FfcsEngineResult.Ok
        val bothRankings = FfcsEngine.generate(
            request(
                courses, locks,
                block = { copy(rankingFactors = factors, rankingMode = RankingMode.BEST_FACULTY) },
            )
        ) as FfcsEngineResult.Ok
        assertEquals(
            onlyFactors.timetables.map { it.rankingScore },
            bothRankings.timetables.map { it.rankingScore },
            "a present factor list supersedes the legacy profile entirely",
        )
        assertNotNull(onlyFactors.timetables.first().rankingScore)
    }

    @Test
    fun factor_only_config_reorders_results_by_ranking_score() = runTest {
        // Two offerings per course: A1/B1 are morning slots, A2/B2 afternoon.
        // Every combination has the same six free half-days and no gaps, so the
        // balanced base sort is tied and keeps DFS/report order:
        //   [A1,B1], [A1,B2], [A2,B1], [A2,B2].
        // With only the eight-factor config set (no rankingMode, no faculty
        // ratings, no soft preference), the ranking-sort gate must still fire:
        // LATE_START scores the afternoon weeks ~100, the mixed weeks ~56.3 and
        // the all-morning week ~4.9, so the order reverses.
        val courses = listOf(
            offering("CSE1001", "A1"), offering("CSE1001", "A2"),
            offering("MAT1001", "B1"), offering("MAT1001", "B2"),
        )
        val locks = listOf(lock("CSE1001"), lock("MAT1001"))
        val result = FfcsEngine.generate(
            request(courses, locks, block = {
                copy(rankingFactors = RankingFactorsConfig(order = listOf(RankingFactor.LATE_START)))
            })
        ) as FfcsEngineResult.Ok

        assertEquals(
            listOf(
                "CSE1001[A2] MAT1001[B2]",
                "CSE1001[A1] MAT1001[B2]",
                "CSE1001[A2] MAT1001[B1]",
                "CSE1001[A1] MAT1001[B1]",
            ),
            result.timetables.map(::layout),
            "a factor-only run must sort by rankingScore even with no legacy ranking field set",
        )
        assertTrue(result.timetables.first().rankingScore!! > result.timetables.last().rankingScore!!)
    }

    // ── the legacy comparator must still produce the old parity results ────

    @Test
    fun legacy_closeGaps_sort_still_ranks_gap_free_first() = runTest {
        val ranked = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(
                listOf(offering("CSE1001", "L1+L3"), offering("CSE1001", "A1+F1")),
            ),
            options = FfcsEngine.GenerateOptions(closeGaps = true),
        )
        assertEquals(2, ranked.size)
        assertEquals(0, ranked.first().metrics.gaps)
        assertTrue(ranked.any { it.metrics.gaps > 0 })
    }

    @Test
    fun legacy_lateStart_and_earlyFinish_sorts_are_unchanged() = runTest {
        val late = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(listOf(offering("CSE1001", "A1"), offering("CSE1001", "A2"))),
            options = FfcsEngine.GenerateOptions(lateStart = true),
        )
        assertEquals(2, late.size)
        assertTrue(late.first().firstStartMin > late.last().firstStartMin)

        val early = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(listOf(offering("CSE1001", "A2"), offering("CSE1001", "A1"))),
            options = FfcsEngine.GenerateOptions(earlyFinish = true),
        )
        assertEquals(2, early.size)
        assertTrue(early.first().lastEndMin < early.last().lastEndMin)
    }
}
