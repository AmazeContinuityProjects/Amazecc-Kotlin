package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.api.effectiveSemester
import com.amazecc.app.shared.model.Official
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless checks for the wiring that makes the Profile surface populate more than regNo/attendance.
 * Every seam is pure or injected — no network, WebView or encrypted cache is involved.
 */
class ProfileWiringTest {

    private val originalSource = AmazeClient.vtopSource

    @BeforeTest
    fun setUp() {
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.REMOTE)
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(originalSource)
    }

    // ── isThinIdentity decides when the host warms the consolidated snapshot ──

    @Test
    fun `regNo-only identity is thin and needs a warm-up`() {
        assertTrue(isThinIdentity(StudentIdentity(regNo = "21BCE1001")))
        // A cached photo without any detail is still thin.
        assertTrue(isThinIdentity(StudentIdentity(regNo = "21BCE1001", photoBase64 = "abc")))
    }

    @Test
    fun `an identity carrying a name or any profile detail is not thin`() {
        assertFalse(isThinIdentity(StudentIdentity(regNo = "21BCE1001", name = "Aarav")))
        assertFalse(isThinIdentity(StudentIdentity(regNo = "21BCE1001", friendlyName = "Aarav")))
        assertFalse(isThinIdentity(StudentIdentity(regNo = "21BCE1001", program = "B.Tech")))
        assertFalse(isThinIdentity(StudentIdentity(regNo = "21BCE1001", proctor = Official(name = "Dr Rao"))))
        assertFalse(isThinIdentity(StudentIdentity(regNo = "21BCE1001", email = "aarav@example.com")))
    }

    @Test
    fun `an empty identity is not thin because there is no regNo to warm from`() {
        assertFalse(isThinIdentity(StudentIdentity()))
        assertFalse(isThinIdentity(StudentIdentity(name = "Aarav")))
    }

    // ── getMeWith honours the LOCAL / REMOTE source preference ──

    @Test
    fun `LOCAL branch uses only the headless scraper and never the remote path`() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        var remoteCalls = 0

        val result = AmazeClient.getMeWith(
            remoteFetch = { remoteCalls++; error("remote must not be called in LOCAL") },
            restoreCache = { error("restore must not be called in LOCAL") },
            cachedIdentity = { StudentIdentity(regNo = "STALE", name = "Stale Name") },
            localFetch = { StudentIdentity(regNo = "21BCE1001", name = "Fresh Name") }
        )

        assertEquals(0, remoteCalls)
        assertTrue(result.success)
        assertEquals("Fresh Name", result.identity?.name)
        assertNull(result.error)
    }

    @Test
    fun `LOCAL branch still serves a cached thin identity when the scraper is unavailable`() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)

        val result = AmazeClient.getMeWith(
            remoteFetch = { error("remote must not be called in LOCAL") },
            restoreCache = { error("restore must not be called in LOCAL") },
            cachedIdentity = { StudentIdentity(regNo = "21BCE1001") },
            localFetch = { null }
        )

        assertTrue(result.success)
        assertEquals("21BCE1001", result.identity?.regNo)
        assertTrue(result.error?.contains("cached") == true)
        // The cached result is thin; the host's LaunchedEffect is what triggers the full sync.
        assertTrue(isThinIdentity(result.identity ?: error("identity expected")))
    }

    // ── Supporting wiring: full sweep + calendar semester resolution ──

    @Test
    fun `full sweep names resolve to the modules that page-local effects no longer fetch`() {
        assertEquals(SyncModule.OD_DETAILS, SyncEngine.moduleOf("OD Details"))
        assertEquals(SyncModule.FREE_CLASSROOMS, SyncEngine.moduleOf("Free Classrooms"))
        assertEquals(SyncModule.DOCUMENTS, SyncEngine.moduleOf("Documents"))
        assertEquals(SyncModule.FEEDBACK_STATUS, SyncEngine.moduleOf("Feedback Status"))
    }

    @Test
    fun `blank requested semester falls through to the session and selected fallbacks`() {
        assertEquals(
            "CH20262701",
            effectiveSemester(
                requested = "   ",
                session = "CH20262701",
                selected = "CH20252601",
                firstAcademic = "CH20242501"
            )
        )
        assertEquals(
            "CH20252601",
            effectiveSemester(requested = null, session = null, selected = "CH20252601", firstAcademic = "CH20242501")
        )
        assertEquals(
            "CH20242501",
            effectiveSemester(requested = "", session = "", selected = "", firstAcademic = "CH20242501")
        )
        assertNull(effectiveSemester(null, null, null, null))
    }

    @Test
    fun `REMOTE branch maps a partial whoami payload instead of dropping it`() = runBlocking {
        val result = AmazeClient.getMeWith(
            remoteFetch = {
                Json.parseToJsonElement(
                    """{"success":false,"regNo":"21BCE1001","fetchErrors":{"bank":"HTTP 500"}}"""
                ).jsonObject
            },
            restoreCache = { StudentIdentity() },
            cachedIdentity = { StudentIdentity() },
            localFetch = { error("local must not be called in REMOTE") }
        )

        assertTrue(result.success)
        assertEquals("21BCE1001", result.identity?.regNo)
        assertTrue(result.error?.contains("bank") == true)
    }
}
