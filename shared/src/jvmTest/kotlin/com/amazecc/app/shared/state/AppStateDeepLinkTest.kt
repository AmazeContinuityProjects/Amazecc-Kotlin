package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-193 part 3 — deep-link parsing/application and the invite hand-off to Social.
 * Headless: only AppState flows are touched; no HTTP, no WebView, no composables.
 */
class AppStateDeepLinkTest {

    @BeforeTest
    fun setUp() {
        AppState.consumeIncomingDeepLink()
        AppState.consumePendingInviteHandle()
        AppState.closeGuestLanding()
        AppState.navigateTo(Screen.HOME)
    }

    @AfterTest
    fun tearDown() {
        AppState.consumeIncomingDeepLink()
        AppState.consumePendingInviteHandle()
    }

    // -- open=friend-finder ---------------------------------------------------------------

    @Test
    fun `web open friend finder navigates to Friend Finder`() {
        assertTrue(AppState.applyDeepLink("https://amazecc.app/?open=friend-finder"))
        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
        assertNull(AppState.pendingInviteHandle.value)
    }

    @Test
    fun `amazecc tab friend finder also navigates`() {
        assertTrue(AppState.applyDeepLink("amazecc://tab?tab=friend-finder"))
        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
    }

    @Test
    fun `amazecc tab friend finder parses to a Tab route`() {
        val route = AppState.parseDeepLink("amazecc://tab?tab=friend-finder")
        assertTrue(route is AppState.DeepLinkRoute.Tab)
        assertEquals(Screen.FRIEND_FINDER, (route as AppState.DeepLinkRoute.Tab).screen)
    }

    // -- add=<handle> invites -------------------------------------------------------------

    @Test
    fun `web invite parses to an AddPeer route`() {
        val route = AppState.parseDeepLink("https://amazecc.app/?add=AMZ-2K7P-9RTW")
        assertTrue(route is AppState.DeepLinkRoute.AddPeer)
        assertEquals("AMZ-2K7P-9RTW", (route as AppState.DeepLinkRoute.AddPeer).handle)
    }

    @Test
    fun `add handle parks the invite and switches to Social`() {
        assertTrue(AppState.applyDeepLink("https://amazecc.app/?add=AMZ-2K7P-9RTW"))
        assertEquals(Screen.SOCIAL, AppState.currentScreen.value)
        assertEquals("AMZ-2K7P-9RTW", AppState.pendingInviteHandle.value)
        // The host clears the note once it has opened the prefilled sheet.
        assertEquals("AMZ-2K7P-9RTW", AppState.consumePendingInviteHandle())
        assertNull(AppState.pendingInviteHandle.value)
    }

    @Test
    fun `add handle normalises case and tolerates trailing params`() {
        assertTrue(AppState.applyDeepLink("https://amazecc.app/?add=amz-2k7p-9rtw&src=qr"))
        assertEquals(Screen.SOCIAL, AppState.currentScreen.value)
        assertEquals("AMZ-2K7P-9RTW", AppState.consumePendingInviteHandle())
    }

    @Test
    fun `invalid handles are ignored without navigating or parking`() {
        val invalid = listOf(
            "AMZ-2K7I-9RTW",       // I is outside the Crockford-style alphabet
            "AMZ-2K7P-9RT",       // one character short
            "AMZ-2K7P-9RTW-EXTRA",
            "AMZ2K7P9RTW",        // no dashes
            ""
        )
        invalid.forEach { handle ->
            assertFalse(AppState.applyDeepLink("https://amazecc.app/?add=$handle"), handle)
            assertNull(AppState.pendingInviteHandle.value, handle)
        }
        assertEquals(Screen.HOME, AppState.currentScreen.value)
    }

    @Test
    fun `repeat visits replace the parked handle and the raw link is cleared`() {
        AppState.setPendingDeepLink("https://amazecc.app/?add=AMZ-2K7P-9RTW")
        AppState.setPendingDeepLink("https://amazecc.app/?add=AMZ-8H3M-4N5Q")
        assertTrue(AppState.consumeIncomingDeepLink())
        assertNull(AppState.incomingDeepLink.value)
        assertEquals(Screen.SOCIAL, AppState.currentScreen.value)
        assertEquals("AMZ-8H3M-4N5Q", AppState.pendingInviteHandle.value)

        assertEquals("AMZ-8H3M-4N5Q", AppState.consumePendingInviteHandle())
        assertNull(AppState.pendingInviteHandle.value)

        // A later, non-invite link works and cannot resurrect the consumed invite.
        AppState.setPendingDeepLink("https://amazecc.app/?open=friend-finder")
        assertTrue(AppState.consumeIncomingDeepLink())
        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
        assertNull(AppState.pendingInviteHandle.value)
    }

    // -- other delivery paths stay intact -------------------------------------------------

    @Test
    fun `target screen extra travels through the pending queue`() {
        AppState.setPendingDeepLink(targetScreen = "FRIEND_FINDER")
        assertTrue(AppState.consumeIncomingDeepLink())
        assertEquals(Screen.FRIEND_FINDER, AppState.currentScreen.value)
        assertNull(AppState.incomingDeepLink.value)
    }

    @Test
    fun `invalid target screen extra is dropped`() {
        AppState.setPendingDeepLink(targetScreen = "NOT_A_SCREEN")
        assertNull(AppState.incomingDeepLink.value)
    }

    @Test
    fun `share codes and other tabs still route`() {
        assertTrue(AppState.applyDeepLink("amazecc://share#s=not-a-real-code"))
        assertEquals(Screen.SOCIAL, AppState.currentScreen.value)
        assertTrue(AppState.applyDeepLink("amazecc://tab?tab=home"))
        assertEquals(Screen.HOME, AppState.currentScreen.value)
        assertNull(AppState.pendingInviteHandle.value)
    }

    // -- Social hub sign-in contract (host consumes these APIs) ---------------------------

    @Test
    fun `typed errors pick the right sign in identity`() {
        assertEquals(SocialSignInKind.AMAZE_ID, SocialApiError("amazeid_session_expired").requiredSignIn)
        assertEquals(SocialSignInKind.VTOP, SocialApiError("vtop_session_expired").requiredSignIn)
        assertEquals(SocialSignInKind.NONE, SocialApiError("handle_not_found").requiredSignIn)
        val vtop = SocialApiError("signin_required")
        assertTrue(vtop.needsVtopSignIn)
        assertFalse(vtop.needsAmazeIdSignIn)
        assertEquals(
            "Sign in with Amaze ID to use the friend finder.",
            SocialClient.messageFor("amazeid_signin_required")
        )
    }

    @Test
    fun `Amaze ID gate is local and its auth signal is consumable`() {
        val token = SessionManager.amazeAccessToken.value
        try {
            SessionManager.clearAmazeIdSession()
            assertFalse(SocialClient.hasAmazeIdSession())

            SessionManager.saveAmazeIdSession("test-token")
            assertTrue(SocialClient.hasAmazeIdSession())

            SessionManager.markAmazeAuthRequired()
            assertTrue(AmazeClient.isAmazeIdSignInRequired())
            AmazeClient.clearAmazeIdSignInRequired()
            assertFalse(AmazeClient.isAmazeIdSignInRequired())
        } finally {
            SessionManager.clearAmazeIdSession()
            if (!token.isNullOrBlank()) SessionManager.saveAmazeIdSession(token)
        }
    }
}
