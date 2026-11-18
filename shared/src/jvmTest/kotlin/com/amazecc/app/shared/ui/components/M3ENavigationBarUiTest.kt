package com.amazecc.app.shared.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.m3e.components.M3ENavigationBar
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class M3ENavigationBarUiTest {

    @Test
    fun testNavigationBarRendersTabsAndSelectedLabel() = runComposeUiTest {
        val tabs = listOf(Screen.HOME, Screen.ATTENDANCE, Screen.ACADEMICS, Screen.PROFILE, Screen.MORE)

        setContent {
            AmazeM3ETheme {
                M3ENavigationBar(
                    tabs = tabs,
                    currentScreen = Screen.HOME,
                    onTabSelected = {}
                )
            }
        }

        // Active tab label should be visible
        onNodeWithText("Home").assertExists()
    }

    @Test
    fun testClickingTabTriggersCallback() = runComposeUiTest {
        val tabs = listOf(Screen.HOME, Screen.ATTENDANCE, Screen.ACADEMICS, Screen.PROFILE, Screen.MORE)
        val selectedScreens = mutableListOf<Screen>()

        setContent {
            AmazeM3ETheme {
                M3ENavigationBar(
                    tabs = tabs,
                    currentScreen = Screen.HOME,
                    onTabSelected = { selectedScreens.add(it) }
                )
            }
        }

        onNodeWithText("Home").performClick()
        assertEquals(listOf(Screen.HOME), selectedScreens)
    }

    @Test
    fun testHiddenOnLoginAndSplash() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ENavigationBar(
                    currentScreen = Screen.LOGIN,
                    onTabSelected = {}
                )
            }
        }

        onNodeWithText("Home").assertDoesNotExist()
    }
}
