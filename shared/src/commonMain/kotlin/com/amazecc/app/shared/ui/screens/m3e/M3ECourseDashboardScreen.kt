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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
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
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One course shown in the cross-semester course dashboard. */
@Immutable
data class M3EDashboardCourse(
    val courseCode: String,
    val title: String,
    val typeBadge: String,
    val attendancePct: Double?,
    val faculty: String,
    val semesterId: String
)

/** A semester group of courses. */
@Immutable
data class M3EDashboardSemester(val id: String, val label: String, val courses: List<M3EDashboardCourse>)

/** Immutable, stateless state for [M3ECourseDashboardScreen]. */
@Immutable
data class M3ECourseDashboardState(
    val semesters: List<M3EDashboardSemester>,
    val availableSemesterIds: List<String>,
    val semesterLabels: Map<String, String>,
    val selectedSemesterId: String = "All",
    val pastSemestersSynced: Boolean = false,
    val isLoading: Boolean = false
)

/** User intents emitted by [M3ECourseDashboardScreen]. */
sealed interface M3ECourseDashboardAction {
    data class SelectSemester(val id: String) : M3ECourseDashboardAction
    data class OpenCourse(val courseCode: String, val semesterId: String) : M3ECourseDashboardAction
    data object RefreshPastSemesters : M3ECourseDashboardAction
    data object Refresh : M3ECourseDashboardAction
    data object Back : M3ECourseDashboardAction
}

/**
 * Material 3 Expressive Course Dashboard — all registered courses across semesters.
 * 100% stateless: accepts [M3ECourseDashboardState], emits [M3ECourseDashboardAction].
 */
@Composable
fun M3ECourseDashboardScreen(
    state: M3ECourseDashboardState,
    onAction: (M3ECourseDashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

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
                        Text("Course Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("All courses across semesters", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3ECourseDashboardAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "semester_filter") {
                M3EConnectedButtonGroup(
                    items = state.availableSemesterIds,
                    selectedItem = state.selectedSemesterId,
                    onItemSelected = { onAction(M3ECourseDashboardAction.SelectSemester(it)) },
                    labelProvider = { id ->
                        if (id == "All") "All Semesters"
                        else state.semesterLabels[id]?.let { label ->
                            val parts = label.split(" ")
                            if (parts.size >= 2) "${parts[0]} ${parts[1].take(4)}" else label.take(20)
                        } ?: id.take(10)
                    }
                )
            }

            item(key = "past_semesters") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = if (state.pastSemestersSynced) SurfaceContainerLevel.LOW else SurfaceContainerLevel.STANDARD,
                    borderColor = if (state.pastSemestersSynced) null else colors.primary,
                    onClick = { onAction(M3ECourseDashboardAction.RefreshPastSemesters) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.pastSemestersSynced) Icons.Rounded.CheckCircle else Icons.Rounded.History,
                            contentDescription = null,
                            tint = if (state.pastSemestersSynced) colors.success else colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (state.pastSemestersSynced) {
                                "Past semesters loaded · Tap to refresh"
                            } else {
                                "Load past semester attendance & marks"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.semesters.isEmpty()) {
                item(key = "empty") {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No courses found", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            } else {
                state.semesters.forEach { semester ->
                    item(key = "header_${semester.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = semester.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${semester.courses.size} courses",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                    }
                    semester.courses.forEach { course ->
                        item(key = "${semester.id}_${course.courseCode}") {
                            M3EDashboardCourseCard(
                                course = course,
                                onClick = { onAction(M3ECourseDashboardAction.OpenCourse(course.courseCode, course.semesterId)) }
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
private fun M3EDashboardCourseCard(course: M3EDashboardCourse, onClick: () -> Unit) {
    val colors = M3ETheme.colors
    val badgeColor = when (course.typeBadge.uppercase()) {
        "PJ" -> colors.tertiary
        "OC" -> colors.secondary
        "LAB" -> colors.success
        "EMBEDDED" -> colors.primary
        else -> colors.primary
    }
    val pct = course.attendancePct
    val tone = when {
        pct == null -> colors.onSurfaceVariant
        pct >= 85.0 -> colors.success
        pct >= 75.0 -> colors.warning
        else -> colors.error
    }

    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeColor.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(course.typeBadge, fontSize = 10.sp, fontWeight = FontWeight.Black, color = badgeColor)
                    }
                    Text(course.courseCode, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
                if (pct != null) {
                    Text(pct.toFixed(2, "%"), fontSize = 14.sp, fontWeight = FontWeight.Black, color = tone)
                }
            }
            Text(course.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 2)
            if (pct != null) {
                M3EWavyLinearProgress(
                    progress = (pct / 100.0).toFloat(),
                    activeColor = tone,
                    trackColor = colors.surfaceContainerHighest
                )
            }
            if (course.faculty.isNotBlank()) {
                Text(course.faculty, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun mockCourseDashboardState() = M3ECourseDashboardState(
    semesters = listOf(
        M3EDashboardSemester(
            id = "CH20262701",
            label = "Fall Semester 2026-27",
            courses = listOf(
                M3EDashboardCourse("CSE3002", "Internet and Web Programming", "Embedded", 93.3, "Prof. Sundar", "CH20262701"),
                M3EDashboardCourse("MAT2001", "Complex Variables & Linear Algebra", "Theory", 73.3, "Dr. Anita", "CH20262701")
            )
        )
    ),
    availableSemesterIds = listOf("All", "CH20262701"),
    semesterLabels = mapOf("CH20262701" to "Fall Semester 2026-27")
)

@Preview
@Composable
private fun M3ECourseDashboardScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECourseDashboardScreen(state = mockCourseDashboardState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECourseDashboardScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECourseDashboardScreen(state = mockCourseDashboardState(), onAction = {})
    }
}
