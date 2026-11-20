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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Stable test tags for the grouped feedback-status surface (D-117). */
object M3EFeedbackStatusTestTags {
    const val LIST = "m3e_feedback_status_list"
    const val MID_HEADER = "m3e_feedback_status_mid_header"
    const val TEE_HEADER = "m3e_feedback_status_tee_header"
    const val MID_EMPTY = "m3e_feedback_status_mid_empty"
    const val TEE_EMPTY = "m3e_feedback_status_tee_empty"

    fun cardTag(group: String, index: Int) = "m3e_feedback_status_${group}_card_$index"

    fun iconTag(group: String, index: Int, given: Boolean) =
        "m3e_feedback_status_${group}_${index}_${if (given) "given" else "not_given"}_icon"
}

/**
 * The VTOP feedback table writes this exact phrase into a cell when a submission was made.
 * Anything else (including blank or "Not Given") is honestly rendered as not submitted.
 */
fun isFeedbackGiven(value: String?): Boolean =
    value?.contains("Feed Back Given For", ignoreCase = true) == true

/** A selectable feedback semester. */
@Immutable
data class M3EFeedbackSemester(val value: String, val label: String)

/** One feedback-status record. The two semester cells hold the real VTOP text. */
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
 *
 * The records are laid out as exactly two tonal, hairline-bordered groups — Mid Semester and
 * End Semester (TEE Semester). Each real record keeps its course/faculty text and raw cell text;
 * the status is a real Material tick or cross icon in the matching status tone.
 */
@Composable
fun M3EFeedbackStatusScreen(
    state: M3EFeedbackStatusState,
    onAction: (M3EFeedbackStatusAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val midGivenCount = state.rows.count { isFeedbackGiven(it.midSemester) }
    val teeGivenCount = state.rows.count { isFeedbackGiven(it.endSemester) }
    val totalGiven = midGivenCount + teeGivenCount
    val totalSubmissions = state.rows.size * 2

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(M3EFeedbackStatusTestTags.LIST),
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
                        Icon(Icons.Rounded.AssignmentTurnedIn, contentDescription = null, tint = colors.success, modifier = Modifier.size(24.dp))
                        Column {
                            Text("$totalGiven of $totalSubmissions", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
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
                else -> {
                    // Section 1 — Mid Semester.
                    item(key = "mid_header") {
                        FeedbackGroupHeader(
                            title = "Mid Semester",
                            given = midGivenCount,
                            total = state.rows.size,
                            tag = M3EFeedbackStatusTestTags.MID_HEADER
                        )
                    }
                    if (state.rows.isEmpty()) {
                        item(key = "mid_empty") {
                            FeedbackGroupEmpty(
                                message = "No mid-semester feedback records were returned for this semester.",
                                tag = M3EFeedbackStatusTestTags.MID_EMPTY
                            )
                        }
                    } else {
                        itemsIndexed(state.rows, key = { index, row -> "mid_${index}_${row.feedbackType}" }) { index, row ->
                            FeedbackRecordCard(
                                group = "mid",
                                groupLabel = "Mid Semester",
                                index = index,
                                courseText = row.feedbackType,
                                rawValue = row.midSemester,
                                given = isFeedbackGiven(row.midSemester)
                            )
                        }
                    }

                    // Section 2 — End Semester (TEE Semester).
                    item(key = "tee_header") {
                        FeedbackGroupHeader(
                            title = "End Semester (TEE Semester)",
                            given = teeGivenCount,
                            total = state.rows.size,
                            tag = M3EFeedbackStatusTestTags.TEE_HEADER
                        )
                    }
                    if (state.rows.isEmpty()) {
                        item(key = "tee_empty") {
                            FeedbackGroupEmpty(
                                message = "No end-semester (TEE) feedback records were returned for this semester.",
                                tag = M3EFeedbackStatusTestTags.TEE_EMPTY
                            )
                        }
                    } else {
                        itemsIndexed(state.rows, key = { index, row -> "tee_${index}_${row.feedbackType}" }) { index, row ->
                            FeedbackRecordCard(
                                group = "tee",
                                groupLabel = "End Semester (TEE Semester)",
                                index = index,
                                courseText = row.feedbackType,
                                rawValue = row.endSemester,
                                given = isFeedbackGiven(row.endSemester)
                            )
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
private fun FeedbackGroupHeader(title: String, given: Int, total: Int, tag: String) {
    val colors = M3ETheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (total == 0) "NO RECORDS" else "$given/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (total > 0 && given == total) colors.success else colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.5f)))
    }
}

@Composable
private fun FeedbackGroupEmpty(message: String, tag: String) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth().testTag(tag), containerLevel = SurfaceContainerLevel.LOWEST) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AssignmentTurnedIn, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackRecordCard(
    group: String,
    groupLabel: String,
    index: Int,
    courseText: String,
    rawValue: String,
    given: Boolean
) {
    val colors = M3ETheme.colors
    val statusContainer = if (given) colors.successContainer else colors.errorContainer
    val statusForeground = if (given) colors.onSuccessContainer else colors.onErrorContainer

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag(M3EFeedbackStatusTestTags.cardTag(group, index)),
        containerLevel = SurfaceContainerLevel.LOWEST
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusContainer)
                    .testTag(M3EFeedbackStatusTestTags.iconTag(group, index, given)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (given) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = if (given) "$groupLabel — Feedback given" else "$groupLabel — Feedback not given",
                    tint = statusForeground,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    courseText.ifBlank { "Feedback record" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    rawValue.ifBlank { "No status text returned" },
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun mockFeedbackStatusState() = M3EFeedbackStatusState(
    semesters = listOf(M3EFeedbackSemester("CH20262701", "Fall 2026-27")),
    selectedSemesterValue = "CH20262701",
    rows = listOf(
        M3EFeedbackRow("CSE1001 · Programming / Dr. Anita Rao", "Feed Back Given For : CSE1001", "Not Given"),
        M3EFeedbackRow("CSE2005 · Operating Systems / Dr. Sundar", "Feed Back Given For : CSE2005", "Feed Back Given For : CSE2005")
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
