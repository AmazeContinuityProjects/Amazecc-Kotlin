package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.ffcs.ParsedCourse
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AcademicDerivers.resolveCurrentSemester
import com.amazecc.app.shared.state.AcademicDerivers.toAttendanceItem
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.BusyEntry
import com.amazecc.app.shared.state.SocialClient.FfcsMatchRequest
import com.amazecc.app.shared.state.FfcsViewModel
import com.amazecc.app.shared.state.FriendsViewModel
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SocialApiError
import com.amazecc.app.shared.state.SocialClient
import com.amazecc.app.shared.state.SocialVisibility
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.QRCodeGenerator
import com.amazecc.app.shared.utils.buildSimpleHtml
import com.amazecc.app.shared.utils.buildTableHtml
import com.amazecc.app.shared.utils.SocialUtils
import com.amazecc.app.shared.utils.encodeQrPng
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.rememberFileSaver
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val M3E_OD_TRACKER_KEY = "m3e_od_tracker"
private val m3eOdTrackerJson = Json { ignoreUnknownKeys = true }

/**
 * Stateful host wrappers for the converted ACADEMIC & SOCIAL M3E screens.
 *
 * Every host collects the existing [AppState] / ViewModel flows, builds an immutable
 * screen state, wires user intents back to the existing AppState methods, and renders
 * the matching 100% stateless `M3E*Screen`. All side effects (network, storage,
 * navigation, clipboard, URI handling) live here — never in the stateless screens.
 *
 * Both data paths are preserved: online scraper/API calls go through [AmazeClient],
 * while offline data comes from the cached [AppDataStore] / [AppState.domain] flows.
 */
object M3EAcademicLegacyBridge

private fun backTo(screen: Screen) {
    if (!AppState.navigateBack()) AppState.navigateTo(screen)
}

private fun backToHub() {
    if (!AppState.navigateBack()) AppState.navigateTo(Screen.MORE)
}

// ============================================================================
// 1. Course Attendance (Bunk-O-Meter)
// ============================================================================

@Composable
fun M3ECourseAttendanceScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val courseCode by AppState.selectedCourseCode.collectAsState()
    val calendar by AppState.calendar.collectAsState()
    val isBusSubscriber by AppState.isBusSubscriber.collectAsState()
    val customTarget by AppState.customAttendanceTarget.collectAsState()
    val attendanceTarget = remember(isBusSubscriber, customTarget) {
        AppState.effectiveAttendanceTarget(isBusSubscriber)
    }

    var activeTab by remember { mutableStateOf(M3ECourseAttendanceTab.PREDICTOR) }
    var cutoffMode by remember { mutableStateOf(M3ECutoffMode.LID) }
    var whatIfAttend by remember { mutableStateOf(0) }
    var whatIfMiss by remember { mutableStateOf(0) }
    var notes by remember(courseCode) { mutableStateOf(SettingsManager.getCourseNote(courseCode ?: "")) }
    val skippedDates = remember { mutableStateMapOf<Int, Boolean>() }

    val course = remember(domain, courseCode) {
        Projections.currentSemesterAttendance(domain).firstOrNull { it.courseCode == courseCode }
    }
    val total = course?.totalClasses ?: 0
    val attended = course?.attendedClasses ?: 0
    val computedPct = if (total > 0) attended.toDouble() / total * 100.0 else 0.0
    val percentText = course?.attendancePercentage?.replace("%", "")?.toDoubleOrNull()

    // Derive the real future class sessions (same rule as the legacy predictor) and
    // fold in this host's skipped-date set. Reading the map here keeps the chips live
    // when a date is toggled; the key is an immutable snapshot so `remember` recomputes.
    val skippedKeys = skippedDates.filter { it.value }.keys.toSet()
    val futureClassDates = remember(domain, courseCode, cutoffMode, calendar, skippedKeys) {
        M3EGapFutureDates.deriveFutureClassDates(
            months = calendar?.months.orEmpty(),
            domain = domain,
            courseCode = courseCode.orEmpty(),
            courseType = course?.courseType.orEmpty(),
            cutoffMode = cutoffMode,
            today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            skippedKeys = skippedKeys
        )
    }

    val state = M3ECourseAttendanceState(
        courseCode = course?.courseCode ?: courseCode.orEmpty(),
        courseTitle = course?.courseTitle.orEmpty(),
        slotName = course?.slotName.orEmpty(),
        courseType = course?.courseType.orEmpty(),
        attendedClasses = attended,
        totalClasses = total,
        currentPercentage = percentText ?: computedPct,
        targetPercentage = attendanceTarget.toInt(),
        activeTab = activeTab,
        cutoffMode = cutoffMode,
        futureClassDates = futureClassDates,
        whatIfAttend = whatIfAttend,
        whatIfMiss = whatIfMiss,
        logs = course?.logs?.map { log ->
            val normalized = when (log.status.trim().lowercase()) {
                "present", "p" -> "Present"
                "on duty", "od", "onduty" -> "On Duty"
                else -> "Absent"
            }
            M3EAttendanceLogEntry(log.date, normalized)
        }.orEmpty(),
        notes = notes
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseAttendanceScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECourseAttendanceAction.SelectTab -> activeTab = action.tab
                    is M3ECourseAttendanceAction.SelectCutoffMode -> cutoffMode = action.mode
                    is M3ECourseAttendanceAction.ToggleSkip -> {
                        skippedDates[action.key] = !(skippedDates[action.key] ?: false)
                    }
                    is M3ECourseAttendanceAction.SetWhatIfAttend -> whatIfAttend = action.count
                    is M3ECourseAttendanceAction.SetWhatIfMiss -> whatIfMiss = action.count
                    is M3ECourseAttendanceAction.UpdateNotes -> {
                        notes = action.text
                        SettingsManager.saveCourseNote(state.courseCode, action.text)
                    }
                    is M3ECourseAttendanceAction.Refresh -> AppState.loadAllData()
                    is M3ECourseAttendanceAction.Back -> backTo(Screen.ACADEMICS)
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 2. Course Dashboard
// ============================================================================

@Composable
fun M3ECourseDashboardScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val pastSynced by AppState.pastSemestersSynced.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedSemester by remember { mutableStateOf("All") }

    val semesters = remember(domain, selectedSemester) {
        val groups = domain.academics.semesters.map { (semId, sem) ->
            val courses = sem.courses.values.map { c ->
                val total = c.attendance?.total ?: 0
                val attended = c.attendance?.attended ?: 0
                val pct = if (total > 0) attended.toDouble() / total * 100.0 else null
                // Web parity: the hub shows "Theory" / "Lab" / "Embedded" tokens.
                val badge = when {
                    c.type.contains("Project", ignoreCase = true) -> "PJ"
                    c.type.contains("Online", ignoreCase = true) -> "OC"
                    c.type.contains("Embedded", ignoreCase = true) -> "Embedded"
                    c.type.contains("Lab", ignoreCase = true) -> "Lab"
                    else -> "Theory"
                }
                M3EDashboardCourse(
                    courseCode = c.code,
                    title = c.title,
                    typeBadge = badge,
                    attendancePct = pct,
                    faculty = c.faculty.orEmpty(),
                    semesterId = semId
                )
            }
            M3EDashboardSemester(
                id = semId,
                label = semesterMap[semId] ?: AppState.deriveSemesterName(semId),
                courses = courses
            )
        }
        if (selectedSemester == "All") groups else groups.filter { it.id == selectedSemester }
    }

    val state = M3ECourseDashboardState(
        semesters = semesters,
        availableSemesterIds = listOf("All") + domain.academics.semesters.keys.toList(),
        semesterLabels = semesterMap,
        selectedSemesterId = selectedSemester,
        pastSemestersSynced = pastSynced,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseDashboardScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECourseDashboardAction.SelectSemester -> selectedSemester = action.id
                    is M3ECourseDashboardAction.OpenCourse -> {
                        AppState.openCourseDetail(action.courseCode, action.semesterId)
                    }
                    is M3ECourseDashboardAction.RefreshPastSemesters -> AppState.refreshPastSemesters()
                    is M3ECourseDashboardAction.Refresh -> AppState.refreshAllAcademic()
                    is M3ECourseDashboardAction.Back -> backTo(Screen.ACADEMICS)
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 3. OD Tracker
// ============================================================================

@Composable
fun M3EODTrackerScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    // VTOP OD records flow from the OD-details endpoint through AppState (SyncModule.OD_DETAILS).
    // On-duty days also survive in the synced attendance logs, so derive those locally (pure, no
    // extra fetch) and UNION them with the VTOP payload - a failed/empty OD call must not hide
    // real On Duty days that attendance already knows about.
    val odDetails by AppState.odDetails.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var activeTab by remember { mutableStateOf(M3EODTab.OVERVIEW) }
    var refreshTick by remember { mutableStateOf(0) }
    // Wasted/recovered annotations persist across restarts (legacy did this through
    // SettingsManager.getODTrackerState). They are keyed by "date|courseCode".
    val tracker = remember {
        mutableStateMapOf<String, M3EODStatus>().apply {
            runCatching {
                val raw = SettingsManager.getString(M3E_OD_TRACKER_KEY, "{}")
                putAll(m3eOdTrackerJson.decodeFromString<Map<String, M3EODStatus>>(raw))
            }
        }
    }

    // Entry reads what the central Sync All sweep (SyncModule.OD_DETAILS) cached; only an
    // explicit Refresh triggers a network fetch.
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0 && selectedSemester.isNotBlank()) {
            AppState.refreshOdDetails(selectedSemester)
        }
    }

    // Pure/local derivation from the RAW synced attendance rows — no EmbeddedLinkMerge. D-125:
    // the merge folds an embedded theory/lab pair into one row, drops a duplicate OD log and
    // joins the slots theory-first ("C1+L31"), turning a 2h lab OD into a 1h theory OD. The web
    // counts each attendanceRes.attendance row with its own slotName, so this host must too.
    val derivedOdRows = remember(domain) {
        deriveOdRowsFromSnapshot(domain)
    }
    val days = remember(odDetails, tracker, derivedOdRows) {
        mapOdRecordsToDays(odDetails, tracker, derivedOdRows)
    }

    val state = M3EODTrackerState(activeTab = activeTab, days = days, isLoading = isLoading)

    AmazeM3ETheme(isDark = isDark) {
        M3EODTrackerScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EODTrackerAction.SelectTab -> activeTab = action.tab
                    is M3EODTrackerAction.CycleStatus -> {
                        val key = "${action.date}|${action.courseCode}"
                        tracker[key] = when (tracker[key]) {
                            M3EODStatus.WASTED -> M3EODStatus.RECOVERED
                            M3EODStatus.RECOVERED -> M3EODStatus.NONE
                            else -> M3EODStatus.WASTED
                        }
                        SettingsManager.setString(
                            M3E_OD_TRACKER_KEY,
                            m3eOdTrackerJson.encodeToString(tracker.toMap())
                        )
                    }
                    is M3EODTrackerAction.Refresh -> refreshTick++
                    is M3EODTrackerAction.Back -> backTo(Screen.ACADEMICS)
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 5. QBank catalog
// ============================================================================

@Composable
fun M3EQBankScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var courses by remember { mutableStateOf<List<M3EQBankCourse>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }

    val stats = remember(refreshTick) {
        val all = SettingsManager.getQBankStats().values
        val attempts = all.sumOf { it.attempts }
        val correct = all.sumOf { it.correct }
        if (attempts == 0) null
        else M3EQBankStats(attempts, correct, correct.toDouble() / attempts * 100.0)
    }

    LaunchedEffect(refreshTick) {
        isLoading = true
        errorMessage = null
        try {
            val res = AmazeClient.getQBankCourses()
            if (res.success) {
                courses = res.courses
                    .distinctBy { it.courseCode }
                    .map { M3EQBankCourse(it.courseCode, it.courseTitle) }
            } else {
                errorMessage = res.message ?: res.error ?: "Unable to load question bank"
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unable to load question bank"
        }
        isLoading = false
    }

    val filtered = remember(courses, searchQuery) {
        courses.filter {
            it.courseCode.contains(searchQuery, ignoreCase = true) ||
                it.courseTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EQBankScreen(
            state = M3EQBankState(
                courses = courses,
                filteredCourses = filtered,
                searchQuery = searchQuery,
                stats = stats,
                errorMessage = errorMessage,
                isLoading = isLoading
            ),
            onAction = { action ->
                when (action) {
                    is M3EQBankAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EQBankAction.OpenCourse -> {
                        // Hand the course to the existing QBank workspace mechanism.
                        AppState.openQBankCourse(action.courseCode)
                    }
                    is M3EQBankAction.Refresh -> refreshTick++
                    is M3EQBankAction.Back -> backToHub()
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 6. Social
// ============================================================================

@Composable
fun M3ESocialScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val friendsFlow by FriendsViewModel.friends.collectAsState()
    val groupsFlow by FriendsViewModel.groups.collectAsState()
    val academic by AppState.academic.collectAsState()
    val identity by UserStore.identity.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val saveFile = rememberFileSaver()
    var qrExportNotice by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf(M3ESocialTab.SHARE) }
    var addFriendOpen by remember { mutableStateOf(false) }
    var createGroupOpen by remember { mutableStateOf(false) }
    var addFriendCode by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }

    // Server-backed Social state (D-003/D-006/D-064). Every value here comes from a real
    // SocialClient response; failures are surfaced as text, never replaced with demo data.
    var handle by remember { mutableStateOf("") }
    var identityLabel by remember { mutableStateOf("") }
    var serverSemester by remember { mutableStateOf("") }
    var serverError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var needsSignIn by remember { mutableStateOf(false) }
    var isServerLoading by remember { mutableStateOf(false) }
    var peers by remember { mutableStateOf<List<M3ESocialPeerUi>>(emptyList()) }
    var serverClasses by remember { mutableStateOf<List<M3ESocialClassUi>>(emptyList()) }
    var overlaps by remember { mutableStateOf<List<M3ESocialOverlapUi>>(emptyList()) }
    var serverFreeSlots by remember { mutableStateOf<List<M3ESocialFreeSlotUi>>(emptyList()) }
    var freeNowHandles by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentSlotLabel by remember { mutableStateOf("") }
    var joined by remember { mutableStateOf(false) }
    var discoverable by remember { mutableStateOf(false) }
    var addHandleOpen by remember { mutableStateOf(false) }
    var addHandleInput by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    var importInput by remember { mutableStateOf("") }
    var openPeerHandle by remember { mutableStateOf<String?>(null) }
    var peerTimetable by remember { mutableStateOf<M3ESocialTimetableUi?>(null) }

    /**
     * One fresh read of server truth: identity + grants, peer busy maps (common-free grid),
     * and the caller's Classes Together state. Sign-in failures stay inline with an explicit
     * action instead of silently redirecting on mount.
     */
    suspend fun loadServer() {
        isServerLoading = true
        serverError = null
        needsSignIn = false
        try {
            val sync = SocialClient.identitySync()
            handle = sync.identity.handle
            identityLabel = sync.identity.displayName
            serverSemester = sync.identity.semesterId.takeIf { it.isNotBlank() }
                ?: selectedSemester.trim().uppercase()

            peers = sync.peers.filter { !it.isSelf && it.handle.isNotBlank() }.map { p ->
                val grant = sync.grantSecrets.firstOrNull { it.peerHandle == p.handle }
                M3ESocialPeerUi(
                    handle = p.handle,
                    name = p.name.orEmpty(),
                    visibility = p.visibility.name.lowercase(),
                    shared = p.shared,
                    lastPublishedAt = p.lastPublishedAt,
                    semesterId = p.semesterId.orEmpty(),
                    grantId = grant?.grantId.orEmpty(),
                    secret = grant?.secret.orEmpty()
                )
            }

            val peerMaps = mutableListOf<Pair<String, Map<String, BusyEntry>>>()
            for (peer in peers) {
                if (peer.grantId.isBlank() || peer.secret.isBlank()) continue
                try {
                    val timetable = SocialClient.readPeerTimetable(peer.secret, peer.handle, serverSemester)
                    peerMaps += peer.handle to timetable.busyMap
                    if (peer.handle == openPeerHandle) {
                        peerTimetable = M3ESocialTimetableUi(
                            handle = peer.handle,
                            title = timetable.identity.displayName?.takeIf { it.isNotBlank() }
                                ?: peer.name.ifBlank { peer.handle },
                            semesterLabel = timetable.identity.semesterLabel,
                            courses = timetable.courses.map { c ->
                                listOfNotNull(
                                    c.code.takeIf { it.isNotBlank() },
                                    c.title.takeIf { it.isNotBlank() },
                                    c.ltpjc.takeIf { it.isNotBlank() } ?: c.classId.takeIf { it.isNotBlank() },
                                    c.venue.takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                            },
                            stale = timetable.stale,
                            coarse = timetable.visibility == SocialVisibility.COARSE
                        )
                    }
                } catch (_: SocialApiError) {
                    // A revoked or expired grant simply drops out of the grid.
                }
            }
            serverFreeSlots = M3ESocialProto.commonFreeSlots(sync.busyMap, peerMaps)

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val slotKey = M3ESocialProto.currentSlotKey(now.dayOfWeek.name.take(3), now.hour * 60 + now.minute)
            currentSlotLabel = slotKey.orEmpty()
            freeNowHandles = M3ESocialProto.freeNowHandles(sync.busyMap, peerMaps, slotKey)

            val match = SocialClient.ffcsMatchState(FfcsMatchRequest.Get(serverSemester))
            joined = match.me.joined
            discoverable = match.me.discoverable
            serverClasses = match.sections.map { section ->
                M3ESocialClassUi(
                    classId = section.classId,
                    courseCode = section.offering?.code.orEmpty(),
                    courseTitle = section.offering?.title.orEmpty(),
                    slot = section.offering?.slot.orEmpty(),
                    faculty = section.offering?.faculty.orEmpty(),
                    people = section.people.map { it.name.ifBlank { it.handle } }
                )
            }
            overlaps = match.overlaps.map { overlap ->
                M3ESocialOverlapUi(
                    handle = overlap.handle,
                    name = overlap.name,
                    shared = overlap.shared,
                    identical = overlap.identical,
                    sharedClassIds = overlap.sharedClassIds
                )
            }
        } catch (e: SocialApiError) {
            needsSignIn = e.needsAmazeIdSignIn
            serverError = if (needsSignIn) {
                "Sign in with Amaze ID to use Social."
            } else {
                e.message?.takeIf { it.isNotBlank() } ?: "Social is unavailable (${e.code})."
            }
        } catch (e: Exception) {
            serverError = e.message?.takeIf { it.isNotBlank() } ?: "Social is unavailable."
        }
        isServerLoading = false
    }

    LaunchedEffect(Unit) { loadServer() }

    val userAttendance = remember(academic) {
        resolveCurrentSemester(academic)?.courses?.values?.map { it.toAttendanceItem() }.orEmpty()
    }
    val shareCode = remember(userAttendance, identity) {
        if (userAttendance.isEmpty()) "" else SocialUtils.exportScheduleCode(
            userAttendance,
            identity.displayName.ifBlank { identity.regNo.orEmpty() },
            identity.regNo.orEmpty()
        )
    }

    val friends = remember(friendsFlow) {
        friendsFlow.map {
            M3ESocialFriend(it.name, it.regNumber, it.nickname, it.classSlots.size)
        }
    }
    val groups = remember(groupsFlow, friends) {
        groupsFlow.map { group ->
            val names = group.memberRegNumbers.map { reg ->
                friends.firstOrNull { it.regNumber == reg }?.name ?: reg
            }
            M3ESocialGroup(group.id, group.name, names)
        }
    }

    // SocialUtils does the CPU-bound scan on Dispatchers.Default internally; produceState
    // keeps the result alive across recompositions and reruns when either input changes.
    val commonFreeSlots by produceState(initialValue = emptyList<String>(), userAttendance, friendsFlow) {
        value = SocialUtils.getCommonFreeSlots(userAttendance, friendsFlow)
    }

    AmazeM3ETheme(isDark = isDark) {
        M3ESocialScreen(
            state = M3ESocialState(
                activeTab = activeTab,
                shareCode = shareCode,
                handle = handle,
                identityLabel = identityLabel,
                serverError = serverError,
                needsSignIn = needsSignIn,
                isServerLoading = isServerLoading,
                peers = peers,
                serverClasses = serverClasses,
                overlaps = overlaps,
                serverFreeSlots = serverFreeSlots,
                freeNowHandles = freeNowHandles,
                currentSlotLabel = currentSlotLabel,
                joined = joined,
                discoverable = discoverable,
                openPeerHandle = openPeerHandle,
                peerTimetable = peerTimetable,
                friends = friends,
                groups = groups,
                commonFreeSlots = commonFreeSlots,
                isAddFriendDialogOpen = addFriendOpen,
                isCreateGroupDialogOpen = createGroupOpen,
                addFriendCode = addFriendCode,
                newGroupName = newGroupName,
                isAddHandleDialogOpen = addHandleOpen,
                addHandleInput = addHandleInput,
                isImportDialogOpen = importOpen,
                importCodeInput = importInput,
                formError = formError,
                qrExportNotice = qrExportNotice
            ),
            onAction = { action ->
                when (action) {
                    is M3ESocialAction.SelectTab -> activeTab = action.tab
                    is M3ESocialAction.UpdateAddFriendCode -> addFriendCode = action.code
                    is M3ESocialAction.UpdateNewGroupName -> newGroupName = action.name
                    is M3ESocialAction.OpenAddFriendDialog -> { addFriendOpen = true; formError = null }
                    is M3ESocialAction.CloseAddFriendDialog -> { addFriendOpen = false; formError = null }
                    is M3ESocialAction.OpenCreateGroupDialog -> createGroupOpen = true
                    is M3ESocialAction.CloseCreateGroupDialog -> createGroupOpen = false
                    is M3ESocialAction.AddFriend -> {
                        if (FriendsViewModel.addFriendFromCode(addFriendCode)) {
                            addFriendCode = ""
                            addFriendOpen = false
                            formError = null
                        } else {
                            formError = "Could not read that schedule code."
                        }
                    }
                    is M3ESocialAction.CreateGroup -> {
                        if (newGroupName.isNotBlank()) {
                            FriendsViewModel.createGroup(newGroupName.trim(), emptyList())
                            newGroupName = ""
                            createGroupOpen = false
                        }
                    }
                    is M3ESocialAction.RemoveFriend -> FriendsViewModel.removeFriend(action.regNumber)
                    is M3ESocialAction.DeleteGroup -> FriendsViewModel.deleteGroup(action.groupId)
                    is M3ESocialAction.CopyShareCode -> clipboard.setText(AnnotatedString(shareCode))
                    is M3ESocialAction.Refresh -> AppState.loadAllData()
                    is M3ESocialAction.RefreshServer -> scope.launch { loadServer() }
                    is M3ESocialAction.Back -> backToHub()

                    is M3ESocialAction.SignIn -> SignInReturn.prepare(Screen.SOCIAL)
                    is M3ESocialAction.CopyHandle ->
                        if (handle.isNotBlank()) clipboard.setText(AnnotatedString(handle)) else Unit
                    is M3ESocialAction.CopyInviteLink ->
                        if (handle.isNotBlank()) clipboard.setText(AnnotatedString(M3ESocialProto.inviteLink(handle))) else Unit
                    is M3ESocialAction.SaveQr -> {
                        // D-145: export the same real invite link the on-screen QR encodes. Never
                        // claim success unless generation and the device save both actually work.
                        if (handle.isBlank()) {
                            qrExportNotice = "No handle yet — sync Social to claim one before saving a QR."
                        } else {
                            val link = M3ESocialProto.inviteLink(handle)
                            val matrix = runCatching { QRCodeGenerator.generate(link) }.getOrNull()
                            if (matrix == null) {
                                qrExportNotice = "Could not generate the QR image for this handle."
                            } else {
                                val bytes = runCatching { encodeQrPng(matrix) }.getOrNull()
                                if (bytes == null) {
                                    qrExportNotice = "Could not render the QR image."
                                } else {
                                    scope.launch {
                                        qrExportNotice = if (saveFile("amazecc-qr.png", bytes)) {
                                            "QR saved as amazecc-qr.png."
                                        } else {
                                            "Could not save the QR image on this device."
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is M3ESocialAction.OpenAddHandleDialog -> { addHandleOpen = true; formError = null }
                    is M3ESocialAction.CloseAddHandleDialog -> { addHandleOpen = false; formError = null }
                    is M3ESocialAction.UpdateAddHandleInput -> { addHandleInput = action.input; formError = null }
                    is M3ESocialAction.AddHandle -> {
                        val parsed = M3ESocialProto.handleFromInvite(addHandleInput)
                        when {
                            !M3ESocialProto.isValidHandle(parsed) ->
                                formError = "That does not look like a handle. Expected AMZ-XXXX-XXXX."
                            parsed.equals(handle, ignoreCase = true) ->
                                formError = "That is your own handle."
                            else -> {
                                isServerLoading = true
                                formError = null
                                scope.launch {
                                    try {
                                        SocialClient.lookupPerson(parsed)
                                        SocialClient.claimPair(parsed, SocialVisibility.FULL)
                                        addHandleInput = ""
                                        addHandleOpen = false
                                        loadServer()
                                    } catch (e: SocialApiError) {
                                        needsSignIn = e.needsAmazeIdSignIn
                                        formError = when {
                                            needsSignIn -> "Sign in with Amaze ID to pair."
                                            e.code == "handle_not_found" -> "No student has that handle."
                                            else -> e.message?.takeIf { it.isNotBlank() } ?: "Could not pair (${e.code})."
                                        }
                                        isServerLoading = false
                                    } catch (e: Exception) {
                                        formError = e.message ?: "Could not pair with that handle."
                                        isServerLoading = false
                                    }
                                }
                            }
                        }
                    }

                    is M3ESocialAction.OpenImportDialog -> { importOpen = true; formError = null }
                    is M3ESocialAction.CloseImportDialog -> { importOpen = false; formError = null }
                    is M3ESocialAction.UpdateImportCode -> { importInput = action.code; formError = null }
                    is M3ESocialAction.ImportCode -> {
                        if (FriendsViewModel.addFriendFromCode(importInput.trim())) {
                            importInput = ""
                            importOpen = false
                            formError = null
                        } else {
                            formError = "Could not read that code. Use a v5/v6 code or an amazecc://share link."
                        }
                    }

                    is M3ESocialAction.PublishClasses,
                    is M3ESocialAction.LeaveClasses,
                    is M3ESocialAction.SetDiscoverable -> {
                        val sem = serverSemester.ifBlank { selectedSemester.trim().uppercase() }
                        val request = when (action) {
                            is M3ESocialAction.PublishClasses -> FfcsMatchRequest.Publish(sem)
                            is M3ESocialAction.LeaveClasses -> FfcsMatchRequest.Leave(sem)
                            is M3ESocialAction.SetDiscoverable -> FfcsMatchRequest.Visibility(sem, action.discoverable)
                            else -> FfcsMatchRequest.Get(sem)
                        }
                        scope.launch {
                            try {
                                SocialClient.ffcsMatchState(request)
                                loadServer()
                            } catch (e: SocialApiError) {
                                needsSignIn = e.needsAmazeIdSignIn
                                serverError = if (needsSignIn) "Sign in with Amaze ID to manage Classes Together."
                                else e.message?.takeIf { it.isNotBlank() } ?: "Could not update Classes Together (${e.code})."
                                isServerLoading = false
                            } catch (e: Exception) {
                                serverError = e.message ?: "Could not update Classes Together."
                                isServerLoading = false
                            }
                        }
                    }

                    is M3ESocialAction.RevokeGrant -> {
                        scope.launch {
                            try {
                                SocialClient.revokeGrant(action.grantId, action.secret)
                                loadServer()
                            } catch (e: Exception) {
                                serverError = e.message ?: "Could not revoke that pairing."
                                isServerLoading = false
                            }
                        }
                    }
                    is M3ESocialAction.SetGrantVisibility -> {
                        val visibility = if (action.visibility.equals("full", ignoreCase = true)) {
                            SocialVisibility.FULL
                        } else {
                            SocialVisibility.COARSE
                        }
                        scope.launch {
                            try {
                                SocialClient.setGrantVisibility(action.grantId, visibility)
                                loadServer()
                            } catch (e: Exception) {
                                serverError = e.message ?: "Could not change that pairing's visibility."
                                isServerLoading = false
                            }
                        }
                    }
                    is M3ESocialAction.OpenPeer -> {
                        openPeerHandle = action.handle
                        peerTimetable = null
                        val peer = peers.firstOrNull { it.handle == action.handle }
                        if (peer == null || peer.secret.isBlank() || peer.grantId.isBlank()) {
                            serverError = "No active grant for that peer."
                        } else {
                            scope.launch {
                                try {
                                    val timetable = SocialClient.readPeerTimetable(peer.secret, peer.handle, serverSemester)
                                    peerTimetable = M3ESocialTimetableUi(
                                        handle = peer.handle,
                                        title = timetable.identity.displayName?.takeIf { it.isNotBlank() }
                                            ?: peer.name.ifBlank { peer.handle },
                                        semesterLabel = timetable.identity.semesterLabel,
                                        courses = timetable.courses.map { c ->
                                            listOfNotNull(
                                                c.code.takeIf { it.isNotBlank() },
                                                c.title.takeIf { it.isNotBlank() },
                                                c.ltpjc.takeIf { it.isNotBlank() } ?: c.classId.takeIf { it.isNotBlank() },
                                                c.venue.takeIf { it.isNotBlank() }
                                            ).joinToString(" · ")
                                        },
                                        stale = timetable.stale,
                                        coarse = timetable.visibility == SocialVisibility.COARSE
                                    )
                                } catch (e: Exception) {
                                    serverError = e.message ?: "Could not read that timetable."
                                }
                            }
                        }
                    }
                    is M3ESocialAction.ClosePeer -> {
                        openPeerHandle = null
                        peerTimetable = null
                    }
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 7. FFCS Planner
// ============================================================================

@Composable
fun M3EFfcsPlannerScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val isLoading by FfcsViewModel.isLoading.collectAsState()
    val offerings by FfcsViewModel.courseOfferings.collectAsState()
    val selectedCodes by FfcsViewModel.selectedCodes.collectAsState()
    val generated by FfcsViewModel.generatedTimetables.collectAsState()
    val isGenerating by FfcsViewModel.isGenerating.collectAsState()
    val error by FfcsViewModel.errorMsg.collectAsState()
    val generateOptions by FfcsViewModel.generateOptions.collectAsState()

    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    var activeTab by remember { mutableStateOf(M3EFfcsTab.COURSES) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedResultId by remember { mutableStateOf<String?>(null) }
    var resultsSearchQuery by remember { mutableStateOf("") }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (offerings.isEmpty()) {
            // Prefer the real domain academic snapshot (the "modern bridge" data source);
            // fall back to the static CSV only when nothing is synced yet.
            val domainCourses = domain.academics.semesters[selectedSemId]?.courses?.values
                ?.mapNotNull(::domainCourseToParsed)?.takeIf { it.isNotEmpty() }
            if (domainCourses != null) FfcsViewModel.initFromCourses(domainCourses)
            else FfcsViewModel.initFromCsv()
        }
    }

    val courses = remember(offerings, selectedCodes) {
        offerings.map { (code, offs) ->
            M3EFfcsCourse(
                code = code,
                title = offs.firstOrNull()?.title.orEmpty(),
                credits = offs.firstOrNull()?.credits.orEmpty(),
                offeringCount = offs.size,
                isSelected = code in selectedCodes
            )
        }
    }
    val filtered = remember(courses, searchQuery) {
        courses.filter {
            it.code.contains(searchQuery, ignoreCase = true) ||
                it.title.contains(searchQuery, ignoreCase = true)
        }
    }
    val results = remember(generated, resultsSearchQuery) {
        generated
            .map { timetable ->
                M3EFfcsResult(
                    id = timetable.id,
                    name = timetable.name,
                    summary = "${timetable.metrics.halfDays} half-days · ${timetable.metrics.gaps} gaps · ${timetable.courses.size} courses"
                )
            }
            // TimetableState has no summary; search the mapped variants that the UI actually shows.
            .filter {
                resultsSearchQuery.isBlank() ||
                    it.name.contains(resultsSearchQuery, ignoreCase = true) ||
                    it.summary.contains(resultsSearchQuery, ignoreCase = true)
            }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EFfcsPlannerScreen(
            state = M3EFfcsPlannerState(
                activeTab = activeTab,
                courses = courses,
                filteredCourses = filtered,
                searchQuery = searchQuery,
                selectedCount = selectedCodes.size,
                results = results,
                selectedResultId = selectedResultId,
                isGenerating = isGenerating,
                errorMessage = error,
                isLoading = isLoading,
                closeGaps = generateOptions.closeGaps,
                lateStart = generateOptions.lateStart,
                earlyFinish = generateOptions.earlyFinish,
                resultsSearchQuery = resultsSearchQuery,
                exportNotice = exportNotice
            ),
            onAction = { action ->
                when (action) {
                    is M3EFfcsPlannerAction.SelectTab -> activeTab = action.tab
                    is M3EFfcsPlannerAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EFfcsPlannerAction.ToggleCourse -> FfcsViewModel.toggleCourse(action.code)
                    is M3EFfcsPlannerAction.Generate -> {
                        activeTab = M3EFfcsTab.RESULTS
                        FfcsViewModel.generate()
                    }
                    is M3EFfcsPlannerAction.SelectResult -> selectedResultId = action.id
                    is M3EFfcsPlannerAction.SetCloseGaps -> FfcsViewModel.setCloseGaps(action.enabled)
                    is M3EFfcsPlannerAction.SetLateStart -> FfcsViewModel.setLateStart(action.enabled)
                    is M3EFfcsPlannerAction.SetEarlyFinish -> FfcsViewModel.setEarlyFinish(action.enabled)
                    is M3EFfcsPlannerAction.SetResultsSearchQuery -> resultsSearchQuery = action.query
                    is M3EFfcsPlannerAction.ExportIcs -> {
                        val ics = FfcsViewModel.exportIcs()
                        if (ics.isNullOrBlank()) {
                            exportNotice = "No clash-free timetable is available to export yet."
                        } else {
                            scope.launch {
                                exportNotice = if (saveFile("amazecc-ffcs.ics", ics.toByteArray())) {
                                    "FFCS timetable exported as amazecc-ffcs.ics."
                                } else {
                                    "Could not save the FFCS export."
                                }
                            }
                        }
                    }
                    is M3EFfcsPlannerAction.ExportHtmlDossier -> {
                        val headers = listOf("Course", "Title", "Credits", "Type", "Slots", "Faculty", "Venue")
                        // Prefer the user-selected generated variant; fall back to the first one.
                        val timetable = generated.firstOrNull { it.id == selectedResultId }
                            ?: generated.firstOrNull()
                        val rows = timetable?.courses.orEmpty().map { course ->
                            listOf(
                                course.code,
                                course.title,
                                course.credits,
                                course.type,
                                course.slots.joinToString(", "),
                                course.faculty,
                                course.venue
                            )
                        }
                        val html = if (timetable != null && rows.isNotEmpty()) {
                            buildTableHtml(timetable.name, headers, rows)
                        } else {
                            // No generated timetable: fall back to the real selected-course strings.
                            val selected = courses.filter { it.isSelected }
                            if (selected.isEmpty()) {
                                exportNotice = "Select courses or generate a timetable before exporting a dossier."
                                null
                            } else {
                                buildSimpleHtml(
                                    "FFCS Dossier (${selected.size} selected course${if (selected.size == 1) "" else "s"})",
                                    selected.map { "${it.code} · ${it.title} · ${it.credits} credits" }
                                )
                            }
                        }
                        if (html != null) {
                            scope.launch {
                                val fileName = "amazecc-ffcs-dossier.html"
                                exportNotice = if (saveFile(fileName, html.toByteArray())) {
                                    "FFCS dossier exported as $fileName."
                                } else {
                                    "Could not save the FFCS dossier."
                                }
                            }
                        }
                    }
                    is M3EFfcsPlannerAction.Refresh -> {
                        val domainCourses = domain.academics.semesters[selectedSemId]?.courses?.values
                            ?.mapNotNull(::domainCourseToParsed)?.takeIf { it.isNotEmpty() }
                        if (domainCourses != null) FfcsViewModel.initFromCourses(domainCourses)
                        else FfcsViewModel.initFromCsv()
                    }
                    is M3EFfcsPlannerAction.Back -> backToHub()
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Maps a domain academic course to the FFCS catalog entry shape the planner consumes.
 * Courses without a parseable slot are dropped — the planner works on slot strings.
 */
private fun domainCourseToParsed(c: com.amazecc.app.shared.domain.Course): ParsedCourse? {
    val slot = c.slots.joinToString("+").trim().takeIf { it.isNotBlank() } ?: return null
    return ParsedCourse(
        code = c.code,
        title = c.title,
        type = c.type.ifBlank { "Theory" },
        credits = c.credits ?: "3",
        room = c.venue.orEmpty(),
        slot = slot,
        faculty = c.faculty.orEmpty()
    )
}

// ============================================================================
// 8. Club Detail
// ============================================================================

@Composable
fun M3EClubDetailScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val clubsRes by AppState.clubs.collectAsState()
    val clubId by AppState.selectedClubId.collectAsState()
    val registeredRes by AppState.registeredEvents.collectAsState()
    val registrationRes by AppState.eventRegistration.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    var isRegistrationStatusVisible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val club = remember(clubsRes, clubId) {
        clubsRes?.clubs?.firstOrNull { it.id == clubId }
    }
    // /api/clubs/details carries event_id and recruitment_link on each club row.
    val eventId = club?.eventId?.takeIf { it.isNotBlank() }
    // Enrollment is derived from real registrations, never from a local toggle.
    val isEnrolled = remember(registeredRes, eventId) {
        eventId != null && registeredRes?.events?.any { it.eid == eventId } == true
    }
    val applyUrl = club?.recruitmentLink?.takeIf { it.isNotBlank() }
        ?: club?.website?.takeIf { it.isNotBlank() }
    val registrationStatus = remember(registrationRes, isRegistrationStatusVisible) {
        val r = registrationRes
        if (isRegistrationStatusVisible && r != null) {
            M3EEventRegistrationUi(status = r.status, message = r.message, paymentUrl = r.url)
        } else {
            null
        }
    }

    val state = M3EClubDetailState(
        clubId = club?.id.orEmpty(),
        name = club?.name.orEmpty(),
        description = club?.description.orEmpty(),
        website = club?.website,
        instagram = club?.instagram,
        whatsapp = club?.whatsapp,
        isEnrolled = isEnrolled,
        isLoading = isLoading,
        eventId = eventId,
        applyUrl = applyUrl,
        registrationStatus = registrationStatus,
        notice = notice
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EClubDetailScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EClubDetailAction.RegisterEvent -> {
                        if (eventId != null) {
                            isRegistrationStatusVisible = true
                            AppState.registerForEvent(eventId)
                        } else {
                            notice = "This club has no linked event. Apply on the club's own site."
                        }
                    }
                    is M3EClubDetailAction.OpenLink -> openExternalUrl(action.url)
                    is M3EClubDetailAction.DismissStatus -> isRegistrationStatusVisible = false
                    is M3EClubDetailAction.DismissNotice -> notice = null
                    is M3EClubDetailAction.Refresh -> AppState.syncEventsAndClubs()
                    is M3EClubDetailAction.Back -> backTo(Screen.CLUB_HUB)
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 9. Feedback Status
// ============================================================================

@Composable
fun M3EFeedbackStatusScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    // Participate in the app's state/sync path: the feedback payload now arrives via AppState,
    // sourced from POST /api/ffcs with {semesterId, section: "feedback"} (legacy alias
    // /api/feedback-status is only a fallback in the data layer).
    val feedbackRes by AppState.feedbackStatus.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val selectedAppSemester by AppState.selectedSemester.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    val initialSemesters = remember(semesterMap) {
        AppDataStore.academic.value.semesters.keys.map { id ->
            M3EFeedbackSemester(id, semesterMap[id] ?: AppState.deriveSemesterName(id))
        }
    }
    var semesters by remember { mutableStateOf(initialSemesters) }
    var selectedSemester by remember {
        mutableStateOf(
            initialSemesters.firstOrNull { it.value == selectedAppSemester }?.value
                ?: initialSemesters.firstOrNull()?.value
        )
    }

    LaunchedEffect(feedbackRes) {
        val fromApi = feedbackRes?.semesters.orEmpty().map { M3EFeedbackSemester(it.value, it.text) }
        if (fromApi.isNotEmpty() && fromApi.map { it.value } != semesters.map { it.value }) {
            semesters = fromApi
        }
        if (selectedSemester == null) {
            selectedSemester = fromApi.firstOrNull { it.value == selectedAppSemester }?.value
                ?: fromApi.firstOrNull()?.value
                ?: selectedAppSemester.takeIf { it.isNotBlank() }
        }
    }

    // Feedback status is synced centrally (SyncModule.FEEDBACK_STATUS). Page entry only reads
    // the cached flow; the Refresh action below is the sole user-initiated fetch.

    val rows = remember(feedbackRes) {
        feedbackRes?.feedbackTable.orEmpty().map {
            M3EFeedbackRow(it.feedbackType.orEmpty(), it.midSemester.orEmpty(), it.teeSemester.orEmpty())
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EFeedbackStatusScreen(
            state = M3EFeedbackStatusState(
                semesters = semesters,
                selectedSemesterValue = selectedSemester,
                rows = rows,
                errorMessage = feedbackRes?.error,
                isLoading = isLoading || feedbackRes == null
            ),
            onAction = { action ->
                when (action) {
                    is M3EFeedbackStatusAction.SelectSemester -> selectedSemester = action.value
                    is M3EFeedbackStatusAction.Refresh -> {
                        selectedSemester?.let { AppState.refreshFeedbackStatus(it) }
                    }
                    is M3EFeedbackStatusAction.Back -> backTo(Screen.ACADEMICS)
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 10. Fresher Welcome
// ============================================================================

@Composable
fun M3EFresherWelcomeScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identity by UserStore.identity.collectAsState()
    val fresherProfile = remember(identity) { M3EFresherContent.fromIdentity(identity) }
    val sections = remember(fresherProfile) { M3EFresherContent.sectionsFor(fresherProfile) }

    AmazeM3ETheme(isDark = isDark) {
        M3EFresherWelcomeScreen(
            state = M3EFresherWelcomeState(sections = sections, profile = fresherProfile),
            onAction = { action ->
                when (action) {
                    is M3EFresherWelcomeAction.OpenSection -> {
                        when (action.category) {
                            M3EFresherCategory.CAMPUS -> AppState.navigateTo(Screen.MORE)
                            M3EFresherCategory.ACADEMICS -> AppState.navigateTo(Screen.ACADEMICS)
                            M3EFresherCategory.HOSTEL -> AppState.navigateTo(Screen.HOSTEL)
                            M3EFresherCategory.TRANSPORT -> AppState.navigateTo(Screen.TRANSPORT)
                            M3EFresherCategory.EVENTS -> AppState.navigateTo(Screen.EVENTS)
                        }
                    }
                    is M3EFresherWelcomeAction.Back -> backToHub()
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 11. Hall of Fame
// ============================================================================

@Composable
fun M3EHallOfFameScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var contributors by remember { mutableStateOf<List<M3EHallOfFameContributor>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        contributors = ContentData.contributors().map {
            M3EHallOfFameContributor(it.name, it.role)
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EHallOfFameScreen(
            state = M3EHallOfFameState(contributors = contributors),
            onAction = { action ->
                when (action) {
                    is M3EHallOfFameAction.Refresh -> refreshTick++
                    is M3EHallOfFameAction.Back -> backToHub()
                }
            },
            modifier = modifier
        )
    }
}
