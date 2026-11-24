package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.vtop.VtopSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * D-193 part 2 — the Friend Finder host gates on a local Amaze ID session before any network and
 * maps a typed [com.amazecc.app.shared.state.SocialApiError] to the identity it names. Every call
 * is driven through the `AmazeClient.socialRequestOverride` seam: no network, no WebView.
 */
@OptIn(ExperimentalTestApi::class)
class M3EFriendFinderHostTest {

    private var token: String? = null
    private var dpop: String? = null
    private var confirmation: String? = null
    private var source: VtopSource = VtopSource.LOCAL
    private val captured = mutableListOf<AmazeClient.SocialRequestDescriptor>()

    @BeforeTest
    fun setUp() {
        token = SessionManager.amazeAccessToken.value
        dpop = SessionManager.amazeDpopHeader.value
        confirmation = SessionManager.amazeTokenConfirmation.value
        source = AmazeClient.vtopSource

        captured.clear()
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        SessionManager.clearAmazeIdSession()
        AmazeClient.socialRequestOverride = { request ->
            captured += request
            error("Unexpected social request in this test: $request")
        }
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.socialRequestOverride = null
        SessionManager.clearAmazeIdSession()
        if (!token.isNullOrBlank()) SessionManager.saveAmazeIdSession(token!!, dpop, confirmation)
        AmazeClient.setVtopSource(source)
        AmazeClient.setUseMockData(false)
    }

    @Test
    fun signedOutHostMakesNoNetworkCallAndShowsHonestSignInCopy() = runComposeUiTest {
        // No token was saved in setUp: the host must short-circuit before the transport seam.
        setContent { AmazeM3ETheme { M3EFriendFinderHost() } }

        onNodeWithText("Friend Finder needs an Amaze ID session to read your classes.").assertExists()
        onNodeWithText("Sign in with Amaze ID to use Friend Finder.").assertExists()
        assertEquals(0, captured.size, "No social request may be sent while signed out")
    }

    @Test
    fun typedVtopErrorIsMappedToTheVtopSignInMessage() = runComposeUiTest {
        SessionManager.saveAmazeIdSession("test-token")
        AmazeClient.socialRequestOverride = { request ->
            captured += request
            AmazeClient.SocialHttpResponse(401, """{"success":false,"error":"vtop_session_required"}""")
        }

        setContent { AmazeM3ETheme { M3EFriendFinderHost() } }

        // The host maps requiredSignIn == VTOP to its own VTOP-specific copy, not the Amaze one.
        onNodeWithText("Sign in again with VTOP and load this semester's attendance to open Friend Finder.")
            .assertExists()
        onNodeWithText("Sign in with Amaze ID to open Friend Finder.").assertDoesNotExist()
        assertEquals(1, captured.size, "A sign-in error stops the load chain at the failing call")
    }

    @Test
    fun retryReAttemptsAfterTheAuthSignalIsCleared() = runComposeUiTest {
        // A guarded 401 latches the one-shot gate. A token is present, but SocialClient still
        // short-circuits locally until showError consumes that signal.
        SessionManager.saveAmazeIdSession("test-token")
        SessionManager.markAmazeAuthRequired()
        var transportAttempts = 0
        AmazeClient.socialRequestOverride = { request ->
            transportAttempts++
            captured += request
            AmazeClient.SocialHttpResponse(401, """{"success":false,"error":"amazeid_session_expired"}""")
        }

        setContent { AmazeM3ETheme { M3EFriendFinderHost() } }

        onNodeWithText("Sign in with Amaze ID to open Friend Finder.").assertExists()
        assertEquals(
            0,
            transportAttempts,
            "the latched gate stops the first load before any transport call"
        )
        assertFalse(
            AmazeClient.isAmazeIdSignInRequired(),
            "showError consumes the one-shot signal so Retry is not short-circuited"
        )

        onNodeWithContentDescription("Refresh friend finder").performClick()
        waitForIdle()

        assertEquals(
            1,
            transportAttempts,
            "Retry re-attempts the social read now that the gate is cleared"
        )
    }
}
