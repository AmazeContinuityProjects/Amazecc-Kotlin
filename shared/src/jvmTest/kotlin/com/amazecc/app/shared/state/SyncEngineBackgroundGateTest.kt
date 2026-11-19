package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.vtop.CaptchaAnswer
import com.amazecc.app.shared.vtop.CaptchaChallenge
import com.amazecc.app.shared.vtop.Vtop
import com.amazecc.app.shared.vtop.VtopSession
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the forced-VTOP-login sync gate is safe for background/scheduled sweeps while the
 * foreground path still performs a genuine fresh login.
 */
class SyncEngineBackgroundGateTest {

    private var previousVtopSource: VtopSource = VtopSource.LOCAL

    @BeforeTest
    fun setUp() {
        previousVtopSource = AmazeClient.vtopSource
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        VtopSession.clear()
        SyncEngine.interactiveVtopOverride = null
        AmazeClient.sessionLoginOverride = null
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.sessionLoginOverride = null
        SyncEngine.interactiveVtopOverride = null
        AmazeClient.setVtopSource(previousVtopSource)
        VtopSession.clear()
        Vtop.cancelCaptcha()
    }

    /**
     * (a) No interactive VTOP (background/alarm sweep): the gate verifies the cached session only.
     * With a stale session it returns false and never enters the forced-login path — including not
     * clearing the cached tokens/cookies that the fresh-login path would discard.
     */
    @Test
    fun backgroundGateRejectsInvalidCachedSessionWithoutForcedLogin() = runBlocking {
        SyncEngine.interactiveVtopOverride = { false }

        val staleTs = Clock.System.now().toEpochMilliseconds() - (15 * 60 * 1000L)
        VtopSession.save(csrf = "stale_csrf", authorizedID = "21BCE0001", authorizedIDX = "idx_stale")
        VtopSession.markActive(staleTs)
        assertTrue(VtopSession.isExpired, "Precondition: the cached session must be expired")

        var forcedLoginCalls = 0
        AmazeClient.sessionLoginOverride = { forcedLoginCalls++ ; true }

        val ok = SyncEngine.ensureAuthenticatedSession(setOf(SyncModule.ATTENDANCE))

        assertFalse(ok, "A stale cached session must fail the background gate")
        assertEquals(0, forcedLoginCalls, "Background gate must not invoke the forced-login path")
        assertEquals("stale_csrf", VtopSession.csrf.value, "Background gate must not clear cached cookies/tokens")
        assertEquals("21BCE0001", VtopSession.authorizedID.value, "Background gate must not clear cached cookies/tokens")
    }

    /** (a) With a still-valid cached session the background gate proceeds without any login. */
    @Test
    fun backgroundGateAcceptsValidCachedSessionWithoutForcedLogin() = runBlocking {
        SyncEngine.interactiveVtopOverride = { false }

        VtopSession.save(csrf = "valid_csrf", authorizedID = "21BCE0002", authorizedIDX = "idx_valid")
        VtopSession.markActive()
        assertTrue(VtopSession.isValid, "Precondition: the cached session must be valid")

        var forcedLoginCalls = 0
        AmazeClient.sessionLoginOverride = { forcedLoginCalls++ ; true }

        val ok = SyncEngine.ensureAuthenticatedSession(setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE))

        assertTrue(ok, "A valid cached session must satisfy the background gate")
        assertEquals(0, forcedLoginCalls, "Background gate must not invoke the forced-login path")
    }

    /**
     * (b) Foreground sweep: even with an Activity and a cached-fresh session, the gate still forces
     * a real login (forceFresh = true) rather than reusing cached tokens.
     */
    @Test
    fun foregroundGateStillForcesFreshLogin() = runBlocking {
        SyncEngine.interactiveVtopOverride = { true }

        VtopSession.save(csrf = "cached_csrf", authorizedID = "21BCE0003", authorizedIDX = "idx_cached")
        VtopSession.markActive()
        assertTrue(VtopSession.isValid, "A cached-fresh session is present")

        var forcedLoginCalls = 0
        var forcedFlag: Boolean? = null
        AmazeClient.sessionLoginOverride = { forceFresh ->
            forcedLoginCalls++
            forcedFlag = forceFresh
            true
        }

        val ok = SyncEngine.ensureAuthenticatedSession(setOf(SyncModule.ATTENDANCE))

        assertTrue(ok)
        assertEquals(1, forcedLoginCalls, "Foreground gate must perform a real login")
        assertEquals(true, forcedFlag, "Foreground gate must force a fresh login")
    }

    /** (c) A UI captcha prompt nobody answers fails fast instead of parking the sweep forever. */
    @Test
    fun uiCaptchaWaitFailsFast() = runBlocking {
        val challenge = CaptchaChallenge(base64 = "dummy", isRecaptcha = false)
        val start = Clock.System.now().toEpochMilliseconds()

        val answer = withTimeout(1_000L) {
            Vtop.awaitUiCaptcha(challenge, timeoutMs = 60L)
        }
        val elapsed = Clock.System.now().toEpochMilliseconds() - start

        assertTrue(answer is CaptchaAnswer.Cancelled, "An unanswered prompt must resolve as cancelled")
        assertTrue(elapsed < 1_000L, "The prompt must time out quickly, not hang ($elapsed ms)")
        assertNull(Vtop.captchaRequest.value, "A timed-out prompt must be cleared from the UI state")
    }
}
