package com.amazecc.app.shared.api

import com.amazecc.app.shared.domain.ApiAuth
import com.amazecc.app.shared.domain.ApiMounts
import com.amazecc.app.shared.domain.ApiTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-015: the declarative mount list resolves per operation and per auth domain. */
class ApiMountsTest {

    @Test
    fun `default order is cache hono legacy unicc`() {
        assertEquals(listOf("cache", "hono", "legacy", "unicc"), ApiMounts.ORDER)
    }

    @Test
    fun `a vtop scraper resolves to hono legacy then unicc`() {
        val ids = ApiMounts.resolve("attendance", ApiAuth.VTOP).map { it.mountId }
        assertEquals(listOf("hono", "legacy", "unicc"), ids)
    }

    @Test
    fun `curves is hono only because legacy has no route and unicc does not serve it`() {
        val candidates = ApiMounts.resolve("curves", ApiAuth.VTOP)
        assertEquals(listOf("hono"), candidates.map { it.mountId })
        assertEquals("https://hono.amazecc.com/api/services/curves", candidates.single().url)
    }

    @Test
    fun `amaze id ops never fall back to legacy or unicc`() {
        val candidates = ApiMounts.resolve("cabshare/hubs", ApiAuth.AMAZE_ID)
        assertEquals(listOf("hono"), candidates.map { it.mountId })
        assertTrue(candidates.single().path.startsWith("/api/cabshare/"))
    }

    @Test
    fun `a public op prefers the cache GET mount`() {
        val candidates = ApiMounts.resolve("buses", ApiAuth.NONE)
        assertEquals("cache", candidates.first().mountId)
        assertEquals("GET", candidates.first().method)
        assertEquals("https://cache.amazecc.com/transport/buses", candidates.first().url)
    }

    @Test
    fun `preferred target floats to the front without dropping other mounts`() {
        val ids = ApiMounts.resolve(
            "attendance", ApiAuth.VTOP, preferredId = ApiTarget.BACKUP.mountId
        ).map { it.mountId }
        assertEquals(listOf("legacy", "hono", "unicc"), ids)
    }

    @Test
    fun `a custom base url is injected as the first candidate`() {
        val candidates = ApiMounts.resolve(
            "curves", ApiAuth.VTOP, preferredId = ApiTarget.CUSTOM.mountId,
            customBaseUrl = "https://my-worker.example/",
        )
        assertEquals("custom", candidates.first().mountId)
        assertEquals("https://my-worker.example/api/services/curves", candidates.first().url)
        // Built-in mounts still follow, so a broken custom URL can fail over.
        assertEquals("hono", candidates[1].mountId)
    }

    @Test
    fun `custom target selection persists so mountedPost can inject the custom url`() {
        AmazeClient.setApiTarget(ApiTarget.CUSTOM)
        val persisted = com.amazecc.app.shared.repository.SettingsManager.getApiTargetName()
        assertEquals(ApiTarget.CUSTOM.name, persisted)
        assertEquals(ApiTarget.CUSTOM, ApiTarget.fromName(persisted))
    }
}
