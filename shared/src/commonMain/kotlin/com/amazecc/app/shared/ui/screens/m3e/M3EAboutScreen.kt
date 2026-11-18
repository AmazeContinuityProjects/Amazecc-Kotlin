package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Immutable, screen-owned state for [M3EAboutScreen]. */
@Immutable
data class M3EAboutState(
    val version: String = "",
    val updateStatusText: String? = null,
    val releaseHighlights: List<String> = emptyList(),
    val isCheckingUpdate: Boolean = false,
    val showChangelog: Boolean = false,
    val showUpdateCheck: Boolean = false
)

/** User intents emitted by [M3EAboutScreen]. */
sealed interface M3EAboutAction {
    data object CheckForUpdates : M3EAboutAction
    data object OpenChangelog : M3EAboutAction
    data object DismissDialog : M3EAboutAction
    data object OpenGithub : M3EAboutAction
    data object Back : M3EAboutAction
}

/**
 * Material 3 Expressive About & Diagnostics screen.
 * 100% stateless: receives [M3EAboutState] and emits [M3EAboutAction].
 * Version, update status and release notes are read by the host from
 * UpdateConfig / AppState.updateStatus / AppState.latestReleaseNotes.
 */
@Composable
fun M3EAboutScreen(
    state: M3EAboutState,
    onAction: (M3EAboutAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Text(
                text = "About AmazeCC",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = colors.onSurface
            )
        }

        // 1. App identity hero — the single expressive element on this screen.
        item(key = "identity") {
            M3ESurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerLevel = SurfaceContainerLevel.HIGH,
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "AmazeCC",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primary)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state.version.isBlank()) "version" else "v${state.version}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Your all-in-one student companion for VIT. Track attendance, manage academics, explore campus life, and stay connected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 2. Updates.
        item(key = "update_actions") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ElevatedButton(
                    onClick = { onAction(M3EAboutAction.CheckForUpdates) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    enabled = !state.isCheckingUpdate,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isCheckingUpdate) "Checking for updates..." else "Check for Updates",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                ElevatedButton(
                    onClick = { onAction(M3EAboutAction.OpenChangelog) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "What's New (Changelog)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (!state.updateStatusText.isNullOrBlank()) {
            item(key = "update_status") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.updateStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }

        // 3. Credits, kept calm and scannable.
        item(key = "credits") {
            M3ESurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerLevel = SurfaceContainerLevel.LOW,
                onClick = { onAction(M3EAboutAction.OpenGithub) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "App Information & Credits",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open GitHub repository",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Developed by the AmazeCC Open Source Team",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "Built with Kotlin Multiplatform & Compose Multiplatform",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "Powered by VTOP API & Secure Local Cache",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }

    if (state.showChangelog) {
        AlertDialog(
            onDismissRequest = { onAction(M3EAboutAction.DismissDialog) },
            title = { Text("What's New in v${state.version}", color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.releaseHighlights.isNotEmpty()) {
                        state.releaseHighlights.forEach { line ->
                            Text("• $line", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            text = "No release notes available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(M3EAboutAction.DismissDialog) }) {
                    Text("Close", color = colors.primary)
                }
            }
        )
    }

    if (state.showUpdateCheck) {
        AlertDialog(
            onDismissRequest = { onAction(M3EAboutAction.DismissDialog) },
            title = { Text("Update Check", color = colors.onSurface) },
            text = {
                Text(
                    text = state.updateStatusText ?: "Checking for updates...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(M3EAboutAction.DismissDialog) }) {
                    Text("OK", color = colors.primary)
                }
            }
        )
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewAboutState() = M3EAboutState(
    version = "2.4.0",
    updateStatusText = "You are up to date",
    releaseHighlights = listOf("Faster sync", "Material 3 Expressive UI")
)

@Preview
@Composable
private fun M3EAboutScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAboutScreen(state = previewAboutState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EAboutScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EAboutScreen(state = previewAboutState(), onAction = {})
    }
}
