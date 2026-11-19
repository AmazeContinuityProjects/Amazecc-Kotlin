package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EContactItem
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EResidenceStatus
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Residence segmented control, bus checkbox, and the preserved profile actions. */
@OptIn(ExperimentalTestApi::class)
class M3EProfileResidenceActionsTest {

    private fun state(
        residence: M3EResidenceStatus,
        isRegisteredForBus: Boolean = false,
        contacts: List<M3EContactItem> = emptyList()
    ) = M3EProfileState(
        studentName = "Aditya Sharma",
        registrationNumber = "22BCE1045",
        applicationNumber = "1202204567",
        programme = "B.Tech Computer Science & Engineering",
        school = "SCOPE",
        campus = "Vellore Campus",
        dob = "14 Aug 2004",
        bloodGroup = "O +ve",
        email = "aditya@vitstudent.ac.in",
        phone = "+91 98401 22334",
        proctorName = "Dr. Anita R",
        proctorEmail = "anita.r@vit.ac.in",
        hostelBlock = "Q Block",
        hostelRoom = "514",
        cgpa = 9.24,
        attendancePercentage = 91.5,
        residence = residence,
        isRegisteredForBus = isRegisteredForBus,
        contacts = contacts
    )

    @Test
    fun segmentedControlEmitsSetResidenceForBothChoices() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(M3EResidenceStatus.HOSTELLER),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithTag(M3EProfileTestTags.RESIDENCE_SEGMENT_DAYSCHOLAR, useUnmergedTree = true).performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EProfileAction.SetResidence)
        assertEquals(M3EResidenceStatus.DAYSCHOLAR, (actions[0] as M3EProfileAction.SetResidence).residence)
    }

    @Test
    fun busCheckboxOnlyVisibleForDayscholarAndEmitsAction() = runComposeUiTest {
        val hostellerActions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(M3EResidenceStatus.HOSTELLER),
                    onAction = { hostellerActions.add(it) }
                )
            }
        }
        onNodeWithTag(M3EProfileTestTags.BUS_CHECKBOX, useUnmergedTree = true).assertDoesNotExist()

        val dayscholarActions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(M3EResidenceStatus.DAYSCHOLAR, isRegisteredForBus = false),
                    onAction = { dayscholarActions.add(it) }
                )
            }
        }
        onNodeWithTag(M3EProfileTestTags.BUS_CHECKBOX, useUnmergedTree = true).performClick()
        assertEquals(1, dayscholarActions.size)
        assertTrue(dayscholarActions[0] is M3EProfileAction.SetRegisteredForBus)
        assertEquals(true, (dayscholarActions[0] as M3EProfileAction.SetRegisteredForBus).enabled)
        onNodeWithText("Registered for University Transport / Bus").assertExists()
    }

    @Test
    fun flipAndRefreshActionsArePreserved() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(M3EResidenceStatus.HOSTELLER), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("VELLORE INSTITUTE OF TECHNOLOGY").performClick()
        onNodeWithContentDescription("Refresh").performClick()

        assertEquals(2, actions.size)
        assertTrue(actions[0] is M3EProfileAction.FlipIdCard)
        assertTrue(actions[1] is M3EProfileAction.Refresh)
    }

    @Test
    fun contactEmailFromWhoamiContactEmitsOpenEmail() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        residence = M3EResidenceStatus.DAYSCHOLAR,
                        contacts = listOf(
                            M3EContactItem(
                                office = "Transport Office",
                                description = "Bus pass queries",
                                email = "transport@vit.ac.in"
                            )
                        )
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        // The contact section sits down the LazyColumn; scroll the icon into view before clicking.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Email transport@vit.ac.in"))
        // The email icon (not the card itself) emits the action.
        onNodeWithContentDescription("Email transport@vit.ac.in").performClick()
        assertTrue(actions.any { it is M3EProfileAction.OpenEmail && it.email == "transport@vit.ac.in" })
    }

    @Test
    fun dayscholarSelectionEmitsEvenFromUnknownState() = runComposeUiTest {
        val actions = mutableListOf<M3EProfileAction>()
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(M3EResidenceStatus.UNKNOWN), onAction = { actions.add(it) })
            }
        }

        onNodeWithTag(M3EProfileTestTags.RESIDENCE_SEGMENT_HOSTELLER, useUnmergedTree = true).performClick()
        assertEquals(1, actions.size)
        assertEquals(M3EResidenceStatus.HOSTELLER, (actions[0] as M3EProfileAction.SetResidence).residence)
    }

    @Test
    fun residenceParsingToleratesPayloadAndOnboardingValues() {
        assertEquals(M3EResidenceStatus.HOSTELLER, parseM3EResidence("HOSTELLER"))
        assertEquals(M3EResidenceStatus.HOSTELLER, parseM3EResidence("hosteller"))
        assertEquals(M3EResidenceStatus.DAYSCHOLAR, parseM3EResidence("DAYSCHOLAR"))
        assertEquals(M3EResidenceStatus.DAYSCHOLAR, parseM3EResidence("Day Scholar"))
        assertEquals(M3EResidenceStatus.UNKNOWN, parseM3EResidence(null))
        assertEquals(M3EResidenceStatus.UNKNOWN, parseM3EResidence(""))
    }
}
