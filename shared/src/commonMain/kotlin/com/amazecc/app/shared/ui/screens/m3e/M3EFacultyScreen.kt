package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
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
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Faculty Directory & Cabin Locator.
 * 100% Stateless: accepts [M3EFacultyState] and emits [M3EFacultyAction].
 * Features school filter strip, search, cabin finder, and quick-copy email shortcuts.
 */
@Composable
fun M3EFacultyScreen(
    state: M3EFacultyState,
    onAction: (M3EFacultyAction) -> Unit,
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
            item(key = "faculty_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Faculty Directory",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Find professors, cabin locations, and timetable availability",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Search Bar
            item(key = "search_bar") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EFacultyAction.UpdateSearch(it)) },
                    placeholder = { Text("Search by name, cabin, or course...", fontSize = 13.sp) },
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
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLow
                    ),
                    singleLine = true
                )
            }

            // School Filter Chips
            item(key = "school_chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.availableSchools) { school ->
                        val isSelected = school == state.selectedSchool
                        val bg = if (isSelected) colors.primary else colors.surfaceContainerLow
                        val contentColor = if (isSelected) colors.onPrimary else colors.onSurface

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .clickable { onAction(M3EFacultyAction.SelectSchool(school)) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = school,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }

            // Faculty List
            if (state.filteredFaculty.isEmpty()) {
                item(key = "empty_faculty") {
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
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No faculty found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Try adjusting your search query or school filter",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.filteredFaculty,
                    key = { it.id }
                ) { faculty ->
                    val isExpanded = state.expandedFacultyId == faculty.id
                    M3EFacultyCard(
                        faculty = faculty,
                        isExpanded = isExpanded,
                        onToggleExpand = { onAction(M3EFacultyAction.ToggleExpand(faculty.id)) },
                        onCopyEmail = { onAction(M3EFacultyAction.CopyEmail(faculty.email)) },
                        onViewSchedule = { onAction(M3EFacultyAction.ViewSchedule(faculty.id)) }
                    )
                }
            }

            // Bottom Spacing
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun M3EFacultyCard(
    faculty: M3EFacultyMember,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyEmail: () -> Unit,
    onViewSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onToggleExpand,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Avatar Initials + Name + Star / Expand Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Initials Circle
                    val initials = faculty.name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("")

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials.ifBlank { "F" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = faculty.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            if (faculty.isMyFaculty) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.warning.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Enrolled",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.warning
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${faculty.designation} • ${faculty.school}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Cabin & Email Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MeetingRoom,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Cabin: ${faculty.cabin}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = faculty.email,
                        fontSize = 12.sp,
                        color = colors.primary
                    )
                    IconButton(
                        onClick = onCopyEmail,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Email",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Expandable Schedule & Courses Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))

                    faculty.intercom?.let { ext ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Intercom Extension: $ext",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    if (faculty.coursesTaught.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Courses Taught",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                faculty.coursesTaught.forEach { course ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surfaceContainerHighest)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = course,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ElevatedButton(
                        onClick = onViewSchedule,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("View Teaching Schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun createMockFacultyState() = M3EFacultyState(
    facultyList = listOf(
        M3EFacultyMember(
            id = "fac_1",
            name = "Prof. Sundar N",
            designation = "Associate Professor Grade 1",
            school = "SCOPE",
            cabin = "SJT 412-A",
            email = "sundar.n@vit.ac.in",
            intercom = "1412",
            coursesTaught = listOf("CSE3002", "CSE1007"),
            isMyFaculty = true
        ),
        M3EFacultyMember(
            id = "fac_2",
            name = "Dr. Anita R",
            designation = "Professor Higher Academic Grade",
            school = "SCOPE",
            cabin = "TT 314",
            email = "anita.r@vit.ac.in",
            intercom = "1314",
            coursesTaught = listOf("MAT2001", "MAT1011"),
            isMyFaculty = false
        )
    ),
    filteredFaculty = listOf(
        M3EFacultyMember(
            id = "fac_1",
            name = "Prof. Sundar N",
            designation = "Associate Professor Grade 1",
            school = "SCOPE",
            cabin = "SJT 412-A",
            email = "sundar.n@vit.ac.in",
            intercom = "1412",
            coursesTaught = listOf("CSE3002", "CSE1007"),
            isMyFaculty = true
        )
    )
)

@Preview
@Composable
private fun M3EFacultyScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFacultyScreen(state = createMockFacultyState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EFacultyScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EFacultyScreen(state = createMockFacultyState(), onAction = {})
    }
}
