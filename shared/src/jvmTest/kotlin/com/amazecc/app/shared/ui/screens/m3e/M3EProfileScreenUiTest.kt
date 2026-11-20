package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EProfileScreenUiTest {

    private fun sampleProfileState() = M3EProfileState(
        studentName = "Aditya Sharma",
        registrationNumber = "22BCE1045",
        applicationNumber = "1202204567",
        programme = "B.Tech Computer Science & Engineering",
        school = "SCOPE",
        campus = "Vellore Campus",
        dob = "14 Aug 2004",
        bloodGroup = "O +ve",
        email = "aditya.sharma2022@vitstudent.ac.in",
        phone = "+91 98401 22334",
        proctorName = "Dr. Anita R",
        proctorEmail = "anita.r@vit.ac.in",
        proctorCabin = "SJT 413-A",
        hostelBlock = "Q Block",
        hostelRoom = "514",
        cgpa = 9.24,
        attendancePercentage = 91.5,
        isIdCardFlipped = false
    )

    @Test
    fun testProfileRendersDigitalIdAndStats() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = sampleProfileState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Student Profile").assertExists()
        onNodeWithText("Aditya Sharma").assertExists()
        onNodeWithText("22BCE1045").assertExists()
        onNodeWithText("VELLORE INSTITUTE OF TECHNOLOGY").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("91.50%").assertExists()
        onNodeWithText("Dr. Anita R").assertExists()
    }

    @Test
    fun testFlipIdCardEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EProfileAction>()

        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = sampleProfileState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("VELLORE INSTITUTE OF TECHNOLOGY").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EProfileAction.FlipIdCard)
    }

    // D-110: direct contact email actions moved to the Contacts page. The profile's
    // menu-card navigation is covered by M3EProfileResidenceContactTest and the email action by
    // M3EContactsScreenUiTest, so this stale profile-level assertion was removed.
}
