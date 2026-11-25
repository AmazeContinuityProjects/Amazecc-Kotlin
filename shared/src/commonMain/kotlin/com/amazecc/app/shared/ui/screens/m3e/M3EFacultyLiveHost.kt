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
import androidx.compose.runtime.collectAsState
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
import com.amazecc.app.shared.data.FfcsReportData
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
import com.amazecc.app.shared.utils.FacultyUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Driven from the Faculty Schedule Tab (`FacultyScheduleTab.tsx` in ../AmazeCC):
 * - Builds faculty groups directly from the bundled FFCS report ([FfcsReportData.CSV_DATA]),
 *   so the directory always loads instantaneously without fragile VTOP scraping.
 * - Hoists the student's own course faculties (`AppState.courses`) to the top.
 * - Enriches each faculty with cached vitfac reviews, employee IDs, and CDN avatars
 *   (`https://cache.amazecc.com/faculty` + `https://cdn.amazecc.com/faculty/{id}.webp`).
 * - Tapping a faculty opens [M3EFacultyScheduleSheet] with their full weekly schedule.
 */
@Composable
fun M3EFacultyLiveHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val clipboard = LocalClipboardManager.current
    val domain by AppState.domain.collectAsState()
    val enrolledCourses = remember(domain) {
        domain.academics.semesters.values.flatMap { it.courses.values }
    }

    var facultyList by remember { mutableStateOf<List<M3EFacultyMember>>(emptyList()) }
    var availableSchools by remember { mutableStateOf<List<String>>(listOf("All")) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var ui by remember { mutableStateOf(FacultyLiveUiState(selectedSchool = "All")) }

    // 1. Build faculty directory from bundled FFCS report + enrolled courses + vitfac cache.
    LaunchedEffect(ui.refreshTick, enrolledCourses) {
        isLoading = true
        loadError = null
        try {
            val myFacultyNames = buildSet {
                enrolledCourses.forEach { c ->
                    c.faculty?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }

            // Build base groups from FfcsReportData.CSV_DATA off the main thread,
            // and immediately apply any disk-cached vitfac catalog so photos & reviews show on frame 1.
            val (initialMembers, initialSchools) = withContext(Dispatchers.Default) {
                val base = buildFacultyDirectoryFromReport(myFacultyNames)
                val cachedCatalog = AmazeClient.getCachedVitfacCatalog()
                val enriched = if (cachedCatalog.isNotEmpty()) enrichDirectoryWithVitfac(base, cachedCatalog) else base
                val schools = enriched.map { it.school }.filter { it.isNotBlank() && it != "All" }.distinct().sorted()
                enriched to schools
            }
            facultyList = initialMembers
            if (initialSchools.isNotEmpty()) availableSchools = listOf("All") + initialSchools
            isLoading = false

            // Refresh vitfac catalog in background
            val catalog = withContext(Dispatchers.Default) {
                AmazeClient.getVitfacCatalog(forceRefresh = ui.refreshTick > 0 || AmazeClient.getCachedVitfacCatalog().isEmpty())
            }
            if (catalog.isNotEmpty()) {
                val enriched = withContext(Dispatchers.Default) {
                    enrichDirectoryWithVitfac(initialMembers, catalog)
                }
                facultyList = enriched
                val schoolsList = enriched.map { it.school }
                    .filter { it.isNotBlank() && it != "All" }
                    .distinct()
                    .sorted()
                if (schoolsList.isNotEmpty()) availableSchools = listOf("All") + schoolsList
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (facultyList.isEmpty()) {
                loadError = e.message ?: "Failed to load the faculty directory"
            }
            isLoading = false
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
            listOf(member.name, member.email, member.designation, member.school, member.cabin, member.employeeId.orEmpty())
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

private val csvIdRegex = Regex("""\(([^)]+)\)""")

private fun facultyGroupKey(name: String): String {
    val norm = FacultyUtils.normalizeName(name.replace(csvIdRegex, " "))
    return norm.split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ")
}

private fun facultyNamesMatch(a: String, b: String): Boolean {
    val ka = facultyGroupKey(a)
    val kb = facultyGroupKey(b)
    if (ka.isBlank() || kb.isBlank()) return false
    if (ka == kb) return true
    val ta = ka.split(" ").toSet()
    val tb = kb.split(" ").toSet()
    val (smaller, larger) = if (ta.size <= tb.size) ta to tb else tb to ta
    return smaller.size >= 2 && larger.containsAll(smaller)
}

/**
 * Builds the faculty directory from [FfcsReportData.CSV_DATA] (matching `FacultyScheduleTab.tsx`),
 * hoisting [myFacultyNames] to the top (`isMyFaculty = true`).
 */
internal fun buildFacultyDirectoryFromReport(myFacultyNames: Set<String>): List<M3EFacultyMember> {
    data class Acc(
        val key: String,
        val canonical: String,
        val courses: MutableSet<String> = linkedSetOf(),
        val venues: MutableSet<String> = linkedSetOf()
    )

    val groups = linkedMapOf<String, Acc>()
    val lines = FfcsReportData.CSV_DATA.replace("\r", "").split("\n").drop(1)
    for (line in lines) {
        if (line.isBlank()) continue
        var inQuotes = false
        val cols = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cols.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        cols.add(cur.toString().trim())
        if (cols.size < 6) continue

        val code = cols[0].trim()
        val facultyRaw = cols[5].trim()
        val venue = cols.getOrNull(6)?.trim().orEmpty()
        if (facultyRaw.isBlank() || facultyRaw.equals("NIL", ignoreCase = true)) continue

        val cleanName = facultyRaw.replace(csvIdRegex, "").trim()
        val key = facultyGroupKey(cleanName).ifBlank { cleanName.uppercase() }
        val acc = groups.getOrPut(key) { Acc(key = key, canonical = cleanName) }
        if (code.isNotBlank()) acc.courses.add(code)
        if (venue.isNotBlank() && !venue.equals("NIL", ignoreCase = true)) acc.venues.add(venue)
    }

    val members = groups.values.map { acc ->
        val isMine = myFacultyNames.any { myName -> facultyNamesMatch(myName, acc.canonical) }
        M3EFacultyMember(
            id = acc.key,
            name = acc.canonical,
            designation = "",
            school = "All",
            cabin = "",
            email = "",
            intercom = null,
            rating = null,
            coursesTaught = acc.courses.toList(),
            isMyFaculty = isMine,
            timetable = emptyList(),
            isCurrentlyTeaching = false,
            currentSlotInfo = null
        )
    }

    return members.sortedWith(compareByDescending<M3EFacultyMember> { it.isMyFaculty }.thenBy { it.name })
}

/** Enriches report-derived [M3EFacultyMember]s with cached vitfac catalog data (avatar, reviews, dept). */
internal fun enrichDirectoryWithVitfac(
    members: List<M3EFacultyMember>,
    catalog: List<AmazeClient.VitfacCatalogEntry>
): List<M3EFacultyMember> {
    if (catalog.isEmpty()) return members
    val byGroupKey = catalog.associateBy { facultyGroupKey(it.name) }
    val byEmpId = catalog.filter { it.employeeId.isNotBlank() }.associateBy { it.employeeId }

    return members.map { m ->
        val match = m.employeeId?.let { byEmpId[it] }
            ?: byGroupKey[facultyGroupKey(m.name)]
            ?: catalog.firstOrNull { facultyNamesMatch(it.name, m.name) }
        if (match == null) {
            m
        } else {
            val empId = match.employeeId.takeIf { it.isNotBlank() } ?: m.employeeId
            m.copy(
                employeeId = empId,
                imageUrl = match.imageUrl ?: AmazeClient.getFacultyCdnUrl(empId) ?: m.imageUrl,
                wPct = match.wPct ?: m.wPct,
                reviewCount = if (match.reviews > 0) match.reviews else m.reviewCount,
                rating = match.wPct?.let { it / 20.0 } ?: m.rating,
                designation = match.designation.takeIf { it.isNotBlank() } ?: m.designation,
                school = match.dept.takeIf { it.isNotBlank() } ?: m.school,
                email = match.email.takeIf { it.isNotBlank() } ?: m.email,
                intercom = match.intercom.takeIf { it.isNotBlank() } ?: m.intercom
            )
        }
    }
}
