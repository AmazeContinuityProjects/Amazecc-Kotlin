package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * Android-Settings-style primitives for the M3E settings overhaul.
 *
 * Design rules (why these exist):
 * - **No nested cards.** A group is a [SettingsGroupHeader] plus a run of rows
 *   painted directly on the surface. There is no `M3ESurfaceCard` wrapper and no
 *   row-level `background`, which is what caused the old double padding (card
 *   padding + row padding stacked).
 * - **One padding owner.** Every row owns exactly 16 dp of horizontal padding;
 *   group headers share the same 16 dp start edge so text aligns optically.
 * - **Real controls only.** A row either navigates ([SettingsMenuRow]), toggles
 *   a real persisted value ([SettingsToggleRow]), runs a real action
 *   ([SettingsActionRow]), or reports a real value ([SettingsInfoRow]).
 *
 * All composables are stateless: they take values + callbacks and can be
 * previewed/tested without AppState, SettingsManager or a network client.
 */

/** Section label above a run of setting rows (Android Settings category header). */
@Composable
fun SettingsGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = colors.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 6.dp)
    )
}

/** Inset divider that lines up with the row text, not the icon. */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 70.dp, end = 16.dp)
            .height(1.dp)
            .background(M3ETheme.colors.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * A navigation row: tinted icon, title/subtitle, optional current-value summary
 * and a chevron. Use for opening a settings detail screen.
 */
@Composable
fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val tint = if (danger) colors.error else colors.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableA11y(onClickLabel = "Open $title", onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (danger) colors.errorContainer else colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) colors.error else colors.onSurface
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
    SettingsRowDivider()
}

/** A row with a real on/off switch. The whole row is the touch target. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableA11y(onClickLabel = "Toggle $title", enabled = enabled, onClick = { onCheckedChange(!checked) })
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.primary,
                checkedTrackColor = colors.primaryContainer
            )
        )
    }
    SettingsRowDivider()
}

/**
 * A real action row (clear cache, run diagnostics, export, send test alert).
 * [busy] renders a progress spinner and disables the row instead of faking a result.
 */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val interactive = enabled && !busy
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableA11y(onClickLabel = title, enabled = interactive, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    danger -> colors.error
                    interactive -> colors.primary
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = when {
                    danger -> colors.error
                    interactive -> colors.onSurface
                    else -> colors.onSurfaceVariant
                }
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (busy) {
            M3ELoadingIndicator(modifier = Modifier.size(20.dp), size = 20.dp, color = colors.primary)
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    SettingsRowDivider()
}

/** A non-interactive label/value row for real persisted facts (version, username). */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface
        )
    }
    SettingsRowDivider()
}

/**
 * A labelled segmented-choice row, built on the real M3E connected button group.
 * Kept here so settings detail screens share one consistent control shape.
 */
@Composable
fun <T : Any> SettingsSegmentedRow(
    title: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
        }
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        M3EConnectedButtonGroup(
            items = items,
            selectedItem = selectedItem,
            onItemSelected = onItemSelected,
            labelProvider = labelProvider,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
    SettingsRowDivider()
}

/**
 * Top bar for a settings detail screen: back arrow + title. The caller owns the
 * scrolling container (a `LazyColumn` with 140 dp bottom inset for the floating
 * nav bar) and places this as its first item, followed by the detail body.
 */
@Composable
fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back to Settings",
                tint = colors.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface
        )
    }
}
