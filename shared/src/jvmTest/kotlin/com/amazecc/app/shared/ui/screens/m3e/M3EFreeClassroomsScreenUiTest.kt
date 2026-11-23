package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.FreeClassroomBlockSummary
import com.amazecc.app.shared.domain.FreeClassroomPeriodOption
import com.amazecc.app.shared.domain.FreeClassroomRoomRow
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomClassDetails
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomInsights
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomOccupant
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomTimelineEntry
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EFreeClassroomsScreenUiTest {

    private fun periods() = listOf(
        FreeClassroomPeriodOption("p1", "08:00 - 08:50", 480, 530),
        FreeClassroomPeriodOption("p2", "09:00 - 09:50", 540, 590),
        FreeClassroomPeriodOption("p3", "10:00 - 10:50", 600, 650)
    )

    private fun room(code: String, block: String, isLab: Boolean = false) = FreeClassroomRoomRow(
        code = code,
        block = block,
        number = code.substringAfter('-', code),
        isLab = isLab,
        isFree = true
    )

    private fun block(block: String, rooms: List<FreeClassroomRoomRow>) = FreeClassroomBlockSummary(
        block = block,
        total = rooms.size + 2,
        free = rooms.size,
        theoryFree = rooms.count { !it.isLab },
        labFree = rooms.count { it.isLab },
        rooms = rooms,
        freeRooms = rooms
    )

    private fun baseState(
        blockSummaries: List<FreeClassroomBlockSummary> = listOf(block("X", listOf(room("X-1", "X"), room("X-2", "X")))),
        blockFilter: String = "All"
    ) = M3EFreeClassroomsState(
        selectedDay = "MON",
        selectedPeriodKey = "p1",
        availablePeriodOptions = periods(),
        blockSummaries = blockSummaries,
        availableBlocks = listOf("X"),
        blockFilter = blockFilter,
        clockLabel = "08:02 · Mon",
        insights = M3EFreeClassroomInsights(
            isLiveNow = true,
            freeNowCount = 2,
            bestPeriodKey = "p2",
            bestPeriodLabel = "09:00 - 09:50",
            bestPeriodFree = 4,
            runFreeCount = 1
        )
    )

    // ── ONE answer track ────────────────────────────────────────────────────────

    @Test
    fun singleResultTrackHasOneCountAndNoLegacyCardFields() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState(
                        blockSummaries = listOf(
                            block("X", listOf(room("X-1", "X"), room("X-2", "X"))),
                            block("Y", listOf(room("Y-1", "Y")))
                        )
                    ),
                    onAction = {}
                )
            }
        }

        // Three free rooms, one derived count, no second "rooms found" counter.
        onNodeWithText("3 free").assertExists()
        onAllNodesWithText("rooms found").assertCountEquals(0)
        onNodeWithText("X Block").assertExists()
        onNodeWithText("2 of 4 free").assertExists()
        onAllNodesWithText("2 of 5 free").assertCountEquals(0)

        // Honest absence: no AC badge, no capacity, no floor, no invented status word.
        onNodeWithText("AC").assertDoesNotExist()
        onNodeWithText("Floor 4").assertDoesNotExist()
        onNodeWithText("60 seats").assertDoesNotExist()
        onNodeWithText("unknown", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun dayPickerAndStepperFollowTheStrictClock() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState().copy(isLive = true, nowDayKey = "MON", nowPeriodKey = "p1"),
                    onAction = { captured.add(it) }
                )
            }
        }

        onNodeWithText("Live").assertExists()
        onNodeWithText("Selected").assertDoesNotExist()
        onNodeWithContentDescription("Previous period").assertIsNotEnabled()
        onNodeWithContentDescription("Next period").performClick()
        assertEquals(M3EFreeClassroomsAction.StepPeriod(1), captured.single())

        onNodeWithText("Tue").performClick()
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SelectDay("TUE") })

        val nowActionIndex = captured.indexOf(M3EFreeClassroomsAction.JumpToNow)
        onNodeWithContentDescription("Jump to now").performClick()
        assertTrue(captured.indexOf(M3EFreeClassroomsAction.JumpToNow) > nowActionIndex)
    }

    @Test
    fun selectedBadgeAppearsWhenTheClockIsNotLive() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState().copy(isLive = false), onAction = {})
            }
        }
        onNodeWithText("Selected").assertExists()
        onNodeWithText("Live").assertDoesNotExist()
        onNodeWithContentDescription("Jump to now").assertDoesNotExist()
    }

    // ── collapsed controls ──────────────────────────────────────────────────────

    @Test
    fun insightsChangeTheSingleAnswer() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState(), onAction = { captured.add(it) })
            }
        }

        onNodeWithText("Free now").performClick()
        onNodeWithText("Emptiest period").performClick()
        onNodeWithText("Free for 3 periods").performClick()

        assertEquals(
            listOf("free_now", "emptiest", "run"),
            captured.filterIsInstance<M3EFreeClassroomsAction.ApplyInsight>().map { it.id }
        )
    }

    @Test
    fun freeNowInsightIsHiddenWhenThereIsNoStrictLivePeriod() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState().copy(
                        isLive = false,
                        insights = baseState().insights.copy(isLiveNow = false, freeNowCount = 7)
                    ),
                    onAction = {}
                )
            }
        }
        // A stale selection must never be advertised as a "Free now" count.
        onNodeWithText("Free now").assertDoesNotExist()
        onAllNodesWithText("Jump to the live period").assertCountEquals(0)
        onNodeWithText("7").assertDoesNotExist()
    }

    @Test
    fun inlineNoticeStaysInlineOverValidResults() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState().copy(inlineNotice = "Sync in progress, try again shortly"),
                    onAction = {}
                )
            }
        }
        onNodeWithText("Sync in progress, try again shortly").assertExists()
        onNodeWithText("2 free").assertExists()
        onNodeWithText("Couldn't load the timetable").assertDoesNotExist()
    }

    @Test
    fun filtersLiveInOneSheetAndEmitRealChanges() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState().copy(filtersOpen = true, availableBlocks = listOf("X", "AB1")),
                    onAction = { captured.add(it) }
                )
            }
        }

        onAllNodesWithText("Filters", substring = true).assertCountEquals(2)
        onNodeWithContentDescription("Labs").performClick()
        onNodeWithContentDescription("Free for 3 periods").performClick()
        onNodeWithContentDescription("AB1").performScrollTo().performClick()
        onNodeWithText("Reset").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()

        assertTrue(captured.any { it == M3EFreeClassroomsAction.SetKindFilter(M3EFreeClassroomKindFilter.LAB) })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SetRunOnly(true) })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SetFiltersOpen(false) })
        assertTrue(captured.any { it is M3EFreeClassroomsAction.SetBlockFilter && it.block == "AB1" })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SetKindFilter(M3EFreeClassroomKindFilter.ALL) })
    }

    @Test
    fun searchClearEmitsAnEmptyQuery() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState().copy(searchQuery = "401"), onAction = { captured.add(it) })
            }
        }
        onNodeWithContentDescription("Clear search").performClick()
        assertEquals(M3EFreeClassroomsAction.SearchQueryChanged(""), captured.single())
    }

    @Test
    fun moreRoomsExpandAndChipHasSeparateInspectCopyTargets() = runComposeUiTest {
        val many = (1..13).map { room("X-$it", "X") }
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState(blockSummaries = listOf(block("X", many))),
                    onAction = { captured.add(it) }
                )
            }
        }

        onAllNodes(hasScrollToNodeAction())[1].performScrollToNode(hasText("+1 more in X"))
        onNodeWithText("+1 more in X").performClick()
        onNodeWithContentDescription("Inspect X-1").performClick()
        onNodeWithContentDescription("Copy X-1").performClick()

        assertTrue(captured.any { it == M3EFreeClassroomsAction.ToggleBlockExpanded("X") })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SelectRoom("X-1") })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.CopyRoom("X-1") })
    }

    @Test
    fun expandedBlockOffersShowFewer() = runComposeUiTest {
        val many = (1..13).map { room("X-$it", "X") }
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState(blockSummaries = listOf(block("X", many))).copy(expandedBlocks = setOf("X")),
                    onAction = { captured.add(it) }
                )
            }
        }
        onAllNodes(hasScrollToNodeAction())[1].performScrollToNode(hasText("Show fewer"))
        onNodeWithText("Show fewer").performClick()
        assertEquals(M3EFreeClassroomsAction.ToggleBlockExpanded("X"), captured.single())
    }

    // ── inspector depth ─────────────────────────────────────────────────────────

    private fun inspectorState() = baseState().copy(
        selectedRoom = "SJT-401",
        selectedDay = "MON",
        inspectDay = "TUE",
        roomTimeline = listOf(
            M3EFreeClassroomTimelineEntry(
                periodKey = "p1",
                label = "08:00 - 08:50",
                isFree = false,
                isSelected = false,
                occupants = listOf(
                    M3EFreeClassroomOccupant(
                        key = "TUE|p1|SJT-401|CSE1001|1",
                        courseCode = "CSE1001",
                        title = "Theory One",
                        faculty = "Dr Rao",
                        erpId = "12345",
                        classId = "C-9",
                        slot = "A1",
                        type = "Theory Only",
                        schoolId = "SCOPE",
                        source = "catalogue"
                    )
                )
            ),
            M3EFreeClassroomTimelineEntry("p2", "09:00 - 09:50", true, false, emptyList())
        )
    )

    @Test
    fun inspectorHasItsOwnDayTimelineAndDrillsIntoOneClass() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = inspectorState(), onAction = { captured.add(it) })
            }
        }

        onNodeWithText("Tuesday · 1/2 free").assertExists()
        onNodeWithText("Show Tuesday in the list").assertExists()
        onNodeWithText("Taken").assertExists()
        onNodeWithText("Free").assertExists()
        onNodeWithText("CSE1001", substring = true).assertExists()
        // A class from another period must not leak into this period's timeline.
        onNodeWithText("CSE1002", substring = true).assertDoesNotExist()

        onNodeWithText("Show Tuesday in the list").performClick()
        onNodeWithText("Copy room code").performClick()
        onNodeWithContentDescription("Open class details for 08:00 - 08:50").performClick()

        assertTrue(captured.any { it == M3EFreeClassroomsAction.ShowDayInList("TUE") })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.CopyRoom("SJT-401") })
        assertTrue(captured.any { it is M3EFreeClassroomsAction.OpenClass && it.occupantKey == "TUE|p1|SJT-401|CSE1001|1" })
    }

    @Test
    fun freeTimelineRowAppliesThatPeriodInsteadOfOpeningAClass() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = inspectorState(), onAction = { captured.add(it) })
            }
        }
        onNodeWithContentDescription("Show 09:00 - 09:50 in the list").performClick()
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SelectPeriod("p2") })
        assertTrue(captured.none { it is M3EFreeClassroomsAction.OpenClass })
    }

    @Test
    fun classOverlayShowsRealIdsAndARealFacultyActionWhenDataExists() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        val details = M3EFreeClassroomClassDetails(
            occupantKey = "TUE|p1|SJT-401|CSE1001|1",
            courseCode = "CSE1001",
            title = "Theory One",
            faculty = "Dr Rao",
            erpId = "12345",
            classId = "C-9",
            slot = "A1",
            type = "Theory Only",
            roomCode = "SJT-401",
            dayLabel = "Tuesday",
            periodLabel = "08:00 - 08:50",
            schoolId = "SCOPE",
            source = "catalogue"
        )
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState().copy(selectedClass = details), onAction = { captured.add(it) })
            }
        }

        onNodeWithText("ERP ID").assertExists()
        onNodeWithText("12345").assertExists()
        onNodeWithText("Class ID").assertExists()
        onNodeWithText("C-9").assertExists()
        onNodeWithText("A1").assertExists()
        onNodeWithText("FFCS catalogue").assertExists()
        onNodeWithText("View Faculty Schedule").performClick()
        assertEquals(
            M3EFreeClassroomsAction.ViewFacultySchedule("12345", "SCOPE"),
            captured.single()
        )
    }

    @Test
    fun classOverlayIsHonestWhenNoErpIdWasPublished() = runComposeUiTest {
        val details = M3EFreeClassroomClassDetails(
            occupantKey = "k", courseCode = "CSE1001", title = "Theory One",
            faculty = "", erpId = null, classId = null, slot = "", type = "Theory Only",
            roomCode = "SJT-401", dayLabel = "Monday", periodLabel = "08:00 - 08:50",
            schoolId = null, source = "snapshot"
        )
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState().copy(selectedClass = details), onAction = {})
            }
        }
        onAllNodesWithText("Not published").assertCountEquals(1)
        onNodeWithText("View Faculty Schedule").assertDoesNotExist()
        onNodeWithText("Faculty schedule is unavailable: no ERP id was published for this class.").assertExists()
    }

    // ── honest full-page states ─────────────────────────────────────────────────

    @Test
    fun errorIsAFullPageWithTryAgainNotASmallCard() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState().copy(errorMessage = "Sync failed"),
                    onAction = { captured.add(it) }
                )
            }
        }
        onNodeWithText("Couldn't load the timetable").assertExists()
        onNodeWithText("Sync failed").assertExists()
        onNodeWithText("Filters", substring = true).assertDoesNotExist()
        onNodeWithText("Try again").performClick()
        assertEquals(M3EFreeClassroomsAction.Refresh, captured.single())
    }

    @Test
    fun loadingShowsASkeletonAndNoEmptyVerdict() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = baseState().copy(isLoading = true), onAction = {})
            }
        }
        onNodeWithContentDescription("Classroom availability loading").assertExists()
        onNodeWithText("Every room is taken for this period").assertDoesNotExist()
        onNodeWithText("Filters", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyStateForOneBlockOffersAllBlocksAndJumpToNow() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState(blockSummaries = emptyList(), blockFilter = "SJT"),
                    onAction = { captured.add(it) }
                )
            }
        }
        onNodeWithText("Nothing free in SJT").assertExists()
        onNodeWithText("All blocks").performClick()
        onNodeWithText("Jump to now").performClick()
        assertTrue(captured.any { it == M3EFreeClassroomsAction.SetBlockFilter("All") })
        assertTrue(captured.any { it == M3EFreeClassroomsAction.JumpToNow })
    }

    @Test
    fun emptyStateForAllBlocksHasNoAllBlocksAction() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = baseState(blockSummaries = emptyList(), blockFilter = "All"),
                    onAction = {}
                )
            }
        }
        onNodeWithText("Every room is taken for this period").assertExists()
        onNodeWithText("All blocks").assertDoesNotExist()
        onNodeWithText("Jump to now").assertExists()
    }
}
