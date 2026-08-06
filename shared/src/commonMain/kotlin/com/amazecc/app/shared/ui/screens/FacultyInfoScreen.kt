package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.utils.FacultyFreeSlotsUtil
import com.amazecc.app.shared.utils.FacultySlot
import kotlinx.coroutines.launch

@Composable
fun FacultyInfoScreen() {
    val colors = AmazeTheme.colors
    var schools by remember { mutableStateOf<List<FacultySchool>>(emptyList()) }
    var loadingSchools by remember { mutableStateOf(true) }
    var selectedSchoolId by remember { mutableStateOf<String?>(null) }
    var faculties by remember { mutableStateOf<List<FacultyProfile>>(emptyList()) }
    var loadingFaculties by remember { mutableStateOf(false) }
    var selectedFaculty by remember { mutableStateOf<FacultyProfile?>(null) }
    var searchTerm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        error = null
        try {
            val res = AmazeClient.getFacultySchools()
            if (res.success && res.schools.isNotEmpty()) {
                schools = res.schools
                val firstId = res.schools.first().id
                selectedSchoolId = firstId
                loadingFaculties = true
                try {
                    val scrapeRes = AmazeClient.postFacultyScrape(firstId)
                    if (scrapeRes.success) faculties = scrapeRes.faculties
                    else error = scrapeRes.error
                } catch (e: Exception) { error = e.message }
                loadingFaculties = false
            } else {
                error = res.error ?: "Failed to load schools"
            }
        } catch (e: Exception) {
            error = e.message
        }
        loadingSchools = false
    }

    val sf = selectedFaculty
    if (sf != null) {
        FacultyDetailScreen(
            faculty = sf,
            onBack = { selectedFaculty = null }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (loadingSchools) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderSpacer()
                // School Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schools.forEach { school ->
                        val isSelected = selectedSchoolId == school.id

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else colors.surface)
                                .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                                .clickable(
                                    onClick = {
                                        if (school.id != selectedSchoolId) {
                                            selectedSchoolId = school.id
                                            faculties = emptyList()
                                            searchTerm = ""
                                            error = null
                                            loadingFaculties = true
                                            scope.launch {
                                                try {
                                                    val res = AmazeClient.postFacultyScrape(school.id)
                                                    if (res.success) faculties = res.faculties
                                                    else error = res.error
                                                } catch (e: Exception) { error = e.message }
                                                loadingFaculties = false
                                            }
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                school.school_name,
                                style = AmazeTheme.typography.body.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.background else colors.textPrimary
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Error
                val err = error
                if (err != null) {
                    AmazeCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = colors.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(err, color = colors.danger, style = AmazeTheme.typography.body.copy())
                        }
                    }
                }

                // Search
                if (selectedSchoolId != null) {
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { searchTerm = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search by name, ID, email...", style = AmazeTheme.typography.body.copy(color = colors.textMuted)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent.copy(alpha = 0.5f),
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accent
                        )
                    )
                }

                // Faculty list
                val filtered = remember(faculties, searchTerm) {
                    if (searchTerm.isBlank()) faculties
                    else faculties.filter { f ->
                        f.name.contains(searchTerm, ignoreCase = true) ||
                        f.employeeId.contains(searchTerm, ignoreCase = true) ||
                        f.email.contains(searchTerm, ignoreCase = true) ||
                        f.designation.contains(searchTerm, ignoreCase = true)
                    }
                }

                if (loadingFaculties) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else if (selectedSchoolId != null) {
                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                Text(
                                    if (faculties.isEmpty()) "No faculty data available for this school"
                                    else "No faculty found matching \"$searchTerm\"",
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                        ) {
                            items(filtered, key = { it.id }) { faculty ->
                                FacultyCard(
                                    faculty = faculty,
                                    onClick = { selectedFaculty = faculty },
                                    onDetailFetched = { profile ->
                                        faculties = faculties.map {
                                            if (it.id == profile.id) {
                                                it.copy(
                                                    designation = profile.designation.ifBlank { it.designation },
                                                    email = profile.email.ifBlank { it.email },
                                                    intercom = profile.intercom.ifBlank { it.intercom }
                                                )
                                            } else it
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedSchoolId == null && !loadingSchools) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.School, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("Select a school to view its faculty directory", color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        
        ScreenHeader(title = "Faculty Info", description = "Global Faculty Directory", showBackButton = true)
    }
}

@Composable
private fun FacultyCard(faculty: FacultyProfile, onClick: () -> Unit, onDetailFetched: (FacultyProfile) -> Unit) {
    val colors = AmazeTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var loadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, faculty.email) {
        if (expanded && faculty.email.isBlank() && faculty.employeeId.isNotBlank()) {
            loadingDetail = true
            val profile = AmazeClient.getFacultyProfile(faculty.employeeId)
            loadingDetail = false
            if (profile != null) onDetailFetched(profile)
        }
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                        Text(
                            faculty.name.take(2).uppercase(),
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                        )
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(faculty.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(faculty.designation, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.textMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                HorizontalDivider(color = colors.border.copy(alpha = 0.3f))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                if (faculty.email.isNotBlank()) {
                    Text("Email", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text(faculty.email, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                } else if (loadingDetail) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.textMuted
                        )
                        Spacer(Modifier.width(AmazeTheme.spacing.xs))
                        Text("Loading details...", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                }
                AmazeButton(
                    text = "View Schedule",
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.SECONDARY
                )
            }
        }
    }
}

@Composable
fun FacultyDetailScreen(
    faculty: FacultyProfile,
    onBack: () -> Unit
) {
    val colors = AmazeTheme.colors

    ScreenHeader(
        title = faculty.name,
        description = faculty.designation,
        showBackButton = true,
        showSyncButton = false,
        onBackOverride = onBack
    )

    DisposableEffect(Unit) {
        onDispose {
            AppState.clearHeaderBackOverride()
        }
    }

    val schedule = remember(faculty) {
        FacultyFreeSlotsUtil.getFacultySchedule(faculty)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
            item { HeaderSpacer() }

            // Info card
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Faculty Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        DetailRow("Name", faculty.name, colors)
                        DetailRow("Designation", faculty.designation, colors)
                        if (faculty.employeeId.isNotBlank()) DetailRow("Employee ID", faculty.employeeId, colors)
                        if (faculty.email.isNotBlank()) DetailRow("Email", faculty.email, colors, isEmail = true)
                        if (faculty.intercom.isNotBlank()) DetailRow("Intercom", faculty.intercom, colors)
                    }
                }
            }

            // Schedule header
            item(key = "schedule_header") {
                Text("Weekly Schedule", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Tap a slot to see course details", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            }

            // Weekly grid
            item(key = "schedule_grid_${faculty.id}") {
                val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI")
                val timePeriods = remember { FacultyFreeSlotsUtil.getAllTimePeriods() }
                val dayLabels = mapOf("MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri")
                val freeColor = colors.success
                val occupiedSlotKeys = remember(schedule) {
                    schedule.occupiedSlots.map { "${it.day}:${it.timeRange}" }.toSet()
                }
                val occupiedSlotByKey = remember(schedule) {
                    schedule.occupiedSlots.associateBy { "${it.day}:${it.timeRange}" }
                }

                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(52.dp))
                            weekDays.forEach { day ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(dayLabels[day] ?: day, style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))

                        timePeriods.forEach { time ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(time, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                }
                                weekDays.forEach { day ->
                                    val isOccupied = occupiedSlotKeys.contains("$day:$time")
                                    val slot = if (isOccupied) occupiedSlotByKey["$day:$time"] else null
                                    val cellColor = if (slot != null) colors.danger.copy(alpha = 0.18f) else freeColor.copy(alpha = 0.12f)
                                    val borderColor = if (slot != null) colors.danger.copy(alpha = 0.35f) else freeColor.copy(alpha = 0.25f)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(1.5.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                            .background(cellColor)
                                            .border(0.5.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.xs)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (slot != null) {
                                            Text(slot.slotCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(freeColor.copy(alpha = 0.2f)).border(0.5.dp, freeColor.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.xs)))
                                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                Text("Free", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.danger.copy(alpha = 0.2f)).border(0.5.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.xs)))
                                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                Text("Occupied", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }

            // Free slots summary
            item {
                Text("Free Slots", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            }

            if (schedule.freeSlots.isEmpty()) {
                item {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Info, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            Text("No free slot data available. Sync attendance data to see schedule.", color = colors.textSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                val dayLabels = mapOf("MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday", "FRI" to "Friday")
                FacultyFreeSlotsUtil.workingDays.forEach { day ->
                    val free = schedule.freeSlots[day]
                    if (free != null && free.isNotEmpty()) {
                        item(key = "free_$day") {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                            .background(colors.success.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(day.take(2), style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.success))
                                    }
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(dayLabels[day] ?: day, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text(free.joinToString(", "), style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Occupied slots detail
            if (schedule.occupiedSlots.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("Occupied Slots (${schedule.occupiedSlots.size})", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }

                schedule.occupiedSlots.forEachIndexed { index, slot ->
                    item(key = "occ_${slot.day}_${slot.slotCode}_${index}") {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(colors.danger.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(slot.day.take(2), style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.danger))
                                }
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(slot.timeRange, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("${slot.courseCode} - ${slot.courseTitle}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                AmazeBadge(text = slot.slotCode, variant = BadgeVariant.WARNING)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, colors: AmazeColors, isEmail: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
        Text(
            value,
            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = if (isEmail) colors.accent else colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
