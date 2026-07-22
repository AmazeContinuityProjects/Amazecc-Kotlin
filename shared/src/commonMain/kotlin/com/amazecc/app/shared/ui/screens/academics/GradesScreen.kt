package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun GradesScreen() {
    val colors = AmazeTheme.colors
    val allGradesRes by AppState.allGrades.collectAsState()
    val semesterMap = AppState.semesterMap

    val gpaRecords = allGradesRes?.grades ?: emptyMap()
    val semesterIds = gpaRecords.keys.toList().sortedDescending()

    var selectedSemesterId by remember { mutableStateOf(semesterIds.firstOrNull() ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val selectedSemester = gpaRecords[selectedSemesterId]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Grade History",
            description = "All semesters — GPA and course grades",
            showBackButton = true,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (gpaRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No grade history available.",
                            style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Sync data from your profile.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                        )
                    }
                }
                return
            }

            // Semester selector
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = semesterMap[selectedSemesterId] ?: selectedSemesterId,
                                style = AmazeTheme.typography.body.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            )
                            Text(
                                text = "GPA: ${selectedSemester?.gpa ?: "—"}",
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.textSecondary
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(colors.surface)
                ) {
                    semesterIds.forEach { semId ->
                        val sem = gpaRecords[semId]
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = semesterMap[semId] ?: semId,
                                        style = AmazeTheme.typography.body.copy(color = colors.textPrimary)
                                    )
                                    Text(
                                        text = "GPA: ${sem?.gpa ?: "—"}",
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            },
                            onClick = {
                                selectedSemesterId = semId
                                expanded = false
                            }
                        )
                    }
                }
            }

            // GPA summary card
            if (selectedSemester != null) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val gradeDistribution = selectedSemester.grades?.groupBy { it.grade }
                        val totalCourses = selectedSemester.grades?.size ?: 0
                        val sCount = gradeDistribution?.get("S")?.size ?: 0
                        val aCount = gradeDistribution?.get("A")?.size ?: 0
                        val bCount = gradeDistribution?.get("B")?.size ?: 0
                        val otherCount = totalCourses - sCount - aCount - bCount

                        StatCircle("S", sCount.toString(), colors.chart1)
                        StatCircle("A", aCount.toString(), colors.chart2)
                        StatCircle("B", bCount.toString(), colors.chart3)
                        StatCircle("Other", otherCount.toString(), colors.textMuted)
                    }
                }

                // Course grade list
                selectedSemester.grades?.forEach { gradeItem ->
                    GradeCard(gradeItem, colors)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a semester to view grades.",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCircle(label: String, value: String, color: Color) {
    val colors = AmazeTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = AmazeTheme.typography.heading.copy(
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp
            )
        )
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(
                color = colors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun GradeCard(gradeItem: GradeItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val gradeColor = when (gradeItem.grade) {
        "S" -> colors.chart1
        "A" -> colors.chart2
        "B" -> colors.chart3
        "C" -> colors.chart3
        "D", "E", "F", "N" -> colors.chart5
        else -> colors.textSecondary
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(gradeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = gradeItem.grade,
                    style = AmazeTheme.typography.subheading.copy(
                        color = gradeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gradeItem.courseCode,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = gradeItem.courseTitle,
                    style = AmazeTheme.typography.body.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = gradeItem.grandTotal,
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = gradeItem.courseType,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
