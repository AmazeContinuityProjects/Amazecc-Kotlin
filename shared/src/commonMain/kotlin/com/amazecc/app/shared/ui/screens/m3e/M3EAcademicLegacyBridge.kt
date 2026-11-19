package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AcademicDerivers.resolveCurrentSemester
import com.amazecc.app.shared.state.AcademicDerivers.toAttendanceItem
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.FfcsViewModel
import com.amazecc.app.shared.state.FriendsViewModel
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.SocialUtils
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
                val badge = when {
                    c.type.contains("Project", ignoreCase = true) -> "PJ"
                    c.type.contains("Online", ignoreCase = true) -> "OC"
                    c.type.contains("Embedded", ignoreCase = true) -> "EMB"
                    c.type.contains("Lab", ignoreCase = true) -> "LO"
                    else -> "TH"
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
    // Real OD data now flows from the VTOP OD details endpoint through AppState instead of being
    // inferred from attendance-log statuses with a lab/theory hour heuristic.
    val odDetails by AppState.odDetails.collectAsState()
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

    LaunchedEffect(selectedSemester, refreshTick) {
        if (selectedSemester.isNotBlank()) {
            AppState.refreshOdDetails(selectedSemester)
        }
    }

    val days = remember(odDetails, tracker) { mapOdRecordsToDays(odDetails, tracker) }

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
// 4. Arrears & Remedial
// ============================================================================

@Composable
fun M3EArrearScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var activeTab by remember { mutableStateOf(M3EArrearTab.SCHEDULE) }

    AmazeM3ETheme(isDark = isDark) {
        M3EArrearScreen(
            state = M3EArrearState(activeTab = activeTab),
            onAction = { action ->
                when (action) {
                    is M3EArrearAction.SelectTab -> activeTab = action.tab
                    is M3EArrearAction.Refresh -> AppState.loadAllData()
                    is M3EArrearAction.Back -> backTo(Screen.ACADEMICS)
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

    var activeTab by remember { mutableStateOf(M3ESocialTab.SHARE) }
    var addFriendOpen by remember { mutableStateOf(false) }
    var createGroupOpen by remember { mutableStateOf(false) }
    var addFriendCode by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current

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
                friends = friends,
                groups = groups,
                commonFreeSlots = commonFreeSlots,
                isAddFriendDialogOpen = addFriendOpen,
                isCreateGroupDialogOpen = createGroupOpen,
                addFriendCode = addFriendCode,
                newGroupName = newGroupName
            ),
            onAction = { action ->
                when (action) {
                    is M3ESocialAction.SelectTab -> activeTab = action.tab
                    is M3ESocialAction.UpdateAddFriendCode -> addFriendCode = action.code
                    is M3ESocialAction.UpdateNewGroupName -> newGroupName = action.name
                    is M3ESocialAction.OpenAddFriendDialog -> addFriendOpen = true
                    is M3ESocialAction.CloseAddFriendDialog -> addFriendOpen = false
                    is M3ESocialAction.OpenCreateGroupDialog -> createGroupOpen = true
                    is M3ESocialAction.CloseCreateGroupDialog -> createGroupOpen = false
                    is M3ESocialAction.AddFriend -> {
                        if (FriendsViewModel.addFriendFromCode(addFriendCode)) {
                            addFriendCode = ""
                            addFriendOpen = false
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
                    is M3ESocialAction.Back -> backToHub()
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

    var activeTab by remember { mutableStateOf(M3EFfcsTab.COURSES) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedResultId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (offerings.isEmpty()) FfcsViewModel.initFromCsv()
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
    val results = remember(generated) {
        generated.map { timetable ->
            M3EFfcsResult(
                id = timetable.id,
                name = timetable.name,
                summary = "${timetable.metrics.halfDays} half-days · ${timetable.metrics.gaps} gaps · ${timetable.courses.size} courses"
            )
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
                isLoading = isLoading
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
                    is M3EFfcsPlannerAction.Refresh -> FfcsViewModel.initFromCsv()
                    is M3EFfcsPlannerAction.Back -> backToHub()
                }
            },
            modifier = modifier
        )
    }
}

// ============================================================================
// 8. Club Detail
// ============================================================================

@Composable
fun M3EClubDetailScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val clubsRes by AppState.clubs.collectAsState()
    val clubId by AppState.selectedClubId.collectAsState()
    var isEnrolled by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val club = remember(clubsRes, clubId) {
        clubsRes?.clubs?.firstOrNull { it.id == clubId }
    }
    val state = M3EClubDetailState(
        clubId = club?.id.orEmpty(),
        name = club?.name.orEmpty(),
        description = club?.description.orEmpty(),
        website = club?.website,
        instagram = club?.instagram,
        whatsapp = club?.whatsapp,
        isEnrolled = isEnrolled
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EClubDetailScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EClubDetailAction.ToggleEnroll -> isEnrolled = !isEnrolled
                    is M3EClubDetailAction.OpenLink -> {
                        try { uriHandler.openUri(action.url) } catch (_: Exception) { }
                    }
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

    // Always pass a real semesterId - the FFCS-section call requires one. A selection is seeded
    // above from cache; when the cache is cold, fall back to the app's selected semester id.
    LaunchedEffect(Unit) {
        if (selectedSemester == null && selectedAppSemester.isNotBlank()) {
            AppState.refreshFeedbackStatus(selectedAppSemester)
        }
    }
    LaunchedEffect(selectedSemester) {
        val sem = selectedSemester ?: return@LaunchedEffect
        AppState.refreshFeedbackStatus(sem)
    }

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
    val sections = listOf(
        M3EFresherSection(M3EFresherCategory.CAMPUS, "Campus", "VIT has state-of-the-art labs, libraries, sports facilities and hostels. Get your ID card and explore!"),
        M3EFresherSection(M3EFresherCategory.ACADEMICS, "Academics", "Attend orientation, understand CBCS, manage registration, and track attendance and grades via VTOP."),
        M3EFresherSection(M3EFresherCategory.HOSTEL, "Hostel", "Accommodation, mess timings, laundry and counselling are managed in the Hostel module."),
        M3EFresherSection(M3EFresherCategory.TRANSPORT, "Transport", "College buses run on fixed routes. Day-boarders can register for bus passes in Transport."),
        M3EFresherSection(M3EFresherCategory.EVENTS, "Events", "Join clubs, hackathons, cultural fests and tech talks via Events and Social.")
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EFresherWelcomeScreen(
            state = M3EFresherWelcomeState(sections = sections),
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
