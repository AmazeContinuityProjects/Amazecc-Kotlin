package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EAcknowledgementDocument
import com.amazecc.app.shared.domain.overhaul.M3EContactItem
import com.amazecc.app.shared.domain.overhaul.M3EFieldItem
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EResidenceStatus
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test

/** Consolidated whoami fragments: contact cards, acknowledgement documents, personal grid. */
@OptIn(ExperimentalTestApi::class)
class M3EProfileResidenceContactTest {

    private fun state(
        contacts: List<M3EContactItem> = emptyList(),
        documents: List<M3EAcknowledgementDocument> = emptyList(),
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
        contacts = contacts,
        acknowledgementDocuments = documents,
        personalRecords = personalRecords
    )

    @Test
    fun contactCardsRenderEveryRealWhoamiContact() = runComposeUiTest {
        val contacts = listOf(
            M3EContactItem(office = "Accommodation Office", description = "Hostel allotment queries", email = "hostel@vit.ac.in"),
            M3EContactItem(office = "Examination Office", description = "Seating and hall tickets")
        )
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(contacts = contacts), onAction = {})
            }
        }

        onAllNodesWithTag(M3EProfileTestTags.CONTACT_CARD).assertCountEquals(2)
        onNodeWithText("Accommodation Office").assertExists()
        onNodeWithText("Hostel allotment queries").assertExists()
        onNodeWithText("hostel@vit.ac.in").assertExists()
        onNodeWithText("Examination Office").assertExists()
        // No email is fabricated for a contact without one.
        onNodeWithText("seating@vit.ac.in").assertDoesNotExist()
    }

    @Test
    fun contactCardsAreOmittedWhenAbsent() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(contacts = emptyList()), onAction = {})
            }
        }

        onAllNodesWithTag(M3EProfileTestTags.CONTACT_CARD).assertCountEquals(0)
        onNodeWithText("Contact").assertDoesNotExist()
    }

    @Test
    fun acknowledgementDocumentsRenderSerialNameAndStatus() = runComposeUiTest {
        val documents = listOf(
            M3EAcknowledgementDocument(serialNo = "1", documentName = "Undertaking", status = "Accepted"),
            M3EAcknowledgementDocument(serialNo = "2", documentName = " Bonafide ", status = "Pending")
        )
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(documents = documents), onAction = {})
            }
        }

        onAllNodesWithTag(M3EProfileTestTags.ACK_DOCUMENT).assertCountEquals(2)
        onNodeWithText("1. Undertaking").assertExists()
        onNodeWithText("Accepted").assertExists()
        onNodeWithText("2. Bonafide").assertExists()
        onNodeWithText("Pending").assertExists()
    }

    @Test
    fun acknowledgementSectionOmittedWhenAbsent() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(documents = emptyList()), onAction = {})
            }
        }

        onAllNodesWithTag(M3EProfileTestTags.ACK_DOCUMENT).assertCountEquals(0)
        onNodeWithText("Acknowledgement").assertDoesNotExist()
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
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Personal Information"))
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
