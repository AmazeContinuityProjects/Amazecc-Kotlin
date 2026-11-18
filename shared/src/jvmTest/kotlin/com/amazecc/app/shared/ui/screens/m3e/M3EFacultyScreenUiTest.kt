package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EFacultyScreenUiTest {

    private fun sampleState() = M3EFacultyState(
        facultyList = listOf(
            M3EFacultyMember(
                id = "fac_1",
                name = "Prof. Sundar N",
                designation = "Associate Professor",
                school = "SCOPE",
                cabin = "SJT 412-A",
                email = "sundar.n@vit.ac.in",
                intercom = "1412",
                coursesTaught = listOf("CSE3002"),
                isMyFaculty = true
            )
        ),
        filteredFaculty = listOf(
            M3EFacultyMember(
                id = "fac_1",
                name = "Prof. Sundar N",
                designation = "Associate Professor",
                school = "SCOPE",
                cabin = "SJT 412-A",
                email = "sundar.n@vit.ac.in",
                intercom = "1412",
                coursesTaught = listOf("CSE3002"),
                isMyFaculty = true
            )
        )
    )

    @Test
    fun testFacultyScreenRendersHeaderAndCard() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Faculty Directory").assertExists()
        onNodeWithText("Prof. Sundar N").assertExists()
        onNodeWithText("Cabin: SJT 412-A").assertExists()
        onNodeWithText("Enrolled").assertExists()
    }

    @Test
    fun testSchoolFilterEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EFacultyAction>()

        setContent {
            AmazeM3ETheme {
                M3EFacultyScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
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
                M3EFacultyScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Copy Email").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val copyAction = capturedActions.filterIsInstance<M3EFacultyAction.CopyEmail>().firstOrNull()
        assertEquals("sundar.n@vit.ac.in", copyAction?.email)
    }
}
