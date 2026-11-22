package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.SocialApiError
import com.amazecc.app.shared.state.SocialClient
import com.amazecc.app.shared.state.SocialClient.FfcsMatchRequest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * D-164: server-backed Friend Finder host. Every rendered value is mapped from a real
 * [SocialClient] response; failures (including sign-in-required) are surfaced as honest text.
 * No cached demo state and no optimistic success — each mutation re-reads server truth.
 */
@Composable
fun M3EFriendFinderHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val scope = rememberCoroutineScope()

    var joined by remember { mutableStateOf(false) }
    var discoverable by remember { mutableStateOf(false) }
    var participants by remember { mutableIntStateOf(0) }
    var classes by remember { mutableStateOf<List<FriendFinderClassUi>>(emptyList()) }
    var friends by remember { mutableStateOf<List<FriendFinderFriendUi>>(emptyList()) }
    var overlaps by remember { mutableStateOf<List<FriendFinderFriendUi>>(emptyList()) }
    var self by remember { mutableStateOf(FriendFinderPersonUi()) }
    var selfClassCount by remember { mutableIntStateOf(0) }
    var socialHandles by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(FriendFinderGrouping.CLASSES) }
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedFriend by remember { mutableStateOf<FriendFinderFriendUi?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsSignIn by remember { mutableStateOf(false) }
    var semesterId by remember { mutableStateOf("") }

    /** One fresh read of POST /api/social/ffcs-match op=get. */
    suspend fun load() {
        isLoading = true
        errorMessage = null
        needsSignIn = false
        val sem = semesterId.ifBlank { selectedSemester.trim().uppercase() }
        try {
            val match = withContext(Dispatchers.Default) {
                SocialClient.ffcsMatchState(FfcsMatchRequest.Get(sem))
            }
            semesterId = match.semesterId.ifBlank { sem }
            joined = match.me.joined
            discoverable = match.me.discoverable
            participants = match.participants
            self = FriendFinderPersonUi(
                handle = match.me.handle,
                name = match.me.name,
                source = match.me.source.orEmpty()
            )
            selfClassCount = match.me.classIds.size
            socialHandles = match.handles

            classes = match.sections.map { section ->
                FriendFinderClassUi(
                    classId = section.classId,
                    courseCode = section.offering?.code.orEmpty(),
                    courseTitle = section.offering?.title.orEmpty(),
                    slot = section.offering?.slot.orEmpty(),
                    faculty = section.offering?.faculty.orEmpty(),
                    people = section.people.map { person ->
                        FriendFinderPersonUi(
                            handle = person.handle,
                            name = person.name,
                            regNo = person.regNo,
                            source = person.source
                        )
                    }
                )
            }
            // Friends and overlaps are the same server truth: the rows and the drill-in sheet
            // must never disagree about a shared-class count.
            val mappedOverlaps = match.overlaps.map { overlap ->
                FriendFinderFriendUi(
                    handle = overlap.handle,
                    name = overlap.name,
                    regNo = overlap.regNo,
                    source = overlap.source,
                    shared = overlap.shared,
                    sharedClassIds = overlap.sharedClassIds,
                    identical = overlap.identical
                )
            }
            overlaps = mappedOverlaps
            friends = mappedOverlaps
        } catch (e: SocialApiError) {
            needsSignIn = e.needsAmazeIdSignIn
            errorMessage = if (e.needsAmazeIdSignIn) {
                "Sign in with Amaze ID to use Friend Finder."
            } else {
                e.message?.takeIf { it.isNotBlank() } ?: "Friend Finder is unavailable (${e.code})."
            }
        } catch (e: Exception) {
            errorMessage = e.message?.takeIf { it.isNotBlank() } ?: "Friend Finder is unavailable."
        }
        isLoading = false
    }

    /** Publish / visibility / leave: real ops only, then re-read so the UI cannot lie. */
    suspend fun run(request: FfcsMatchRequest, actionLabel: String) {
        isBusy = true
        errorMessage = null
        needsSignIn = false
        try {
            withContext(Dispatchers.Default) { SocialClient.ffcsMatchState(request) }
            load()
        } catch (e: SocialApiError) {
            needsSignIn = e.needsAmazeIdSignIn
            errorMessage = if (e.needsAmazeIdSignIn) {
                "Sign in with Amaze ID to $actionLabel."
            } else {
                e.message?.takeIf { it.isNotBlank() } ?: "Could not $actionLabel (${e.code})."
            }
        } catch (e: Exception) {
            errorMessage = e.message?.takeIf { it.isNotBlank() } ?: "Could not $actionLabel."
        }
        isBusy = false
    }

    LaunchedEffect(Unit) { load() }

    // M3EDismissibleSheet's LIFO guard requires its id on AppState.overlayStack; without this
    // every dismissal route (X / swipe / backdrop / Escape / system back) would be a no-op.
    LaunchedEffect(selectedFriend) {
        if (selectedFriend != null) {
            AppState.pushOverlay(FRIEND_FINDER_FRIEND_SHEET_OVERLAY_ID)
        } else {
            AppState.popOverlay(FRIEND_FINDER_FRIEND_SHEET_OVERLAY_ID)
        }
    }
    LaunchedEffect(profileOpen) {
        if (profileOpen) {
            AppState.pushOverlay(FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID)
        } else {
            AppState.popOverlay(FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            AppState.popOverlay(FRIEND_FINDER_FRIEND_SHEET_OVERLAY_ID)
            AppState.popOverlay(FRIEND_FINDER_PROFILE_SHEET_OVERLAY_ID)
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EFriendFinderScreen(
            modifier = modifier,
            state = M3EFriendFinderState(
                joined = joined,
                discoverable = discoverable,
                participants = participants,
                classes = classes,
                friends = friends,
                overlaps = overlaps,
                query = query,
                grouping = grouping,
                selectedClassId = selectedClassId,
                selectedFriend = selectedFriend,
                isProfileOpen = profileOpen,
                isLoading = isLoading,
                errorMessage = errorMessage,
                needsSignIn = needsSignIn,
                isBusy = isBusy,
                self = self,
                selfClassCount = selfClassCount,
                socialHandles = socialHandles
            ),
            onAction = { action ->
                // Same semester the Get used; fall back to the app's selection before first load.
                val opSemester = semesterId.ifBlank { selectedSemester.trim().uppercase() }
                when (action) {
                    M3EFriendFinderAction.Refresh -> scope.launch { load() }
                    M3EFriendFinderAction.Publish -> {
                        scope.launch { run(FfcsMatchRequest.Publish(opSemester), "publish your classes") }
                    }
                    M3EFriendFinderAction.Leave -> {
                        selectedClassId = null
                        selectedFriend = null
                        profileOpen = false
                        scope.launch { run(FfcsMatchRequest.Leave(opSemester), "leave Friend Finder") }
                    }
                    is M3EFriendFinderAction.SetDiscoverable -> {
                        scope.launch {
                            run(
                                FfcsMatchRequest.Visibility(opSemester, action.discoverable),
                                "change your visibility"
                            )
                        }
                    }
                    is M3EFriendFinderAction.SetGrouping -> grouping = action.grouping
                    is M3EFriendFinderAction.SetQuery -> query = action.query
                    is M3EFriendFinderAction.OpenClass -> selectedClassId = action.classId
                    M3EFriendFinderAction.CloseClass -> selectedClassId = null
                    is M3EFriendFinderAction.OpenFriend -> {
                        selectedFriend = friends.firstOrNull { it.handle == action.handle }
                            ?: classes.firstNotNullOfOrNull { cls ->
                                cls.people.firstOrNull { it.handle == action.handle }?.let { person ->
                                    FriendFinderFriendUi(
                                        handle = person.handle,
                                        name = person.name,
                                        regNo = person.regNo,
                                        source = person.source
                                    )
                                }
                            }
                    }
                    M3EFriendFinderAction.CloseFriend -> selectedFriend = null
                    M3EFriendFinderAction.OpenProfile -> profileOpen = true
                    M3EFriendFinderAction.CloseProfile -> profileOpen = false
                    M3EFriendFinderAction.DismissError -> {
                        errorMessage = null
                        needsSignIn = false
                    }
                }
            }
        )
    }
}
