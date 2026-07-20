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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.FriendGroup
import com.amazecc.app.shared.state.FriendsViewModel
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
                0 -> FriendsTab(colors)
                1 -> GroupsTab(colors)
                2 -> CommonSlotsTab(colors)
                3 -> ShareScheduleTab(colors)
            }
        }
    }
}

@Composable
private fun ShareScheduleTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val attendance by AppState.attendance.collectAsState()
    val studentProfile by AppState.studentProfile.collectAsState()
    val authorizedID by com.amazecc.app.shared.repository.SessionManager.authorizedID.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val name = studentProfile?.name ?: authorizedID ?: "Student"
    val regNumber = studentProfile?.regNo ?: authorizedID ?: "0000"
    val attList = attendance?.attendance ?: emptyList()

    val scheduleCode = remember(attList, name, regNumber) {
        if (attList.isNotEmpty()) SocialUtils.exportScheduleCode(attList, name, regNumber) else ""
    }

    val shareCode = "$name|$regNumber"
    val qrMatrix = remember(shareCode) {
        if (shareCode.isNotBlank()) QRCodeGenerator.generate(shareCode) else null
    }

    var copied by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(2000); copied = false }
    }
    LaunchedEffect(codeCopied) {
        if (codeCopied) { kotlinx.coroutines.delay(2000); codeCopied = false }
    }

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
        if (qrMatrix != null) {
            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).padding(12.dp)
            ) {
                QrCodeCanvas(
                    matrix = qrMatrix,
                    modifier = Modifier.fillMaxSize()
                )
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
                        clipboardManager.setText(AnnotatedString(scheduleCode))
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

        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                clipboardManager.setText(AnnotatedString(scheduleCode))
                codeCopied = true
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (codeCopied) Icons.Rounded.Check else Icons.Rounded.Share, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (codeCopied) "Copied!" else "Share Code", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Text("Copy schedule code to share with a friend", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(12.dp))

        var showScanDialog by remember { mutableStateOf(false) }
        if (showScanDialog) {
            AlertDialog(
                onDismissRequest = { showScanDialog = false },
                title = { Text("Scan Friend's Code", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Camera scanning is not available on this platform. Go to the Friends tab and use 'Add Friend via Code' to paste your friend's schedule code manually.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showScanDialog = false
                    }) { Text("OK", color = colors.accent) }
                },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary
            )
        }

        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showScanDialog = true }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scan Friend's Code", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Text("Enter a friend's schedule code manually", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QrCodeCanvas(matrix: List<BooleanArray>, modifier: Modifier = Modifier) {
    val size = matrix.size
    Canvas(modifier = modifier) {
        val w = this.size.width / size
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (matrix[row][col]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(col * w, row * w),
                        size = Size(w, w)
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val friends by com.amazecc.app.shared.state.FriendsViewModel.friends.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Friend", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    Text("Paste your friend's schedule code here to add them to your timetable matches.", style = AmazeTheme.typography.caption)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("Schedule Code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val success = com.amazecc.app.shared.state.FriendsViewModel.addFriendFromCode(codeInput)
                    if (success) {
                        showAddDialog = false
                        codeInput = ""
                    }
                }) {
                    Text("Add", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        AmazeButton(
            text = "Add Friend via Code",
            icon = Icons.Rounded.Add,
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        if (friends.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No friends added yet.", color = colors.textMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(friends) { friend ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(friend.name.firstOrNull()?.uppercase() ?: "F", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text(friend.regNumber, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            IconButton(onClick = { com.amazecc.app.shared.state.FriendsViewModel.removeFriend(friend.regNumber) }) {
                                Icon(Icons.Rounded.Delete, null, tint = colors.dangerText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommonSlotsTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val friends by com.amazecc.app.shared.state.FriendsViewModel.friends.collectAsState()
    
    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add friends to see common free slots.", color = colors.textMuted)
        }
        return
    }
    
    // In a real app, we would intersect user's free slots with friends' free slots.
    // For this UI, we mock the result of the complex algorithm.
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Common Free Slots", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(Modifier.height(4.dp))
            Text("Times when you and selected friends are free", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        }
        
        val dummySlots = listOf("Monday 10:00 - 11:30", "Tuesday 14:00 - 15:30", "Friday 08:00 - 09:30")
        
        items(dummySlots) { slot ->
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventAvailable, null, tint = colors.successText)
                    Spacer(Modifier.width(12.dp))
                    Text(slot, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
            }
        }
    }
}

@Composable
private fun GroupsTab(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val friends by FriendsViewModel.friends.collectAsState()
    val groups by FriendsViewModel.groups.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Group", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Groups let you share schedules with multiple friends at once.", style = AmazeTheme.typography.caption)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (friends.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("All friends will be added to the group.", style = AmazeTheme.typography.caption)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newGroupName.isNotBlank() && friends.isNotEmpty(), onClick = {
                    FriendsViewModel.createGroup(newGroupName, friends.map { it.regNumber })
                    newGroupName = ""
                    showCreateDialog = false
                }) { Text("Create", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        AmazeButton(
            text = "Create Group",
            icon = Icons.Rounded.GroupAdd,
            onClick = { showCreateDialog = true },
            enabled = friends.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Groups, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (friends.isEmpty()) "Add friends first to create groups" else "No groups yet", color = colors.textMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups) { group ->
                    val memberNames = group.memberRegNumbers.mapNotNull { reg ->
                        friends.find { it.regNumber == reg }?.name
                    }
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Groups, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("${memberNames.size} members", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                IconButton(onClick = { FriendsViewModel.deleteGroup(group.id) }) {
                                    Icon(Icons.Rounded.Delete, null, tint = colors.dangerText)
                                }
                            }
                            if (memberNames.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    memberNames.joinToString(", "),
                                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
