package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EEventCategory
import com.amazecc.app.shared.domain.overhaul.M3EEventHubAction
import com.amazecc.app.shared.domain.overhaul.M3EEventHubState
import com.amazecc.app.shared.domain.overhaul.M3EEventItem
import com.amazecc.app.shared.domain.overhaul.M3EEventPreviewUi
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.domain.overhaul.M3ERegisteredEventItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wiring coverage for the real event-hub data surfaced by the stateless screen. */
@OptIn(ExperimentalTestApi::class)
class M3EEventHubWiringUiTest {

    private fun sampleEvent() = M3EEventItem(
        id = "evt_01",
        title = "Battle of the Bands",
        festName = "",
        category = M3EEventCategory.CULTURAL,
        date = "15 Feb 2027",
        time = "05:30 PM",
        venue = "Outdoor Stadium",
        price = "Free Entry",
        isFree = true
    )

    private fun baseState() = M3EEventHubState(
        events = listOf(sampleEvent()),
        filteredEvents = listOf(sampleEvent())
    )

    @Test
    fun loadingAndErrorAreVisible() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(state = baseState().copy(isLoading = true), onAction = {})
            }
        }
        onNodeWithText("Loading events…").assertExists()
        onNodeWithText("Couldn't refresh events").assertDoesNotExist()
    }

    @Test
    fun syncErrorIsVisible() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(errorMessage = "EventHub unavailable"),
                    onAction = {}
                )
            }
        }
        onNodeWithText("Couldn't refresh events").assertExists()
        onNodeWithText("EventHub unavailable").assertExists()
    }

    @Test
    fun blankFestNameDoesNotRenderFakeBadge() = runComposeUiTest {
        setContent {
            AmazeM3ETheme { M3EEventHubScreen(state = baseState(), onAction = {}) }
        }
        onNodeWithText("CAMPUS FEST").assertDoesNotExist()
        onNodeWithText("Battle of the Bands").assertExists()
    }

    @Test
    fun registeredEventsExposeRealReceiptCertificateAndPayNowLinks() = runComposeUiTest {
        val actions = mutableListOf<M3EEventHubAction>()
        val registration = M3ERegisteredEventItem(
            eid = "evt_02",
            title = "Robotics Workshop",
            venue = "SMV 112",
            date = "05 Dec 2026",
            time = "10:00 AM",
            paymentStatus = "Payment due",
            receiptUrl = "https://events.example/receipt/evt_02",
            certificateUrl = "https://events.example/certificate/evt_02",
            payNowUrl = "https://events.example/pay/evt_02"
        )
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(registeredEvents = listOf(registration)),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Registered Events").assertExists()
        onNodeWithText("Payment: Payment due").assertExists()
        onNodeWithText("Receipt").assertExists()
        onNodeWithText("Certificate").assertExists()
        onNodeWithText("Pay now").performClick()

        assertEquals(1, actions.size)
        val opened = actions.first() as M3EEventHubAction.OpenExternalLink
        assertEquals("https://events.example/pay/evt_02", opened.url)
    }

    @Test
    fun previewDialogShowsDescriptionMetaAndPosterLink() = runComposeUiTest {
        val actions = mutableListOf<M3EEventHubAction>()
        val preview = M3EEventPreviewUi(
            eid = "evt_01",
            title = "Battle of the Bands",
            imageUrl = "https://events.example/poster/evt_01.png",
            description = "Three bands, one night.",
            metaDetails = mapOf("Eligibility" to "All students", "Venue" to "Outdoor Stadium")
        )
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(preview = preview),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Three bands, one night.").assertExists()
        onNodeWithText("Eligibility").assertExists()
        onNodeWithText("All students").assertExists()
        onNodeWithText("Open poster full size").performClick()
        onAllNodesWithText("Close")[0].performClick()

        assertEquals(2, actions.size)
        assertTrue(actions[0] is M3EEventHubAction.OpenExternalLink)
        assertEquals("https://events.example/poster/evt_01.png", (actions[0] as M3EEventHubAction.OpenExternalLink).url)
        assertTrue(actions[1] is M3EEventHubAction.DismissPreview)
    }

    @Test
    fun successRegistrationDialogDismisses() = runComposeUiTest {
        val actions = mutableListOf<M3EEventHubAction>()
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(
                        registrationResult = M3EEventRegistrationUi(status = "success")
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Registration confirmed").assertExists()
        onAllNodesWithText("OK")[0].performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EEventHubAction.DismissRegistrationStatus)
    }

    @Test
    fun paymentRequiredDialogOpensPaymentUrlExternally() = runComposeUiTest {
        val actions = mutableListOf<M3EEventHubAction>()
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(
                        registrationResult = M3EEventRegistrationUi(
                            status = "payment_required",
                            message = "Complete payment to confirm your seat.",
                            paymentUrl = "https://events.example/pay/evt_01"
                        )
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Payment required").assertExists()
        onNodeWithText("Pay now").performClick()
        assertEquals(1, actions.size)
        val opened = actions.first() as M3EEventHubAction.OpenExternalLink
        assertEquals("https://events.example/pay/evt_01", opened.url)
    }

    @Test
    fun alreadyRegisteredAndPaymentFormStatusesAreRecognised() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = baseState().copy(
                        registrationResult = M3EEventRegistrationUi(status = "already_registered")
                    ),
                    onAction = {}
                )
            }
        }
        onNodeWithText("Already registered").assertExists()
    }
}
