package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
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
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EStatCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3EStatSubtitle
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
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

/** A VTOP OD-details record matched to an attendance day - explains *why*, never adds hours. */
@Immutable
data class M3EODOfficialReason(val reason: String, val remarks: String)

/** All OD entries for one date. */
@Immutable
data class M3EODDay(
    val date: String,
    val entries: List<M3EODEntry>,
    /** VTOP's "why" text for this day, matched by date range. Informational only. */
    val officialReasons: List<M3EODOfficialReason> = emptyList(),
) {
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
    /**
     * Legacy allowance hint, retained for state compatibility only. The web OD Hours History page
     * has no allowance and no cap, so D-120 stops presenting this number as if it were real data.
     */
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
    // D-120: no invented 40h allowance / percentage — the web page reports only real OD hours.

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
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Total OD", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text("${metrics.usedHours} hrs", fontSize = 32.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                        Text(
                            "Across ${metrics.totalODDays} ${if (metrics.totalODDays == 1) "day" else "days"} on duty",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
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
                                    stat = M3EStatItem("od_days", "OD Days", "${metrics.totalODDays}", M3EStatSubtitle.fromData("Recorded on-duty days"), M3EStatTone.PRIMARY),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("lab_hours", "Lab Hours", "${metrics.labHours}h", M3EStatSubtitle.fromData("From attendance logs"), M3EStatTone.SECONDARY),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3EStatCard(
                                    stat = M3EStatItem("theory_hours", "Theory Hours", "${metrics.theoryHours}h", M3EStatSubtitle.fromData("From attendance logs"), M3EStatTone.TERTIARY),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("valid_hours", "Valid Hours", "${metrics.validHours}h", M3EStatSubtitle.fromData("After waste & recovery"), M3EStatTone.GOOD),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3EStatCard(
                                    stat = M3EStatItem("wasted", "Wasted Hours", "${metrics.wastedHours}h", M3EStatSubtitle.fromData("Marked as wasted"), M3EStatTone.CRITICAL),
                                    modifier = Modifier.weight(1f)
                                )
                                M3EStatCard(
                                    stat = M3EStatItem("recovered", "Recovered", "${metrics.recoveredHours}h", M3EStatSubtitle.fromData("Regained hours"), M3EStatTone.WARNING),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    item(key = "summary") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("OD Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                SummaryRow("Total OD Hours", "${metrics.usedHours}h", false)
                                SummaryRow("Valid Hours", "${metrics.validHours}h", false)
                                SummaryRow("Wasted Hours", "${metrics.wastedHours}h", false)
                                SummaryRow("Recovered Hours", "${metrics.recoveredHours}h", false)
                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                                SummaryRow("Net Impact", "${metrics.validHours}h", true)
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
                                        day.officialReasons.forEach { official ->
                                            Text(
                                                official.remarks.takeIf { it.isNotBlank() }
                                                    ?.let { "${official.reason} — $it" } ?: official.reason,
                                                fontSize = 11.sp,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
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
    // D-122: the whole row is one merged button with a role, a spoken label and the
    // current tracking state (the row cycles that state on tap).
    val statusSpoken = when (entry.status) {
        M3EODStatus.WASTED -> "wasted"
        M3EODStatus.RECOVERED -> "recovered"
        M3EODStatus.NONE -> "not reviewed"
    }
    val rowLabel = "${entry.title.ifBlank { entry.courseCode }}, ${entry.hours} hours, " +
        "$statusSpoken. Tap to change tracking status"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableA11y(onClickLabel = rowLabel, onClick = onClick),
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

/**
 * Parse the hours a real OD record *claims* from VTOP's `time` cell. Retained only as an
 * inspection helper: D-120 moved OD counting to the attendance slot rule in
 * `domain/OdHoursDerivation.kt`, so this value never feeds a counter on the OD Tracker page.
 */
internal fun odHoursFrom(raw: String): Int {
    val value = raw.trim()
    if (value.isEmpty()) return 0
    Regex("(\\d+(?:\\.\\d+)?)\\s*h(?:ou)?rs?", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { return maxOf(0, it.toInt()) }

    val clocks = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", RegexOption.IGNORE_CASE)
        .findAll(value).toList()
    if (clocks.size >= 2) {
        fun minutes(match: MatchResult): Int? {
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val adjusted = when (match.groupValues[3].uppercase()) {
                "PM" -> if (hour == 12) 12 else hour + 12
                "AM" -> if (hour == 12) 0 else hour
                else -> hour
            }
            return adjusted * 60 + minute
        }
        val start = minutes(clocks[0])
        val end = minutes(clocks[1])
        if (start != null && end != null) {
            val rawDiff = end - start
            val diff = if (rawDiff < 0) rawDiff + 12 * 60 else rawDiff
            return maxOf(0, (diff + 30) / 60)
        }
    }
    return 0
}

/** Lab/theory classification based on the record's real `type`/`basis` text. */
internal fun odTypeFor(type: String, basis: String): String {
    val combined = "$type $basis"
    return if (combined.contains("lab", ignoreCase = true) ||
        combined.contains("practical", ignoreCase = true)
    ) "LAB" else "TH"
}

/** Sort key for a VTOP OD date; unparseable dates sink to the bottom. */
internal fun odDateEpochDay(raw: String): Long? =
    ExamUtils.parseExamDateToLocalDate(raw)?.toEpochDays()?.toLong()

/**
 * Build the stateless day/entry list the way the web OD Hours History page does (D-120).
 *
 * Counts come *only* from [derivedOdRows] - the real attendance logs filtered by
 * `deriveOdHoursDays`. VTOP OD-details records are never turned into hour-bearing entries: the web
 * page uses them solely to answer "why was this day an OD?" That union was the overshoot - a VTOP
 * "09:00 - 11:00" cell became two hours on top of the one hour the attendance slot already gave,
 * and the old (date, course, hours) de-dup key let both through whenever the hours disagreed.
 *
 * Persisted wasted/recovered annotations still apply, keyed by `date|code`.
 */
internal fun mapOdRecordsToDays(
    res: OdDetailsRes?,
    tracker: Map<String, M3EODStatus>,
    derivedOdRows: List<DerivedOdRow> = emptyList(),
): List<M3EODDay> {
    val officialRecords = if (res != null && res.success && res.error == null) res.records else emptyList()
    val byDate = LinkedHashMap<String, MutableList<M3EODEntry>>()
    for (row in derivedOdRows) {
        byDate.getOrPut(row.date) { mutableListOf() } += M3EODEntry(
            courseCode = row.courseCode,
            title = row.title,
            type = row.type,
            hours = row.hours,
            status = tracker["${row.date}|${row.courseCode}"] ?: M3EODStatus.NONE,
        )
    }
    return byDate.map { (date, entries) ->
        M3EODDay(date, entries, officialReasons = matchOfficialOdReasons(date, officialRecords))
    }.sortedByDescending { odDateEpochDay(it.date) ?: Long.MIN_VALUE }
}

/** Web `matchOfficialOdForDate`: a record explains a day when its range contains that day. */
internal fun matchOfficialOdReasons(
    dayDate: String,
    records: List<com.amazecc.app.shared.model.OdRecordItem>,
): List<M3EODOfficialReason> {
    val day = odDateEpochDay(dayDate) ?: return emptyList()
    return records.mapNotNull { record ->
        val parts = record.date.split(Regex("\\s+to\\s+", RegexOption.IGNORE_CASE))
        val from = odDateEpochDay(parts.firstOrNull().orEmpty())
        val to = odDateEpochDay(parts.getOrNull(1) ?: parts.firstOrNull().orEmpty())
        if (from == null || to == null || day < from || day > to) return@mapNotNull null
        val reason = record.reason.ifBlank { record.type }
        reason.takeIf { it.isNotBlank() }?.let { M3EODOfficialReason(it, record.remarks) }
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
