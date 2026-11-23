package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-131: the compact nav has two explicit treatments. EXPANDING restores the
 * old pill (icon-only unselected items, animated label on the selected one) but
 * must never drop the destination name from the a11y tree; LABELLED_DOCKED keeps
 * every label visible. Both must still emit the clicked destination's id.
 */
@OptIn(ExperimentalTestApi::class)
class M3EFloatingToolbarModesUiTest {

    private val fourDestinations = listOf(
        M3EDestination.HOME,
        M3EDestination.ATTENDANCE,
        M3EDestination.ACADEMICS,
        M3EDestination.MORE
    )

    @Test
    fun expandingHidesUnselectedTextButKeepsEveryContentDescription() = runComposeUiTest {
        val selected = mutableStateOf(M3EDestination.HOME)
        val clicked = mutableListOf<M3EDestination>()

        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFloatingToolbar(
                    currentDestination = selected.value,
                    onDestinationSelected = {
                        clicked.add(it)
                        selected.value = it
                    },
                    destinations = fourDestinations,
                    style = M3ENavBarStyle.EXPANDING
                )
            }
        }
        waitForIdle()

        // A11y contract: every destination is named even though three have no text.
        // substring = true survives Compose merging the visible Text into the
        // clickable node's content description (e.g. "More, More").
        fourDestinations.forEach {
            onNodeWithContentDescription(it.label, substring = true).assertExists()
        }
        // Selected label is shown; unselected label TEXT is absent.
        onNodeWithText(M3EDestination.HOME.label).assertExists()
        listOf(M3EDestination.ATTENDANCE, M3EDestination.ACADEMICS, M3EDestination.MORE)
            .forEach { onAllNodesWithText(it.label).assertCountEquals(0) }

        // Clicking a non-selected destination emits its id and moves the label.
        onNodeWithContentDescription(M3EDestination.MORE.label, substring = true).performClick()
        waitForIdle()

        assertEquals(listOf(M3EDestination.MORE), clicked)
        onNodeWithText(M3EDestination.MORE.label).assertExists()
        onAllNodesWithText(M3EDestination.HOME.label).assertCountEquals(0)
    }

    @Test
    fun labelledDockedShowsEveryLabelAndStillEmitsClicks() = runComposeUiTest {
        val clicked = mutableListOf<M3EDestination>()

        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFloatingToolbar(
                    currentDestination = M3EDestination.HOME,
                    onDestinationSelected = { clicked.add(it) },
                    destinations = fourDestinations,
                    style = M3ENavBarStyle.LABELLED_DOCKED
                )
            }
        }
        waitForIdle()

        fourDestinations.forEach {
            onNodeWithContentDescription(it.label, substring = true).assertExists()
            onNodeWithText(it.label).assertExists()
        }

        onNodeWithContentDescription(M3EDestination.ACADEMICS.label, substring = true).performClick()
        waitForIdle()

        assertEquals(listOf(M3EDestination.ACADEMICS), clicked)
    }
}
