package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.utils.ChangelogEntry
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.UpdateConfig

@Composable
fun ChangelogScreen() {
    val colors = AmazeTheme.colors
    var currentVersion by remember { mutableStateOf("...") }
    LaunchedEffect(Unit) {
        currentVersion = UpdateConfig.getCurrentVersion()
    }
    val latestReleaseNotes by AppState.latestReleaseNotes.collectAsState()
    var bundledChanges by remember { mutableStateOf(emptyList<ChangelogEntry>()) }
    LaunchedEffect(Unit) {
        bundledChanges = ContentData.changelog()
    }

    val changes = remember(latestReleaseNotes, bundledChanges) {
        if (latestReleaseNotes.isNotBlank()) {
            latestReleaseNotes.lines().filter { it.isNotBlank() }.map { "Release Notes" to it }
        } else {
            bundledChanges.mapIndexed { index, entry ->
                (entry.title ?: "Update ${bundledChanges.size - index}") to entry.description
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSpacer() }
            items(changes.size, key = { it }) { index ->
                val change = changes[index]
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
                        Column {
                            Text(
                                text = change.first,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                text = change.second,
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, lineHeight = 18.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}