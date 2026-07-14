package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.utils.QRCodeGenerator
import com.amazecc.app.shared.utils.SocialUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen() {
    val colors = AmazeTheme.colors
    val tabs = listOf("Friends", "Groups", "Common Slots", "Share Schedule")
    var activeTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Social & Friends",
            description = "Find friends and match timetables",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshCurrentSemester
        )

        TabRow(
            selectedTabIndex = activeTab,
            containerColor = colors.background,
            contentColor = colors.accent
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    text = { Text(tab, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                3 -> ShareScheduleTab(colors)
                else -> PlaceholderTab(tabs[activeTab], colors)
            }
        }
    }
}

@Composable
private fun ShareScheduleTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val attendance by AppState.attendance.collectAsState()
    val studentProfile by AppState.studentProfile.collectAsState()
    val authorizedID by com.amazecc.app.shared.repository.SessionManager.authorizedID.collectAsState()

    val name = studentProfile?.name ?: authorizedID ?: "Student"
    val regNumber = studentProfile?.regNo ?: authorizedID ?: "0000"
    val attList = attendance?.attendance ?: emptyList()

    val scheduleCode = remember(attList, name, regNumber) {
        if (attList.isNotEmpty()) SocialUtils.exportScheduleCode(attList, name, regNumber) else ""
    }

    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Share Your Schedule", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text("Friends can scan this code to see your free slots", color = colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))

        // QR Code display
        if (scheduleCode.isNotEmpty()) {
            val qrMatrix = remember(scheduleCode) { QRCodeGenerator.generate(scheduleCode) }
            val matrixSize = qrMatrix.size

            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellSize = size.width / matrixSize
                    for (row in 0 until matrixSize) {
                        for (col in 0 until matrixSize) {
                            if (qrMatrix[row][col]) {
                                drawRect(Color.Black, topLeft = Offset(col * cellSize, row * cellSize), size = Size(cellSize, cellSize))
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(colors.surface).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QrCode, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No timetable data", color = colors.textMuted, fontSize = 12.sp)
                    Text("Sync your data first", color = colors.textMuted, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Code preview
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        scheduleCode.let { if (it.length > 60) it.take(60) + "..." else it },
                        color = colors.textSecondary, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                AmazeButton(
                    text = if (copied) "Copied!" else "Copy",
                    onClick = {
                        copied = true
                    },
                    variant = if (copied) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Share options
        Text("Share Options", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Share, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Share Code", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Text("Send your schedule code to a friend", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(12.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scan Friend's Code", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Text("Use camera to scan and add a friend", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlaceholderTab(title: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Construction, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("$title coming soon", color = colors.textMuted)
        }
    }
}
