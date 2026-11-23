package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.amazecc.app.shared.domain.FREE_CLASSROOM_RUN_LENGTH
import com.amazecc.app.shared.domain.FreeClassroomDay
import com.amazecc.app.shared.domain.FreeClassroomDeriver
import com.amazecc.app.shared.domain.FreeClassroomKindFilter
import com.amazecc.app.shared.domain.FreeClassroomOccupant
import com.amazecc.app.shared.domain.FreeClassroomQuery
import com.amazecc.app.shared.domain.FreeClassroomWeekIndex
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomClassDetails
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomInsights
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomOccupant
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomTimelineEntry
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ffcs.FfcsStorage
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Stateful hosts for campus screens that previously had their data faked inside
 * [M3EStateBridge]. Every screen here stays 100% stateless; all flow collection,
 * filtering and refresh side-effects live in these hosts.
 *
 * Data-contract note (owned by the data-layer worker — do not redefine here):
 *  - `AppState.freeClassrooms: StateFlow<FreeClassroomsRes?>` / `refreshFreeClassrooms()`
 *    This is an OFFLINE derivation from the FFCS course report / domain snapshot.
 *    There is no server endpoint or VTOP scraper; `refreshFreeClassrooms()` is a
 *    local recomputation, never a network call.
 *  - `AppState.documents: StateFlow<List<DocumentItem>>` / `refreshDocuments()`
 *    Built by flattening the existing `/api/circulars` folder tree; no separate
 *    documents endpoint or scraper exists.
 *
 * D-191 follow-up: `FreeClassroomsRes` is read here only as a refresh signal and for its own
 * success/error flag — no room field is mapped into the screen any more. The single answer comes
 * from [FreeClassroomWeekIndex]. `DocumentItem`: only `id` and `title` still apply to the documents
 * host below.
 */

/**
 * D-191 real-data host for free classrooms. Replaces the hardcoded `sampleRooms`
 * host in M3EStateBridge.kt. App.kt / MainTabPager.kt should route here.
 *
 * Every number and every occupant shown by the stateless screen comes from the one
 * [FreeClassroomWeekIndex] answer derived from the signed-in domain snapshot plus the FFCS
 * catalogue rows already in local storage. No secondary per-room track is written any more.
 */
@Composable
fun M3EFreeClassroomsHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val freeClassroomsRes by AppState.freeClassrooms.collectAsState()
    val freeRoomTarget by AppState.freeRoomTarget.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val clipboard = LocalClipboardManager.current

    // D-191 follow-up: refresh feedback is FC-local. It must never be written to the global
    // AppState.error (where it would put a full-page error over valid results and leak to other
    // screens), and it auto-clears on a real timer.
    var refreshNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refreshNotice) {
        if (refreshNotice == null) return@LaunchedEffect
        delay(4_000)
        if (refreshNotice != null) refreshNotice = null
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedBuilding by remember { mutableStateOf(M3EClassroomBuilding.ALL) }
    var onlyAvailableNow by remember { mutableStateOf(true) }

    val periodOptions = remember { FreeClassroomDeriver.periodOptions() }
    var selectedDay by remember { mutableStateOf(FreeClassroomDay.MON.key) }
    var selectedPeriodKey by remember { mutableStateOf(periodOptions.first { !it.isLunch }.key) }
    var kindFilter by remember { mutableStateOf(M3EFreeClassroomKindFilter.ALL) }
    var blockFilter by remember { mutableStateOf("All") }
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var inspectDay by remember { mutableStateOf(selectedDay) }
    var filtersOpen by remember { mutableStateOf(false) }
    var runOnly by remember { mutableStateOf(false) }
    var expandedBlocks by remember { mutableStateOf(emptySet<String>()) }
    var copiedRoom by remember { mutableStateOf<String?>(null) }
    var selectedClassKey by remember { mutableStateOf<String?>(null) }

    // A real 60-second tick, matching the web page. The deriver itself stays clock-free.
    var tick by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = Clock.System.now()
            delay(60_000)
        }
    }
    val nowInfo = remember(tick, periodOptions) {
        FreeClassroomWeekIndex.resolveNow(
            periodOptions,
            tick.toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }

    // Land on a real day/period once, only when the strict clock actually has a live target.
    var clockInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(nowInfo) {
        if (!clockInitialized && nowInfo.isLive && nowInfo.day != null && nowInfo.period != null) {
            selectedDay = nowInfo.day.key
            inspectDay = nowInfo.day.key
            selectedPeriodKey = nowInfo.period.key
            clockInitialized = true
        }
    }

    // Search deep-links are write-only in AppState; consume the value once on entry so a user
    // clearing the field is not immediately overwritten by the same target on recomposition.
    // Consuming clears the flow, which re-runs this effect with null and re-arms the one-shot
    // guard for a later, different openFreeRoom(...) target.
    var targetApplied by remember { mutableStateOf(false) }
    LaunchedEffect(freeRoomTarget) {
        val target = freeRoomTarget
        if (target.isNullOrBlank()) {
            targetApplied = false
            return@LaunchedEffect
        }
        if (!targetApplied) {
            searchQuery = target
            targetApplied = true
            AppState.consumeFreeRoomTarget()
        }
    }

    // FFCS catalogue rows are already on the device. The cached derivation flow is used only as a
    // refresh signal; no field of it feeds the answer below.
    val catalogueRows = remember(domain, freeClassroomsRes) {
        val semesterId = Projections.reportingSemester(domain)?.id
        val forSemester = semesterId?.let {
            runCatching { FfcsStorage.Default.loadRawCourses(it) }.getOrDefault(emptyList())
        }.orEmpty()
        if (forSemester.isNotEmpty()) {
            forSemester
        } else {
            runCatching { FfcsStorage.Default.loadRawCourses() }.getOrDefault(emptyList())
        }
    }
    val week = remember(domain, catalogueRows) {
        FreeClassroomWeekIndex.build(domain, catalogueRows)
    }
    val dayAnswer = remember(week, selectedDay) { week.day(selectedDay) }

    val domainKind = when (kindFilter) {
        M3EFreeClassroomKindFilter.ALL -> FreeClassroomKindFilter.ALL
        M3EFreeClassroomKindFilter.THEORY -> FreeClassroomKindFilter.THEORY
        M3EFreeClassroomKindFilter.LAB -> FreeClassroomKindFilter.LAB
    }
    val query = FreeClassroomQuery(
        day = dayAnswer.day,
        periodKey = selectedPeriodKey,
        kind = domainKind,
        building = selectedBuilding.code,
        block = blockFilter,
        search = searchQuery,
        runOnly = runOnly,
        onlyFree = onlyAvailableNow
    )
    val result = FreeClassroomWeekIndex.query(week, query)

    // The three insights read the UNFILTERED day answer, so they can always widen the result.
    // "Free now" is only ever the STRICT live period from the ticking clock. When the clock is not
    // live (weekend / lunch / outside hours) the card is hidden rather than relabelling a stale
    // selection count as "Free now".
    val livePeriodAnswer = if (nowInfo.isLive && nowInfo.day != null && nowInfo.period != null) {
        week.day(nowInfo.day.key).period(nowInfo.period.key)
    } else {
        null
    }
    val insights = remember(dayAnswer, selectedPeriodKey, livePeriodAnswer) {
        val best = FreeClassroomWeekIndex.bestPeriod(dayAnswer)
        M3EFreeClassroomInsights(
            isLiveNow = livePeriodAnswer != null,
            freeNowCount = livePeriodAnswer?.freeRoomCodes?.size
                ?: dayAnswer.period(selectedPeriodKey).freeRoomCodes.size,
            bestPeriodKey = best?.first?.key,
            bestPeriodLabel = best?.first?.label,
            bestPeriodFree = best?.second ?: 0,
            runFreeCount = FreeClassroomWeekIndex.freeCodesForRun(
                dayAnswer,
                selectedPeriodKey,
                FREE_CLASSROOM_RUN_LENGTH
            ).size
        )
    }

    // The inspector's day is its own; browsing a room's Thursday never moves the list.
    val timeline = remember(week, selectedRoom, inspectDay, selectedDay, selectedPeriodKey) {
        val roomCode = selectedRoom
        if (roomCode.isNullOrBlank()) {
            emptyList()
        } else {
            FreeClassroomWeekIndex.timeline(week, inspectDay, roomCode, selectedDay, selectedPeriodKey)
                .map { entry ->
                    M3EFreeClassroomTimelineEntry(
                        periodKey = entry.periodKey,
                        label = entry.label,
                        isFree = entry.isFree,
                        isSelected = entry.isSelected,
                        occupants = entry.occupants.map { it.toM3E() }
                    )
                }
        }
    }
    val selectedClass = remember(timeline, selectedClassKey, selectedRoom, inspectDay) {
        val key = selectedClassKey
        val row = if (key == null) null else timeline.firstOrNull { it.occupants.any { o -> o.key == key } }
        val occupant = row?.occupants?.firstOrNull { it.key == key }
        if (row == null || occupant == null) {
            null
        } else {
            M3EFreeClassroomClassDetails(
                occupantKey = occupant.key,
                courseCode = occupant.courseCode,
                title = occupant.title,
                faculty = occupant.faculty,
                erpId = occupant.erpId,
                classId = occupant.classId,
                slot = occupant.slot,
                type = occupant.type,
                roomCode = selectedRoom.orEmpty(),
                dayLabel = freeClassroomFullDayLabel(inspectDay),
                periodLabel = row.label,
                schoolId = occupant.schoolId,
                source = occupant.source
            )
        }
    }

    // M3EDismissibleSheet's LIFO guard requires each id on AppState.overlayStack; without this
    // every dismissal route (X / swipe / backdrop / Escape / back) would be a no-op.
    val inspectorOpen = selectedRoom != null
    val classOpen = selectedClass != null
    LaunchedEffect(inspectorOpen) {
        if (inspectorOpen) AppState.pushOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID)
        else AppState.popOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID)
    }
    LaunchedEffect(classOpen) {
        if (classOpen) AppState.pushOverlay(FREE_CLASSROOM_CLASS_DETAILS_OVERLAY_ID)
        else AppState.popOverlay(FREE_CLASSROOM_CLASS_DETAILS_OVERLAY_ID)
    }
    LaunchedEffect(filtersOpen) {
        if (filtersOpen) AppState.pushOverlay(FREE_CLASSROOM_FILTERS_OVERLAY_ID)
        else AppState.popOverlay(FREE_CLASSROOM_FILTERS_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose {
            AppState.popOverlay(FREE_CLASSROOM_CLASS_DETAILS_OVERLAY_ID)
            AppState.popOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID)
            AppState.popOverlay(FREE_CLASSROOM_FILTERS_OVERLAY_ID)
        }
    }

    AppBackHandler {
        // Close the topmost overlay first; never navigate the whole screen away under a sheet.
        when {
            selectedClass != null -> selectedClassKey = null
            selectedRoom != null -> {
                selectedRoom = null
                selectedClassKey = null
            }
            filtersOpen -> filtersOpen = false
            else -> if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS)
        }
    }

    // The cached derivation is only a refresh signal; the screen reads the single week-index
    // answer above. A failed derivation with valid results stays inline instead of going full page.
    val responseError = freeClassroomsRes?.let { res ->
        if (!res.success) res.error ?: res.message else null
    }
    val answerHasData = result.blocks.isNotEmpty() || dayAnswer.blocks.isNotEmpty()

    // Transient clipboard feedback, cleared on a real timer. No fake "copied" state.
    LaunchedEffect(copiedRoom) {
        val code = copiedRoom ?: return@LaunchedEffect
        delay(1_800)
        if (copiedRoom == code) copiedRoom = null
    }

    val isLive = nowInfo.isLive &&
        selectedDay == nowInfo.day?.key &&
        selectedPeriodKey == nowInfo.period?.key
    // Outside teaching hours there is no live period, but there is still a real teaching day to
    // jump to. On a weekend there is neither, so the affordance is hidden.
    val jumpPeriodKey = nowInfo.period?.key
        ?: if (nowInfo.day != null) periodOptions.first { !it.isLunch }.key else ""

    val state = M3EFreeClassroomsState(
        selectedBuilding = selectedBuilding,
        onlyAvailableNow = onlyAvailableNow,
        searchQuery = searchQuery,
        isLoading = isLoading && result.blocks.isEmpty() && dayAnswer.blocks.isEmpty(),
        errorMessage = responseError?.takeIf { !answerHasData },
        inlineNotice = refreshNotice ?: responseError?.takeIf { answerHasData },
        selectedDay = dayAnswer.day.key,
        selectedPeriodKey = result.period.key,
        availablePeriodOptions = periodOptions,
        kindFilter = kindFilter,
        blockFilter = blockFilter,
        blockSummaries = result.blocks,
        availableBlocks = dayAnswer.blocks,
        selectedRoom = selectedRoom,
        inspectDay = inspectDay,
        roomTimeline = timeline,
        selectedClass = selectedClass,
        filtersOpen = filtersOpen,
        runOnly = runOnly,
        expandedBlocks = expandedBlocks,
        copiedRoom = copiedRoom,
        clockLabel = nowInfo.clockLabel,
        isLive = isLive,
        nowDayKey = nowInfo.day?.key.orEmpty(),
        nowPeriodKey = jumpPeriodKey,
        insights = insights
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EFreeClassroomsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EFreeClassroomsAction.SelectBuilding -> selectedBuilding = action.building
                    is M3EFreeClassroomsAction.ToggleOnlyAvailable -> onlyAvailableNow = action.onlyAvailable
                    is M3EFreeClassroomsAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EFreeClassroomsAction.Refresh -> {
                        if (isLoading || SyncEngine.isAnyModuleLoading()) {
                            // Local only: a busy tap gets an inline FC note, never a global error.
                            refreshNotice = "Sync in progress, try again shortly"
                        } else {
                            refreshNotice = null
                            AppState.refreshFreeClassrooms()
                        }
                    }
                    is M3EFreeClassroomsAction.SelectDay -> {
                        selectedDay = action.day
                        inspectDay = action.day
                        blockFilter = "All"
                        selectedRoom = null
                        selectedClassKey = null
                    }
                    is M3EFreeClassroomsAction.SelectPeriod -> selectedPeriodKey = action.periodKey
                    is M3EFreeClassroomsAction.StepPeriod -> {
                        val selectable = periodOptions.filter { !it.isLunch }
                        val current = selectable.indexOfFirst { it.key == selectedPeriodKey }
                            .takeIf { it >= 0 } ?: 0
                        val next = (current + action.delta).coerceIn(0, selectable.lastIndex)
                        selectedPeriodKey = selectable[next].key
                    }
                    is M3EFreeClassroomsAction.SetKindFilter -> kindFilter = action.filter
                    is M3EFreeClassroomsAction.SetBlockFilter -> blockFilter = action.block
                    is M3EFreeClassroomsAction.SelectRoom -> {
                        selectedRoom = action.roomCode
                        selectedClassKey = null
                        inspectDay = selectedDay
                    }
                    M3EFreeClassroomsAction.ToggleRoomInspector -> {
                        selectedRoom = null
                        selectedClassKey = null
                    }
                    is M3EFreeClassroomsAction.SetFiltersOpen -> filtersOpen = action.open
                    is M3EFreeClassroomsAction.SetRunOnly -> runOnly = action.enabled
                    is M3EFreeClassroomsAction.ToggleBlockExpanded -> {
                        expandedBlocks = if (action.block in expandedBlocks) {
                            expandedBlocks - action.block
                        } else {
                            expandedBlocks + action.block
                        }
                    }
                    is M3EFreeClassroomsAction.CopyRoom -> {
                        val copied = runCatching {
                            clipboard.setText(AnnotatedString(action.roomCode))
                        }.isSuccess
                        copiedRoom = if (copied) action.roomCode else null
                    }
                    is M3EFreeClassroomsAction.SelectInspectDay -> inspectDay = action.day
                    is M3EFreeClassroomsAction.OpenClass -> {
                        // Only open a detail overlay for an occupant the real timeline contains.
                        if (timeline.any { row -> row.occupants.any { it.key == action.occupantKey } }) {
                            selectedClassKey = action.occupantKey
                        }
                    }
                    M3EFreeClassroomsAction.CloseClass -> selectedClassKey = null
                    is M3EFreeClassroomsAction.ViewFacultySchedule -> {
                        if (action.employeeId.isNotBlank()) {
                            AppState.openFaculty(action.schoolId.orEmpty(), action.employeeId)
                        }
                    }
                    is M3EFreeClassroomsAction.ApplyInsight -> when (action.id) {
                        "free_now" -> {
                            val instant = Clock.System.now()
                            tick = instant
                            val fresh = FreeClassroomWeekIndex.resolveNow(
                                periodOptions,
                                instant.toLocalDateTime(TimeZone.currentSystemDefault())
                            )
                            if (fresh.day != null) {
                                selectedDay = fresh.day.key
                                inspectDay = fresh.day.key
                                selectedPeriodKey = fresh.period?.key
                                    ?: periodOptions.first { !it.isLunch }.key
                            }
                            runOnly = false
                            blockFilter = "All"
                            searchQuery = ""
                            kindFilter = M3EFreeClassroomKindFilter.ALL
                            selectedBuilding = M3EClassroomBuilding.ALL
                            onlyAvailableNow = true
                        }
                        "emptiest" -> {
                            FreeClassroomWeekIndex.bestPeriod(dayAnswer)?.let { best ->
                                selectedPeriodKey = best.first.key
                                runOnly = false
                            }
                        }
                        "run" -> {
                            runOnly = !runOnly
                            blockFilter = "All"
                            searchQuery = ""
                        }
                        else -> Unit
                    }
                    M3EFreeClassroomsAction.JumpToNow -> {
                        val instant = Clock.System.now()
                        tick = instant
                        val fresh = FreeClassroomWeekIndex.resolveNow(
                            periodOptions,
                            instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        )
                        fresh.day?.key?.let {
                            selectedDay = it
                            inspectDay = it
                        }
                        if (fresh.period != null) {
                            selectedPeriodKey = fresh.period.key
                        } else if (fresh.day != null) {
                            selectedPeriodKey = periodOptions.first { !it.isLunch }.key
                        }
                        runOnly = false
                        blockFilter = "All"
                        searchQuery = ""
                    }
                    is M3EFreeClassroomsAction.ShowDayInList -> {
                        selectedDay = action.day
                        blockFilter = "All"
                    }
                }
            },
            modifier = modifier
        )
    }
}

private fun FreeClassroomOccupant.toM3E(): M3EFreeClassroomOccupant = M3EFreeClassroomOccupant(
    key = key,
    courseCode = courseCode,
    title = title,
    faculty = faculty,
    erpId = erpId,
    classId = classId,
    slot = slot,
    type = type,
    schoolId = schoolId,
    source = source
)

private fun freeClassroomFullDayLabel(key: String): String = when (FreeClassroomDay.fromKey(key)) {
    FreeClassroomDay.MON -> "Monday"
    FreeClassroomDay.TUE -> "Tuesday"
    FreeClassroomDay.WED -> "Wednesday"
    FreeClassroomDay.THU -> "Thursday"
    FreeClassroomDay.FRI -> "Friday"
    FreeClassroomDay.SAT -> "Saturday"
}
