package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.domain.overhaul.M3EFacultyTimetableSlot
import com.amazecc.app.shared.model.FacultyProfile
import com.amazecc.app.shared.model.FacultySchool
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.FacultyFreeSlotsUtil
import com.amazecc.app.shared.utils.FacultyFreeSlotsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * D-163: stateful host that brings the real web faculty directory into the M3E screen.
 *
 * All fetching (schools, per-school scrape, optional directory enrichment and the bundled
 * FFCS schedule derivation) happens off the UI thread. The only values handed to the
 * stateless [M3EFacultyScreen] are immutable snapshots in [M3EFacultyState]; no faculty,
 * schedule slot or presence claim is ever fabricated. In particular, real data never sets
 * `isCurrentlyTeaching` / `currentSlotInfo`, so the screen shows no "in class" claim.
 */
@Composable
fun M3EFacultyLiveHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val clipboard = LocalClipboardManager.current

    var schools by remember { mutableStateOf<List<FacultySchool>>(emptyList()) }
    var facultyList by remember { mutableStateOf<List<M3EFacultyMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var ui by remember { mutableStateOf(FacultyLiveUiState()) }
    var attemptedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 1. Mount / refresh: real school list only. A failed payload is surfaced, never replaced
    //    with the hardcoded school names from M3EFacultyState's default.
    LaunchedEffect(ui.refreshTick) {
        isLoading = true
        loadError = null
        attemptedIds = emptySet()
        try {
            val res = withContext(Dispatchers.Default) { AmazeClient.getFacultySchools() }
            if (!res.success || res.schools.isEmpty()) {
                schools = emptyList()
                facultyList = emptyList()
                loadError = res.error ?: "Failed to load faculty schools"
                isLoading = false
            } else {
                schools = res.schools
                val labels = mapFacultySchools(res.schools)
                if (ui.selectedSchool !in labels || ui.selectedSchool == "All") {
                    labels.firstOrNull { it != "All" }?.let { ui = ui.copy(selectedSchool = it) }
                }
            }
        } catch (e: Exception) {
            schools = emptyList()
            facultyList = emptyList()
            loadError = e.message ?: "Failed to load faculty schools"
            isLoading = false
        }
    }

    // 2. Real scrape for the selected school (or every real school when "All" is chosen).
    LaunchedEffect(ui.refreshTick, ui.selectedSchool, schools) {
        if (schools.isEmpty()) return@LaunchedEffect
        val targets = if (ui.selectedSchool == "All") {
            schools.map { it.id }
        } else {
            schools.filter { it.school_name == ui.selectedSchool }.map { it.id }
        }
        if (targets.isEmpty()) {
            facultyList = emptyList()
            loadError = "No schools available for this selection"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val merged = mutableListOf<M3EFacultyMember>()
        val errors = mutableListOf<String>()
        try {
            withContext(Dispatchers.Default) {
                for (id in targets) {
                    val res = AmazeClient.postFacultyScrape(id)
                    if (res.success) {
                        val label = schools.firstOrNull { it.id == id }?.school_name ?: id
                        merged.addAll(mapFacultyProfiles(res.faculties, label))
                    } else {
                        res.error?.takeIf { it.isNotBlank() }?.let(errors::add)
                    }
                }
            }
            facultyList = merged
            loadError = errors.distinct().joinToString(" · ").ifBlank { null }
        } catch (e: Exception) {
            facultyList = emptyList()
            loadError = e.message ?: "Failed to load the faculty directory"
        } finally {
            isLoading = false
        }
    }

    // 3. Optional enrichment of the expanded member from the public directory. The real
    //    scraped profile (and its already-derived schedule) stays in place if lookup fails.
    LaunchedEffect(ui.expandedFacultyId, facultyList, ui.refreshTick) {
        val id = ui.expandedFacultyId ?: return@LaunchedEffect
        val member = facultyList.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (member.id.isBlank() || member.id in attemptedIds) return@LaunchedEffect
        attemptedIds = attemptedIds + member.id
        val detail = withContext(Dispatchers.Default) { AmazeClient.getFacultyProfile(member.id) }
        if (detail != null) {
            val enriched = enrichFacultyMember(member, detail)
            facultyList = facultyList.map { if (it.id == member.id) enriched else it }
        }
    }

    // Transient, honest feedback for CopyEmail / a missing schedule target.
    LaunchedEffect(ui.notice) {
        if (ui.notice == null) return@LaunchedEffect
        delay(2_500)
        ui = ui.copy(notice = null)
    }

    fun handle(action: M3EFacultyAction) {
        // The clipboard is a real platform seam; no fake "copied" message if it is unavailable.
        val copySucceeded = if (action is M3EFacultyAction.CopyEmail && action.email.isNotBlank()) {
            runCatching { clipboard.setText(AnnotatedString(action.email)) }.isSuccess
        } else {
            true
        }
        ui = applyFacultyLiveAction(ui, action, facultyList, copySucceeded)
    }

    val availableSchools = remember(schools) { mapFacultySchools(schools) }
    val filtered = remember(facultyList, ui.searchQuery, ui.selectedSchool) {
        filterFacultyMembers(facultyList, ui.searchQuery, ui.selectedSchool)
    }
    val state = M3EFacultyState(
        searchQuery = ui.searchQuery,
        selectedSchool = ui.selectedSchool,
        availableSchools = availableSchools,
        facultyList = facultyList,
        filteredFaculty = filtered,
        expandedFacultyId = ui.expandedFacultyId,
        isLoading = isLoading,
        errorMessage = loadError
    )
    val sheetFaculty = remember(facultyList, ui.sheetFacultyId) {
        facultyList.firstOrNull { it.id == ui.sheetFacultyId }
    }
    val scheduleSheetOpen = sheetFaculty != null
    LaunchedEffect(scheduleSheetOpen) {
        if (scheduleSheetOpen) AppState.pushOverlay(FACULTY_SCHEDULE_OVERLAY_ID)
        else AppState.popOverlay(FACULTY_SCHEDULE_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(FACULTY_SCHEDULE_OVERLAY_ID) }
    }

    AmazeM3ETheme(isDark = isDark) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The stateless screen renders no isLoading/errorMessage chrome, so the host owns it.
                if (state.isLoading) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = M3ETheme.colors.primary
                        )
                        Text(
                            text = "Loading faculty directory…",
                            fontSize = 12.sp,
                            color = M3ETheme.colors.onSurfaceVariant
                        )
                    }
                }

                val error = state.errorMessage
                if (!error.isNullOrBlank() && !state.isLoading) {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.HIGH,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = error, fontSize = 13.sp, color = M3ETheme.colors.error)
                            TextButton(onClick = { handle(M3EFacultyAction.Refresh) }) {
                                Text("Retry", fontSize = 13.sp)
                            }
                        }
                    }
                }

                // The stateless screen never emits ToggleShowTimetable. This host-owned control
                // supplies the missing path and uses the exact same action handler below.
                M3EFacultyScheduleEntry(
                    expandedFacultyId = state.expandedFacultyId,
                    onOpenSchedule = { id -> handle(M3EFacultyAction.ToggleShowTimetable(id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                M3EFacultyScreen(
                    state = state,
                    onAction = { handle(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            ui.notice?.let { message ->
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 88.dp)
                ) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = M3ETheme.colors.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            val sheet = sheetFaculty
            if (sheet != null) {
                M3EFacultyScheduleSheet(
                    faculty = sheet,
                    onDismiss = { ui = ui.copy(sheetFacultyId = null) }
                )
            }
        }
    }
}

/**
 * D-163 follow-up: one full-width, clearly labelled action above the stateless screen.
 *
 * The current M3EFacultyScreen never emits ToggleShowTimetable, so this host-owned entry
 * point supplies the missing path. It is intentionally not a dead control: with no expanded
 * member it shows an honest helper line instead of a disabled-looking button. Clicking the
 * button reports the expanded member's id, which the host feeds straight into the existing
 * [M3EFacultyAction.ToggleShowTimetable] handler — no duplicate sheet-opening logic.
 */
@Composable
internal fun M3EFacultyScheduleEntry(
    expandedFacultyId: String?,
    onOpenSchedule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val id = expandedFacultyId
    if (id == null) {
        Text(
            text = "Select a faculty member to view their weekly schedule",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = modifier.testTag("faculty_schedule_entry_helper")
        )
        return
    }
    Button(
        onClick = { onOpenSchedule(id) },
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("faculty_schedule_open_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "View weekly schedule", fontSize = 14.sp)
    }
}

/** Local UI state owned by the host; every transition is covered by [applyFacultyLiveAction]. */
internal data class FacultyLiveUiState(
    val searchQuery: String = "",
    val selectedSchool: String = "All",
    val expandedFacultyId: String? = null,
    val sheetFacultyId: String? = null,
    val notice: String? = null,
    val refreshTick: Int = 0
)

/**
 * Pure reducer for all six [M3EFacultyAction] variants. Keeping it pure makes the wiring
 * (search / school / expand / clipboard feedback / timetable sheet / refresh) testable
 * without mocking [AmazeClient].
 */
internal fun applyFacultyLiveAction(
    current: FacultyLiveUiState,
    action: M3EFacultyAction,
    faculty: List<M3EFacultyMember> = emptyList(),
    clipboardCopySucceeded: Boolean = true
): FacultyLiveUiState = when (action) {
    is M3EFacultyAction.UpdateSearch -> current.copy(searchQuery = action.query)
    is M3EFacultyAction.SelectSchool ->
        current.copy(selectedSchool = action.school, expandedFacultyId = null)
    is M3EFacultyAction.ToggleExpand -> current.copy(
        expandedFacultyId = if (current.expandedFacultyId == action.facultyId) null else action.facultyId
    )
    is M3EFacultyAction.CopyEmail -> current.copy(
        notice = when {
            action.email.isBlank() -> "No email published for this faculty member"
            clipboardCopySucceeded -> "Email copied"
            else -> "Could not copy email"
        }
    )
    is M3EFacultyAction.ToggleShowTimetable -> {
        val member = faculty.firstOrNull { it.id == action.facultyId }
        if (member == null) {
            current.copy(notice = "Schedule is unavailable for this faculty member")
        } else {
            current.copy(sheetFacultyId = member.id)
        }
    }
    M3EFacultyAction.Refresh -> current.copy(refreshTick = current.refreshTick + 1, notice = null)
}

/** Real school labels, with the honest "All" option the stateless screen expects. */
internal fun mapFacultySchools(schools: List<FacultySchool>): List<String> =
    listOf("All") + schools.map { it.school_name }.filter { it.isNotBlank() }.distinct()

/**
 * DTO -> [M3EFacultyMember], field by field. Unknown values stay blank/null:
 * [FacultyProfile] has no cabin, no courses and no room/vendor fields, and the bundled FFCS
 * report has no venue for a faculty row. `isCurrentlyTeaching` is deliberately never set.
 */
internal fun mapFacultyProfiles(
    profiles: List<FacultyProfile>,
    schoolLabel: String
): List<M3EFacultyMember> = profiles.map { profile ->
    val base = M3EFacultyMember(
        id = profile.employeeId.ifBlank { profile.id },
        name = profile.name,
        designation = profile.designation,
        school = schoolLabel,
        cabin = "",
        email = profile.email,
        intercom = profile.intercom.takeIf { it.isNotBlank() },
        rating = null,
        coursesTaught = emptyList(),
        isMyFaculty = false,
        timetable = emptyList(),
        isCurrentlyTeaching = false,
        currentSlotInfo = null
    )
    attachFacultySchedule(base, FacultyFreeSlotsUtil.getFacultySchedule(profile))
}

/** Directory lookup only fills fields the directory actually returned; nothing is invented. */
internal fun enrichFacultyMember(
    member: M3EFacultyMember,
    detail: FacultyProfile
): M3EFacultyMember = member.copy(
    name = detail.name.takeIf { it.isNotBlank() } ?: member.name,
    designation = detail.designation.takeIf { it.isNotBlank() } ?: member.designation,
    email = detail.email.takeIf { it.isNotBlank() } ?: member.email,
    intercom = detail.intercom.takeIf { it.isNotBlank() } ?: member.intercom
)

/** Drive the stateless screen's filter from real school labels and real member fields. */
internal fun filterFacultyMembers(
    members: List<M3EFacultyMember>,
    query: String,
    school: String
): List<M3EFacultyMember> {
    val q = query.trim()
    return members.filter { member ->
        val schoolOk = school == "All" || member.school.equals(school, ignoreCase = true)
        val queryOk = q.isBlank() ||
            listOf(member.name, member.email, member.designation, member.school, member.cabin)
                .any { it.contains(q, ignoreCase = true) } ||
            member.coursesTaught.any { it.contains(q, ignoreCase = true) }
        schoolOk && queryOk
    }
}

/** Rebuild the only [FacultyProfile] input [FacultyFreeSlotsUtil] needs from a real member. */
internal fun facultyMemberToScheduleProfile(member: M3EFacultyMember): FacultyProfile = FacultyProfile(
    id = member.id,
    name = member.name,
    designation = member.designation,
    email = member.email,
    employeeId = member.id,
    intercom = member.intercom.orEmpty()
)

/** Real occupied periods become inline timetable rows; venue stays blank (never published). */
internal fun attachFacultySchedule(
    member: M3EFacultyMember,
    schedule: FacultyFreeSlotsResult
): M3EFacultyMember {
    val slots = schedule.occupiedSlots.map { occupied ->
        M3EFacultyTimetableSlot(
            slotCode = occupied.slotCode,
            courseCode = occupied.courseCode,
            courseTitle = occupied.courseTitle,
            roomNumber = "",
            dayOfWeek = occupied.day
        )
    }
    return member.copy(
        timetable = slots,
        coursesTaught = slots.map { it.courseCode }.distinct()
    )
}

/** One row of the sheet's real offerings table. */
internal data class FacultyOffering(
    val courseCode: String,
    val courseTitle: String,
    val type: String,
    val slot: String,
    val venue: String,
    val day: String,
    val timeRange: String
)

/** Conservative type derived from the real slot code (same L/T convention used across the app). */
internal fun facultyOfferingType(slotCode: String): String = when {
    slotCode.trim().startsWith("L", ignoreCase = true) -> "Lab"
    slotCode.trim().startsWith("T", ignoreCase = true) -> "Tutorial"
    else -> "Theory"
}

/** Offerings come only from the FFCS derivation; a blank venue is shown as unpublished. */
internal fun buildFacultyOfferings(schedule: FacultyFreeSlotsResult): List<FacultyOffering> =
    schedule.occupiedSlots.map { slot ->
        FacultyOffering(
            courseCode = slot.courseCode,
            courseTitle = slot.courseTitle,
            type = facultyOfferingType(slot.slotCode),
            slot = slot.slotCode,
            venue = "",
            day = slot.day,
            timeRange = slot.timeRange
        )
    }

/** One real cell of the weekly grid: every published period for a working day. */
internal data class FacultyDayPeriod(
    val day: String,
    val timeRange: String,
    val courseCode: String?,
    val isFree: Boolean
)

/**
 * Days x all FFCS periods, filled from [FacultyFreeSlotsResult]. Free cells are the periods
 * the derivation actually reported as free; a period with no data stays neither "class" nor
 * "free" so the grid never claims more than the bundled report says.
 */
internal fun buildFacultyWeeklyGrid(schedule: FacultyFreeSlotsResult): List<FacultyDayPeriod> {
    val periods = FacultyFreeSlotsUtil.getAllTimePeriods()
    val occupiedByDay = schedule.occupiedSlots.groupBy { it.day }
    return FacultyFreeSlotsUtil.workingDays.flatMap { day ->
        val dayOccupied = occupiedByDay[day].orEmpty()
        val dayFree = schedule.freeSlots[day].orEmpty().toSet()
        periods.map { period ->
            val classHere = dayOccupied.firstOrNull { it.timeRange == period }
            FacultyDayPeriod(
                day = day,
                timeRange = period,
                courseCode = classHere?.courseCode,
                isFree = classHere == null && period in dayFree
            )
        }
    }
}
