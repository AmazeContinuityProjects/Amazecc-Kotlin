package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.domain.overhaul.M3EFacultyTimetableSlot
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EFacultyScreenUiTest {

    private fun sampleMember() = M3EFacultyMember(
        id = "fac_1",
        name = "Prof. Sundar N",
        designation = "Associate Professor",
        school = "SCOPE",
        cabin = "SJT 412-A",
        email = "sundar.n@vit.ac.in",
        intercom = "1412",
        coursesTaught = listOf("CSE3002"),
        isMyFaculty = true,
        timetable = listOf(
            M3EFacultyTimetableSlot("A1", "CSE3002", "Full Stack Development", "SJT-512", "MON")
        )
    )

    private fun sampleState(expandedId: String? = null) = M3EFacultyState(
        facultyList = listOf(sampleMember()),
        filteredFaculty = listOf(sampleMember()),
        expandedFacultyId = expandedId
    )

    @Test
    fun testFacultyScreenRendersHeaderAndCard() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(state = sampleState(), onAction = {})
            }
        }
        onNodeWithText("Faculty").assertExists()
        onNodeWithText("Prof. Sundar N").assertExists()
        onNodeWithText("SJT 412-A").assertExists()
    }

    @Test
    fun testExpandedCardShowsTimetable() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(state = sampleState(expandedId = "fac_1"), onAction = {})
            }
        }
        onNodeWithText("TEACHING SCHEDULE").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("MON").assertExists()
        onNodeWithText("SJT-512").assertExists()
    }

    @Test
    fun testSchoolFilterEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EFacultyAction>()
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(state = sampleState(), onAction = { capturedActions.add(it) })
            }
        }
        onNodeWithText("SCOPE").performClick()
        assertTrue(capturedActions.isNotEmpty())
        val filterAction = capturedActions.filterIsInstance<M3EFacultyAction.SelectSchool>().firstOrNull()
        assertEquals("SCOPE", filterAction?.school)
    }

    @Test
    fun testCopyEmailEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EFacultyAction>()
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(state = sampleState(), onAction = { capturedActions.add(it) })
            }
        }
        onNodeWithContentDescription("Copy Email").performClick()
        assertTrue(capturedActions.isNotEmpty())
        val copyAction = capturedActions.filterIsInstance<M3EFacultyAction.CopyEmail>().firstOrNull()
        assertEquals("sundar.n@vit.ac.in", copyAction?.email)
    }

    @Test
    fun testEmptyStateShowsNoFacultyMessage() = runComposeUiTest {
        val emptyState = sampleState().copy(filteredFaculty = emptyList())
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(state = emptyState, onAction = {})
            }
        }
        onNodeWithText("No faculty found").assertExists()
    }
}
