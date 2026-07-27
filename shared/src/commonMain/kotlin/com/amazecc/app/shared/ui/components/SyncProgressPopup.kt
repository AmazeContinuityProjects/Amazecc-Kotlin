package com.amazecc.app.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.ModuleState
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    var isMinimized by remember { mutableStateOf(false) }

    if (!showSyncDialog) return

    val activeModules = syncProgress.activeModules
    val isSyncing = activeModules.isNotEmpty()

    // ── Minimized pill (bottom-right) ──
    if (isMinimized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
                .clickable { isMinimized = false },
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 96.dp)
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { isMinimized = false }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                        CircularProgressIndicator(
                            progress = { syncProgress.percentage / 100f },
                            modifier = Modifier.size(24.dp),
                            color = colors.accent,
                            strokeWidth = 2.5.dp,
                            trackColor = colors.accent.copy(alpha = 0.15f),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync Complete",
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = syncProgress.displayText,
                            style = AmazeTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        return
    }

    // ── Full overlay ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = false) { /* consume clicks */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSyncing) "Syncing Data" else "Sync Summary",
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = syncProgress.displayText,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
                IconButton(onClick = { isMinimized = true }) {
                    Icon(Icons.Rounded.RemoveRedEye, "Minimize", tint = colors.textSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textSecondary)
                }
            }

            // ── Overall Progress Bar ──
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overall Progress",
                        style = AmazeTheme.typography.caption.copy(
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    )
                    Text(
                        text = "${syncProgress.percentage.toInt()}%",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontSize = 14.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent.copy(alpha = 0.15f))
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = syncProgress.percentage / 100f,
                        animationSpec = tween(600)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Module List ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(SyncModule.entries.filter { it.cacheKey != null || it == SyncModule.ALL_SEMESTER_ATTENDANCE || it == SyncModule.CAB_TRIPS }) { module ->
                    val state = moduleStates[module] ?: ModuleState()
                    ModuleSyncRow(module, state, colors)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Log Lines ──
            if (logLines.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 100.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Activity Log",
                        style = AmazeTheme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    logLines.takeLast(5).forEach { log ->
                        val icon = when (log.status) {
                            SyncStatus.SUCCESS -> "✓"
                            SyncStatus.ERROR -> "✗"
                            SyncStatus.LOADING -> "⟳"
                            SyncStatus.IDLE -> "·"
                        }
                        val iconColor = when (log.status) {
                            SyncStatus.SUCCESS -> Color(0xFF4CAF50)
                            SyncStatus.ERROR -> Color(0xFFEF5350)
                            SyncStatus.LOADING -> colors.accent
                            SyncStatus.IDLE -> colors.textSecondary
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = icon,
                                color = iconColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${moduleShortName(log.module)}: ${log.message}",
                                style = AmazeTheme.typography.caption.copy(
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Action Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isSyncing) {
                    OutlinedButton(
                        onClick = onSaveOffline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                    ) {
                        Icon(Icons.Rounded.SaveAlt, "Save Offline", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Offline", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onSyncAll()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                    ) {
                        Icon(Icons.Rounded.Refresh, "Sync All", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync All", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { SyncEngine.cancelAll() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Icon(Icons.Rounded.Cancel, "Cancel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleSyncRow(
    module: SyncModule,
    state: ModuleState,
    colors: com.amazecc.app.shared.theme.AmazeColors,
) {
    val icon = when (state.status) {
        SyncStatus.LOADING -> Icons.Rounded.HourglassTop
        SyncStatus.SUCCESS -> Icons.Rounded.CheckCircle
        SyncStatus.ERROR -> Icons.Rounded.Error
        SyncStatus.IDLE -> Icons.Rounded.RadioButtonUnchecked
    }
    val iconColor = when (state.status) {
        SyncStatus.LOADING -> colors.accent
        SyncStatus.SUCCESS -> Color(0xFF4CAF50)
        SyncStatus.ERROR -> Color(0xFFEF5350)
        SyncStatus.IDLE -> colors.textSecondary.copy(alpha = 0.4f)
    }
    val bgColor = when (state.status) {
        SyncStatus.LOADING -> colors.accent.copy(alpha = 0.06f)
        SyncStatus.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.06f)
        SyncStatus.ERROR -> Color(0xFFEF5350).copy(alpha = 0.06f)
        SyncStatus.IDLE -> colors.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.status == SyncStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = state.status.name,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.displayName,
                style = AmazeTheme.typography.body.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            )
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = AmazeTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        color = Color(0xFFEF5350)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (state.lastSynced != null) {
            Text(
                text = formatTimeAgo(state.lastSynced),
                style = AmazeTheme.typography.caption.copy(
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            )
        }
    }
}

private fun moduleShortName(module: SyncModule): String = when (module) {
    SyncModule.ATTENDANCE -> "Att"
    SyncModule.ALL_SEMESTER_ATTENDANCE -> "AllSem"
    SyncModule.TIMETABLE -> "TT"
    SyncModule.MARKS -> "Marks"
    SyncModule.GRADES -> "Grades"
    SyncModule.CURRICULUM -> "Curr"
    SyncModule.HOSTEL_DETAILS -> "Hostel"
    SyncModule.HOSTEL_LEAVES -> "Leaves"
    SyncModule.EXAM_SCHEDULE -> "Exam"
    SyncModule.CALENDAR -> "Cal"
    SyncModule.CALENDARS_LIST -> "CalList"
    SyncModule.PAYMENTS -> "Pay"
    SyncModule.LIBRARY -> "Lib"
    SyncModule.TRANSPORT -> "Trans"
    SyncModule.BUSES -> "Buses"
    SyncModule.LMS -> "LMS"
    SyncModule.EVENTS -> "Events"
    SyncModule.CLUBS -> "Clubs"
    SyncModule.QCM_VIEW -> "QCM"
    SyncModule.STUDENT_PROFILE -> "Profile"
    SyncModule.CAB_TRIPS -> "Cab Share"
    SyncModule.CIRCULARS -> "Circ"
    SyncModule.PROFILE_IMAGES -> "Imgs"
    SyncModule.BANK_INFO -> "Bank"
    SyncModule.DAYBOARDER -> "Day"
    SyncModule.EPT_SCHEDULE -> "EPT"
    SyncModule.REGISTRATION_SCHEDULE -> "Reg"
    SyncModule.APAAR_ID -> "APAAR"
}

private fun formatTimeAgo(instant: Instant): String {
    val now = kotlinx.datetime.Clock.System.now()
    val diff = now - instant
    val seconds = diff.inWholeSeconds
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
