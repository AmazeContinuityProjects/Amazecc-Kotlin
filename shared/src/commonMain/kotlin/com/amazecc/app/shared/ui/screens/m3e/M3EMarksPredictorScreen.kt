package com.amazecc.app.shared.ui.screens.m3e

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECoursePredictionItem
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Marks & GPA Predictor Screen.
 * 100% Stateless: accepts [M3EMarksPredictorState] and emits [M3EMarksPredictorAction].
 * Features Predicted SGPA Hero, Grade selector chips, and Required FAT calculations.
 */
@Composable
fun M3EMarksPredictorScreen(
    state: M3EMarksPredictorState,
    onAction: (M3EMarksPredictorAction) -> Unit,
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
            // Header Row
            item(key = "header_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Marks & GPA Predictor",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Simulate target grades & calculate required exam scores",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3EMarksPredictorAction.ResetAll) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerLow)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Targets",
                            tint = colors.onSurface
                        )
                    }
                }
            }

            // Honest empty state: never present a predicted number when there is no real data.
            if (state.errorMessage != null) {
                item(key = "predictor_no_data") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.STANDARD,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Not enough data",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = state.errorMessage.orEmpty(),
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Predicted SGPA Hero Card
            item(key = "predicted_gpa_hero") {
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
                                text = "Predicted Semester GPA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = if (state.errorMessage != null) "—" else state.predictedGpa.toFixed(2),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Simulated Credits",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "${state.totalCreditsSimulated}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }

            // Courses Simulation List
            items(
                items = state.courses,
                key = { it.courseCode }
            ) { course ->
                M3EPredictionCourseCard(
                    course = course,
                    onSelectGrade = { grade ->
                        onAction(M3EMarksPredictorAction.SetCourseGrade(course.courseCode, grade))
                    }
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
private fun M3EPredictionCourseCard(
    course: M3ECoursePredictionItem,
    onSelectGrade: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val gradeOptions = listOf("S", "A", "B", "C", "D")

    val toneColor = when (course.tone) {
        M3ECardTone.GOOD -> colors.success
        M3ECardTone.WARNING -> colors.warning
        M3ECardTone.CRITICAL -> colors.error
        M3ECardTone.NEUTRAL -> colors.primary
        else -> colors.primary
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Code + Credits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

            // Course Title
            Text(
                text = course.courseTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Current Internal Marks Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Internals",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = if (course.internalMax > 0.0) {
                            "${course.internalScored.toFixed(2)} / ${course.internalMax.toFixed(2)}"
                        } else "Not available",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                val progress = if (course.internalMax > 0.0) {
                    (course.internalScored / course.internalMax).toFloat()
                } else 0f

                M3EWavyLinearProgress(
                    progress = progress,
                    activeColor = colors.primary,
                    trackColor = colors.surfaceContainerHighest
                )
            }

            // Grade Selector
            Text(
                text = if (course.selectedGrade.isBlank()) "Target Grade (not published — choose one)" else "Target Grade",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant
            )

            M3EConnectedButtonGroup(
                items = gradeOptions,
                selectedItem = course.selectedGrade,
                onItemSelected = onSelectGrade
            )

            // Required FAT Mark Result
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(toneColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Required in Final Exam (FAT):",
                        fontSize = 12.sp,
                        color = toneColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = course.requiredFatMark?.let { "${it.toFixed(0)} / 100" }
                            ?: if (course.internalMax > 0.0) "Choose a grade" else "Not available",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = toneColor
                    )
                }
            }
        }
    }
}

private fun createMockPredictorState() = M3EMarksPredictorState(
    predictedGpa = 9.25,
    totalCreditsSimulated = 24,
    courses = listOf(
        M3ECoursePredictionItem(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            internalScored = 46.0,
            internalMax = 50.0,
            selectedGrade = "S",
            requiredFatMark = 82.0,
            tone = M3ECardTone.GOOD
        ),
        M3ECoursePredictionItem(
            courseCode = "MAT2001",
            courseTitle = "Complex Variables & Linear Algebra",
            credits = 4,
            internalScored = 38.0,
            internalMax = 50.0,
            selectedGrade = "A",
            requiredFatMark = 76.0,
            tone = M3ECardTone.WARNING
        )
    )
)

@Preview
@Composable
private fun M3EMarksPredictorScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EMarksPredictorScreen(state = createMockPredictorState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EMarksPredictorScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EMarksPredictorScreen(state = createMockPredictorState(), onAction = {})
    }
}
