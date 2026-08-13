package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Read-only tappable field used to open a date/time picker.
 */
@Composable
fun PickerField(
    value: String,
    label: String,
    colors: AmazeColors,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.CalendarMonth,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background.copy(alpha = 0.3f))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = AmazeTheme.fontSize.micro,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value.ifBlank { "Not set" },
                    fontSize = AmazeTheme.fontSize.sm,
                    color = if (value.isBlank()) colors.textMuted else colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.Edit, null, tint = colors.textMuted.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

private fun LocalDate.toMillis(): Long = toEpochDays() * 86_400_000L

private fun Long.toLocalDate(): LocalDate = LocalDate.fromEpochDays((this / 86_400_000L).toInt())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    title: String,
    initial: LocalDate?,
    colors: AmazeColors,
    onSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.toMillis())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.Rounded.CalendarMonth,
                title = title,
                subtitle = "Pick a date",
                colors = colors,
                onClose = onDismiss
            )
            Spacer(Modifier.height(12.dp))
            DatePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                colors = DatePickerDefaults.colors(
                    containerColor = colors.background.copy(alpha = 0.4f),
                    titleContentColor = colors.textPrimary,
                    headlineContentColor = colors.accent,
                    weekdayContentColor = colors.textMuted,
                    dayContentColor = colors.textPrimary,
                    disabledDayContentColor = colors.textMuted.copy(alpha = 0.35f),
                    selectedDayContainerColor = colors.accent,
                    selectedDayContentColor = colors.background,
                    todayContentColor = colors.accent,
                    todayDateBorderColor = colors.accent,
                    navigationContentColor = colors.accent
                )
            )
            Spacer(Modifier.height(16.dp))
            AmazeButton(
                text = "Choose Date",
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onSelected(millis.toLocalDate()) else onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    title: String,
    initial: LocalTime?,
    colors: AmazeColors,
    onSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 18,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.Rounded.Schedule,
                title = title,
                subtitle = "Pick a time",
                colors = colors,
                onClose = onDismiss
            )
            Spacer(Modifier.height(8.dp))
            TimePicker(
                state = state,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            AmazeButton(
                text = "Choose Time",
                onClick = { onSelected(LocalTime(state.hour, state.minute)) },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            )
        }
    }
}

/**
 * Two-step reminder picker: date first, then time. Combines into a "YYYY-MM-DD HH:mm" style pair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerSheet(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    colors: AmazeColors,
    onSelected: (LocalDate, LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var pickedDate by remember { mutableStateOf(initialDate) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDate?.toMillis())
    val timeState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 18,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = true
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.Rounded.Schedule,
                title = "Reminder",
                subtitle = if (step == 0) "Step 1 of 2 — pick the date" else "Step 2 of 2 — pick the time",
                colors = colors,
                onClose = onDismiss
            )
            Spacer(Modifier.height(12.dp))
            if (step == 0) {
                DatePicker(
                    state = dateState,
                    modifier = Modifier.fillMaxWidth(),
                    colors = DatePickerDefaults.colors(
                        containerColor = colors.background.copy(alpha = 0.4f),
                        titleContentColor = colors.textPrimary,
                        headlineContentColor = colors.accent,
                        weekdayContentColor = colors.textMuted,
                        dayContentColor = colors.textPrimary,
                        disabledDayContentColor = colors.textMuted.copy(alpha = 0.35f),
                        selectedDayContainerColor = colors.accent,
                        selectedDayContentColor = colors.background,
                        todayContentColor = colors.accent,
                        todayDateBorderColor = colors.accent,
                        navigationContentColor = colors.accent
                    )
                )
                Spacer(Modifier.height(16.dp))
                AmazeButton(
                    text = "Next",
                    onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            pickedDate = millis.toLocalDate()
                            step = 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
            } else {
                TimePicker(
                    state = timeState,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                AmazeButton(
                    text = "Done",
                    onClick = {
                        val date = pickedDate ?: LocalDate.fromEpochDays(0)
                        onSelected(date, LocalTime(timeState.hour, timeState.minute))
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
            }
        }
    }
}

@Composable
internal fun SheetHeaderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: AmazeColors,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    subtitle,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted)
        }
    }
}
