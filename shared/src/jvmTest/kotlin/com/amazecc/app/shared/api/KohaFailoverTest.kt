package com.amazecc.app.shared.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-seam tests for the dual-host routing. No HTTP client is constructed: host order, the
 * status gate and error propagation are driven through [kohaWithFailover] with injected probes.
 */
class KohaFailoverTest {

    @Test
    fun `only transport failure and 5xx force a failover`() {
        // 0 = network/timeout exception in KohaProbe.
        assertTrue(kohaShouldFailOver(0, false))
        KOHA_FAILOVER_STATUSES.forEach { assertTrue(kohaShouldFailOver(it, true)) }

        // 4xx (including 401) is an authoritative answer — never re-ask elsewhere.
        assertFalse(kohaShouldFailOver(401, true))
        assertFalse(kohaShouldFailOver(403, true))
        assertFalse(kohaShouldFailOver(404, true))

        // A success-looking 2xx is never a failover; a 2xx with success:false is still an answer.
        assertFalse(kohaShouldFailOver(200, true))
        assertFalse(kohaShouldFailOver(200, false))
        assertFalse(kohaShouldFailOver(299, true))
    }

    @Test
    fun `error wins over message and a stable fallback when both are blank`() {
        assertEquals("Not found", kohaErrorMessage("Not found", "Ignored"))
        assertEquals("Server busy", kohaErrorMessage("   ", "Server busy"))
        assertEquals("Koha request failed", kohaErrorMessage(null, null))
    }

    @Test
    fun `first usable host wins and hosts are tried in order`() = runBlocking {
        val visited = mutableListOf<String>()
        val result = kohaWithFailover(
            hosts = listOf("https://hono.amazecc.com", "https://api.amazecc.com"),
            fetch = { host ->
                visited += host
                if (host.startsWith("https://hono")) {
                    // A 5xx is a genuine server failure: move to the legacy host.
                    KohaProbe(503, null, "HTTP 503")
                } else {
                    KohaProbe(200, "books", null)
                }
            },
            isUsable = { it == "books" },
            fallback = { "fallback" }
        )

        assertEquals("books", result)
        assertEquals(
            listOf("https://hono.amazecc.com", "https://api.amazecc.com"),
            visited
        )
    }

    @Test
    fun `transport exception on the primary skips to the legacy host`() = runBlocking {
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = { host ->
                if (host == "primary") throw IllegalStateException("DNS failure")
                KohaProbe(200, "detail", null)
            },
            isUsable = { it == "detail" },
            fallback = { "fallback" }
        )

        assertEquals("detail", result)
    }

    @Test
    fun `a 401 is an answer and does not fall over to the next host`() = runBlocking {
        var calls = 0
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = {
                calls++
                KohaProbe(401, null, "Unauthorized")
            },
            isUsable = { true },
            fallback = { err -> "fallback:$err" }
        )

        assertEquals(1, calls)
        assertEquals("fallback:Unauthorized", result)
    }

    @Test
    fun `a 404 is an answer and does not fall over to the next host`() = runBlocking {
        var calls = 0
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = {
                calls++
                KohaProbe(404, "Not found", "HTTP 404")
            },
            isUsable = { true },
            fallback = { err -> "fallback:$err" }
        )

        assertEquals(1, calls)
        assertEquals("fallback:HTTP 404", result)
    }

    @Test
    fun `a status 200 with success false is an answer and is not retried`() = runBlocking {
        var calls = 0
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = {
                calls++
                KohaProbe(200, "failure body", "Not found")
            },
            isUsable = { false },
            fallback = { err -> "fallback:$err" }
        )

        assertEquals(1, calls)
        assertEquals("fallback:Not found", result)
    }

    @Test
    fun `every host 5xx falls through and the last real error reaches the fallback`() = runBlocking {
        var receivedError: String? = null
        val visited = mutableListOf<String>()
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = { host ->
                visited += host
                KohaProbe(500, null, if (host == "primary") "first error" else "last error")
            },
            isUsable = { true },
            fallback = { err -> receivedError = err; "fallback" }
        )

        assertEquals("fallback", result)
        assertEquals("last error", receivedError)
        assertEquals(listOf("primary", "legacy"), visited)
    }
}
