package com.amazecc.app.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The Event Hub wire models are the contract between the injected DOM scripts and the caches.
 * These round-trip tests guard the additive fields so an OFFLINE_CACHE restore cannot silently
 * drop receipt/certificate/payment information.
 */
class EventHubWiringModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun registeredEventPreservesPaymentAndLinkFields() {
        val event = EventHubRegisteredEvent(
            eid = "EV-1",
            title = "Hackathon",
            location = "VIT Hall",
            date = "2026-11-22",
            time = "10:00 AM",
            orderId = "ORD-9",
            paymentStatus = "Pay Now",
            receiptId = "RCP-7",
            certificateId = "CERT-3",
            receiptUrl = "https://eventhubcc.vit.ac.in/EventHub/receipt/RCP-7",
            certificateUrl = "https://eventhubcc.vit.ac.in/EventHub/conf/getCertificate/CERT-3",
            payNowUrl = "https://eventhubcc.vit.ac.in/EventHub/showPaymentTC/ORD-9",
            payLaterLink = null,
            receiptLink = "https://eventhubcc.vit.ac.in/EventHub/receipt/RCP-7",
            certificateLink = "https://eventhubcc.vit.ac.in/EventHub/conf/getCertificate/CERT-3",
            payNowLink = "https://eventhubcc.vit.ac.in/EventHub/showPaymentTC/ORD-9"
        )
        val restored = json.decodeFromString<EventHubRegisteredEvent>(json.encodeToString(event))
        assertEquals(event, restored)
        assertEquals("Pay Now", restored.paymentStatus)
        assertEquals("RCP-7", restored.receiptId)
        assertEquals("CERT-3", restored.certificateId)
        assertEquals(event.payNowUrl, restored.payNowLink)
    }

    @Test
    fun legacyRegisteredEventJsonStillDecodes() {
        // Old cache entries only carried the five original fields; new ones must default to null.
        val legacy = """{"eid":"EV-2","name":"Quiz","venue":"Room 1","date":"2026-01-01","time":"11:00"}"""
        val event = json.decodeFromString<EventHubRegisteredEvent>(legacy)
        assertEquals("Quiz", event.title)
        assertEquals("Room 1", event.location)
        assertEquals(null, event.paymentStatus)
        assertEquals(null, event.receiptUrl)
    }

    @Test
    fun previewCarriesImageDescriptionAndMeta() {
        val preview = EventHubPreview(
            eid = "EV-3",
            imageSrc = "https://eventhubcc.vit.ac.in/EventHub/image/?id=EV-3",
            description = "A coding contest",
            metaDetails = mapOf("Total Fees" to "100", "Event Venue" to "Room 2")
        )
        val restored = json.decodeFromString<EventHubPreview>(json.encodeToString(preview))
        assertEquals(preview, restored)
        assertNotNull(restored.metaDetails)
        assertEquals("100", restored.metaDetails!!["Total Fees"])
    }

    @Test
    fun registrationOutcomeCarriesUrlAndHtml() {
        val res = EventHubRegisterRes(
            status = "payment_form",
            url = "https://eventhubcc.vit.ac.in/EventHub/doPtmPayment",
            html = "<base href=\"https://eventhubcc.vit.ac.in/\"><form></form>"
        )
        assertEquals(res, json.decodeFromString<EventHubRegisterRes>(json.encodeToString(res)))
    }
}
