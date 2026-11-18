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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A selectable feedback semester. */
@Immutable
data class M3EFeedbackSemester(val value: String, val label: String)

/** One feedback-status table row. */
@Immutable
data class M3EFeedbackRow(
    val feedbackType: String = "",
    val midSemester: String = "",
    val endSemester: String = ""
)

/** Immutable, stateless state for [M3EFeedbackStatusScreen]. */
@Immutable
data class M3EFeedbackStatusState(
    val semesters: List<M3EFeedbackSemester> = emptyList(),
    val selectedSemesterValue: String? = null,
    val rows: List<M3EFeedbackRow> = emptyList(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

/** User intents emitted by [M3EFeedbackStatusScreen]. */
sealed interface M3EFeedbackStatusAction {
    data class SelectSemester(val value: String) : M3EFeedbackStatusAction
    data object Refresh : M3EFeedbackStatusAction
    data object Back : M3EFeedbackStatusAction
}

/**
 * Material 3 Expressive Feedback Status screen.
 * 100% stateless: accepts [M3EFeedbackStatusState], emits [M3EFeedbackStatusAction].
 */
@Composable
fun M3EFeedbackStatusScreen(
    state: M3EFeedbackStatusState,
    onAction: (M3EFeedbackStatusAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val completedCount = state.rows.count { row ->
        listOf(row.midSemester, row.endSemester).any { it.equals("Given", ignoreCase = true) }
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
                        Text("Feedback Status", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Course feedback submission status & history", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3EFeedbackStatusAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            if (state.semesters.isNotEmpty()) {
                item(key = "semesters") {
                    M3EConnectedButtonGroup(
                        items = state.semesters.map { it.value },
                        selectedItem = state.selectedSemesterValue ?: state.semesters.first().value,
                        onItemSelected = { onAction(M3EFeedbackStatusAction.SelectSemester(it)) },
                        labelProvider = { value -> state.semesters.firstOrNull { it.value == value }?.label ?: value }
                    )
                }
            }

            item(key = "summary") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(24.dp))
                        Column {
                            Text("$completedCount of ${state.rows.size}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                            Text("Feedback submissions recorded", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    item(key = "loading") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                            Text("Fetching feedback status...", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
                state.errorMessage != null -> {
                    item(key = "error") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW, borderColor = colors.error) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = colors.error, modifier = Modifier.size(36.dp))
                                Text(state.errorMessage ?: "Something went wrong", fontSize = 13.sp, color = colors.error)
                            }
                        }
                    }
                }
                state.rows.isEmpty() -> {
                    item(key = "empty") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                            Text("No feedback records for this semester.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    item(key = "table_header") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Feedback", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.weight(1.4f))
                                Text("Mid", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("End", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    items(state.rows, key = { "${it.feedbackType}_${it.midSemester}_${it.endSemester}" }) { row ->
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.feedbackType.ifBlank { "—" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.weight(1.4f))
                                FeedbackPill(row.midSemester, Modifier.weight(1f))
                                FeedbackPill(row.endSemester, Modifier.weight(1f))
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
private fun FeedbackPill(value: String, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    val given = value.equals("Given", ignoreCase = true)
    val color = if (given) colors.success else colors.warning
    Box(modifier = modifier.padding(end = 4.dp)) {
        Box(
            modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(value.ifBlank { "—" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun mockFeedbackStatusState() = M3EFeedbackStatusState(
    semesters = listOf(M3EFeedbackSemester("CH20262701", "Fall 2026-27")),
    selectedSemesterValue = "CH20262701",
    rows = listOf(
        M3EFeedbackRow("Course", "Given", "Not Given"),
        M3EFeedbackRow("Curriculum", "Not Given", "Not Given")
    )
)

@Preview
@Composable
private fun M3EFeedbackStatusScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EFeedbackStatusScreen(state = mockFeedbackStatusState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EFeedbackStatusScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EFeedbackStatusScreen(state = mockFeedbackStatusState(), onAction = {}) }
}
