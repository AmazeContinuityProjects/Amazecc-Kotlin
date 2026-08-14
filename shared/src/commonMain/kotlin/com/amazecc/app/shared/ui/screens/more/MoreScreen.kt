package com.amazecc.app.shared.ui.screens.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var currentPanel by remember { mutableStateOf(LibraryPanel.PRIMARY) }

    val pinnedNavTabs by AppState.pinnedNavTabs.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val semIds = semesterMap.keys.toList().sortedDescending()

    val onDismissSheet: () -> Unit = {
        AppState.closeAppLibrary()
    }

    // All searchable items — single shared source (also consumed by the global palette)
    val allSearchableItems = remember { appLibraryItems }

    val primaryStudyItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Study" } }
    val primaryCampusItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Campus" } }
    val primaryToolsItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Tools" } }
    val primaryAccountItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Account" } }

    val academicsSubItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Academics" } }
    val hostelSubItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Hostel" } }

    val scope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableStateOf(0f) }
    // Offset of the sheet from its open position, in pixels. 0 = fully open,
    // sheetHeightPx = fully hidden below the screen.
    val sheetOffsetPx = remember { Animatable(0f) }

    AppBackHandler(enabled = true) {
        onDismissSheet()
    }

    // Enter animation: slide up from below the screen once the sheet is measured.
    LaunchedEffect(sheetHeightPx) {
        if (sheetHeightPx > 0f && !sheetOffsetPx.isRunning) {
            sheetOffsetPx.snapTo(sheetHeightPx)
            sheetOffsetPx.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim — fades in with the sheet, tap anywhere outside to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - sheetOffsetPx.value / sheetHeightPx.coerceAtLeast(1f)) * 0.5f))
                .pointerInput(Unit) { detectTapGestures { onDismissSheet() } }
        )

        // Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .offset { IntOffset(0, sheetOffsetPx.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.background)
                .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                sheetOffsetPx.snapTo(
                                    (sheetOffsetPx.value + dragAmount)
                                        .coerceIn(0f, sheetHeightPx.coerceAtLeast(sheetOffsetPx.value))
                                )
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val threshold = sheetHeightPx * 0.45f
                                if (sheetOffsetPx.value > threshold) {
                                    sheetOffsetPx.animateTo(sheetHeightPx, tween(240, easing = FastOutSlowInEasing))
                                    onDismissSheet()
                                } else {
                                    sheetOffsetPx.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { sheetOffsetPx.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.textMuted.copy(alpha = 0.4f))
                    )
                }

                // Header Section: Title + New-Line Semester Dropdown + Search
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Line 1: Header Title & Sub-Panel Back Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPanel != LibraryPanel.PRIMARY) {
                            IconButton(
                                onClick = { currentPanel = LibraryPanel.PRIMARY },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Primary", tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (currentPanel) {
                                    LibraryPanel.PRIMARY -> "App Library"
                                    LibraryPanel.ACADEMICS -> "Academics Hub"
                                    LibraryPanel.HOSTEL -> "Hostel Hub"
                                },
                                style = AmazeTheme.typography.heading.copy(fontSize = AmazeTheme.fontSize.lg, color = colors.textPrimary)
                            )
                            Text(
                                text = when (currentPanel) {
                                    LibraryPanel.PRIMARY -> "Select a module to open or pin"
                                    LibraryPanel.ACADEMICS -> "Choose an academic sub-module"
                                    LibraryPanel.HOSTEL -> "Select a hostel service"
                                },
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }

                    // Line 2: Active Semester selector on its OWN NEW LINE with ample breathing room!
                    if (currentPanel == LibraryPanel.PRIMARY) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var showSemesterSheet by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.12f))
                                        .border(1.dp, colors.accent.copy(alpha = 0.28f), CircleShape)
                                        .clickable { showSemesterSheet = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Active Semester: ", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 11.sp))
                                    Text(semesterMap[selectedSemester] ?: selectedSemester, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (showSemesterSheet) {
                                SemesterPickerSheet(
                                    semIds = semIds,
                                    selectedId = selectedSemester,
                                    colors = colors,
                                    onDismiss = { showSemesterSheet = false },
                                    onSelect = { semId ->
                                        showSemesterSheet = false
                                        if (semId != selectedSemester) AppState.selectSemester(semId)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                // Scrollable Grid Content — scrolling is enabled only while the sheet is fully open;
                // while it is being dragged, all vertical drags move the sheet itself.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState(), enabled = sheetOffsetPx.value <= 0f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedContent(
                            targetState = currentPanel,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { panel ->
                            when (panel) {
                                LibraryPanel.PRIMARY -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SectionHeader("STUDY")
                                        DualRowGrid(
                                            items = primaryStudyItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                if (item.type == "panel" && item.panelTarget != null) {
                                                    currentPanel = item.panelTarget
                                                } else {
                                                    AppState.closeAppLibrary()
                                                    if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                                }
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )

                                        SectionHeader("CAMPUS")
                                        DualRowGrid(
                                            items = primaryCampusItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                if (item.type == "panel" && item.panelTarget != null) {
                                                    currentPanel = item.panelTarget
                                                } else {
                                                    AppState.closeAppLibrary()
                                                    if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                                }
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )

                                        SectionHeader("TOOLS & COMMUNITIES")
                                        DualRowGrid(
                                            items = primaryToolsItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                AppState.closeAppLibrary()
                                                if (item.onClickOverride != null) item.onClickOverride.invoke()
                                                else if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )

                                        SectionHeader("ACCOUNT & APP")
                                        DualRowGrid(
                                            items = primaryAccountItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                if (item.label == "Log Out") {
                                                    showLogoutConfirm = true
                                                    return@DualRowGrid
                                                }
                                                AppState.closeAppLibrary()
                                                if (item.onClickOverride != null) item.onClickOverride.invoke()
                                                else if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )
                                    }
                                }

                                LibraryPanel.ACADEMICS -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SectionHeader("ALL ACADEMIC SUB-MODULES")
                                        DualRowGrid(
                                            items = academicsSubItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                AppState.closeAppLibrary()
                                                if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )
                                    }
                                }

                                LibraryPanel.HOSTEL -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SectionHeader("HOSTEL SERVICES")
                                        DualRowGrid(
                                            items = hostelSubItems,
                                            pinnedTabs = pinnedNavTabs,
                                            onItemClick = { item ->
                                                AppState.closeAppLibrary()
                                                if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                                            },
                                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                                            colors = colors
                                        )
                                    }
                                }
                            }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (showLogoutConfirm) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirm = false },
                    title = { Text("Log Out Session?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to log out of your session?", color = colors.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = { AppState.logout(); showLogoutConfirm = false }) {
                            Text("Log Out", color = colors.danger, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutConfirm = false }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    },
                    containerColor = colors.surface
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = AmazeTheme.typography.smallLabel.copy(
            color = AmazeTheme.colors.textMuted,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun DualRowGrid(
    items: List<AppLibraryItem>,
    pinnedTabs: List<Screen>,
    onItemClick: (AppLibraryItem) -> Unit,
    onPinToggle: (Screen) -> Unit,
    colors: AmazeColors
) {
    val chunked = items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    val isPinned = item.pinnableScreen != null && pinnedTabs.contains(item.pinnableScreen)
                    AppLibraryCard(
                        item = item,
                        isPinned = isPinned,
                        onItemClick = { onItemClick(item) },
                        onPinToggle = { if (item.pinnableScreen != null) onPinToggle(item.pinnableScreen) },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppLibraryCard(
    item: AppLibraryItem,
    isPinned: Boolean,
    onItemClick: () -> Unit,
    onPinToggle: () -> Unit,
    colors: AmazeColors,
    modifier: Modifier = Modifier
) {
    val groupColor = when (item.groupName) {
        "Study", "Academics" -> colors.chart1
        "Campus", "Hostel" -> colors.chart2
        "Tools" -> colors.chart3
        else -> colors.chart5
    }

    AmazeCard(
        modifier = modifier,
        onClick = onItemClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                    .background(groupColor.copy(alpha = 0.14f))
                    .border(1.dp, groupColor.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.xs)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = groupColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.label,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subLabel,
                    style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.type == "panel") {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open sub-panel",
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            } else if (item.pinnableScreen != null) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) colors.accent else colors.textMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

private fun togglePinState(tab: Screen, currentPinned: List<Screen>) {
    val newList = if (currentPinned.contains(tab)) {
        currentPinned.filter { it != tab }
    } else {
        if (currentPinned.size < 4) currentPinned + tab else currentPinned
    }
    AppState.setPinnedNavTabs(newList)
}
