package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EBusRoute
import com.amazecc.app.shared.domain.overhaul.M3ETransportAction
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM tests for the newly wired CallDriver / OpenWhatsappGroup actions. */
@OptIn(ExperimentalTestApi::class)
class M3EActionsTransportTest {

    private fun route(
        phone: String = "+91 98401 23456",
        group: String? = "https://chat.whatsapp.com/route12"
    ) = M3EBusRoute(
        id = 1,
        routeNumber = "Route 12",
        routeName = "Campus Loop",
        isAc = true,
        driverName = "K. Murugan",
        driverPhone = phone,
        whatsappGroup = group
    )

    private fun stateFor(route: M3EBusRoute) = M3ETransportState(
        routes = listOf(route),
        filteredRoutes = listOf(route),
        expandedRouteId = route.id
    )

    @Test
    fun callDriverEmitsRealPhoneNumber() = runComposeUiTest {
        val actions = mutableListOf<M3ETransportAction>()
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateFor(route()),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Call Driver").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ETransportAction.CallDriver)
        assertEquals(
            "+91 98401 23456",
            (actions.first() as M3ETransportAction.CallDriver).phoneNumber
        )
    }

    @Test
    fun whatsappButtonEmitsGroupUrl() = runComposeUiTest {
        val actions = mutableListOf<M3ETransportAction>()
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateFor(route()),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Route Chat").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ETransportAction.OpenWhatsappGroup)
        assertEquals(
            "https://chat.whatsapp.com/route12",
            (actions.first() as M3ETransportAction.OpenWhatsappGroup).link
        )
    }

    @Test
    fun callButtonIsHiddenWhenDriverPhoneIsBlank() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateFor(route(phone = "")),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Call Driver").assertDoesNotExist()
    }

    @Test
    fun chatButtonIsHiddenWhenGroupIsMissing() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETransportScreen(
                    state = stateFor(route(group = null)),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Route Chat").assertDoesNotExist()
    }
}
