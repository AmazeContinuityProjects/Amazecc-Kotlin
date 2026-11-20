package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ETransportAction
import com.amazecc.app.shared.domain.overhaul.M3ETransportRegistration
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.model.TransportDataRes
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Evidence-backed tests for the live GPS share link (`busUrl`) carried by `TransportDataRes`.
 * The link is surfaced in the registration card only when the API actually returned one, and
 * the emitted action carries the exact upstream URL — never a fabricated placeholder.
 */
@OptIn(ExperimentalTestApi::class)
class M3ETransportBusUrlTest {

    private val liveUrl = "https://example.test/vtop/transport/track?token=abc123"

    private fun stateWithBusUrl(busUrl: String?) = M3ETransportState(
        registration = M3ETransportRegistration(
            hasRegistration = true,
            registerNumber = "21BCE1001",
            routeSelected = "Tambaram - Campus",
            busUrl = busUrl
        )
    )

    @Test
    fun busTrackingLinkShownWhenUrlPresent() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateWithBusUrl(liveUrl),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Open live bus tracking").assertExists()
    }

    @Test
    fun busTrackingLinkHiddenWhenUrlNull() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateWithBusUrl(null),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Open live bus tracking").assertDoesNotExist()
    }

    @Test
    fun busTrackingLinkHiddenWhenUrlBlank() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateWithBusUrl("   "),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Open live bus tracking").assertDoesNotExist()
    }

    @Test
    fun busTrackingButtonEmitsExactUrl() = runComposeUiTest {
        val actions = mutableListOf<M3ETransportAction>()
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateWithBusUrl(liveUrl),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Open live bus tracking").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ETransportAction.OpenBusTracking)
        assertEquals(liveUrl, (actions.first() as M3ETransportAction.OpenBusTracking).url)
    }

    @Test
    fun transportRegistrationMappingPreservesBusUrl() {
        assertNull(transportRegistrationFrom(null))
        val registration = transportRegistrationFrom(
            TransportDataRes(
                hasRegistration = true,
                registerNumber = "21BCE1001",
                busUrl = liveUrl
            )
        )
        assertNotNull(registration)
        assertEquals(liveUrl, registration.busUrl)
    }
}
