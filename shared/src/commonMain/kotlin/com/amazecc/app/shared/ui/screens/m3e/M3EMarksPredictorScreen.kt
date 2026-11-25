package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailAction
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.domain.overhaul.M3ECoursePredictionItem
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorState
import com.amazecc.app.shared.domain.overhaul.M3EPredictedGpaBreakdown
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Marks & GPA Predictor Screen.
 *
 * Direct replication of AmazeCC's `MarksPredictorTab.tsx`:
 * - Master-detail shape: list of subjects scanning points lost, opening per-course predictor page
 * - SGPA Projected & CGPA Projected hero tiles with badge and max/desirable projections
 * - Section header with count and "+ Add Subject" modal
 * - Courses sorted by points lost descending (furthest behind at top)
 * - Row with course title, code, type (ETL, TO, LO), credits, -X.X pts lost, and grade badge
 * - Edit mode: Eye/EyeOff toggle to exclude subjects from GPA, Trash icon to delete custom subjects
 * - Overall reset sheet with destructive confirmation
 * - Deep integration with UniCC API class statistics and What-If CoursePredictorSection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3EMarksPredictorScreen(
    state: M3EMarksPredictorState,
    onAction: (M3EMarksPredictorAction) -> Unit,
    modifier: Modifier = Modifier,
    activeCourseDetailState: M3ECourseDetailState? = null,
    onCourseDetailAction: (M3ECourseDetailAction) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val colors = M3ETheme.colors

    var isResetConfirmOpen by remember { mutableStateOf(false) }
    var deleteCourseCode by remember { mutableStateOf<String?>(null) }
    var isAddCustomOpen by remember { mutableStateOf(false) }

    val activeCourse = remember(state.courses, state.activeCourseCode) {
        state.courses.firstOrNull { it.courseCode == state.activeCourseCode }
    }

    val pageTitle = if (state.viewMode == "course") {
        activeCourse?.courseCode ?: "Course"
    } else {
        "Marks Predictor"
    }

    val pageSubtitle = if (state.viewMode == "course") {
        activeCourse?.courseTitle ?: "Course Prediction & Solver"
    } else {
        "Pick a course to test what-if scores and see exactly what each remaining assessment can still earn."
    }

    M3EPageShell(
        title = pageTitle,
        eyebrow = "Academics · Simulator",
        subtitle = pageSubtitle,
        onBack = {
            if (state.viewMode == "course") {
                onAction(M3EMarksPredictorAction.BackToList)
            } else {
                onBack()
            }
        },
        actions = {
            if (state.viewMode == "course") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "What-if",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                }
            } else {
                IconButton(
                    onClick = { onAction(M3EMarksPredictorAction.ToggleEditMode(!state.isEditMode)) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLow)
                ) {
                    Icon(
                        imageVector = if (state.isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = if (state.isEditMode) "Done editing" else "Edit subjects",
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = { isResetConfirmOpen = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLow)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset all stats",
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        if (state.viewMode == "course") {
            // Per-course predictor page (CoursePredictorSection)
            if (activeCourseDetailState != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    CoursePredictorSection(
                        state = activeCourseDetailState,
                        onAction = onCourseDetailAction
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading course prediction...",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        } else {
            // List mode
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Top Stat Tiles Grid (2 columns: SGPA Projected, CGPA Projected)
                item(key = "gpa_stat_tiles") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // SGPA Projected Tile
                        val sgpaSub = if (state.gpa.optimalCredits < state.gpa.semesterCredits) {
                            "${state.gpa.optimalCredits}/${state.gpa.semesterCredits} graded · max ${state.gpa.maximisableSgpa.toFixed(2)}"
                        } else {
                            "maximisable ${state.gpa.maximisableSgpa.toFixed(2)}"
                        }
                        M3EPredictorStatTile(
                            label = "SGPA Projected",
                            value = if (state.gpa.optimalCredits > 0) state.gpa.optimalSgpa.toFixed(2) else "—",
                            badge = "${state.gpa.optimalCredits} cr",
                            badgeContainer = colors.primaryContainer,
                            badgeContent = colors.onPrimaryContainer,
                            sub = sgpaSub,
                            modifier = Modifier.weight(1f)
                        )

                        // CGPA Projected Tile
                        val cgpaBadge = when {
                            state.gpa.optimalCgpa == null -> "—"
                            state.gpa.optimalCgpaIsAnchor -> "VTOP"
                            else -> "Projected"
                        }
                        val cgpaSub = state.gpa.desirableCgpa?.let {
                            "most desirable ${it.toFixed(2)}"
                        } ?: "sync grades for a base figure"

                        M3EPredictorStatTile(
                            label = "CGPA Projected",
                            value = state.gpa.optimalCgpa?.let { it.toFixed(2) } ?: "—",
                            badge = cgpaBadge,
                            badgeContainer = colors.secondaryContainer,
                            badgeContent = colors.onSecondaryContainer,
                            sub = cgpaSub,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Section Header: Courses + Add Subject button
                item(key = "courses_section_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Science,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = "Courses",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceContainerHighest)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${state.courses.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            // Ghost Button: + Add Subject
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { isAddCustomOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Add Subject",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary
                                )
                            }
                        }

                        Text(
                            text = "Sorted by where you have the most room to improve. Tap one to simulate it.",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                // 3. Courses List or Empty State
                if (state.courses.isEmpty()) {
                    item(key = "empty_courses") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No courses found",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = "Pull your marks from VTOP, or add a custom subject to simulate.",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = state.courses,
                        key = { it.courseCode }
                    ) { course ->
                        M3EPredictorCourseRowItem(
                            course = course,
                            isEditMode = state.isEditMode,
                            onClick = { onAction(M3EMarksPredictorAction.OpenCourse(course.courseCode)) },
                            onToggleIgnored = { onAction(M3EMarksPredictorAction.ToggleCourseIgnored(course.courseCode)) },
                            onDeleteCustom = { deleteCourseCode = course.courseCode }
                        )
                    }
                }

                // Bottom navigation padding
                item(key = "bottom_padding") {
                    Spacer(Modifier.height(140.dp))
                }
            }
        }
    }

    // Modal: Add Custom Subject
    if (isAddCustomOpen) {
        AddCustomSubjectDialog(
            onDismiss = { isAddCustomOpen = false },
            onAdd = { code, title, type, credits ->
                onAction(M3EMarksPredictorAction.AddCustomCourse(code, title, type, credits))
                isAddCustomOpen = false
            }
        )
    }

    // Sheet: Reset All Confirmation
    if (isResetConfirmOpen) {
        PredictorConfirmDialog(
            title = "Reset every course's stats?",
            body = "All saved changes drop — what-if marks, custom regimens, goals and any custom subjects. Every course row returns to the marks VTOP published, and the SGPA pair recomputes from those. This cannot be undone.",
            confirmLabel = "Reset to zero",
            confirmColor = colors.error,
            onDismiss = { isResetConfirmOpen = false },
            onConfirm = {
                onAction(M3EMarksPredictorAction.ResetAll)
                isResetConfirmOpen = false
            }
        )
    }

    // Sheet: Delete Custom Subject Confirmation
    deleteCourseCode?.let { code ->
        val customCourse = state.courses.firstOrNull { it.courseCode == code }
        val title = customCourse?.courseTitle ?: code
        PredictorConfirmDialog(
            title = "Remove $title?",
            body = "The subject is removed from the list together with everything saved against it — its regimen, what-if marks and goal. Adding it back starts fresh.",
            confirmLabel = "Remove subject",
            confirmColor = colors.error,
            onDismiss = { deleteCourseCode = null },
            onConfirm = {
                onAction(M3EMarksPredictorAction.DeleteCustomCourse(code))
                deleteCourseCode = null
            }
        )
    }
}

/**
 * Top Stat Tile corresponding to AmazeCC's StatTile.
 */
@Composable
private fun M3EPredictorStatTile(
    label: String,
    value: String,
    badge: String,
    badgeContainer: Color,
    badgeContent: Color,
    sub: String,
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeContent,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colors.onSurface
            )

            Text(
                text = sub,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Row item in the Marks Predictor course list.
 */
@Composable
private fun M3EPredictorCourseRowItem(
    course: M3ECoursePredictionItem,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onToggleIgnored: () -> Unit,
    onDeleteCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main row button
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            cornerRadius = 14.dp,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .alpha(if (course.isIgnored) 0.55f else 1.0f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Title + Subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = course.courseTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${course.courseCode} · ${course.typeLabel} · ${course.credits} credits",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Right: Points Lost + Grade Badge + Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = if (course.pointsLost > 0.0) "-${course.pointsLost.toFixed(1)}" else "0.0",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface
                        )
                        Text(
                            text = "pts lost",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    if (course.isIgnored) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 36.dp, minHeight = 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceContainerHighest)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ignored",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurfaceVariant
                            )
                        }
                    } else {
                        val grade = course.predictedGrade ?: "—"
                        val tone = M3EGradeComputations.toneForGrade(grade)
                        M3EGradeToneBadge(
                            text = grade,
                            tone = tone
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Edit mode controls
        if (isEditMode) {
            if (course.isCustom) {
                IconButton(
                    onClick = onDeleteCustom,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLow)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove subject",
                        tint = colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onToggleIgnored,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLow)
                ) {
                    Icon(
                        imageVector = if (course.isIgnored) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (course.isIgnored) "Include in GPA" else "Exclude from GPA",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Destructive Confirmation Dialog for Reset All or Delete Subject.
 */
@Composable
private fun PredictorConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = M3ETheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = confirmColor
            )
        },
        text = {
            Text(
                text = body,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Dialog to add a custom subject.
 */
@Composable
private fun AddCustomSubjectDialog(
    onDismiss: () -> Unit,
    onAdd: (code: String, title: String, type: String, credits: Int) -> Unit
) {
    val colors = M3ETheme.colors
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var credits by remember { mutableIntStateOf(4) }
    var type by remember { mutableStateOf("Embedded Theory") }

    val types = listOf(
        "Embedded Theory" to "Embedded (ETL)",
        "Theory Only" to "Theory Only (TO)",
        "Lab Only" to "Lab Only (LO)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Subject",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Course Code (e.g. CS3001)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title (e.g. Cloud Computing)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Credits",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { cr ->
                        FilterChip(
                            selected = credits == cr,
                            onClick = { credits = cr },
                            label = { Text("$cr cr") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary
                            )
                        )
                    }
                }

                Text(
                    text = "Course Type",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    types.forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = type == typeKey,
                            onClick = { type = typeKey },
                            label = { Text(typeLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isNotBlank() && title.isNotBlank()) {
                        onAdd(code.trim(), title.trim(), type, credits)
                    }
                },
                enabled = code.isNotBlank() && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add Subject", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    )
}
