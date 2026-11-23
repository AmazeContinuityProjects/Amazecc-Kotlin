package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.RankingComponents
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-173 — pins the byte-faithful port of `ffcsRanking.ts` against the golden
 * vectors in the ffcs-planner skill's `assets/golden-vectors.json` and the
 * repo's own `ffcsRanking.test.ts` / cross-stack parity score.
 */
class FfcsRankingTest {

    private fun assertBreakdown(expected: Map<String, Double>, actual: Map<String, Double>) {
        assertEquals(expected.keys, actual.keys, "breakdown keys")
        expected.forEach { (key, value) ->
            assertEquals(value, actual.getValue(key), 1e-9, "breakdown[$key]")
        }
    }

    /** JS `Number(x.toFixed(1))`, used as an independent oracle for the asymmetry. */
    private fun jsRound1(value: Double): Double = floor(value * 10.0 + 0.5) / 10.0

    // ── profiles ───────────────────────────────────────────────────────────

    @Test
    fun legacyProfiles_haveTheExactSixWeightTables() {
        assertEquals(
            listOf("Best Faculty", "Balanced", "Half Days", "Minimize Gaps", "Early Finish", "Late Start"),
            RANKING_MODES.map { it.value },
        )
        assertEquals(ProfileWeights(2.0, 2.0, 1.0, 1.0, 1.0, 45.0), profileFor(RankingMode.BEST_FACULTY))
        assertEquals(ProfileWeights(10.0, 14.0, 10.0, 8.0, 8.0), profileFor(RankingMode.BALANCED))
        assertEquals(ProfileWeights(30.0, 10.0, 8.0, 4.0, 3.0), profileFor(RankingMode.HALF_DAYS))
        assertEquals(ProfileWeights(6.0, 32.0, 6.0, 3.0, 8.0), profileFor(RankingMode.MINIMIZE_GAPS))
        assertEquals(ProfileWeights(8.0, 10.0, 30.0, 2.0, 4.0), profileFor(RankingMode.EARLY_FINISH))
        assertEquals(ProfileWeights(8.0, 8.0, 4.0, 30.0, 4.0), profileFor(RankingMode.LATE_START))
    }

    @Test
    fun asRankingMode_isSafeForNullAndUnknownValues() {
        assertNull(asRankingMode(null))
        assertNull(asRankingMode(""))
        assertNull(asRankingMode("Half-Day Focused"))
        assertNull(asRankingMode("balanced")) // exact wire value, not a guess
        assertNull(asRankingMode(42))
        assertEquals(RankingMode.BEST_FACULTY, asRankingMode("Best Faculty"))
        assertEquals(RankingMode.BALANCED, asRankingMode(RankingMode.BALANCED))
        assertEquals(profileFor(RankingMode.BALANCED), profileFor(null))
    }

    // ── reading a week ─────────────────────────────────────────────────────

    @Test
    fun rankingInputs_usesTheGoldenConstantsAndValues() {
        assertEquals(240, COMPACT_DAY_MAX_MIN)
        assertEquals(20, GAP_NOT_COUNTED_MIN)
        assertEquals(55, PERIOD_MIN)

        // TB1 = Mon 10:45-11:35 (645-695): one compact day, no gaps.
        val one = rankingInputs(listOf(listOf(ClassMeeting(645, 695))))
        assertEquals(1, one.activeDays)
        assertEquals(1, one.compactDays)
        assertEquals(645.0, one.averageDayStartMinutes, 1e-9)
        assertEquals(695.0, one.averageDayEndMinutes, 1e-9)
        assertEquals(0, one.totalGapSlots)
        assertEquals(0.0, one.dailyLoadVariance, 1e-9)

        // TB1 + TG2 on the same day spans 465 min (> 4h) and leaves a 365 min gap.
        val longDay = rankingInputs(listOf(listOf(ClassMeeting(645, 695), ClassMeeting(1060, 1110))))
        assertEquals(1, longDay.activeDays)
        assertEquals(0, longDay.compactDays, "compactDays is tight class days, not free half-days")
        assertEquals(7, longDay.totalGapSlots) // round(365 / 55)

        val empty = rankingInputs(emptyList())
        assertEquals(0, empty.activeDays)
        assertEquals(0.0, empty.averageDayStartMinutes, 1e-9)
        assertEquals(0.0, empty.averageDayEndMinutes, 1e-9)
        assertEquals(0.0, empty.dailyLoadVariance, 1e-9)
    }

    @Test
    fun rankingInputs_ignoresShortGapsAndCountsRemainingGapSlots() {
        val days = listOf(
            listOf(ClassMeeting(0, 100), ClassMeeting(120, 150)), // gap 20 -> changeover
            listOf(ClassMeeting(0, 100), ClassMeeting(121, 150)), // gap 21 -> max(1, round(21/55)) = 1
            listOf(ClassMeeting(0, 100), ClassMeeting(210, 250)), // gap 110 -> round(110/55) = 2
        )
        assertEquals(3, rankingInputs(days).totalGapSlots)
    }

    @Test
    fun rankingInputs_varianceIsPopulationOverActiveDaysOnly() {
        val inputs = rankingInputs(
            listOf(
                listOf(ClassMeeting(0, 50)),                                               // 1 class
                listOf(ClassMeeting(0, 50), ClassMeeting(55, 100), ClassMeeting(105, 150)), // 3 classes
                emptyList(),                                                               // not an active day
            ),
        )
        assertEquals(2, inputs.activeDays)
        // counts [1, 3]: mean 2, population variance ((1)^2 + (1)^2) / 2 = 1.
        assertEquals(1.0, inputs.dailyLoadVariance, 1e-9)
    }

    // ── legacy profile scoring ─────────────────────────────────────────────

    @Test
    fun scoreRanking_accumulatesRoundedBreakdownEntries_LegacyAsymmetry() {
        val inputs = RankingInputs(
            activeDays = 1,
            compactDays = 0,
            totalGapSlots = 0,
            averageDayStartMinutes = 480.0,
            averageDayEndMinutes = 530.0,
            dailyLoadVariance = 0.0,
        )
        val result = scoreRanking(inputs, RankingMode.BEST_FACULTY)
        assertEquals(57.2, result.score, 1e-9)
        assertBreakdown(
            mapOf(
                "compactDays" to 0.0,
                "lowGaps" to 28.6,
                "earlyFinish" to 14.3,
                "lateStart" to 0.0,
                "balancedLoad" to 14.3,
            ),
            result.breakdown,
        )
        assertEquals(result.breakdown.values.sum(), result.score, 1e-9)

        // The same points summed RAW and rounded once would be 57.1, not 57.2.
        val rawPoints = 100.0 * 2.0 / 7.0 + 100.0 * 1.0 / 7.0 + 100.0 * 1.0 / 7.0
        assertEquals(57.1, jsRound1(rawPoints), 1e-9)
        assertFalse(result.score == jsRound1(rawPoints))
    }

    @Test
    fun scoreRanking_staysWithinZeroAndHundredAndTreatsNullAsBalanced() {
        val emptyWeek = rankingInputs(emptyList())
        for (mode in RANKING_MODES) {
            val result = scoreRanking(emptyWeek, mode)
            assertTrue(result.score in 0.0..100.0, "$mode score must be 0..100")
            assertTrue(result.breakdown.values.all { it in 0.0..100.0 }, "$mode breakdown must be 0..100")
        }
        val week = rankingInputs(listOf(listOf(ClassMeeting(645, 695))))
        assertEquals(scoreRanking(week, RankingMode.BALANCED), scoreRanking(week, null))
    }

    // ── eight-factor path ──────────────────────────────────────────────────

    @Test
    fun combineFactorScores_matchesTheGoldenVectors_60_55_70_0() {
        val scores = mapOf(
            RankingFactor.GAPS to 80.0,
            RankingFactor.HALFDAYS to 50.0,
            RankingFactor.SOCIAL to 20.0,
        )
        val canonical = listOf(RankingFactor.GAPS, RankingFactor.HALFDAYS, RankingFactor.SOCIAL)

        val first = combineFactorScores(scores, canonical)
        assertEquals(60.0, first.score, 1e-9)
        assertBreakdown(mapOf("gaps" to 40.0, "halfdays" to 16.7, "social" to 3.3), first.breakdown)

        val reversed = combineFactorScores(scores, listOf(RankingFactor.HALFDAYS, RankingFactor.GAPS, RankingFactor.SOCIAL))
        assertEquals(55.0, reversed.score, 1e-9)
        assertBreakdown(mapOf("halfdays" to 25.0, "gaps" to 26.7, "social" to 3.3), reversed.breakdown)

        val missingSocial = combineFactorScores(
            mapOf(RankingFactor.GAPS to 80.0, RankingFactor.HALFDAYS to 50.0),
            canonical,
        )
        assertEquals(70.0, missingSocial.score, 1e-9)
        assertBreakdown(mapOf("gaps" to 53.3, "halfdays" to 16.7), missingSocial.breakdown)

        val empty = combineFactorScores(emptyMap<RankingFactor, Double?>(), listOf(RankingFactor.GAPS, RankingFactor.FACULTY))
        assertEquals(0.0, empty.score, 1e-9)
        assertEquals(emptyMap<String, Double>(), empty.breakdown)
    }

    @Test
    fun combineFactorScores_dedupesDropsUnknownAndDropsAbsentBeforeWeights() {
        val deduped = combineFactorScoresByKey(
            mapOf("gaps" to 80.0, "halfdays" to 50.0, "social" to 20.0, "bogus" to 999.0),
            listOf("gaps", "gaps", "unknown", "halfdays", "social"),
        )
        assertEquals(60.0, deduped.score, 1e-9)
        assertEquals(setOf("gaps", "halfdays", "social"), deduped.breakdown.keys)

        // The top factor is absent, so it takes no weight: weights become 2 and 1.
        val absentTop = combineFactorScores(
            mapOf(RankingFactor.GAPS to 80.0, RankingFactor.HALFDAYS to 50.0),
            listOf(RankingFactor.SOCIAL, RankingFactor.GAPS, RankingFactor.HALFDAYS),
        )
        assertEquals(70.0, absentTop.score, 1e-9)
    }

    @Test
    fun combineFactorScores_accumulatesRawPoints_NotRoundedBreakdown() {
        // golden #1: raw points 40 + 16.666... + 3.333... = 60, but the rounded
        // breakdown entries add up to 60.0 here too — use a case where they differ.
        val scores = mapOf(
            RankingFactor.GAPS to 100.0 / 3.0,
            RankingFactor.HALFDAYS to 50.0,
            RankingFactor.SOCIAL to 100.0,
        )
        val result = combineFactorScores(scores, listOf(RankingFactor.GAPS, RankingFactor.HALFDAYS, RankingFactor.SOCIAL))
        assertTrue(
            result.breakdown.values.all { kotlin.math.abs(it - 16.7) < 1e-9 },
            "breakdown entries stay rounded to 1 dp",
        )
        assertEquals(50.0, result.score, 1e-9)
    }

    @Test
    fun rankingFactorScores_omitsDataLessFactorsAndClamps() {
        val inputs = RankingInputs(
            activeDays = 4,
            compactDays = 2,
            totalGapSlots = 6,
            averageDayStartMinutes = 540.0,
            averageDayEndMinutes = 1020.0,
            dailyLoadVariance = 1.0,
        )
        val scores = rankingFactorScores(
            RankingFactorInputs(inputs, halfDays = 6.0, socialScore = 40.0, facultyQuality = null, timeScore = null),
        )
        assertEquals(75.0, scores.getValue(RankingFactor.GAPS), 1e-9)
        assertEquals(50.0, scores.getValue(RankingFactor.HALFDAYS), 1e-9)
        assertEquals(28.57142857142857, scores.getValue(RankingFactor.EARLY_FINISH), 1e-9)
        assertFalse(RankingFactor.FACULTY in scores, "unrated faculty is absent, not zero")
        assertFalse(RankingFactor.TIME in scores, "no time preference is absent, not zero")

        val withData = rankingFactorScores(
            RankingFactorInputs(inputs, halfDays = 6.0, socialScore = 40.0, facultyQuality = 80.0, timeScore = 90.0),
        )
        assertEquals(80.0, withData.getValue(RankingFactor.FACULTY), 1e-9)
        assertEquals(90.0, withData.getValue(RankingFactor.TIME), 1e-9)

        val clamped = rankingFactorScores(
            RankingFactorInputs(inputs, halfDays = 6.0, socialScore = 500.0, facultyQuality = null, timeScore = null),
        )
        assertEquals(100.0, clamped.getValue(RankingFactor.SOCIAL), 1e-9)
    }

    // ── legacy four-component blend ────────────────────────────────────────

    @Test
    fun combineRankingScores_crossStackScoreIs52_1Not52_0() {
        // rankingComponents of the sample report's Option 1, from the TS/Python/Go
        // cross-stack parity gate (SWARM_BOARD entries 15-19).
        val result = combineRankingScores(
            RankingComponents(profile = 63.8, faculty = null, sort = 28.645833333333332, time = null),
        )
        assertEquals(52.1, result.score, 1e-9)
        assertBreakdown(mapOf("profile" to 42.5, "sort" to 9.5), result.breakdown)
        // Rounded breakdown entries alone would give 52.0, not 52.1.
        assertEquals(52.0, result.breakdown.values.sum(), 1e-9)
    }

    @Test
    fun combineRankingScores_completesTheCanonicalTailAndSkipsAbsentComponents() {
        val result = combineRankingScores(
            RankingComponents(profile = 100.0, faculty = 50.0, sort = 20.0, time = null),
            listOf(RankingPriority.TIME, RankingPriority.PROFILE),
        )
        assertEquals(70.0, result.score, 1e-9)
        assertBreakdown(mapOf("profile" to 50.0, "faculty" to 16.7, "sort" to 3.3), result.breakdown)

        val nothing = combineRankingScores(RankingComponents())
        assertEquals(0.0, nothing.score, 1e-9)
        assertEquals(emptyMap<String, Double>(), nothing.breakdown)
    }

    @Test
    fun combineRankingScores_absentComponentTakesNoWeight_ZeroComponentDoes() {
        val absent = combineRankingScores(RankingComponents(profile = 100.0, faculty = null))
        val zeroed = combineRankingScores(RankingComponents(profile = 100.0, faculty = 0.0))
        assertTrue(absent.score > zeroed.score)
        assertFalse("faculty" in absent.breakdown)
        assertTrue("faculty" in zeroed.breakdown)
        assertEquals(100.0, absent.score, 1e-9)
    }

    @Test
    fun combiners_clampEveryScoreIntoZeroAndHundred() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -50.0, 500.0)) {
            val factors = combineFactorScores(mapOf(RankingFactor.GAPS to bad), listOf(RankingFactor.GAPS))
            assertTrue(factors.score in 0.0..100.0, "factor score for $bad must be 0..100")
        }
        val legacy = combineRankingScores(
            RankingComponents(profile = Double.NaN, faculty = Double.POSITIVE_INFINITY, sort = -10.0, time = 1000.0),
        )
        assertTrue(legacy.score in 0.0..100.0)
    }

    // ── exclusivity ────────────────────────────────────────────────────────

    @Test
    fun exclusivity_presentRankingFactorsSupersedeModeAndPriority() {
        val factors = RankingFactorsConfig(
            order = listOf(RankingFactor.GAPS, RankingFactor.HALFDAYS),
            disabled = listOf(RankingFactor.HALFDAYS),
        )
        val factorSelection = RankingSelection.resolve(rankingFactors = factors, hasLegacyRanking = true)
        assertTrue(factorSelection is RankingSelection.Factors)
        val factorResult = combineForSelection(
            selection = factorSelection,
            factorScores = mapOf(RankingFactor.GAPS to 80.0, RankingFactor.HALFDAYS to 50.0),
            components = RankingComponents(profile = 100.0),
        )
        // Disabled factor removed; the legacy profile score is ignored entirely.
        assertEquals(80.0, factorResult!!.score, 1e-9)
        assertBreakdown(mapOf("gaps" to 80.0), factorResult.breakdown)

        val legacySelection = RankingSelection.resolve(rankingFactors = null, hasLegacyRanking = true)
        assertTrue(legacySelection is RankingSelection.Legacy)
        val legacyResult = combineForSelection(
            selection = legacySelection,
            factorScores = mapOf(RankingFactor.GAPS to 1.0),
            components = RankingComponents(profile = 100.0),
        )
        assertEquals(100.0, legacyResult!!.score, 1e-9)

        val none = RankingSelection.resolve(rankingFactors = null, hasLegacyRanking = false)
        assertEquals(RankingSelection.None, none)
        assertNull(combineForSelection(none, emptyMap<RankingFactor, Double?>(), RankingComponents()))
    }
}
