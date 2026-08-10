package com.amazecc.app.shared.ui.components

import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.ui.strings.Strings

data class HeaderConfig(
    val title: String,
    val description: String = "",
    val showBackButton: Boolean = true,
    val showSyncButton: Boolean = true,
    val onRefresh: (() -> Unit)? = null,
    val syncModules: Set<SyncModule> = emptySet(),
    val onBackOverride: (() -> Unit)? = null,
    /** When set, the header search icon invokes this (e.g. reveal the screen's local search field). */
    val searchAction: (() -> Unit)? = null
)

fun headerConfigFor(screen: Screen): HeaderConfig? = when (screen) {
    Screen.ATTENDANCE -> HeaderConfig(
        title = "Attendance Hub",
        description = "Track your attendance, view timelines and predict shortfalls",
        showBackButton = true,
        showSyncButton = true,
        onRefresh = AppState::refreshCurrentSemester
    )
    Screen.ACADEMICS -> HeaderConfig(
        title = "Academics Hub",
        description = "Student OS",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshCurrentSemester
    )
    Screen.PAYMENTS -> HeaderConfig(
        title = "Payments",
        description = "View dues and transaction history",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshPayments
    )
    Screen.LIBRARIES -> HeaderConfig(
        title = "Library",
        description = "Search catalog & manage books",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = { AppState.syncLibrary() }
    )
    Screen.HOSTEL -> HeaderConfig(
        title = "Hostel Hub",
        description = "Manage mess, outings, laundry & counseling",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshHostel
    )
    Screen.PROFILE -> HeaderConfig(
        title = Strings.profile,
        description = "Your personal information",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshProfile
    )
    Screen.CABSHARE -> HeaderConfig(
        title = "Cab Share",
        description = "Verify VTOP + phone to get started",
        showBackButton = false,
        showSyncButton = false
    )
    Screen.TRANSPORT -> HeaderConfig(
        title = "Dayscholar Bus Hub",
        description = "Search and explore bus routes",
        showBackButton = true,
        showSyncButton = true,
        onRefresh = AppState::refreshTransport
    )
    Screen.EVENTS -> HeaderConfig(
        title = "Events",
        description = "Discover tech fests and meetups",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshEventsAndClubs
    )
    Screen.QBANK -> HeaderConfig(
        title = "Exam Prep Hub & QBank",
        description = "Targeted practice papers and exam preparation",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.SOCIAL -> HeaderConfig(
        title = "Social & Friends",
        description = "Find friends and match timetables",
        showBackButton = false,
        showSyncButton = true,
        onRefresh = AppState::refreshCurrentSemester
    )
    Screen.FFCS_PLANNER -> HeaderConfig(
        title = "FFCS Planner",
        description = "Select courses, generate timetables",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.FREE_CLASSROOMS -> HeaderConfig(
        title = "Free Classrooms",
        description = "Find live available empty rooms on campus",
        showBackButton = true,
        showSyncButton = false,
        onBackOverride = { AppState.navigateTo(Screen.ACADEMICS) }
    )
    Screen.CALENDAR -> HeaderConfig(
        title = "Academic Calendar",
        description = "Schedule, exams & assignments",
        showBackButton = true,
        showSyncButton = true,
        onRefresh = { AppState.refreshCalendarsList() },
        onBackOverride = { AppState.navigateTo(Screen.ACADEMICS) }
    )
    Screen.GRADES -> HeaderConfig(
        title = "Grade History",
        description = "All semesters — GPA and course grades",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.GPA_PREDICTOR -> HeaderConfig(
        title = "CGPA Predictor",
        description = "Project your CGPA or find the grade you need",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.CIRCULARS -> HeaderConfig(
        title = "Circulars",
        description = "Academic notices and circulars",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.CURRICULUM -> HeaderConfig(
        title = "Curriculum",
        description = "Track your degree requirements",
        showBackButton = true,
        showSyncButton = true,
        syncModules = setOf(SyncModule.CURRICULUM)
    )
    Screen.OD_TRACKER -> HeaderConfig(
        title = "OD Tracker",
        description = "Track on-duty hours",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.COURSE_DASHBOARD -> HeaderConfig(
        title = "Course Dashboard",
        description = "All courses across semesters",
        showBackButton = true,
        showSyncButton = true,
        onRefresh = AppState::refreshAllAcademic,
        onBackOverride = { AppState.navigateTo(Screen.ACADEMICS) }
    )
    Screen.COURSE_ATTENDANCE -> HeaderConfig(
        title = "Course Attendance",
        description = "",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.FACULTY_INFO -> HeaderConfig(
        title = "Faculty Info",
        description = "Global Faculty Directory",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.COURSE_MANAGEMENT -> HeaderConfig(
        title = "Course Management",
        description = "Option changes, EXC, minors",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.PROJECTS -> HeaderConfig(
        title = "Projects",
        description = "Academic projects and guides",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.WISHLIST -> HeaderConfig(
        title = "Wishlist",
        description = "Course wishlist",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.FEEDBACK_STATUS -> HeaderConfig(
        title = "Feedback Status",
        description = "Course feedback status",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.FRESHER_WELCOME -> HeaderConfig(
        title = "Fresher's Welcome",
        description = "Get started with campus life",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.DOCUMENTS -> HeaderConfig(
        title = "Documents",
        description = "Bonafide, transcripts, and learning",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.ABOUT -> HeaderConfig(
        title = Strings.about,
        description = "AmazeCC Student Companion",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.CLUB_DETAIL -> HeaderConfig(
        title = "Club Details",
        description = "Club Information",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.COURSE_DETAIL -> HeaderConfig(
        title = "Course Detail",
        description = "",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.SETTINGS -> HeaderConfig(
        title = "App Settings",
        description = "Customize your experience & app preferences",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.MOODLE -> HeaderConfig(
        title = "Moodle LMS",
        description = "Track your assignments and coursework",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.CLUB_HUB -> HeaderConfig(
        title = "Club Hub",
        description = "Explore clubs and community feed",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.TASKS -> HeaderConfig(
        title = "Tasks & Study Planner",
        description = "Workload, subtasks, focus sessions & Kanban",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.EXAM_SCHEDULE -> HeaderConfig(
        title = "Exam Schedule",
        description = "Upcoming exams, seating, and venue",
        showBackButton = true,
        showSyncButton = true,
        onRefresh = AppState::refreshExamSchedule
    )
    Screen.CHANGELOG -> HeaderConfig(
        title = "Changelog",
        description = "What's new in AmazeCC",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.HALL_OF_FAME -> HeaderConfig(
        title = "Hall of Fame",
        description = "Contributors who made this possible",
        showBackButton = true,
        showSyncButton = false
    )
    Screen.ARREAR -> HeaderConfig(
        title = "Arrear Management",
        description = "Track backlogs and exams",
        showBackButton = true,
        showSyncButton = true
    )
    Screen.SPLASH, Screen.LOGIN, Screen.ONBOARDING, Screen.HOME, Screen.MORE -> null
}