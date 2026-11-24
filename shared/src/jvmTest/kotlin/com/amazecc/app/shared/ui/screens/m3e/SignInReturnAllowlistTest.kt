package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SocialClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-193 follow-up: Friend Finder and Social emit real sign-in actions, so their return trips must
 * survive [SignInReturn.prepare]. Only allowlisted surfaces park a note, and they must be routed
 * to the destination that can actually persist an Amaze ID token — not the VTOP-only login form.
 * Headless (no UI): `openLogin = false` keeps the note-only tests to the note itself; the
 * routing tests leave it on so the AppState destination is asserted too.
 */
class SignInReturnAllowlistTest {

    @BeforeTest
    fun setUp() {
        SignInReturn.clear()
        AmazeClient.clearAmazeIdSignInRequired()
        SessionManager.clearAmazeIdSession()
        AppState.navigateTo(Screen.HOME)
    }

    @AfterTest
    fun tearDown() {
        SignInReturn.clear()
        SessionManager.clearAmazeIdSession()
        AmazeClient.clearAmazeIdSignInRequired()
        AppState.navigateTo(Screen.HOME)
    }

    @Test
    fun `friend finder sign in is parked for the return trip`() {
        SignInReturn.prepare(Screen.FRIEND_FINDER, openLogin = false)
        assertEquals(Screen.FRIEND_FINDER, SignInReturn.peek())
        assertEquals(Screen.FRIEND_FINDER, SignInReturn.take())
        assertNull(SignInReturn.take(), "the note is one-shot")
    }

    @Test
    fun `social remains an allowed return target`() {
        SignInReturn.prepare(Screen.SOCIAL, openLogin = false)
        assertEquals(Screen.SOCIAL, SignInReturn.take())
    }

    @Test
    fun `a non social surface is still refused`() {
        SignInReturn.prepare(Screen.HOME, openLogin = false)
        assertNull(SignInReturn.peek())
    }

    @Test
    fun `amaze id sign in opens the real destination and completes back to friend finder`() {
        SignInReturn.prepare(Screen.FRIEND_FINDER)

        assertEquals(
            Screen.AMAZE_ID_SIGN_IN,
            AppState.currentScreen.value,
            "the VTOP-only LOGIN form can never save an Amaze ID token"
        )
        assertEquals(Screen.FRIEND_FINDER, SignInReturn.peek(), "the original target is parked")

        // Simulate the host's verified Done control after a real token was saved.
        SessionManager.saveAmazeIdSession("real-looking-token")
        assertTrue(SocialClient.hasAmazeIdSession())
        SignInReturn.completeSignIn(Screen.HOME)

        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
        assertNull(SignInReturn.peek(), "the return note is consumed by the completion")
    }

    @Test
    fun `social sign in opens the same real amaze id destination`() {
        SignInReturn.prepare(Screen.SOCIAL)

        assertEquals(Screen.AMAZE_ID_SIGN_IN, AppState.currentScreen.value)
        assertEquals(Screen.SOCIAL, SignInReturn.peek())

        SessionManager.saveAmazeIdSession("real-looking-token")
        SignInReturn.completeSignIn(Screen.HOME)

        assertEquals(Screen.SOCIAL, AppState.currentScreen.value)
    }

    @Test
    fun `non social sign in keeps the legacy vtop login destination`() {
        SignInReturn.prepare(Screen.HOME)

        assertEquals(Screen.LOGIN, AppState.currentScreen.value)
        assertNull(SignInReturn.peek(), "no return trip is parked for a non-social surface")
    }

    @Test
    fun `a saved token clears the gate and allows the return trip to complete`() {
        SessionManager.markAmazeAuthRequired()
        assertTrue(AmazeClient.isAmazeIdSignInRequired(), "a guarded 401 latches the one-shot gate")
        assertFalse(SocialClient.hasAmazeIdSession())

        SignInReturn.prepare(Screen.FRIEND_FINDER, openLogin = false)
        SessionManager.saveAmazeIdSession("real-looking-token")

        assertFalse(
            AmazeClient.isAmazeIdSignInRequired(),
            "saving a token clears the sign-in-required signal"
        )
        assertTrue(SocialClient.hasAmazeIdSession(), "the host's Done control may now complete")

        SignInReturn.completeSignIn(Screen.HOME)
        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
    }
}
