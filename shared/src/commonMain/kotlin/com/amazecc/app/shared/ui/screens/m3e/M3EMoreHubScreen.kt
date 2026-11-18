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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive More & Features Navigator Hub.
 * 100% Stateless: accepts [M3EMoreHubState] and emits [M3EMoreHubAction].
 * Routes to all overhauled modules across Academics, Campus Life, Student Hub, and Settings.
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
            it.category.contains(state.searchQuery, ignoreCase = true)
        }
    }

    val groupedScreens = filteredScreens.groupBy { it.category }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Header Bar
        item {
            Column {
                Text(
                    text = "Campus Features",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = colors.onSurface
                )
                Text(
                    text = "Explore all services & campus utilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }

        // 2. Feature Search Bar
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onAction(M3EMoreHubAction.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search all tools, fests, or screens...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.primary
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    unfocusedPlaceholderColor = colors.onSurfaceVariant
                )
            )
        }

        // 3. Category Groups
        groupedScreens.forEach { (categoryName, screens) ->
            item {
                Text(
                    text = categoryName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    ),
                    color = colors.primary
                )
            }

            items(screens) { screen ->
                FeatureTileCard(
                    screen = screen,
                    onClick = { onAction(M3EMoreHubAction.OpenSubScreen(screen)) }
                )
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
        M3ESubScreen.EXAM_SCHEDULE -> Icons.Rounded.CalendarMonth
        M3ESubScreen.GRADES_HISTORY -> Icons.Rounded.TrendingUp
        M3ESubScreen.COURSE_DETAIL -> Icons.Rounded.School
        M3ESubScreen.FACULTY -> Icons.Rounded.AccountBox
        M3ESubScreen.CURRICULUM -> Icons.Rounded.Badge
        M3ESubScreen.MARKS_PREDICTOR -> Icons.Rounded.Calculate
        M3ESubScreen.HOSTEL -> Icons.Rounded.Hotel
        M3ESubScreen.TRANSPORT -> Icons.Rounded.DirectionsBus
        M3ESubScreen.CAB_SHARE -> Icons.Rounded.DirectionsCar
        M3ESubScreen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom
        M3ESubScreen.EVENT_HUB -> Icons.Rounded.Event
        M3ESubScreen.CLUB_HUB -> Icons.Rounded.Groups
        M3ESubScreen.TASKS -> Icons.Rounded.Assignment
        M3ESubScreen.PROFILE -> Icons.Rounded.Badge
        M3ESubScreen.SETTINGS -> Icons.Rounded.Settings
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 18.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = screen.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
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
