package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeSearchInput
import com.amazecc.app.shared.utils.UpdateConfig

@Composable
fun SettingsHub(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSubScreen: (SettingsSubScreen) -> Unit
) {
    val colors = AmazeTheme.colors

    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()
    val uiScale by AppState.uiScale.collectAsState()
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    val widgetOrder by AppState.widgetOrder.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    var version by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        version = UpdateConfig.getCurrentVersion()
    }

    fun valueFor(sub: SettingsSubScreen): String? = when (sub) {
        SettingsSubScreen.APPEARANCE -> "${themeLabel(activeTheme)} · ${accentLabel(activeAccent)}"
        SettingsSubScreen.DISPLAY -> "${(uiScale * 100).toInt()}%"
        SettingsSubScreen.DASHBOARD -> "${widgetOrder.size} widget${if (widgetOrder.size != 1) "s" else ""}"
        SettingsSubScreen.BOTTOM_NAV -> "${pinnedTabs.size}/4 tabs"
        SettingsSubScreen.ACADEMICS -> semesterMap[selectedSemester] ?: selectedSemester
        SettingsSubScreen.DATA_SYNC -> {
            val on = listOf(
                SettingsManager.isNotifClassRemindersEnabled(),
                SettingsManager.isNotifAssignmentRemindersEnabled(),
                SettingsManager.isNotifTaskRemindersEnabled()
            ).count { it }
            "$on/3 alerts"
        }
        SettingsSubScreen.CREDENTIALS -> {
            val username = SettingsManager.getString(SettingsManager.KEY_USERNAME)
            if (username.isNotBlank()) username else "Not saved"
        }
        SettingsSubScreen.ABOUT -> if (version.isBlank()) null else "v$version"
        SettingsSubScreen.DANGER -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AmazeSearchInput(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "Search all settings (e.g., Theme, Semester, Sync, Haptics)..."
        )

        if (searchQuery.isNotBlank()) {
            val results = SettingsSubScreen.entries.filter { sub ->
                sub.title.contains(searchQuery, ignoreCase = true) ||
                        sub.description.contains(searchQuery, ignoreCase = true)
            }
            if (results.isEmpty()) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No settings found for \"$searchQuery\"",
                            color = colors.textSecondary,
                            style = AmazeTheme.typography.body,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                results.forEachIndexed { index, sub ->
                    SettingsRow(
                        icon = sub.icon,
                        title = sub.title,
                        subtitle = "${sub.description}  ·  ${sub.group.label}",
                        value = valueFor(sub),
                        tint = if (sub.group == SettingsGroup.DANGER) colors.danger else colors.accent,
                        danger = sub.group == SettingsGroup.DANGER,
                        onClick = { onOpenSubScreen(sub) }
                    )
                    if (index < results.lastIndex) SettingsRowDivider()
                }
            }
        } else {
            SettingsGroup.entries.forEach { group ->
                val subs = SettingsSubScreen.entries.filter { it.group == group }
                if (subs.isEmpty()) return@forEach

                SettingsGroupLabel(group.label)
                SettingsGroupCard(danger = group == SettingsGroup.DANGER) {
                    subs.forEachIndexed { index, sub ->
                        SettingsRow(
                            icon = sub.icon,
                            title = sub.title,
                            subtitle = sub.description,
                            value = valueFor(sub),
                            tint = if (group == SettingsGroup.DANGER) colors.danger else colors.accent,
                            danger = group == SettingsGroup.DANGER,
                            onClick = { onOpenSubScreen(sub) }
                        )
                        if (index < subs.lastIndex) SettingsRowDivider()
                    }
                }
            }
        }
    }
}

private fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.LIGHT -> "Light"
    AppTheme.DARK -> "Dark"
    AppTheme.AMOLED -> "AMOLED"
    AppTheme.SYSTEM -> "System"
}

private fun accentLabel(accent: AccentTheme): String = when (accent) {
    AccentTheme.OCEAN -> "Ocean"
    AccentTheme.FOREST -> "Forest"
    AccentTheme.LAVENDER -> "Lavender"
    AccentTheme.SUNSET -> "Sunset"
}
