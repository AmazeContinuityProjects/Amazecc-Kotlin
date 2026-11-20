package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.vtop.VtopSource
import com.amazecc.app.shared.vtop.vtopEngineSupported

/**
 * First-run notice for the move to on-device VTOP.
 *
 * `vtop_source` defaults to [VtopSource.LOCAL] because the server genuinely cannot reach VTOP —
 * it is geo-restricted to Indian IPs and hono.amazecc.com runs in Singapore. That makes this a
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

    // D-112: bottom-sheet modal. This is a forced first-run choice, so the sheet is
    // non-dismissable — no X / swipe / backdrop / Escape / back. Both explicit actions
    // call onChoose, which clears the caller's showMigrationPrompt flag.
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = {},
        title = "Fetch VTOP on this device",
        dismissable = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
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
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmazeButton(
                    text = "Use on-device",
                    onClick = { onChoose(VtopSource.LOCAL) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onChoose(VtopSource.REMOTE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep server", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
