package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WhoamiProfileGetMeTest {

    private val originalSource = AmazeClient.vtopSource
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.REMOTE)
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(originalSource)
    }

    @Test
    fun `remote branch maps the flat payload into the identity`() = runBlocking {
        var remoteCalls = 0
        val result = AmazeClient.getMeWith(
            remoteFetch = {
                remoteCalls++
                json.parseToJsonElement(FLAT_WHOAMI).jsonObject
            },
            restoreCache = { StudentIdentity() },
            cachedIdentity = { StudentIdentity() },
            localFetch = { null }
        )

        assertEquals(1, remoteCalls)
        assertTrue(result.success)
        assertNotNull(result.identity)
        assertEquals("21BCE1001", result.identity?.regNo)
        assertEquals("Aarav Test", result.identity?.name)
        assertEquals(RESIDENCE_DAYSCHOLAR, result.identity?.residenceStatus)
        assertEquals("secret", result.identity?.credentials?.first()?.password)
        assertEquals("Fee Receipt", result.identity?.acknowledgement?.documents?.first()?.documentName)
    }

    @Test
    fun `remote branch surfaces partial fetchErrors as a warning but keeps usable data`() = runBlocking {
        val result = AmazeClient.getMeWith(
            remoteFetch = {
                json.parseToJsonElement(
                    """{"success":false,"regNo":"21BCE1","fetchErrors":{"bank":"HTTP 500"}}"""
                ).jsonObject
            },
            restoreCache = { StudentIdentity() },
            cachedIdentity = { StudentIdentity() },
            localFetch = { null }
        )
        assertTrue(result.success)
        assertEquals("21BCE1", result.identity?.regNo)
        assertTrue(result.error?.contains("bank") == true)
    }

    @Test
    fun `offline cache branch restores the cached identity`() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)
        var restoreCalls = 0
        val result = AmazeClient.getMeWith(
            remoteFetch = { error("remote must not be called in OFFLINE_CACHE") },
            restoreCache = {
                restoreCalls++
                StudentIdentity(regNo = "CACHED1", residenceStatus = RESIDENCE_DAYSCHOLAR)
            },
            cachedIdentity = { StudentIdentity() },
            localFetch = { error("local must not be called in OFFLINE_CACHE") }
        )

        assertEquals(1, restoreCalls)
        assertTrue(result.success)
        assertEquals("CACHED1", result.identity?.regNo)
        assertEquals(RESIDENCE_DAYSCHOLAR, result.identity?.residenceStatus)
    }

    @Test
    fun `local branch prefers a fresh scrape then falls back to cache`() = runBlocking {
        AmazeClient.setVtopSource(VtopSource.LOCAL)

        val fresh = AmazeClient.getMeWith(
            remoteFetch = { error("remote must not be called in LOCAL") },
            restoreCache = { error("restore must not be called in LOCAL") },
            cachedIdentity = { StudentIdentity(regNo = "STALE") },
            localFetch = { StudentIdentity(regNo = "FRESH1") }
        )
        assertTrue(fresh.success)
        assertEquals("FRESH1", fresh.identity?.regNo)

        val cached = AmazeClient.getMeWith(
            remoteFetch = { error("remote must not be called in LOCAL") },
            restoreCache = { error("restore must not be called in LOCAL") },
            cachedIdentity = { StudentIdentity(regNo = "STALE") },
            localFetch = { null }
        )
        assertTrue(cached.success)
        assertEquals("STALE", cached.identity?.regNo)
        assertTrue(cached.error?.contains("cached") == true)
    }

    @Test
    fun `demo mode reports failure so the caller keeps the demo sweep`() = runBlocking {
        AmazeClient.setUseMockData(true)
        val result = AmazeClient.getMeWith(
            remoteFetch = { error("must not fetch in demo") },
            restoreCache = { StudentIdentity() },
            cachedIdentity = { StudentIdentity() },
            localFetch = { null }
        )
        assertFalse(result.success)
        assertEquals("demo mode", result.error)
    }
}
