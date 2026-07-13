package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme

data class CommandItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val screen: Screen
)

@Composable
fun CommandPalette(
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    var query by remember { mutableStateOf("") }

    val allCommands = remember {
        listOf(
            CommandItem("Home", Icons.Rounded.Home, Screen.HOME),
            CommandItem("Attendance", Icons.AutoMirrored.Rounded.FactCheck, Screen.ATTENDANCE),
            CommandItem("Academics Hub", Icons.Rounded.School, Screen.ACADEMICS),
            CommandItem("Payments", Icons.Rounded.CreditCard, Screen.PAYMENTS),
            CommandItem("Library", Icons.AutoMirrored.Rounded.LibraryBooks, Screen.LIBRARIES),
            CommandItem("Hostel", Icons.Rounded.Apartment, Screen.HOSTEL),
            CommandItem("Transport", Icons.Rounded.DirectionsBus, Screen.TRANSPORT),
            CommandItem("Cab Share", Icons.Rounded.DirectionsCar, Screen.CABSHARE),
            CommandItem("Events", Icons.Rounded.Event, Screen.EVENTS),
            CommandItem("QBank", Icons.Rounded.Topic, Screen.QBANK),
            CommandItem("Social", Icons.Rounded.People, Screen.SOCIAL),
            CommandItem("Profile", Icons.Rounded.Person, Screen.PROFILE),
            CommandItem("Grades", Icons.Rounded.History, Screen.GRADES),
            CommandItem("CGPA Predictor", Icons.AutoMirrored.Rounded.TrendingUp, Screen.GPA_PREDICTOR),
            CommandItem("Arrear Management", Icons.Rounded.Warning, Screen.ARREAR),
            CommandItem("Makeup & Compre", Icons.Rounded.School, Screen.MAKEUP_COMPRE),
            CommandItem("Circulars", Icons.Rounded.Campaign, Screen.CIRCULARS),
            CommandItem("Curriculum", Icons.AutoMirrored.Rounded.MenuBook, Screen.CURRICULUM),
            CommandItem("OD Tracker", Icons.Rounded.TaskAlt, Screen.OD_TRACKER),
            CommandItem("Course Hub", Icons.Rounded.Dashboard, Screen.COURSE_DASHBOARD),
            CommandItem("Marks Timeline", Icons.Rounded.Timeline, Screen.MARKS_TIMELINE),
            CommandItem("VITOL Wallet", Icons.Rounded.AccountBalanceWallet, Screen.VITOL),
            CommandItem("Faculty Info", Icons.Rounded.People, Screen.FACULTY_INFO),
            CommandItem("Course Management", Icons.Rounded.School, Screen.COURSE_MANAGEMENT),
            CommandItem("Projects", Icons.Rounded.WorkspacePremium, Screen.PROJECTS),
            CommandItem("Wishlist", Icons.Rounded.Favorite, Screen.WISHLIST),
            CommandItem("Feedback", Icons.Rounded.RateReview, Screen.FEEDBACK_STATUS),
            CommandItem("Fresher Welcome", Icons.Rounded.Star, Screen.FRESHER_WELCOME),
            CommandItem("Documents", Icons.Rounded.Description, Screen.DOCUMENTS),
            CommandItem("About", Icons.Rounded.Info, Screen.ABOUT),
            CommandItem("Activity Tree", Icons.Rounded.GridView, Screen.ACTIVITY_TREE)
        )
    }

    val filtered = remember(query) {
        if (query.isBlank()) allCommands
        else allCommands.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            AmazeTextField(
                value = query,
                onValueChange = { query = it },
                label = "",
                placeholder = "Search commands...",
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                AppState.navigateTo(cmd.screen)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cmd.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(cmd.label, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
