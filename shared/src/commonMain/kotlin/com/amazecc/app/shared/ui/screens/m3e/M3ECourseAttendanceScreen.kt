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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the per-course attendance deep dive. */
enum class M3ECourseAttendanceTab(val label: String) {
    PREDICTOR("Predictor"), LOG("Log"), NOTES("Notes")
}

/** Attendance projection cutoff modes. */
enum class M3ECutoffMode(val label: String) {
    LID("LID"), CAT1("CAT 1"), CAT2("CAT 2"), END("End of Sem")
}

/** A single future class session the student may mark as skipped. */
@Immutable
data class M3EFutureClassDate(val key: Int, val label: String, val isSkipped: Boolean = false)

/** A single attendance log row. */
@Immutable
data class M3EAttendanceLogEntry(val date: String, val status: String)

/**
 * Immutable, stateless state for [M3ECourseAttendanceScreen].
 * Mirrors the legacy "Bunk-O-Meter" course attendance experience.
 */
@Immutable
data class M3ECourseAttendanceState(
    val courseCode: String,
    val courseTitle: String,
    val slotName: String,
    val courseType: String,
    val attendedClasses: Int,
    val totalClasses: Int,
    val currentPercentage: Double,
    val targetPercentage: Int = 75,
    val activeTab: M3ECourseAttendanceTab = M3ECourseAttendanceTab.PREDICTOR,
    val cutoffMode: M3ECutoffMode = M3ECutoffMode.LID,
    val futureClassDates: List<M3EFutureClassDate> = emptyList(),
    val whatIfAttend: Int = 0,
    val whatIfMiss: Int = 0,
    val logs: List<M3EAttendanceLogEntry> = emptyList(),
    val notes: String = "",
    val isLoading: Boolean = false
) {
    val isSafe: Boolean get() = currentPercentage >= targetPercentage
    val targetClasses: Int get() = kotlin.math.ceil(totalClasses * targetPercentage / 100.0).toInt()
}

/** User intents emitted by [M3ECourseAttendanceScreen]. */
sealed interface M3ECourseAttendanceAction {
    data class SelectTab(val tab: M3ECourseAttendanceTab) : M3ECourseAttendanceAction
    data class SelectCutoffMode(val mode: M3ECutoffMode) : M3ECourseAttendanceAction
    data class ToggleSkip(val key: Int) : M3ECourseAttendanceAction
    data class SetWhatIfAttend(val count: Int) : M3ECourseAttendanceAction
    data class SetWhatIfMiss(val count: Int) : M3ECourseAttendanceAction
    data class UpdateNotes(val text: String) : M3ECourseAttendanceAction
    data object Refresh : M3ECourseAttendanceAction
    data object Back : M3ECourseAttendanceAction
}

/**
 * Material 3 Expressive per-course attendance deep dive.
 * 100% stateless: accepts [M3ECourseAttendanceState], emits [M3ECourseAttendanceAction].
 * Uses the signature wavy progress indicator and expressive connected button groups.
 */
@Composable
fun M3ECourseAttendanceScreen(
    state: M3ECourseAttendanceState,
    onAction: (M3ECourseAttendanceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val toneColor = when {
        state.currentPercentage >= 85.0 -> colors.success
        state.currentPercentage >= state.targetPercentage -> colors.warning
        else -> colors.error
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
                        Text(
                            text = state.courseTitle.ifBlank { "Course Attendance" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = listOf(state.courseCode, state.courseType, state.slotName)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 13.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { onAction(M3ECourseAttendanceAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "hero") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 24.dp,
                    borderColor = toneColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${state.currentPercentage.toInt()}%",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = toneColor
                        )
                        Text(
                            text = if (state.isSafe) "SAFE · Above ${state.targetPercentage}% target" else "AT RISK · Below ${state.targetPercentage}% target",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isSafe) colors.success else colors.error
                        )
                        M3EWavyLinearProgress(
                            progress = (state.currentPercentage / 100.0).toFloat(),
                            activeColor = toneColor,
                            trackColor = colors.surfaceContainerHighest
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            HeroStat("Attended", "${state.attendedClasses}", colors.primary)
                            HeroStat("Total Classes", "${state.totalClasses}", colors.onSurface)
                            HeroStat("Target (${state.targetPercentage}%)", "${state.targetClasses}", colors.warning)
                        }
                    }
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3ECourseAttendanceTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3ECourseAttendanceAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            when (state.activeTab) {
                M3ECourseAttendanceTab.PREDICTOR -> {
                    item(key = "cutoff") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Projection Cutoff", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                            M3EConnectedButtonGroup(
                                items = M3ECutoffMode.entries,
                                selectedItem = state.cutoffMode,
                                onItemSelected = { onAction(M3ECourseAttendanceAction.SelectCutoffMode(it)) },
                                labelProvider = { it.label }
                            )
                        }
                    }
                    item(key = "whatif") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("What-If Simulator", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text(
                                    "Attend future classes: ${state.whatIfAttend}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurfaceVariant
                                )
                                Slider(
                                    value = state.whatIfAttend.toFloat(),
                                    onValueChange = { onAction(M3ECourseAttendanceAction.SetWhatIfAttend(it.toInt())) },
                                    valueRange = 0f..20f,
                                    steps = 19
                                )
                                Text(
                                    "Miss future classes: ${state.whatIfMiss}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurfaceVariant
                                )
                                Slider(
                                    value = state.whatIfMiss.toFloat(),
                                    onValueChange = { onAction(M3ECourseAttendanceAction.SetWhatIfMiss(it.toInt())) },
                                    valueRange = 0f..20f,
                                    steps = 19
                                )
                                val simulatedPct = simulatedPercentage(state)
                                val simulatedTone = if (simulatedPct >= state.targetPercentage) colors.success else colors.error
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(simulatedTone.copy(alpha = 0.12f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Simulated Outcome", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                            Text(
                                                "${state.attendedClasses + state.whatIfAttend} / ${state.totalClasses + state.whatIfAttend + state.whatIfMiss} classes",
                                                fontSize = 12.sp,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "${simulatedPct.toInt()}%",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = simulatedTone
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.futureClassDates.isNotEmpty()) {
                        item(key = "future_dates") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Future Sessions (${state.futureClassDates.size})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )
                                    Text("Tap a date to mark it as bunked", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        state.futureClassDates.chunked(4).forEach { chunk ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                chunk.forEach { date ->
                                                    val bg = if (date.isSkipped) colors.errorContainer else colors.surfaceContainerHigh
                                                    val fg = if (date.isSkipped) colors.error else colors.onSurface
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(bg)
                                                            .clickable { onAction(M3ECourseAttendanceAction.ToggleSkip(date.key)) }
                                                            .padding(vertical = 8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(date.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                                                            Text(
                                                                text = if (date.isSkipped) "Bunked" else "Attend",
                                                                fontSize = 9.sp,
                                                                color = fg
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
                    }
                }
                M3ECourseAttendanceTab.LOG -> {
                    if (state.logs.isEmpty()) {
                        item(key = "empty_log") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("No attendance logs available", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.logs, key = { "${it.date}_${it.status}" }) { log ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(log.date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Text(
                                        text = log.status,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (log.status.lowercase()) {
                                            "present" -> colors.success
                                            "on duty", "od" -> colors.primary
                                            else -> colors.error
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                M3ECourseAttendanceTab.NOTES -> {
                    item(key = "notes") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Personal Course Notes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text("Saved locally on your device for ${state.courseCode}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                OutlinedTextField(
                                    value = state.notes,
                                    onValueChange = { onAction(M3ECourseAttendanceAction.UpdateNotes(it)) },
                                    placeholder = { Text("Write your notes for this course...") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                                )
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

private fun simulatedPercentage(state: M3ECourseAttendanceState): Double {
    val total = state.totalClasses + state.whatIfAttend + state.whatIfMiss
    return if (total > 0) (state.attendedClasses + state.whatIfAttend).toDouble() / total * 100.0 else 0.0
}

@Composable
private fun HeroStat(label: String, value: String, color: Color) {
    val colors = M3ETheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
    }
}

private fun mockCourseAttendanceState() = M3ECourseAttendanceState(
    courseCode = "CSE3002",
    courseTitle = "Internet and Web Programming",
    slotName = "B1+TB1",
    courseType = "Embedded Theory",
    attendedClasses = 28,
    totalClasses = 32,
    currentPercentage = 87.5,
    logs = listOf(
        M3EAttendanceLogEntry("Class 1", "Present"),
        M3EAttendanceLogEntry("Class 2", "Present"),
        M3EAttendanceLogEntry("Class 3", "On Duty")
    )
)

@Preview
@Composable
private fun M3ECourseAttendanceScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECourseAttendanceScreen(state = mockCourseAttendanceState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECourseAttendanceScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECourseAttendanceScreen(state = mockCourseAttendanceState(), onAction = {})
    }
}
