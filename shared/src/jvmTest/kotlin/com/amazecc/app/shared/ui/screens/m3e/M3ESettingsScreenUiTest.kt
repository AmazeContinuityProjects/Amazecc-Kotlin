package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
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
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
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
