package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.FacultyFreeSlotsUtil

/** Shared LIFO id: the host must push/pop this or every dismissal route no-ops. */
internal const val FACULTY_SCHEDULE_OVERLAY_ID = "faculty-schedule"

/**
 * D-163: stateless per-faculty schedule sheet. Everything rendered comes from the bundled
 * FFCS report via [FacultyFreeSlotsUtil]; there are no placeholder classes and no live
 * presence claim. The whole body is height-capped and scrolled so the sheet chrome stays
 * on screen, matching [M3ETimetableSheet]'s pattern.
 */
@Composable
fun M3EFacultyScheduleSheet(
    faculty: M3EFacultyMember,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val schedule = remember(faculty.id, faculty.name) {
        FacultyFreeSlotsUtil.getFacultySchedule(facultyMemberToScheduleProfile(faculty))
    }
    val offerings = remember(schedule) { buildFacultyOfferings(schedule) }
    val grid = remember(schedule) { buildFacultyWeeklyGrid(schedule) }
    val days = FacultyFreeSlotsUtil.workingDays
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        modifier = modifier,
        title = "${faculty.name}'s Schedule",
        overlayId = FACULTY_SCHEDULE_OVERLAY_ID
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "Weekly grid from the bundled FFCS report · ${schedule.occupiedSlots.size} occupied periods",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (offerings.isEmpty()) {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "No scheduled classes found",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Real working-day selector; every chip count comes from the derivation.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            days.forEachIndexed { index, day ->
                                val classCount = offerings.count { it.day == day }
                                FilterChip(
                                    selected = index == selectedDayIndex,
                                    onClick = { selectedDayIndex = index },
                                    label = { Text("$day ($classCount)", fontSize = 11.sp) }
                                )
                            }
                        }

                        // Days x published periods. Each cell is class / free / unknown —
                        // unknown is rendered "—" and never upgraded to a claim.
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val selectedDay = days.getOrNull(selectedDayIndex) ?: days.first()
                                Text(
                                    text = "$selectedDay · WEEKLY GRID",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                grid.filter { it.day == selectedDay }.forEach { cell ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = cell.timeRange,
                                            color = colors.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val detail = when {
                                            cell.courseCode != null -> cell.courseCode
                                            cell.isFree -> "Free"
                                            else -> "—"
                                        }
                                        Text(
                                            text = detail,
                                            color = if (cell.courseCode != null) colors.primary else colors.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = if (cell.courseCode != null) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Real offerings table. The FFCS report has no venue column, so venue
                        // is shown as unpublished rather than filled with a guessed room.
                        Text(
                            text = "OFFERINGS",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TableRow(
                                    left = "COURSE",
                                    type = "TYPE",
                                    slot = "SLOT",
                                    venue = "VENUE",
                                    isHeader = true
                                )
                                offerings.forEach { offering ->
                                    TableRow(
                                        left = buildString {
                                            append(offering.courseCode)
                                            if (offering.courseTitle.isNotBlank()) {
                                                append('\n').append(offering.courseTitle)
                                            }
                                        },
                                        type = offering.type,
                                        slot = offering.slot,
                                        venue = offering.venue.ifBlank { "—" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    left: String,
    type: String,
    slot: String,
    venue: String,
    isHeader: Boolean = false
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isHeader) 0.dp else 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = left,
            color = if (isHeader) colors.onSurfaceVariant else colors.onSurface,
            fontSize = if (isHeader) 10.sp else 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = if (isHeader) 1 else 2,
            modifier = Modifier.weight(1.6f)
        )
        Text(
            text = type,
            color = if (isHeader) colors.onSurfaceVariant else colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = slot,
            color = if (isHeader) colors.onSurfaceVariant else colors.primary,
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = venue,
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(0.8f)
        )
    }
}
