package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EClubCategory
import com.amazecc.app.shared.domain.overhaul.M3EClubHubAction
import com.amazecc.app.shared.domain.overhaul.M3EClubHubState
import com.amazecc.app.shared.domain.overhaul.M3EClubItem
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wiring coverage for club discovery and club-detail real-data actions. */
@OptIn(ExperimentalTestApi::class)
class M3EEventHubWiringClubTest {

    private fun sampleClub(
        type: String = "CHAPTER",
        lead: String = "Ada Lovelace",
        faculty: String = "Dr. Turing",
        members: Int = 40,
        recruiting: Boolean = true,
        instagram: String? = "codechefvitc",
        recruitmentLink: String? = "https://forms.example.com/clb01"
    ) = M3EClubItem(
        id = "club_01",
        name = "CodeChef VITC",
        category = M3EClubCategory.TECHNICAL,
        type = type,
        studentLead = lead,
        facultyCoordinator = faculty,
        memberCount = members,
        isRecruiting = recruiting,
        instagramHandle = instagram,
        description = "Competitive programming club.",
        recruitmentLink = recruitmentLink
    )

    @Test
    fun clubHubInstagramButtonEmitsOpenLink() = runComposeUiTest {
        val actions = mutableListOf<M3EClubHubAction>()
        val club = sampleClub(instagram = "codechefvitc")
        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = M3EClubHubState(
                        clubs = listOf(club),
                        filteredClubs = listOf(club),
                        expandedClubId = club.id
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("@codechefvitc").performClick()
        assertEquals(1, actions.size)
        val link = actions.first() as M3EClubHubAction.OpenLink
        assertEquals("https://instagram.com/codechefvitc", link.url)
    }

    @Test
    fun clubHubNoticeIsVisibleAndDismissible() = runComposeUiTest {
        val actions = mutableListOf<M3EClubHubAction>()
        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = M3EClubHubState(notice = "No application link is available for this club yet."),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("No application link is available for this club yet.").assertExists()
        onNodeWithText("Dismiss").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EClubHubAction.DismissNotice)
    }

    @Test
    fun clubHubOmitsRowsWhenSourceFieldsAreAbsent() = runComposeUiTest {
        val club = sampleClub(
            type = "",
            lead = "",
            faculty = "",
            members = 0,
            recruiting = false,
            instagram = null,
            recruitmentLink = null
        )
        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = M3EClubHubState(
                        clubs = listOf(club),
                        filteredClubs = listOf(club),
                        expandedClubId = club.id
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Lead: ").assertDoesNotExist()
        onNodeWithText("Faculty Coordinator: ").assertDoesNotExist()
        onNodeWithText("0+ members").assertDoesNotExist()
        onNodeWithText("CHAPTER").assertDoesNotExist()
    }

    @Test
    fun applyButtonUsesRecruitmentLinkEvenWhenNotMarkedRecruiting() = runComposeUiTest {
        val actions = mutableListOf<M3EClubHubAction>()
        val club = sampleClub(recruiting = false, recruitmentLink = "https://forms.example.com/clb01")
        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = M3EClubHubState(
                        clubs = listOf(club),
                        filteredClubs = listOf(club),
                        expandedClubId = club.id
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Apply Now").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EClubHubAction.ApplyClub)
        assertEquals("club_01", (actions[0] as M3EClubHubAction.ApplyClub).clubId)
    }

    @Test
    fun clubDetailWithEventEmitsRegisterEvent() = runComposeUiTest {
        val actions = mutableListOf<M3EClubDetailAction>()
        setContent {
            AmazeM3ETheme {
                M3EClubDetailScreen(
                    state = M3EClubDetailState(
                        clubId = "club_01",
                        name = "CodeChef VITC",
                        description = "Competitive programming.",
                        eventId = "evt_09"
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Register for Event").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EClubDetailAction.RegisterEvent)
    }

    @Test
    fun clubDetailWithoutEventUsesOwnSiteLink() = runComposeUiTest {
        val actions = mutableListOf<M3EClubDetailAction>()
        setContent {
            AmazeM3ETheme {
                M3EClubDetailScreen(
                    state = M3EClubDetailState(
                        clubId = "club_01",
                        name = "CodeChef VITC",
                        applyUrl = "https://club.example/apply"
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Apply on club's own site").performClick()
        assertEquals(1, actions.size)
        val link = actions.first() as M3EClubDetailAction.OpenLink
        assertEquals("https://club.example/apply", link.url)
    }

    @Test
    fun clubDetailRegistrationStatusOpensPaymentUrl() = runComposeUiTest {
        val actions = mutableListOf<M3EClubDetailAction>()
        setContent {
            AmazeM3ETheme {
                M3EClubDetailScreen(
                    state = M3EClubDetailState(
                        clubId = "club_01",
                        name = "CodeChef VITC",
                        registrationStatus = M3EEventRegistrationUi(
                            status = "payment_form",
                            message = "Finish payment on the event hub.",
                            paymentUrl = "https://events.example/pay/club_01"
                        )
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Payment required").assertExists()
        onNodeWithText("Pay now").performClick()
        assertEquals(1, actions.size)
        val link = actions.first() as M3EClubDetailAction.OpenLink
        assertEquals("https://events.example/pay/club_01", link.url)
    }

    @Test
    fun clubDetailClubNotFoundIsVisible() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EClubDetailScreen(state = M3EClubDetailState(), onAction = {})
            }
        }
        onAllNodesWithText("Club not found")[0].assertExists()
    }
}
