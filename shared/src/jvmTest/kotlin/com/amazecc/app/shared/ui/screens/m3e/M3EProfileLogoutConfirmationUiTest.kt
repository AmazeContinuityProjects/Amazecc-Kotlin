package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.ui.m3e.components.M3EPageActionsMenuTestTags

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-118: the profile's destructive Logout action must be confirmed once before it is emitted.
 *
 * The stateless surface emits [M3EProfileAction.SignOut] only from the confirmation sheet's
 * Confirm button; the host owns the single `AppState.logout()` call. Every dismissal route of
 * the shared sheet funnels into its `onDismiss`, which here only hides the sheet, so cancel
 * leaves both the emitted actions and the rendered profile untouched.
 */
@OptIn(ExperimentalTestApi::class)
class M3EProfileLogoutConfirmationUiTest {

    private fun sampleProfileState() = M3EProfileState(
        studentName = "Aditya Sharma",
        registrationNumber = "22BCE1045",
        applicationNumber = "1202204567",
        programme = "B.Tech Computer Science & Engineering",
        school = "SCOPE",
        campus = "Vellore Campus",
        dob = "14 Aug 2004",
        bloodGroup = "O +ve",
        email = "aditya.sharma2022@vitstudent.ac.in",
        phone = "+91 98401 22334",
        proctorName = "Dr. Anita R",
        proctorEmail = "anita.r@vit.ac.in",
        hostelBlock = "Q Block",
        hostelRoom = "514",
        cgpa = 9.24,
        attendancePercentage = 91.5
    )

    private fun ComposeUiTest.openConfirmation() {
        // D-116/D-124: sign-out is an item of the page-root overlay FAB.
        onNodeWithTag(M3EPageActionsMenuTestTags.HOST_ROOT).assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertExists()
        onNodeWithContentDescription("More actions").performClick()
        waitForIdle() // let the M3E enter spring mount the labelled item
        // Fire the item's own OnClick semantics action: the menu items sit below
        // the toggle's 56dp box, where desktop mouse injection misses them.
        // The menu is index-based: Refresh=0, Sign Out=1. Use the primitive's generated
        // item tag (on the actual clickable Row) and its OnClick semantics action.
        onNodeWithTag(M3EPageActionsMenuTestTags.item(1), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM_SHEET, useUnmergedTree = true).assertExists()
    }

    @Test
    fun signOutOpensConfirmationWithHonestClearListBeforeEmitting() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = sampleProfileState(), onAction = { actions.add(it) })
            }
        }

        openConfirmation()

        onNodeWithText("Log out of AmazeCC?").assertExists()
        // Honest copy naming exactly what AppState.logout() clears.
        onNodeWithText("sign-in", substring = true).assertExists()
        onNodeWithText("credentials", substring = true).assertExists()
        onNodeWithText("active session", substring = true).assertExists()
        onNodeWithText("local app data", substring = true).assertExists()
        onNodeWithText("resets to defaults", substring = true).assertExists()
        // The icon alone never logs out.
        assertEquals(0, actions.size)
    }

    @Test
    fun cancelLeavesStateUntouched() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = sampleProfileState(), onAction = { actions.add(it) })
            }
        }

        openConfirmation()
        onNodeWithTag(M3EProfileTestTags.LOGOUT_CANCEL).performClick()

        onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM_SHEET, useUnmergedTree = true).assertDoesNotExist()
        // Profile is still intact ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â no navigation / logout side effect.
        onNodeWithText("Student Profile").assertExists()
        // The menu collapses after the selection; reopen it to see the trigger again.
        onNodeWithContentDescription("More actions").performClick()
        waitForIdle()
        onNodeWithTag(M3EPageActionsMenuTestTags.item(1), useUnmergedTree = true).assertExists()
        assertEquals(0, actions.size)
    }

    @Test
    fun everySheetDismissalRouteCancelsWithoutSideEffects() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = sampleProfileState(), onAction = { actions.add(it) })
            }
        }

        // X / backdrop / Escape / system back all funnel through the primitive's onDismiss.
        // Both labelled close surfaces (and any nested icon) are exercised here; whichever
        // order they appear in, neither may emit SignOut.
        val dismissals = listOf(
            { onAllNodesWithContentDescription("Dismiss sheet")[0].performClick() },
            {
                val closeNodes = onAllNodesWithContentDescription("Dismiss sheet")
                closeNodes[closeNodes.fetchSemanticsNodes().lastIndex].performClick()
            }
        )
        dismissals.forEach { dismiss ->
            openConfirmation()
            dismiss()
            onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM_SHEET, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("Student Profile").assertExists()
            assertEquals(0, actions.size)
        }
    }

    @Test
    fun confirmEmitsLogoutActionExactlyOnce() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = sampleProfileState(), onAction = { actions.add(it) })
            }
        }

        openConfirmation()
        onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM).performClick()

        onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM_SHEET, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EProfileAction.SignOut)

        // A second, separate confirmation emits a second single action â€” the first
        // confirmation is never duplicated or replayed.
        openConfirmation()
        onNodeWithTag(M3EProfileTestTags.LOGOUT_CONFIRM).performClick()
        assertEquals(2, actions.size)
        assertTrue(actions[1] is M3EProfileAction.SignOut)
    }

    @Test
    fun logoutIconIsLabelledForAccessibilityAsBefore() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = sampleProfileState(), onAction = {})
            }
        }
        onNodeWithContentDescription("More actions").performClick()
        waitForIdle()
        onNodeWithContentDescription("Sign Out").assertExists()
    }
}
