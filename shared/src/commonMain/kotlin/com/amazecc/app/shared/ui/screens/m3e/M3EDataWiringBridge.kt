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
import com.amazecc.app.shared.domain.FreeClassroomDeriver
import com.amazecc.app.shared.domain.FreeClassroomDay
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
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
 * The only model fields read from those flows are:
 *  - `FreeClassroomsRes`: `rooms`, `success`, `error`, `message`; each room exposes
 *    `roomNumber`, `buildingCode`, `floor`, `capacity`, `isAirConditioned`,
 *    `availabilityStatus`, `isCurrentlyAvailable`, `nextOccupiedTime`.
 *  - `DocumentItem`: only `id` and `title` (the flat circulars-derived list).
 *
 * Field access is deliberately confined to the small mapping helpers below so
 * aligning with the final model shape is a one-line change per field.
 */

/**
 * New real-data host for free classrooms. Replaces the hardcoded `sampleRooms`
 * host in M3EStateBridge.kt. App.kt / MainTabPager.kt should route here.
 */
@Composable
fun M3EFreeClassroomsHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val freeClassroomsRes by AppState.freeClassrooms.collectAsState()
    val freeRoomTarget by AppState.freeRoomTarget.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedBuilding by remember { mutableStateOf(M3EClassroomBuilding.ALL) }
    var onlyAvailableNow by remember { mutableStateOf(true) }

    // D-162: the "When" / block answer is derived from real snapshot rows, so changing the day
    // or period genuinely changes which rooms come back. The legacy cache still feeds the
    // per-room card list below (one derivation, no duplicated occupancy logic).
    val periodOptions = remember { FreeClassroomDeriver.periodOptions() }
    var selectedDay by remember { mutableStateOf(FreeClassroomDay.MON.key) }
    var selectedPeriodKey by remember { mutableStateOf(periodOptions.first().key) }
    var kindFilter by remember { mutableStateOf(M3EFreeClassroomKindFilter.ALL) }
    var blockFilter by remember { mutableStateOf("All") }
    var selectedRoom by remember { mutableStateOf<String?>(null) }

    val derivation = remember(domain, selectedDay, selectedPeriodKey) {
        FreeClassroomDeriver.derive(domain, selectedDay, selectedPeriodKey)
    }

    // Search deep-links are write-only in AppState; consume the value once on entry so a user
    // clearing the field is not immediately overwritten by the same target on recomposition.
    var targetApplied by remember { mutableStateOf(false) }
    LaunchedEffect(freeRoomTarget) {
        if (!targetApplied && !freeRoomTarget.isNullOrBlank()) {
            searchQuery = freeRoomTarget!!
            targetApplied = true
        }
    }

    // Real current time, used only for the "Now" affordance label and its target. The deriver
    // itself stays clock-free; this is host-side presentation state.
    val (nowDayKey, nowPeriodKey, nowLabel) = remember(periodOptions) {
        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val key = when (local.date.dayOfWeek) {
            DayOfWeek.MONDAY -> "MON"
            DayOfWeek.TUESDAY -> "TUE"
            DayOfWeek.WEDNESDAY -> "WED"
            DayOfWeek.THURSDAY -> "THU"
            DayOfWeek.FRIDAY -> "FRI"
            DayOfWeek.SATURDAY -> "SAT"
            DayOfWeek.SUNDAY -> "MON"
        }
        val minute = local.time.hour * 60 + local.time.minute
        val pick = periodOptions.firstOrNull { !it.isLunch && minute in it.startMinute until it.endMinute }
            ?: periodOptions.firstOrNull { !it.isLunch }
            ?: periodOptions.firstOrNull()
        Triple(
            key,
            pick?.key.orEmpty(),
            if (pick == null) "" else "Now · ${FreeClassroomDay.fromKey(key).label} ${pick.label}"
        )
    }

    // The inspector sheet's LIFO guard requires its id on the overlay stack; without this
    // every dismissal route (X / swipe / backdrop / Escape / back) would be a no-op.
    val inspectorOpen = selectedRoom != null
    LaunchedEffect(inspectorOpen) {
        if (inspectorOpen) AppState.pushOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID)
        else AppState.popOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(FREE_CLASSROOM_INSPECTOR_OVERLAY_ID) }
    }

    // Free classrooms are derived by the central Sync All sweep (SyncModule.FREE_CLASSROOMS).
    // Page entry only reads the cached flow; the Refresh action remains the explicit recompute.
    AppBackHandler {
        // Close the open inspector first; do not navigate the whole screen away under a sheet.
        if (selectedRoom != null) selectedRoom = null
        else if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS)
    }

    val classrooms = remember(freeClassroomsRes) {
        freeClassroomsRes?.rooms.orEmpty().map { room ->
            M3EClassroomSlot(
                roomNumber = room.roomNumber,
                buildingCode = room.buildingCode,
                floor = room.floor,
                capacity = room.capacity,
                isAirConditioned = room.isAirConditioned,
                availabilityStatus = room.availabilityStatus,
                isCurrentlyAvailable = room.isCurrentlyAvailable,
                nextOccupiedTime = room.nextOccupiedTime
            )
        }
    }
    val filtered = remember(classrooms, searchQuery, selectedBuilding, onlyAvailableNow) {
        filterM3EClassrooms(classrooms, selectedBuilding, onlyAvailableNow, searchQuery)
    }
    val responseError = freeClassroomsRes?.let { res ->
        if (!res.success) res.error ?: res.message else null
    }

    val state = M3EFreeClassroomsState(
        classrooms = classrooms,
        filteredClassrooms = filtered,
        selectedBuilding = selectedBuilding,
        onlyAvailableNow = onlyAvailableNow,
        searchQuery = searchQuery,
        isLoading = isLoading && classrooms.isEmpty(),
        errorMessage = error ?: responseError,
        selectedDay = derivation.day.key,
        selectedPeriodKey = derivation.period.key,
        availablePeriodOptions = periodOptions,
        kindFilter = kindFilter,
        blockFilter = blockFilter,
        blockSummaries = derivation.blocks,
        selectedRoom = selectedRoom,
        nowLabel = nowLabel,
        nowDayKey = nowDayKey,
        nowPeriodKey = nowPeriodKey
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EFreeClassroomsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EFreeClassroomsAction.SelectBuilding -> selectedBuilding = action.building
                    is M3EFreeClassroomsAction.ToggleOnlyAvailable -> onlyAvailableNow = action.onlyAvailable
                    is M3EFreeClassroomsAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EFreeClassroomsAction.Refresh -> AppState.refreshFreeClassrooms()
                    is M3EFreeClassroomsAction.SelectDay -> {
                        selectedDay = action.day
                        blockFilter = "All"
                        selectedRoom = null
                    }
                    is M3EFreeClassroomsAction.SelectPeriod -> selectedPeriodKey = action.periodKey
                    is M3EFreeClassroomsAction.StepPeriod -> {
                        val current = periodOptions.indexOfFirst { it.key == selectedPeriodKey }
                            .takeIf { it >= 0 } ?: 0
                        val next = (current + action.delta).coerceIn(0, periodOptions.lastIndex)
                        selectedPeriodKey = periodOptions[next].key
                    }
                    is M3EFreeClassroomsAction.SetKindFilter -> kindFilter = action.filter
                    is M3EFreeClassroomsAction.SetBlockFilter -> blockFilter = action.block
                    is M3EFreeClassroomsAction.SelectRoom -> selectedRoom = action.roomCode
                    M3EFreeClassroomsAction.ToggleRoomInspector -> selectedRoom = null
                }
            },
            modifier = modifier
        )
    }
}

/** Pure filter used by [M3EFreeClassroomsHost] and covered by JVM unit tests. */
internal fun filterM3EClassrooms(
    classrooms: List<M3EClassroomSlot>,
    building: M3EClassroomBuilding,
    onlyAvailableNow: Boolean,
    searchQuery: String
): List<M3EClassroomSlot> = classrooms.filter { room ->
    val matchesBuilding = building == M3EClassroomBuilding.ALL || room.buildingCode == building.code
    val matchesAvailability = !onlyAvailableNow || room.isCurrentlyAvailable
    val matchesQuery = searchQuery.isBlank() || room.roomNumber.contains(searchQuery, ignoreCase = true)
    matchesBuilding && matchesAvailability && matchesQuery
}
