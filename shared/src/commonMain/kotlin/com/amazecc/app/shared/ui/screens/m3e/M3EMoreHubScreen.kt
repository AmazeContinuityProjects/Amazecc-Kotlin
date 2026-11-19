package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive More & Features Navigator Hub.
 * 100% Stateless: accepts [M3EMoreHubState] and emits [M3EMoreHubAction].
 *
 * Sized and styled with:
 * - Prominent quick access cards for newly relocated navigation items (Attendance & Library)
 * - Expressive search pill with clear action
 * - Categorized modular surface cards with rich icons & descriptions
 * - Guaranteed 140dp bottom spacing to clear the floating M3E navigation bar
 * - 100% theme adaptive tokens for vibrant light and dark modes
 */
@Composable
fun M3EMoreHubScreen(
    state: M3EMoreHubState,
    onAction: (M3EMoreHubAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    val filteredScreens = if (state.searchQuery.isBlank()) {
        M3ESubScreen.entries
    } else {
        M3ESubScreen.entries.filter {
            it.label.contains(state.searchQuery, ignoreCase = true) ||
            it.category.contains(state.searchQuery, ignoreCase = true) ||
            it.description.contains(state.searchQuery, ignoreCase = true)
        }
    }

    val groupedScreens = filteredScreens.groupBy { it.category }

    M3EPageShell(
        eyebrow = "VIT CHENNAI",
        title = "Campus Features",
        subtitle = "Explore all services & campus utilities",
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // 1. Search Bar Pill
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EMoreHubAction.SearchQueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search attendance, exams, mess, transit...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = state.searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(onClick = { onAction(M3EMoreHubAction.SearchQueryChanged("")) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear search",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    )
                )
            }

            // 2. Quick Navigation Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    val filterChips = listOf(
                        "All" to "",
                        "Attendance" to "Attendance",
                        "Library" to "Library",
                        "Exams" to "Exam",
                        "Hostel" to "Hostel",
                        "Transport" to "Transport"
                    )
                    items(filterChips) { (chipLabel, query) ->
                        val isSelected = (query.isEmpty() && state.searchQuery.isEmpty()) || 
                                         (query.isNotEmpty() && state.searchQuery.equals(query, ignoreCase = true))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { onAction(M3EMoreHubAction.SearchQueryChanged(query)) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Category Groups & Feature Tiles
            if (groupedScreens.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No matching modules found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "Try searching for courses, hostel, buses, or marks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                groupedScreens.forEach { (categoryName, screens) ->
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = categoryName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                                fontSize = 11.5.sp
                            ),
                            color = colors.primary
                        )
                    }

                    items(screens, key = { it.name }) { screen ->
                        FeatureTileCard(
                            screen = screen,
                            onClick = { onAction(M3EMoreHubAction.OpenSubScreen(screen)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTileCard(
    screen: M3ESubScreen,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    val icon: ImageVector = when (screen) {
        M3ESubScreen.ACADEMICS_HUB -> Icons.Rounded.Dashboard
        M3ESubScreen.ATTENDANCE -> Icons.Rounded.EventAvailable
        M3ESubScreen.LIBRARIES -> Icons.AutoMirrored.Rounded.LibraryBooks
        M3ESubScreen.COURSE_DETAIL -> Icons.Rounded.School
        M3ESubScreen.COURSE_ATTENDANCE -> Icons.Rounded.FactCheck
        M3ESubScreen.COURSE_DASHBOARD -> Icons.Rounded.Insights
        M3ESubScreen.FACULTY -> Icons.Rounded.AccountBox
        M3ESubScreen.CURRICULUM -> Icons.Rounded.Badge
        M3ESubScreen.MARKS_PREDICTOR -> Icons.Rounded.Calculate
        M3ESubScreen.EXAM_SCHEDULE -> Icons.Rounded.CalendarMonth
        M3ESubScreen.GRADES_HISTORY -> Icons.Rounded.TrendingUp
        M3ESubScreen.CALENDAR -> Icons.Rounded.CalendarToday
        M3ESubScreen.FFCS_PLANNER -> Icons.Rounded.ViewTimeline
        M3ESubScreen.MOODLE -> Icons.Rounded.MenuBook
        M3ESubScreen.OD_TRACKER -> Icons.Rounded.TaskAlt
        M3ESubScreen.FEEDBACK_STATUS -> Icons.Rounded.RateReview
        M3ESubScreen.QBANK -> Icons.Rounded.Topic
        M3ESubScreen.HOSTEL -> Icons.Rounded.Hotel
        M3ESubScreen.TRANSPORT -> Icons.Rounded.DirectionsBus
        M3ESubScreen.CAB_SHARE -> Icons.Rounded.DirectionsCar
        M3ESubScreen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom
        M3ESubScreen.PAYMENTS -> Icons.Rounded.CreditCard
        M3ESubScreen.CIRCULARS -> Icons.Rounded.Campaign
        M3ESubScreen.DOCUMENTS -> Icons.Rounded.Description
        M3ESubScreen.EVENT_HUB -> Icons.Rounded.Event
        M3ESubScreen.CLUB_HUB -> Icons.Rounded.Groups
        M3ESubScreen.TASKS -> Icons.Rounded.Assignment
        M3ESubScreen.SOCIAL -> Icons.Rounded.People
        M3ESubScreen.FRESHER_WELCOME -> Icons.Rounded.Explore
        M3ESubScreen.HALL_OF_FAME -> Icons.Rounded.EmojiEvents
        M3ESubScreen.PROFILE -> Icons.Rounded.Badge
        M3ESubScreen.SETTINGS -> Icons.Rounded.Settings
        M3ESubScreen.ABOUT -> Icons.Rounded.Info
        M3ESubScreen.CHANGELOG -> Icons.Rounded.History
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = screen.label,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = colors.onSurface
                    )
                    if (screen.description.isNotBlank()) {
                        Text(
                            text = screen.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Navigate to ${screen.label}",
                tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EMoreHubScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EMoreHubScreen(
            state = M3EMoreHubState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3EMoreHubScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EMoreHubScreen(
            state = M3EMoreHubState(),
            onAction = {}
        )
    }
}
