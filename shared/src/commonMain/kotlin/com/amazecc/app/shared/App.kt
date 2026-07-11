package com.amazecc.app.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()
    var isMoreOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SessionManager.isLoggedIn) {
            if (SessionManager.postLoginCompleted.value) {
                AppState.switchTopLevel(Screen.DASHBOARD)
                AppState.loadAllData()
            } else {
                AppState.switchTopLevel(Screen.POST_LOGIN_ONBOARDING)
            }
        }
    }

    AmazeTheme(
        appTheme = currentTheme,
        accentTheme = currentAccent
    ) {
        val colors = AmazeTheme.colors
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220))) togetherWith
                                (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.97f, animationSpec = tween(180)))
                            }
                        ) { targetScreen ->
                            when (targetScreen) {
                                Screen.LOGIN -> LoginScreen()
                                Screen.POST_LOGIN_ONBOARDING -> PostLoginOnboardingScreen()
                                Screen.DASHBOARD -> DashboardScreen()
                                Screen.ATTENDANCE -> AttendanceScreen()
                                Screen.MARKS -> MarksGradesScreen()
                                Screen.TIMETABLE -> TimetableScreen()
                                Screen.HOSTEL -> HostelScreen()
                                Screen.PAYMENTS -> PaymentsScreen()
                                Screen.LIBRARY -> LibraryScreen()
                                Screen.TRANSPORT -> TransportScreen()
                                Screen.LMS -> LMSScreen()
                                Screen.PROFILE -> ProfileScreen()
                            }
                        }
                    }

                    if (currentScreen != Screen.LOGIN && currentScreen != Screen.POST_LOGIN_ONBOARDING) {
                        AmazeBottomBar(
                            currentScreen = currentScreen,
                            onMoreClick = { isMoreOpen = true }
                        )
                    }
                }

                if (isMoreOpen) {
                    AmazeMoreSheet(
                        onDismiss = { isMoreOpen = false },
                        onNavigate = { screen ->
                            isMoreOpen = false
                            AppState.switchTopLevel(screen)
                        }
                    )
                }

                // Global Loading Overlay
                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.6f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = syncStatus ?: "Syncing data...",
                            color = colors.textPrimary
                        )
                    }
                }

                if (!isLoading && syncError != null && currentScreen != Screen.LOGIN) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (currentScreen == Screen.LOGIN) 16.dp else 88.dp
                            )
                            .background(colors.dangerSurface, MaterialTheme.shapes.small)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = syncError ?: "",
                            color = colors.dangerText
                        )
                    }
                }
            }
        }
    }
}

private data class BottomNavItem(
    val label: String,
    val screen: Screen?,
    val icon: ImageVector,
    val selectedScreens: Set<Screen>
)

@Composable
private fun AmazeBottomBar(
    currentScreen: Screen,
    onMoreClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val items = listOf(
        BottomNavItem("Home", Screen.DASHBOARD, Icons.Rounded.Home, setOf(Screen.DASHBOARD)),
        BottomNavItem("Attend", Screen.ATTENDANCE, Icons.Rounded.CheckCircle, setOf(Screen.ATTENDANCE)),
        BottomNavItem("Academics", Screen.MARKS, Icons.Rounded.Star, setOf(Screen.MARKS)),
        BottomNavItem("More", null, Icons.Rounded.MoreHoriz, setOf(
            Screen.TIMETABLE, Screen.HOSTEL, Screen.PAYMENTS, Screen.LIBRARY, Screen.TRANSPORT, Screen.LMS, Screen.PROFILE
        ))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .shadow(10.dp, RoundedCornerShape(radius.large))
            .clip(RoundedCornerShape(radius.large))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(radius.large))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentScreen in item.selectedScreens
            val itemBg by animateColorAsState(
                targetValue = if (selected) colors.elevatedSurface else colors.surface
            )
            val itemTint by animateColorAsState(
                targetValue = if (selected) colors.accent else colors.textMuted
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(itemBg)
                    .clickable {
                        item.screen?.let { AppState.switchTopLevel(it) } ?: onMoreClick()
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = itemTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.label,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private data class MoreModule(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val screen: Screen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmazeMoreSheet(
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    var query by remember { mutableStateOf("") }
    val modules = listOf(
        MoreModule("Timetable", "Class hours, rooms, and schedule preview", Icons.Rounded.DateRange, Screen.TIMETABLE),
        MoreModule("Hostel", "Mess, leave requests, and resident tools", Icons.Rounded.Home, Screen.HOSTEL),
        MoreModule("Payments", "Tuition fees, hostel dues, and transactions", Icons.Rounded.ShoppingCart, Screen.PAYMENTS),
        MoreModule("Library", "Issued books, dues, and KOHA access", Icons.Rounded.Book, Screen.LIBRARY),
        MoreModule("Transport", "Bus routes and day scholar status", Icons.Rounded.Info, Screen.TRANSPORT),
        MoreModule("LMS", "Assignments, deadlines, and submissions", Icons.Rounded.List, Screen.LMS),
        MoreModule("Profile", "Student summary and preferences", Icons.Rounded.AccountCircle, Screen.PROFILE)
    )
    val filteredModules = modules.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = colors.accent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "More Options",
                        style = AmazeTheme.typography.heading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = "AmazeCC modules and student tools",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Search modules", color = colors.textMuted)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted)
                },
                shape = RoundedCornerShape(radius.medium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.elevatedSurface,
                    unfocusedContainerColor = colors.elevatedSurface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filteredModules.forEach { module ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.medium))
                            .background(colors.elevatedSurface)
                            .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                            .clickable { onNavigate(module.screen) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(radius.small))
                                .background(colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(module.icon, contentDescription = null, tint = colors.accent)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = module.title,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = module.description,
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(16.dp))

            val currentTheme by AppState.theme.collectAsState()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Theme Cycle Card
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                        .clickable {
                            val nextTheme = when (currentTheme) {
                                AppTheme.SYSTEM -> AppTheme.LIGHT
                                AppTheme.LIGHT -> AppTheme.DARK
                                AppTheme.DARK -> AppTheme.MIDNIGHT
                                AppTheme.MIDNIGHT -> AppTheme.SYSTEM
                            }
                            AppState.changeTheme(nextTheme)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(radius.small))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Theme",
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = when (currentTheme) {
                                AppTheme.LIGHT -> "Light"
                                AppTheme.DARK -> "Dark"
                                AppTheme.MIDNIGHT -> "Midnight"
                                AppTheme.SYSTEM -> "System"
                            },
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                        )
                    }
                }

                // Logout Card
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                        .clickable {
                            onDismiss()
                            AppState.logout()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(radius.small))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Sign Out",
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.danger)
                        )
                        Text(
                            text = "End session",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                        )
                    }
                }
            }
        }
    }
}
