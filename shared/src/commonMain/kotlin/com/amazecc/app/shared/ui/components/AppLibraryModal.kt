package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme

data class AppModule(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val screen: Screen,
    val color: Color = Color(0xFF3B82F6) // Default blue
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLibraryModal(onDismiss: () -> Unit) {
    val colors = AmazeTheme.colors
    
    val modules = listOf(
        AppModule("attendance", "Attendance", Icons.Rounded.EventAvailable, Screen.ATTENDANCE, Color(0xFF10B981)), // Emerald
        AppModule("marks", "Marks & GPA", Icons.Rounded.BarChart, Screen.MARKS, Color(0xFFF59E0B)), // Amber
        AppModule("timetable", "Timetable", Icons.Rounded.Schedule, Screen.TIMETABLE, Color(0xFF6366F1)), // Indigo
        AppModule("ffcs", "FFCS Planner", Icons.Rounded.EditCalendar, Screen.FFCS, Color(0xFFEC4899)), // Pink
        AppModule("events", "Events", Icons.Rounded.Celebration, Screen.EVENTS, Color(0xFF8B5CF6)), // Violet
        AppModule("qbank", "QBank", Icons.Rounded.MenuBook, Screen.QBANK, Color(0xFF14B8A6)), // Teal
        AppModule("calendar", "Calendar", Icons.Rounded.CalendarToday, Screen.CALENDAR, Color(0xFFF43F5E)), // Rose
        AppModule("library", "Library", Icons.Rounded.LocalLibrary, Screen.LIBRARY, Color(0xFFF97316)), // Orange
        AppModule("payments", "Payments", Icons.Rounded.Payment, Screen.PAYMENTS, Color(0xFF06B6D4)), // Cyan
        AppModule("hostel", "Hostel Hub", Icons.Rounded.HomeWork, Screen.HOSTEL, Color(0xFF84CC16)), // Lime
        AppModule("transport", "Transport", Icons.Rounded.DirectionsBus, Screen.TRANSPORT, Color(0xFFEAB308)), // Yellow
        AppModule("social", "Social", Icons.Rounded.People, Screen.SOCIAL, Color(0xFF3B82F6)) // Blue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "App Library",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(modules) { module ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppState.navigateTo(module.screen)
                                onDismiss()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.elevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = module.icon,
                                contentDescription = module.name,
                                tint = module.color,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = module.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
