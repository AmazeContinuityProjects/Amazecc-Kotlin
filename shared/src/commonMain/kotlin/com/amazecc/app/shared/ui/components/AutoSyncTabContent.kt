package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncScheduler
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatClock(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

private fun formatInstant(i: Instant?): String {
    if (i == null) return "Not scheduled yet"
    val ldt = i.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[ldt.dayOfWeek.ordinal]
    return "$day, ${ldt.dayOfMonth} ${ldt.month.name.take(3)} • ${formatClock(ldt.hour, ldt.minute)}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoSyncTabContent(colors: AmazeColors) {
    var refreshKey by remember { mutableStateOf(0) }
    val refresh = { refreshKey++ }

    val profiles by SyncEngine.profiles.collectAsState()
    val enabled = remember(refreshKey) { SyncScheduler.isEnabled() }
    val lightDaily = remember(refreshKey) { SyncScheduler.isLightDaily() }
    val lightInterval = remember(refreshKey) { SyncScheduler.lightIntervalDays() }
    val lightHour = remember(refreshKey) { SyncScheduler.lightHour() }
    val lightMinute = remember(refreshKey) { SyncScheduler.lightMinute() }
    val lightProfileId = remember(refreshKey) { SyncScheduler.lightProfileId() }
    val fullDay = remember(refreshKey) { SyncScheduler.fullDayOfWeek() }
    val fullHour = remember(refreshKey) { SyncScheduler.fullHour() }
    val fullMinute = remember(refreshKey) { SyncScheduler.fullMinute() }
    val fullProfileId = remember(refreshKey) { SyncScheduler.fullProfileId() }
    val nextLight = remember(refreshKey) { SyncScheduler.getNextRun(SyncScheduler.LIGHT_KIND) }
    val nextFull = remember(refreshKey) { SyncScheduler.getNextRun(SyncScheduler.FULL_KIND) }

    var showLightTimePicker by remember { mutableStateOf(false) }
    var showFullTimePicker by remember { mutableStateOf(false) }

    val weekdays = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Master switch ──
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (enabled) colors.accent.copy(alpha = 0.15f) else colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            null,
                            tint = if (enabled) colors.accent else colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Auto Sync",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            "Background sync on a schedule",
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { SyncScheduler.setEnabled(it); refresh() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.background
                    )
                )
            }
        }

        if (!enabled) {
            Text(
                "Auto sync is turned off — nothing runs on a schedule.",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }

        // ── Daily quick sync ──
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "DAILY QUICK SYNC",
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 1.sp)
                        )
                        Text(
                            "Refreshes the chosen profile once a day",
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazePill(
                        label = "Every Day",
                        selected = lightDaily,
                        colors = colors,
                        onClick = { SyncScheduler.setLightRecurrence(true); refresh() }
                    )
                    AmazePill(
                        label = "Every N Days",
                        selected = !lightDaily,
                        colors = colors,
                        onClick = { SyncScheduler.setLightRecurrence(false); refresh() }
                    )
                }

                if (!lightDaily) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Every", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        IconButton(
                            onClick = { SyncScheduler.setLightIntervalDays(lightInterval - 1); refresh() },
                            enabled = lightInterval > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "$lightInterval day${if (lightInterval > 1) "s" else ""}",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        IconButton(
                            onClick = { SyncScheduler.setLightIntervalDays(lightInterval + 1); refresh() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                PickerField(
                    value = formatClock(lightHour, lightMinute),
                    label = "Sync time",
                    colors = colors,
                    icon = Icons.Rounded.Schedule,
                    onClick = { showLightTimePicker = true }
                )

                Text(
                    "PROFILE",
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp, fontSize = AmazeTheme.fontSize.micro)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        AmazePill(
                            label = profile.name,
                            selected = profile.id == lightProfileId,
                            colors = colors,
                            onClick = { SyncScheduler.setLightProfileId(profile.id); refresh() }
                        )
                    }
                }
            }
        }

        // ── Weekly full sync ──
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.chart1.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DateRange, null, tint = colors.chart1, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "WEEKLY FULL SYNC",
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.chart1, letterSpacing = 1.sp)
                        )
                        Text(
                            "Full refresh of everything, once a week",
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekdays.forEach { (label, value) ->
                        AmazePill(
                            label = label,
                            selected = fullDay == value,
                            colors = colors,
                            tint = colors.chart1,
                            onClick = { SyncScheduler.setFullDayOfWeek(value); refresh() }
                        )
                    }
                }

                PickerField(
                    value = formatClock(fullHour, fullMinute),
                    label = "Sync time",
                    colors = colors,
                    icon = Icons.Rounded.Schedule,
                    onClick = { showFullTimePicker = true }
                )

                Text(
                    "PROFILE",
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp, fontSize = AmazeTheme.fontSize.micro)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        AmazePill(
                            label = profile.name,
                            selected = profile.id == fullProfileId,
                            colors = colors,
                            onClick = { SyncScheduler.setFullProfileId(profile.id); refresh() }
                        )
                    }
                }
            }
        }

        // ── Next runs ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "NEXT RUNS",
                style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp, fontSize = AmazeTheme.fontSize.micro)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Daily quick sync: ${formatInstant(nextLight)}",
                    style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DateRange, null, tint = colors.chart1, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Weekly full sync: ${formatInstant(nextFull)}",
                    style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Syncs also run when the app opens if a scheduled time was missed.",
                style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
            )
        }
    }

    if (showLightTimePicker) {
        TimePickerSheet(
            title = "Daily Sync Time",
            initial = LocalTime(lightHour, lightMinute),
            colors = colors,
            onSelected = { time ->
                SyncScheduler.setLightTime(time.hour, time.minute)
                refresh()
            },
            onDismiss = { showLightTimePicker = false }
        )
    }

    if (showFullTimePicker) {
        TimePickerSheet(
            title = "Weekly Sync Time",
            initial = LocalTime(fullHour, fullMinute),
            colors = colors,
            onSelected = { time ->
                SyncScheduler.setFullTime(time.hour, time.minute)
                refresh()
            },
            onDismiss = { showFullTimePicker = false }
        )
    }
}