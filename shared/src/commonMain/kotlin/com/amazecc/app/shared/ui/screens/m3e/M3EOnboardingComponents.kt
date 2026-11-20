package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Uppercase tracked section label used across every onboarding page. */
@Composable
internal fun M3EOnboardingSectionLabel(
    text: String,
    tint: Color? = null
) {
    val effectiveTint = tint ?: M3ETheme.colors.primary
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = effectiveTint,
        letterSpacing = 1.4.sp
    )
}

/** Small tonal badge (DISCOVER / PLAN / TELL). */
@Composable
internal fun M3EOnboardingBadge(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            letterSpacing = 1.sp
        )
    }
}

/** M3E switch row with a tonal icon container — used for all preference toggles. */
@Composable
internal fun M3EOnboardingSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color? = null
) {
    val colors = M3ETheme.colors
    val effectiveTint = tint ?: colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // D-120: the row is the single toggle source. The indicator on the
            // right is draw-only so there is no second click/toggle target.
            .clickableA11y(
                onClickLabel = if (checked) "Turn off $title" else "Turn on $title",
                onClick = { onCheckedChange(!checked) }
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) effectiveTint.copy(alpha = 0.16f) else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) effectiveTint else colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        // Display-only: the row above is the one toggle source and carries the
        // Role/label through clickableA11y.
        M3EDisplaySwitch(checked = checked, tint = effectiveTint)
    }
}

/** M3 switch appearance with no input semantics — used when a parent row owns the toggle. */
@Composable
private fun M3EDisplaySwitch(checked: Boolean, tint: Color) {
    val colors = M3ETheme.colors
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) tint else colors.surfaceContainerHighest),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.onSurfaceVariant)
        )
    }
}

/** Expressive multi-select chip (used where more than one option can be active). */
@Composable
internal fun M3EOnboardingChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    tint: Color? = null
) {
    val colors = M3ETheme.colors
    val effectiveTint = tint ?: colors.primary
    val background by animateColorAsState(
        targetValue = if (selected) effectiveTint else colors.surfaceContainerLow,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "OnboardingChipBackground"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "OnboardingChipContent"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else colors.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickableA11y(
                onClickLabel = if (selected) "Deselect $label" else "Select $label",
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = content, maxLines = 1)
        }
    }
}

/** M3E read-only tappable field used to open a time picker. */
@Composable
internal fun M3EOnboardingPickerField(
    value: String,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickableA11y(onClickLabel = "Choose $label", onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** M3E bottom sheet wrapper shared by all onboarding pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3EOnboardingSheet(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = M3ETheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = colors.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** M3E time-sheet for the auto-sync schedules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3EOnboardingTimePickerSheet(
    title: String,
    initial: LocalTime,
    onSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = M3ETheme.colors
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    M3EOnboardingSheet(title = title, icon = Icons.Rounded.Schedule, onDismiss = onDismiss) {
        TimePicker(
            state = state,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSelected(LocalTime(state.hour, state.minute)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
        ) {
            Text("Choose Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** M3E semester picker with search, replacing the legacy SemesterPickerSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3EOnboardingSemesterSheet(
    options: List<Pair<String, String>>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val colors = M3ETheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) options else options.filter { (id, name) -> id.lowercase().contains(q) || name.lowercase().contains(q) }
    }
    M3EOnboardingSheet(title = "Select Semester", icon = Icons.Rounded.CalendarMonth, onDismiss = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search semesters...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                cursorColor = colors.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No semesters match \"$query\"", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.first }) { (id, name) ->
                    val isSelected = id == selectedId
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
                        animationSpec = M3ETheme.motion.effectsDefault(),
                        label = "SemesterRowBackground"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickableA11y(onClickLabel = "Select semester $id", onClick = { onSelect(id) })
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Text(name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** M3E custom-accent sheet: expressive preset swatches plus hue/saturation sliders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3EOnboardingCustomAccentSheet(
    initial: Color,
    onSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = M3ETheme.colors
    var hue by remember { mutableStateOf(210f) }
    var saturation by remember { mutableStateOf(0.7f) }
    val live = hsvToColor(hue, saturation, 1f)
    M3EOnboardingSheet(title = "Custom Accent", icon = Icons.Rounded.Palette, onDismiss = onDismiss) {
        Text("Pick a signature color", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                colors.primary, colors.secondary, colors.tertiary,
                colors.success, colors.warning, colors.error, initial
            ).forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(2.dp, if (swatch == live) colors.onSurface else colors.outlineVariant, CircleShape)
                        .clickableA11y(onClickLabel = "Use this preset color") {
                            hue = 0f
                            saturation = 0f
                            onSelected(swatch)
                            onDismiss()
                        }
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Text("Hue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = live, activeTrackColor = live, inactiveTrackColor = colors.surfaceContainerHighest)
        )
        Text("Saturation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = live, activeTrackColor = live, inactiveTrackColor = colors.surfaceContainerHighest)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(live),
            contentAlignment = Alignment.Center
        ) {
            Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (live.luminance() > 0.5f) Color.Black else Color.White)
        }
        Button(
            onClick = { onSelected(live); onDismiss() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
        ) {
            Text("Save Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** HSV → Compose Color without relying on platform color helpers. */
private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f / 60f
    val i = hue.toInt()
    val f = hue - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) = when (i) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, g, b)
}

/** M3E icon/label lookup for pinnable navigation tabs. */
internal fun m3eScreenIconAndLabel(screen: Screen): Pair<ImageVector, String> = when (screen) {
    Screen.HOME -> Icons.Rounded.Home to "Home"
    Screen.ATTENDANCE -> Icons.Rounded.EventAvailable to "Attendance"
    Screen.ACADEMICS -> Icons.Rounded.School to "Academics"
    Screen.PAYMENTS -> Icons.Rounded.CreditCard to "Payments"
    Screen.LIBRARIES -> Icons.AutoMirrored.Rounded.LibraryBooks to "Library"
    Screen.HOSTEL -> Icons.Rounded.Apartment to "Hostel"
    Screen.CABSHARE -> Icons.Rounded.DirectionsCar to "Cabshare"
    Screen.TRANSPORT -> Icons.Rounded.DirectionsBus to "Transport"
    Screen.MORE -> Icons.Rounded.Apps to "More"
    Screen.PROFILE -> Icons.Rounded.Person to "Profile"
    Screen.EVENTS -> Icons.Rounded.Event to "Events"
    Screen.QBANK -> Icons.Rounded.Topic to "QBank"
    Screen.SOCIAL -> Icons.Rounded.People to "Social"
    Screen.FFCS_PLANNER -> Icons.Rounded.ViewTimeline to "FFCS"
    Screen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom to "Classes"
    Screen.CALENDAR -> Icons.Rounded.CalendarMonth to "Calendar"
    else -> Icons.Rounded.Circle to "Unknown"
}

@Preview
@Composable
private fun M3EOnboardingComponentsPreview() {
    AmazeM3ETheme(isDark = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            M3EOnboardingSectionLabel("Section label")
            M3EOnboardingChoiceChip(label = "Selected", selected = true, onClick = {})
            M3EOnboardingChoiceChip(label = "Unselected", selected = false, onClick = {})
        }
    }
}
