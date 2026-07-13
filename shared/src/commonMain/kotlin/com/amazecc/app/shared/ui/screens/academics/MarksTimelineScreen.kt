package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun MarksTimelineScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val allGradesRes by AppState.allGrades.collectAsState()

    val courses = marksRes?.marks ?: emptyList()
    val cgpaData = marksRes?.cgpa
    val allGrades = allGradesRes?.grades ?: emptyMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Marks Timeline",
            description = "Assessment and grade history",
            showBackButton = true,
            showSyncButton = true
        )

        if (marksRes == null && allGradesRes == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Info, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No marks data available", color = colors.textSecondary, style = AmazeTheme.typography.body)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (cgpaData != null) {
                    item { GpaOverviewCard(cgpaData, colors) }
                }

                if (allGrades.isNotEmpty()) {
                    item {
                        Text(
                            "GPA Trend",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold, color = colors.textPrimary
                            )
                        )
                    }
                    item { GpaTrendSection(allGrades, colors) }

                    item {
                        Text(
                            "Semester Breakdown",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold, color = colors.textPrimary
                            )
                        )
                    }
                    allGrades.forEach { (semesterId, result) ->
                        if (result != null) {
                            item {
                                SemesterGradeCard(semesterId = semesterId, result = result, colors = colors)
                            }
                        }
                    }
                }

                if (courses.isNotEmpty()) {
                    item {
                        Text(
                            "Course Assessments",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold, color = colors.textPrimary
                            )
                        )
                    }
                    items(courses) { course ->
                        CourseAssessmentCard(course = course, colors = colors)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GpaOverviewCard(
    cgpaData: CGPAResult,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                cgpaData.cgpa?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CGPA",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                        Text(
                            it,
                            style = AmazeTheme.typography.display.copy(
                                color = colors.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                cgpaData.creditsEarned?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Credits Earned",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                        Text(
                            it,
                            style = AmazeTheme.typography.display.copy(
                                color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                cgpaData.creditsRequired?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Credits Required",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                        Text(
                            it,
                            style = AmazeTheme.typography.display.copy(
                                color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpaTrendSection(
    grades: Map<String, SemesterGradeResult?>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val sortedSemesters = AppState.semesterIDs.filter { it in grades }.sorted()

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sortedSemesters.forEach { semId ->
                val result = grades[semId] ?: return@forEach
                val semesterName = AppState.semesterMap[semId] ?: semId
                val gpa = result.gpa ?: "N/A"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        semesterName,
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary),
                        modifier = Modifier.weight(1f)
                    )
                    val gpaFloat = gpa.toFloatOrNull()
                    val gpaColor = when {
                        gpaFloat == null -> colors.textMuted
                        gpaFloat >= 9.0f -> Color(0xFF10B981)
                        gpaFloat >= 8.0f -> Color(0xFF3B82F6)
                        gpaFloat >= 7.0f -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Text(
                        gpa,
                        style = AmazeTheme.typography.body.copy(
                            color = gpaColor, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterGradeCard(
    semesterId: String,
    result: SemesterGradeResult,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val semesterName = AppState.semesterMap[semesterId] ?: semesterId
    val gradeItems = result.grades

    val gradeCounts = remember(result) {
        mapOf(
            "S" to gradeItems.count { it.grade.equals("S", ignoreCase = true) },
            "A" to gradeItems.count { it.grade.equals("A", ignoreCase = true) },
            "B" to gradeItems.count { it.grade.equals("B", ignoreCase = true) },
            "C" to gradeItems.count { it.grade.equals("C", ignoreCase = true) },
            "D" to gradeItems.count { it.grade.equals("D", ignoreCase = true) },
            "E" to gradeItems.count { it.grade.equals("E", ignoreCase = true) },
            "F" to gradeItems.count { it.grade.equals("F", ignoreCase = true) }
        )
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    semesterName,
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold, color = colors.textPrimary
                    )
                )
                if (result.gpa != null) {
                    Text(
                        "GPA: ${result.gpa}",
                        style = AmazeTheme.typography.body.copy(
                            color = colors.accent, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                gradeCounts.forEach { (grade, count) ->
                    if (count > 0) {
                        val gradeCol = gradeColorFor(grade)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(gradeCol.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    grade,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = gradeCol, fontWeight = FontWeight.Bold, fontSize = 11.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                count.toString(),
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.textSecondary, fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            gradeItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.courseTitle,
                            style = AmazeTheme.typography.body.copy(
                                color = colors.textPrimary, fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            item.courseCode,
                            style = AmazeTheme.typography.caption.copy(
                                color = colors.textMuted, fontSize = 10.sp
                            )
                        )
                    }
                    val gradeCol = gradeColorFor(item.grade)
                    Text(
                        item.grade,
                        style = AmazeTheme.typography.body.copy(
                            color = gradeCol, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseAssessmentCard(
    course: MarksCourseItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        course.courseTitle,
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp
                        )
                    )
                    Text(
                        "${course.courseCode} | ${course.courseType} | ${course.slot}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 10.sp)
                    )
                }
                if (course.credits != null) {
                    Text(
                        "${course.credits} cr",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (course.assessments.isNotEmpty()) {
                HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                course.assessments.forEach { assessment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                assessment.title,
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.textPrimary, fontSize = 12.sp
                                )
                            )
                            Text(
                                "Max: ${assessment.maxMark} | Weightage: ${assessment.weightagePercent}%",
                                style = AmazeTheme.typography.caption.copy(
                                    color = colors.textMuted, fontSize = 10.sp
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                assessment.scoredMark,
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            )
                            if (assessment.weightageMark.isNotBlank() && assessment.weightageMark != "0") {
                                Text(
                                    "(${assessment.weightageMark})",
                                    style = AmazeTheme.typography.caption.copy(
                                        color = colors.textMuted, fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (course.faculty.isNotBlank()) {
                Text(
                    "Faculty: ${course.faculty}",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 10.sp)
                )
            }
        }
    }
}

private fun gradeColorFor(grade: String): Color = when (grade.uppercase()) {
    "S" -> Color(0xFF10B981)
    "A" -> Color(0xFF3B82F6)
    "B" -> Color(0xFFF59E0B)
    "C" -> Color(0xFFF97316)
    "D", "E" -> Color(0xFFEF4444)
    "F" -> Color(0xFF991B1B)
    else -> Color(0xFF9CA3AF)
}
