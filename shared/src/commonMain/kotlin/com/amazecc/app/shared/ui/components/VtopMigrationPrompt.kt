package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.vtop.VtopSource
import com.amazecc.app.shared.vtop.vtopEngineSupported

/**
 * First-run notice for the move to on-device VTOP.
 *
 * `vtop_source` defaults to [VtopSource.LOCAL] because the server genuinely cannot reach VTOP —
 * it is geo-restricted to Indian IPs and api.amazecc.com runs in Singapore. That makes this a
 * behaviour change for existing installs, so the user is asked once rather than being switched
 * silently. See docs/sep-29-2026/vtop-local-integration-plan.md.
 *
 * Not shown where [VtopSource.LOCAL] cannot work (iOS), since there is no alternative to offer.
 */
@Composable
fun VtopMigrationPrompt(
    onChoose: (VtopSource) -> Unit
) {
    val colors = AmazeTheme.colors

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            AmazeButton(
                text = "Use on-device",
                onClick = { onChoose(VtopSource.LOCAL) }
            )
        },
        dismissButton = {
            TextButton(onClick = { onChoose(VtopSource.REMOTE) }) {
                Text("Keep server", color = colors.textSecondary)
            }
        },
        title = {
            Text(
                "Fetch VTOP on this device",
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
            ) {
                Text(
                    "VTOP only accepts connections from Indian networks, so it can no longer be " +
                        "read through AmazeCC's server. This app can now fetch it directly instead.",
                    color = colors.textSecondary
                )
                Text(
                    "Your VTOP password and session stay on this device. Features backed by " +
                        "AmazeCC's own database — question bank, clubs, cab share, transport — " +
                        "are unaffected either way.",
                    color = colors.textSecondary
                )
                if (!vtopEngineSupported) {
                    Text(
                        "On-device fetching is not available on this platform, so the server " +
                            "option will be used.",
                        color = colors.warningText
                    )
                }
                Text(
                    "You can change this any time in Settings, and the diagnostics page will tell " +
                        "you which source is working.",
                    color = colors.textMuted
                )
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(AmazeTheme.radius.large)
    )
}
