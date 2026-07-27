package com.amazecc.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen

@Composable
fun rememberGlobalCommands(): List<CommandItem> {
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val tasks by AppState.tasks.collectAsState()
    val profile by AppState.studentProfile.collectAsState()

    return remember(attendanceRes, marksRes, tasks, profile) {
        val result = mutableListOf<CommandItem>()

        // 1. Static Navigation Commands
        val navCommands = listOf(
            CommandItem("nav-home", "Home", "Go to Dashboard", Icons.Rounded.Home, "Navigation", onSelect = { AppState.navigateTo(Screen.HOME) }),
            CommandItem("nav-attendance", "Attendance", "View your attendance", Icons.AutoMirrored.Rounded.FactCheck, "Navigation", onSelect = { AppState.navigateTo(Screen.ATTENDANCE) }),
            CommandItem("nav-academics", "Academics Hub", "All academic resources", Icons.Rounded.School, "Navigation", onSelect = { AppState.navigateTo(Screen.ACADEMICS) }),
            CommandItem("nav-payments", "Payments", "Fee payments & receipts", Icons.Rounded.CreditCard, "Navigation", onSelect = { AppState.navigateTo(Screen.PAYMENTS) }),
            CommandItem("nav-library", "Library", "Koha catalog and dues", Icons.AutoMirrored.Rounded.MenuBook, "Navigation", onSelect = { AppState.navigateTo(Screen.LIBRARIES) }),
            CommandItem("nav-hostel", "Hostel", "Hostel services & leave", Icons.Rounded.Apartment, "Navigation", onSelect = { AppState.navigateTo(Screen.HOSTEL) }),
            CommandItem("nav-transport", "Transport", "Bus routes & timings", Icons.Rounded.DirectionsBus, "Navigation", onSelect = { AppState.navigateTo(Screen.TRANSPORT) }),
            CommandItem("nav-events", "Events", "Upcoming fests & events", Icons.Rounded.Event, "Navigation", onSelect = { AppState.navigateTo(Screen.EVENTS) }),
            CommandItem("nav-qbank", "QBank", "Question papers", Icons.Rounded.Topic, "Navigation", onSelect = { AppState.navigateTo(Screen.QBANK) }),
            CommandItem("nav-social", "Social", "Student community", Icons.Rounded.People, "Navigation", onSelect = { AppState.navigateTo(Screen.SOCIAL) }),
            CommandItem("nav-profile", "Profile", "Your student details", Icons.Rounded.Person, "Navigation", onSelect = { AppState.navigateTo(Screen.PROFILE) }),
            CommandItem("nav-grades", "Grades", "Academic performance", Icons.Rounded.History, "Navigation", onSelect = { AppState.navigateTo(Screen.GRADES) }),
            CommandItem("nav-cgpa", "CGPA Predictor", "Predict your CGPA", Icons.AutoMirrored.Rounded.TrendingUp, "Navigation", onSelect = { AppState.navigateTo(Screen.GPA_PREDICTOR) }),
        )
        result.addAll(navCommands)

        // 2. Profile Details
        profile?.let { p ->
            result.add(
                CommandItem(
                    id = "profile-detail",
                    label = "Profile: ${p.name.ifEmpty { "Student" }}",
                    description = p.regNo,
                    icon = Icons.Rounded.Person,
                    category = "Profile",
                    onSelect = { AppState.navigateTo(Screen.PROFILE) }
                )
            )
        }

        // 3. Attendance Commands
        attendanceRes?.attendance?.forEach { course ->
            result.add(
                CommandItem(
                    id = "att-${course.courseCode}",
                    label = course.courseTitle,
                    description = "Attendance: ${course.attendancePercentage}%",
                    icon = Icons.Rounded.Class,
                    category = "Courses (Attendance)",
                    onSelect = { AppState.openCourseDetail(course.courseCode) }
                )
            )
        }

        // 4. Tasks Commands
        tasks.forEach { task ->
            result.add(
                CommandItem(
                    id = "task-${task.id}",
                    label = task.title,
                    description = "Due: ${task.dueDate}",
                    icon = Icons.Rounded.TaskAlt,
                    category = "Tasks",
                    onSelect = { AppState.navigateTo(Screen.TASKS) }
                )
            )
        }

        result
    }
}
