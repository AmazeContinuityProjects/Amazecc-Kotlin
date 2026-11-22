package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.m3e.components.M3ELoadingIndicator
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckModal(onDismiss: () -> Unit) {
    val colors = AmazeTheme.colors
    val updateStatus by AppState.updateStatus.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // D-112 follow-up: callers (AboutScreen / SettingsAccountPages) only clear their
    // local `showUpdateCheck` flag on dismissal. Reset AppState's status as well so a
    // settled check — notably an Error — cannot reappear the next time the sheet is
    // opened. The Available hand-off below deliberately keeps the plain onDismiss:
    // that path must stay in AppState long enough for UpdateDialog to render.
    val dismissCheck: () -> Unit = {
        AppState.clearUpdateStatus()
        onDismiss()
    }

    LaunchedEffect(updateStatus) {
        if (updateStatus is AppState.UpdateStatus.Available) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismissCheck,
        sheetState = sheetState,
        containerColor = M3ETheme.colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val status = updateStatus) {
                is AppState.UpdateStatus.Checking -> {
                    M3ELoadingIndicator(size = 40.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Checking for updates…",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Contacting the update server…",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
                    )
                }
                is AppState.UpdateStatus.Available -> {
                    M3ELoadingIndicator(size = 40.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Update found — opening…",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
                is AppState.UpdateStatus.UpToDate -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You're up to date!",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No updates available — you're on the latest version.",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(20.dp))
                    AmazeButton(
                        text = "OK",
                        onClick = dismissCheck,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is AppState.UpdateStatus.Idle -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You're up to date!",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No updates available — you're on the latest version.",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(20.dp))
                    AmazeButton(
                        text = "OK",
                        onClick = dismissCheck,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is AppState.UpdateStatus.Error -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Check failed",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        status.message,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(20.dp))
                    AmazeButton(
                        text = "Try Again",
                        onClick = { AppState.forceCheckForUpdate() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = dismissCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Close", color = colors.textSecondary)
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Checking for updates…",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
            }
        }
    }

    val availableStatus = updateStatus as? AppState.UpdateStatus.Available
    if (availableStatus != null) {
        UpdateDialog(
            release = availableStatus.release,
            currentVersion = availableStatus.currentVersion,
            onDismiss = { AppState.dismissUpdateDialog() },
            onDownload = { AppState.dismissUpdateDialog() }
        )
    }
}