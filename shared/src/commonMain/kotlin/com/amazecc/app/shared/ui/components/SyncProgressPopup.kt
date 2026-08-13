package com.amazecc.app.shared.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.ModuleState
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncCategory
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay

private enum class SyncDialogTab(val title: String) {
    OVERVIEW("Overview"),
    MODULES("Modules"),
    AUTOSYNC("Auto Sync"),
    LOGS("Activity Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncProgressPopup(
    onDismiss: () -> Unit,
    onSaveOffline: () -> Unit = {},
    onSyncAll: () -> Unit = {},
) {
    val colors = AmazeTheme.colors
    val moduleStates by SyncEngine.moduleStates.collectAsState()
    val syncProgress by SyncEngine.syncProgress.collectAsState()
    val logLines by SyncEngine.logLines.collectAsState()
    val showSyncDialog by SyncEngine.showSyncDialog.collectAsState()
    val startMinimized by SyncEngine.startMinimized.collectAsState()
    val isAppStateSyncing by AppState.isSyncing.collectAsState()
    val isAppStateLoading by AppState.isLoading.collectAsState()
    val syncMessage by AppState.syncMessage.collectAsState()

    val isEngineSyncing = syncProgress.activeModules.isNotEmpty() || SyncEngine.isAnyModuleLoading()
    val isSyncing = isAppStateSyncing || isAppStateLoading || isEngineSyncing

    var userDismissed by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(if (startMinimized) true else false) }
    var selectedTab by remember { mutableStateOf(SyncDialogTab.OVERVIEW) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var copiedLogsToast by remember { mutableStateOf(false) }

    LaunchedEffect(showSyncDialog) {
        if (showSyncDialog) {
            userDismissed = false
            if (!startMinimized) isMinimized = false
        }
    }

    val shouldShow = if (showSyncDialog) !userDismissed else isSyncing
    if (!shouldShow) return

    if (showSettingsDialog) {
        SyncSettingsDialog(onDismiss = { showSettingsDialog = false })
    }

    val isFullOverlay = showSyncDialog && !userDismissed && !isMinimized

    val isFinished = !isSyncing && (syncProgress.completedModules > 0 || !isAppStateSyncing)

    // Auto-dismiss 2.5s after clean completion (only when dialog was explicitly opened and not minimized)
    LaunchedEffect(isSyncing, isFinished, syncProgress.errorCount, isMinimized) {
        if (!isSyncing && showSyncDialog && !isMinimized && syncProgress.errorCount == 0) {
            delay(2500L)
            SyncEngine.setShowSyncDialog(false)
        }
    }

    val displayText = when {
        isSyncing && syncProgress.activeModules.isNotEmpty() -> syncProgress.displayText
        isSyncing && !syncMessage.isNullOrBlank() -> syncMessage!!
        isSyncing -> "Syncing data..."
        syncProgress.errorCount > 0 -> "Completed with ${syncProgress.errorCount} errors"
        else -> if (syncProgress.displayText.isNotBlank()) syncProgress.displayText else "Sync complete"
    }

    // Floating Minimized Pill (also shown whenever a sync runs in the background)
    if (!isFullOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(120f),
            contentAlignment = Alignment.BottomEnd
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(AmazeTheme.radius.large),
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 96.dp)
                    .width(270.dp)
                    .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.large))
                    .clickable {
                        userDismissed = false
                        isMinimized = false
                        SyncEngine.setShowSyncDialog(true, minimized = false)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSyncing) colors.accent.copy(alpha = 0.15f) else colors.success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                progress = { syncProgress.percentage / 100f },
                                modifier = Modifier.size(24.dp),
                                color = colors.accent,
                                strokeWidth = 2.5.dp,
                                trackColor = colors.accent.copy(alpha = 0.15f),
                            )
                        } else {
                            Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync Complete",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base, color = colors.textPrimary)
                        )
                        Text(
                            text = displayText,
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Settings, "Sync Settings", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        return
    }

    // Full Sheet (pulls up from the bottom like the rest of the app's sheets)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSyncing) Icons.Rounded.Sync else Icons.Rounded.CloudDone,
                            null,
                            tint = colors.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isSyncing) "Syncing Data" else "Sync Overview",
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = syncProgress.displayText,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Settings, "Sync Settings", tint = colors.textMuted)
                    }
                    IconButton(onClick = { isMinimized = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = colors.textMuted)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textMuted)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Segmented Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = colors.background.copy(alpha = 0.5f),
                contentColor = colors.accent,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = colors.accent
                        )
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.medium))
            ) {
                SyncDialogTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                tab.title,
                                style = AmazeTheme.typography.caption.copy(
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == tab) colors.accent else colors.textMuted
                                )
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tab Content
            when (selectedTab) {
                SyncDialogTab.OVERVIEW -> {
                    OverviewTabContent(
                        isSyncing = isSyncing,
                        isFinished = isFinished,
                        syncProgress = syncProgress,
                        colors = colors,
                        onSyncAll = onSyncAll,
                        onCancel = { AppState.cancelSync() },
                        onDismiss = onDismiss
                    )
                }
                SyncDialogTab.MODULES -> {
                    ModulesTabContent(
                        moduleStates = moduleStates,
                        colors = colors
                    )
                }
                SyncDialogTab.AUTOSYNC -> {
                    AutoSyncTabContent(colors = colors)
                }
                SyncDialogTab.LOGS -> {
                    LogsTabContent(
                        logLines = logLines,
                        colors = colors,
                        onCopyLogs = {
                            val text = logLines.joinToString("\n") { "[${it.status.name}] ${it.module.displayName}: ${it.message}" }
                            clipboard.setText(AnnotatedString(text))
                            copiedLogsToast = true
                        },
                        copiedLogsToast = copiedLogsToast
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTabContent(
    isSyncing: Boolean,
    isFinished: Boolean,
    syncProgress: com.amazecc.app.shared.state.SyncProgress,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onSyncAll: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Progress Indicator
        Box(
            modifier = Modifier.size(110.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val animatedProgress by animateFloatAsState(
                targetValue = syncProgress.percentage / 100f,
                animationSpec = tween(600)
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = if (syncProgress.errorCount > 0) colors.warning else if (isFinished) colors.success else colors.accent,
                strokeWidth = 6.dp,
                trackColor = colors.accent.copy(alpha = 0.12f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${syncProgress.percentage.toInt()}%",
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.xl,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = if (isSyncing) "Syncing" else if (syncProgress.errorCount > 0) "Errors" else "Ready",
                    style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Status Card Banner
        val bannerBg = when {
            syncProgress.errorCount > 0 -> colors.danger.copy(alpha = 0.12f)
            isFinished -> colors.success.copy(alpha = 0.12f)
            else -> colors.accentSurface.copy(alpha = 0.2f)
        }
        val bannerColor = when {
            syncProgress.errorCount > 0 -> colors.danger
            isFinished -> colors.success
            else -> colors.accent
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = bannerBg),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        syncProgress.errorCount > 0 -> Icons.Rounded.Warning
                        isFinished -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.HourglassTop
                    },
                    null,
                    tint = bannerColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = syncProgress.displayText,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = bannerColor, fontSize = AmazeTheme.fontSize.sm)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSyncing) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AmazeTheme.radius.medium),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger)
                ) {
                    Icon(Icons.Rounded.Cancel, "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel Sync", fontSize = AmazeTheme.fontSize.sm)
                }
            } else {
                AmazeButton(
                    text = "Sync All Modules",
                    onClick = onSyncAll,
                    modifier = Modifier.weight(1f).height(38.dp)
                )
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(AmazeTheme.radius.medium)
                ) {
                    Text("Close", fontSize = AmazeTheme.fontSize.sm)
                }
            }
        }
    }
}

@Composable
private fun ModulesTabContent(
    moduleStates: Map<SyncModule, ModuleState>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val groupedModules = remember(moduleStates) {
        SyncCategory.entries.associateWith { cat ->
            SyncModule.entries.filter { it.category == cat }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        groupedModules.forEach { (category, modules) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = category.displayName,
                    style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = AmazeTheme.fontSize.xs),
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
                modules.forEach { module ->
                    val state = moduleStates[module] ?: ModuleState()
                    ModuleSyncRow(module, state, colors)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ModuleSyncRow(
    module: SyncModule,
    state: ModuleState,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val icon = when (state.status) {
        SyncStatus.LOADING -> Icons.Rounded.Sync
        SyncStatus.SUCCESS -> Icons.Rounded.CheckCircle
        SyncStatus.ERROR -> Icons.Rounded.Error
        SyncStatus.IDLE -> Icons.Rounded.RadioButtonUnchecked
    }
    val iconColor = when (state.status) {
        SyncStatus.LOADING -> colors.accent
        SyncStatus.SUCCESS -> colors.success
        SyncStatus.ERROR -> colors.danger
        SyncStatus.IDLE -> colors.textMuted.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.background.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.status == SyncStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.displayName,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary)
            )
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.danger),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LogsTabContent(
    logLines: List<com.amazecc.app.shared.state.LogLine>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onCopyLogs: () -> Unit,
    copiedLogsToast: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Execution Trace",
                style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, fontSize = AmazeTheme.fontSize.xs)
            )
            TextButton(onClick = onCopyLogs, modifier = Modifier.height(28.dp)) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp), tint = colors.accent)
                Spacer(Modifier.width(4.dp))
                Text(if (copiedLogsToast) "Copied!" else "Copy Log", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                .background(colors.background)
                .padding(10.dp)
        ) {
            if (logLines.isEmpty()) {
                Text("No log events recorded yet.", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logLines.takeLast(40), key = { "${it.timestamp}-${it.module.name}-${it.message}" }) { log ->
                        val color = when (log.status) {
                            SyncStatus.SUCCESS -> colors.success
                            SyncStatus.ERROR -> colors.danger
                            SyncStatus.LOADING -> colors.accent
                            SyncStatus.IDLE -> colors.textMuted
                        }
                        Text(
                            text = "[${log.module.displayName}] ${log.message}",
                            style = AmazeTheme.typography.caption.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = AmazeTheme.fontSize.micro,
                                color = color
                            )
                        )
                    }
                }
            }
        }
    }
}
