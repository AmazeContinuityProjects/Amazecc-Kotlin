package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EFieldItem
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EResidenceStatus
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Consolidated whoami fragments on the profile surface after D-110: personal grid and
 * residence stay inline, while contacts and acknowledgements moved to their own pages.
 */
@OptIn(ExperimentalTestApi::class)
class M3EProfileResidenceContactTest {

    private fun state(
        personalRecords: List<M3EFieldItem> = emptyList(),
        residence: M3EResidenceStatus = M3EResidenceStatus.HOSTELLER,
        dayboarderInfo: String? = null
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
        dayboarderInfo = dayboarderInfo,
        personalRecords = personalRecords
    )

    @Test
    fun profileOffersContactsMenuCardThatNavigates() = runComposeUiTest {
        var opened = 0
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(),
                    onAction = {},
                    onOpenContacts = { opened++ }
                )
            }
        }

        // The card sits down the LazyColumn; bring it into composition before clicking.
        onNodeWithTag("profile_list").performScrollToNode(
            hasTestTag(M3EProfileTestTags.CONTACTS_MENU_CARD)
        )
        onNodeWithTag(M3EProfileTestTags.CONTACTS_MENU_CARD).performClick()
        assertEquals(1, opened)
        onNodeWithText("Contacts").assertExists()
    }

    @Test
    fun movedSectionsAreAbsentFromProfile() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(), onAction = {})
            }
        }

        // D-110: acknowledgements live on Documents; contact rows live on Contacts.
        onNodeWithText("Acknowledgement").assertDoesNotExist()
        onNodeWithText("Description").assertDoesNotExist()
    }

    @Test
    fun personalInformationFoldsRecordsAndHostelStatus() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        personalRecords = listOf(M3EFieldItem("Nationality", "Indian")),
                        residence = M3EResidenceStatus.HOSTELLER
                    ),
                    onAction = {}
                )
            }
        }

        // The personal grid sits far down the LazyColumn; bring it into composition first.
        onNodeWithTag("profile_list").performScrollToNode(hasText("Personal Information"))
        onNodeWithText("Personal Information").assertExists()
        onNodeWithText("Nationality").assertExists()
        onNodeWithText("Indian").assertExists()
        onNodeWithText("Programme").assertExists()
        onNodeWithTag(M3EProfileTestTags.RESIDENCE_VALUE, useUnmergedTree = true).assertTextEquals("Q Block - Room 514")
    }

    @Test
    fun dayscholarResidenceCardIsIndependentOfHostelFields() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        residence = M3EResidenceStatus.DAYSCHOLAR,
                        dayboarderInfo = null
                    ),
                    onAction = {}
                )
            }
        }

        // Hostel block/room may still be present in the payload, but residence wins.
        onNodeWithTag(M3EProfileTestTags.RESIDENCE_VALUE, useUnmergedTree = true).assertTextEquals("Dayscholar")
    }
}
