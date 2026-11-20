package com.amazecc.app.shared.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-seam tests for the Koha dual-host routing. No HTTP client is constructed: the host order,
 * status gate and error propagation are driven through [kohaWithFailover] with injected probes.
 */
class KohaFailoverTest {

    @Test
    fun `non 2xx or success false forces a failover`() {
        assertFalse(kohaShouldFailOver(200, true))
        assertTrue(kohaShouldFailOver(404, true))
        assertTrue(kohaShouldFailOver(200, false))
        assertTrue(kohaShouldFailOver(500, false))
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
                    // Hono has no Koha route: 404 bodies must never decode into a success.
                    KohaProbe(404, "Not found", "HTTP 404")
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
    fun `when every host fails the fallback receives the last real error`() = runBlocking {
        var receivedError: String? = null
        val result = kohaWithFailover(
            hosts = listOf("primary", "legacy"),
            fetch = { host ->
                KohaProbe(200, "bad", if (host == "primary") "first error" else "Not found")
            },
            isUsable = { it == "usable" },
            fallback = { err -> receivedError = err; "fallback" }
        )

        assertEquals("fallback", result)
        assertEquals("Not found", receivedError)
    }

    @Test
    fun `a status 200 with success false is retried on the next host`() = runBlocking {
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

        assertEquals(2, calls)
        assertEquals("fallback:Not found", result)
    }
}
