package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D-164: stateless Friend Finder screen. State is real-shaped (server-style handles, shared
 * counts, class ids) — no fabricated names or counts. Covers the Classes|Friends switch,
 * search, the friend row, the profile visibility sheet, and honest empty/error copy.
 */
@OptIn(ExperimentalTestApi::class)
class M3EFriendFinderScreenUiTest {

    private fun sampleState() = M3EFriendFinderState(
        joined = true,
        discoverable = true,
        participants = 42,
        classes = listOf(
            FriendFinderClassUi(
                classId = "CSE3002-A1",
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                slot = "A1",
                faculty = "Prof. Sundar",
                people = listOf(
                    FriendFinderPersonUi(
                        handle = "@ada",
                        name = "Ada Lovelace",
                        regNo = "21BCE1042",
                        source = "registered"
                    )
                )
            ),
            FriendFinderClassUi(
                classId = "CSE2005-B1",
                courseCode = "CSE2005",
                courseTitle = "Operating Systems",
                slot = "B1+TB1",
                faculty = "Dr. Anita",
                people = emptyList()
            )
        ),
        friends = listOf(
            FriendFinderFriendUi(
                handle = "@ada",
                name = "Ada Lovelace",
                regNo = "21BCE1042",
                source = "registered",
                shared = 2,
                sharedClassIds = listOf("CSE3002-A1", "CSE2005-B1"),
                identical = true
            )
        ),
        overlaps = listOf(
            FriendFinderFriendUi(
                handle = "@ada",
                name = "Ada Lovelace",
                regNo = "21BCE1042",
                source = "registered",
                shared = 2,
                sharedClassIds = listOf("CSE3002-A1", "CSE2005-B1"),
                identical = true
            )
        ),
        self = FriendFinderPersonUi(handle = "@me", name = "You", source = "registered"),
        selfClassCount = 4,
        socialHandles = listOf("@me", "@me-old")
    )

    @Test
    fun classesFriendsSwitchEmitsTheGroupingAction() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Classes").assertExists()
        onNodeWithText("Friends").performClick()
        assertTrue(actions.contains(M3EFriendFinderAction.SetGrouping(FriendFinderGrouping.FRIENDS)))
        assertTrue(actions.none { it is M3EFriendFinderAction.SetGrouping && it.grouping == FriendFinderGrouping.CLASSES })
    }

    @Test
    fun searchFieldEmitsTheQueryAction() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(grouping = FriendFinderGrouping.FRIENDS),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithTag(FRIEND_FINDER_SEARCH_TEST_TAG).performTextInput("Ada")
        assertTrue(actions.any { it is M3EFriendFinderAction.SetQuery && it.query == "Ada" })
    }

    @Test
    fun friendRowEmitsOpenFriendAndShowsRealCountsAndBadge() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(grouping = FriendFinderGrouping.FRIENDS),
                    onAction = { actions.add(it) }
                )
            }
        }

        // Stats come from the DTOs, not invented text.
        // M3EStatCard uppercases its label token.
        onNodeWithText("CLASSMATES").assertExists()
        onNodeWithText("JOINED").assertExists()
        onNodeWithText("42").assertExists()
        onNodeWithText("Identical").assertExists()
        onAllNodesWithText("Identical").assertCountEquals(1)

        // Lazy rows are not composed until scrolled into view.
        onNodeWithTag(FRIEND_FINDER_LIST_TEST_TAG)
            .performScrollToNode(hasText("Ada Lovelace"))
        onNodeWithContentDescription("Open Ada Lovelace").performClick()
        assertTrue(actions.contains(M3EFriendFinderAction.OpenFriend("@ada")))
    }

    @Test
    fun visibilityToggleInProfileSheetEmitsTheVisibilityAction() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        AppState.pushOverlay(FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID)
        try {
            setContent {
                AmazeM3ETheme {
                    M3EFriendFinderScreen(
                        state = sampleState().copy(isProfileOpen = true),
                        onAction = { actions.add(it) }
                    )
                }
            }

            onNodeWithText("Your Profile").assertExists()
            onNodeWithContentDescription("Toggle Visible to classmates").performClick()
            assertTrue(actions.contains(M3EFriendFinderAction.SetDiscoverable(false)))
        } finally {
            AppState.popOverlay(FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID)
        }
    }

    @Test
    fun emptyCopyIsHonestWhenNoFriendsHaveJoined() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(
                        grouping = FriendFinderGrouping.FRIENDS,
                        friends = emptyList(),
                        overlaps = emptyList()
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("None of your friends have joined yet. Once classmates register their timetable, they will show up here.").assertExists()
        onNodeWithText("Ada Lovelace").assertDoesNotExist()
    }

    @Test
    fun signedOutAndErrorCopyStatesTheSignInRequirement() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(
                        joined = false,
                        discoverable = false,
                        isLoading = false,
                        errorMessage = "Sign in with Amaze ID to use Friend Finder.",
                        needsSignIn = true
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Find classmates").assertExists()
        // The same sign-required copy appears on the signed-out card and in the error banner.
        onAllNodesWithText("Sign in with Amaze ID to use Friend Finder.").assertCountEquals(2)
        // No fake success or invented participant count while signed out.
        onNodeWithText("CLASSMATES").assertDoesNotExist()
        onNodeWithText("42").assertDoesNotExist()
    }

    @Test
    fun loadingStateIsHonestWithNoServerDataRendered() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(isLoading = true),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Loading friend finder…").assertExists()
        onNodeWithText("Reading your Classes Together state from the server.").assertExists()
        onNodeWithText("Ada Lovelace").assertDoesNotExist()
    }

    // -- D-193: sign-in, retry and match-rule honesty ------------------------------------

    @Test
    fun signInButtonIsRealAndEmitsSignIn() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(
                        joined = false,
                        discoverable = false,
                        isLoading = false,
                        needsSignIn = true,
                        signInMessage = "You're signed out. Sign in with Amaze ID to load your Classes Together state.",
                        errorMessage = "Friend Finder needs an Amaze ID session to read your classes."
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithTag(FRIEND_FINDER_SIGN_IN_TEST_TAG).assertExists().performClick()
        assertTrue(actions.contains(M3EFriendFinderAction.SignIn))
        // A dead "Publish" control is hidden while signed in is required.
        onNodeWithContentDescription("Publish registered classes").assertDoesNotExist()
    }

    private fun signInState(message: String) = sampleState().copy(
        joined = false,
        discoverable = false,
        isLoading = false,
        needsSignIn = true,
        signInMessage = message,
        errorMessage = "Friend Finder is unavailable right now."
    )

    @Test
    fun amazeIdSignInMessageIsRenderedVerbatim() = runComposeUiTest {
        val amazeCopy = "Sign in with Amaze ID to open Friend Finder."
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(state = signInState(amazeCopy), onAction = {})
            }
        }
        onNodeWithText(amazeCopy).assertExists()
        onNodeWithText("Sign in again with VTOP and load this semester's attendance to open Friend Finder.")
            .assertDoesNotExist()
    }

    @Test
    fun vtopSignInMessageIsRenderedVerbatim() = runComposeUiTest {
        val vtopCopy = "Sign in again with VTOP and load this semester's attendance to open Friend Finder."
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(state = signInState(vtopCopy), onAction = {})
            }
        }
        onNodeWithText(vtopCopy).assertExists()
        onNodeWithText("Sign in with Amaze ID to open Friend Finder.").assertDoesNotExist()
    }

    @Test
    fun errorCardOffersARealRetry() = runComposeUiTest {
        val actions = mutableListOf<M3EFriendFinderAction>()
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(errorMessage = "The server is unreachable right now."),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("The server is unreachable right now.").assertExists()
        onNodeWithTag(FRIEND_FINDER_RETRY_TEST_TAG).assertExists().performClick()
        assertTrue(actions.contains(M3EFriendFinderAction.Refresh))
    }

    @Test
    fun friendsTabExplainsWhyOneSharedClassIsNotAMatch() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFriendFinderScreen(
                    state = sampleState().copy(grouping = FriendFinderGrouping.FRIENDS),
                    onAction = {}
                )
            }
        }

        onNodeWithText(
            "Matches need at least two classes in common or identical timetables. Sharing just one class won't show up here."
        ).assertExists()
    }
}
