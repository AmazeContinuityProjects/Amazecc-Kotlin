package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.PaletteMode
import com.amazecc.app.shared.theme.PaletteRole
import com.amazecc.app.shared.theme.currentOf
import com.amazecc.app.shared.theme.toHexString
import com.amazecc.app.shared.ui.components.AmazePill
import com.amazecc.app.shared.ui.components.ColorPickerSheet

/**
 * Full-screen palette editor: per-mode (light/dark) per-role color overrides,
 * applied live to the running theme via AppState.
 */
@Composable
fun PaletteEditorScreen() {
    val colors = AmazeTheme.colors
    val palette by AppState.customPalette.collectAsState()
    var mode by remember { mutableStateOf(PaletteMode.LIGHT) }
    var editingRole by remember { mutableStateOf<PaletteRole?>(null) }
    val overrides = palette.overridesFor(mode)

    Column(
        modifier = Modifier
            .background(colors.background)
            .padding(horizontal = 16.dp)
            .padding(bottom = 48.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Custom Palette",
                style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { AppState.resetCustomPalette() },
                enabled = overrides.values.isNotEmpty()
            ) {
                Text("Reset All", color = if (overrides.values.isNotEmpty()) colors.danger else colors.textMuted)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Tweak every color role — the app handles the rest. Dark overrides also apply to AMOLED mode.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmazePill(
                label = "Light",
                selected = mode == PaletteMode.LIGHT,
                colors = colors,
                onClick = { mode = PaletteMode.LIGHT }
            )
            AmazePill(
                label = "Dark",
                selected = mode == PaletteMode.DARK,
                colors = colors,
                onClick = { mode = PaletteMode.DARK }
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteRole.EDITABLE.forEach { role ->
                val override = overrides.color(role)
                val effective = role.currentOf(colors)
                val isOverride = override != null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.surface)
                        .border(1.dp, if (isOverride) colors.accent.copy(alpha = 0.5f) else colors.textMuted.copy(alpha = 0.15f), RoundedCornerShape(AmazeTheme.radius.medium))
                        .clickable { editingRole = role }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(effective)
                            .border(2.dp, if (isOverride) colors.accent else colors.textMuted.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOverride) {
                            Icon(Icons.Rounded.Done, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            role.label,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            if (isOverride) "${override.toHexString()} · overridden" else effective.toHexString(),
                            style = AmazeTheme.typography.caption.copy(color = if (isOverride) colors.accent else colors.textMuted, fontSize = AmazeTheme.fontSize.xs)
                        )
                    }
                    if (isOverride) {
                        IconButton(
                            onClick = { AppState.clearPaletteRole(mode, role) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Undo, "Reset to default", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "${overrides.values.size} override${if (overrides.values.size == 1) "" else "s"} in ${mode.label} mode",
            style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
        )
    }

    val currentRole = editingRole
    if (currentRole != null) {
        ColorPickerSheet(
            title = "${mode.label} · ${currentRole.label}",
            initial = overrides.color(currentRole) ?: currentRole.currentOf(colors),
            colors = colors,
            onSelected = { color ->
                AppState.setPaletteRole(mode, currentRole, color)
                editingRole = null
            },
            onDismiss = { editingRole = null }
        )
    }
}
