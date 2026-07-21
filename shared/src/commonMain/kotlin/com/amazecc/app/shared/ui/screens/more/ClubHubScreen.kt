package com.amazecc.app.shared.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.ClubItem
import com.amazecc.app.shared.model.FeedPost
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.api.AmazeClient
import kotlinx.coroutines.launch

@Composable
fun ClubHubScreen() {
    val colors = AmazeTheme.colors
    val initialTab by AppState.clubHubInitialTab.collectAsState()
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Directory", "Feed")

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Club Hub",
            description = "Explore clubs and community feed",
            showBackButton = true,
            showSyncButton = false
        )

        TabRow(
            selectedTabIndex = tabs.indexOf(activeTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(tab, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            when (activeTab) {
                "Directory" -> DirectoryTab(colors)
                "Feed" -> FeedTab(colors)
            }
        }
    }
}

@Composable
private fun DirectoryTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val clubsRes by AppState.clubs.collectAsState()
    val clubs = clubsRes?.clubs ?: emptyList()
    var searchQuery by remember { mutableStateOf("") }

    val filteredClubs = remember(clubs, searchQuery) {
        if (searchQuery.isBlank()) clubs
        else clubs.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search clubs...", color = colors.textMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.accent
            )
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (clubs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Groups, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No clubs available", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                            Text("Sync from Events page to load data", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else if (filteredClubs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No clubs match your search", color = colors.textMuted)
                    }
                }
            } else {
                items(filteredClubs) { club ->
                    ClubCard(club, colors)
                }
            }
        }
    }
}

@Composable
private fun ClubCard(club: ClubItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable { AppState.openClubDetail(club.id ?: "") }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    club.name?.firstOrNull()?.uppercase() ?: "C",
                    style = AmazeTheme.typography.subheading.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(club.name ?: "Unnamed Club", fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                if (!club.description.isNullOrEmpty()) {
                    Text(club.description, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FeedTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val scope = rememberCoroutineScope()
    var feedPosts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val res = AmazeClient.getClubFeed()
        if (res.success) {
            feedPosts = res.feed
        } else {
            error = res.error
        }
        isLoading = false
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
        } else if (error != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CloudOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Could not load feed", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text(error ?: "", color = colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        AmazeButton("Retry", onClick = {
                            error = null; isLoading = true
                            scope.launch {
                                val res = AmazeClient.getClubFeed()
                                if (res.success) feedPosts = res.feed else error = res.error
                                isLoading = false
                            }
                        }, variant = ButtonVariant.SECONDARY)
                    }
                }
            }
        } else if (feedPosts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Feed, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No community posts yet", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Check back later for updates", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(feedPosts) { post ->
                FeedPostCard(post, colors)
            }
        }
    }
}

@Composable
private fun FeedPostCard(post: FeedPost, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Club ${post.clubId}", fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(post.createdAt, color = colors.textMuted, fontSize = 11.sp)
            }

            Text(post.content, color = colors.textPrimary, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)

            if (post.imageUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.imageUrls.take(3).forEach { _ ->
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Image, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            if (post.eventId != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.1f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Event: ${post.eventId}", color = Color(0xFF8B5CF6), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            var promoted by remember(post.hasPromoted) { mutableStateOf(post.hasPromoted) }
            var promoteCount by remember(post.promoteCount) { mutableStateOf(post.promoteCount) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        promoted = !promoted
                        promoteCount += if (promoted) 1 else -1
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.ThumbUp,
                        null,
                        tint = if (promoted) colors.accent else colors.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text("$promoteCount", color = if (promoted) colors.accent else colors.textMuted, fontSize = 12.sp)
            }
        }
    }
}
