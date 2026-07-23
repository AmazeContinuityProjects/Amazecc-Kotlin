package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.GitHubRelease
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = AmazeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(colors.chart2, colors.chart1))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.NewReleases, null, tint = colors.background, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Update Available",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 20.sp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${release.tagName}",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.chart2)
                )
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "You're running v$currentVersion. Tap download to get the latest release from GitHub.",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 20.sp)
                )
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "What's new:",
                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        release.body.take(500),
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, lineHeight = 18.sp, fontSize = 12.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.chart1),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp), tint = colors.textSecondary)
                Spacer(Modifier.width(6.dp))
                Text("Remind Later", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun UpdateResultDialog(
    status: com.amazecc.app.shared.state.AppState.UpdateStatus,
    onDismiss: () -> Unit,
    onCheckAgain: () -> Unit
) {
    val colors = AmazeTheme.colors
    val title: String
    val text: String
    val icon: @Composable () -> Unit

    when (status) {
        is com.amazecc.app.shared.state.AppState.UpdateStatus.UpToDate -> {
            title = "You're up to date"
            text = "You're running the latest version of AmazeCC."
            icon = {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.chart1.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text("✓", style = AmazeTheme.typography.heading.copy(color = colors.chart1, fontSize = 28.sp))
                }
            }
        }
        is com.amazecc.app.shared.state.AppState.UpdateStatus.Error -> {
            title = "Check Failed"
            text = status.message
            icon = {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.chart5.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text("!", style = AmazeTheme.typography.heading.copy(color = colors.chart5, fontSize = 28.sp))
                }
            }
        }
        else -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                icon()
                Spacer(Modifier.height(12.dp))
                Text(title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 20.sp))
            }
        },
        text = { Text(text, style = AmazeTheme.typography.body.copy(color = colors.textSecondary)) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (status is com.amazecc.app.shared.state.AppState.UpdateStatus.Error) colors.chart5 else colors.chart1),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (status is com.amazecc.app.shared.state.AppState.UpdateStatus.Error) "Try Again" else "Great", fontWeight = FontWeight.Bold)
            }
        }
    )
}
