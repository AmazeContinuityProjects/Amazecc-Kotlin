package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wiring checks for the LOCAL Event Hub path. The network/DOM parts need a real WebView and are
 * covered by the platform integration tests; these are the pure, host-independent guards:
 * the SSRF allow-list, expired-session detection and that the injected DOM scripts actually ask
 * the WebView for the fields the Kotlin decoders read.
 */
class EventHubWiringTest {

    // ── URL allow-list (CWE-918) ────────────────────────────────────────────

    @Test
    fun absoluteEventHubHttpsUrlIsAccepted() {
        val url = "https://eventhubcc.vit.ac.in/EventHub/showPaymentTC/12345"
        assertEquals(url, VtopEventHub.resolveEventHubUrl(url))
    }

    @Test
    fun relativeUrlsAreResolvedAgainstTheEventHubOrigin() {
        assertEquals(
            "https://eventhubcc.vit.ac.in/EventHub/conf/getCertificate/abc",
            VtopEventHub.resolveEventHubUrl("/EventHub/conf/getCertificate/abc")
        )
        assertEquals(
            "https://eventhubcc.vit.ac.in/EventHub/EventHub/image/?id=42",
            VtopEventHub.resolveEventHubUrl("EventHub/image/?id=42")
        )
    }

    @Test
    fun foreignHostsSchemesAndSuffixTricksAreRejected() {
        assertNull(VtopEventHub.resolveEventHubUrl("http://eventhubcc.vit.ac.in/EventHub/profile"))
        assertNull(VtopEventHub.resolveEventHubUrl("https://evil.example/EventHub/profile"))
        assertNull(VtopEventHub.resolveEventHubUrl("https://eventhubcc.vit.ac.in.evil.example/x"))
        assertNull(VtopEventHub.resolveEventHubUrl("https://sub.eventhubcc.vit.ac.in/x"))
        assertNull(VtopEventHub.resolveEventHubUrl("https://user@eventhubcc.vit.ac.in/x"))
        assertNull(VtopEventHub.resolveEventHubUrl("https://eventhubcc.vit.ac.in:8443/x"))
        assertNull(VtopEventHub.resolveEventHubUrl(""))
        assertNull(VtopEventHub.resolveEventHubUrl(null))
    }

    // ── session-expiry detection ────────────────────────────────────────────

    @Test
    fun loginPageIsDetectedRegardlessOfQuoteStyleOrCase() {
        assertTrue(VtopEventHub.looksLikeLoginPage("""<form action="/EventHub/mainDashboard" method="POST">"""))
        assertTrue(VtopEventHub.looksLikeLoginPage("""<form ACTION='/EventHub/mainDashboard'>"""))
        assertFalse(VtopEventHub.looksLikeLoginPage("""<table id="regTable"><tr><th>Event Name</th></tr></table>"""))
    }

    // ── DOM script field wiring ─────────────────────────────────────────────

    @Test
    fun previewScriptReadsLabelsDescriptionAndFees() {
        val script = VtopScripts.parseEventHubPreview("<html></html>")
        for (needle in listOf(".list-group-item", ".fw-bold", "Event Description", "#EventFees1", "metaDetails")) {
            assertTrue(script.contains(needle), "preview script is missing $needle")
        }
    }

    @Test
    fun registrationFormScriptCollectsHiddenInputsAndDetectsAlreadyRegistered() {
        val script = VtopScripts.parseEventHubRegistrationForm("<html></html>")
        for (needle in listOf(
            "alreadyRegistered", "sessionExpired", "hiddenFields",
            "/EventHub/registerEvent", "/EventHub/mainDashboard"
        )) {
            assertTrue(script.contains(needle), "registration script is missing $needle")
        }
    }

    @Test
    fun paymentFormScriptFindsTheDoPtmPaymentAction() {
        val script = VtopScripts.parseEventHubPaymentForm("<html></html>")
        assertTrue(script.contains("doptmpayment"))
        assertTrue(script.contains("hiddenFields"))
    }

    @Test
    fun profileScriptEmitsEveryPreservedField() {
        val script = VtopScripts.parseEventHubProfile("<html></html>")
        for (field in listOf(
            "orderId", "paymentStatus", "receiptId", "certificateId",
            "receiptUrl", "certificateUrl", "payNowUrl", "payLaterLink",
            "receiptLink", "certificateLink", "payNowLink", "showPaymentTC", "getCertificate"
        )) {
            assertTrue(script.contains(field), "profile script is missing $field")
        }
    }

    @Test
    fun scriptsAreWrappedInSelfInvokingFunctions() {
        for (script in listOf(
            VtopScripts.parseEventHubEvents("<html></html>"),
            VtopScripts.parseEventHubProfile("<html></html>"),
            VtopScripts.parseEventHubPreview("<html></html>"),
            VtopScripts.parseEventHubRegistrationForm("<html></html>"),
            VtopScripts.parseEventHubPaymentForm("<html></html>")
        )) {
            assertNotNull(script)
            assertTrue(script.contains("JSON.stringify"))
            assertTrue(script.trimStart().startsWith("(function()"))
        }
    }
}
