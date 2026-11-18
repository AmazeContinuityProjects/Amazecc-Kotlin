package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ESettingsScreenUiTest {

    private fun sampleSettingsState() = M3ESettingsState(
        selectedThemeMode = M3EThemeMode.DARK_OLED,
        selectedPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
        honoApiBaseUrl = "https://amazecc-api-hono.sugeerth.workers.dev",
        cacheSizeText = "14.2 MB",
        isDiagnosticsRunning = false,
        diagnosticStatus = "All systems operational (Ping: 42ms)"
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
        onNodeWithText("Hono Backend API").assertExists()
        onNodeWithText("Storage & Cache (14.2 MB)").assertExists()
        onNodeWithText("All systems operational (Ping: 42ms)").assertExists()
        onNodeWithText("AmazeCC Open Source").assertExists()
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

        onAllNodesWithText("Classic Light")[0].performClick()
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

        onNodeWithText("Clear Cache").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ESettingsAction.ClearCache)
    }
}
