package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.DashboardWidget
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.ui.screens.academics.AddTaskDialog
import com.amazecc.app.shared.ui.strings.Strings
import io.ktor.util.decodeBase64Bytes
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.utils.toImageBitmap
import com.amazecc.app.shared.utils.WidgetDataUtils
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.sessionDisplay
import com.amazecc.app.shared.utils.seatLocationDisplay
import com.amazecc.app.shared.utils.examDateParsed
import com.amazecc.app.shared.model.displayCgpa
import com.amazecc.app.shared.model.displayCreditsEarned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private class DragState {
    var draggedIndex by mutableStateOf(-1)
    var dragOffset by mutableFloatStateOf(0f)
    var itemHeights = mutableMapOf<Int, Float>()
}

@Composable
private fun rememberDragState(): DragState = remember { DragState() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WidgetDashboard(
    updateDialog: @Composable () -> Unit
) {
    val colors = AmazeTheme.colors
    val widgetOrder by AppState.widgetOrder.collectAsState()
    val isEditing by AppState.isDashboardEditMode.collectAsState()
    val dragState = rememberDragState()
    var showManageDialog by remember { mutableStateOf(false) }

    updateDialog()

    if (showManageDialog) {
        ManageWidgetsDialog(onDismiss = { showManageDialog = false })
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AmazeTheme.spacing.pageHorizontal,
                end = AmazeTheme.spacing.pageHorizontal,
                bottom = BOTTOM_NAV_PADDING + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sectionGap),
            userScrollEnabled = dragState.draggedIndex == -1
        ) {
            item(key = "status_bar_spacer") {
                Spacer(Modifier.statusBarsPadding().height(4.dp))
            }
            item(key = "edit_header") {
                if (isEditing) {
                    EditModeHeader(colors, onOpenManage = { showManageDialog = true })
                }
            }

            itemsIndexed(
                items = widgetOrder,
                key = { _, w -> w }
            ) { index, widget ->
                val isDragging = dragState.draggedIndex == index
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .then(
                            if (isDragging) Modifier
                                .graphicsLayer {
                                    translationY = dragState.dragOffset
                                    scaleX = 1.04f
                                    scaleY = 1.04f
                                    shadowElevation = 12f
                                }
                            else Modifier
                        )
                        .animateItem()
                ) {
                    WidgetWrapper(
                        widget = widget,
                        index = index,
                        isEditing = isEditing,
                        isDragging = isDragging,
                        dragState = dragState,
                        totalWidgets = widgetOrder.size
                    )
                }
            }

            if (!isEditing) {
                item(key = "edit_trigger") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                                .clickable { AppState.toggleDashboardEditMode() }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Edit, "Quick Reorder", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Quick Edit",
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                .background(colors.accentSurface.copy(alpha = 0.25f))
                                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.medium))
                                .clickable { showManageDialog = true }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Tune, "Manage & Toggle Widgets", tint = colors.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Manage Widgets",
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "add_widgets") {
                    HiddenWidgetsRow(colors, onOpenManage = { showManageDialog = true })
                }
            }

            item(key = "footer_spacer") {
                FooterSpacer()
            }
        }
    }
}

@Composable
private fun EditModeHeader(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onOpenManage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(AmazeTheme.radius.medium))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Edit, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Text(
            "Use \u25B2\u25BC or drag to reorder",
            style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onOpenManage,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("Manage All", color = colors.accent, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.success.copy(alpha = 0.15f))
                .clickable { AppState.toggleDashboardEditMode() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Done", color = colors.success, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HiddenWidgetsRow(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onOpenManage: () -> Unit
) {
    val widgetOrder by AppState.widgetOrder.collectAsState()
    val hiddenWidgets = remember(widgetOrder) {
        DashboardWidget.entries.filter { it !in widgetOrder }
    }
    if (hiddenWidgets.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Disabled / Hidden Widgets",
                style = AmazeTheme.typography.caption.copy(
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium
                )
            )
            TextButton(onClick = onOpenManage, modifier = Modifier.height(28.dp)) {
                Text("Manage", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hiddenWidgets, key = { it }) { widget ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .clickable { AppState.restoreWidget(widget) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, "Add widget back", tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            getWidgetTitle(widget),
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WidgetWrapper(
    widget: DashboardWidget,
    index: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    dragState: DragState,
    totalWidgets: Int
) {
    val colors = AmazeTheme.colors

    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.9f else if (isEditing) 0.95f else 1f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEditing) Modifier
                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(colors.accentSurface.copy(alpha = 0.1f))
                    .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.medium))
                    .padding(8.dp)
                else Modifier
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DragHandle(
                            index = index,
                            dragState = dragState
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            getWidgetTitle(widget),
                            style = AmazeTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { AppState.moveWidgetUp(widget) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                "Move Up",
                                tint = if (index > 0) colors.textPrimary else colors.textMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { AppState.moveWidgetDown(widget) },
                            enabled = index < totalWidgets - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                "Move Down",
                                tint = if (index < totalWidgets - 1) colors.textPrimary else colors.textMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { AppState.removeWidget(widget) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                "Disable widget",
                                tint = colors.danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = dragAlpha }
            ) {
                WidgetContent(widget)
            }
        }
    }
}

@Composable
private fun DragHandle(
    index: Int,
    dragState: DragState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Icon(
        imageVector = Icons.Rounded.DragHandle,
        contentDescription = "Drag to reorder",
        tint = AmazeTheme.colors.accent,
        modifier = modifier
            .clip(CircleShape)
            .background(AmazeTheme.colors.accentSurface)
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragState.draggedIndex = index
                        dragState.dragOffset = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragState.dragOffset += dragAmount.y
                        val currentIdx = dragState.draggedIndex
                        if (currentIdx < 0) return@detectDragGesturesAfterLongPress
                        if (dragState.dragOffset > 100f && currentIdx < DashboardWidget.entries.size - 1) {
                            AppState.reorderWidget(currentIdx, currentIdx + 1)
                            dragState.draggedIndex = currentIdx + 1
                            dragState.dragOffset = 0f
                        } else if (dragState.dragOffset < -100f && currentIdx > 0) {
                            AppState.reorderWidget(currentIdx, currentIdx - 1)
                            dragState.draggedIndex = currentIdx - 1
                            dragState.dragOffset = 0f
                        }
                    },
                    onDragEnd = {
                        dragState.draggedIndex = -1
                        dragState.dragOffset = 0f
                    },
                    onDragCancel = {
                        dragState.draggedIndex = -1
                        dragState.dragOffset = 0f
                    }
                )
            }
            .padding(6.dp)
            .size(20.dp)
    )
}

@Composable
private fun ManageWidgetsDialog(
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AmazeButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.height(36.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = { AppState.resetWidgetsToDefault() }) {
                Text("Reset Default", color = colors.textMuted, style = AmazeTheme.typography.caption)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Customize Dashboard",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Toggle widgets on/off and reorder them for your home screen.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                DashboardWidgetRows()
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(AmazeTheme.radius.large)
    )
}

@Composable
internal fun DashboardWidgetRows() {
    val colors = AmazeTheme.colors
    val widgetOrder by AppState.widgetOrder.collectAsState()

    QuickStatsCustomizationRows()

    Text(
        "Home Dashboard Widgets",
        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )

    DashboardWidget.entries.forEach { widget ->
        val isEnabled = widget in widgetOrder
        val index = widgetOrder.indexOf(widget)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) colors.accentSurface.copy(alpha = 0.2f) else colors.surface
            ),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { AppState.setWidgetEnabled(widget, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.border
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        getWidgetTitle(widget),
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) colors.textPrimary else colors.textMuted
                        )
                    )
                    Text(
                        getWidgetDescription(widget),
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isEnabled) {
                    Column {
                        IconButton(
                            onClick = { AppState.moveWidgetUp(widget) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                "Move Up",
                                tint = if (index > 0) colors.textPrimary else colors.textMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { AppState.moveWidgetDown(widget) },
                            enabled = index >= 0 && index < widgetOrder.lastIndex,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                "Move Down",
                                tint = if (index >= 0 && index < widgetOrder.lastIndex) colors.textPrimary else colors.textMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatsCustomizationRows() {
    val colors = AmazeTheme.colors
    val statsCardsOrder by AppState.statsCardsOrder.collectAsState()
    val enabledStatsCards by AppState.enabledStatsCards.collectAsState()

    val cardLabels = mapOf(
        "attendance" to ("Attendance" to "Displays overall attendance %"),
        "cgpa" to ("CGPA" to "Displays current cumulative GPA"),
        "credits" to ("Credits" to "Displays total credits earned (Links to Curriculum)"),
        "od" to ("OD Hours" to "Displays total On Duty hours")
    )

    Text(
        "Quick Stats Order & Toggles",
        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )

    statsCardsOrder.forEachIndexed { index, cardKey ->
        val isEnabled = enabledStatsCards.contains(cardKey)
        val info = cardLabels[cardKey] ?: (cardKey to "")

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) colors.accentSurface.copy(alpha = 0.2f) else colors.surface
            ),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { AppState.setStatCardEnabled(cardKey, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.border
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.first,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) colors.textPrimary else colors.textMuted,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    )
                    if (info.second.isNotEmpty()) {
                        Text(
                            info.second,
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                val mutable = statsCardsOrder.toMutableList()
                                val tmp = mutable[index]
                                mutable[index] = mutable[index - 1]
                                mutable[index - 1] = tmp
                                AppState.setStatsCardsOrder(mutable)
                            }
                        },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            null,
                            tint = if (index > 0) colors.accent else colors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index < statsCardsOrder.size - 1) {
                                val mutable = statsCardsOrder.toMutableList()
                                val tmp = mutable[index]
                                mutable[index] = mutable[index + 1]
                                mutable[index + 1] = tmp
                                AppState.setStatsCardsOrder(mutable)
                            }
                        },
                        enabled = index < statsCardsOrder.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            null,
                            tint = if (index < statsCardsOrder.size - 1) colors.accent else colors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun getWidgetTitle(widget: DashboardWidget): String = when (widget) {
    DashboardWidget.PROFILE_HEADER -> "Profile Header & Actions"
    DashboardWidget.METRIC_CARDS -> "Quick Stats (CGPA & Credits)"
    DashboardWidget.EXAM_AND_CLASS -> "Exam & Live Class Tracker"
    DashboardWidget.ATTENDANCE_BUNK -> "Attendance & Bunk Calculator"
    DashboardWidget.TODAYS_CLASSES -> "Today's Schedule & Live Tracker"
    DashboardWidget.COURSE_ATTENDANCE -> "Course Attendance Breakdown"
    DashboardWidget.QUICK_ACTIONS -> "Quick Action Shortcuts"
    DashboardWidget.FREE_CLASSROOMS -> "Free Classrooms Finder"
}

internal fun getWidgetDescription(widget: DashboardWidget): String = when (widget) {
    DashboardWidget.PROFILE_HEADER -> "Greeting, avatar, sync status & search"
    DashboardWidget.METRIC_CARDS -> "CGPA, earned credits & active ODs"
    DashboardWidget.EXAM_AND_CLASS -> "Shows today's exams, next exam within 24h, or live & next class"
    DashboardWidget.ATTENDANCE_BUNK -> "Overall percentage & bunk-o-meter"
    DashboardWidget.TODAYS_CLASSES -> "Timetable for today with countdowns"
    DashboardWidget.COURSE_ATTENDANCE -> "Per-course breakdown and predictor"
    DashboardWidget.QUICK_ACTIONS -> "Navigation shortcuts to key modules"
    DashboardWidget.FREE_CLASSROOMS -> "Available rooms on campus right now"
}

@Composable
private fun WidgetContent(widget: DashboardWidget) {
    when (widget) {
        DashboardWidget.PROFILE_HEADER -> ProfileHeaderWidget()
        DashboardWidget.METRIC_CARDS -> MetricCardsWidget()
        DashboardWidget.EXAM_AND_CLASS -> ExamAndClassWidget()
        DashboardWidget.ATTENDANCE_BUNK -> AttendanceBunkWidget()
        DashboardWidget.TODAYS_CLASSES -> TodayClassesWidget()
        DashboardWidget.COURSE_ATTENDANCE -> CourseAttendanceWidget()
        DashboardWidget.QUICK_ACTIONS -> QuickActionsWidget()
        DashboardWidget.FREE_CLASSROOMS -> FreeClassroomsWidget()
    }
}

// ── PROFILE HEADER WIDGET ──

@Composable
private fun ProfileHeaderWidget() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val profile by AppState.studentProfile.collectAsState()
    val profileImages by AppState.profileImages.collectAsState()
    val vtopPhotoBase64 by AppState.vtopPhotoBase64.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    val nameToDisplay = remember(profile, authorizedID) {
        val fullName = profile?.name ?: "Student"
        val first = fullName.split(" ").firstOrNull() ?: fullName
        if (first == authorizedID) "Student" else first
    }
    val avatarText = remember(profile) {
        val name = profile?.name ?: "?"
        name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }

    val rawBase64 = remember(profile, profileImages, vtopPhotoBase64) {
        profile?.photoBase64
            ?: profileImages?.student?.photoBase64
            ?: profileImages?.profile?.photoBase64
            ?: profileImages?.studentPhoto
            ?: vtopPhotoBase64
    }
    var decodedBitmap by remember(rawBase64) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(rawBase64) {
        if (rawBase64 != null) {
            withContext(Dispatchers.Default) {
                try {
                    val cleanBase64 = rawBase64.substringAfter("base64,")
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace(" ", "")
                    cleanBase64.decodeBase64Bytes().toImageBitmap()
                } catch (e: Exception) { null }
            }.let { decodedBitmap = it }
        } else {
            decodedBitmap = null
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.15f))
                .border(1.5.dp, colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val bmp = decodedBitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp,
                    contentDescription = "Profile Image",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = avatarText,
                    style = AmazeTheme.typography.subheading.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Good ${getGreeting()}",
                style = AmazeTheme.typography.caption.copy(
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
            )
            Text(
                text = nameToDisplay,
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {
                SyncEngine.setShowSyncDialog(true, minimized = true)
                AppState.loadAllData()
            },
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.border, CircleShape)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Sync All Data",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.border, CircleShape)
                .clickable { AppState.openCommandPalette() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = Strings.search,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── METRIC CARDS WIDGET ──

@Composable
private fun MetricCardsWidget() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    val isCgpaHidden by AppState.cgpaHidden.collectAsState()
    val isBusSubscriber by AppState.isBusSubscriber.collectAsState()

    val statsCardsOrder by AppState.statsCardsOrder.collectAsState()
    val enabledStatsCards by AppState.enabledStatsCards.collectAsState()

    val visibleCardKeys = remember(statsCardsOrder, enabledStatsCards) {
        statsCardsOrder.filter { enabledStatsCards.contains(it) }
    }

    val cgpa = remember(marksRes) { marksRes.displayCgpa }
    val cgpaDisplay = remember(cgpa, isCgpaHidden) {
        if (isCgpaHidden) "\u2022\u2022\u2022" else cgpa.toFixed(2)
    }
    val credits = remember(marksRes) { marksRes.displayCreditsEarned }
    val odCount = remember(courses) { WidgetDataUtils.computeODHours(courses) }

    val overallAttendance = remember(courses) {
        val validCourses = courses.filter { it.totalClasses > 0 }
        if (validCourses.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in validCourses) {
                totalAtt += item.attendedClasses
                totalCls += item.totalClasses
            }
            if (totalCls == 0) 0f else (totalAtt.toFloat() / totalCls.toFloat()) * 100f
        }
    }
    val targetPct = if (isBusSubscriber) 85f else 75f
    val attColor = when {
        overallAttendance >= targetPct -> colors.success
        overallAttendance >= 50f -> colors.warning
        else -> colors.danger
    }
    val attSurfaceBg = when {
        overallAttendance >= targetPct -> colors.successSurface
        overallAttendance >= 50f -> colors.warningSurface
        else -> colors.dangerSurface
    }

    val isScrollable = visibleCardKeys.size > 3
    val rowModifier = if (isScrollable) {
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }

    if (visibleCardKeys.isNotEmpty()) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
        ) {
            visibleCardKeys.forEach { key ->
                val cardModifier = if (isScrollable) Modifier.width(120.dp) else Modifier.weight(1f)
                when (key) {
                    "attendance" -> GlassMetricCard(
                        title = "Attendance",
                        value = if (courses.isNotEmpty()) "${overallAttendance.toInt()}%" else "—",
                        icon = Icons.Rounded.CheckCircle,
                        colors = colors,
                        modifier = cardModifier,
                        iconTint = attColor,
                        surfaceBg = attSurfaceBg,
                        onClick = { AppState.openAttendanceView("Predictor") }
                    )
                    "cgpa" -> GlassMetricCard(
                        title = "CGPA",
                        value = cgpaDisplay,
                        icon = if (isCgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Star,
                        colors = colors,
                        modifier = cardModifier,
                        iconTint = if (isCgpaHidden) colors.textMuted else colors.warning,
                        surfaceBg = colors.warningSurface,
                        onClick = { AppState.setCgpaHidden(!isCgpaHidden) }
                    )
                    "credits" -> GlassMetricCard(
                        title = "Credits",
                        value = credits.toInt().toString(),
                        icon = Icons.Rounded.Info,
                        colors = colors,
                        modifier = cardModifier,
                        iconTint = colors.info,
                        surfaceBg = colors.infoSurface,
                        onClick = { AppState.navigateTo(Screen.CURRICULUM) }
                    )
                    "od" -> GlassMetricCard(
                        title = "OD Hours",
                        value = if (courses.isNotEmpty()) "${odCount}h" else "—",
                        icon = Icons.Rounded.CheckCircle,
                        colors = colors,
                        modifier = cardModifier,
                        iconTint = colors.success,
                        surfaceBg = colors.successSurface,
                        onClick = { AppState.navigateTo(Screen.OD_TRACKER) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier,
    iconTint: Color = colors.accent,
    surfaceBg: Color = colors.accentSurface,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(AmazeTheme.radius.medium), clip = false)
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(colors.surface)
            .background(surfaceBg.copy(alpha = 0.20f))
            .border(
                1.5.dp,
                iconTint.copy(alpha = 0.45f),
                RoundedCornerShape(AmazeTheme.radius.medium)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null
            ) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Left corner icon indicator & category label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(surfaceBg.copy(alpha = 0.55f))
                        .border(1.dp, iconTint.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title.uppercase(),
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Emphasized Large Metric Value (Left Aligned)
            Text(
                text = value,
                style = AmazeTheme.typography.heading.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    fontSize = 22.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── ATTENDANCE & BUNK-O-METER WIDGET ──

@Composable
private fun AttendanceBunkWidget() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    val overallAttendance = remember(courses) {
        val validCourses = courses.filter { it.totalClasses > 0 }
        if (validCourses.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in validCourses) {
                totalAtt += item.attendedClasses
                totalCls += item.totalClasses
            }
            if (totalCls == 0) 0f else (totalAtt.toFloat() / totalCls.toFloat()) * 100f
        }
    }
    val animatedAttendance by animateFloatAsState(
        targetValue = overallAttendance / 100f,
        animationSpec = tween(1500)
    )
    val attColor = when {
        overallAttendance >= 75f -> colors.success
        overallAttendance >= 50f -> colors.warning
        else -> colors.danger
    }
    val attLabel = when {
        overallAttendance >= 75f -> "You're on track!"
        overallAttendance >= 50f -> "Needs improvement!"
        else -> "Critical!"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(colors.surface)
            .border(1.dp, colors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.large))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AmazeTheme.spacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(88.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { animatedAttendance },
                        modifier = Modifier.fillMaxSize(),
                        color = attColor,
                        trackColor = colors.border,
                        strokeWidth = 8.dp
                    )
                    Text(
                        text = "${overallAttendance.toInt()}%",
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = AmazeTheme.fontSize.lg
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Overall Attendance",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                    Text(
                        attLabel,
                        style = AmazeTheme.typography.caption.copy(
                            color = attColor,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    AmazeButton(
                        text = "Predict Attendance",
                        onClick = { AppState.openAttendanceView("Predictor") },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
            BunkOMeterCard(
                attendance = attendanceRes,
                modifier = Modifier.fillMaxWidth(),
                isInnerCard = true
            )
        }
    }
}

// ── TODAY'S CLASSES WIDGET ──

private data class DashboardClassEvent(
    val slots: List<String>,
    val startMins: Int,
    val endMins: Int,
    val course: AttendanceItem
)

// ── EXAM & LIVE CLASS WIDGET ──
// Shows today's exams from when the last class ends until the exam ends;
// otherwise falls back to the live / next class tracker.

@Composable
private fun ExamAndClassWidget() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    val todayAbbrev = remember(calendarRes) {
        com.amazecc.app.shared.utils.AttendanceTimetable.getTodayAttendanceDay(calendarRes).name
    }

    val todayClasses = remember(courses, todayAbbrev) {
        val dayMap = SlotMap.map[todayAbbrev] ?: emptyMap()
        val dayClasses = mutableListOf<DashboardClassEvent>()
        courses.forEach { course ->
            val slots = course.slotName.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            slots.forEach { slot ->
                val timeStr = dayMap[slot]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = com.amazecc.app.shared.utils.TimeMath.toMinutes(parts[0])
                        val end = com.amazecc.app.shared.utils.TimeMath.toMinutes(parts[1])
                        dayClasses.add(DashboardClassEvent(listOf(slot), start, end, course))
                    }
                }
            }
        }
        dayClasses.sortBy { it.startMins }
        dayClasses
    }

    var currentMins by remember {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        mutableStateOf(now.hour * 60 + now.minute)
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000L)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            currentMins = now.hour * 60 + now.minute
        }
    }

    val todayDate = remember(currentMins) {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val allExams = rememberSelectedSemesterExams()
    val todayExams = remember(allExams, todayDate) {
        allExams
            .filter { it.examDateParsed == todayDate }
            .sortedBy { ExamUtils.examStartMinutes(it) ?: 0 }
    }

    val tasks by AppState.tasks.collectAsState()
    val todayQuizTasks = remember(tasks, todayDate) {
        tasks
            .filter { !it.completed && (it.type == "quiz" || it.type == "exam") && it.showOnCalendar }
            .filter { try { it.dueDate == todayDate.toString() } catch (_: Exception) { false } }
            .sortedBy { it.dueTime }
    }

    val nextExam = remember(allExams, currentMins) {
        ExamUtils.nextExamWithin(allExams, withinHours = 24)
    }

    // Exams today that have not finished yet (unknown end time keeps them visible).
    val activeTodayExams = remember(todayExams, currentMins) {
        val now = Clock.System.now()
        todayExams.filter { exam ->
            val end = ExamUtils.examEndInstant(exam) ?: return@filter true
            now < end
        }
    }

    if (todayExams.isNotEmpty() || nextExam != null || todayQuizTasks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (todayExams.isNotEmpty()) {
                if (activeTodayExams.isNotEmpty() || nextExam == null) {
                    ExamTimeWidget(todayExams, colors)
                } else {
                    UpcomingExamWidget(nextExam, colors)
                }
            } else if (nextExam != null) {
                UpcomingExamWidget(nextExam, colors)
            }
            if (todayQuizTasks.isNotEmpty()) {
                TodayQuizTasksWidget(todayQuizTasks, colors)
            }
        }
    } else {
        LiveNextClassContent(todayClasses, currentMins, colors)
    }
}

/** Compact card for the next exam within the next 24 hours (when there is no exam today). */
@Composable
private fun UpcomingExamWidget(
    exam: com.amazecc.app.shared.model.ExamItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val now = Clock.System.now()
    val start = ExamUtils.examStartInstant(exam)
    val minutes = start?.let { ((it - now).inWholeMilliseconds / 60_000L).toInt() } ?: 0
    val label = when {
        minutes >= 60 -> "In ${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "In ${minutes}m"
        else -> "Today"
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.navigateTo(Screen.EXAM_SCHEDULE) },
        variant = CardVariant.ACCENT_SURFACE,
        accentStrip = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.chart1.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.EventSeat, null, tint = colors.chart1)
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Next Exam",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.chart1,
                            fontWeight = FontWeight.Black,
                            fontSize = AmazeTheme.fontSize.xs
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = exam.courseCode,
                        style = AmazeTheme.typography.smallLabel.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
                Text(
                    text = exam.courseTitle,
                    style = AmazeTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(exam.examDate)
                        if (exam.examTime.isNotBlank()) append(" · ").append(exam.examTime)
                        if (exam.venue.isNotBlank()) append(" · ").append(exam.venue)
                    },
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                    .background(colors.chart1.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.chart1,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.micro
                    )
                )
            }
        }
    }
}

@Composable
private fun TodayQuizTasksWidget(
    tasks: List<com.amazecc.app.shared.model.HomeworkTask>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.navigateTo(Screen.TASKS) },
        variant = CardVariant.ACCENT_SURFACE,
        accentStrip = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Assignment, null, tint = colors.chart1, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Today's Quiz / Exam Tasks",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(Modifier.weight(1f))
                AmazeBadge(text = "${tasks.size} task${if (tasks.size != 1) "s" else ""} due", variant = BadgeVariant.INFO)
            }

            tasks.forEach { task ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.chart1.copy(alpha = 0.07f))
                        .border(1.dp, colors.chart1.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.small))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = task.courseCode,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = colors.chart1,
                                        fontWeight = FontWeight.Black,
                                        fontSize = AmazeTheme.fontSize.xs
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(colors.chart1.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (task.type == "quiz") "QUIZ" else "EXAM",
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.chart1,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        )
                                    )
                                }
                            }
                            Text(
                                text = task.title,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = task.dueTime,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.chart1)
                            )
                            Text(
                                text = "DUE",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamTimeWidget(
    exams: List<com.amazecc.app.shared.model.ExamItem>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val now = Clock.System.now()

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.navigateTo(Screen.EXAM_SCHEDULE) },
        variant = CardVariant.ACCENT_SURFACE,
        accentStrip = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EventSeat, null, tint = colors.chart1, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Exam Time",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(Modifier.weight(1f))
                AmazeBadge(text = "${exams.size} exam${if (exams.size != 1) "s" else ""} today", variant = BadgeVariant.INFO)
            }

            exams.forEach { exam ->
                val status = examWidgetStatus(exam, now, colors)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(status.color.copy(alpha = 0.07f))
                        .border(1.dp, status.color.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.small))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = exam.courseCode,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = status.color,
                                        fontWeight = FontWeight.Black,
                                        fontSize = AmazeTheme.fontSize.xs
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(status.color.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = status.label,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = status.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        )
                                    )
                                }
                            }
                            Text(
                                text = exam.courseTitle,
                                style = AmazeTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(exam.examTime.ifBlank { exam.reportingTime.ifBlank { "Time TBD" } })
                                    if (exam.venue.isNotBlank()) append(" · ").append(exam.venue)
                                    if (exam.sessionDisplay != "TBD") append(" · ").append(exam.sessionDisplay)
                                    append(" · Seat ").append(exam.seatLocationDisplay)
                                    if (exam.seatNo.isNotBlank()) append(" #").append(exam.seatNo)
                                },
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.ChevronRight,
                            null,
                            tint = status.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class ExamWidgetStatus(val label: String, val color: Color)

private fun examWidgetStatus(
    exam: com.amazecc.app.shared.model.ExamItem,
    now: kotlinx.datetime.Instant,
    colors: com.amazecc.app.shared.theme.AmazeColors
): ExamWidgetStatus {
    val start = ExamUtils.examStartInstant(exam) ?: return ExamWidgetStatus("TODAY", colors.chart1)
    val end = ExamUtils.examEndInstant(exam)

    return when {
        end != null && now >= end -> ExamWidgetStatus("DONE", colors.success)
        now < start -> {
            val minutes = ((start - now).inWholeMilliseconds / 60_000L).toInt()
            val label = if (minutes >= 60) "In ${minutes / 60}h ${minutes % 60}m" else "In ${minutes}m"
            val color = when {
                minutes <= 60 -> colors.danger
                minutes <= 180 -> colors.warning
                else -> colors.chart1
            }
            ExamWidgetStatus(label, color)
        }
        else -> ExamWidgetStatus("LIVE", colors.accent)
    }
}

@Composable
private fun LiveNextClassContent(
    todayClasses: List<DashboardClassEvent>,
    currentMins: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val currentClass = todayClasses.firstOrNull { it.startMins <= currentMins && it.endMins >= currentMins }
    val nextClass = todayClasses.firstOrNull { it.startMins > currentMins }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Live & Next Class",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
                if (currentClass != null) {
                    AmazeBadge(text = "LIVE", variant = BadgeVariant.SUCCESS)
                } else if (nextClass != null) {
                    AmazeBadge(text = "UPCOMING", variant = BadgeVariant.INFO)
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))

            if (todayClasses.isEmpty()) {
                Text(
                    "☕ No classes scheduled for today!",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else if (currentClass == null && nextClass == null) {
                Text(
                    "🎉 All classes done for today!",
                    style = AmazeTheme.typography.caption.copy(color = colors.success, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentClass != null) {
                        val diff = currentClass.endMins - currentMins
                        val timeStr = if (diff >= 60) "${diff / 60}h ${diff % 60}m left" else "${diff}m left"
                        val venue = currentClass.course.slotVenue?.takeIf { it.isNotBlank() } ?: "N/A"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(colors.success.copy(alpha = 0.12f))
                                .border(1.dp, colors.success.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "CURRENT • ${currentClass.course.courseCode}",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = colors.success,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AmazeTheme.fontSize.micro
                                    )
                                )
                                Text(
                                    currentClass.course.courseTitle,
                                    style = AmazeTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "📍 $venue • Slot ${currentClass.slots.joinToString("+")}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            AmazeBadge(text = timeStr, variant = BadgeVariant.SUCCESS)
                        }
                    }

                    if (nextClass != null) {
                        val diff = nextClass.startMins - currentMins
                        val timeStr = if (diff >= 60) "In ${diff / 60}h ${diff % 60}m" else "In ${diff}m"
                        val venue = nextClass.course.slotVenue?.takeIf { it.isNotBlank() } ?: "N/A"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(colors.accentSurface.copy(alpha = 0.2f))
                                .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "NEXT • ${nextClass.course.courseCode}",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AmazeTheme.fontSize.micro
                                    )
                                )
                                Text(
                                    nextClass.course.courseTitle,
                                    style = AmazeTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "📍 $venue • Slot ${nextClass.slots.joinToString("+")}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            AmazeBadge(text = timeStr, variant = BadgeVariant.INFO)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayClassesWidget() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    val todayAbbrev = remember(calendarRes) {
        com.amazecc.app.shared.utils.AttendanceTimetable.getTodayAttendanceDay(calendarRes).name
    }

    val todayClasses = remember(courses, todayAbbrev) {
        val dayMap = SlotMap.map[todayAbbrev] ?: emptyMap()
        val dayClasses = mutableListOf<DashboardClassEvent>()
        courses.forEach { course ->
            val slots = course.slotName.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            slots.forEach { slot ->
                val timeStr = dayMap[slot]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = com.amazecc.app.shared.utils.TimeMath.toMinutes(parts[0])
                        val end = com.amazecc.app.shared.utils.TimeMath.toMinutes(parts[1])
                        dayClasses.add(DashboardClassEvent(listOf(slot), start, end, course))
                    }
                }
            }
        }
        dayClasses.sortBy { it.startMins }
        val merged = mutableListOf<DashboardClassEvent>()
        dayClasses.forEach { item ->
            if (merged.isEmpty()) {
                merged.add(item)
            } else {
                val last = merged.last()
                if (last.course.courseCode == item.course.courseCode && kotlin.math.abs(last.endMins - item.startMins) <= 10) {
                    merged[merged.size - 1] = last.copy(
                        endMins = kotlin.math.max(last.endMins, item.endMins),
                        slots = last.slots + item.slots
                    )
                } else {
                    merged.add(item)
                }
            }
        }
        merged
    }

    var currentMins by remember { 
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        mutableStateOf(now.hour * 60 + now.minute) 
    }
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(60000L)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            currentMins = now.hour * 60 + now.minute
        }
    }

    // FFCS registration alert — takes over the status banner when the slot is within 24h
    val regInfo by AppState.ffcsRegistration.collectAsState()
    val regAlert = remember(regInfo, currentMins) {
        val info = regInfo
        if (info == null) {
            null
        } else {
            val start = com.amazecc.app.shared.utils.NotificationsUtils.registrationStartInstant(info)
                ?: return@remember null
            val remaining = start - Clock.System.now()
            if (remaining > 24.hours || remaining < -2.hours) {
                null
            } else {
                Triple(info, start, remaining)
            }
        }
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.School, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Today's Classes",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
                if (todayClasses.isNotEmpty()) {
                    AmazeBadge(
                        text = "${todayClasses.size}",
                        variant = BadgeVariant.INFO
                    )
                }
            }
            
            val currentClass = todayClasses.firstOrNull { it.startMins <= currentMins && it.endMins >= currentMins }
            val nextClass = todayClasses.firstOrNull { it.startMins > currentMins }
            
            val statusText = when {
                currentClass != null -> {
                    val diff = currentClass.endMins - currentMins
                    "Ongoing: Ends in ${if(diff >= 60) "${diff/60}h ${diff%60}m" else "$diff mins"}"
                }
                nextClass != null -> {
                    val diff = nextClass.startMins - currentMins
                    "Next class in ${if(diff >= 60) "${diff/60}h ${diff%60}m" else "$diff mins"}"
                }
                todayClasses.isNotEmpty() && currentMins > todayClasses.last().endMins -> "All classes done for today!"
                else -> null
            }
            val statusColor = if (currentClass != null) colors.success else colors.info

            if (regAlert != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.HowToReg,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "FFCS Registration ${regAlert.first.date}",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val slot = buildString {
                            if (regAlert.first.fromTime.isNotBlank()) append(regAlert.first.fromTime)
                            if (regAlert.first.toTime.isNotBlank()) {
                                if (isNotEmpty()) append(" - ")
                                append(regAlert.first.toTime)
                            }
                        }
                        if (slot.isNotBlank()) {
                            Text(
                                slot,
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        regCountdownText(regAlert.third),
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else if (statusText != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))

            if (todayClasses.isEmpty()) {
                Text(
                    "\u2615 No classes today!",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                todayClasses.forEach { cls ->
                    val isHappening = currentMins in cls.startMins..cls.endMins
                    val isPast = currentMins > cls.endMins
                    val cardAlpha = if (isPast) 0.5f else 1f
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.surface)
                            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
                            .graphicsLayer { alpha = cardAlpha },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isHappening) colors.success else colors.accent)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        cls.course.courseCode,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.textMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    val venue = cls.course.slotVenue?.takeIf { it.isNotBlank() }
                                    if (venue != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                .background(colors.accent.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MeetingRoom,
                                                    contentDescription = null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    venue,
                                                    style = AmazeTheme.typography.smallLabel.copy(
                                                        color = colors.accent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AmazeTheme.fontSize.micro
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    cls.course.courseTitle,
                                    style = AmazeTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (cls.slots.isNotEmpty()) {
                                AmazeBadge(text = cls.slots.joinToString("+"), variant = BadgeVariant.INFO)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private data class CourseStats(val total: Int, val safe: Int, val warn: Int, val crit: Int, val avgPct: Double)

// ── COURSE ATTENDANCE WIDGET ──

@Composable
private fun CourseAttendanceWidget() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    val stats by remember(allSemesterAttendance, courses) {
        val allCourses = allSemesterAttendance.values
            .filterNotNull()
            .flatMap { it.attendance.orEmpty() } + courses
        var safe = 0; var warn = 0; var crit = 0
        var totalP = 0; var totalT = 0
        for (c in allCourses) {
            val t = c.totalClasses
            if (t > 0) {
                val p = c.attendedClasses.toDouble() / t
                when {
                    p >= 0.75 -> safe++
                    p >= 0.5 -> warn++
                    else -> crit++
                }
                totalP += c.attendedClasses
                totalT += t
            }
        }
        val avg = if (totalT > 0) totalP.toDouble() / totalT * 100 else 0.0
        mutableStateOf(CourseStats(allCourses.size, safe, warn, crit, avg))
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Course Attendance",
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                    if (stats.total > 0) {
                    Spacer(Modifier.width(6.dp))
                    AmazeBadge(text = "${stats.total} courses", variant = BadgeVariant.INFO)
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(value = stats.safe.toString(), label = "Safe (≥75%)", color = colors.chart1, modifier = Modifier.weight(1f))
                    StatChip(value = stats.warn.toString(), label = "Warning (50-74%)", color = colors.chart3, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(value = stats.crit.toString(), label = "Critical (<50%)", color = colors.chart5, modifier = Modifier.weight(1f))
                    StatChip(value = stats.avgPct.toFixed(1, "%"), label = "Overall Avg", color = colors.chart2, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            if (stats.total == 0) {
                Text(
                    "No course data available.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val coursesList = attendanceRes?.attendance ?: emptyList()
                val displayCourses = remember(coursesList) { coursesList.take(4) }
                displayCourses.forEach { course ->
                    ModernCourseCardWidget(
                        course = course,
                        onClick = { AppState.openCourseDetail(course.courseCode) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (stats.total > 4) {
                    Spacer(Modifier.height(4.dp))
                    AmazeButton(
                        text = "View All ${stats.total} Courses",
                        variant = ButtonVariant.SECONDARY,
                        onClick = { AppState.navigateTo(Screen.COURSE_DASHBOARD) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            label,
            style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModernCourseCardWidget(
    course: AttendanceItem,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val attended = course.attendedClasses
    val total = course.totalClasses
    val pct = if (total > 0) attended.toDouble() / total * 100 else 0.0
    val pctColor = when {
        pct >= 85 -> colors.success
        pct >= 75 -> colors.chart1
        else -> colors.danger
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                course.courseCode,
                style = AmazeTheme.typography.smallLabel.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                course.courseTitle,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted,
                    fontSize = AmazeTheme.fontSize.xs
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$attended/$total",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted,
                    fontSize = AmazeTheme.fontSize.xs
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(pctColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    pct.toFixed(0),
                    style = AmazeTheme.typography.smallLabel.copy(
                        fontWeight = FontWeight.Bold,
                        color = pctColor,
                        fontSize = AmazeTheme.fontSize.xs
                    )
                )
            }
        }
    }
}

// ── QUICK ACTIONS WIDGET ──

@Composable
private fun QuickActionsWidget() {
    val colors = AmazeTheme.colors
    val showAddTaskDialog = remember { mutableStateOf(false) }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Quick Actions",
                style = AmazeTheme.typography.body.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassActionCardWidget("Predict Att.", Icons.Rounded.TrendingUp, colors.chart1) {
                    AppState.navigateTo(Screen.COURSE_ATTENDANCE)
                }
                GlassActionCardWidget("GPA Calc", Icons.Rounded.Calculate, colors.chart2) {
                    AppState.navigateTo(Screen.GRADES)
                }
                GlassActionCardWidget("Quick Task", Icons.Rounded.AddTask, colors.chart3) {
                    showAddTaskDialog.value = true
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassActionCardWidget("Bus Routes", Icons.Rounded.DirectionsBus, colors.chart4) {
                    AppState.navigateTo(Screen.TRANSPORT)
                }
                GlassActionCardWidget("Wishlist", Icons.Rounded.FavoriteBorder, colors.chart5) {
                    AppState.navigateTo(Screen.WISHLIST)
                }
                GlassActionCardWidget("Curriculum", Icons.Rounded.MenuBook, colors.chart1) {
                    AppState.navigateTo(Screen.CURRICULUM)
                }
            }
            if (showAddTaskDialog.value) {
                AddTaskDialog(onDismiss = { showAddTaskDialog.value = false })
            }
        }
    }
}

@Composable
private fun GlassActionCardWidget(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    Box(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.small))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textSecondary,
                    fontSize = AmazeTheme.fontSize.micro,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── FREE CLASSROOMS WIDGET ──

@Composable
private fun FreeClassroomsWidget() {
    val colors = AmazeTheme.colors

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.navigateTo(Screen.FREE_CLASSROOMS) },
        variant = CardVariant.ACCENT_SURFACE,
        accentStrip = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.accentSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MeetingRoom, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AmazeTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Find Free Classrooms",
                    style = AmazeTheme.typography.subheading.copy(
                        fontSize = AmazeTheme.fontSize.lg,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Locate an empty spot to sit and study.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── UTILITY ──

private fun getGreeting(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return when {
        now.hour < 12 -> "Morning"
        now.hour < 17 -> "Afternoon"
        else -> "Evening"
    }
}

private fun regCountdownText(remaining: kotlin.time.Duration): String {
    val totalMin = remaining.inWholeMinutes
    return if (totalMin < 0) {
        "Started ${formatCountdownParts(-totalMin)} ago"
    } else {
        "Starts in ${formatCountdownParts(totalMin)}"
    }
}

private fun formatCountdownParts(totalMin: Long): String {
    val days = totalMin / 1440
    val hours = (totalMin % 1440) / 60
    val mins = totalMin % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
