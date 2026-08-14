package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.DashboardWidget
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun AppearancePage(onOpenSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val colors = AmazeTheme.colors
    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()
    val customAccent by AppState.customAccentColor.collectAsState()
    val customPalette by AppState.customPalette.collectAsState()
    val hapticEnabled by AppState.hapticEnabled.collectAsState()
    val animationsEnabled by AppState.animationsEnabled.collectAsState()
    val heroColorEnabled by AppState.heroColorEnabled.collectAsState()
    var showCustomAccent by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Color Theme")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppTheme.LIGHT to "Light",
                        AppTheme.DARK to "Dark",
                        AppTheme.SYSTEM to "System"
                    ).forEach { (theme, label) ->
                        val isSelected = activeTheme == theme
                        AmazeButton(
                            text = label,
                            onClick = { AppState.changeTheme(theme) },
                            modifier = Modifier.weight(1f),
                            variant = if (isSelected) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                        )
                    }
                }
                val isAmoled = activeTheme == AppTheme.AMOLED
                AmazeButton(
                    text = if (isAmoled) "✦ AMOLED Pure Black" else "AMOLED Pure Black",
                    onClick = { AppState.changeTheme(AppTheme.AMOLED) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = if (isAmoled) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
            }
        }

        SettingsGroupLabel("Accent Colors")
        SettingsGroupCard {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccentSwatch("Ocean", AccentTheme.OCEAN, activeAccent, colors, Modifier.weight(1f))
                AccentSwatch("Forest", AccentTheme.FOREST, activeAccent, colors, Modifier.weight(1f))
                AccentSwatch("Lavender", AccentTheme.LAVENDER, activeAccent, colors, Modifier.weight(1f))
                AccentSwatch("Sunset", AccentTheme.SUNSET, activeAccent, colors, Modifier.weight(1f))
                AccentSwatch("Custom", AccentTheme.CUSTOM, activeAccent, colors, Modifier.weight(1f),
                    customColor = customAccent,
                    onClick = { showCustomAccent = true })
            }
        }

        if (showCustomAccent) {
            ColorPickerSheet(
                title = "Custom Accent",
                initial = customAccent,
                colors = colors,
                onSelected = { AppState.setCustomAccent(it); showCustomAccent = false },
                onDismiss = { showCustomAccent = false }
            )
        }

        SettingsGroupLabel("Custom Palette")
        SettingsGroupCard {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Adjust,
                    title = "Enable Custom Palette",
                    subtitle = "Override every color role for light & dark",
                    tint = colors.accent,
                    checked = customPalette.enabled,
                    onCheckedChange = { AppState.setPaletteEnabled(it) }
                )
                SettingsRowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSubScreen(SettingsSubScreen.PALETTE) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Edit Palette",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    val overrideCount = customPalette.light.values.size + customPalette.dark.values.size
                    Text(
                        if (overrideCount == 0) "Not started" else "$overrideCount override${if (overrideCount == 1) "" else "s"}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        SettingsGroupLabel("Interactions & Feel")
        SettingsGroupCard {
            SettingsSwitchRow(
                icon = Icons.Rounded.Vibration,
                title = "Haptic Feedback",
                subtitle = "Vibrate on button taps, card presses & navigation",
                tint = colors.accent,
                checked = hapticEnabled,
                onCheckedChange = { AppState.setHapticEnabled(it) }
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                icon = Icons.Rounded.Palette,
                title = "Colorful Hero Cards",
                subtitle = "Accent gradient summary cards; off uses a neutral surface",
                tint = colors.accent,
                checked = heroColorEnabled,
                onCheckedChange = { AppState.setHeroColorEnabled(it) }
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                icon = Icons.Rounded.Animation,
                title = "Spring Animations",
                subtitle = "Bouncy press-scale physics on interactive cards & swatches",
                tint = colors.accent,
                checked = animationsEnabled,
                onCheckedChange = { AppState.setAnimationsEnabled(it) }
            )
        }
    }
}

@Composable
fun DisplayPage() {
    val colors = AmazeTheme.colors
    val cgpaHidden by AppState.cgpaHidden.collectAsState()
    val attendanceMode by AppState.attendanceDisplayMode.collectAsState()
    val currentScale by AppState.uiScale.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Display Preferences")
        SettingsGroupCard {
            SettingsSwitchRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "Hide CGPA on Dashboard",
                subtitle = "Keep your CGPA private on the home screen",
                tint = colors.accent,
                checked = cgpaHidden,
                onCheckedChange = { AppState.setCgpaHidden(it) }
            )
            SettingsRowDivider()
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Attendance Format", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                AmazeSegmentedControl(
                    items = listOf(
                        AttendanceDisplayMode.PERCENTAGE to "Percentage (%)",
                        AttendanceDisplayMode.FRACTION to "Fraction (x/y)"
                    ),
                    selectedItem = attendanceMode,
                    onItemSelected = { AppState.setAttendanceDisplayMode(it) }
                )
            }
            SettingsRowDivider()
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("UI Scale & Zoom", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { AppState.changeUiScale((currentScale - 0.05f).coerceIn(0.7f, 1.5f)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ZoomOut, "Decrease scale", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    Slider(
                        value = currentScale,
                        onValueChange = { AppState.changeUiScale(it) },
                        valueRange = 0.7f..1.5f,
                        steps = 7,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                    )
                    IconButton(
                        onClick = { AppState.changeUiScale((currentScale + 0.05f).coerceIn(0.7f, 1.5f)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ZoomIn, "Increase scale", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Scale: ${(currentScale * 100).toInt()}%", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                    TextButton(onClick = { AppState.changeUiScale(1.0f) }) {
                        Text("Reset (100%)", color = colors.accent, fontSize = AmazeTheme.fontSize.xs, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardPage() {
    val colors = AmazeTheme.colors
    val widgetOrder by AppState.widgetOrder.collectAsState()
    val hiddenWidgets = remember(widgetOrder) {
        DashboardWidget.entries.filter { it !in widgetOrder }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Home Screen Widgets")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Toggle widgets on/off and reorder them for your home screen.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                DashboardWidgetRows()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${widgetOrder.size} of ${DashboardWidget.entries.size} widgets visible",
                        color = colors.textSecondary,
                        fontSize = AmazeTheme.fontSize.xs
                    )
                    TextButton(onClick = { AppState.resetWidgetsToDefault() }) {
                        Text("Reset Default", color = colors.accent, fontSize = AmazeTheme.fontSize.xs, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (hiddenWidgets.isNotEmpty()) {
            SettingsGroupLabel("Hidden Widgets")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hiddenWidgets.forEach { widget ->
                        SettingsRow(
                            icon = Icons.Rounded.Add,
                            title = getWidgetTitle(widget),
                            subtitle = getWidgetDescription(widget),
                            tint = colors.accent,
                            onClick = { AppState.restoreWidget(widget) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavPage() {
    val colors = AmazeTheme.colors
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    var selectedTabsList by remember(pinnedTabs) { mutableStateOf(pinnedTabs) }
    var showAddModuleDialog by remember { mutableStateOf(false) }

    val availableTabs = listOf(
        com.amazecc.app.shared.state.Screen.ATTENDANCE,
        com.amazecc.app.shared.state.Screen.ACADEMICS,
        com.amazecc.app.shared.state.Screen.LIBRARIES,
        com.amazecc.app.shared.state.Screen.PROFILE,
        com.amazecc.app.shared.state.Screen.PAYMENTS,
        com.amazecc.app.shared.state.Screen.CABSHARE,
        com.amazecc.app.shared.state.Screen.TRANSPORT,
        com.amazecc.app.shared.state.Screen.CALENDAR,
        com.amazecc.app.shared.state.Screen.FFCS_PLANNER,
        com.amazecc.app.shared.state.Screen.FREE_CLASSROOMS,
        com.amazecc.app.shared.state.Screen.QBANK,
        com.amazecc.app.shared.state.Screen.SOCIAL
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Live Preview")
        SettingsGroupCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("LIVE BOTTOM BAR PREVIEW", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Home, "Home", tint = colors.accent, modifier = Modifier.size(20.dp))
                        Text("Home", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    selectedTabsList.forEach { tab ->
                        val (icon, label) = getScreenIconAndLabel(tab)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            Text(label, color = colors.textSecondary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Grid3x3, "More", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        Text("More", color = colors.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        SettingsGroupLabel("Pinned Tabs")
        SettingsGroupCard {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pinned Tabs (${selectedTabsList.size}/4)", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base)
                    if (selectedTabsList.size < 4) {
                        AmazeButton(
                            text = "+ Add Module",
                            onClick = { showAddModuleDialog = true },
                            variant = ButtonVariant.PRIMARY,
                            height = 36.dp
                        )
                    }
                }

                if (selectedTabsList.isEmpty()) {
                    Text(
                        "No additional tabs pinned. Tap '+ Add Module' to pin quick access tabs.",
                        color = colors.textMuted,
                        fontSize = AmazeTheme.fontSize.xs,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                selectedTabsList.forEachIndexed { index, tab ->
                    val (icon, label) = getScreenIconAndLabel(tab)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DragHandle, "Handle bar", tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Icon(icon, label, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, modifier = Modifier.weight(1f))

                        if (index > 0) {
                            IconButton(
                                onClick = {
                                    val newList = selectedTabsList.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index - 1]
                                    newList[index - 1] = temp
                                    selectedTabsList = newList
                                    AppState.setPinnedNavTabs(newList)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (index < selectedTabsList.size - 1) {
                            IconButton(
                                onClick = {
                                    val newList = selectedTabsList.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index + 1]
                                    newList[index + 1] = temp
                                    selectedTabsList = newList
                                    AppState.setPinnedNavTabs(newList)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                val newList = selectedTabsList.filter { it != tab }
                                selectedTabsList = newList
                                AppState.setPinnedNavTabs(newList)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Close, "Unpin", tint = colors.dangerText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddModuleDialog) {
        val unpinned = availableTabs.filter { !selectedTabsList.contains(it) }
        AlertDialog(
            onDismissRequest = { showAddModuleDialog = false },
            title = { Text("Add Module to Bottom Bar", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unpinned.isEmpty()) {
                        Text("All available modules are already pinned.", color = colors.textSecondary)
                    } else {
                        unpinned.forEach { screen ->
                            val (icon, label) = getScreenIconAndLabel(screen)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .clickable {
                                        val newList = selectedTabsList + screen
                                        selectedTabsList = newList
                                        AppState.setPinnedNavTabs(newList)
                                        showAddModuleDialog = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, label, tint = colors.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(label, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddModuleDialog = false }) {
                    Text("Close", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun AccentSwatch(name: String, accent: AccentTheme, current: AccentTheme, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier, customColor: Color = Color(0xFF0EA5E9), onClick: (() -> Unit)? = null) {
    val selected = accent == current
    val swatchColor = when (accent) {
        AccentTheme.OCEAN -> colors.accent
        AccentTheme.FOREST -> colors.success
        AccentTheme.LAVENDER -> colors.info
        AccentTheme.SUNSET -> colors.warning
        AccentTheme.CUSTOM -> customColor
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (selected) swatchColor.copy(alpha = 0.18f) else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) swatchColor else colors.border,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (onClick != null) onClick() else AppState.changeAccent(accent) }
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Text(
                text = name,
                color = if (selected) swatchColor else colors.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = AmazeTheme.fontSize.xs,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
