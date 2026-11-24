package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3EStatCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3EStatSubtitle
import com.amazecc.app.shared.ui.m3e.components.SettingsToggleRow
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/** LIFO ids owned by the host — the opener MUST push/pop these or sheet dismissal is blocked. */
internal const val FRIEND_FINDER_FRIEND_SHEET_OVERLAY_ID = "ff-friend-sheet"
internal const val FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID = "ff-profile-sheet"
internal const val FRIEND_FINDER_SEARCH_TEST_TAG = "ff_search"
internal const val FRIEND_FINDER_LIST_TEST_TAG = "ff_list"
internal const val FRIEND_FINDER_SIGN_IN_TEST_TAG = "ff_sign_in"
internal const val FRIEND_FINDER_RETRY_TEST_TAG = "ff_retry"

/** Classes | Friends segmented switch, matching the web friend finder. */
enum class FriendFinderGrouping(val label: String) {
    CLASSES("Classes"),
    FRIENDS("Friends")
}

/** One person in a class roster or the caller's own identity. No field is synthesized. */
data class FriendFinderPersonUi(
    val handle: String = "",
    val name: String = "",
    val regNo: String = "",
    val source: String = ""
) {
    val displayName: String get() = name.ifBlank { handle }
}

/** One Classes Together section (a class + the people paired in it). */
data class FriendFinderClassUi(
    val classId: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val slot: String = "",
    val faculty: String = "",
    val people: List<FriendFinderPersonUi> = emptyList()
)

/** A friend row / overlap. [shared] and [sharedClassIds] come straight from the server. */
data class FriendFinderFriendUi(
    val handle: String = "",
    val name: String = "",
    val regNo: String = "",
    val source: String? = null,
    val shared: Int = 0,
    val sharedClassIds: List<String> = emptyList(),
    val identical: Boolean = false
) {
    val displayName: String get() = name.ifBlank { handle }
}

/**
 * Everything the stateless friend-finder screen renders. Every data field is mapped from a
 * real `SocialClient` DTO by [M3EFriendFinderHost]; the screen invents no names or counts.
 */
data class M3EFriendFinderState(
    val joined: Boolean = false,
    val discoverable: Boolean = false,
    val participants: Int = 0,
    val classes: List<FriendFinderClassUi> = emptyList(),
    val friends: List<FriendFinderFriendUi> = emptyList(),
    val overlaps: List<FriendFinderFriendUi> = emptyList(),
    val query: String = "",
    val grouping: FriendFinderGrouping = FriendFinderGrouping.CLASSES,
    val selectedClassId: String? = null,
    val selectedFriend: FriendFinderFriendUi? = null,
    val isProfileOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Identity-specific sign-in explanation from the host (Amaze ID vs VTOP); null keeps the Amaze fallback. */
    val signInMessage: String? = null,
    val needsSignIn: Boolean = false,
    val isBusy: Boolean = false,
    val self: FriendFinderPersonUi = FriendFinderPersonUi(),
    val selfClassCount: Int = 0,
    val socialHandles: List<String> = emptyList()
)

sealed interface M3EFriendFinderAction {
    /** Real sign-in-and-return affordance; the host parks the return screen and opens login. */
    data object SignIn : M3EFriendFinderAction
    data object Refresh : M3EFriendFinderAction
    data object Publish : M3EFriendFinderAction
    data object Leave : M3EFriendFinderAction
    data class SetDiscoverable(val discoverable: Boolean) : M3EFriendFinderAction
    data class SetGrouping(val grouping: FriendFinderGrouping) : M3EFriendFinderAction
    data class SetQuery(val query: String) : M3EFriendFinderAction
    data class OpenClass(val classId: String) : M3EFriendFinderAction
    data object CloseClass : M3EFriendFinderAction
    data class OpenFriend(val handle: String) : M3EFriendFinderAction
    data object CloseFriend : M3EFriendFinderAction
    data object OpenProfile : M3EFriendFinderAction
    data object CloseProfile : M3EFriendFinderAction
    data object DismissError : M3EFriendFinderAction
}

/**
 * D-164: 100% stateless port of the web Friend Finder discovery tab. Uses M3E primitives only:
 * [M3EPageShell] (app bar + bottom-nav padding), [M3EConnectedButtonGroup] for Classes|Friends,
 * [M3EDismissibleSheet] for the friend-overlap and profile sheets, [SettingsToggleRow] for the
 * visibility switch, and [M3ETheme] tokens throughout. All navigation and server I/O is owned
 * by [M3EFriendFinderHost].
 */
@Composable
fun M3EFriendFinderScreen(
    state: M3EFriendFinderState,
    onAction: (M3EFriendFinderAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val selectedClass = state.classes.firstOrNull { it.classId == state.selectedClassId }
    val codeOf = remember(state.classes) { state.classes.associate { it.classId to it.courseCode } }
    val visibleFriends = remember(state.friends, state.query, codeOf) {
        val q = state.query.trim()
        if (q.isEmpty()) state.friends
        else state.friends.filter { friend ->
            friend.displayName.contains(q, ignoreCase = true) ||
                friend.regNo.contains(q, ignoreCase = true) ||
                friend.sharedClassIds.any { id -> codeOf[id].orEmpty().contains(q, ignoreCase = true) }
        }
    }
    val uniqueClassmateCount = remember(state.classes) {
        state.classes.flatMap { it.people.map { person -> person.handle } }.toSet().size
    }

    M3EPageShell(
        modifier = modifier,
        eyebrow = "Social · Friend Finder",
        title = if (selectedClass != null) "Class details" else "Find your friend",
        subtitle = if (selectedClass != null) {
            listOfNotNull(
                selectedClass.courseCode.takeIf { it.isNotBlank() },
                selectedClass.slot.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Your registered section" }
        } else {
            "See who picked the same faculty and slots as you"
        },
        onBack = if (selectedClass != null) ({ onAction(M3EFriendFinderAction.CloseClass) }) else null,
        actions = @Composable {
            IconButton(
                onClick = { onAction(M3EFriendFinderAction.Refresh) },
                enabled = !state.isLoading && !state.isBusy
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh friend finder",
                    tint = colors.onSurface
                )
            }
            IconButton(onClick = { onAction(M3EFriendFinderAction.OpenProfile) }) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "Open your profile",
                    tint = colors.onSurface
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(FRIEND_FINDER_LIST_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            // A refresh/action failure stays visible even when cached joined state is on screen.
            if (!state.errorMessage.isNullOrBlank() && state.joined) {
                item(key = "ff_error_banner") { ErrorBanner(state, onAction) }
            }

            when {
                state.isLoading -> item(key = "ff_loading") { LoadingCard() }

                !state.joined -> item(key = "ff_landing") { SignedOutLanding(state, onAction) }

                selectedClass != null -> item(key = "ff_class_detail") {
                    ClassDetailCard(selectedClass, codeOf, onAction)
                }

                !state.discoverable -> item(key = "ff_hidden") { HiddenBanner(state, onAction) }

                else -> {
                    item(key = "ff_stats") {
                        StatTiles(uniqueClassmateCount, state.participants)
                    }
                    item(key = "ff_grouping") {
                        M3EConnectedButtonGroup(
                            items = FriendFinderGrouping.entries,
                            selectedItem = state.grouping,
                            onItemSelected = { onAction(M3EFriendFinderAction.SetGrouping(it)) },
                            labelProvider = { it.label }
                        )
                    }
                    when (state.grouping) {
                        FriendFinderGrouping.CLASSES -> {
                            item(key = "ff_sections_header") {
                                SectionHeader("Your sections", state.classes.size)
                            }
                            if (state.classes.isEmpty()) {
                                item(key = "ff_sections_empty") {
                                    EmptyCard(
                                        "No sections returned yet",
                                        "The server has not published any class rosters for this semester."
                                    )
                                }
                            } else {
                                items(state.classes, key = { it.classId }) { cls ->
                                    ClassRow(cls, onAction)
                                }
                            }
                        }
                        FriendFinderGrouping.FRIENDS -> {
                            item(key = "ff_search") { SearchField(state, onAction) }
                            // Honest server rule: overlaps are returned only for >= 2 shared classes
                            // or identical timetables, so one shared class alone never shows a match.
                            item(key = "ff_match_rule") { MatchRuleNote() }
                            item(key = "ff_friends_header") {
                                SectionHeader("Friends with common classes", visibleFriends.size)
                            }
                            if (visibleFriends.isEmpty()) {
                                item(key = "ff_friends_empty") {
                                    EmptyCard(
                                        if (state.query.isBlank()) "No friends joined yet"
                                        else "No friends matching “${state.query}”",
                                        if (state.query.isBlank()) {
                                            "None of your friends have joined yet. Once classmates register their timetable, they will show up here."
                                        } else {
                                            "Try a different name, registration number, or course code."
                                        }
                                    )
                                }
                            } else {
                                items(visibleFriends, key = { it.handle }) { friend ->
                                    FriendRow(friend, codeOf, onAction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedFriend?.let { friend ->
        M3EDismissibleSheet(
            isOpen = true,
            onDismiss = { onAction(M3EFriendFinderAction.CloseFriend) },
            title = friend.displayName,
            overlayId = FRIEND_FINDER_FRIEND_SHEET_OVERLAY_ID
        ) {
            val overlap = state.overlaps.firstOrNull { it.handle == friend.handle } ?: friend
            val detail = listOfNotNull(overlap.regNo.takeIf { it.isNotBlank() }) +
                listOf("${overlap.shared} shared ${if (overlap.shared == 1) "class" else "classes"}")
            Text(
                text = detail.joinToString(" · "),
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (overlap.identical) {
                Text(
                    text = "Identical",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "Classes in common",
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (overlap.sharedClassIds.isEmpty()) {
                    Text(
                        text = "No shared class ids were returned for this friend.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    overlap.sharedClassIds.forEach { id ->
                        val cls = state.classes.firstOrNull { it.classId == id }
                        Text(
                            text = listOfNotNull(
                                codeOf[id].takeIf { !it.isNullOrBlank() },
                                cls?.courseTitle?.takeIf { it.isNotBlank() },
                                cls?.slot?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ").ifBlank { id },
                            color = colors.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceContainerLow)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    if (state.isProfileOpen) {
        M3EDismissibleSheet(
            isOpen = true,
            onDismiss = { onAction(M3EFriendFinderAction.CloseProfile) },
            title = "Your Profile",
            overlayId = FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = state.self.displayName.ifBlank { "Your Amaze CC identity" },
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (state.self.source == "registered") "Verified · registered in VTOP" else "Planned timetable",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = "${state.selfClassCount} ${if (state.selfClassCount == 1) "class" else "classes"} shared",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
                SettingsToggleRow(
                    title = "Visible to classmates",
                    subtitle = if (state.discoverable) {
                        "Others in your sections can find you"
                    } else {
                        "You are hidden from everyone"
                    },
                    checked = state.discoverable,
                    enabled = !state.isBusy,
                    onCheckedChange = { onAction(M3EFriendFinderAction.SetDiscoverable(it)) }
                )
                if (state.socialHandles.size > 1) {
                    Text(
                        text = "Your social handles: ${state.socialHandles.joinToString(", ")}",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "Leaving removes your published classes from Friend Finder.",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerLow)
                        .clickableA11y(
                            onClickLabel = "Leave Friend Finder",
                            enabled = !state.isBusy,
                            onClick = { onAction(M3EFriendFinderAction.Leave) }
                        )
                        .padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun StatTiles(classmates: Int, participants: Int) {
    val primary = M3EStatItem(
        id = "ff_classmates",
        label = "Classmates",
        value = classmates.toString(),
        sub = M3EStatSubtitle.fromData("found"),
        tone = M3EStatTone.PRIMARY,
        icon = Icons.Rounded.Groups
    )
    val joined = M3EStatItem(
        id = "ff_joined",
        label = "Joined",
        value = participants.toString(),
        sub = M3EStatSubtitle.fromData("students"),
        tone = M3EStatTone.NEUTRAL,
        icon = Icons.Rounded.Person
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // M3EStatCard owns fillMaxWidth(); give it a bounded slot instead of passing weight
        // straight through, which would let the card swallow the whole row.
        Box(modifier = Modifier.weight(1f)) { M3EStatCard(stat = primary) }
        Box(modifier = Modifier.weight(1f)) { M3EStatCard(stat = joined) }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val colors = M3ETheme.colors
    Text(
        text = "$title ($count)",
        color = colors.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

@Composable
private fun SearchField(state: M3EFriendFinderState, onAction: (M3EFriendFinderAction) -> Unit) {
    OutlinedTextField(
        value = state.query,
        onValueChange = { onAction(M3EFriendFinderAction.SetQuery(it)) },
        modifier = Modifier.fillMaxWidth().testTag(FRIEND_FINDER_SEARCH_TEST_TAG),
        singleLine = true,
        placeholder = { Text("Search friends by name, reg no, or course") }
    )
}

@Composable
private fun LoadingCard() {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Loading friend finder…",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text(
            text = "Reading your Classes Together state from the server.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ErrorBanner(state: M3EFriendFinderState, onAction: (M3EFriendFinderAction) -> Unit) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = state.errorMessage.orEmpty(),
            color = colors.onErrorContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        if (state.needsSignIn) {
            // The host supplies the identity-specific message (Amaze ID vs VTOP).
            Text(
                text = state.signInMessage ?: "Sign in with Amaze ID to use Friend Finder.",
                color = colors.onErrorContainer,
                fontSize = 12.sp
            )
        }
        // At most two labelled buttons per row: retry, then sign-in when required.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onAction(M3EFriendFinderAction.Refresh) },
                modifier = Modifier.testTag(FRIEND_FINDER_RETRY_TEST_TAG)
            ) {
                Text("Retry", color = colors.onErrorContainer)
            }
            if (state.needsSignIn) {
                TextButton(
                    onClick = { onAction(M3EFriendFinderAction.SignIn) },
                    modifier = Modifier.testTag(FRIEND_FINDER_SIGN_IN_TEST_TAG)
                ) {
                    Text("Sign in", color = colors.onErrorContainer)
                }
            }
        }
        TextButton(onClick = { onAction(M3EFriendFinderAction.DismissError) }) {
            Text("Dismiss", color = colors.onErrorContainer)
        }
    }
}

@Composable
private fun SignedOutLanding(state: M3EFriendFinderState, onAction: (M3EFriendFinderAction) -> Unit) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Find classmates",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        if (!state.errorMessage.isNullOrBlank()) {
            Text(
                text = state.errorMessage.orEmpty(),
                color = if (state.needsSignIn) colors.error else colors.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Text(
            text = "Join with your registered timetable to see who shares your faculty and slots.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            text = if (state.needsSignIn) {
                // Host supplies the identity-specific copy; the literal keeps legacy callers honest.
                state.signInMessage ?: "Sign in with Amaze ID to use Friend Finder."
            } else {
                "Publishing uses the timetable you registered in VTOP. Nothing is shared until you join."
            },
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
        if (state.needsSignIn) {
            // A real sign-in affordance, not a dead control: publishing is hidden until signed in.
            Text(
                text = "Sign in to continue",
                color = colors.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FRIEND_FINDER_SIGN_IN_TEST_TAG)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.primary)
                    .clickableA11y(
                        onClickLabel = "Sign in",
                        enabled = !state.isBusy,
                        onClick = { onAction(M3EFriendFinderAction.SignIn) }
                    )
                    .padding(14.dp)
            )
        } else {
            Text(
                text = "Publish registered classes",
                color = colors.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.primary)
                    .clickableA11y(
                        onClickLabel = "Publish registered classes",
                        enabled = !state.isBusy,
                        onClick = { onAction(M3EFriendFinderAction.Publish) }
                    )
                    .padding(14.dp)
            )
        }
    }
}

@Composable
private fun HiddenBanner(state: M3EFriendFinderState, onAction: (M3EFriendFinderAction) -> Unit) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "You're hidden",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Nobody can see you, and you can't see anyone either. Your classes are still saved.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            text = "Become visible",
            color = colors.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.primary)
                .clickableA11y(
                    onClickLabel = "Become visible",
                    enabled = !state.isBusy,
                    onClick = { onAction(M3EFriendFinderAction.SetDiscoverable(true)) }
                )
                .padding(14.dp)
        )
    }
}

@Composable
private fun MatchRuleNote() {
    val colors = M3ETheme.colors
    Text(
        text = "Matches need at least two classes in common or identical timetables. Sharing just one class won't show up here.",
        color = colors.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    )
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = detail, color = colors.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun ClassRow(cls: FriendFinderClassUi, onAction: (M3EFriendFinderAction) -> Unit) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .clickableA11y(
                onClickLabel = "Open ${cls.courseCode.ifBlank { cls.classId }}",
                onClick = { onAction(M3EFriendFinderAction.OpenClass(cls.classId)) }
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = listOfNotNull(
                cls.courseCode.takeIf { it.isNotBlank() },
                cls.courseTitle.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { cls.classId },
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = listOfNotNull(
                cls.slot.takeIf { it.isNotBlank() },
                cls.faculty.takeIf { it.isNotBlank() },
                "${cls.people.size} ${if (cls.people.size == 1) "person" else "people"}"
            ).joinToString(" · "),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ClassDetailCard(
    cls: FriendFinderClassUi,
    codeOf: Map<String, String>,
    onAction: (M3EFriendFinderAction) -> Unit
) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = cls.courseTitle.ifBlank { codeOf[cls.classId] ?: cls.classId },
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = listOfNotNull(
                cls.courseCode.takeIf { it.isNotBlank() },
                cls.slot.takeIf { it.isNotBlank() },
                cls.faculty.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = "People in this section (${cls.people.size})",
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        if (cls.people.isEmpty()) {
            Text(
                text = "No people were returned for this section.",
                color = colors.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            cls.people.forEach { person ->
                Text(
                    text = listOfNotNull(
                        person.displayName.takeIf { it.isNotBlank() },
                        person.regNo.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainer)
                        .then(
                            if (person.handle.isNotBlank()) {
                                Modifier.clickableA11y(
                                    onClickLabel = "Open ${person.displayName}",
                                    onClick = { onAction(M3EFriendFinderAction.OpenFriend(person.handle)) }
                                )
                            } else Modifier
                        )
                        .padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Back to sections",
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickableA11y(
                    onClickLabel = "Back to sections",
                    onClick = { onAction(M3EFriendFinderAction.CloseClass) }
                )
                .padding(12.dp)
        )
    }
}

@Composable
private fun FriendRow(
    friend: FriendFinderFriendUi,
    codeOf: Map<String, String>,
    onAction: (M3EFriendFinderAction) -> Unit
) {
    val colors = M3ETheme.colors
    val codes = friend.sharedClassIds.mapNotNull { codeOf[it]?.takeIf { code -> code.isNotBlank() } }
    val summary = when {
        friend.identical -> "Exactly your timetable"
        codes.isNotEmpty() -> codes.joinToString(" · ")
        else -> "${friend.shared} shared ${if (friend.shared == 1) "class" else "classes"}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .clickableA11y(
                onClickLabel = "Open ${friend.displayName}",
                onClick = { onAction(M3EFriendFinderAction.OpenFriend(friend.handle)) }
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = friend.displayName,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (friend.source == "registered") {
                    Text(text = "Verified", color = colors.primary, fontSize = 11.sp)
                }
            }
            Text(
                text = listOfNotNull(friend.regNo.takeIf { it.isNotBlank() }, summary).joinToString(" · "),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (friend.identical) {
            Text(text = "Identical", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Text(
            text = friend.shared.toString(),
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.size(2.dp))
    }
}
