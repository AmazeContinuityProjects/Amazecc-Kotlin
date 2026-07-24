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
import com.amazecc.app.shared.ui.components.ScreenHeader

private val gradePointMap = mapOf(
    "S" to 10.0, "A" to 9.0, "B" to 8.0, "C" to 7.0,
    "D" to 6.0, "E" to 5.0, "F" to 0.0
)

@Composable
fun GPAPredictorScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()

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
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current CGPA card
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem("Current CGPA", fmt2(currentCgpa), Icons.Rounded.EmojiEvents, Color(0xFF10B981))
                    StatItem("Credits Earned", fmt0(creditsEarned), Icons.Rounded.School, Color(0xFF3B82F6))
                    StatItem("Projected", fmt2(projectedCgpa), Icons.AutoMirrored.Rounded.TrendingUp, colors.accent)
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
                                fontSize = 11.sp
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
                            val achievableEntry = gradePointMap.entries
                                .filter { it.value <= avgGradePoint + 0.5 }
                                .maxByOrNull { it.value }
                            neededGrade = achievableEntry?.key ?: "Impossible"
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
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPossible) colors.surface else colors.dangerSurface)
                .border(1.dp, if (isPossible) colors.border else colors.danger, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Prediction",
                    style = AmazeTheme.typography.smallLabel.copy(color = if (isPossible) colors.textSecondary else colors.dangerText)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = if (isPossible) colors.textPrimary else colors.dangerText)
                )
            }
        }
    }
}
