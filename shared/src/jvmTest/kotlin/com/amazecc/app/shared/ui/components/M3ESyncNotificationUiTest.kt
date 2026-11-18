package com.amazecc.app.shared.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.ui.m3e.components.M3ESyncNotification
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ESyncNotificationUiTest {

    @BeforeTest
    fun setup() {
        SyncEngine.resetAllStates()
        SyncEngine.setShowSyncDialog(false, minimized = false)
    }

    @AfterTest
    fun tearDown() {
        SyncEngine.setShowSyncDialog(false, minimized = false)
    }

    @Test
    fun testHiddenWhenNotSyncingAndNotRequested() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = {})
            }
        }

        // Neither minimized energy bar nor sheet should exist
        onNodeWithText("Authentication & Session").assertDoesNotExist()
        onNodeWithText("Attendance & Timetable").assertDoesNotExist()
    }

    @Test
    fun testExpandedModalShowsMilestones() = runComposeUiTest {
        SyncEngine.setShowSyncDialog(true, minimized = false)

        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = {})
            }
        }

        // Milestones should be rendered in the expanded bottom sheet modal
        onNodeWithText("Authentication & Session").assertExists()
        onNodeWithText("Attendance & Timetable").assertExists()
        onNodeWithText("Marks & Grades").assertExists()
        onNodeWithText("Local Storage & Finalize").assertExists()
    }

    @Test
    fun testMinimizedPillCanExpand() = runComposeUiTest {
        SyncEngine.setShowSyncDialog(true, minimized = true)

        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = {})
            }
        }

        // Percentage badge exists on minimized pill
        onNodeWithText("0%").assertExists()

        // Clicking the pill expands into modal
        onNodeWithText("0%").performClick()

        // Milestone titles should now appear
        onNodeWithText("Authentication & Session").assertExists()
    }

    @Test
    fun testDismissCallbackInvoked() = runComposeUiTest {
        SyncEngine.setShowSyncDialog(true, minimized = false)
        var dismissed = false

        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = { dismissed = true })
            }
        }

        onNodeWithText("Cancel Sync").performClick()
        assertTrue(dismissed)
    }
}
