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
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EClubHubScreenUiTest {

    private fun sampleClubState() = M3EClubHubState(
        clubs = listOf(
            M3EClubItem(
                id = "club_01",
                name = "ACM Student Chapter VIT",
                category = M3EClubCategory.TECHNICAL,
                type = "CHAPTER",
                studentLead = "Rohan Verma",
                facultyCoordinator = "Dr. B. K. Tripathy",
                memberCount = 180,
                isRecruiting = true,
                recruitmentDeadline = "02 Dec 2026",
                instagramHandle = "acm_vit",
                description = "Premier computing society organizing hackathons and code camps."
            )
        ),
        filteredClubs = listOf(
            M3EClubItem(
                id = "club_01",
                name = "ACM Student Chapter VIT",
                category = M3EClubCategory.TECHNICAL,
                type = "CHAPTER",
                studentLead = "Rohan Verma",
                facultyCoordinator = "Dr. B. K. Tripathy",
                memberCount = 180,
                isRecruiting = true,
                recruitmentDeadline = "02 Dec 2026",
                instagramHandle = "acm_vit",
                description = "Premier computing society organizing hackathons and code camps."
            )
        ),
        selectedCategory = M3EClubCategory.ALL,
        searchQuery = "",
        expandedClubId = "club_01"
    )

    @Test
    fun testClubHubRendersClubsAndRecruitment() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = sampleClubState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Clubs & Chapters").assertExists()
        onNodeWithText("ACM Student Chapter VIT").assertExists()
        onNodeWithText("CHAPTER").assertExists()
        onNodeWithText("RECRUITING").assertExists()
        onNodeWithText("Lead: Rohan Verma").assertExists()
        onNodeWithText("Apply Now").assertExists()
        onNodeWithText("@acm_vit").assertExists()
    }

    @Test
    fun testApplyClubEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EClubHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = sampleClubState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Apply Now").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EClubHubAction.ApplyClub)
        assertEquals("club_01", (capturedActions[0] as M3EClubHubAction.ApplyClub).clubId)
    }

    @Test
    fun testCategoryFilterSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EClubHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EClubHubScreen(
                    state = sampleClubState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("Tech Chapters")[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EClubHubAction.SelectCategory)
        assertEquals(M3EClubCategory.TECHNICAL, (capturedActions[0] as M3EClubHubAction.SelectCategory).category)
    }
}
