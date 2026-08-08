package com.amazecc.app.shared.ui.screens.more

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

data class LibraryItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val group: String,
    val screen: Screen?,
    val onClickOverride: (() -> Unit)? = null,
    val pinnableScreen: Screen? = screen
)

@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Study", "Campus", "Tools", "Account")

    val pinnedNavTabs by AppState.pinnedNavTabs.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()

    val libraryItems = remember {
        listOf(
            // ── STUDY & ACADEMICS ──
            LibraryItem("Attendance", "Class attendance & slot tracker", Icons.Rounded.EventAvailable, "Study", Screen.ATTENDANCE),
            LibraryItem("Timetable Calendar", "Daily schedule & exam calendar", Icons.Rounded.CalendarMonth, "Study", Screen.CALENDAR),
            LibraryItem("Academics Hub", "Grades, curriculum, CGPA & arrear hub", Icons.Rounded.School, "Study", Screen.ACADEMICS),
            LibraryItem("Question Bank", "CAT & FAT previous year question papers", Icons.Rounded.Topic, "Study", Screen.QBANK),
            LibraryItem("FFCS Planner", "Course timetable builder & slot clash finder", Icons.Rounded.ViewTimeline, "Study", Screen.FFCS_PLANNER),
            LibraryItem("Free Classrooms", "Empty classroom locator for study sessions", Icons.Rounded.MeetingRoom, "Study", Screen.FREE_CLASSROOMS),
            LibraryItem("Faculty Directory", "Faculty cabin location & teacher info", Icons.Rounded.People, "Study", Screen.FACULTY_INFO),
            LibraryItem("Moodle LMS", "Course materials & assignment portal", Icons.AutoMirrored.Rounded.MenuBook, "Study", Screen.MOODLE),
            LibraryItem("Projects", "Academic projects & lab progress", Icons.Rounded.AccountTree, "Study", Screen.PROJECTS),
            LibraryItem("Wishlist", "Saved target courses & slot wishlist", Icons.Rounded.Bookmark, "Study", Screen.WISHLIST),
            LibraryItem("Feedback Status", "VTOP faculty feedback completion tracker", Icons.Rounded.RateReview, "Study", Screen.FEEDBACK_STATUS),

            // ── CAMPUS & SERVICES ──
            LibraryItem("Cab Share", "Ride sharing & split fare community", Icons.Rounded.DirectionsCar, "Campus", Screen.CABSHARE),
            LibraryItem("Payments & Fees", "Hostel & academic fee receipt status", Icons.Rounded.CreditCard, "Campus", Screen.PAYMENTS),
            LibraryItem("Library Portal", "Book search & digital library access", Icons.AutoMirrored.Rounded.LibraryBooks, "Campus", Screen.LIBRARIES),
            LibraryItem("Hostel Hub", "Mess menu, laundry & gatepass leaves", Icons.Rounded.Apartment, "Campus", Screen.HOSTEL),
            LibraryItem("Campus Transport", "Shuttle bus routes & campus mobility", Icons.Rounded.DirectionsBus, "Campus", Screen.TRANSPORT),

            // ── COMMUNITIES & TOOLS ──
            LibraryItem("Social Feed", "Anonymous campus community & discussion feed", Icons.Rounded.Public, "Tools", Screen.SOCIAL),
            LibraryItem("Event Hub", "Campus fests, hackathons & workshops", Icons.Rounded.Event, "Tools", Screen.EVENTS),
            LibraryItem("Club Hub", "Student clubs, chapters & campus teams", Icons.Rounded.Groups, "Tools", Screen.CLUB_HUB, onClickOverride = { AppState.openClubHub("Directory") }),

            // ── ACCOUNT & APP ──
            LibraryItem("Student Profile", "Registration details, CGPA & academic bio", Icons.Rounded.Person, "Account", Screen.PROFILE, pinnableScreen = null),
            LibraryItem("App Settings", "Submenu hub for themes, bottom bar & alerts", Icons.Rounded.Settings, "Account", Screen.SETTINGS, pinnableScreen = null),
            LibraryItem("About AmazeCC", "Version info, open source credits & legal", Icons.Rounded.Info, "Account", Screen.ABOUT, pinnableScreen = null),
            LibraryItem("Fresher's Welcome", "Orientation guide & campus starter kit", Icons.Rounded.Star, "Account", Screen.FRESHER_WELCOME, pinnableScreen = null)
        )
    }

    val filteredItems = libraryItems.filter { item ->
        val matchesCategory = selectedCategory == "All" || item.group == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val groupedItems = filteredItems.groupBy { it.group }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "App Library",
            description = "Explore all modules, campus services & app utilities",
            showBackButton = false,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderSpacer()

            // ── ACTIVE SEMESTER INDICATOR ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE SEMESTER",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    )
                }
                Text(
                    text = semesterMap[selectedSemester] ?: selectedSemester,
                    style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
            }

            // ── SEARCH & CATEGORY FILTER BAR ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmazeSearchInput(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search App Library (e.g., Attendance, Hostel, Club)..."
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = bouncySpring()
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedCategory = category }
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = if (isSelected) Color.White else colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // ── CATEGORIZED APP LIBRARY ITEMS ──
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No modules found matching \"$searchQuery\"",
                        color = colors.textMuted,
                        fontSize = AmazeTheme.fontSize.sm
                    )
                }
            } else {
                val groupOrder = listOf("Study", "Campus", "Tools", "Account")
                val groupTitles = mapOf(
                    "Study" to ("Study & Academics" to colors.chart1),
                    "Campus" to ("Campus & Services" to colors.chart2),
                    "Tools" to ("Communities & Tools" to colors.chart3),
                    "Account" to ("Account & App" to colors.chart5)
                )

                groupOrder.forEach { groupKey ->
                    val itemsInGroup = groupedItems[groupKey]
                    if (!itemsInGroup.isNullOrEmpty()) {
                        val (title, titleColor) = groupTitles[groupKey] ?: (groupKey to colors.textPrimary)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = titleColor)
                                )
                                Text(
                                    text = "${itemsInGroup.size} items",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                                )
                            }

                            itemsInGroup.forEach { item ->
                                val isPinned = item.pinnableScreen != null && pinnedNavTabs.contains(item.pinnableScreen)
                                LibraryItemCard(
                                    item = item,
                                    isPinned = isPinned,
                                    onPinToggle = {
                                        if (item.pinnableScreen != null) {
                                            val newList = if (isPinned) {
                                                pinnedNavTabs.filter { it != item.pinnableScreen }
                                            } else {
                                                if (pinnedNavTabs.size < 4) pinnedNavTabs + item.pinnableScreen else pinnedNavTabs
                                            }
                                            AppState.setPinnedNavTabs(newList)
                                        }
                                    },
                                    onClick = {
                                        if (item.onClickOverride != null) {
                                            item.onClickOverride.invoke()
                                        } else if (item.screen != null) {
                                            AppState.navigateTo(item.screen)
                                        }
                                    },
                                    colors = colors
                                )
                            }
                        }
                    }
                }
            }

            // ── LOGOUT SESSION FOOTER BUTTON ──
            if (selectedCategory == "All" || selectedCategory == "Account") {
                Spacer(Modifier.height(4.dp))
                AmazeButton(
                    text = "Log Out Session",
                    onClick = { showLogoutConfirm = true },
                    variant = ButtonVariant.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FooterSpacer()
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
private fun LibraryItemCard(
    item: LibraryItem,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onClick: () -> Unit,
    colors: AmazeColors
) {
    val groupColor = when (item.group) {
        "Study" -> colors.chart1
        "Campus" -> colors.chart2
        "Tools" -> colors.chart3
        else -> colors.chart5
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(groupColor.copy(alpha = 0.14f))
                    .border(1.dp, groupColor.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = groupColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    text = item.description,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.pinnableScreen != null) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(if (isPinned) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (isPinned) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.xs))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (isPinned) "Unpin tab" else "Pin tab to bottom bar",
                        tint = if (isPinned) colors.accent else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open module",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
