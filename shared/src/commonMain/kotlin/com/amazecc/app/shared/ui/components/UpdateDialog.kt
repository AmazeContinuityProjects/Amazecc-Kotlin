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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.amazecc.app.shared.model.GitHubRelease
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.isAndroid

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = AmazeTheme.colors
    val isAndroid = isAndroid()
    val uriHandler = LocalUriHandler.current

    val updateIconGradient = remember(colors) {
        Brush.linearGradient(listOf(colors.chart2, colors.chart1))
    }

    val apkAssetUrl = remember(release) {
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl ?: release.htmlUrl
    }

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
                        .background(updateIconGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.NewReleases, null, tint = colors.background, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Update Available",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.xl)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.accent.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("v${currentVersion.removePrefix("v")}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold))
                    Text("➔", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                    Text("v${release.tagName.removePrefix("v").removePrefix("V")}", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                if (isAndroid) {
                    Text(
                        "A new update is available on GitHub. Tap download to get the latest release.",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 20.sp)
                    )
                } else {
                    Text(
                        "A new release is available. iOS builds are distributed via TestFlight — check the release notes below.",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 20.sp)
                    )
                }
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "What's new:",
                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        release.body.take(500),
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, lineHeight = 18.sp, fontSize = AmazeTheme.fontSize.sm)
                    )
                }
            }
        },
        confirmButton = {
            if (isAndroid) {
                Button(
                    onClick = { onDownload(); uriHandler.openUri(apkAssetUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.chart1),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download APK", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onDownload(); uriHandler.openUri(release.htmlUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.chart1),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Release Notes", fontWeight = FontWeight.Bold)
                }
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