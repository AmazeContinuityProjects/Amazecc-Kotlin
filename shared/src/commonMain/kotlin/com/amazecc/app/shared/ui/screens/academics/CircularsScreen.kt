package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun CircularsScreen() {
    val colors = AmazeTheme.colors
    val circularsRes by AppState.circulars.collectAsState()
    val isSyncing by AppState.isSyncing.collectAsState()
    val loading = isSyncing && circularsRes == null
    val circulars = circularsRes?.circulars ?: emptyList()
    val error = circularsRes?.let { if (!it.success) it.message ?: it.error else null }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        if (circularsRes == null) {
            AppState.refreshCirculars()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Circulars",
            description = "Academic notices and circulars",
            showBackButton = true,
            showSyncButton = false
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text("Loading circulars...", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text(error ?: "Unknown error", color = colors.danger, style = AmazeTheme.typography.body.copy(textAlign = TextAlign.Center))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    TextButton(onClick = { AppState.refreshCirculars() }) {

                        Text(Strings.retry, color = colors.accent)
                    }
                }
            }
        } else if (circulars.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FolderOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text("No circulars available", color = colors.textSecondary, style = AmazeTheme.typography.body)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
            ) {
                item { HeaderSpacer() }

                itemsIndexed(circulars, key = { idx, folder -> "${folder.title ?: "unknown"}-$idx" }) { idx, folder ->
                    val folderName = folder.title ?: "Untitled"
                    val isExpanded = folderName in expandedFolders
                    val items = folder.children ?: emptyList()

                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedFolders = if (isExpanded) {
                                            expandedFolders - folderName
                                        } else {
                                            expandedFolders + folderName
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                Text(
                                    text = folderName,
                                    style = AmazeTheme.typography.subheading.copy(
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AmazeTheme.fontSize.lg
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (items.isNotEmpty()) {
                                    Text(
                                        text = "${items.size}",
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.textSecondary,
                                            fontSize = AmazeTheme.fontSize.sm
                                        ),
                                        modifier = Modifier
                                            .background(colors.elevatedSurface, RoundedCornerShape(AmazeTheme.radius.xs))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (items.isEmpty()) {
                                        Text(
                                            text = "No items",
                                            style = AmazeTheme.typography.body.copy(
                                                color = colors.textMuted,
                                                fontSize = AmazeTheme.fontSize.base
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                        )
                                    } else {
                                        items.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                    .background(colors.elevatedSurface)
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Description,
                                                    contentDescription = null,
                                                    tint = colors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                                Text(
                                                    text = item.title ?: item.id ?: "Untitled",
                                                    style = AmazeTheme.typography.body.copy(
                                                        color = colors.textPrimary,
                                                        fontSize = AmazeTheme.fontSize.base
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (item.id != null) {
                                                    Text(
                                                        text = item.id,
                                                        style = AmazeTheme.typography.smallLabel.copy(
                                                            color = colors.textMuted,
                                                            fontSize = AmazeTheme.fontSize.micro
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(AmazeTheme.spacing.md)) }
            }
        }
    }
}
