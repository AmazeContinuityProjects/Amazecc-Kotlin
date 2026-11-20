package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.AcademicCourseFilter
import com.amazecc.app.shared.domain.overhaul.M3EAcademicCourse
import com.amazecc.app.shared.domain.overhaul.M3EAcademicSummary
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsAction
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsState
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Academics & Grades Screen.
 * 100% Stateless: accepts [M3EAcademicsState] and emits [M3EAcademicsAction].
 * Features CGPA Hero Card, Course Type Filtering, Internal Marks Wavy Progress, and Drilldown.
 */
@Composable
fun M3EAcademicsScreen(
    state: M3EAcademicsState,
    onAction: (M3EAcademicsAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Navigation is a host concern; the immutable action set carries no course-detail intent. */
    onOpenCourse: (String) -> Unit = {}
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            item(key = "academics_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Academics",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = state.semesterName,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onAction(M3EAcademicsAction.Refresh) },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh academics",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Semester switch — host maps the selected name back to its id.
            if (state.availableSemesters.size > 1) {
                item(key = "academics_semesters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.availableSemesters.forEach { semester ->
                            FilterChip(
                                selected = semester == state.semesterName,
                                onClick = { onAction(M3EAcademicsAction.SelectSemester(semester)) },
                                label = { Text(semester, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Search — the host already filters on this query.
            item(key = "academics_search") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EAcademicsAction.UpdateSearch(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search course code, title or faculty",
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    trailingIcon = if (state.searchQuery.isBlank()) null else {
                        {
                            IconButton(onClick = { onAction(M3EAcademicsAction.UpdateSearch("")) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLowest,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    )
                )
            }

            // CGPA & Credits Hero Summary Card
            item(key = "academics_hero") {
                M3EAcademicHeroCard(summary = state.summary)
            }

            // Course Type Filter (All, Theory, Lab, Embedded)
            item(key = "academics_filter") {
                M3EConnectedButtonGroup(
                    items = AcademicCourseFilter.entries,
                    selectedItem = state.selectedFilter,
                    onItemSelected = { onAction(M3EAcademicsAction.SetFilter(it)) },
                    labelProvider = { it.label }
                )
            }

            // The host owns filtering (same Theory/Lab/Embedded tokens for every surface),
            // so render its list directly instead of re-filtering with different abbreviations.
            if (state.courses.isEmpty() && !state.isLoading) {
                item(key = "academics_empty") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Text(
                            text = "No courses match this semester and search.",
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            items(
                items = state.courses,
                key = { it.courseCode }
            ) { course ->
                val isExpanded = state.expandedCourseCode == course.courseCode
                M3EAcademicCourseCard(
                    course = course,
                    isExpanded = isExpanded,
                    onToggleExpand = { onAction(M3EAcademicsAction.ToggleExpandCourse(course.courseCode)) },
                    onOpenCourse = onOpenCourse
                )
            }

            // Bottom Spacing for floating toolbar (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun M3EAcademicHeroCard(
    summary: M3EAcademicSummary,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cumulative GPA",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary.cgpa.toFixed(2),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.primary
                )
                summary.rankOrStanding?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = colors.success,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Earned",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.creditsEarned}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Credits",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Registered",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.creditsRegistered}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Credits",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun M3EAcademicCourseCard(
    course: M3EAcademicCourse,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onToggleExpand,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Course Code + Credits Pill + Grade/Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = course.courseCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${course.credits} Credits",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    course.grade?.let { grade ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.successContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Grade: $grade",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.success
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title
            Text(
                text = course.courseTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Faculty
            Text(
                text = course.faculty,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )

            // Internal Marks Wavy Progress
            if (course.internalMax > 0.0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Internal Marks",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "${course.internalScored.toFixed(2)}/${course.internalMax.toFixed(2)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }

                    val progress = (course.internalScored / course.internalMax).toFloat()
                    M3EWavyLinearProgress(
                        progress = progress,
                        activeColor = colors.primary,
                        trackColor = colors.surfaceContainerHighest
                    )
                }
            }

            // Expandable Assessment Drilldown
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))

                    Text(
                        text = "Assessments Breakdown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )

                    if (course.assessments.isEmpty()) {
                        Text(
                            text = "No detailed assessments published yet.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    } else {
                        course.assessments.forEach { assessment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = assessment.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "Weightage: ${assessment.weightagePercent.toFixed(2)}%",
                                        fontSize = 10.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "${assessment.scoredMarks.toFixed(2)} / ${assessment.maxMarks.toFixed(2)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }

                    Text(
                        text = "Open course detail →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.clickable { onOpenCourse(course.courseCode) }
                    )
                }
            }
        }
    }
}

private fun createMockAcademicsState() = M3EAcademicsState(
    semesterName = "Winter Semester 2025-26",
    summary = M3EAcademicSummary(
        cgpa = 9.24,
        creditsEarned = 124,
        creditsRegistered = 24,
        rankOrStanding = "Top 5% in Program"
    ),
    courses = listOf(
        M3EAcademicCourse(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            courseType = "ETH",
            faculty = "Prof. Sundar",
            internalScored = 45.5,
            internalMax = 50.0,
            assessments = listOf(
                M3EAssessmentItem(
                    title = "Continuous Assessment 1",
                    maxMarks = 50.0,
                    scoredMarks = 46.0,
                    weightagePercent = 15.0,
                    weightageMarks = 13.8
                ),
                M3EAssessmentItem(
                    title = "Continuous Assessment 2",
                    maxMarks = 50.0,
                    scoredMarks = 48.0,
                    weightagePercent = 15.0,
                    weightageMarks = 14.4
                )
            )
        ),
        M3EAcademicCourse(
            courseCode = "MAT2001",
            courseTitle = "Complex Variables & Linear Algebra",
            credits = 4,
            courseType = "TH",
            faculty = "Dr. Anita",
            grade = "A",
            internalScored = 52.0,
            internalMax = 60.0
        )
    )
)

@Preview
@Composable
private fun M3EAcademicsScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAcademicsScreen(state = createMockAcademicsState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EAcademicsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EAcademicsScreen(state = createMockAcademicsState(), onAction = {})
    }
}
