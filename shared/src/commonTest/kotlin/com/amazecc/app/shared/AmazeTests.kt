package com.amazecc.app.shared

import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmazeTests {

    @Test
    fun testSessionManager() {
        // Assert initial state is logged out
        SessionManager.clearSession()
        assertFalse(SessionManager.isLoggedIn)

        // Save session
        SessionManager.saveSession(
            cookies = "vtop_session=test_cookie",
            csrf = "test_csrf",
            authorizedID = "25BYB1043",
            clubToken = "rep_token"
        )

        // Assert session states
        assertTrue(SessionManager.isLoggedIn)
        assertEquals("vtop_session=test_cookie", SessionManager.cookies.value)
        assertEquals("test_csrf", SessionManager.csrf.value)
        assertEquals("25BYB1043", SessionManager.authorizedID.value)
        assertEquals("rep_token", SessionManager.clubToken.value)

        // Clear session
        SessionManager.clearSession()
        assertFalse(SessionManager.isLoggedIn)
        assertEquals(null, SessionManager.cookies.value)
    }

    @Test
    fun testAppStateNavigation() {
        // Reset to initial screen
        AppState.logout()
        assertEquals(Screen.LOGIN, AppState.currentScreen.value)

        // Navigate to Home
        AppState.navigateTo(Screen.HOME)
        assertEquals(Screen.HOME, AppState.currentScreen.value)

        // Navigate to Attendance
        AppState.navigateTo(Screen.ATTENDANCE)
        assertEquals(Screen.ATTENDANCE, AppState.currentScreen.value)

        // Navigate back (should return to Home)
        val popped = AppState.navigateBack()
        assertTrue(popped)
        assertEquals(Screen.HOME, AppState.currentScreen.value)

        // Navigate back again (should return to Login)
        val poppedAgain = AppState.navigateBack()
        assertTrue(poppedAgain)
        assertEquals(Screen.LOGIN, AppState.currentScreen.value)

        // Navigate back once more (backstack is empty, should remain on Login)
        val poppedEmpty = AppState.navigateBack()
        assertFalse(poppedEmpty)
        assertEquals(Screen.LOGIN, AppState.currentScreen.value)
    }

    @Test
    fun testAttendancePercentageCalculator() {
        // Test basic simulator algebra
        val attended = 15
        val total = 20
        val percentage = (attended.toDouble() / total.toDouble()) * 100
        assertEquals(75.0, percentage)
        
        val missedOneMorePercentage = (attended.toDouble() / (total + 1).toDouble()) * 100
        assertEquals(71.42857142857143, missedOneMorePercentage)
    }
}
