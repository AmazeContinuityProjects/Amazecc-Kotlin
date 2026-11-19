package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EResidenceStatus
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test

/** Residence-adaptive rendering of the status card and the ID card back. */
@OptIn(ExperimentalTestApi::class)
class M3EProfileResidenceCardTest {

    private fun state(
        residence: M3EResidenceStatus,
        flipped: Boolean = false,
        hostelBlock: String? = "Q Block",
        hostelRoom: String? = "514",
        dayboarderInfo: String? = null,
        campus: String = "Vellore Campus"
    ) = M3EProfileState(
        studentName = "Aditya Sharma",
        registrationNumber = "22BCE1045",
        applicationNumber = "1202204567",
        programme = "B.Tech Computer Science & Engineering",
        school = "SCOPE",
        campus = campus,
        dob = "14 Aug 2004",
        bloodGroup = "O +ve",
        email = "aditya@vitstudent.ac.in",
        phone = "+91 98401 22334",
        proctorName = "Dr. Anita R",
        proctorEmail = "anita.r@vit.ac.in",
        hostelBlock = hostelBlock,
        hostelRoom = hostelRoom,
        cgpa = 9.24,
        attendancePercentage = 91.5,
        residence = residence,
        dayboarderInfo = dayboarderInfo,
        isIdCardFlipped = flipped
    )

    @Test
    fun hostellerCardUsesHostelDetailsAndShowsResidentBadge() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(M3EResidenceStatus.HOSTELLER), onAction = {})
            }
        }

        onNodeWithTag(M3EProfileTestTags.RESIDENCE_VALUE, useUnmergedTree = true).assertTextEquals("Q Block - Room 514")
        onNodeWithTag(M3EProfileTestTags.RESIDENT_BADGE, useUnmergedTree = true).assertTextEquals("Hosteller")
        // Residence is explicit now — a dayboarder card is not invented from hostel fields.
        onNodeWithText("Dayboarder Info").assertDoesNotExist()
    }

    @Test
    fun hostellerIdBackShowsBlockAndRoom() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(state = state(M3EResidenceStatus.HOSTELLER, flipped = true), onAction = {})
            }
        }

        onNodeWithTag(M3EProfileTestTags.ID_BACK_RESIDENCE, useUnmergedTree = true).assertTextEquals("Q Block - Room 514")
        onNodeWithText("Hostel Residence").assertExists()
    }

    @Test
    fun dayscholarCardUsesDayboarderInfoAndDayScholarBadge() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        residence = M3EResidenceStatus.DAYSCHOLAR,
                        dayboarderInfo = "Day Boarder (Vellore)"
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithTag(M3EProfileTestTags.RESIDENCE_VALUE, useUnmergedTree = true).assertTextEquals("Day Boarder (Vellore)")
        onNodeWithTag(M3EProfileTestTags.RESIDENT_BADGE, useUnmergedTree = true).assertTextEquals("Day Scholar")
    }

    @Test
    fun dayscholarIdBackFallsBackToDayscholarLabel() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(M3EResidenceStatus.DAYSCHOLAR, flipped = true, dayboarderInfo = null),
                    onAction = {}
                )
            }
        }

        onNodeWithTag(M3EProfileTestTags.ID_BACK_RESIDENCE, useUnmergedTree = true).assertTextEquals("Dayscholar")
    }

    @Test
    fun dayscholarNeverShowsHostelRoomOnIdBackEvenWhenFieldsExist() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        residence = M3EResidenceStatus.DAYSCHOLAR,
                        flipped = true,
                        hostelBlock = "Q Block",
                        hostelRoom = "514",
                        dayboarderInfo = "Day Boarder (Vellore)"
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithTag(M3EProfileTestTags.ID_BACK_RESIDENCE, useUnmergedTree = true).assertTextEquals("Day Boarder (Vellore)")
    }

    @Test
    fun unknownStateShowsDashAndNoBadge() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EProfileScreen(
                    state = state(
                        residence = M3EResidenceStatus.UNKNOWN,
                        flipped = true,
                        hostelBlock = null,
                        hostelRoom = null,
                        dayboarderInfo = null
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithTag(M3EProfileTestTags.RESIDENCE_VALUE, useUnmergedTree = true).assertTextEquals("—")
        onNodeWithTag(M3EProfileTestTags.ID_BACK_RESIDENCE, useUnmergedTree = true).assertTextEquals("—")
        onNodeWithTag(M3EProfileTestTags.RESIDENT_BADGE, useUnmergedTree = true).assertDoesNotExist()
    }
}
