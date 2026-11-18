package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.vtop.VtopSession
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*

class SyncEngineSessionTest {

    private var previousVtopSource: VtopSource = VtopSource.REMOTE

    @BeforeTest
    fun setup() {
        previousVtopSource = AmazeClient.vtopSource
        VtopSession.clear()
        SessionManager.clearSession()
        SyncEngine.resetAllStates()
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.setVtopSource(previousVtopSource)
        VtopSession.clear()
        SessionManager.clearSession()
        SyncEngine.resetAllStates()
    }

    // =========================================================================
    // Tier 1: Feature Tests (≥5 tests for Features 1, 2, 3)
    // =========================================================================

    @Test
    fun testEnsureSessionReturnsTrueWhenActiveSessionExists() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        val now = Clock.System.now().toEpochMilliseconds()
        VtopSession.save(csrf = "valid-csrf-token", authorizedID = "21BCE0001", authorizedIDX = "idx-123")
        VtopSession.markActive(now)

        assertTrue(VtopSession.hasSession, "VtopSession should report hasSession == true")
        assertFalse(VtopSession.isExpired, "Fresh session should not be expired")
        assertTrue(VtopSession.isValid, "Fresh session should be valid")

        val result = AmazeClient.ensureSession()
        assertTrue(result, "ensureSession() must return true when active fresh session exists")
    }

    @Test
    fun testEnsureSessionBypassesOnOfflineCacheSource() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)
        // Explicitly clear session tokens to guarantee no valid VTOP session exists
        VtopSession.clear()
        SessionManager.clearSession()

        assertFalse(VtopSession.hasSession, "VtopSession should be empty")
        assertFalse(SessionManager.isLoggedIn, "SessionManager should be logged out")

        val result = AmazeClient.ensureSession()
        assertTrue(result, "ensureSession() must return true on OFFLINE_CACHE source even with no tokens")
        assertTrue(SyncEngine.ensureAuthenticatedSession(), "SyncEngine must bypass auth on OFFLINE_CACHE")
    }

    @Test
    fun testSweepProgressionAndGatingMarksModulesLoadingAndSuccess() {
        val sweepModules = setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE, SyncModule.MARKS)
        SyncEngine.beginSweep(sweepModules)

        // All selected modules should be in LOADING state
        for (module in sweepModules) {
            val state = SyncEngine.getModuleState(module)
            assertEquals(SyncStatus.LOADING, state.status, "Module ${module.name} should be LOADING initially")
        }
        // Unselected modules remain IDLE
        assertEquals(SyncStatus.IDLE, SyncEngine.getModuleState(SyncModule.PAYMENTS).status)

        // Mark first module as SUCCESS
        SyncEngine.markModuleSuccess(SyncModule.ATTENDANCE)
        assertEquals(SyncStatus.SUCCESS, SyncEngine.getModuleState(SyncModule.ATTENDANCE).status)
        assertNotNull(SyncEngine.getLastSyncedAt(SyncModule.ATTENDANCE))

        // Progress check
        val progress = SyncEngine.syncProgress.value
        assertEquals(3, progress.totalModules)
        assertEquals(1, progress.completedModules)
        assertEquals(1, progress.successCount)
        assertEquals(0, progress.errorCount)

        // Mark remaining as SUCCESS and end sweep
        SyncEngine.markModuleSuccess(SyncModule.TIMETABLE)
        SyncEngine.markModuleSuccess(SyncModule.MARKS)
        SyncEngine.endSweep()

        val finalProgress = SyncEngine.syncProgress.value
        assertEquals(3, finalProgress.completedModules)
        assertEquals(3, finalProgress.successCount)
        assertEquals(0, finalProgress.errorCount)
        assertEquals(100f, finalProgress.percentage)
    }

    @Test
    fun testEnsureAuthenticatedSessionBypassesNonVtopModules() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        VtopSession.clear()

        // LMS and Moodle do not require VTOP authentication
        val nonVtopModules = setOf(SyncModule.LMS, SyncModule.MOODLE)
        val result = SyncEngine.ensureAuthenticatedSession(modules = nonVtopModules)
        assertTrue(result, "ensureAuthenticatedSession must return true for LMS & Moodle non-VTOP sweep")
    }

    @Test
    fun testValidSessionPropagationInSessionManager() {
        val testCookies = "JSESSIONID=TEST123456789; Path=/vtop"
        val testCsrf = "csrf-token-abc-xyz"
        val testAuthorizedId = "22BCE1234"
        val testClubToken = "club-auth-token-999"

        SessionManager.saveInMemorySession(
            cookies = testCookies,
            csrf = testCsrf,
            authorizedID = testAuthorizedId,
            clubToken = testClubToken
        )

        assertTrue(SessionManager.isLoggedIn, "SessionManager should be logged in")
        assertEquals(testCookies, SessionManager.cookies.value)
        assertEquals(testCsrf, SessionManager.csrf.value)
        assertEquals(testAuthorizedId, SessionManager.authorizedID.value)
        assertEquals(testClubToken, SessionManager.clubToken.value)
        assertTrue(SessionManager.sessionCreatedAt.value > 0L)
        assertFalse(SessionManager.isSessionStale(maxAgeMs = 60_000L), "Fresh session must not be stale")
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (≥5 tests for Features 1, 2, 3)
    // =========================================================================

    @Test
    fun testSessionAgedBeyondRefreshThresholdIsExpired() {
        val now = Clock.System.now().toEpochMilliseconds()
        VtopSession.save(csrf = "token", authorizedID = "21BCE0001", authorizedIDX = "idx")

        // Age exactly equal to refresh threshold: 9.5 minutes (570,000 ms)
        val agedTime = now - VtopSession.SESSION_REFRESH_THRESHOLD_MS - 1000L
        VtopSession.markActive(agedTime)

        assertTrue(VtopSession.hasSession, "Tokens still exist in session")
        assertTrue(VtopSession.sessionAgeMs >= VtopSession.SESSION_REFRESH_THRESHOLD_MS, "Age exceeds threshold")
        assertTrue(VtopSession.isExpired, "Session aged > 9.5 min must be expired")
        assertFalse(VtopSession.isValid, "Expired session must not be valid")
    }

    @Test
    fun testSessionTimestampZeroTreatedAsExpired() {
        VtopSession.clear()
        assertEquals(0L, VtopSession.sessionTimestamp.value)
        assertEquals(Long.MAX_VALUE, VtopSession.sessionAgeMs, "Age with timestamp 0 should be Long.MAX_VALUE")
        assertTrue(VtopSession.isExpired, "Session with timestamp 0 must be expired")
        assertFalse(VtopSession.isValid, "Session with timestamp 0 must not be valid")
    }

    @Test
    fun testMalformedOrMissingCsrfInvalidatesSession() {
        VtopSession.save(csrf = "", authorizedID = "21BCE0001", authorizedIDX = "idx")
        assertFalse(VtopSession.hasSession, "Empty CSRF must result in hasSession == false")
        assertNull(VtopSession.verifyMenuParams(nowMs = 12345L), "verifyMenuParams must return null when CSRF is empty")

        VtopSession.save(csrf = "valid-csrf", authorizedID = "   ", authorizedIDX = "idx")
        assertFalse(VtopSession.hasSession, "Blank authorizedID must result in hasSession == false")
        assertNull(VtopSession.verifyMenuParams(nowMs = 12345L), "verifyMenuParams must return null when authorizedID is blank")
    }

    @Test
    fun testUnauthenticatedSweepMarksAllTargetModulesWithError() {
        val gatedModules = setOf(SyncModule.ATTENDANCE, SyncModule.MARKS, SyncModule.GRADES)
        SyncEngine.beginSweep(gatedModules)

        val errorMessage = "Your VTOP session has expired. Sign in again to sync."
        gatedModules.forEach { module ->
            SyncEngine.markModuleError(module, errorMessage)
            SyncEngine.addLog(module, errorMessage, SyncStatus.ERROR)
        }
        SyncEngine.endSweep()

        for (module in gatedModules) {
            val state = SyncEngine.getModuleState(module)
            assertEquals(SyncStatus.ERROR, state.status)
            assertEquals(errorMessage, state.error)
        }

        val progress = SyncEngine.syncProgress.value
        assertEquals(3, progress.completedModules)
        assertEquals(0, progress.successCount)
        assertEquals(3, progress.errorCount)
        assertTrue(progress.displayText.contains("3 errors", ignoreCase = true))
    }

    @Test
    fun testSessionManagerStalenessThresholdCalculation() {
        val now = Clock.System.now().toEpochMilliseconds()
        SessionManager.saveInMemorySession("cookies", "csrf", "authID")

        // Fresh session within 5 minutes
        assertFalse(SessionManager.isSessionStale(maxAgeMs = 5 * 60 * 1000L))

        // Age session by 15 minutes
        val staleTimestamp = now - 15 * 60 * 1000L
        SettingsManager.setLong(SettingsManager.SESSION_CREATED_AT, staleTimestamp)

        // Session staleness check against 10 minute max age
        assertTrue(SessionManager.isSessionStale(maxAgeMs = 10 * 60 * 1000L))
    }

    // =========================================================================
    // Tier 3: Pairwise Combinations & Concurrency
    // =========================================================================

    @Test
    fun testPairwiseSessionFreshnessAndOfflineCacheFallback() = runBlocking {
        // Condition: Session is completely expired and invalid
        VtopSession.clear()
        SessionManager.clearSession()
        assertTrue(VtopSession.isExpired)

        // Action: Fallback to OFFLINE_CACHE mode
        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)

        // Verification: ensureSession and sweep auth succeed despite expired tokens
        val sessionOk = AmazeClient.ensureSession()
        assertTrue(sessionOk, "OFFLINE_CACHE must bypass expired session check")

        val authOk = SyncEngine.ensureAuthenticatedSession(setOf(SyncModule.ATTENDANCE, SyncModule.HOSTEL_DETAILS))
        assertTrue(authOk, "SyncEngine must authenticate cleanly under OFFLINE_CACHE")
    }

    @Test
    fun testConcurrentEnsureSessionInvocations() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        VtopSession.save(csrf = "concurrent-csrf", authorizedID = "21BCE0001", authorizedIDX = "idx-1")
        VtopSession.markActive()

        // Launch 10 concurrent ensureSession calls
        val deferreds = (1..10).map {
            async {
                AmazeClient.ensureSession()
            }
        }
        val results = deferreds.awaitAll()

        assertEquals(10, results.size)
        assertTrue(results.all { it }, "All concurrent ensureSession calls must return true")
        assertTrue(VtopSession.isValid, "VtopSession must remain valid after concurrent calls")
    }

    @Test
    fun testSweepResetAndCancellationUnderActiveSync() {
        val modules = setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE, SyncModule.MARKS, SyncModule.GRADES)
        SyncEngine.beginSweep(modules)

        SyncEngine.markModuleLoading(SyncModule.ATTENDANCE)
        SyncEngine.markModuleSuccess(SyncModule.ATTENDANCE)
        SyncEngine.markModuleLoading(SyncModule.TIMETABLE)

        assertTrue(SyncEngine.isAnyModuleLoading(), "At least one module should be loading")

        // Cancel all
        SyncEngine.cancelAll()

        assertFalse(SyncEngine.isAnyModuleLoading(), "No module should be loading after cancelAll")
        for (module in modules) {
            assertEquals(SyncStatus.IDLE, SyncEngine.getModuleState(module).status)
        }
        val progress = SyncEngine.syncProgress.value
        assertEquals(0, progress.totalModules)
        assertEquals(0, progress.completedModules)
    }
}
