package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.ODEntry
import com.amazecc.app.shared.model.ODListItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.parseViewLink
import kotlinx.serialization.json.*

private const val OD_TOTAL = 40

private val tabLabels = listOf("Overview", "Entries")

private data class ODMetrics(
    val totalODs: Int,
    val labHours: Int,
    val theoryHours: Int,
    val wastedHours: Int,
    val recoveredHours: Int
) {
    val validHours: Int get() = labHours + theoryHours - wastedHours
    val netHours: Int get() = validHours + recoveredHours
}

private data class ODDay(
    val date: String,
    val entries: List<ODEntry>,
    val total: Int
)

private fun extractODEntries(attendance: List<AttendanceItem>): List<ODDay> {
    val rawEntries = mutableListOf<Pair<String, ODEntry>>()
    for (course in attendance) {
        val daily = try {
            val arr = parseViewLink(course.viewLinkRaw)?.jsonArray
            arr?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                date to status
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        for ((date, status) in daily) {
            if (status.equals("On Duty", ignoreCase = true)) {
                val isLab = course.slotName?.startsWith("L") == true
                val hours = if (isLab) 2 else 1
                rawEntries.add(date to ODEntry(course.courseTitle, if (isLab) "LAB" else "TH", hours))
            }
        }
    }

    val grouped = rawEntries.groupBy { it.first }
    return grouped.map { (date, entries) ->
        ODDay(date, entries.map { it.second }, entries.sumOf { it.second.hours })
    }.sortedByDescending { it.date }
}

private fun computeMetrics(odDays: List<ODDay>): ODMetrics {
    var labHours = 0
    var theoryHours = 0
    for (day in odDays) {
        for (entry in day.entries) {
            if (entry.type == "LAB") labHours += entry.hours
            else theoryHours += entry.hours
        }
    }
    return ODMetrics(
        totalODs = odDays.size,
        labHours = labHours,
        theoryHours = theoryHours,
        wastedHours = 0,
        recoveredHours = 0
    )
}

@Composable
fun ODTrackerScreen() {
    val colors = AmazeTheme.colors
    var activeTab by remember { mutableStateOf(0) }
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    val odDays = remember(courses) { extractODEntries(courses) }
    val metrics = remember(odDays) { computeMetrics(odDays) }

    val usedHours = metrics.labHours + metrics.theoryHours
    val usagePercent = if (OD_TOTAL > 0) (usedHours.toFloat() / OD_TOTAL).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "OD Tracker",
            description = "Track on-duty hours",
            showBackButton = true,
            showSyncButton = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "OD Hours",
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        "$usedHours / $OD_TOTAL",
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                usagePercent >= 0.9f -> colors.danger.copy(alpha = 0.1f)
                                usagePercent >= 0.7f -> colors.warning.copy(alpha = 0.1f)
                                else -> colors.success.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(usagePercent * 100).toInt()}%",
                        style = AmazeTheme.typography.subheading.copy(
                            color = when {
                                usagePercent >= 0.9f -> colors.danger
                                usagePercent >= 0.7f -> colors.warning
                                else -> colors.success
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val progressColor = when {
                usagePercent >= 0.9f -> colors.danger
                usagePercent >= 0.7f -> colors.warning
                else -> colors.accent
            }
            LinearProgressIndicator(
                progress = { usagePercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = colors.border.copy(alpha = 0.5f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabLabels.forEachIndexed { idx, label ->
                val isSelected = activeTab == idx
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = bouncySpring()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { activeTab = idx }
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) colors.background else colors.textPrimary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        when (activeTab) {
            0 -> OverviewTab(metrics = metrics, colors = colors)
            1 -> EntriesTab(entries = odDays, colors = colors)
        }
    }
}

@Composable
private fun OverviewTab(
    metrics: ODMetrics,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val usedHours = metrics.labHours + metrics.theoryHours

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Total ODs",
                    value = "${metrics.totalODs}",
                    subtitle = "$OD_TOTAL max",
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    iconColor = colors.accent,
                    colors = colors
                )
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Lab Hours",
                    value = "${metrics.labHours}h",
                    icon = Icons.Rounded.Science,
                    iconColor = colors.info,
                    colors = colors
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Theory Hours",
                    value = "${metrics.theoryHours}h",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    iconColor = colors.warning,
                    colors = colors
                )
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Valid Hours",
                    value = "${metrics.validHours}h",
                    icon = Icons.Rounded.CheckCircle,
                    iconColor = colors.success,
                    colors = colors
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Wasted Hours",
                    value = "${metrics.wastedHours}h",
                    icon = Icons.Rounded.CancelScheduleSend,
                    iconColor = colors.danger,
                    colors = colors
                )
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Recovered Hours",
                    value = "${metrics.recoveredHours}h",
                    icon = Icons.Rounded.Restore,
                    iconColor = colors.info,
                    colors = colors
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "OD Summary",
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Spacer(Modifier.height(2.dp))

                    SummaryRow("Total OD Hours", "${usedHours}h / ${OD_TOTAL}h", colors)
                    SummaryRow("Valid Hours", "${metrics.validHours}h", colors)
                    SummaryRow("Wasted Hours", "${metrics.wastedHours}h", colors)
                    SummaryRow("Recovered Hours", "${metrics.recoveredHours}h", colors)

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                    SummaryRow("Net Impact", "${metrics.netHours}h", colors, isHighlight = true)
                    SummaryRow("Remaining", "${(OD_TOTAL - usedHours).coerceAtLeast(0)}h", colors, isHighlight = true)

                    Spacer(Modifier.height(4.dp))

                    val remainingPercent = ((OD_TOTAL - usedHours).toFloat() / OD_TOTAL).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { remainingPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.success,
                        trackColor = colors.border.copy(alpha = 0.5f),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KPICard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    title,
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Text(
                value,
                style = AmazeTheme.typography.display.copy(
                    color = colors.textPrimary,
                    fontSize = 28.sp
                )
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AmazeTheme.typography.body.copy(
                color = if (isHighlight) colors.textPrimary else colors.textSecondary,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
            )
        )
        Text(
            value,
            style = AmazeTheme.typography.body.copy(
                color = if (isHighlight) colors.accent else colors.textPrimary,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun EntriesTab(
    entries: List<ODDay>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.EventBusy,
                    null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No OD entries found",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sync attendance data to check for On Duty days",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(entries) { day ->
                ODDateGroup(group = day, colors = colors)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ODDateGroup(
    group: ODDay,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        group.date,
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${group.total}h",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            HorizontalDivider(color = colors.border)

            group.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val typeIcon = if (entry.type == "LAB") Icons.Rounded.Science else Icons.AutoMirrored.Rounded.MenuBook
                        val typeColor = if (entry.type == "LAB") colors.info else colors.warning
                        Icon(
                            typeIcon,
                            null,
                            tint = typeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                entry.title,
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                entry.type,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (entry.type) {
                                    "LAB" -> colors.info.copy(alpha = 0.1f)
                                    else -> colors.warning.copy(alpha = 0.1f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${entry.hours}h",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (entry.type == "LAB") colors.info else colors.warning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
