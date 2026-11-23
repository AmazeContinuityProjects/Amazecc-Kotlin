package com.amazecc.app.shared.api

import com.amazecc.app.shared.domain.ApiAuth
import com.amazecc.app.shared.domain.ApiMounts
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.state.SyncModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-192: the public bus-route path must behave like the web app. Pure contract tests only:
 * mount resolution, envelope decoding, failure semantics and gate attribution. No HTTP client is
 * constructed and no live network is used.
 */
class FfcsBusRouteFetchTest {

    @Test
    fun `buses resolve to the public cache GET first and never to unicc`() {
        // getBuses() calls mountedPost(preferCache = true), which resolves with no preferred mount,
        // i.e. exactly this list. The default API-target float must not jump ahead of the cache.
        val candidates = ApiMounts.resolve("buses", ApiAuth.NONE)
        assertEquals(listOf("cache", "hono", "legacy"), candidates.map { it.mountId })
        assertEquals("GET", candidates.first().method)
        assertEquals("https://cache.amazecc.com/transport/buses", candidates.first().url)
        assertFalse(candidates.any { "unicc" in it.url || it.mountId == "unicc" })
        // The two fallbacks are unauthenticated POSTs, so no credentials can be attached.
        assertEquals(listOf("POST", "POST"), candidates.drop(1).map { it.method })
        assertEquals(
            listOf("https://hono.amazecc.com/api/buses", "https://api.amazecc.com/api/buses"),
            candidates.drop(1).map { it.url }
        )
    }

    @Test
    fun `a 5xx or transport error advances but a 4xx is a final answer`() {
        assertTrue(kohaShouldFailOver(0, false))
        assertTrue(kohaShouldFailOver(503, false))
        assertFalse(kohaShouldFailOver(400, false))
        assertFalse(kohaShouldFailOver(404, false))
        assertFalse(kohaShouldFailOver(200, false)) // 2xx success:false is still an answer.
    }

    @Test
    fun `cache envelope maps field-exactly and tolerates unknown keys and nulls`() {
        val payload = """
            {
              "success": true,
              "unknownEnvelopeKey": "ignored",
              "buses": [
                {
                  "id": "bus-7",
                  "type": "AC",
                  "route": "Campus - City",
                  "boardingPoints": ["Gate 1", "Gate 2"],
                  "driverName": "Ravi",
                  "driverPhone": "9876543210",
                  "whatsappGroup": "https://chat.whatsapp.com/abc",
                  "busLocation": "Near library",
                  "supervisorName": "Ms Rao",
                  "supervisorPhone": "9000000001",
                  "driverInchargeName": "Mr Kumar",
                  "driverInchargePhone": "9000000002",
                  "stops": [
                    {"stopOrder": 1, "stopName": "Gate 1", "pickupTime": "08:00"},
                    {"stopOrder": 2, "stopName": "City Stand"}
                  ],
                  "placements": [
                    {"zone": "North", "dispersalTime": "5PM"}
                  ],
                  "futureKey": "ignored"
                },
                {
                  "id": "bus-8",
                  "type": null,
                  "route": "R2",
                  "boardingPoints": null,
                  "driverName": null,
                  "driverPhone": null,
                  "whatsappGroup": null,
                  "busLocation": null
                }
              ]
            }
        """.trimIndent()

        val res = AmazeClient.decodeBuses(payload)!!
        assertTrue(res.success)
        assertEquals(2, res.buses.size)

        val bus = res.buses[0]
        assertEquals("bus-7", bus.id)
        assertEquals("AC", bus.type)
        assertEquals("Campus - City", bus.route)
        assertEquals(listOf("Gate 1", "Gate 2"), bus.boardingPoints)
        assertEquals("Ravi", bus.driverName)
        assertEquals("9876543210", bus.driverPhone)
        assertEquals("https://chat.whatsapp.com/abc", bus.whatsappGroup)
        assertEquals("Near library", bus.busLocation)
        assertEquals("Ms Rao", bus.supervisorName)
        assertEquals("9000000001", bus.supervisorPhone)
        assertEquals("Mr Kumar", bus.driverInchargeName)
        assertEquals("9000000002", bus.driverInchargePhone)
        assertEquals(2, bus.stops.size)
        assertEquals(1, bus.stops[0].stopOrder)
        assertEquals("Gate 1", bus.stops[0].stopName)
        assertEquals("08:00", bus.stops[0].pickupTime)
        assertEquals(2, bus.stops[1].stopOrder)
        assertNull(bus.stops[1].pickupTime)
        assertEquals("North", bus.placements.single().zone)
        assertEquals("5PM", bus.placements.single().dispersalTime)

        // coerceInputValues: null for a non-null field with a default becomes that default.
        val nulled = res.buses[1]
        assertEquals("bus-8", nulled.id)
        assertEquals("", nulled.type)
        assertTrue(nulled.boardingPoints.isEmpty())
        assertEquals("", nulled.driverName)
        assertEquals("", nulled.driverPhone)
        assertEquals("", nulled.whatsappGroup)
        assertEquals("", nulled.busLocation)
    }

    @Test
    fun `a message-only failure is not success and does not clobber the cached list`() {
        val cached = BusesRes(
            success = true,
            buses = listOf(
                com.amazecc.app.shared.model.BusRoute(
                    id = "bus-7", type = "AC", route = "Campus - City",
                    driverPhone = "9876543210", driverName = "Ravi",
                    whatsappGroup = "link", busLocation = "Gate 1",
                )
            ),
        )
        val messageOnly = AmazeClient.decodeBuses("""{"success":false,"message":"Cache temporarily unavailable"}""")!!

        assertFalse(AmazeClient.busesSucceeded(messageOnly))
        // A payload with ONLY a message decodes with the default success=true; that must still
        // not be counted as a successful fetch (the exact D-191-era regression).
        val defaultSuccessMessageOnly = AmazeClient.decodeBuses("""{"message":"No buses published"}""")!!
        assertTrue(defaultSuccessMessageOnly.success)
        assertFalse(AmazeClient.busesSucceeded(defaultSuccessMessageOnly))
        assertEquals("Cache temporarily unavailable", AmazeClient.busesFailureMessage(messageOnly))
        // This is what AppState's BUSES isSuccess predicate now sees: no update -> cache survives.
        assertFalse(AmazeClient.busesSucceeded(AmazeClient.busesFailure(cached, messageOnly)))
        assertEquals(cached.buses, AmazeClient.busesFailure(cached, messageOnly).buses)
        assertTrue(AmazeClient.busesFailure(cached, messageOnly).message!!.contains("showing last cached routes"))

        // A transport failure (no decoded envelope) keeps the same list and reports honestly.
        val offline = AmazeClient.busesFailure(cached, null)
        assertFalse(AmazeClient.busesSucceeded(offline))
        assertEquals(cached.buses, offline.buses)
        assertTrue(offline.message!!.startsWith("Bus routes could not be reached"))

        // With no prior cache the result is empty, not a fabricated route.
        val noCache = AmazeClient.busesFailure(null, messageOnly)
        assertTrue(noCache.buses.isEmpty())
        assertEquals("Cache temporarily unavailable", noCache.message)
    }

    @Test
    fun `both bus URL shapes are attributed to the BUSES module`() {
        assertEquals(SyncModule.BUSES, com.amazecc.app.shared.state.inferModule("https://cache.amazecc.com/transport/buses"))
        assertEquals(SyncModule.BUSES, com.amazecc.app.shared.state.inferModule("https://hono.amazecc.com/api/buses"))
        assertEquals(SyncModule.BUSES, com.amazecc.app.shared.state.inferModule("https://api.amazecc.com/api/buses"))
        // The transport registration route must keep its own module bucket.
        assertEquals(SyncModule.TRANSPORT, com.amazecc.app.shared.state.inferModule("https://hono.amazecc.com/api/transport"))
    }
}
