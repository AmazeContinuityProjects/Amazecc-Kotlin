package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.LogLine
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Notice types reflecting non-routine sync states from ../AmazeCC SyncNotification.
 */
enum class M3ESyncNotice {
    OFFLINE,
    LOGIN,
    VTOP_DOWN
}

/**
 * Material 3 Expressive Sync Notification & Bottom Sheet Modal.
 * Replaces the legacy popup with 100% exact UX, behavior, and telemetry
 * copied directly from ../AmazeCC's SyncNotification component.
 *
 * States:
 * 1. Minimized floating pill (energy bar filling with progress, glyph tile,
 *    live server telemetry, milestone status, and expansion trigger).
 * 2. Expanded M3E bottom sheet modal (drag handle, milestones, live terminal log stream,
 *    mode cadence selector, offline retry, and inline auth login prompt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3ESyncNotification(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveOffline: () -> Unit = {},
    onSyncAll: () -> Unit = {}
) {
    val colors = M3ETheme.colors
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

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
    var isMinimized by remember { mutableStateOf(startMinimized) }
    var showConsole by remember { mutableStateOf(false) }
    var copiedLogsToast by remember { mutableStateOf(false) }

    // Inline login state
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // Classify notice state (Offline, Login, VTOP down)
    val notice: M3ESyncNotice? = remember(syncProgress, logLines, isSyncing) {
        val lastLog = logLines.lastOrNull()?.message.orEmpty().lowercase()
        when {
            lastLog.contains("vtop is down") || lastLog.contains("unreachable") -> M3ESyncNotice.VTOP_DOWN
            lastLog.contains("not authorized") || lastLog.contains("invalid credentials") || lastLog.contains("session expired") -> M3ESyncNotice.LOGIN
            lastLog.contains("offline") || lastLog.contains("no network") -> M3ESyncNotice.OFFLINE
            syncProgress.errorCount > 0 && !isSyncing -> null
            else -> null
        }
    }

    LaunchedEffect(showSyncDialog) {
        if (showSyncDialog) {
            userDismissed = false
            if (!startMinimized) isMinimized = false
        }
    }

    // Auto-expand on fatal auth failure or fatal error
    LaunchedEffect(notice) {
        if (notice == M3ESyncNotice.LOGIN || notice == M3ESyncNotice.VTOP_DOWN) {
            isMinimized = false
        }
    }

    // Auto-dismiss 2.5s after clean completion
    LaunchedEffect(isSyncing, syncProgress.completedModules, syncProgress.errorCount, isMinimized) {
        if (!isSyncing && showSyncDialog && !isMinimized && syncProgress.errorCount == 0 && syncProgress.completedModules > 0) {
            delay(2500L)
            SyncEngine.setShowSyncDialog(false)
        }
    }

    val shouldShow = (showSyncDialog && !userDismissed) || isSyncing
    if (!shouldShow) return

    val progressPercent = syncProgress.percentage.coerceIn(0f, 100f)

    val cleanHeadline = remember(logLines, syncMessage) {
        val raw = logLines.lastOrNull()?.message ?: syncMessage ?: "Syncing campus records…"
        raw.replace(Regex("[✅❌⏳📝•*-]"), "").trim()
    }

    val activeSourceLabel = when (AmazeClient.vtopSource) {
        VtopSource.REMOTE -> "Hono API"
        VtopSource.LOCAL -> "Tier 2: Scraper"
        VtopSource.OFFLINE_CACHE -> "Offline Cache"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ==========================================
        // 1. MINIMIZED FLOATING PILL (ENERGY BAR)
        // ==========================================
        AnimatedVisibility(
            visible = isMinimized,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isMinimized = false }
                    )
            ) {
                // Energy bar progress track
                val animatedWidthPercent by animateFloatAsState(
                    targetValue = progressPercent / 100f,
                    animationSpec = tween(500),
                    label = "EnergyBarWidth"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedWidthPercent)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.25f),
                                    colors.secondary.copy(alpha = 0.35f)
                                )
                            )
                        )
                )

                // Leading-edge glow indicator
                if (progressPercent in 1f..99f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedWidthPercent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .align(Alignment.CenterEnd)
                                .background(colors.primary)
                                .shadow(8.dp, CircleShape)
                        )
                    }
                }

                // Foreground Content Row
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mode Glyph Tile
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer.copy(alpha = 0.5f))
                            .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pinned Status Labels
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "VTOP Sync",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceContainerLow)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activeSourceLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.primary
                                )
                            }
                        }

                        Text(
                            text = cleanHeadline,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Percentage Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = progressPercent.toFixed(2, "%"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = colors.primary
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. EXPANDED M3 EXPRESSIVE BOTTOM SHEET
        // ==========================================
        AnimatedVisibility(
            visible = !isMinimized,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            // Modal Backdrop Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isMinimized = true }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Sheet Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // block dismissal when tapping inside
                        ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = colors.surfaceContainerHigh,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(colors.outlineVariant)
                        )

                        // Sheet Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = colors.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "VTOP Sync Engine",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.surfaceContainerHighest)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = activeSourceLabel.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = colors.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "AmazeCC • Cloud & Local Scraper Sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            // Header Actions (Console, Minimize, Close)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { showConsole = !showConsole }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = "Console",
                                        tint = if (showConsole) colors.primary else colors.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = { isMinimized = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Minimize",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    userDismissed = true
                                    SyncEngine.setShowSyncDialog(false)
                                    onDismiss()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // NOTICE VIEWS: Offline / Login / VTOP Down
                        when (notice) {
                            M3ESyncNotice.OFFLINE -> {
                                M3ESurfaceCard(
                                    containerLevel = SurfaceContainerLevel.LOW,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WifiOff,
                                            contentDescription = null,
                                            tint = colors.warning,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "Offline Mode Active",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = "VTOP synchronization requires active connectivity. Offline cached data is fully loaded and safe.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    isMinimized = true
                                                    onSaveOffline()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape
                                            ) {
                                                Text("Use Offline")
                                            }
                                            ElevatedButton(
                                                onClick = { onSyncAll() },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.elevatedButtonColors(
                                                    containerColor = colors.primary,
                                                    contentColor = colors.onPrimary
                                                )
                                            ) {
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                            }

                            M3ESyncNotice.LOGIN -> {
                                M3ESurfaceCard(
                                    containerLevel = SurfaceContainerLevel.LOW,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "VTOP Session Required",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = colors.onSurface
                                            )
                                        }

                                        Text(
                                            text = "Enter your credentials to negotiate a fresh session directly with VTOP.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )

                                        OutlinedTextField(
                                            value = loginUsername,
                                            onValueChange = { loginUsername = it },
                                            label = { Text("Registration Number") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = colors.onSurface,
                                                unfocusedTextColor = colors.onSurface,
                                                focusedBorderColor = colors.primary,
                                                unfocusedBorderColor = colors.outlineVariant
                                            )
                                        )

                                        OutlinedTextField(
                                            value = loginPassword,
                                            onValueChange = { loginPassword = it },
                                            label = { Text("VTOP Password") },
                                            singleLine = true,
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPassword = !showPassword }) {
                                                    Icon(
                                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = colors.onSurface,
                                                unfocusedTextColor = colors.onSurface,
                                                focusedBorderColor = colors.primary,
                                                unfocusedBorderColor = colors.outlineVariant
                                            )
                                        )

                                        if (!loginError.isNullOrBlank()) {
                                            Text(
                                                text = loginError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.error
                                            )
                                        }

                                        ElevatedButton(
                                            onClick = {
                                                if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                                    loginError = "Please enter both username and password."
                                                    return@ElevatedButton
                                                }
                                                loginError = null
                                                isLoggingIn = true
                                                coroutineScope.launch {
                                                    val res = AmazeClient.login(loginUsername, loginPassword)
                                                    if (res.success) {
                                                        AppState.loadAllData()
                                                    } else {
                                                        loginError = res.message ?: "Authentication failed"
                                                    }
                                                    isLoggingIn = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = colors.primary,
                                                contentColor = colors.onPrimary
                                            )
                                        ) {
                                            if (isLoggingIn) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = colors.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Authenticating…")
                                            } else {
                                                Text("Sign In & Sync")
                                            }
                                        }
                                    }
                                }
                            }

                            M3ESyncNotice.VTOP_DOWN -> {
                                M3ESurfaceCard(
                                    containerLevel = SurfaceContainerLevel.LOW,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            tint = colors.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "VTOP Gateway Down",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = "vtopcc.vit.ac.in is unreachable. Requests were safely halted to protect cached data.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                        ElevatedButton(
                                            onClick = { isMinimized = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = colors.surfaceContainerHighest,
                                                contentColor = colors.onSurface
                                            )
                                        ) {
                                            Text("Dismiss to Background")
                                        }
                                    }
                                }
                            }

                            null -> {
                                // ACTIVE SYNC VIEW: Progress Bar + Milestones
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Headline text
                                    Text(
                                        text = cleanHeadline,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Wavy Progress Bar
                                    M3EWavyLinearProgress(
                                        progress = progressPercent / 100f,
                                        modifier = Modifier.fillMaxWidth().height(16.dp)
                                    )

                                    // 4 Milestones List
                                    val milestones = remember(progressPercent, logLines) {
                                        listOf(
                                            MilestoneItem(
                                                id = "auth",
                                                label = "Authentication & Session",
                                                detail = if (progressPercent >= 20f) "Session verified" else "Negotiating VTOP session…",
                                                status = if (progressPercent >= 20f) MilestoneStatus.COMPLETED else MilestoneStatus.ACTIVE
                                            ),
                                            MilestoneItem(
                                                id = "academics",
                                                label = "Attendance & Timetable",
                                                detail = if (progressPercent >= 60f) "Classes & slots updated" else if (progressPercent >= 20f) "Fetching timetable…" else "Waiting",
                                                status = if (progressPercent >= 60f) MilestoneStatus.COMPLETED else if (progressPercent >= 20f) MilestoneStatus.ACTIVE else MilestoneStatus.PENDING
                                            ),
                                            MilestoneItem(
                                                id = "evaluations",
                                                label = "Marks & Grades",
                                                detail = if (progressPercent >= 85f) "Assessments verified" else if (progressPercent >= 60f) "Pulling assessment marks…" else "Waiting",
                                                status = if (progressPercent >= 85f) MilestoneStatus.COMPLETED else if (progressPercent >= 60f) MilestoneStatus.ACTIVE else MilestoneStatus.PENDING
                                            ),
                                            MilestoneItem(
                                                id = "cache",
                                                label = "Local Storage & Finalize",
                                                detail = if (progressPercent >= 100f) "Synchronized" else if (progressPercent >= 85f) "Saving offline database…" else "Waiting",
                                                status = if (progressPercent >= 100f) MilestoneStatus.COMPLETED else if (progressPercent >= 85f) MilestoneStatus.ACTIVE else MilestoneStatus.PENDING
                                            )
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        milestones.forEach { m ->
                                            MilestoneRow(item = m)
                                        }
                                    }
                                }
                            }
                        }

                        // Collapsible Console / Activity Logs
                        AnimatedVisibility(
                            visible = showConsole,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOWEST,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            ) {
                                val logState = rememberLazyListState()
                                LaunchedEffect(logLines.size) {
                                    if (logLines.isNotEmpty()) {
                                        logState.animateScrollToItem(logLines.size - 1)
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ACTIVITY CONSOLE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = colors.primary
                                        )
                                        Text(
                                            text = if (copiedLogsToast) "Copied!" else "Copy",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.onSurfaceVariant,
                                            modifier = Modifier.clickable {
                                                val all = logLines.joinToString("\n") { "[${it.status}] ${it.message}" }
                                                clipboard.setText(AnnotatedString(all))
                                                copiedLogsToast = true
                                                coroutineScope.launch {
                                                    delay(1800)
                                                    copiedLogsToast = false
                                                }
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LazyColumn(
                                        state = logState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logLines) { line ->
                                            Text(
                                                text = line.message,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = when (line.status) {
                                                    SyncStatus.SUCCESS -> colors.success
                                                    SyncStatus.ERROR -> colors.error
                                                    SyncStatus.LOADING -> colors.primary
                                                    SyncStatus.IDLE -> colors.onSurfaceVariant
                                                },
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions: Cancel & Minimize
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    userDismissed = true
                                    SyncEngine.setShowSyncDialog(false)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape
                            ) {
                                Text("Cancel Sync")
                            }

                            ElevatedButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Text("Minimize")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MilestoneStatus { PENDING, ACTIVE, COMPLETED, ERROR }

private data class MilestoneItem(
    val id: String,
    val label: String,
    val detail: String,
    val status: MilestoneStatus
)

@Composable
private fun MilestoneRow(item: MilestoneItem) {
    val colors = M3ETheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (item.status) {
            MilestoneStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(18.dp)
                )
            }
            MilestoneStatus.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp
                )
            }
            MilestoneStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(18.dp)
                )
            }
            MilestoneStatus.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, colors.outlineVariant, CircleShape)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
