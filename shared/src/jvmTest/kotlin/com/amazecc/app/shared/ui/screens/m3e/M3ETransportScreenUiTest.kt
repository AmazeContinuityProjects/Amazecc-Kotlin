package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EBusPlacement
import com.amazecc.app.shared.domain.overhaul.M3EBusRoute
import com.amazecc.app.shared.domain.overhaul.M3EBusStop
import com.amazecc.app.shared.domain.overhaul.M3ETransportAction
import com.amazecc.app.shared.domain.overhaul.M3ETransportFilter
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ETransportScreenUiTest {

    private fun sampleTransportState() = M3ETransportState(
        routes = listOf(
            M3EBusRoute(
                id = 1,
                routeNumber = "Route 12",
                routeName = "Tambaram Sanatorium to Campus via Chromepet",
                isAc = true,
                driverName = "K. Murugan",
                driverPhone = "+91 98401 23456",
                whatsappGroup = "https://chat.whatsapp.com/sample12",
                busLocation = "Near TT Gate",
                stops = listOf(
                    M3EBusStop(stopOrder = 1, stopName = "Tambaram Sanatorium", pickupTime = "06:45 AM")
                ),
                placements = listOf(
                    M3EBusPlacement(dispersalTime = "5:00 PM", zone = "Bay 4")
                )
            )
        ),
        filteredRoutes = listOf(
            M3EBusRoute(
                id = 1,
                routeNumber = "Route 12",
                routeName = "Tambaram Sanatorium to Campus via Chromepet",
                isAc = true,
                driverName = "K. Murugan",
                driverPhone = "+91 98401 23456",
                whatsappGroup = "https://chat.whatsapp.com/sample12",
                busLocation = "Near TT Gate",
                stops = listOf(
                    M3EBusStop(stopOrder = 1, stopName = "Tambaram Sanatorium", pickupTime = "06:45 AM")
                ),
                placements = listOf(
                    M3EBusPlacement(dispersalTime = "5:00 PM", zone = "Bay 4")
                )
            )
        ),
        searchQuery = "",
        activeFilter = M3ETransportFilter.ALL,
        expandedRouteId = 1
    )

    @Test
    fun testTransportScreenRendersRoutesAndDetails() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = sampleTransportState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Campus Transit").assertExists()
        onNodeWithText("Route 12").assertExists()
        onNodeWithText("AC COACH").assertExists()
        onNodeWithText("Tambaram Sanatorium to Campus via Chromepet").assertExists()
        onNodeWithText("Driver: K. Murugan").assertExists()
        onNodeWithText("Near TT Gate").assertExists()
        onNodeWithText("Tambaram Sanatorium").assertExists()
        onNodeWithText("06:45 AM").assertExists()
    }

    @Test
    fun testFilterSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETransportAction>()

        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = sampleTransportState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("AC Buses")[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ETransportAction.SelectFilter)
        assertEquals(M3ETransportFilter.AC, (capturedActions[0] as M3ETransportAction.SelectFilter).filter)
    }

    @Test
    fun testCallDriverEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETransportAction>()

        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = sampleTransportState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Call Driver").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ETransportAction.CallDriver)
        assertEquals("+91 98401 23456", (capturedActions[0] as M3ETransportAction.CallDriver).phoneNumber)
    }
}
