package com.amazecc.app.shared.ui.screens.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen

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

/**
 * Single source of truth for every module in the app — consumed both by the
 * App Library sheet (MoreScreen) and the global command palette.
 */
val appLibraryItems: List<AppLibraryItem> = listOf(
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
    AppLibraryItem("Settings", "App theme, bottom bar, alerts & credentials", Icons.Rounded.Settings, "Account", targetScreen = Screen.SETTINGS, pinnableScreen = null),
    AppLibraryItem("About & Resources", "Version info, open source & legal", Icons.Rounded.Info, "Account", targetScreen = Screen.ABOUT, pinnableScreen = null),
    AppLibraryItem("Fresher's Welcome", "Orientation guide & starter kit", Icons.Rounded.Star, "Account", targetScreen = Screen.FRESHER_WELCOME, pinnableScreen = null),
    AppLibraryItem("Log Out", "Log out active student session", Icons.Rounded.Logout, "Account", onClickOverride = { AppState.logout() }, pinnableScreen = null)
)

/** Executes the navigation/action of an app-library item from outside the sheet (e.g. palette). */
fun executeAppLibraryItem(item: AppLibraryItem) {
    if (item.onClickOverride != null) {
        item.onClickOverride.invoke()
    } else if (item.targetScreen != null) {
        AppState.navigateTo(item.targetScreen)
    }
}