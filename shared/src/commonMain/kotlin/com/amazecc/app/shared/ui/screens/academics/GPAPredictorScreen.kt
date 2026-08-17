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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.amazecc.app.shared.state.AppState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.state.AcademicDerivers.toAttendanceItem
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.HeroCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel

private val gradePointMap = mapOf(
    "S" to 10.0, "A" to 9.0, "B" to 8.0, "C" to 7.0,
    "D" to 6.0, "E" to 5.0, "F" to 0.0
)

@Composable
fun GPAPredictorScreen() {
    val colors = AmazeTheme.colors
    val academic by AppState.academic.collectAsState()
    val selectedSem by AppState.selectedSemester.collectAsState()

    val sem = academic.semesters[selectedSem]
    val currentCgpa = sem?.gpa?.trim()?.toDoubleOrNull() ?: 0.0
    val creditsEarned = sem?.courses?.values.orEmpty()
        .filter { it.grade != null }
        .mapNotNull { it.credits?.trim()?.toDoubleOrNull() }
        .sum()

    var activeMode by remember { mutableStateOf("project") }

    var coursesInitialized by remember { mutableStateOf(false) }
    var courses by remember { mutableStateOf(listOf<ProjectedCourse>()) }

    val attendanceCourses = sem?.courses?.values?.map { it.toAttendanceItem() }.orEmpty()
    LaunchedEffect(attendanceCourses) {
        if (!coursesInitialized) {
            val att = attendanceCourses.filter { it.courseCode.isNotBlank() }
            if (att.isNotEmpty()) {
                courses = att
                    .distinctBy { "${it.courseCode}|${it.courseType}" }
                    .groupBy { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() }
                    .flatMap { (_, items) ->
                        val theory = items.firstOrNull { a ->
                            !(a.courseType.lowercase().contains("lab") || a.slotName.uppercase().startsWith("L"))
                        }
                        val lab = items.firstOrNull { a ->
                            a.courseType.lowercase().contains("lab") || a.slotName.uppercase().startsWith("L")
                        }
                        if (theory != null && lab != null) {
                            listOf(
                                ProjectedCourse(
                                    name = cleanCourseTitle(theory.courseTitle).cleanTitle.ifBlank { theory.courseTitle.ifBlank { "Course" } },
                                    credits = (theory.credits?.toDoubleOrNull() ?: 3.0) + (lab.credits?.toDoubleOrNull() ?: 0.0),
                                    grade = "A"
                                )
                            )
                        } else {
                            items.map {
                                ProjectedCourse(
                                    name = it.courseTitle.ifBlank { "Course" },
                                    credits = it.credits?.toDoubleOrNull() ?: 3.0,
                                    grade = "A"
                                )
                            }
                        }
                    }
                coursesInitialized = true
            }
        }
    }

    // What-if mode state
    val savedGoal by AppState.gpaGoal.collectAsState()
    var targetCgpa by remember(savedGoal) { mutableStateOf(savedGoal) }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSpacer()

            // Hero Gradient Card (Settings & Exam Schedule design language)
            HeroCard(colors = colors, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) { p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CGPA Predictor & Target", style = AmazeTheme.typography.heading.copy(color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        Text("Simulate grades & track GPA goals", style = AmazeTheme.typography.caption.copy(color = p.textSecondary))
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(p.iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            fmt2(projectedCgpa),
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Black,
                                color = p.text,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = p.divider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current CGPA", color = p.statLabel, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold)
                        Text(fmt2(currentCgpa), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(p.divider))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Credits Earned", color = p.statLabel, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold)
                        Text(fmt0(creditsEarned), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(p.divider))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Target Goal", color = p.statLabel, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold)
                        Text(if (targetCgpa.isBlank()) "9.00" else targetCgpa, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Mode toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "project" to "Project GPA",
                    "whatif" to "What Grade?",
                    "course" to "Course Targets"
                ).forEach { (modeKey, label) ->
                    val isSelected = activeMode == modeKey
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = bouncySpring()
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.surface)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { activeMode = modeKey }
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colors.background else colors.textPrimary,
                                fontSize = AmazeTheme.fontSize.xs
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (activeMode == "course") {
                CourseTargetMode(
                    calendarRes = AppState.calendar.collectAsState().value,
                    colors = colors
                )
            } else if (activeMode == "project") {
                InteractiveGradeCanvas(
                    courses = courses,
                    onCourseChange = { idx, updated ->
                        val newList = courses.toMutableList()
                        newList[idx] = updated
                        courses = newList
                    },
                    onAdd = { name, cred, grade ->
                        courses = courses + ProjectedCourse(name, cred, grade)
                    },
                    onRemove = { idx ->
                        courses = courses.toMutableList().apply { removeAt(idx) }
                    },
                    colors = colors
                )
            } else {
                WhatIfMode(
                    targetCgpa = targetCgpa,
                    onTargetCgpaChange = {
                        targetCgpa = it
                        AppState.setGpaGoal(it)
                    },
                    courses = courses,
                    onCourseGradeChange = { idx, grade ->
                        val newList = courses.toMutableList()
                        newList[idx] = newList[idx].copy(grade = grade)
                        courses = newList
                    },
                    creditsEarned = creditsEarned,
                    totalOldPoints = totalOldPoints,
                    colors = colors
                )
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xl))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
        Text(
            text = value,
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = color)
        )
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(color = AmazeTheme.colors.textSecondary)
        )
    }
}

@Composable
private fun InteractiveGradeCanvas(
    courses: List<ProjectedCourse>,
    onCourseChange: (Int, ProjectedCourse) -> Unit,
    onAdd: (String, Double, String) -> Unit,
    onRemove: (Int) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val gradeList = listOf("F", "E", "D", "C", "B", "A", "S")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Tactile Grade Balancing",
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        Text(
            text = "Slide your expected grades to dynamically balance your CGPA.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )

        courses.forEachIndexed { index, course ->
            val gradeIndex = gradeList.indexOf(course.grade).coerceAtLeast(0)
            
            val gradeColor = when (course.grade) {
                "S" -> colors.success
                "A" -> colors.accent
                "B" -> colors.warning
                else -> colors.danger
            }

            AmazeCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(gradeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                course.grade,
                                style = AmazeTheme.typography.body.copy(color = gradeColor, fontWeight = FontWeight.Black)
                            )
                        }
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.name, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.clickable { if (course.credits > 1.0) onCourseChange(index, course.copy(credits = course.credits - 1.0)) }.background(colors.surface, CircleShape).padding(4.dp)) {
                                    Icon(Icons.Rounded.Remove, null, modifier = Modifier.size(14.dp), tint = colors.textSecondary)
                                }
                                Text("${course.credits.toInt()} Credits", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), modifier = Modifier.padding(horizontal = 8.dp))
                                Box(modifier = Modifier.clickable { onCourseChange(index, course.copy(credits = course.credits + 1.0)) }.background(colors.surface, CircleShape).padding(4.dp)) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(14.dp), tint = colors.textSecondary)
                                }
                            }
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Rounded.Close, null, tint = colors.danger)
                        }
                    }
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Slider(
                        value = gradeIndex.toFloat(),
                        onValueChange = { newIdx ->
                            val newGrade = gradeList[newIdx.toInt()]
                            if (newGrade != course.grade) {
                                onCourseChange(index, course.copy(grade = newGrade))
                            }
                        },
                        valueRange = 0f..6f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = gradeColor,
                            activeTrackColor = gradeColor,
                            inactiveTrackColor = colors.border
                        )
                    )
                }
            }
        }

        AmazeButton(
            text = "Add Course",
            onClick = { onAdd("New Course", 3.0, "A") },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.SECONDARY,
            icon = Icons.Rounded.Add
        )
    }
}

@Composable
private fun WhatIfMode(
    targetCgpa: String,
    onTargetCgpaChange: (String) -> Unit,
    courses: List<ProjectedCourse>,
    onCourseGradeChange: (Int, String) -> Unit,
    creditsEarned: Double,
    totalOldPoints: Double,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    // index -> grade the user has locked in for that course. Courses without a pin
    // get filled in automatically by the combination engine.
    val pinned = remember { mutableStateMapOf<Int, String>() }
    val validPins = pinned.filterKeys { it in courses.indices }

    val gradeColor = mapOf(
        "S" to colors.success, "A" to colors.accent, "B" to colors.warning,
        "C" to colors.chart1, "D" to colors.chart3, "E" to colors.chart4, "F" to colors.danger
    )

    val totalCredits = courses.sumOf { it.credits }
    val currentTotal = courses.sumOf { (gradePointMap[it.grade] ?: 0.0) * it.credits }
    val currentCgpa = if (creditsEarned + totalCredits > 0.0)
        (totalOldPoints + currentTotal) / (creditsEarned + totalCredits) else 0.0
    val target = targetCgpa.toDoubleOrNull()

    val comboResult = remember(target, courses, validPins) {
        if (target == null || courses.isEmpty()) null
        else {
            val needPoints = target * (creditsEarned + totalCredits) - totalOldPoints
            minimumEffortCombo(courses, validPins, needPoints)
        }
    }

    val allSCgpa = if (creditsEarned + totalCredits > 0.0)
        (totalOldPoints + totalCredits * 10.0) / (creditsEarned + totalCredits) else 0.0

    val recommendedCgpa = comboResult?.let { res ->
        if (res.feasible) {
            val comboPoints = res.combo.entries.sumOf { (i, g) -> (gradePointMap[g] ?: 0.0) * courses[i].credits } +
                validPins.entries.sumOf { (i, g) -> (gradePointMap[g] ?: 0.0) * courses[i].credits }
            if (creditsEarned + totalCredits > 0.0)
                (totalOldPoints + comboPoints) / (creditsEarned + totalCredits) else 0.0
        } else null
    }

    val alreadyOnTarget = target != null && currentCgpa >= target - 0.0001

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Grade, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column {
                Text(
                    text = "What Grade Do I Need?",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    text = "Enter a target CGPA — tap grades to lock them in, the rest auto-adjust.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }

        SettingsGroupLabel("Target CGPA")
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.accent.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Adjust, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target CGPA", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("e.g. 9.5", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                    OutlinedTextField(
                        value = targetCgpa,
                        onValueChange = { onTargetCgpaChange(it.filter { c -> c.isDigit() || c == '.' }) },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        textStyle = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("8.0", "8.5", "9.0", "9.5").forEach { preset ->
                        val isActive = targetCgpa == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(if (isActive) colors.accent else colors.surface)
                                .border(1.dp, if (isActive) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.xs))
                                .clickable { onTargetCgpaChange(preset) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                preset,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = if (isActive) colors.background else colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AmazeTheme.fontSize.xs
                                )
                            )
                        }
                    }
                }
            }
        }

        if (courses.isEmpty()) {
            AmazeCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.textMuted.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.School, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text(
                        "No semester courses loaded. Sync attendance to populate this list.",
                        color = colors.textSecondary,
                        style = AmazeTheme.typography.caption,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return
        }

        SettingsGroupLabel("Course Grades")
        CoursePinsGroupCard(
            courses = courses,
            validPins = validPins,
            comboResult = comboResult,
            gradeColor = gradeColor,
            onTogglePin = { idx, grade ->
                if (idx < 0) pinned.clear()
                else if (grade == null) pinned.remove(idx)
                else pinned[idx] = grade
            },
            colors = colors
        )

        SettingsGroupLabel("Recommendation")
        when {
            target == null -> {
                AmazeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(
                            "Enter a target CGPA above to see the grade combination you need.",
                            color = colors.textSecondary,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
            alreadyOnTarget -> {
                ResultCard(
                    title = "You're already there",
                    message = "Your current grades project to ${fmt2(currentCgpa)} — that's at or above your target of ${fmt2(target)}.",
                    color = colors.success,
                    icon = Icons.Rounded.CheckCircle,
                    colors = colors
                )
            }
            comboResult != null && comboResult.feasible -> {
                RecommendationCard(
                    combo = comboResult.combo,
                    courses = courses,
                    validPins = validPins,
                    gradeColor = gradeColor,
                    recommendedCgpa = recommendedCgpa ?: 0.0,
                    target = target ?: 0.0,
                    allSCgpa = allSCgpa,
                    onApply = {
                        comboResult.combo.forEach { (idx, grade) -> onCourseGradeChange(idx, grade) }
                        pinned.clear()
                    },
                    colors = colors
                )
            }
            else -> {
                ResultCard(
                    title = "Not achievable",
                    message = "Even with an S in every remaining course you can only reach ${fmt2(allSCgpa)}. Lower your target or improve your earlier-semester CGPA.",
                    color = colors.danger,
                    icon = Icons.Rounded.Cancel,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    message: String,
    color: Color,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = color.copy(alpha = 0.08f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Text(
                    title,
                    style = AmazeTheme.typography.smallLabel.copy(color = color, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xs)
                )
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
            Text(
                message,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary)
            )
        }
    }
}

@Composable
private fun CoursePinsGroupCard(
    courses: List<ProjectedCourse>,
    validPins: Map<Int, String>,
    comboResult: ComboResult?,
    gradeColor: Map<String, Color>,
    onTogglePin: (Int, String?) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Course Grades",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${courses.size} course${if (courses.size != 1) "s" else ""} · tap a grade to lock it in, the rest auto-adjust",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (validPins.isNotEmpty()) {
                    TextButton(onClick = { onTogglePin(-1, null) }) {
                        Text("Clear", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
                    }
                }
            }

            courses.forEachIndexed { index, course ->
                if (index > 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                CourseGradeRow(
                    course = course,
                    pinnedGrade = validPins[index],
                    recommended = comboResult?.combo?.get(index),
                    gradeColor = gradeColor,
                    onTogglePin = { grade -> onTogglePin(index, grade) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun CourseGradeRow(
    course: ProjectedCourse,
    pinnedGrade: String?,
    recommended: String?,
    gradeColor: Map<String, Color>,
    onTogglePin: (String?) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val gradeList = listOf("S", "A", "B", "C", "D", "E", "F")
    val isPinned = pinnedGrade != null
    val displayGrade = pinnedGrade ?: recommended
    val displayColor = when {
        pinnedGrade != null -> gradeColor[pinnedGrade] ?: colors.textMuted
        recommended != null -> gradeColor[recommended] ?: colors.textMuted
        else -> colors.textMuted
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(displayColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    displayGrade ?: "—",
                    style = AmazeTheme.typography.body.copy(color = displayColor, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.sm)
                )
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        course.name,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Lock, null, tint = colors.accent, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${course.credits.toInt()} credits",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
            if (isPinned) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "PINNED",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            val autoSelected = !isPinned
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                    .background(if (autoSelected) colors.accent else colors.surface)
                    .border(1.dp, if (autoSelected) colors.accent else colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.xs))
                    .clickable { onTogglePin(null) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "AUTO",
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = if (autoSelected) colors.background else colors.textMuted,
                        fontWeight = FontWeight.Black,
                        fontSize = AmazeTheme.fontSize.micro
                    )
                )
            }
            gradeList.forEach { g ->
                val isPinnedGrade = pinnedGrade == g
                val isRecommended = !isPinnedGrade && recommended == g
                val gColor = gradeColor[g] ?: colors.textMuted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(
                            when {
                                isPinnedGrade -> gColor
                                isRecommended -> gColor.copy(alpha = 0.14f)
                                else -> colors.surface
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isPinnedGrade -> gColor
                                isRecommended -> gColor.copy(alpha = 0.7f)
                                else -> colors.border.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(AmazeTheme.radius.xs)
                        )
                        .clickable {
                            if (isPinnedGrade) onTogglePin(null) else onTogglePin(g)
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        g,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isPinnedGrade) Color.White else gColor,
                            fontWeight = FontWeight.Black,
                            fontSize = AmazeTheme.fontSize.micro
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    combo: Map<Int, String>,
    courses: List<ProjectedCourse>,
    validPins: Map<Int, String>,
    gradeColor: Map<String, Color>,
    recommendedCgpa: Double,
    target: Double,
    allSCgpa: Double,
    onApply: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = colors.success.copy(alpha = 0.08f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column {
                    Text(
                        "Recommended Grade Combination",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.success, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xs)
                    )
                    Text(
                        "Minimum effort path to ${fmt2(target)}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GpaMetricTile("Projected CGPA", fmt2(recommendedCgpa), colors.success, colors, Modifier.weight(1f))
                GpaMetricTile("Target", fmt2(target), colors.accent, colors, Modifier.weight(1f))
                GpaMetricTile("Max Possible", fmt2(allSCgpa), colors.textSecondary, colors, Modifier.weight(1f))
            }

            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

            courses.forEachIndexed { index, course ->
                val grade = combo[index] ?: validPins[index] ?: return@forEachIndexed
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background((gradeColor[grade] ?: colors.textMuted).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            grade,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = gradeColor[grade] ?: colors.textMuted,
                                fontWeight = FontWeight.Black,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Text(
                        course.name,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${course.credits.toInt()} cr)",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            AmazeButton(
                text = "Apply Recommended Grades",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GpaMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(8.dp)
    ) {
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = valueColor, fontSize = AmazeTheme.fontSize.base),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class ComboResult(
    val feasible: Boolean,
    val combo: Map<Int, String>
)

// Finds the minimum-effort grade assignment (for unpinned courses) whose total points
// reach needPoints. Pinned courses contribute their fixed points. DP over total points.
private fun minimumEffortCombo(
    courses: List<ProjectedCourse>,
    pinned: Map<Int, String>,
    needPoints: Double
): ComboResult {
    val scale = 2
    fun scaled(grade: String, credits: Double): Int =
        ((gradePointMap[grade] ?: 0.0) * credits * scale).roundToInt()

    val unpinned = courses.indices.filter { it !in pinned }
    val fixedPoints = pinned.entries.sumOf { (i, g) -> (gradePointMap[g] ?: 0.0) * courses[i].credits }
    val remainingNeed = ((needPoints - fixedPoints).coerceAtLeast(0.0) * scale).roundToInt()
    val maxTotal = unpinned.sumOf { scaled("S", courses[it].credits) }

    if (remainingNeed <= 0) return ComboResult(true, emptyMap())
    if (maxTotal < remainingNeed) return ComboResult(false, emptyMap())

    val gradeList = listOf("S", "A", "B", "C", "D", "E", "F")
    var reachable = BooleanArray(maxTotal + 1)
    reachable[0] = true
    val parents = Array(unpinned.size) { Array(maxTotal + 1) { -1 to -1 } }

    unpinned.forEachIndexed { step, idx ->
        val opts = gradeList.map { g -> scaled(g, courses[idx].credits) }
        val next = BooleanArray(maxTotal + 1)
        for (t in 0..maxTotal) {
            if (!reachable[t]) continue
            opts.forEachIndexed { gi, pts ->
                val nt = t + pts
                if (nt <= maxTotal && !next[nt]) {
                    next[nt] = true
                    parents[step][nt] = t to gi
                }
            }
        }
        reachable = next
    }

    var bestTotal = -1
    for (t in remainingNeed..maxTotal) {
        if (reachable[t]) {
            bestTotal = t
            break
        }
    }
    if (bestTotal < 0) return ComboResult(false, emptyMap())

    val combo = mutableMapOf<Int, String>()
    var t = bestTotal
    for (step in unpinned.indices.reversed()) {
        val (prevT, gi) = parents[step][t]
        combo[unpinned[step]] = gradeList[gi]
        t = prevT
    }
    return ComboResult(true, combo)
}

private fun fmt2(v: Double): String {
    val i = kotlin.math.round(v * 100).toLong()
    val w = i / 100
    val f = (i % 100).coerceIn(0, 99)
    return "$w.${f.toString().padStart(2, '0')}"
}

private fun fmt0(v: Double): String = v.toInt().toString()

private data class ProjectedCourse(
    val name: String,
    val credits: Double,
    val grade: String
)


@Composable
private fun CourseTargetMode(
    calendarRes: com.amazecc.app.shared.model.CalendarRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var courseType by remember { mutableStateOf("Theory") }
    var targetGrade by remember { mutableStateOf("S") }
    
    var cat1Marks by remember { mutableStateOf("") }
    var cat2Marks by remember { mutableStateOf("") }
    var quizMarks by remember { mutableStateOf("") }
    var labInternals by remember { mutableStateOf("") }
    
    // Evaluate calendar for CAT dates
    // Date logic omitted for simplicity
    
    var cat1Date: kotlinx.datetime.LocalDate? = null
    var cat2Date: kotlinx.datetime.LocalDate? = null
    
    calendarRes?.months?.forEach { monthObj ->
        monthObj.days.forEach { day ->
            day.events.forEach { event ->
                val txt = event.text.lowercase()
                val parsedDate = try {
                    // Try parsing month string + date, for simplicity just assume it's a rough date
                    // Since month parsing is complex without a full date formatter in KMP, we will just use a fallback heuristic.
                    null
                } catch (e: Exception) { null }
                
                if (txt.contains("continuous assessment test - i") || txt.contains("cat 1") || txt.contains("cat - i")) {
                    // It's a CAT 1 date
                    // Let's assume if we found the event, we just check if it's past or not (using a simple heuristic or we just show both fields for simplicity)
                }
            }
        }
    }
    
    val cat1Status = "Enter CAT 1 (out of 50)"
    val cat2Status = "Enter CAT 2 (out of 50)"

    val gradeTargetPoints = when (targetGrade) {
        "S" -> 90.0
        "A" -> 80.0
        "B" -> 70.0
        "C" -> 60.0
        "D" -> 50.0
        "E" -> 40.0
        else -> 90.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Predict FAT Requirements",
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Theory", "Lab", "Embedded").forEach { type ->
                AmazeButton(
                    text = type,
                    onClick = { courseType = type },
                    variant = if (courseType == type) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        OutlinedTextField(
            value = targetGrade,
            onValueChange = { targetGrade = it.uppercase().filter { c -> c in listOf('S','A','B','C','D','E','F') }.take(1) },
            label = { Text("Target Grade (S, A, B...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        if (courseType == "Theory" || courseType == "Embedded") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cat1Marks,
                    onValueChange = { cat1Marks = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(cat1Status) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = cat2Marks,
                    onValueChange = { cat2Marks = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(cat2Status) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )
            }
            OutlinedTextField(
                value = quizMarks,
                onValueChange = { quizMarks = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Assignments / Quizzes (out of 30)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }
        
        if (courseType == "Lab" || courseType == "Embedded") {
            OutlinedTextField(
                value = labInternals,
                onValueChange = { labInternals = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Lab Internals (out of 60)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }
        
        // Calculation logic
        val c1 = cat1Marks.toDoubleOrNull() ?: 0.0
        val c2 = cat2Marks.toDoubleOrNull() ?: 0.0
        val q = quizMarks.toDoubleOrNull() ?: 0.0
        val l = labInternals.toDoubleOrNull() ?: 0.0
        
        // Theory internal: CAT1 & CAT2 are each out of 50, contributing 30 marks each. Quiz contributes directly.
        val theoryInternal = (c1 / 50.0 * 30.0) + (c2 / 50.0 * 30.0) + q
        val requiredTotal = gradeTargetPoints
        
        var message = ""
        var isPossible = true
        
        if (courseType == "Theory") {
            val neededTheoryFatMarks = (requiredTotal - theoryInternal) / 0.4
            isPossible = neededTheoryFatMarks <= 100
            val actualNeed = neededTheoryFatMarks.coerceAtLeast(0.0)
            val maxAchievable = theoryInternal + (100 * 0.4)
            message = if (isPossible) {
                "You need ${fmt2(actualNeed)} / 100 in Theory FAT."
            } else {
                "Not mathematically possible to get $targetGrade. Max achievable total marks is ${fmt2(maxAchievable)}."
            }
        } else if (courseType == "Lab") {
            val neededLabFatMarks = (requiredTotal - l) / 0.4
            isPossible = neededLabFatMarks <= 100
            val actualNeed = neededLabFatMarks.coerceAtLeast(0.0)
            val maxAchievable = l + (100 * 0.4)
            message = if (isPossible) {
                "You need ${fmt2(actualNeed)} / 100 in Lab FAT."
            } else {
                "Not mathematically possible to get $targetGrade. Max achievable total marks is ${fmt2(maxAchievable)}."
            }
        } else if (courseType == "Embedded") {
            // Typical embedded weight: Theory 75%, Lab 25% or Theory 60%, Lab 40% depending on credits.
            // Let's assume generic 75% Theory, 25% Lab.
            val totalInternal = (theoryInternal * 0.75) + (l * 0.25)
            val neededFat = (requiredTotal - totalInternal) / 0.4
            isPossible = neededFat <= 100
            val actualNeed = neededFat.coerceAtLeast(0.0)
            val maxAchievable = totalInternal + (100 * 0.4)
            message = if (isPossible) {
                "Assuming 75/25 weightage. You need ${fmt2(actualNeed)} / 100 in FAT."
            } else {
                "Not mathematically possible to get $targetGrade. Max achievable total marks is ${fmt2(maxAchievable)}."
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                .background(if (isPossible) colors.surface else colors.dangerSurface)
                .border(1.dp, if (isPossible) colors.border else colors.danger, RoundedCornerShape(AmazeTheme.radius.small))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Prediction",
                    style = AmazeTheme.typography.smallLabel.copy(color = if (isPossible) colors.textSecondary else colors.dangerText)
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text(
                    text = message,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = if (isPossible) colors.textPrimary else colors.dangerText)
                )
            }
        }
    }
}
