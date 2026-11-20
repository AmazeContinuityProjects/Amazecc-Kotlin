package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.domain.overhaul.allSettingsSections
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.settings.AdvancedSettingsHub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ESettingsScreenUiTest {

    private fun sampleSettingsState() = M3ESettingsState(
        selectedThemeMode = M3EThemeMode.DARK_OLED,
        selectedPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
        honoApiBaseUrl = "https://hono.amazecc.com",
        cacheSizeText = "14.2 MB",
        isDiagnosticsRunning = false,
        diagnosticStatus = "All systems operational (Ping: 42ms)",
        // The reworked M3E Settings screen is driven by the section list; without
        // it no cards render at all (the host supplies this in production).
        visibleSettingsSections = allSettingsSections()
    )

    @Test
    fun testSettingsRendersThemeAndBackendInfo() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ESettingsScreen(
                    state = sampleSettingsState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Settings").assertExists()
        onNodeWithText("Theme & Display Mode").assertExists()
        onNodeWithText("M3 Expressive Color Palette").assertExists()
        // D-059 renamed the backend card to the API target picker. It is below
        // the fold in the LazyColumn, so scroll to it before asserting (same as
        // the storage card below).
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("API Target"))
        onNodeWithText("API Target").assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Storage & Cache (14.2 MB)"))
        onNodeWithText("Storage & Cache (14.2 MB)").assertExists()
        onNodeWithText("All systems operational (Ping: 42ms)").assertExists()
        // The old "AmazeCC Open Source" card was replaced by Backup / Export Settings.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Backup / Export Settings"))
        onNodeWithText("Backup / Export Settings").assertExists()
    }

    @Test
    fun testThemeModeSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ESettingsAction>()

        setContent {
            AmazeM3ETheme {
                M3ESettingsScreen(
                    state = sampleSettingsState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        // Derive the label from the enum so a future copy change cannot stale this test.
        onAllNodesWithText(M3EThemeMode.LIGHT.label)[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ESettingsAction.SelectThemeMode)
        assertEquals(M3EThemeMode.LIGHT, (capturedActions[0] as M3ESettingsAction.SelectThemeMode).mode)
    }

    /**
     * D-121 regression: the crash path is the AdvancedSettingsHub item mounted by
     * M3ESettingsScreen (D-037). LazyColumn does not compose offscreen items, so the
     * existing tests never reached it. Scroll to it explicitly and assert it composes.
     */
    @Test
    fun testSettingsComposesAdvancedHubWithoutThrowing() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ESettingsScreen(
                    state = sampleSettingsState().copy(
                        quietHoursEnabled = true,
                        appIconTheme = com.amazecc.app.shared.utils.AppIconTheme.FIRE,
                    ),
                    onAction = {}
                )
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Display & Layout"))
        onNodeWithText("Display & Layout").assertExists()
        onNodeWithText("Academics & Semester").assertExists()
        onNodeWithText("Danger Zone").assertExists()
    }

    /** D-121: render the hub standalone with realistic state; composition must not throw. */
    @Test
    fun testAdvancedSettingsHubRendersWithoutThrowing() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                AdvancedSettingsHub()
            }
        }

        onNodeWithText("Display & Layout").assertExists()
        onNodeWithText("Bottom Navigation").assertExists()
        // D-122: "Credentials" is ambiguous ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the hub card title and the sync
        // module toggle inside it both carry that exact text. Assert both are
        // present rather than asking assertExists for a single node.
        onAllNodesWithText("Credentials").assertCountEquals(2)
        onNodeWithText("Data, Sync & Alerts").assertExists()
    }

    /**
     * D-121 removed the hub's own verticalScroll to avoid a nested scroller
     * inside the host LazyColumn. In production that LazyColumn supplies the
     * scrolling ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â including the `ScrollToIndex` that `performScrollToNode`
     * needs. Wrap the hub in the same LazyColumn here so the test mirrors real
     * usage without reintroducing a scroller into the composable under test.
     */
    private fun ComposeUiTest.setScrollableHub() {
        setContent {
            AmazeM3ETheme {
                LazyColumn {
                    item { AdvancedSettingsHub() }
                }
            }
        }
    }

    /**
     * D-122: the web-parity controls live in the same D-121 hub the Settings
     * screen mounts. They must compose (no nested scroller reintroduced) and be
     * reachable by scrolling the parent.
     */
    @Test
    fun testWebParityControlsRenderInAdvancedHub() = runComposeUiTest {
        setScrollableHub()

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Default academic calendar"))
        onNodeWithText("Default academic calendar").assertExists()
        onNodeWithText("Attendance target").assertExists()
        onNodeWithText("Residential status").assertExists()
        onNodeWithText("Assignment reminders").assertExists()
        onNodeWithText("Task reminders").assertExists()
    }

    /** D-122: the target group writes both the web blob and the live attendance target. */
    @Test
    fun testAttendanceTargetPersistsToWebBlobAndBehavior() = runComposeUiTest {
        val previousTarget = AppState.customAttendanceTarget.value
        val previousBus = AppState.isBusSubscriber.value
        val previousBlob = SettingsManager.getWebSettings()
        try {
            setScrollableHub()

            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Attendance target"))
            // Connected-button-group segments are clickable Boxes inside a
            // horizontally scrolling row; invoke their OnClick action directly
            // so the scroll offset cannot swallow a mouse click.
            onNodeWithText("80%").performSemanticsAction(SemanticsActions.OnClick)

            assertEquals(80, SettingsManager.getWebSettings().targetAttendance)
            assertEquals(80f, AppState.customAttendanceTarget.value)
            assertEquals(false, AppState.isBusSubscriber.value)
        } finally {
            AppState.setCustomAttendanceTarget(previousTarget)
            AppState.setBusSubscriber(previousBus)
            SettingsManager.setWebSettings(previousBlob)
        }
    }

    /** D-122: the calendar group maps the web id/name pair and persists both stores. */
    @Test
    fun testCalendarSelectionPersists() = runComposeUiTest {
        val previousCalendar = SettingsManager.getPreferredCalendar()
        val previousBlob = SettingsManager.getWebSettings()
        try {
            setScrollableHub()

            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Default academic calendar"))
            onNodeWithText("General Semester").performSemanticsAction(SemanticsActions.OnClick)

            assertEquals("General Semester", SettingsManager.getPreferredCalendar())
            assertEquals("ALL", SettingsManager.getWebSettings().calendarType)
        } finally {
            SettingsManager.savePreferredCalendar(previousCalendar ?: "")
            SettingsManager.setWebSettings(previousBlob)
        }
    }

    @Test
    fun testClearCacheEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ESettingsAction>()

        setContent {
            AmazeM3ETheme {
                M3ESettingsScreen(
                    state = sampleSettingsState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Clear Cache"))
        onNodeWithText("Clear Cache").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ESettingsAction.ClearCache)
    }
}
