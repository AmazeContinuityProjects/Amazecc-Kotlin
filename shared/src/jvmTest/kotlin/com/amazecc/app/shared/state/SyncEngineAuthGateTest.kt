package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.vtop.VtopSession
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*

class SyncEngineAuthGateTest {

    private var previousVtopSource: VtopSource = VtopSource.LOCAL

    @BeforeTest
    fun setUp() {
        previousVtopSource = AmazeClient.vtopSource
        VtopSession.clear()
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.sessionLoginOverride = null
        SyncEngine.interactiveVtopOverride = null
        AmazeClient.setVtopSource(previousVtopSource)
        VtopSession.clear()
    }

    @Test
    fun testVtopSessionIsValidHelper() {
        // Initial state: no session, expired
        VtopSession.clear()
        assertFalse(VtopSession.hasSession, "Initial session must not have tokens")
        assertTrue(VtopSession.isExpired, "Initial session must be expired")
        assertFalse(VtopSession.isValid, "Initial session must not be valid")

        // Fresh session populated
        val now = Clock.System.now().toEpochMilliseconds()
        VtopSession.save(csrf = "test_csrf_token", authorizedID = "21BCE1234", authorizedIDX = "idx_123")
        VtopSession.markActive(now)
        assertTrue(VtopSession.hasSession, "Saved session must have tokens")
        assertFalse(VtopSession.isExpired, "Fresh session must not be expired")
        assertTrue(VtopSession.isValid, "Fresh session must be valid")

        // Stale / expired session (> 9.5 minutes old)
        val expiredTs = now - (10 * 60 * 1000L) // 10 minutes ago
        VtopSession.markActive(expiredTs)
        assertTrue(VtopSession.hasSession, "Stale session still has tokens in memory")
        assertTrue(VtopSession.isExpired, "10-minute-old session must be marked expired")
        assertFalse(VtopSession.isValid, "10-minute-old session must not be valid")
    }

    @Test
    fun testSweepForcesFreshLoginEvenWhenSessionIsActiveAndFresh() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        val now = Clock.System.now().toEpochMilliseconds()
        VtopSession.save(csrf = "active_csrf", authorizedID = "21BCE9999", authorizedIDX = "idx_active")
        VtopSession.markActive(now)

        assertTrue(VtopSession.isValid, "Session must be valid before the gate runs")

        // Simulate a foreground sweep (an Activity is present) so the gate takes the strict
        // forced-login path rather than the background cached-verification degradation.
        SyncEngine.interactiveVtopOverride = { true }

        // Replace the real (platform-specific) login so the test can observe the login path.
        var loginCalls = 0
        var forcedFlag: Boolean? = null
        AmazeClient.sessionLoginOverride = { forceFresh ->
            loginCalls++
            forcedFlag = forceFresh
            true
        }

        val ok = SyncEngine.ensureAuthenticatedSession(
            modules = setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE)
        )
        assertTrue(ok, "A fresh login must satisfy the auth gate")
        assertEquals(1, loginCalls, "Login path must run once even with a cached-fresh session")
        assertEquals(true, forcedFlag, "Sync gate must force a fresh login, not reuse cached tokens")
    }

    @Test
    fun testNoArgEnsureAuthenticatedSessionAlwaysInvokesLoginPath() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        VtopSession.save(csrf = "fresh_csrf", authorizedID = "21BCE7777", authorizedIDX = "idx_fresh")
        VtopSession.markActive()
        assertTrue(VtopSession.isValid, "A cached-fresh session must be present")

        // Foreground: the default gate call must still force a real login.
        SyncEngine.interactiveVtopOverride = { true }

        var loginCalls = 0
        var forcedFlag: Boolean? = null
        AmazeClient.sessionLoginOverride = { forceFresh ->
            loginCalls++
            forcedFlag = forceFresh
            true
        }

        // No explicit forceFresh: the default must still perform a real login, which is the form
        // used by the onboarding session step and every sync sweep.
        assertTrue(SyncEngine.ensureAuthenticatedSession())
        assertEquals(1, loginCalls, "Default gate call must invoke the login path")
        assertEquals(true, forcedFlag, "Default gate call must force a fresh VTOP login")
    }

    @Test
    fun testSweepDetectsExpiredSessionAndRequiresReauth() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        // Clear saved credentials to verify rejection on expired session
        SettingsManager.saveCredentials("", "")

        val staleTs = Clock.System.now().toEpochMilliseconds() - (15 * 60 * 1000L)
        VtopSession.save(csrf = "stale_csrf", authorizedID = "21BCE9999", authorizedIDX = "idx_stale")
        VtopSession.markActive(staleTs)

        assertTrue(VtopSession.isExpired, "15-minute-old session must be expired")
        assertFalse(VtopSession.isValid, "Expired session must not be valid")

        // Because credentials are empty and WebView restoration fails in test environment,
        // pre-flight MUST return false and gate the sweep from scraping with dead tokens
        val ok = SyncEngine.ensureAuthenticatedSession(
            modules = setOf(SyncModule.ATTENDANCE)
        )
        assertFalse(ok, "Expired session without credentials must fail auth gate to protect scraper integrity")
    }

    @Test
    fun testMoodleAndLmsBypassVtopGate() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        VtopSession.clear()
        assertFalse(VtopSession.isValid, "Session is cleared")

        // Moodle and LMS do not require VTOP session tokens
        val moodleOnly = setOf(SyncModule.MOODLE)
        val lmsOnly = setOf(SyncModule.LMS)
        val combined = setOf(SyncModule.MOODLE, SyncModule.LMS)

        assertTrue(SyncEngine.ensureAuthenticatedSession(moodleOnly), "Moodle must bypass VTOP session gate")
        assertTrue(SyncEngine.ensureAuthenticatedSession(lmsOnly), "LMS must bypass VTOP session gate")
        assertTrue(SyncEngine.ensureAuthenticatedSession(combined), "Combined Moodle+LMS must bypass VTOP session gate")
    }

    @Test
    fun testOfflineCacheSourceBypassesVtopAuth() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)
        VtopSession.clear()
        assertFalse(VtopSession.isValid, "Session is cleared")

        val allModules = setOf(SyncModule.ATTENDANCE, SyncModule.MARKS, SyncModule.TIMETABLE, SyncModule.HOSTEL_DETAILS)
        val ok = SyncEngine.ensureAuthenticatedSession(allModules)
        assertTrue(ok, "OFFLINE_CACHE mode must always bypass authentication pre-flight")
    }

    @Test
    fun testOfflineFallbackPreservesAppDataAndUserStore() {
        // Populate UserStore identity
        val student = StudentIdentity(
            regNo = "21BCE8888",
            name = "Test Student",
            program = "B.Tech Computer Science",
            email = "test@example.com"
        )
        UserStore.merge(student, IdentitySource.RECORDS)
        assertEquals("21BCE8888", UserStore.identity.value.regNo)
        assertEquals("Test Student", UserStore.identity.value.name)
        assertEquals("B.Tech Computer Science", UserStore.identity.value.program)
        assertEquals("test@example.com", UserStore.identity.value.email)

        // Clear session to simulate auth failure
        VtopSession.clear()
        assertFalse(VtopSession.isValid)

        // UserStore identity remains completely intact
        assertEquals("21BCE8888", UserStore.identity.value.regNo)
        assertEquals("Test Student", UserStore.identity.value.name)
        assertEquals("B.Tech Computer Science", UserStore.identity.value.program)
        assertEquals("test@example.com", UserStore.identity.value.email)
    }
}
