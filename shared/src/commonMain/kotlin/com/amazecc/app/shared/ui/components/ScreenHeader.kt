package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun ScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    syncModules: Set<SyncModule> = emptySet(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(title, description, showBackButton, showSyncButton, onRefresh, syncModules) {
        AppState.headerTitle.value = title
        AppState.headerDescription.value = description
        AppState.headerShowBack.value = showBackButton
        AppState.headerShowSync.value = showSyncButton
        AppState.headerOnRefresh.value = onRefresh
        AppState.headerSyncModules.value = syncModules
    }
}

@Composable
fun HeaderSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().statusBarsPadding().height(78.dp))
}

@Composable
fun FloatingScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    syncModules: Set<SyncModule> = emptySet(),
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val moduleStates by SyncEngine.moduleStates.collectAsState()
    val appHeaderRefresh by AppState.headerOnRefresh.collectAsState()

    val effectiveModules = remember(syncModules) {
        if (syncModules.isNotEmpty()) syncModules
        else AppState.headerSyncModules.value
    }

    val isModuleLoading = remember(effectiveModules, moduleStates, isLoading) {
        if (effectiveModules.isEmpty()) isLoading
        else isLoading || effectiveModules.any { moduleStates[it]?.status == SyncStatus.LOADING }
    }

    val moduleSyncText = remember(effectiveModules, moduleStates) {
        effectiveModules.firstOrNull { moduleStates[it]?.status == SyncStatus.LOADING }
            ?.let { moduleStates[it]?.let { "Syncing ${it.status.name}..." } }
    }

    val effectiveRefresh = onRefresh ?: appHeaderRefresh

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.navBackground.copy(alpha = 0.90f))
            .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showBackButton) {
                    IconButton(
                        onClick = { AppState.navigateBack() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accent.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            style = AmazeTheme.typography.caption.copy(
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isModuleLoading && (moduleSyncText ?: syncStatus) != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = moduleSyncText ?: syncStatus ?: "",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showSyncButton) {
                val syncAction: () -> Unit = {
                    if (effectiveRefresh != null) {
                        effectiveRefresh()
                    } else if (effectiveModules.isNotEmpty()) {
                        SyncEngine.setShowSyncDialog(true)
                        AppState.loadAllData()
                    } else {
                        AppState.loadAllData()
                    }
                }
                IconButton(
                    onClick = syncAction,
                    enabled = !isModuleLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.accent.copy(alpha = if (isModuleLoading) 0.20f else 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Sync Data",
                        tint = colors.accent,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                if (isModuleLoading) {
                                    rotationZ = rotationAngle
                                }
                            }
                    )
                }
            }
        }
    }
}

// ── 1. UNIFIED ACADEMICS SCREEN (TABS: ATTENDANCE, MARKS, SCHEDULE) ──

