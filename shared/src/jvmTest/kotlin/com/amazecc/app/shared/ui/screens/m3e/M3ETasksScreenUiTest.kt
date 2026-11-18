package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ETaskItem
import com.amazecc.app.shared.domain.overhaul.M3ETaskKind
import com.amazecc.app.shared.domain.overhaul.M3ETaskStatus
import com.amazecc.app.shared.domain.overhaul.M3ETasksAction
import com.amazecc.app.shared.domain.overhaul.M3ETasksFilter
import com.amazecc.app.shared.domain.overhaul.M3ETasksState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ETasksScreenUiTest {

    private fun sampleTasksState() = M3ETasksState(
        tasks = listOf(
            M3ETaskItem(
                id = "task_01",
                title = "Digital Assignment 2",
                kind = M3ETaskKind.DIGITAL_ASSIGNMENT,
                status = M3ETaskStatus.PENDING,
                courseCode = "CSE3001",
                courseTitle = "Database Management Systems",
                dueDate = "29 Nov 2026",
                slotName = "D1+TD1",
                isOverdue = false
            )
        ),
        filteredTasks = listOf(
            M3ETaskItem(
                id = "task_01",
                title = "Digital Assignment 2",
                kind = M3ETaskKind.DIGITAL_ASSIGNMENT,
                status = M3ETaskStatus.PENDING,
                courseCode = "CSE3001",
                courseTitle = "Database Management Systems",
                dueDate = "29 Nov 2026",
                slotName = "D1+TD1",
                isOverdue = false
            )
        ),
        activeFilter = M3ETasksFilter.ALL,
        searchQuery = "",
        pendingCount = 1,
        completedCount = 0
    )

    @Test
    fun testTasksScreenRendersTaskList() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETasksScreen(
                    state = sampleTasksState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Tasks & Planner").assertExists()
        onNodeWithText("Digital Assignment 2").assertExists()
        onNodeWithText("CSE3001").assertExists()
        onNodeWithText("Digital Assignment").assertExists()
        onNodeWithText("Due: 29 Nov 2026").assertExists()
        onNodeWithText("• Slot D1+TD1").assertExists()
    }

    @Test
    fun testToggleTaskStatusEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETasksAction>()

        setContent {
            AmazeM3ETheme {
                M3ETasksScreen(
                    state = sampleTasksState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Toggle status").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ETasksAction.ToggleTaskStatus)
        assertEquals("task_01", (capturedActions[0] as M3ETasksAction.ToggleTaskStatus).taskId)
    }

    @Test
    fun testOpenNewTaskDialogEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETasksAction>()

        setContent {
            AmazeM3ETheme {
                M3ETasksScreen(
                    state = sampleTasksState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("New Task").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ETasksAction.OpenCreateDialog)
    }
}
