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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsSection
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.domain.overhaul.allSettingsSections
import com.amazecc.app.shared.ui.m3e.components.SettingsDetailScaffold
import com.amazecc.app.shared.ui.m3e.components.SettingsGroupHeader
import com.amazecc.app.shared.ui.m3e.components.SettingsMenuRow
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive System & Settings **master** screen.
 *
 * 100% stateless: accepts [M3ESettingsState] and emits [M3ESettingsAction].
 *
 * Android-Settings-style information architecture: a single, un-nested list of
 * grouped navigation rows. There are deliberately no nested surface cards —
 * the header, search field, group headers and rows each own exactly one 16 dp
 * start edge, which is what removes the old card + row double padding.
 *
 * Selecting a row emits [M3ESettingsAction.OpenSettingsMenu]; the host sets
 * [M3ESettingsState.openSettingsMenu] and this composable then renders the
 * detail screen inside [SettingsDetailScaffold]. All detail content lives in
 * [M3ESettingsDetailScreen] (same package).
 */
@Composable
fun M3ESettingsScreen(
    state: M3ESettingsState,
    onAction: (M3ESettingsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val openId = state.openSettingsMenu
    if (openId != null) {
        val title = if (openId == "advanced") {
            "Advanced & Developer"
        } else {
            allSettingsSections().firstOrNull { it.id == openId }?.title ?: "Settings"
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(M3ETheme.colors.surface),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item(key = "settings_detail_top_bar") {
                SettingsDetailScaffold(
                    title = title,
                    onBack = { onAction(M3ESettingsAction.CloseSettingsMenu) }
                )
            }
            item(key = "settings_detail_$openId") {
                M3ESettingsDetailScreen(openId, state, onAction)
            }
        }
        return
    }

    val colors = M3ETheme.colors
    val query = state.settingsSearchQuery

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        // Header — title, a one-line orientation subtitle, and the version chip.
        item(key = "settings_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (query.isBlank()) {
                            "Appearance, Engine, Storage & Privacy"
                        } else {
                            "Filtered — matching menus are grouped below"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                // D-135: no "material overhaul" version badge in the header.
                // The real version lives on the About screen; it is not a setting.
            }
            Spacer(Modifier.height(12.dp))
        }

        // Search — the host filters `visibleSettingsSections`; typing narrows
        // which groups/rows are rendered below.
        item(key = "settings_search") {
            SettingsSearchField(
                query = query,
                resultCount = state.visibleSettingsSections.size,
                onQueryChange = { onAction(M3ESettingsAction.UpdateSettingsSearch(it)) },
                onClear = { onAction(M3ESettingsAction.UpdateSettingsSearch("")) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Grouped navigation rows. Order follows the declared group order, not
        // the (search-reordered) section list, so the menu is stable.
        masterMenuGroups.forEach { group ->
            val rows = group.ids.mapNotNull { id ->
                state.visibleSettingsSections.firstOrNull { it.id == id }
            }
            if (rows.isNotEmpty()) {
                item(key = "settings_group_${group.header}") {
                    SettingsGroupHeader(group.header)
                }
                rows.forEach { section ->
                    item(key = "settings_menu_${section.id}") {
                        val spec = menuRowSpec(section, state)
                        SettingsMenuRow(
                            icon = spec.icon,
                            title = section.title,
                            subtitle = spec.subtitle,
                            value = spec.value,
                            danger = spec.danger,
                            onClick = { onAction(M3ESettingsAction.OpenSettingsMenu(section.id)) }
                        )
                    }
                }
            }
        }

        // Extra master row for the flattened advanced/developer hub. Kept
        // visible for an empty query, and for queries that name it.
        val advancedTitle = "Advanced & Developer"
        val advancedSubtitle = "Display, dashboard, navigation, credentials & sync"
        val showAdvanced = query.isBlank() ||
            advancedTitle.contains(query, ignoreCase = true) ||
            advancedSubtitle.contains(query, ignoreCase = true)
        if (showAdvanced) {
            item(key = "settings_group_advanced") {
                SettingsGroupHeader("Advanced")
            }
            item(key = "settings_menu_advanced") {
                SettingsMenuRow(
                    icon = Icons.Rounded.Build,
                    title = advancedTitle,
                    subtitle = advancedSubtitle,
                    onClick = { onAction(M3ESettingsAction.OpenSettingsMenu("advanced")) }
                )
            }
        }

        // Bottom spacing for the floating navigation bar (140 dp).
        item(key = "settings_bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }
}

// ==========================================
// Menu definitions & helpers
// ==========================================

private data class SettingsMenuGroupDef(val header: String, val ids: List<String>)

private val masterMenuGroups = listOf(
    SettingsMenuGroupDef("Personalize", listOf("theme", "app_icon", "keyboard")),
    SettingsMenuGroupDef("Academics & Study", listOf("pomodoro", "privacy")),
    SettingsMenuGroupDef("Data & Storage", listOf("storage", "api_target", "export_import")),
    SettingsMenuGroupDef("Notifications", listOf("notifications"))
)

/** Static presentation data for one master row; `value` is always a real state value. */
private data class MenuRowSpec(
    val icon: ImageVector,
    val subtitle: String,
    val value: String? = null,
    val danger: Boolean = false
)

private fun menuRowSpec(section: M3ESettingsSection, state: M3ESettingsState): MenuRowSpec =
    when (section.id) {
        "theme" -> MenuRowSpec(
            icon = Icons.Rounded.ColorLens,
            subtitle = section.subtitle,
            value = state.selectedPalettePreset.label
        )

        "app_icon" -> MenuRowSpec(Icons.Rounded.Brush, section.subtitle)

        "keyboard" -> MenuRowSpec(Icons.Rounded.Keyboard, section.subtitle)

        "pomodoro" -> MenuRowSpec(
            icon = Icons.Rounded.Timer,
            subtitle = section.subtitle,
            value = "${state.pomodoroFocusMinutes}/${state.pomodoroBreakMinutes} min"
        )

        // D-008: privacy blur is explicitly OUT of scope for app-wide masking.
        // Say so honestly on the master row instead of implying grades/marks blur.
        "privacy" -> MenuRowSpec(
            icon = Icons.Rounded.HealthAndSafety,
            subtitle = "Masks fee amounts only — app-wide blur for grades, attendance and marks is a separate workstream",
            value = if (state.privacyModeEnabled) "On" else "Off"
        )

        "storage" -> MenuRowSpec(
            icon = Icons.Rounded.Storage,
            subtitle = section.subtitle,
            value = state.cacheSizeText
        )

        "api_target" -> MenuRowSpec(
            icon = Icons.Rounded.CloudDone,
            subtitle = section.subtitle,
            value = state.apiTargetName
        )

        "export_import" -> MenuRowSpec(Icons.Rounded.Upload, section.subtitle)

        "notifications" -> MenuRowSpec(Icons.Rounded.Notifications, section.subtitle)

        else -> MenuRowSpec(Icons.Rounded.Build, section.subtitle)
    }

@Composable
private fun SettingsSearchField(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search settings…") },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search settings") },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = onClear) { Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear search") } }
        } else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerLow,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant
        )
    )
    if (query.isNotBlank() && resultCount == 0) {
        Text(
            text = "No settings match — try another word",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp)
        )
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3ESettingsScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ESettingsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

private fun createMockSettingsState() = M3ESettingsState(
    selectedThemeMode = M3EThemeMode.DARK_OLED,
    selectedPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
    honoApiBaseUrl = "https://hono.amazecc.com",
    cacheSizeText = "14.2 MB",
    isDiagnosticsRunning = false,
    diagnosticStatus = "Not checked",
    visibleSettingsSections = allSettingsSections(),
    privacyModeEnabled = false,
    classReminderLeadMinutes = 15,
    quietHoursEnabled = false,
    appIconTheme = com.amazecc.app.shared.utils.AppIconTheme.DEFAULT
)
