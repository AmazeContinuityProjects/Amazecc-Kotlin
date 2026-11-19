package com.amazecc.app.shared.ui.screens.m3e

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EStatCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the OD Tracker. */
enum class M3EODTab(val label: String) {
    OVERVIEW("Overview"), ENTRIES("Entries")
}

/** Tracking status for a single OD entry. */
@kotlinx.serialization.Serializable
enum class M3EODStatus { NONE, WASTED, RECOVERED }

/** One on-duty class entry. */
@Immutable
data class M3EODEntry(
    val courseCode: String,
    val title: String,
    val type: String,
    val hours: Int,
    val status: M3EODStatus = M3EODStatus.NONE
)

/** All OD entries for one date. */
@Immutable
data class M3EODDay(val date: String, val entries: List<M3EODEntry>) {
    val totalHours: Int get() = entries.sumOf { it.hours }
}

/** Derived metrics for the overview tab. */
@Immutable
data class M3EODMetrics(
    val totalODDays: Int,
    val labHours: Int,
    val theoryHours: Int,
    val wastedHours: Int,
    val recoveredHours: Int
) {
    val usedHours: Int get() = labHours + theoryHours
    val validHours: Int get() = labHours + theoryHours - wastedHours + recoveredHours
}

/** Immutable, stateless state for [M3EODTrackerScreen]. */
@Immutable
data class M3EODTrackerState(
    val totalHoursAllowed: Int = 40,
    val activeTab: M3EODTab = M3EODTab.OVERVIEW,
    val days: List<M3EODDay> = emptyList(),
    val isLoading: Boolean = false
) {
    val metrics: M3EODMetrics
        get() {
            val all = days.flatMap { it.entries }
            return M3EODMetrics(
                totalODDays = days.size,
                labHours = all.filter { it.type == "LAB" }.sumOf { it.hours },
                theoryHours = all.filter { it.type != "LAB" }.sumOf { it.hours },
                wastedHours = all.filter { it.status == M3EODStatus.WASTED }.sumOf { it.hours },
                recoveredHours = all.filter { it.status == M3EODStatus.RECOVERED }.sumOf { it.hours }
            )
        }
}

/** User intents emitted by [M3EODTrackerScreen]. */
sealed interface M3EODTrackerAction {
    data class SelectTab(val tab: M3EODTab) : M3EODTrackerAction
    data class CycleStatus(val date: String, val courseCode: String) : M3EODTrackerAction
    data object Refresh : M3EODTrackerAction
    data object Back : M3EODTrackerAction
}

/**
 * Material 3 Expressive On-Duty (OD) Tracker.
 * 100% stateless: accepts [M3EODTrackerState], emits [M3EODTrackerAction].
 */
@Composable
fun M3EODTrackerScreen(
    state: M3EODTrackerState,
    onAction: (M3EODTrackerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val metrics = state.metrics
    val usage = if (state.totalHoursAllowed > 0) {
        (metrics.usedHours.toFloat() / state.totalHoursAllowed).coerceIn(0f, 1f)
    } else 0f
    val tone = when {
        usage >= 0.9f -> colors.error
        usage >= 0.7f -> colors.warning
        else -> colors.primary
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OD Tracker", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("On-duty hours, valid hours & recovery", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3EODTrackerAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "hero") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp, borderColor = tone) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("OD Hours Used", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                Text("${metrics.usedHours} / ${state.totalHoursAllowed}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                            }
                            Box(
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(tone.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((usage * 100).toFixed(2, "%"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = tone)
                            }
                        }
                        M3EWavyLinearProgress(progress = usage, activeColor = tone, trackColor = colors.surfaceContainerHighest)
                    }
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3EODTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3EODTrackerAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            when (state.activeTab) {
                M3EODTab.OVERVIEW -> {
                    item(key = "kpis") {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3EStatCard(
                                    stat = M3EStatItem("od_days", "OD Days", "${metrics.totalODDays}", "Recorded on-duty days", M3EStatTone.PRIMARY),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("lab_hours", "Lab Hours", "${metrics.labHours}h", "2h per lab session", M3EStatTone.SECONDARY),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3EStatCard(
                                    stat = M3EStatItem("theory_hours", "Theory Hours", "${metrics.theoryHours}h", "1h per theory class", M3EStatTone.TERTIARY),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("valid_hours", "Valid Hours", "${metrics.validHours}h", "After waste & recovery", M3EStatTone.GOOD),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3EStatCard(
                                    stat = M3EStatItem("wasted", "Wasted Hours", "${metrics.wastedHours}h", "Marked as wasted", M3EStatTone.CRITICAL),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("recovered", "Recovered", "${metrics.recoveredHours}h", "Regained hours", M3EStatTone.WARNING),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    item(key = "summary") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("OD Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                SummaryRow("Total OD Hours", "${metrics.usedHours}h / ${state.totalHoursAllowed}h", false)
                                SummaryRow("Valid Hours", "${metrics.validHours}h", false)
                                SummaryRow("Wasted Hours", "${metrics.wastedHours}h", false)
                                SummaryRow("Recovered Hours", "${metrics.recoveredHours}h", false)
                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                                SummaryRow("Net Impact", "${metrics.validHours}h", true)
                                SummaryRow("Remaining", "${(state.totalHoursAllowed - metrics.usedHours).coerceAtLeast(0)}h", true)
                            }
                        }
                    }
                }
                M3EODTab.ENTRIES -> {
                    if (state.days.isEmpty()) {
                        item(key = "empty_entries") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(44.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No OD entries found", fontSize = 14.sp, color = colors.onSurfaceVariant)
                                    Text("Sync attendance to detect On Duty days", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        state.days.forEach { day ->
                            item(key = "day_${day.date}") {
                                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(day.date, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("${day.totalHours}h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                                            }
                                        }
                                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                                        day.entries.forEach { entry ->
                                            M3EODEntryRow(
                                                entry = entry,
                                                onClick = { onAction(M3EODTrackerAction.CycleStatus(day.date, entry.courseCode)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

@Composable
private fun M3EODEntryRow(entry: M3EODEntry, onClick: () -> Unit) {
    val colors = M3ETheme.colors
    val (label, color) = when (entry.status) {
        M3EODStatus.WASTED -> "Wasted" to colors.error
        M3EODStatus.RECOVERED -> "Recovered" to colors.tertiary
        M3EODStatus.NONE -> entry.type to if (entry.type == "LAB") colors.secondary else colors.warning
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                textDecoration = if (entry.status == M3EODStatus.WASTED) TextDecoration.LineThrough else null
            )
            Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("${entry.hours}h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isHighlight: Boolean) {
    val colors = M3ETheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal, color = colors.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isHighlight) colors.primary else colors.onSurface)
    }
}

private fun mockODTrackerState() = M3EODTrackerState(
    days = listOf(
        M3EODDay("12 Sep 2026", listOf(M3EODEntry("CSE3002", "Internet and Web Programming", "LAB", 2))),
        M3EODDay("08 Sep 2026", listOf(M3EODEntry("MAT2001", "Complex Variables", "TH", 1, M3EODStatus.RECOVERED)))
    )
)

@Preview
@Composable
private fun M3EODTrackerScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EODTrackerScreen(state = mockODTrackerState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EODTrackerScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EODTrackerScreen(state = mockODTrackerState(), onAction = {}) }
}
