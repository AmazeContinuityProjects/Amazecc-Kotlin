package com.amazecc.app.shared.ui.screens.m3e

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
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
import com.amazecc.app.shared.domain.overhaul.CourseCompletionStatus
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.domain.overhaul.M3EBasketProgress
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumAction
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumCourse
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Degree Curriculum Tracker.
 * 100% Stateless: accepts [M3ECurriculumState] and emits [M3ECurriculumAction].
 * Features Degree Completion Hero Card, Basket breakdown chips, and Status trackers.
 */
@Composable
fun M3ECurriculumScreen(
    state: M3ECurriculumState,
    onAction: (M3ECurriculumAction) -> Unit,
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
            item(key = "curriculum_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Degree Curriculum",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = state.programmeName,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3ECurriculumAction.DownloadCurriculum) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download Curriculum",
                            tint = colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Degree Completion Hero Card
            item(key = "hero_progress_card") {
                val completionPct = if (state.totalCreditsRequired > 0) {
                    (state.totalCreditsCompleted.toDouble() / state.totalCreditsRequired.toDouble() * 100.0)
                } else 0.0

                val remainingCredits = (state.totalCreditsRequired - state.totalCreditsCompleted).coerceAtLeast(0)

                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Degree Completion",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = completionPct.toFixed(2, "%"),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.primary
                            )
                        }

                        M3EWavyLinearProgress(
                            progress = (completionPct / 100.0).toFloat(),
                            activeColor = colors.primary,
                            trackColor = colors.surfaceContainerHighest
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.totalCreditsCompleted} / ${state.totalCreditsRequired} Credits Completed",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "$remainingCredits Credits Remaining",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingCredits == 0) colors.success else colors.primary
                            )
                        }
                    }
                }
            }

            // Basket Breakdown Mini Cards
            item(key = "basket_breakdown") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.baskets) { basket ->
                        val pct = if (basket.creditsRequired > 0) {
                            (basket.creditsCompleted.toDouble() / basket.creditsRequired.toDouble() * 100.0).coerceAtMost(100.0)
                        } else 0.0

                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 16.dp,
                            modifier = Modifier.width(150.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = basket.basket.code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Text(
                                    text = basket.basket.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${basket.creditsCompleted}/${basket.creditsRequired} cr (${pct.toFixed(2)}%)",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Basket Filter Switcher
            item(key = "basket_filter") {
                M3EConnectedButtonGroup(
                    items = CurriculumBasket.entries,
                    selectedItem = state.selectedBasket,
                    onItemSelected = { onAction(M3ECurriculumAction.SelectBasket(it)) },
                    labelProvider = { it.label }
                )
            }

            // Search Bar
            item(key = "search_field") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3ECurriculumAction.UpdateSearch(it)) },
                    placeholder = { Text("Search course code or title...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    ),
                    singleLine = true
                )
            }

            // Courses List
            if (state.filteredCourses.isEmpty()) {
                item(key = "empty_courses") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No courses match the criteria",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.filteredCourses,
                    key = { "${it.courseCode}_${it.basket.name}" }
                ) { course ->
                    M3ECurriculumCourseCard(course = course)
                }
            }

            // Bottom Spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun M3ECurriculumCourseCard(
    course: M3ECurriculumCourse,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Course Code + Basket + Status Pill
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
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${course.credits} Cr • ${course.basket.code}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                when (course.status) {
                    CourseCompletionStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.successContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = course.grade?.let { "Grade $it" } ?: "Completed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.success
                                )
                            }
                        }
                    }
                    CourseCompletionStatus.IN_PROGRESS -> {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassEmpty,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "In Progress",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                    }
                    CourseCompletionStatus.NOT_STARTED -> {
                        Text(
                            text = "Yet to Complete",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Title
            Text(
                text = course.courseTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Course Type
            Text(
                text = course.courseType,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun createMockCurriculumState() = M3ECurriculumState(
    programmeName = "B.Tech Computer Science & Engineering (2021-25)",
    totalCreditsCompleted = 124,
    totalCreditsRequired = 160,
    baskets = listOf(
        M3EBasketProgress(CurriculumBasket.PROGRAM_CORE, 54, 58),
        M3EBasketProgress(CurriculumBasket.PROGRAM_ELECTIVE, 24, 36),
        M3EBasketProgress(CurriculumBasket.UNIVERSITY_CORE, 36, 42),
        M3EBasketProgress(CurriculumBasket.UNIVERSITY_ELECTIVE, 10, 24)
    ),
    courses = listOf(
        M3ECurriculumCourse(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_CORE,
            courseType = "Embedded Theory & Lab",
            status = CourseCompletionStatus.COMPLETED,
            grade = "S"
        ),
        M3ECurriculumCourse(
            courseCode = "CSE4001",
            courseTitle = "Parallel and Distributed Computing",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_ELECTIVE,
            courseType = "Theory Only",
            status = CourseCompletionStatus.IN_PROGRESS
        )
    ),
    filteredCourses = listOf(
        M3ECurriculumCourse(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_CORE,
            courseType = "Embedded Theory & Lab",
            status = CourseCompletionStatus.COMPLETED,
            grade = "S"
        ),
        M3ECurriculumCourse(
            courseCode = "CSE4001",
            courseTitle = "Parallel and Distributed Computing",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_ELECTIVE,
            courseType = "Theory Only",
            status = CourseCompletionStatus.IN_PROGRESS
        )
    )
)

@Preview
@Composable
private fun M3ECurriculumScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECurriculumScreen(state = createMockCurriculumState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECurriculumScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECurriculumScreen(state = createMockCurriculumState(), onAction = {})
    }
}
