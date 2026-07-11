package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ActionCard
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeDropdown
import com.amazecc.app.shared.ui.components.MetricCard
import com.amazecc.app.shared.ui.components.PageHeaderContainer

@Composable
fun DashboardScreen() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    
    val selectedSemester by AppState.selectedSemester.collectAsState()
    
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val libraryRes by AppState.library.collectAsState()
    val paymentsRes by AppState.payments.collectAsState()
    val hostelDetails by AppState.hostelDetails.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()
    
    // Compute quick dashboard stats
    val overallAttendance = remember(attendanceRes) {
        val list = attendanceRes?.attendance ?: emptyList()
        if (list.isEmpty()) "—"
        else {
            val validPercentages = list.mapNotNull { it.attendancePercentage?.toDoubleOrNull() }
            if (validPercentages.isEmpty()) "—"
            else "${validPercentages.average().toInt()}%"
        }
    }
    
    val cgpa = marksRes?.cgpa?.cgpa ?: "—"
    val libraryDues = libraryRes?.booksIssued?.size ?: 0
    val walletBalance = paymentsRes?.walletBalance ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Page Header
            PageHeaderContainer(
                title = "AmazeCC Student OS",
            description = syncStatus ?: "Welcome back, $authorizedID",
            actions = {
                AmazeDropdown(
                    options = AppState.semesterIDs,
                    selectedOption = selectedSemester,
                    onOptionSelected = { AppState.selectSemester(it) },
                    label = "",
                    modifier = Modifier.width(160.dp)
                )
            }
        )

        // Main Contents Scroll
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Quick metrics overview cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "OVERALL ATTENDANCE",
                    value = overallAttendance,
                    caption = "Classes summary",
                    modifier = Modifier.weight(1f),
                    onClick = { AppState.navigateTo(Screen.ATTENDANCE) }
                )
                MetricCard(
                    title = "ACADEMIC CGPA",
                    value = cgpa,
                    caption = "Latest grades",
                    modifier = Modifier.weight(1f),
                    onClick = { AppState.navigateTo(Screen.MARKS) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "LIBRARY ISSUES",
                    value = "$libraryDues Books",
                    caption = "Active checkouts",
                    modifier = Modifier.weight(1f),
                    onClick = { AppState.navigateTo(Screen.LIBRARY) }
                )
                MetricCard(
                    title = "WALLET BALANCE",
                    value = walletBalance,
                    caption = "Synced from payments API",
                    modifier = Modifier.weight(1f),
                    onClick = { AppState.navigateTo(Screen.PAYMENTS) }
                )
            }

            if (syncError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = syncError ?: "",
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.dangerText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation grid title
            Text(
                text = "Academic Services",
                style = AmazeTheme.typography.subheading.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Action lists
            ActionCard(
                title = "Attendance Tracker",
                description = "Track percentages & simulate future class presence",
                icon = Icons.Rounded.CheckCircle,
                onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Marks & Exam Grades",
                description = "View internal assessments, exams, and grade history",
                icon = Icons.Rounded.Star,
                onClick = { AppState.navigateTo(Screen.MARKS) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Class Timetable & Schedule",
                description = "View daily hours, locations, and instructional calendar",
                icon = Icons.Rounded.DateRange,
                onClick = { AppState.navigateTo(Screen.TIMETABLE) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Campus Life & Tools",
                style = AmazeTheme.typography.subheading.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "LMS assignments & Exams",
                description = "Manage digital submissions and check exam venues",
                icon = Icons.Rounded.List,
                onClick = { AppState.navigateTo(Screen.LMS) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Payments & Wallet",
                description = "View transaction history and download receipts",
                icon = Icons.Rounded.ShoppingCart,
                onClick = { AppState.navigateTo(Screen.PAYMENTS) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionCard(
                    title = "Library Koha",
                    description = "Search catalog",
                    icon = Icons.Rounded.Book,
                    onClick = { AppState.navigateTo(Screen.LIBRARY) },
                    modifier = Modifier.weight(1f)
                )
                
                if (hostelDetails?.isHosteller == true) {
                    ActionCard(
                        title = "Hostel Portal",
                        description = "Mess & leaves",
                        icon = Icons.Rounded.Home,
                        onClick = { AppState.navigateTo(Screen.HOSTEL) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ActionCard(
                        title = "Transport Routes",
                        description = "Bus pass & slots",
                        icon = Icons.Rounded.Info,
                        onClick = { AppState.navigateTo(Screen.TRANSPORT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionCard(
                    title = "Social & Events",
                    description = "Fests & clubs",
                    icon = Icons.Rounded.Star,
                    onClick = { AppState.navigateTo(Screen.EVENTS) },
                    modifier = Modifier.weight(1f)
                )

                ActionCard(
                    title = "Friends Directory",
                    description = "Tap to share",
                    icon = Icons.Rounded.Person,
                    onClick = { AppState.navigateTo(Screen.SOCIAL) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "FFCS Timetable Planner",
                description = "Plan conflict-free schedules",
                icon = Icons.Rounded.DateRange,
                onClick = { AppState.navigateTo(Screen.FFCS) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Logout & System settings shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { AppState.navigateTo(Screen.PROFILE) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Preferences", style = AmazeTheme.typography.body.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                    }
                }
                
                TextButton(
                    onClick = { AppState.logout() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = null, tint = colors.danger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Secure Exit", style = AmazeTheme.typography.body.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Inline helper to remember computed states
@Composable
inline fun <T> remember(key1: Any?, crossinline block: () -> T): T =
    androidx.compose.runtime.remember(key1) { block() }
