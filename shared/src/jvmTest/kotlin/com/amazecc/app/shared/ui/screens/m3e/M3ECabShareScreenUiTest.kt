package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ECabShareAction
import com.amazecc.app.shared.domain.overhaul.M3ECabShareFilter
import com.amazecc.app.shared.domain.overhaul.M3ECabShareHub
import com.amazecc.app.shared.domain.overhaul.M3ECabShareRide
import com.amazecc.app.shared.domain.overhaul.M3ECabShareState
import com.amazecc.app.shared.domain.overhaul.M3EGenderPreference
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ECabShareScreenUiTest {

    private fun sampleCabShareState() = M3ECabShareState(
        hubs = listOf(
            M3ECabShareHub("hub_1", "Chennai Airport", "MAA Airport Split")
        ),
        rides = listOf(
            M3ECabShareRide(
                tripId = "trip_01",
                creatorName = "Aditya Sharma",
                creatorRegNo = "22BCE1045",
                hubName = "Chennai Airport (MAA)",
                travelDate = "28 Nov 2026",
                preferredTime = "04:30 AM",
                toleranceHours = 1,
                availableSeats = 2,
                totalSeats = 4,
                genderPreference = M3EGenderPreference.ANY,
                notes = "Indigo 6E 214 flight at 08:30 AM",
                creatorPhone = "+91 98401 99999",
                isJoined = false
            )
        ),
        activeFilter = M3ECabShareFilter.ALL,
        searchQuery = ""
    )

    @Test
    fun testCabShareRendersRidesAndHubs() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECabShareScreen(
                    state = sampleCabShareState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Cab Share").assertExists()
        onNodeWithText("Chennai Airport").assertExists()
        onNodeWithText("Chennai Airport (MAA)").assertExists()
        onNodeWithText("2 of 4 seats left").assertExists()
        onNodeWithText("Posted by Aditya Sharma").assertExists()
        onNodeWithText("22BCE1045").assertExists()
        onNodeWithText("Join Pool").assertExists()
    }

    @Test
    fun testJoinTripEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECabShareAction>()

        setContent {
            AmazeM3ETheme {
                M3ECabShareScreen(
                    state = sampleCabShareState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Join Pool").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ECabShareAction.JoinTrip)
        assertEquals("trip_01", (capturedActions[0] as M3ECabShareAction.JoinTrip).tripId)
    }

    @Test
    fun testOfferRideEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECabShareAction>()

        setContent {
            AmazeM3ETheme {
                M3ECabShareScreen(
                    state = sampleCabShareState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Offer Ride").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3ECabShareAction.OpenCreateDialog)
    }
}
