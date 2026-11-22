package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.model.FacultyProfile
import com.amazecc.app.shared.model.FacultySchool
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.FacultyFreeSlotsResult
import com.amazecc.app.shared.utils.FacultySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-163: the live host's data path is tested through its small pure seams instead of
 * mocking AmazeClient. Real DTO fields are checked to survive into [M3EFacultyState] with
 * unknown fields left blank, every action transition is checked through the reducer, and
 * the sheet is rendered from the reducer's result to prove "ToggleShowTimetable opens it".
 */
class M3EFacultyLiveHostTest {

    private fun realProfile() = FacultyProfile(
        id = "row-1",
        name = "Real Faculty Person",
        designation = "Associate Professor",
        email = "real.person@vit.ac.in",
        employeeId = "12345",
        intercom = "4321"
    )

    private fun unmappedMember() = M3EFacultyMember(
        id = "nobody-in-report",
        name = "Nobody In The Report",
        designation = "",
        school = "SCOPE",
        cabin = "",
        email = ""
    )

    @Test
    fun realDtoMapsFaithfullyIntoFacultyStateWithoutInventedValues() {
        val schools = listOf(FacultySchool(id = "scope", school_name = "SCOPE"))
        val members = mapFacultyProfiles(listOf(realProfile()), "SCOPE")
        val state = M3EFacultyState(
            selectedSchool = "SCOPE",
            availableSchools = mapFacultySchools(schools),
            facultyList = members,
            filteredFaculty = filterFacultyMembers(members, "", "SCOPE")
        )

        val member = state.facultyList.single()
        // Real fields are preserved verbatim.
        assertEquals("12345", member.id) // employeeId wins over the row id
        assertEquals("Real Faculty Person", member.name)
        assertEquals("Associate Professor", member.designation)
        assertEquals("SCOPE", member.school)
        assertEquals("real.person@vit.ac.in", member.email)
        assertEquals("4321", member.intercom)
        assertEquals(listOf("All", "SCOPE"), state.availableSchools)
        assertEquals(member, state.filteredFaculty.single())

        // Unknown fields stay blank/null and no live-presence claim is fabricated.
        assertEquals("", member.cabin)
        assertNull(member.rating)
        assertFalse(member.isMyFaculty)
        assertFalse(member.isCurrentlyTeaching)
        assertNull(member.currentSlotInfo)
    }

    @Test
    fun blankDirectoryFieldsStayBlankRatherThanGettingGuessed() {
        val blank = realProfile().copy(email = "", intercom = "")
        val member = mapFacultyProfiles(listOf(blank), "SITE").single()
        assertEquals("", member.email)
        assertNull(member.intercom)
        assertEquals("SITE", member.school)
        assertEquals(emptyList<String>(), member.coursesTaught)
        assertTrue(member.timetable.isEmpty())
    }

    @Test
    fun enrichmentOnlyReplacesFieldsTheDirectoryActuallyReturned() {
        val base = mapFacultyProfiles(listOf(realProfile()), "SCOPE").single()
        val detail = FacultyProfile(
            id = "12345",
            name = "Directory Name",
            designation = "",
            email = "updated@vit.ac.in",
            employeeId = "12345",
            intercom = ""
        )
        val enriched = enrichFacultyMember(base, detail)

        assertEquals("Directory Name", enriched.name)
        assertEquals("", detail.designation)
        assertEquals("Associate Professor", enriched.designation) // empty detail does not erase
        assertEquals("updated@vit.ac.in", enriched.email)
        assertEquals("4321", enriched.intercom) // empty detail does not erase
        assertEquals("", enriched.cabin) // still unknown — FacultyProfile has no cabin
        assertFalse(enriched.isCurrentlyTeaching)
    }

    @Test
    fun searchAndSchoolFiltersAreWiredToRealMemberFields() {
        val a = mapFacultyProfiles(listOf(realProfile()), "SCOPE").single()
        val b = a.copy(id = "67890", name = "Other Person", school = "SITE", email = "other@vit.ac.in")
        val all = listOf(a, b)

        assertEquals(listOf(a), filterFacultyMembers(all, "", "SCOPE"))
        assertEquals(listOf(b), filterFacultyMembers(all, "other", "All"))
        assertEquals(listOf(a), filterFacultyMembers(all, "real.person@vit.ac.in", "All"))
        assertTrue(filterFacultyMembers(all, "not-present", "All").isEmpty())
    }

    @Test
    fun reducerWiresSearchSchoolExpandAndRefresh() {
        var ui = FacultyLiveUiState()
        ui = applyFacultyLiveAction(ui, M3EFacultyAction.UpdateSearch("web"))
        assertEquals("web", ui.searchQuery)

        ui = ui.copy(expandedFacultyId = "12345")
        ui = applyFacultyLiveAction(ui, M3EFacultyAction.SelectSchool("SITE"))
        assertEquals("SITE", ui.selectedSchool)
        assertNull(ui.expandedFacultyId) // changing school closes a stale expansion

        ui = applyFacultyLiveAction(ui, M3EFacultyAction.ToggleExpand("12345"))
        assertEquals("12345", ui.expandedFacultyId)
        ui = applyFacultyLiveAction(ui, M3EFacultyAction.ToggleExpand("12345"))
        assertNull(ui.expandedFacultyId)

        ui = applyFacultyLiveAction(ui, M3EFacultyAction.Refresh)
        assertEquals(1, ui.refreshTick)
        assertNull(ui.notice)
    }

    @Test
    fun toggleShowTimetableOpensSheetOnlyForARealMember() {
        val member = unmappedMember()
        val opened = applyFacultyLiveAction(
            FacultyLiveUiState(),
            M3EFacultyAction.ToggleShowTimetable(member.id),
            listOf(member)
        )
        assertEquals(member.id, opened.sheetFacultyId)

        val missing = applyFacultyLiveAction(
            FacultyLiveUiState(),
            M3EFacultyAction.ToggleShowTimetable("not-a-faculty"),
            listOf(member)
        )
        assertNull(missing.sheetFacultyId)
        assertEquals("Schedule is unavailable for this faculty member", missing.notice)
    }

    @Test
    fun copyEmailNoticeNeverClaimsSuccessWhenClipboardFailed() {
        val ok = applyFacultyLiveAction(
            FacultyLiveUiState(),
            M3EFacultyAction.CopyEmail("real.person@vit.ac.in")
        )
        assertEquals("Email copied", ok.notice)

        val failed = applyFacultyLiveAction(
            FacultyLiveUiState(),
            M3EFacultyAction.CopyEmail("real.person@vit.ac.in"),
            emptyList(),
            clipboardCopySucceeded = false
        )
        assertEquals("Could not copy email", failed.notice)

        val blank = applyFacultyLiveAction(FacultyLiveUiState(), M3EFacultyAction.CopyEmail(""))
        assertEquals("No email published for this faculty member", blank.notice)
    }

    @Test
    fun offeringsAndWeeklyGridComeOnlyFromTheDerivation() {
        val schedule = FacultyFreeSlotsResult(
            facultyName = "Real Faculty Person",
            occupiedSlots = listOf(
                FacultySlot("MON", "8:00-8:50", "CSE1001", "Intro To Computing", "A1"),
                FacultySlot("TUE", "8:50-9:40", "CSE1002", "Programming Lab", "L31"),
                FacultySlot("WED", "9:50-10:40", "CSE1003", "Tutorial", "TB1")
            ),
            freeSlots = mapOf("MON" to listOf("9:50-10:40"))
        )

        val offerings = buildFacultyOfferings(schedule)
        assertEquals(3, offerings.size)
        assertEquals("CSE1001", offerings[0].courseCode)
        assertEquals("Theory", offerings[0].type)
        assertEquals("Lab", offerings[1].type)
        assertEquals("Tutorial", offerings[2].type)
        assertTrue(offerings.all { it.venue.isBlank() }) // FFCS CSV has no venue for a faculty row

        val grid = buildFacultyWeeklyGrid(schedule)
        assertTrue(grid.any { it.day == "MON" && it.timeRange == "8:00-8:50" && it.courseCode == "CSE1001" })
        assertTrue(grid.any { it.day == "MON" && it.timeRange == "9:50-10:40" && it.isFree })
        assertTrue(grid.none { it.courseCode == "CSE9999" })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reducerOpenedSheetShowsRealTitleHonestEmptyCopyAndDismisses() = runComposeUiTest {
        val member = unmappedMember()
        val opened = applyFacultyLiveAction(
            FacultyLiveUiState(),
            M3EFacultyAction.ToggleShowTimetable(member.id),
            listOf(member)
        )
        var dismissals = 0

        setContent {
            AmazeM3ETheme {
                val shown = listOf(member).firstOrNull { it.id == opened.sheetFacultyId }
                if (shown != null) {
                    M3EFacultyScheduleSheet(faculty = shown, onDismiss = { dismissals++ })
                }
            }
        }

        onNodeWithText("Nobody In The Report's Schedule").assertExists()
        onNodeWithText("No scheduled classes found").assertExists()

        // The sheet now owns a LIFO id; register it as the host does before dismissing.
        AppState.pushOverlay(FACULTY_SCHEDULE_OVERLAY_ID)
        try {
            onAllNodesWithContentDescription("Dismiss sheet")[0].performClick()
            assertEquals(1, dismissals)
        } finally {
            AppState.popOverlay(FACULTY_SCHEDULE_OVERLAY_ID)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scheduleEntryOpensSheetForExpandedMemberThroughTheActionPath() = runComposeUiTest {
        val member = unmappedMember()
        var ui by mutableStateOf(FacultyLiveUiState(expandedFacultyId = member.id))

        setContent {
            AmazeM3ETheme {
                // Mirrors the host wiring exactly: the labelled control feeds the reducer's
                // ToggleShowTimetable branch, which is the only thing that opens the sheet.
                M3EFacultyScheduleEntry(
                    expandedFacultyId = ui.expandedFacultyId,
                    onOpenSchedule = { id ->
                        ui = applyFacultyLiveAction(
                            ui,
                            M3EFacultyAction.ToggleShowTimetable(id),
                            listOf(member)
                        )
                    }
                )
                val shown = listOf(member).firstOrNull { it.id == ui.sheetFacultyId }
                if (shown != null) M3EFacultyScheduleSheet(faculty = shown, onDismiss = {})
            }
        }

        onNodeWithText("View weekly schedule").assertExists()
        onNodeWithText("Nobody In The Report's Schedule").assertDoesNotExist()

        onNodeWithText("View weekly schedule").performClick()

        assertEquals(member.id, ui.sheetFacultyId)
        onNodeWithText("Nobody In The Report's Schedule").assertExists()
        onNodeWithText("No scheduled classes found").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scheduleEntryWithNoExpandedMemberShowsHonestHelperAndOpensNothing() = runComposeUiTest {
        var openCalls = 0
        setContent {
            AmazeM3ETheme {
                M3EFacultyScheduleEntry(
                    expandedFacultyId = null,
                    onOpenSchedule = { openCalls++ }
                )
            }
        }

        // No dead/disabled button and no invented target: just the honest hint.
        onNodeWithText("View weekly schedule").assertDoesNotExist()
        onNodeWithText("Select a faculty member to view their weekly schedule").assertExists()
        assertEquals(0, openCalls)
    }
}
