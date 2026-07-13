package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader

private val gradePointMap = mapOf(
    "S" to 10.0, "A" to 9.0, "B" to 8.0, "C" to 7.0,
    "D" to 6.0, "E" to 5.0, "F" to 0.0
)

@Composable
fun GPAPredictorScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val allGradesRes by AppState.allGrades.collectAsState()

    val currentCgpa = marksRes?.cgpa?.cgpa?.toDoubleOrNull() ?: 0.0
    val creditsEarned = marksRes?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0

    var activeMode by remember { mutableStateOf("project") }

    // Projection mode state
    var courses by remember { mutableStateOf(listOf<ProjectedCourse>()) }
    var newCourseName by remember { mutableStateOf("") }
    var newCourseCredits by remember { mutableStateOf("3") }
    var newCourseGrade by remember { mutableStateOf("A") }
    var gradeExpanded by remember { mutableStateOf(false) }

    // What-if mode state
    var targetCgpa by remember { mutableStateOf("") }
    var futureCredits by remember { mutableStateOf("") }
    var neededGrade by remember { mutableStateOf<String?>(null) }

    val totalOldPoints = currentCgpa * creditsEarned

    val projectedCgpa = if (courses.isEmpty()) {
        currentCgpa
    } else {
        val newTotalPoints = totalOldPoints + courses.sumOf {
            (gradePointMap[it.grade] ?: 0.0) * it.credits
        }
        val newTotalCredits = creditsEarned + courses.sumOf { it.credits }
        if (newTotalCredits > 0.0) newTotalPoints / newTotalCredits else currentCgpa
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "CGPA Predictor",
            description = "Project your CGPA or find the grade you need",
            showBackButton = true,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current CGPA card
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem("Current CGPA", "%.2f".format(currentCgpa), Icons.Rounded.EmojiEvents, Color(0xFF10B981))
                    StatItem("Credits Earned", "%.0f".format(creditsEarned), Icons.Rounded.School, Color(0xFF3B82F6))
                    StatItem("Projected", "%.2f".format(projectedCgpa), Icons.AutoMirrored.Rounded.TrendingUp, colors.accent)
                }
            }

            // Mode toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AmazeButton(
                    text = "Project GPA",
                    onClick = { activeMode = "project" },
                    modifier = Modifier.weight(1f),
                    variant = if (activeMode == "project") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
                AmazeButton(
                    text = "What Grade?",
                    onClick = { activeMode = "whatif" },
                    modifier = Modifier.weight(1f),
                    variant = if (activeMode == "whatif") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
            }

            if (activeMode == "project") {
                ProjectionMode(
                    courses = courses,
                    onAdd = { name, cred, grade ->
                        courses = courses + ProjectedCourse(name, cred, grade)
                    },
                    onRemove = { idx ->
                        courses = courses.toMutableList().apply { removeAt(idx) }
                    },
                    newCourseName = newCourseName,
                    onNewCourseNameChange = { newCourseName = it },
                    newCourseCredits = newCourseCredits,
                    onNewCourseCreditsChange = { newCourseCredits = it },
                    newCourseGrade = newCourseGrade,
                    onNewCourseGradeChange = { newCourseGrade = it },
                    gradeExpanded = gradeExpanded,
                    onGradeExpandedChange = { gradeExpanded = it },
                    colors = colors
                )
            } else {
                WhatIfMode(
                    targetCgpa = targetCgpa,
                    onTargetCgpaChange = { targetCgpa = it },
                    futureCredits = futureCredits,
                    onFutureCreditsChange = { futureCredits = it },
                    onCalculate = {
                        val target = targetCgpa.toDoubleOrNull()
                        val future = futureCredits.toDoubleOrNull()
                        if (target != null && future != null && future > 0.0) {
                            val neededPoints = target * (creditsEarned + future) - totalOldPoints
                            val avgGradePoint = neededPoints / future
                            neededGrade = gradePointMap.entries
                                .filter { it.value <= avgGradePoint + 0.5 }
                                .maxByOrNull { it.value }
                                ?.key ?: "S"
                        }
                    },
                    neededGrade = neededGrade,
                    onClear = {
                        neededGrade = null
                        targetCgpa = ""
                        futureCredits = ""
                    },
                    colors = colors
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = color, fontSize = 20.sp)
        )
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(color = AmazeTheme.colors.textSecondary, fontSize = 10.sp)
        )
    }
}

@Composable
private fun ProjectionMode(
    courses: List<ProjectedCourse>,
    onAdd: (String, Double, String) -> Unit,
    onRemove: (Int) -> Unit,
    newCourseName: String,
    onNewCourseNameChange: (String) -> Unit,
    newCourseCredits: String,
    onNewCourseCreditsChange: (String) -> Unit,
    newCourseGrade: String,
    onNewCourseGradeChange: (String) -> Unit,
    gradeExpanded: Boolean,
    onGradeExpandedChange: (Boolean) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Add Hypothetical Courses",
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )

        OutlinedTextField(
            value = newCourseName,
            onValueChange = onNewCourseNameChange,
            label = { Text("Course Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newCourseCredits,
                onValueChange = { onNewCourseCreditsChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Credits") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .clickable { onGradeExpandedChange(!gradeExpanded) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(newCourseGrade, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.textSecondary)
                    }
                }
                DropdownMenu(
                    expanded = gradeExpanded,
                    onDismissRequest = { onGradeExpandedChange(false) },
                    modifier = Modifier.background(colors.surface)
                ) {
                    gradePointMap.keys.forEach { grade ->
                        DropdownMenuItem(
                            text = { Text("$grade (${gradePointMap[grade]})", color = colors.textPrimary) },
                            onClick = {
                                onNewCourseGradeChange(grade)
                                onGradeExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        AmazeButton(
            text = "Add Course",
            onClick = {
                val creds = newCourseCredits.toDoubleOrNull() ?: return@AmazeButton
                if (newCourseName.isNotBlank() && creds > 0.0) {
                    onAdd(newCourseName, creds, newCourseGrade)
                    onNewCourseNameChange("")
                    onNewCourseCreditsChange("3")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Add
        )

        if (courses.isNotEmpty()) {
            HorizontalDivider(color = colors.border)
            Text(
                text = "Added Courses",
                style = AmazeTheme.typography.body.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            )

            courses.forEachIndexed { index, course ->
                val gradeColor = when (course.grade) {
                    "S" -> Color(0xFF10B981); "A" -> Color(0xFF3B82F6)
                    "B" -> Color(0xFFF59E0B); else -> Color(0xFFEF4444)
                }
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(gradeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                course.grade,
                                style = AmazeTheme.typography.body.copy(
                                    color = gradeColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.name, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                            Text("${course.credits.toInt()} credits", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Rounded.Close, null, tint = colors.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatIfMode(
    targetCgpa: String,
    onTargetCgpaChange: (String) -> Unit,
    futureCredits: String,
    onFutureCreditsChange: (String) -> Unit,
    onCalculate: () -> Unit,
    neededGrade: String?,
    onClear: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "What Grade Do I Need?",
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )
        Text(
            text = "Enter your target CGPA and the number of future credits to find the minimum grade required.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )

        OutlinedTextField(
            value = targetCgpa,
            onValueChange = { onTargetCgpaChange(it.filter { c -> c.isDigit() || c == '.' }) },
            label = { Text("Target CGPA (e.g. 9.5)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        OutlinedTextField(
            value = futureCredits,
            onValueChange = { onFutureCreditsChange(it.filter { c -> c.isDigit() }) },
            label = { Text("Future Credits") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        AmazeButton(
            text = "Calculate",
            onClick = onCalculate,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Calculate
        )

        if (neededGrade != null) {
            val points = gradePointMap[neededGrade] ?: 0.0
            AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.success.copy(alpha = 0.08f)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You need an average of",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                    )
                    Text(
                        text = neededGrade,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.success,
                            fontWeight = FontWeight.Black,
                            fontSize = 48.sp
                        )
                    )
                    Text(
                        text = "(${points} grade points per credit)",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AmazeButton("Clear", onClick = onClear, variant = ButtonVariant.GHOST)
                }
            }
        }
    }
}

private data class ProjectedCourse(
    val name: String,
    val credits: Double,
    val grade: String
)
