package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

private const val TARGET_CREDITS = 160

private val mockCurriculum = listOf(
    CurriculumItem(basketTitle = "University Core", creditsRequired = "22", creditsEarned = "22"),
    CurriculumItem(basketTitle = "Program Core", creditsRequired = "48", creditsEarned = "45"),
    CurriculumItem(basketTitle = "Program Elective", creditsRequired = "24", creditsEarned = "18"),
    CurriculumItem(basketTitle = "Open Elective", creditsRequired = "12", creditsEarned = "9"),
    CurriculumItem(basketTitle = "Skill Development", creditsRequired = "10", creditsEarned = "10"),
    CurriculumItem(basketTitle = "Humanities & Social Sci.", creditsRequired = "12", creditsEarned = "8"),
    CurriculumItem(basketTitle = "Project Work", creditsRequired = "20", creditsEarned = "10"),
    CurriculumItem(basketTitle = "Internship", creditsRequired = "12", creditsEarned = "6")
)

private data class SemesterSummary(
    val semesterId: String,
    val semesterName: String,
    val gpa: String,
    val credits: Int
)

private val mockSemesters = listOf(
    SemesterSummary("CH20232401", "Fall 2023-24", "8.24", 22),
    SemesterSummary("CH20232405", "Winter 2023-24", "7.86", 23),
    SemesterSummary("CH20242501", "Fall 2024-25", "8.52", 22),
    SemesterSummary("CH20242505", "Winter 2024-25", "7.94", 20),
    SemesterSummary("CH20252601", "Fall 2025-26", "8.10", 21),
    SemesterSummary("CH20252605", "Winter 2025-26", "7.45", 18)
)

@Composable
fun CurriculumScreen() {
    val colors = AmazeTheme.colors
    val allGrades by AppState.allGrades.collectAsState()
    val marks by AppState.marks.collectAsState()

    val totalEarned = mockCurriculum.sumOf { it.creditsEarned.toIntOrNull() ?: 0 }
    val totalRequired = mockCurriculum.sumOf { it.creditsRequired.toIntOrNull() ?: 0 }

    val semesterList = remember(allGrades) {
        val gradesData = allGrades
        if (gradesData?.grades?.isNotEmpty() == true) {
            gradesData.grades.mapNotNull { (semId, result) ->
                val name = AppState.semesterMap[semId] ?: semId
                val gpa = result.gpa ?: "N/A"
                val credits = result.grades.size * 3
                SemesterSummary(semId, name, gpa, credits)
            }.sortedByDescending { it.semesterId }
        } else {
            mockSemesters
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Curriculum",
            description = "Track your degree requirements",
            showBackButton = true,
            showSyncButton = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DegreeProgressCard(earned = totalEarned, target = TARGET_CREDITS, colors = colors) }
            item { CategoriesSection(items = mockCurriculum, colors = colors) }
            item {
                Text(
                    text = "Semester Breakdown",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(semesterList) { sem -> SemesterCard(semester = sem, colors = colors) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DegreeProgressCard(
    earned: Int,
    target: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val progress = (earned.toFloat() / target).coerceIn(0f, 1f)

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.School, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Degree Progress",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
                Text(
                    text = "$earned / $target",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = colors.accent,
                trackColor = colors.border,
            )

            Text(
                text = "${(progress * 100).toInt()}% toward graduation (${target - earned} credits remaining)",
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
            )
        }
    }
}

@Composable
private fun CategoriesSection(
    items: List<CurriculumItem>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Course Categories",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )

            items.forEach { item ->
                val required = item.creditsRequired.toIntOrNull() ?: 0
                val earned = item.creditsEarned.toIntOrNull() ?: 0
                val catProgress = if (required > 0) (earned.toFloat() / required).coerceIn(0f, 1f) else 0f
                val complete = earned >= required

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (complete) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = item.basketTitle,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium)
                            )
                        }
                        Text(
                            text = "$earned/$required",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (complete) colors.success else colors.warning,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    LinearProgressIndicator(
                        progress = { catProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (complete) colors.success else colors.accent,
                        trackColor = colors.border,
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterCard(
    semester: SemesterSummary,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = semester.semesterName,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    text = "${semester.credits} credits",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
            Text(
                text = semester.gpa,
                style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 20.sp)
            )
        }
    }
}
