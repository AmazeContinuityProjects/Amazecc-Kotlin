package com.amazecc.app.shared.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "More",
            description = "Modules, Communities & Info",
            showBackButton = false,
            showSyncButton = false
        )

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {

            Text("App Library", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))

            val modules = listOf(
                Pair(Screen.CALENDAR, Icons.Rounded.CalendarMonth to "Calendar"),
                Pair(Screen.PAYMENTS, Icons.Rounded.CreditCard to "Payments"),
                Pair(Screen.LIBRARIES, Icons.AutoMirrored.Rounded.LibraryBooks to "Library"),
                Pair(Screen.HOSTEL, Icons.Rounded.Apartment to "Hostel"),
                Pair(Screen.TRANSPORT, Icons.Rounded.DirectionsBus to "Transport"),
                Pair(Screen.CABSHARE, Icons.Rounded.DirectionsCar to "Cabshare"),
                Pair(Screen.EVENTS, Icons.Rounded.Event to "Events"),
                Pair(Screen.QBANK, Icons.Rounded.Topic to "QBank"),
                Pair(Screen.SOCIAL, Icons.Rounded.People to "Social"),
                Pair(Screen.FFCS_PLANNER, Icons.Rounded.ViewTimeline to "FFCS"),
                Pair(Screen.FREE_CLASSROOMS, Icons.Rounded.MeetingRoom to "Classes"),
                Pair(Screen.MOODLE, Icons.AutoMirrored.Rounded.MenuBook to "Moodle")
            )

            val chunkedModules = modules.chunked(3)
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    chunkedModules.forEach { rowModules ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowModules.forEach { (screen, iconAndLabel) ->
                                val (icon, label) = iconAndLabel
                                ModuleIcon(
                                    icon = icon,
                                    label = label,
                                    onClick = { AppState.navigateTo(screen) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowModules.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Services & Tools", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard(title = "Faculty Info", description = "Faculty directory by school", icon = Icons.Rounded.People, onClick = { AppState.navigateTo(Screen.FACULTY_INFO) })
                    Spacer(Modifier.height(4.dp))
                    ActionCard(title = "Feedback", description = "Course feedback status", icon = Icons.Rounded.RateReview, onClick = { AppState.navigateTo(Screen.FEEDBACK_STATUS) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Communities", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Club Hub", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Explore student clubs and chapters", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Community Feed", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Latest posts from AmazeCC members", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings & Info", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ClickableRow(title = "App Settings", icon = Icons.Rounded.Settings, onClick = { AppState.navigateTo(Screen.SETTINGS) })
                    ClickableRow(title = "Activity Tree", icon = Icons.Rounded.GridView, onClick = { AppState.navigateTo(Screen.ACTIVITY_TREE) })
                    ClickableRow(title = "About AmazeCC", icon = Icons.Rounded.Info, onClick = { AppState.navigateTo(Screen.ABOUT) })
                    ClickableRow(title = "Fresher's Welcome", icon = Icons.Rounded.Star, onClick = { AppState.navigateTo(Screen.FRESHER_WELCOME) })
                    Spacer(modifier = Modifier.height(12.dp))
                    AmazeButton("Log Out", onClick = { AppState.logout() }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ModuleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ClickableRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = AmazeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
    }
}
