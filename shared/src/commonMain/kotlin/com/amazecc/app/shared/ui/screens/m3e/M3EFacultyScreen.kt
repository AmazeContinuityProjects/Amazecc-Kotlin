package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.utils.toImageBitmap
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.domain.overhaul.M3EFacultyTimetableSlot
import com.amazecc.app.shared.ui.m3e.components.M3EAccordionChevron
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Faculty Directory & Schedule.
 * Combined screen: directory list + inline timetable per faculty.
 * 100% Stateless: accepts [M3EFacultyState] and emits [M3EFacultyAction].
 * Mobile-first LazyColumn layout.
 */
@Composable
fun M3EFacultyScreen(
    state: M3EFacultyState,
    onAction: (M3EFacultyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    var reviewSheetFaculty by remember { mutableStateOf<M3EFacultyMember?>(null) }

    LaunchedEffect(reviewSheetFaculty != null) {
        if (reviewSheetFaculty != null) AppState.pushOverlay(FACULTY_REVIEW_OVERLAY_ID)
        else AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item(key = "faculty_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Faculty",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Directory, cabin locator & teaching schedule",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Search Bar
            item(key = "search_bar") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EFacultyAction.UpdateSearch(it)) },
                    placeholder = { Text("Search name, cabin, or course…", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    ),
                    singleLine = true
                )
            }

            // School Filter Chips — Material FilterChip exposes role + selected state to TalkBack.
            item(key = "school_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableSchools) { school ->
                        FilterChip(
                            selected = school == state.selectedSchool,
                            onClick = { onAction(M3EFacultyAction.SelectSchool(school)) },
                            label = { Text(school, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Faculty Results Count
            if (state.filteredFaculty.isNotEmpty()) {
                item(key = "result_count") {
                    Text(
                        text = "${state.filteredFaculty.size} faculty member${if (state.filteredFaculty.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Faculty List or Empty State
            if (state.filteredFaculty.isEmpty()) {
                item(key = "empty_faculty") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No faculty found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Try adjusting your search or filter",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val myFaculties = state.filteredFaculty.filter { it.isMyFaculty }
                val otherFaculties = state.filteredFaculty.filter { !it.isMyFaculty }

                if (myFaculties.isNotEmpty()) {
                    item(key = "section_my_faculty") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your course faculties (${myFaculties.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = "Yours first",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            )
                        }
                    }
                    items(
                        items = myFaculties,
                        key = { "my_${it.id}" }
                    ) { faculty ->
                        val isExpanded = state.expandedFacultyId == faculty.id
                        M3EFacultyCard(
                            faculty = faculty,
                            isExpanded = isExpanded,
                            onToggleExpand = { onAction(M3EFacultyAction.ToggleExpand(faculty.id)) },
                            onCopyEmail = { onAction(M3EFacultyAction.CopyEmail(faculty.email)) },
                            onOpenSchedule = { onAction(M3EFacultyAction.ToggleShowTimetable(faculty.id)) },
                            onOpenReviews = { reviewSheetFaculty = faculty }
                        )
                    }
                }

                if (otherFaculties.isNotEmpty()) {
                    item(key = "section_other_faculty") {
                        Text(
                            text = if (myFaculties.isNotEmpty()) "All other faculty (${otherFaculties.size})"
                            else "All faculty (${otherFaculties.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(
                        items = otherFaculties,
                        key = { it.id }
                    ) { faculty ->
                        val isExpanded = state.expandedFacultyId == faculty.id
                        M3EFacultyCard(
                            faculty = faculty,
                            isExpanded = isExpanded,
                            onToggleExpand = { onAction(M3EFacultyAction.ToggleExpand(faculty.id)) },
                            onCopyEmail = { onAction(M3EFacultyAction.CopyEmail(faculty.email)) },
                            onOpenSchedule = { onAction(M3EFacultyAction.ToggleShowTimetable(faculty.id)) },
                            onOpenReviews = { reviewSheetFaculty = faculty }
                        )
                    }
                }
            }

            // Bottom spacing for FAB / nav bar clearance (140dp)
            item(key = "bottom_spacer") { Spacer(Modifier.height(140.dp)) }
        }

        reviewSheetFaculty?.let { fac ->
            M3EFacultyReviewSheet(
                faculty = fac,
                onDismiss = { reviewSheetFaculty = null }
            )
        }
    }
}

@Composable
private fun M3EFacultyCard(
    faculty: M3EFacultyMember,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyEmail: () -> Unit,
    onOpenSchedule: () -> Unit = {},
    onOpenReviews: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    var reviewSheetFaculty by remember { mutableStateOf<M3EFacultyMember?>(null) }

    LaunchedEffect(reviewSheetFaculty != null) {
        if (reviewSheetFaculty != null) AppState.pushOverlay(FACULTY_REVIEW_OVERLAY_ID)
        else AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID) }
    }
    var avatarBitmap by remember(faculty.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(faculty.imageUrl) {
        val url = faculty.imageUrl
        if (!url.isNullOrBlank()) {
            avatarBitmap = AmazeClient.getImageBytes(url)?.toImageBitmap()
        }
    }

    M3ESurfaceCard(
        containerLevel = if (isExpanded) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        onClick = onToggleExpand,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Top Row: Cached Photo/Initials + Name/Badges/Courses + Expand chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Cached Photo or Initials Avatar
                    val initials = faculty.name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("")

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (faculty.isCurrentlyTeaching) colors.secondaryContainer
                                else colors.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val bm = avatarBitmap
                        if (bm != null) {
                            Image(
                                bitmap = bm,
                                contentDescription = faculty.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = initials.ifBlank { "F" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (faculty.isCurrentlyTeaching) colors.onSecondaryContainer
                                else colors.onPrimaryContainer
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = faculty.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (faculty.isMyFaculty) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(colors.primaryContainer)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Yours",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }
                            if (!faculty.employeeId.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.surfaceContainerHighest)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "#${faculty.employeeId}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            val wPct = faculty.wPct
                            if (wPct != null && faculty.reviewCount > 0) {
                                val pillColor = when {
                                    wPct >= 70 -> colors.success
                                    wPct >= 40 -> colors.warning
                                    else -> colors.error
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(pillColor.copy(alpha = 0.14f))
                                        .clickable { onOpenReviews() }
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$wPct% W (${faculty.reviewCount})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pillColor
                                    )
                                }
                            }
                        }
                        val coursePreview = if (faculty.coursesTaught.isNotEmpty()) {
                            val shown = faculty.coursesTaught.take(3).joinToString(" · ")
                            val rest = faculty.coursesTaught.size - 3
                            if (rest > 0) "$shown · +$rest" else shown
                        } else ""
                        val subtitle = listOfNotNull(
                            faculty.designation.takeIf { it.isNotBlank() },
                            faculty.school.takeIf { it.isNotBlank() && it != "All" },
                            coursePreview.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                M3EAccordionChevron(
                    isExpanded = isExpanded,
                    contentDescription = if (isExpanded) "Collapse" else "Expand schedule",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Cabin & Email quick-info row (only when at least one is published)
            if (faculty.cabin.isNotBlank() || faculty.email.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (faculty.cabin.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MeetingRoom,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = faculty.cabin,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (faculty.email.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = faculty.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                            IconButton(
                                onClick = { onCopyEmail() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Email",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Currently teaching banner
            faculty.currentSlotInfo?.let { info ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = info,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSecondaryContainer
                        )
                    }
                }
            }

            // Expandable: Intercom + Courses + Timetable
            AnimatedVisibility(
                visible = isExpanded,
                // D-122: M3E specs replace the defaults. The composable M3ETheme.motion
                // provider reads LocalMotionReduced directly, so reduced motion applies
                // on the first frame without the root-fallback lag.
                enter = expandVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeIn(animationSpec = M3ETheme.motion.effectsDefault()),
                exit = shrinkVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeOut(animationSpec = M3ETheme.motion.effectsDefault())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenSchedule,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = onOpenReviews,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("vitfac Reviews", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Vitfac Reviews & Lore (fetched lazily when expanded)
                    val empId = faculty.employeeId
                    if (!empId.isNullOrBlank()) {
                        var vitfacDetail by remember(empId) { mutableStateOf<AmazeClient.VitfacProfileDetail?>(null) }
                        LaunchedEffect(empId, isExpanded) {
                            if (isExpanded && vitfacDetail == null) {
                                vitfacDetail = AmazeClient.getVitfacProfile(empId)
                            }
                        }
                        val detail = vitfacDetail
                        if (detail != null && detail.reviews > 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerHighest.copy(alpha = 0.6f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "VITFAC REVIEWS (${detail.reviews} REVIEWS · ${detail.wCount}W / ${detail.lCount}L)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant,
                                    letterSpacing = 0.6.sp
                                )
                                if (detail.lecture != null || detail.da != null || detail.vibe != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "Lecture" to detail.lecture,
                                            "DA / Grading" to detail.da,
                                            "Vibe" to detail.vibe
                                        ).forEach { (label, score) ->
                                            if (score != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(colors.surface)
                                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                                        Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
                                                        Text("${((score * 10).toInt() / 10.0)}/5", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (detail.greenLore.isNotEmpty()) {
                                    Text(
                                        text = "✓ " + detail.greenLore.take(4).joinToString(" · ") { "${it.text} (${it.count})" },
                                        fontSize = 11.sp,
                                        color = colors.success,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (detail.redLore.isNotEmpty()) {
                                    Text(
                                        text = "✕ " + detail.redLore.take(4).joinToString(" · ") { "${it.text} (${it.count})" },
                                        fontSize = 11.sp,
                                        color = colors.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Intercom
                    faculty.intercom?.let { ext ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Intercom: $ext",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    // Courses Taught Pills
                    if (faculty.coursesTaught.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "COURSES TAUGHT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                faculty.coursesTaught.take(5).forEach { code ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surfaceContainerHighest)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = code,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Timetable Section
                    if (faculty.timetable.isNotEmpty()) {
                        FacultyTimetableSection(slots = faculty.timetable)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerLowest)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No timetable data available",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FacultyTimetableSection(
    slots: List<M3EFacultyTimetableSlot>,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    var reviewSheetFaculty by remember { mutableStateOf<M3EFacultyMember?>(null) }

    LaunchedEffect(reviewSheetFaculty != null) {
        if (reviewSheetFaculty != null) AppState.pushOverlay(FACULTY_REVIEW_OVERLAY_ID)
        else AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(FACULTY_REVIEW_OVERLAY_ID) }
    }
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
    val slotsByDay = days.associateWith { day -> slots.filter { it.dayOfWeek == day } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "TEACHING SCHEDULE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        days.forEach { day ->
            val daySlots = slotsByDay[day].orEmpty()
            if (daySlots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Day header
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    daySlots.forEach { slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceContainerLowest)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slot.courseCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = slot.courseTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = slot.slotCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = slot.roomNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createMockFacultyState() = M3EFacultyState(
    facultyList = listOf(
        M3EFacultyMember(
            id = "fac_1",
            name = "Prof. Sundar N",
            designation = "Associate Professor Grade 1",
            school = "SCOPE",
            cabin = "SJT 412-A",
            email = "sundar.n@vit.ac.in",
            intercom = "1412",
            coursesTaught = listOf("CSE3002", "CSE1007"),
            isMyFaculty = true,
            isCurrentlyTeaching = true,
            currentSlotInfo = "CSE3002 in SJT-512 (Slot A1)",
            timetable = listOf(
                M3EFacultyTimetableSlot("A1", "CSE3002", "Full Stack Development", "SJT-512", "MON"),
                M3EFacultyTimetableSlot("B1", "CSE3002", "Full Stack Development", "SJT-512", "TUE"),
                M3EFacultyTimetableSlot("C1", "CSE1007", "Java Programming", "SJT-603", "WED"),
                M3EFacultyTimetableSlot("D1", "CSE1007", "Java Programming", "SJT-603", "THU"),
                M3EFacultyTimetableSlot("E1", "CSE3002", "Full Stack Development", "SJT-512", "FRI")
            )
        ),
        M3EFacultyMember(
            id = "fac_2",
            name = "Dr. Anita R",
            designation = "Professor Higher Academic Grade",
            school = "SCOPE",
            cabin = "TT 314",
            email = "anita.r@vit.ac.in",
            intercom = "1314",
            coursesTaught = listOf("MAT2001", "MAT1011"),
            isMyFaculty = false,
            timetable = listOf(
                M3EFacultyTimetableSlot("A1", "MAT2001", "Probability & Statistics", "MB-203", "MON"),
                M3EFacultyTimetableSlot("C1", "MAT1011", "Calculus", "MB-204", "WED")
            )
        )
    ),
    filteredFaculty = listOf(
        M3EFacultyMember(
            id = "fac_1",
            name = "Prof. Sundar N",
            designation = "Associate Professor Grade 1",
            school = "SCOPE",
            cabin = "SJT 412-A",
            email = "sundar.n@vit.ac.in",
            intercom = "1412",
            coursesTaught = listOf("CSE3002", "CSE1007"),
            isMyFaculty = true,
            isCurrentlyTeaching = true,
            currentSlotInfo = "CSE3002 in SJT-512 (Slot A1)",
            timetable = listOf(
                M3EFacultyTimetableSlot("A1", "CSE3002", "Full Stack Development", "SJT-512", "MON"),
                M3EFacultyTimetableSlot("B1", "CSE3002", "Full Stack Development", "SJT-512", "TUE"),
                M3EFacultyTimetableSlot("C1", "CSE1007", "Java Programming", "SJT-603", "WED")
            )
        )
    ),
    expandedFacultyId = "fac_1"
)

@Preview
@Composable
private fun M3EFacultyScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFacultyScreen(state = createMockFacultyState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EFacultyScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EFacultyScreen(state = createMockFacultyState(), onAction = {})
    }
}
