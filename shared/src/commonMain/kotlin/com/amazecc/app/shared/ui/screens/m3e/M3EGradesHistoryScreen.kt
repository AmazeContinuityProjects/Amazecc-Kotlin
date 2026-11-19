package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeRecord
import kotlin.math.round
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Grades & CGPA History Hub.
 * 100% Stateless: accepts [M3EGradesHistoryState] and emits [M3EGradesHistoryAction].
 * Features Cumulative GPA Hero, Grade Counts Strip, and Expandable Semester records.
 */
@Composable
fun M3EGradesHistoryScreen(
    state: M3EGradesHistoryState,
    onAction: (M3EGradesHistoryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item(key = "grades_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Grade History & CGPA",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Semester-by-semester academic performance and credits progression",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Cumulative GPA Hero Card
            item(key = "cgpa_hero") {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Cumulative Grade Point Average (CGPA)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.cumulativeGpa.toFixed(2),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Earned / Attempted",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "${state.totalCreditsEarned} / ${state.totalCreditsAttempted}",
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

            // Grade Count Badges Row (S, A, B Grades)
            item(key = "grades_count_strip") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    M3EGradeCountBadge(
                        label = "S Grades",
                        count = state.sGradesCount,
                        modifier = Modifier.weight(1f)
                    )
                    M3EGradeCountBadge(
                        label = "A Grades",
                        count = state.aGradesCount,
                        modifier = Modifier.weight(1f)
                    )
                    M3EGradeCountBadge(
                        label = "B Grades",
                        count = state.bGradesCount,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Semester Records List
            items(
                items = state.semesters,
                key = { it.semesterId }
            ) { semester ->
                val isExpanded = state.expandedSemesterId == semester.semesterId
                M3ESemesterGradeCard(
                    semester = semester,
                    isExpanded = isExpanded,
                    onToggleExpand = { onAction(M3EGradesHistoryAction.ToggleExpandSemester(semester.semesterId)) }
                )
            }

            // Bottom Spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun M3EGradeCountBadge(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 16.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun M3ESemesterGradeCard(
    semester: M3ESemesterGradeRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onToggleExpand,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Semester Name + SGPA + Expand Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = semester.semesterName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "${semester.creditsEarned} Credits Earned",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SGPA: ${semester.sgpa.toFixed(2)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expandable Course Grades List
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

                    semester.courses.forEach { course ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = course.courseCode,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = "${course.credits} Cr",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = course.courseTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurface
                                )
                            }

                            // Grade Pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        when (course.grade) {
                                            "S" -> colors.successContainer
                                            "A" -> colors.primaryContainer
                                            "B" -> colors.secondaryContainer
                                            else -> colors.surfaceContainerHighest
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = course.grade,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when (course.grade) {
                                        "S" -> colors.success
                                        "A" -> colors.primary
                                        "B" -> colors.secondary
                                        else -> colors.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pure, headless-testable derivation of the grades-history state from the real cached grade data.
 *
 * The domain snapshot is the ingestor's view of `AmazeClient.getAllGrades()` (`AppDataStore` grades
 * cache), so SGPA, grade letters and course identity are all real. Credits are the one fact the
 * `CourseGrade` projection drops: they are read from the snapshot's own `Course.credits` (which is
 * curriculum-authoritative), joined by course code. Nothing here invents a "3 credits per course"
 * or a 14/8/2 grade distribution.
 */
internal fun buildGradesHistoryState(
    snapshot: DomainSnapshot,
    expandedSemesterId: String? = null,
    isLoading: Boolean = false
): M3EGradesHistoryState {
    val rows = GradeHistory.semesterRows(snapshot)
    val allCourses = rows.flatMap { it.courses }
    val distribution = GradeHistory.gradeDistribution(allCourses).associate { it.grade to it.count }

    fun creditsOf(semesterId: String, courseCode: String): Int =
        snapshot.academics.semesters[semesterId]?.courses?.get(courseCode)?.credits
            ?.trim()?.toDoubleOrNull()?.toInt() ?: 0

    fun round2(value: Double): Double = round(value * 100.0) / 100.0

    var gpaSum = 0.0
    var gpaTerms = 0
    var attemptedTotal = 0
    val semesterRecords = rows.map { row ->
        val courses = row.courses.map { c ->
            M3ESemesterGradeCourse(
                courseCode = c.courseCode,
                courseTitle = c.courseTitle,
                credits = creditsOf(row.id, c.courseCode),
                grade = c.grade,
                gradePoints = GradeHistory.gradePoints(c.grade) ?: 0,
                courseType = c.courseType
            )
        }
        // Attempted = every registered course this term; earned = only the ones with a passing
        // graded letter. Keeping the two sums distinct is what makes the  Earned / Attempted
        // counter on the screen meaningful instead of printing the same number twice.
        val attempted = snapshot.academics.semesters[row.id]?.courses?.values
            ?.sumOf { it.credits?.trim()?.toDoubleOrNull()?.toInt() ?: 0 } ?: 0
        val earned = courses.filter { (GradeHistory.gradePoints(it.grade) ?: 0) > 0 }
            .sumOf { it.credits }

        if (row.gpa > 0) {
            gpaSum += row.gpa
            gpaTerms += 1
        }
        val cgpaAtEnd = if (gpaTerms == 0) 0.0 else round2(gpaSum / gpaTerms)

        M3ESemesterGradeRecord(
            semesterId = row.id,
            semesterName = row.label,
            sgpa = row.gpa,
            cgpaAtEnd = cgpaAtEnd,
            creditsEarned = earned,
            courses = courses
        ).also { attemptedTotal += attempted }
    }

    return M3EGradesHistoryState(
        cumulativeGpa = GradeHistory.cumulativeGpa(rows).value,
        totalCreditsEarned = semesterRecords.sumOf { it.creditsEarned },
        totalCreditsAttempted = attemptedTotal,
        sGradesCount = distribution["S"] ?: 0,
        aGradesCount = distribution["A"] ?: 0,
        bGradesCount = distribution["B"] ?: 0,
        semesters = semesterRecords,
        expandedSemesterId = expandedSemesterId,
        isLoading = isLoading
    )
}

private fun createMockGradesHistoryState() = M3EGradesHistoryState(
    cumulativeGpa = 9.24,
    totalCreditsEarned = 124,
    totalCreditsAttempted = 124,
    sGradesCount = 14,
    aGradesCount = 18,
    bGradesCount = 4,
    semesters = listOf(
        M3ESemesterGradeRecord(
            semesterId = "sem_winter_2024",
            semesterName = "Winter Semester 2024-25",
            sgpa = 9.35,
            cgpaAtEnd = 9.24,
            creditsEarned = 24,
            courses = listOf(
                M3ESemesterGradeCourse(
                    courseCode = "CSE3002",
                    courseTitle = "Internet and Web Programming",
                    credits = 4,
                    grade = "S",
                    gradePoints = 10,
                    courseType = "ETH"
                ),
                M3ESemesterGradeCourse(
                    courseCode = "MAT2001",
                    courseTitle = "Complex Variables & Linear Algebra",
                    credits = 4,
                    grade = "A",
                    gradePoints = 9,
                    courseType = "TH"
                )
            )
        )
    )
)

@Preview
@Composable
private fun M3EGradesHistoryScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EGradesHistoryScreen(state = createMockGradesHistoryState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EGradesHistoryScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EGradesHistoryScreen(state = createMockGradesHistoryState(), onAction = {})
    }
}
