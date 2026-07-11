package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun EventHubScreen(initialTab: String = "Events") {
    val colors = AmazeTheme.colors
    var activeSubTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Events", "Clubs")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Social & Events",
            description = "Discover tech fests, clubs, and meetups",
            showBackButton = false,
            showSyncButton = true
        )

        androidx.compose.material3.TabRow(
            selectedTabIndex = tabs.indexOf(activeSubTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeSubTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                androidx.compose.material3.Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            when (activeSubTab) {
                "Events" -> EventHubSubScreen()
                "Clubs" -> ClubsSubScreen()
            }
        }
    }
}


@Composable
fun EventHubSubScreen() {
    val colors = AmazeTheme.colors
    val eventRes by AppState.events.collectAsState()
    val eventsList = eventRes?.events ?: emptyList()

    if (eventsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No registered events found.", color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(eventsList) { event ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.type ?: "Event", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent))
                            AmazeBadge(text = event.price ?: "Free", variant = BadgeVariant.SUCCESS)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(event.title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Date: ${event.date}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("Location: ${event.location}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }
}

@Composable
fun ClubsSubScreen() {
    val colors = AmazeTheme.colors
    val clubsRes by AppState.clubs.collectAsState()
    val clubsList = clubsRes?.clubs ?: emptyList()

    if (clubsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No clubs available.", color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(clubsList) { club ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(club.name, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        if (!club.description.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(club.description.toString(), style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
            }
        }
    }
}

