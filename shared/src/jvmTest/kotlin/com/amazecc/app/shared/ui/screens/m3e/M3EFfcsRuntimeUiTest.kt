package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EFfcsBlockedSlotUi
import com.amazecc.app.shared.domain.overhaul.M3EFfcsFacultyPriorityUi
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRankingFactor
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRankingMode
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRetryUi
import com.amazecc.app.shared.ffcs.AddedCourse
import com.amazecc.app.shared.ffcs.FfcsEngine
import com.amazecc.app.shared.ffcs.FfcsEngineResult
import com.amazecc.app.shared.ffcs.FfcsErrorCode
import com.amazecc.app.shared.ffcs.FfcsGenerateResult
import com.amazecc.app.shared.ffcs.ParsedCourse
import com.amazecc.app.shared.ffcs.TimetableState
import com.amazecc.app.shared.ffcs.planner.RankingFactor as EngineRankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode as EngineRankingMode
import com.amazecc.app.shared.state.FfcsGenerationSnapshot
import com.amazecc.app.shared.state.FfcsRuntimeOutcome
import com.amazecc.app.shared.state.FfcsViewModel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-184 wiring evidence: the M3E FFCS controls emit real actions, the state hooks
 * are driven by those actions, ranking config reaches the typed engine params,
 * a retryable failure triggers exactly one runner attempt, and empty/error copy
 * reports what actually happened.
 */
@OptIn(ExperimentalTestApi::class)
class M3EFfcsRuntimeUiTest {

    private fun sampleCourse(code: String) = ParsedCourse(
        code = code,
        title = "$code title",
        type = "Theory",
        credits = "4",
        room = "SJT-101",
        slot = "A1",
        faculty = "Dr A"
    )

    private fun sampleTimetable() = TimetableState(
        id = "tt_test",
        name = "TT 1",
        courses = listOf(
            AddedCourse(
                id = "c1", code = "CSE3002", title = "Web Programming",
                slots = listOf("A1"), faculty = "Dr A", venue = "SJT-101",
                credits = "4", type = "Theory", color = "#2563EB"
            )
        )
    )

    private fun sampleSnapshot(vararg codes: String) = FfcsGenerationSnapshot(
        allCourses = codes.map(::sampleCourse),
        selectedCodes = codes.toList(),
        locks = emptyList(),
        blockedSlots = emptySet(),
        maxResults = 50,
        uniqueFaculty = false,
        morningPreference = false,
        options = FfcsEngine.GenerateOptions(),
        rankingMode = null,
        rankingFactors = null,
        facultyPriority = emptyMap()
    )

    // ── 1. state/action emission ────────────────────────────────────────────

    @Test
    fun courseTabCapabilityControlsEmitRealActions() = runComposeUiTest {
        val captured = mutableListOf<M3EFfcsPlannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EFfcsPlannerScreen(
                    state = M3EFfcsPlannerState(
                        rankingMode = M3EFfcsRankingMode.BEST_FACULTY,
                        enabledRankingFactors = setOf(M3EFfcsRankingFactor.GAPS),
                        facultyPriority = listOf(
                            M3EFfcsFacultyPriorityUi("CSE3002", listOf("Dr A", "Dr B"))
                        ),
                        blockedSlots = listOf(M3EFfcsBlockedSlotUi("X|A1|SJT-101", "X|A1|SJT-101"))
                    ),
                    onAction = { captured.add(it) }
                )
            }
        }

        onNodeWithText("Ranking: Best Faculty").performClick()
        onNodeWithText("Gaps").performClick()
        // Faculty + blocked controls are in later LazyColumn items; scroll each into
        // the viewport before clicking, otherwise the injected click is dropped.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Raise Dr B"))
        onNodeWithText("Raise Dr B").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Clear priorities"))
        onNodeWithText("Clear priorities").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Clear blocked"))
        onNodeWithText("Clear blocked").performClick()

        assertTrue(captured.any { it is M3EFfcsPlannerAction.CycleRankingMode })
        assertTrue(captured.any {
            it is M3EFfcsPlannerAction.ToggleRankingFactor && it.factor == M3EFfcsRankingFactor.GAPS
        })
        assertTrue(captured.any {
            it is M3EFfcsPlannerAction.PromoteFaculty && it.faculty == "Dr B"
        })
        assertTrue(captured.any { it is M3EFfcsPlannerAction.ClearFacultyPriority })
        assertTrue(captured.any { it is M3EFfcsPlannerAction.ClearBlockedSlots })
        // Existing COURSES controls still emit their original actions.
        assertTrue(captured.none { it is M3EFfcsPlannerAction.Generate })
    }

    @Test
    fun resultsTabDropRetryControlsEmitRealActions() = runComposeUiTest {
        val captured = mutableListOf<M3EFfcsPlannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EFfcsPlannerScreen(
                    state = M3EFfcsPlannerState(
                        activeTab = M3EFfcsTab.RESULTS,
                        dropRetry = M3EFfcsRetryUi(
                            enabled = true,
                            canRetry = true,
                            droppedCodes = listOf("MAT2001")
                        )
                    ),
                    onAction = { captured.add(it) }
                )
            }
        }

        onNodeWithText("Drop retry: On").performClick()
        onNodeWithText("Retry with drop").performClick()

        assertTrue(captured.any {
            it is M3EFfcsPlannerAction.SetDropRetryEnabled && !it.enabled
        })
        assertTrue(captured.any { it is M3EFfcsPlannerAction.RetryWithDrop })
        // Existing RESULTS copy and actions are preserved.
        onNodeWithText("No options generated yet.").assertExists()
        onNodeWithText("Generate clash-free options").assertExists()
        onNodeWithText("Export ICS").assertExists()
        onNodeWithText("Export HTML dossier").assertExists()
    }

    @Test
    fun viewModelStateHooksAreDrivenByRealSetters() = runBlocking {
        FfcsViewModel.resetSelection()
        try {
            FfcsViewModel.setRankingMode(EngineRankingMode.BEST_FACULTY)
            FfcsViewModel.setRankingFactorEnabled(EngineRankingFactor.GAPS, true)
            FfcsViewModel.setFacultyPriorityForCourse("CSE3002", listOf("Dr A", "Dr B"))
            FfcsViewModel.promoteFaculty("CSE3002", "Dr B")
            FfcsViewModel.setDropRetryEnabled(true)
            FfcsViewModel.setBlockedSlot("X|A1|SJT-101", true)
            FfcsViewModel.toggleCourse("CSE3002")
            FfcsViewModel.toggleCourse("MAT2001")

            assertEquals(EngineRankingMode.BEST_FACULTY, FfcsViewModel.rankingMode.value)
            val cfg = FfcsViewModel.rankingFactors.value!!
            val active = cfg.order.filterNot { it in cfg.disabled }
            assertEquals(listOf(EngineRankingFactor.GAPS), active)
            assertEquals(listOf("Dr B", "Dr A"), FfcsViewModel.facultyPriority.value["CSE3002"])
            assertTrue(FfcsViewModel.dropRetryEnabled.value)
            assertTrue("X|A1|SJT-101" in FfcsViewModel.blockedSlots.value)
            assertEquals(listOf("CSE3002", "MAT2001"), FfcsViewModel.selectionOrder.value)
        } finally {
            FfcsViewModel.resetSelection()
        }
    }

    // ── 2. ranking config reaches the typed engine ──────────────────────────

    @Test
    fun rankingAndPriorityConfigReachEngineParams() {
        val factors = RankingFactorsConfig(
            order = EngineRankingFactor.CANONICAL_ORDER,
            disabled = EngineRankingFactor.CANONICAL_ORDER.filter { it != EngineRankingFactor.GAPS }
        )
        val priority = mapOf("CSE3002" to listOf("Dr A", "Dr B"))
        val snapshot = sampleSnapshot("CSE3002").copy(
            blockedSlots = setOf("X|A1|SJT-101"),
            rankingMode = EngineRankingMode.BEST_FACULTY,
            rankingFactors = factors,
            facultyPriority = priority
        )

        val params = FfcsViewModel.buildEngineParams(snapshot)

        assertEquals(EngineRankingMode.BEST_FACULTY, params.rankingMode)
        // The adapter carries the active factor order across the seam.
        assertEquals(listOf(EngineRankingFactor.GAPS), params.rankingFactors?.order)
        assertEquals(emptyList<EngineRankingFactor>(), params.rankingFactors?.disabled)
        assertEquals(priority, params.facultyPriority)
        // The blocked offering key contributes its real slot token, not the faculty/room.
        assertEquals(listOf("A1"), params.blockedSlots)
        assertEquals(1, params.courseLocks.size)
        assertEquals("CSE3002", params.courseLocks.first().code)
        assertEquals(listOf(sampleCourse("CSE3002")), params.masterCourses)
    }

    @Test
    fun unsetRankingStaysUnset() {
        val params = FfcsViewModel.buildEngineParams(sampleSnapshot("CSE3002"))
        assertEquals(null, params.rankingMode)
        assertEquals(null, params.rankingFactors)
        assertEquals(null, params.facultyPriority)
    }

    // ── 3. drop retry triggers a real second attempt ────────────────────────

    @Test
    fun retryableFailureTriggersOneRunnerAttempt() = runBlocking {
        FfcsViewModel.resetSelection()
        val snapshot = sampleSnapshot("CSE3002", "MAT2001")
        var engineCalls = 0
        var runnerCalls = 0

        val outcome = FfcsViewModel.runGeneration(
            snapshot = snapshot,
            allowDropRetry = true,
            engine = {
                engineCalls++
                FfcsEngineResult.Error(FfcsErrorCode.NO_CONFLICT_FREE)
            },
            runner = {
                runnerCalls++
                FfcsGenerateResult.Success(listOf(sampleTimetable()))
            }
        )

        assertEquals(1, engineCalls)
        assertEquals(1, runnerCalls)
        assertTrue(outcome is FfcsRuntimeOutcome.Generated)
        assertEquals(1, outcome.timetables.size)
        // Lowest-priority (last added) course is dropped; the first is protected by order.
        assertEquals(listOf("MAT2001"), outcome.droppedCodes)
        assertEquals(1, FfcsViewModel.generatedTimetables.value.size)
        FfcsViewModel.resetSelection()
    }

    @Test
    fun nonRetryableFailureDoesNotCallRunner() = runBlocking {
        FfcsViewModel.resetSelection()
        var runnerCalls = 0
        val outcome = FfcsViewModel.runGeneration(
            snapshot = sampleSnapshot("CSE3002", "MAT2001"),
            allowDropRetry = true,
            engine = { FfcsEngineResult.Error(FfcsErrorCode.ERROR) },
            runner = {
                runnerCalls++
                FfcsGenerateResult.Success(emptyList())
            },
            legacy = { emptyList() }
        )
        assertEquals(0, runnerCalls)
        assertTrue(outcome is FfcsRuntimeOutcome.Failed)
        assertEquals(FfcsErrorCode.ERROR, outcome.code)
        FfcsViewModel.resetSelection()
    }

    // ── 4. empty / error copy is honest ─────────────────────────────────────

    @Test
    fun typedErrorCopyComesFromRealOutcome() = runBlocking {
        FfcsViewModel.resetSelection()
        val outcome = FfcsViewModel.runGeneration(
            snapshot = sampleSnapshot("CSE3002"),
            allowDropRetry = false,
            engine = { FfcsEngineResult.Error(FfcsErrorCode.NO_VALID_SLOTS, "CSE3002") },
            legacy = { emptyList() }
        )
        assertTrue(outcome is FfcsRuntimeOutcome.Failed)
        assertEquals(FfcsErrorCode.NO_VALID_SLOTS, outcome.code)
        assertTrue(outcome.reason.isNotBlank())
        assertEquals(outcome.reason, FfcsViewModel.errorMsg.value)
        assertTrue(FfcsViewModel.generatedTimetables.value.isEmpty())
        FfcsViewModel.resetSelection()
    }

    @Test
    fun emptySuccessReportsNoOptionsWithoutInventingMetrics() = runBlocking {
        FfcsViewModel.resetSelection()
        val outcome = FfcsViewModel.runGeneration(
            snapshot = sampleSnapshot("CSE3002"),
            allowDropRetry = false,
            engine = { FfcsEngineResult.Ok(emptyList()) },
            legacy = { emptyList() }
        )
        assertTrue(outcome is FfcsRuntimeOutcome.Generated)
        assertTrue(outcome.timetables.isEmpty())
        assertTrue(FfcsViewModel.generatedTimetables.value.isEmpty())
        assertEquals(null, FfcsViewModel.errorMsg.value)
        FfcsViewModel.resetSelection()
    }
}
