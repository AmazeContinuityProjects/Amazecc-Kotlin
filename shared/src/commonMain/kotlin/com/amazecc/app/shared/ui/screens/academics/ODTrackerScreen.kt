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
import com.amazecc.app.shared.model.ODTrackedEntry
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.utils.parseViewLink
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

private const val OD_TOTAL = 40

private val odTrackerSerializer =
    MapSerializer(String.serializer(), MapSerializer(String.serializer(), ODTrackedEntry.serializer()))

private val statusMapSerializer =
    MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

private val tabLabels = listOf("Overview", "Entries")

private data class ODMetrics(
    val totalODs: Int,
    val labHours: Int,
    val theoryHours: Int,
    val wastedHours: Int,
    val recoveredHours: Int
) {
    val validHours: Int get() = labHours + theoryHours - wastedHours + recoveredHours
    val netHours: Int get() = validHours
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
            val normalizedStatus = status.trim().lowercase()
            val isOD = normalizedStatus == "on duty" || normalizedStatus == "od" || normalizedStatus == "onduty"
            if (isOD) {
                val isLab = course.slotName?.startsWith("L") == true
                val hours = if (isLab) 2 else 1
                rawEntries.add(date to ODEntry(course.courseTitle, if (isLab) "LAB" else "TH", hours, course.courseCode))
            }
        }
    }

    val grouped = rawEntries.groupBy { it.first }
    return grouped.map { (date, entries) ->
        ODDay(date, entries.map { it.second }, entries.sumOf { it.second.hours })
    }.sortedByDescending { it.date }
}

private fun computeMetrics(odDays: List<ODDay>, trackerState: Map<String, Map<String, ODTrackedEntry>>): ODMetrics {
    var labHours = 0
    var theoryHours = 0
    var wastedHours = 0
    var recoveredHours = 0
    for (day in odDays) {
        for (entry in day.entries) {
            if (entry.type == "LAB") labHours += entry.hours
            else theoryHours += entry.hours
            // Check tracker for this entry
            entry.courseCode?.let { code ->
                val tracked = trackerState[day.date]?.get(code)
                when (tracked?.status) {
                    "wasted" -> wastedHours += entry.hours
                    "recovered" -> recoveredHours += entry.hours
                }
            }
        }
    }
    return ODMetrics(
        totalODs = odDays.size,
        labHours = labHours,
        theoryHours = theoryHours,
        wastedHours = wastedHours,
        recoveredHours = recoveredHours
    )
}

@Composable
fun ODTrackerScreen() {
    val colors = AmazeTheme.colors
    var activeTab by remember { mutableStateOf(0) }
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    // Tracker state: date -> courseCode -> ODTrackedEntry
    val trackerState = remember {
        mutableStateOf<Map<String, Map<String, ODTrackedEntry>>>(
            try {
                val json = Json { ignoreUnknownKeys = true }
                val raw = SettingsManager.getODTrackerState()
                json.decodeFromString<Map<String, Map<String, ODTrackedEntry>>>(raw)
            } catch (_: Exception) { emptyMap() }
        )
    }

    // Previous status map for auto-detection: date -> courseCode -> status
    val prevStatusMap = remember {
        mutableStateOf<Map<String, Map<String, String>>>(
            try {
                val json = Json { ignoreUnknownKeys = true }
                val raw = SettingsManager.getString("od_prev_status_map", "{}")
                json.decodeFromString<Map<String, Map<String, String>>>(raw)
            } catch (_: Exception) { emptyMap() }
        )
    }

    val odDays = remember(courses) { extractODEntries(courses) }

    // Build current status map for auto-detection
    val currentStatusMap = remember(courses) {
        val map = mutableMapOf<String, MutableMap<String, String>>()
        for (course in courses) {
            val daily = try {
                val arr = parseViewLink(course.viewLinkRaw)?.jsonArray
                arr?.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    date to status.trim().lowercase()
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            for ((date, status) in daily) {
                map.getOrPut(date) { mutableMapOf() }[course.courseCode] = status
            }
        }
        // Convert to immutable map for comparison and serialization
        map.mapValues { it.value.toMap() }.toMap()
    }

    // Auto-detect wasted/recovered on attendance change
    LaunchedEffect(currentStatusMap) {
        val prev = prevStatusMap.value
        if (prev.isNotEmpty() && currentStatusMap != prev) {
            var trackerUpdated = false
            val newTracker = trackerState.value.mapValues { it.value.toMutableMap() }.toMutableMap()
            for ((date, courseStatuses) in currentStatusMap) {
                val oldCourseStatuses = prev[date] ?: emptyMap()
                for ((courseCode, newStatus) in courseStatuses) {
                    val oldStatus = oldCourseStatuses[courseCode]
                    if (oldStatus != null && oldStatus != newStatus) {
                        val course = courses.find { it.courseCode == courseCode }
                        val courseTitle = course?.courseTitle ?: ""
                        val courseType = course?.courseType ?: ""
                        val slotName = course?.slotName
                        if (!newTracker.containsKey(date)) newTracker[date] = mutableMapOf()

                        if (oldStatus == "present" && (newStatus == "on duty" || newStatus == "od" || newStatus == "onduty")) {
                            newTracker[date]!![courseCode] = ODTrackedEntry(courseTitle, courseType, slotName, "wasted")
                            trackerUpdated = true
                        } else if ((oldStatus == "on duty" || oldStatus == "od" || oldStatus == "onduty") && newStatus == "present") {
                            val existing = newTracker[date]?.get(courseCode)
                            if (existing != null && existing.status == "wasted") {
                                newTracker[date]!![courseCode] = existing.copy(status = "recovered")
                                trackerUpdated = true
                            }
                        }
                    }
                }
            }
            if (trackerUpdated) {
                val json = Json { ignoreUnknownKeys = true }
                SettingsManager.saveODTrackerState(json.encodeToString(odTrackerSerializer, newTracker))
                trackerState.value = newTracker
            }
        }

        // Persist current status map for next comparison
        val json = Json { ignoreUnknownKeys = true }
        SettingsManager.setString("od_prev_status_map", json.encodeToString(statusMapSerializer, currentStatusMap))
    }

    val metrics = remember(odDays, trackerState.value) { computeMetrics(odDays, trackerState.value) }

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

        HeaderSpacer()

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
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
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

            Spacer(Modifier.height(AmazeTheme.spacing.sm))

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
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs)),
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
            1 -> EntriesTab(
                entries = odDays,
                colors = colors,
                trackerState = trackerState.value,
                onTrackerChange = { date, courseCode, currentStatus ->
                    val nextStatus = when (currentStatus) {
                        "none" -> "wasted"
                        "wasted" -> "recovered"
                        "recovered" -> "none"
                        else -> "wasted"
                    }
                    val newTracker = trackerState.value.mapValues { it.value.toMutableMap() }.toMutableMap()
                    if (!newTracker.containsKey(date)) newTracker[date] = mutableMapOf()
                    if (nextStatus == "none") {
                        newTracker[date]?.remove(courseCode)
                        if (newTracker[date]?.isEmpty() == true) newTracker.remove(date)
                    } else {
                        val course = courses.find { it.courseCode == courseCode }
                        val courseTitle = course?.courseTitle ?: ""
                        val courseType = course?.courseType ?: ""
                        val slotName = course?.slotName
                        newTracker[date]!![courseCode] = ODTrackedEntry(courseTitle, courseType, slotName, nextStatus)
                    }
                    val json = Json { ignoreUnknownKeys = true }
                    SettingsManager.saveODTrackerState(json.encodeToString(odTrackerSerializer, newTracker))
                    trackerState.value = newTracker
                }
            )
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
        contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
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
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
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

                    Spacer(Modifier.height(AmazeTheme.spacing.xs))

                    val remainingPercent = ((OD_TOTAL - usedHours).toFloat() / OD_TOTAL).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { remainingPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                        color = colors.success,
                        trackColor = colors.border.copy(alpha = 0.5f),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
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
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
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
    colors: com.amazecc.app.shared.theme.AmazeColors,
    trackerState: Map<String, Map<String, ODTrackedEntry>>,
    onTrackerChange: (String, String, String) -> Unit
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
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Text(
                    "No OD entries found",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text(
                    "Sync attendance data to check for On Duty days",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                Text(
                    "Tap an entry to mark Wasted / Recovered",
                    style = AmazeTheme.typography.caption.copy(color = colors.accent)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            items(entries, key = { it.date }) { day ->
                ODDateGroup(group = day, colors = colors, trackerState = trackerState, onTrackerChange = onTrackerChange)
            }
            item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
        }
    }
}

@Composable
private fun ODDateGroup(
    group: ODDay,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    trackerState: Map<String, Map<String, ODTrackedEntry>>,
    onTrackerChange: (String, String, String) -> Unit
) {
    val dayTracker = trackerState[group.date] ?: emptyMap()
    val hasWasted = dayTracker.values.any { it.status == "wasted" }
    val allWasted = dayTracker.values.all { it.status == "wasted" }
    val dayStatus = when {
        allWasted && dayTracker.isNotEmpty() -> "wasted"
        hasWasted -> "partial wasted"
        else -> "valid"
    }

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
                    // Day status badge
                    if (dayStatus != "valid") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(
                                    when (dayStatus) {
                                        "wasted" -> colors.danger.copy(alpha = 0.15f)
                                        "partial wasted" -> colors.warning.copy(alpha = 0.15f)
                                        else -> colors.success.copy(alpha = 0.15f)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                when (dayStatus) {
                                    "wasted" -> "Wasted OD"
                                    "partial wasted" -> "Partial Wasted"
                                    else -> "Valid OD"
                                },
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = when (dayStatus) {
                                        "wasted" -> colors.danger
                                        "partial wasted" -> colors.warning
                                        else -> colors.success
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
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
                val tracked = entry.courseCode?.let { code -> dayTracker[code] }
                val isWasted = tracked?.status == "wasted"
                val isRecovered = tracked?.status == "recovered"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackerChange(group.date, entry.courseCode ?: "", tracked?.status ?: "none") },
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
                            tint = if (isWasted) colors.danger else if (isRecovered) Color(0xFF7C3AED) else typeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                entry.title,
                                style = AmazeTheme.typography.body.copy(
                                    color = if (isWasted) colors.danger else if (isRecovered) Color(0xFF7C3AED) else colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (isWasted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                            )
                            Text(
                                entry.type,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            )
                            if (isWasted || isRecovered) {
                                Text(
                                    if (isWasted) "Wasted" else "Recovered",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = if (isWasted) colors.danger else Color(0xFF7C3AED),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(
                                when {
                                    isWasted -> colors.danger.copy(alpha = 0.15f)
                                    isRecovered -> Color(0xFF7C3AED).copy(alpha = 0.15f)
                                    entry.type == "LAB" -> colors.info.copy(alpha = 0.1f)
                                    else -> colors.warning.copy(alpha = 0.1f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${entry.hours}h",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = when {
                                    isWasted -> colors.danger
                                    isRecovered -> Color(0xFF7C3AED)
                                    entry.type == "LAB" -> colors.info
                                    else -> colors.warning
                                },
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
