package com.amazecc.app.shared.ui.screens.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

enum class LibraryPanel {
    PRIMARY,
    ACADEMICS,
    HOSTEL
}

data class AppLibraryItem(
    val label: String,
    val subLabel: String,
    val icon: ImageVector,
    val groupName: String,
    val type: String = "link", // "link" or "panel"
    val targetScreen: Screen? = null,
    val panelTarget: LibraryPanel? = null,
    val onClickOverride: (() -> Unit)? = null,
    val pinnableScreen: Screen? = targetScreen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var currentPanel by remember { mutableStateOf(LibraryPanel.PRIMARY) }
    var searchQuery by remember { mutableStateOf("") }

    val pinnedNavTabs by AppState.pinnedNavTabs.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val semIds = semesterMap.keys.toList().sortedDescending()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onDismissSheet: () -> Unit = {
        AppState.closeAppLibrary()
    }

    // Reset search on panel change
    LaunchedEffect(currentPanel) {
        searchQuery = ""
    }

    // All searchable items (matching AmazeCC AppLibraryPortal)
    val allSearchableItems = remember {
        listOf(
            // STUDY
            AppLibraryItem("Attendance", "Class attendance & slot tracker", Icons.Rounded.EventAvailable, "Study", targetScreen = Screen.ATTENDANCE),
            AppLibraryItem("Timetable Calendar", "Daily schedule & exam calendar", Icons.Rounded.CalendarMonth, "Study", targetScreen = Screen.CALENDAR),
            AppLibraryItem("Academics Hub", "Academic sub-panel & grade tools", Icons.Rounded.School, "Study", type = "panel", panelTarget = LibraryPanel.ACADEMICS, pinnableScreen = null),
            AppLibraryItem("Course Dashboard", "Attendance & marks per course", Icons.Rounded.Book, "Academics", targetScreen = Screen.ATTENDANCE),
            AppLibraryItem("Grade History", "Semester SGPA & grade breakdown", Icons.Rounded.School, "Academics", targetScreen = Screen.ACADEMICS),
            AppLibraryItem("Question Bank", "CAT & FAT previous year papers", Icons.Rounded.Topic, "Academics", targetScreen = Screen.QBANK),
            AppLibraryItem("FFCS Planner", "Timetable builder & clash finder", Icons.Rounded.ViewTimeline, "Academics", targetScreen = Screen.FFCS_PLANNER),
            AppLibraryItem("Free Classrooms", "Empty classroom locator", Icons.Rounded.MeetingRoom, "Academics", targetScreen = Screen.FREE_CLASSROOMS),
            AppLibraryItem("Faculty Directory", "Faculty cabin & ratings", Icons.Rounded.People, "Academics", targetScreen = Screen.FACULTY_INFO),
            AppLibraryItem("Moodle LMS", "Course materials & assignments", Icons.AutoMirrored.Rounded.MenuBook, "Academics", targetScreen = Screen.MOODLE),
            AppLibraryItem("Projects", "Academic projects & lab progress", Icons.Rounded.AccountTree, "Academics", targetScreen = Screen.PROJECTS),
            AppLibraryItem("Wishlist", "Saved target courses & wishlist", Icons.Rounded.Bookmark, "Academics", targetScreen = Screen.WISHLIST),
            AppLibraryItem("Feedback Status", "VTOP faculty feedback status", Icons.Rounded.RateReview, "Academics", targetScreen = Screen.FEEDBACK_STATUS),

            // CAMPUS
            AppLibraryItem("Cab Share", "Ride sharing & split fare hub", Icons.Rounded.DirectionsCar, "Campus", targetScreen = Screen.CABSHARE),
            AppLibraryItem("Payments", "Hostel & academic fee receipts", Icons.Rounded.CreditCard, "Campus", targetScreen = Screen.PAYMENTS),
            AppLibraryItem("Libraries", "Book search & digital library", Icons.AutoMirrored.Rounded.LibraryBooks, "Campus", targetScreen = Screen.LIBRARIES),
            AppLibraryItem("Hostel Hub", "Mess menu, laundry & gatepass", Icons.Rounded.Apartment, "Campus", type = "panel", panelTarget = LibraryPanel.HOSTEL, pinnableScreen = Screen.HOSTEL),
            AppLibraryItem("Transport", "Shuttle bus routes & mobility", Icons.Rounded.DirectionsBus, "Campus", targetScreen = Screen.TRANSPORT),
            AppLibraryItem("Mess Menu", "Daily mess menu & food schedule", Icons.Rounded.Restaurant, "Hostel", targetScreen = Screen.HOSTEL),
            AppLibraryItem("Laundry", "Laundry token & wash status", Icons.Rounded.LocalLaundryService, "Hostel", targetScreen = Screen.HOSTEL),
            AppLibraryItem("Leave / Gatepass", "Hostel leave & gatepass QR", Icons.Rounded.ExitToApp, "Hostel", targetScreen = Screen.HOSTEL),

            // TOOLS & UTILITIES
            AppLibraryItem("Social Feed", "Anonymous campus discussion feed", Icons.Rounded.Public, "Tools", targetScreen = Screen.SOCIAL),
            AppLibraryItem("Event Hub", "Campus fests, hackathons & events", Icons.Rounded.Event, "Tools", targetScreen = Screen.EVENTS),
            AppLibraryItem("Club Hub", "Student clubs, chapters & teams", Icons.Rounded.Groups, "Tools", targetScreen = Screen.CLUB_HUB, onClickOverride = { AppState.openClubHub("Directory") }),

            // ACCOUNT & SETTINGS
            AppLibraryItem("My Info", "Registration details & academic bio", Icons.Rounded.Person, "Account", targetScreen = Screen.PROFILE, pinnableScreen = null),
            AppLibraryItem("Credentials", "Saved VTOP, Moodle & Library logins", Icons.Rounded.Lock, "Account", targetScreen = Screen.SETTINGS, pinnableScreen = null),
            AppLibraryItem("Settings", "App theme, bottom bar & alerts", Icons.Rounded.Settings, "Account", targetScreen = Screen.SETTINGS, pinnableScreen = null),
            AppLibraryItem("About & Resources", "Version info, open source & legal", Icons.Rounded.Info, "Account", targetScreen = Screen.ABOUT, pinnableScreen = null),
            AppLibraryItem("Fresher's Welcome", "Orientation guide & starter kit", Icons.Rounded.Star, "Account", targetScreen = Screen.FRESHER_WELCOME, pinnableScreen = null),
            AppLibraryItem("Log Out", "Log out active student session", Icons.Rounded.Logout, "Account", onClickOverride = { showLogoutConfirm = true }, pinnableScreen = null)
        )
    }

    val primaryStudyItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Study" } }
    val primaryCampusItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Campus" } }
    val primaryToolsItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Tools" } }
    val primaryAccountItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Account" } }

    val academicsSubItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Academics" } }
    val hostelSubItems = remember(allSearchableItems) { allSearchableItems.filter { it.groupName == "Hostel" } }

    val filteredSearchResults = remember(searchQuery, allSearchableItems) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableItems.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.subLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState,
        containerColor = colors.background,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.textMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(bottom = 16.dp)
        ) {
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

                // Line 2: Active Semester Dropdown on its OWN NEW LINE with ample breathing room!
                if (currentPanel == LibraryPanel.PRIMARY) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var semExpanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.12f))
                                    .border(1.dp, colors.accent.copy(alpha = 0.28f), CircleShape)
                                    .clickable { semExpanded = !semExpanded }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Active Semester: ", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 11.sp))
                                Text(semesterMap[selectedSemester] ?: selectedSemester, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = semExpanded,
                                onDismissRequest = { semExpanded = false },
                                modifier = Modifier.background(colors.surface)
                            ) {
                                semIds.forEach { semId ->
                                    val isSelected = semId == selectedSemester
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = semesterMap[semId] ?: semId,
                                                color = if (isSelected) colors.accent else colors.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = AmazeTheme.fontSize.xs
                                            )
                                        },
                                        onClick = {
                                            semExpanded = false
                                            if (semId != selectedSemester) AppState.selectSemester(semId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Line 3: Search Input
                AmazeSearchInput(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search modules (e.g. Attendance, Hostel, Moodle)..."
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

            // Scrollable Grid Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    SectionHeader("SEARCH RESULTS")
                    if (filteredSearchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("No modules found matching \"$searchQuery\"", color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
                        }
                    } else {
                        DualRowGrid(
                            items = filteredSearchResults,
                            pinnedTabs = pinnedNavTabs,
                            onItemClick = { item ->
                                AppState.closeAppLibrary()
                                if (item.onClickOverride != null) item.onClickOverride.invoke()
                                else if (item.targetScreen != null) AppState.navigateTo(item.targetScreen)
                            },
                            onPinToggle = { tab -> togglePinState(tab, pinnedNavTabs) },
                            colors = colors
                        )
                    }
                } else {
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
        }
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
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPinned) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
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
