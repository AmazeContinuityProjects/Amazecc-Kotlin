@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    val apiEvents = eventRes?.events ?: emptyList()

    val mockEvents = remember {
        listOf(
            EventHubEvent("1", "Hackathon 2026", "All students", "Hackathon", "Aug 15, 2026", "Main Auditorium", "Free"),
            EventHubEvent("2", "Tech Talk: AI/ML", "CS/IT students", "Workshop", "Aug 20, 2026", "Seminar Hall B", "₹50"),
            EventHubEvent("3", "Cultural Night", "All students", "Cultural", "Aug 25, 2026", "Open Air Theatre", "₹100"),
            EventHubEvent("4", "Coding Contest", "All students", "Technical", "Sep 1, 2026", "Lab Block 3", "Free"),
            EventHubEvent("5", "Robotics Workshop", "All students", "Workshop", "Sep 10, 2026", "Innovation Lab", "₹200"),
            EventHubEvent("6", "Startup Pitch Fest", "All students", "Technical", "Sep 15, 2026", "Conference Hall", "Free"),
        )
    }

    val eventsList = if (apiEvents.isNotEmpty()) apiEvents else mockEvents
    val categories = listOf("All", "Technical", "Cultural", "Workshop", "Hackathon")
    var selectedCategory by remember { mutableStateOf("All") }
    val filteredEvents = if (selectedCategory == "All") eventsList
    else eventsList.filter { it.type.equals(selectedCategory, ignoreCase = true) }
    val registeredEvents = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            val featured = eventsList.firstOrNull()
            if (featured != null) {
                FeaturedEventCard(
                    event = featured,
                    isRegistered = registeredEvents[featured.eid] == true,
                    onRegister = { registeredEvents[featured.eid] = true }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                            containerColor = colors.surface, labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.border, selectedBorderColor = Color.Transparent,
                            enabled = true, selected = selectedCategory == cat
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(filteredEvents) { event ->
            EventCard(
                event = event,
                isRegistered = registeredEvents[event.eid] == true,
                onRegister = { registeredEvents[event.eid] = true }
            )
        }
    }
}

@Composable
private fun FeaturedEventCard(event: EventHubEvent, isRegistered: Boolean, onRegister: () -> Unit) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(radius.medium))
            .background(colors.accent.copy(alpha = 0.15f))
            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(radius.medium))
            .padding(20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Featured Event", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                AmazeBadge(text = event.type, variant = BadgeVariant.INFO)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(event.title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 18.sp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.date, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Spacer(modifier = Modifier.height(8.dp))
            Text(event.eligibility, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 13.sp), maxLines = 2)
            Spacer(modifier = Modifier.height(12.dp))
            if (isRegistered) {
                AmazeButton(text = "Registered ✓", onClick = {}, enabled = false, variant = ButtonVariant.SECONDARY, icon = Icons.Rounded.CheckCircle)
            } else {
                AmazeButton(text = "Register", onClick = onRegister)
            }
        }
    }
}

@Composable
private fun EventCard(event: EventHubEvent, isRegistered: Boolean, onRegister: () -> Unit) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Event, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(event.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.SemiBold))
                    AmazeBadge(
                        text = if (isRegistered) "Registered" else if (event.isPastEvent == true) "Closed" else if (event.price == "Free") "Open" else "Full",
                        variant = if (isRegistered) BadgeVariant.SUCCESS else if (event.isPastEvent == true) BadgeVariant.DANGER else if (event.price == "Free") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(event.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.textMuted)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(event.date, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.textMuted)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(event.location, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isRegistered) {
                    AmazeButton(text = "Registered ✓", onClick = {}, enabled = false, variant = ButtonVariant.SECONDARY, icon = Icons.Rounded.CheckCircle)
                } else {
                    AmazeButton(text = if (event.isPastEvent == true) "View Details" else "Register", onClick = onRegister)
                }
            }
        }
    }
}

@Composable
fun ClubsSubScreen() {
    val colors = AmazeTheme.colors
    val clubsRes by AppState.clubs.collectAsState()
    val apiClubs = clubsRes?.clubs ?: emptyList()

    val mockClubs = remember {
        listOf(
            ClubItem("1", "CodeChef Chapter", "Competitive programming club for coding contests and hackathons."),
            ClubItem("2", "Drama Society", "Theatre and performing arts club for cultural events."),
            ClubItem("3", "Robotics Club", "Build and compete with robots in national-level events."),
            ClubItem("4", "Music Club", "Singing, instruments, and band performances for all genres."),
            ClubItem("5", "Sports Council", "Organizes inter-college sports tournaments and fitness events."),
        )
    }

    val clubsList = if (apiClubs.isNotEmpty()) apiClubs else mockClubs
    val categories = listOf("All", "Technical", "Cultural", "Sports")
    var selectedCategory by remember { mutableStateOf("All") }

    val clubCategoryMap = remember {
        mapOf(
            "CodeChef Chapter" to "Technical", "Robotics Club" to "Technical",
            "Drama Society" to "Cultural", "Music Club" to "Cultural",
            "Sports Council" to "Sports"
        )
    }
    fun categoryOf(c: ClubItem) = clubCategoryMap[c.name] ?: "Technical"

    val filteredClubs = if (selectedCategory == "All") clubsList
    else clubsList.filter { categoryOf(it) == selectedCategory }

    val enrolledClubs = remember { mutableStateMapOf<String, Boolean>() }
    var expandedClubId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            val featured = clubsList.firstOrNull()
            if (featured != null) {
                FeaturedClubCard(
                    club = featured,
                    isEnrolled = enrolledClubs[featured.id] == true,
                    onEnroll = { if (featured.id != null) enrolledClubs[featured.id] = true }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                            containerColor = colors.surface, labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.border, selectedBorderColor = Color.Transparent,
                            enabled = true, selected = selectedCategory == cat
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        itemsIndexed(filteredClubs) { _, club ->
            val isEnrolled = enrolledClubs[club.id] == true
            val isExpanded = expandedClubId == club.id
            ClubCard(
                club = club,
                isEnrolled = isEnrolled,
                isExpanded = isExpanded,
                onEnroll = {
                    if (club.id != null) {
                        if (isEnrolled) enrolledClubs.remove(club.id) else enrolledClubs[club.id] = true
                    }
                },
                onToggleExpand = { expandedClubId = if (isExpanded) null else club.id }
            )
        }
    }
}

@Composable
private fun FeaturedClubCard(club: ClubItem, isEnrolled: Boolean, onEnroll: () -> Unit) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(radius.medium))
            .background(colors.accent.copy(alpha = 0.15f))
            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(radius.medium))
            .padding(20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Featured Club", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Text(club.name?.firstOrNull()?.uppercase() ?: "C", style = AmazeTheme.typography.body.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(club.name ?: "Unnamed Club", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 18.sp))
            if (!club.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(club.description, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 13.sp), maxLines = 2)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isEnrolled) {
                AmazeButton(text = "Enrolled ✓", onClick = {}, enabled = false, variant = ButtonVariant.SECONDARY, icon = Icons.Rounded.CheckCircle)
            } else {
                AmazeButton(text = "Enroll", onClick = onEnroll)
            }
        }
    }
}

@Composable
private fun ClubCard(club: ClubItem, isEnrolled: Boolean, isExpanded: Boolean, onEnroll: () -> Unit, onToggleExpand: () -> Unit) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = onToggleExpand) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(club.name?.firstOrNull()?.uppercase() ?: "C", style = AmazeTheme.typography.subheading.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(club.name ?: "Unnamed Club", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    if (!club.description.isNullOrEmpty()) {
                        Text(club.description, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = if (isExpanded) Int.MAX_VALUE else 1)
                    }
                }
                if (isEnrolled) Icon(Icons.Rounded.CheckCircle, contentDescription = "Enrolled", tint = colors.successText, modifier = Modifier.size(20.dp))
                Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeButton(
                        text = if (isEnrolled) "Enrolled ✓" else "Enroll",
                        onClick = onEnroll,
                        modifier = Modifier.weight(1f),
                        variant = if (isEnrolled) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
                        icon = if (isEnrolled) Icons.Rounded.CheckCircle else null
                    )
                }
            }
        }
    }
}

