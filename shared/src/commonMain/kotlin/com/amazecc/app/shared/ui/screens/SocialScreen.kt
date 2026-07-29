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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.bouncySpring
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
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.utils.QRCodeGenerator
import com.amazecc.app.shared.utils.SocialUtils
import com.amazecc.app.shared.config.SlotMap
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen() {
    val colors = AmazeTheme.colors
    val tabs = listOf("Friends", "Groups", "Common Slots", "Share Schedule")
    var activeTab by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Social & Friends",
            description = "Find friends and match timetables",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshCurrentSemester
        )

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = activeTab == index
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = bouncySpring()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { activeTab = index }
                        )
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        style = AmazeTheme.typography.smallLabel.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) colors.background else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

    val shareCode = scheduleCode
    val qrMatrix = remember(shareCode) {
        if (shareCode.isNotBlank()) QRCodeGenerator.generate(shareCode) else null
    }

    var copied by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(2.seconds); copied = false }
    }
    LaunchedEffect(codeCopied) {
        if (codeCopied) { delay(2.seconds); codeCopied = false }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = BOTTOM_NAV_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        Text("Share Your Schedule", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Friends can scan this code to see your free slots", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center))

        Spacer(Modifier.height(AmazeTheme.spacing.lg))

        // QR Code display
        if (qrMatrix != null) {
            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(Color.White).padding(12.dp)
            ) {
                QrCodeCanvas(
                    matrix = qrMatrix,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(colors.surface).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QrCode, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("No timetable data", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    Text("Sync your data first", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))

        // Code preview
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        scheduleCode.let { if (it.length > 60) it.take(60) + "..." else it },
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary), maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
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

        Spacer(Modifier.height(AmazeTheme.spacing.lg))

        // Share options
        Text("Share Options", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(AmazeTheme.spacing.sm))

        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                clipboardManager.setText(AnnotatedString(scheduleCode))
                codeCopied = true
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (codeCopied) Icons.Rounded.Check else Icons.Rounded.Share, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(if (codeCopied) "Copied!" else "Share Code", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Copy schedule code to share with a friend", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.sm))

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
                    }) { Text(Strings.ok, color = colors.accent) }
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
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("Scan Friend's Code", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Enter a friend's schedule code manually", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
    }
}

@Composable
private fun QrCodeCanvas(matrix: List<BooleanArray>, modifier: Modifier = Modifier) {
    val size = matrix.size
    if (size == 0) return
    Canvas(modifier = modifier) {
        val w = this.size.width / size
        if (w <= 0f) return@Canvas
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
private fun FriendTimetableGrid(
    friend: com.amazecc.app.shared.utils.Friend,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI")
    val dayLabels = mapOf("MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri")
    val fullDayToAbbr = mapOf(
        "Monday" to "MON", "Tuesday" to "TUE", "Wednesday" to "WED",
        "Thursday" to "THU", "Friday" to "FRI", "Saturday" to "SAT", "Sunday" to "SUN"
    )
    val standardSlots = listOf(
        "MON" to listOf("A1", "F1", "D1", "TB1", "TG1", "A2", "F2", "D2", "TB2", "TG2"),
        "TUE" to listOf("B1", "G1", "E1", "TC1", "TAA1", "B2", "G2", "E2", "TC2", "TAA2"),
        "WED" to listOf("C1", "A1", "F1", "TD1", "TBB1", "C2", "A2", "F2", "TD2", "TBB2"),
        "THU" to listOf("D1", "B1", "G1", "TE1", "TCC1", "D2", "B2", "G2", "TE2", "TCC2"),
        "FRI" to listOf("E1", "C1", "TA1", "TF1", "TDD1", "E2", "C2", "TA2", "TF2", "TDD2")
    )

    val friendSlotIdsByDay = remember(friend) {
        val map = mutableMapOf<String, Set<String>>()
        friend.classSlots.forEach { slot ->
            val abbr = fullDayToAbbr[slot.day] ?: return@forEach
            map[abbr] = (map[abbr] ?: emptySet()) + slot.slotId
        }
        map
    }

    val freeColor = colors.success

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(52.dp))
                weekDays.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(dayLabels[day] ?: day, style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            standardSlots.firstOrNull()?.second?.forEachIndexed { idx, _ ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    val firstDaySlots = standardSlots.first().second
                    val slotCode = firstDaySlots.getOrNull(idx) ?: ""
                    val timeRange = SlotMap.map["MON"]?.get(slotCode) ?: ""
                    Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                        Text(timeRange, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                    weekDays.forEach { day ->
                        val daySlots = standardSlots.find { it.first == day }?.second
                        val daySlotCode = daySlots?.getOrNull(idx) ?: ""
                        val hasSlot = friendSlotIdsByDay[day]?.contains(daySlotCode) == true
                        val cellColor = if (hasSlot) colors.danger.copy(alpha = 0.18f) else freeColor.copy(alpha = 0.12f)
                        val borderColor = if (hasSlot) colors.danger.copy(alpha = 0.35f) else freeColor.copy(alpha = 0.25f)
                        Box(
                            modifier = Modifier
                                .weight(1f).padding(1.5.dp).height(18.dp)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(cellColor)
                                .border(0.5.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.xs)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasSlot) {
                                Text(daySlotCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(freeColor.copy(alpha = 0.2f)).border(0.5.dp, freeColor.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.xs)))
                    Spacer(Modifier.width(AmazeTheme.spacing.xs))
                    Text("Free", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.danger.copy(alpha = 0.2f)).border(0.5.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.xs)))
                    Spacer(Modifier.width(AmazeTheme.spacing.xs))
                    Text("Class", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
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
    
    var selectedFriend by remember { mutableStateOf<com.amazecc.app.shared.utils.Friend?>(null) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Friend", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    Text("Paste your friend's schedule code here to add them to your timetable matches.", style = AmazeTheme.typography.caption)
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
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
                TextButton(onClick = { showAddDialog = false }) { Text(Strings.cancel, color = colors.textSecondary) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (selectedFriend != null) {
        AlertDialog(
            onDismissRequest = { selectedFriend = null },
            title = { Text("${selectedFriend!!.name}'s Timetable", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FriendTimetableGrid(friend = selectedFriend!!, colors = colors)
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                    Text("Course Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    selectedFriend!!.classSlots.sortedBy { listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday").indexOf(it.day) }.forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.background).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(slot.courseCode, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text(slot.courseTitle, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(slot.slotId, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Text(slot.venue, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFriend = null }) { Text(Strings.close, color = colors.accent) }
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
        
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        
        if (friends.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No friends added yet.", color = colors.textMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                items(friends, key = { it.regNumber }) { friend ->
                    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { selectedFriend = friend }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(friend.name.firstOrNull()?.uppercase() ?: "F", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
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
    val attendance by AppState.attendance.collectAsState()
    
    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add friends to see common free slots.", color = colors.textMuted)
        }
        return
    }
    
    val attList = attendance?.attendance ?: emptyList()
    var commonSlots by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(attList, friends) {
        commonSlots = SocialUtils.getCommonFreeSlots(attList, friends)
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
        item {
            Text("Common Free Slots", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Text("Times when you and ALL added friends are free", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        }
        
        items(commonSlots, key = { it }) { slot ->
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (slot.startsWith("No ")) {
                        Icon(Icons.Rounded.EventBusy, null, tint = colors.dangerText)
                    } else {
                        Icon(Icons.Rounded.EventAvailable, null, tint = colors.successText)
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
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
    var selectedRegNumbers by remember { mutableStateOf(friends.map { it.regNumber }) }
    var editingGroup by remember { mutableStateOf<FriendGroup?>(null) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Groups let you share schedules with multiple friends at once.", style = AmazeTheme.typography.caption)
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (friends.isNotEmpty()) {
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Select members:", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        friends.forEach { friend ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                selectedRegNumbers = if (friend.regNumber in selectedRegNumbers)
                                    selectedRegNumbers - friend.regNumber else selectedRegNumbers + friend.regNumber
                            }.padding(vertical = 4.dp)) {
                                Checkbox(
                                    checked = friend.regNumber in selectedRegNumbers,
                                    onCheckedChange = { checked ->
                                        selectedRegNumbers = if (checked) selectedRegNumbers + friend.regNumber
                                        else selectedRegNumbers - friend.regNumber
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.accent)
                                )
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Text(friend.name, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newGroupName.isNotBlank() && selectedRegNumbers.isNotEmpty(), onClick = {
                    FriendsViewModel.createGroup(newGroupName, selectedRegNumbers)
                    newGroupName = ""
                    selectedRegNumbers = friends.map { it.regNumber }
                    showCreateDialog = false
                }) { Text("Create", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(Strings.cancel, color = colors.textSecondary) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (editingGroup != null) {
        val group = editingGroup!!
        var editMembers by remember(group.id) { mutableStateOf(group.memberRegNumbers) }
        AlertDialog(
            onDismissRequest = { editingGroup = null },
            title = { Text("Edit ${group.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Add or remove members:", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    friends.forEach { friend ->
                        val isMember = friend.regNumber in editMembers
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            editMembers = if (isMember) editMembers - friend.regNumber else editMembers + friend.regNumber
                        }.padding(vertical = 4.dp)) {
                            Checkbox(
                                checked = isMember,
                                onCheckedChange = { checked ->
                                    editMembers = if (checked) editMembers + friend.regNumber else editMembers - friend.regNumber
                                },
                                colors = CheckboxDefaults.colors(checkedColor = colors.accent)
                            )
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(friend.name, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Update group: remove members not in editMembers, add new ones
                    editMembers.forEach { reg ->
                        if (reg !in group.memberRegNumbers) FriendsViewModel.addFriendToGroup(group.id, reg)
                    }
                    group.memberRegNumbers.forEach { reg ->
                        if (reg !in editMembers) FriendsViewModel.removeFriendFromGroup(group.id, reg)
                    }
                    editingGroup = null
                }) { Text(Strings.save, color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { editingGroup = null }) { Text(Strings.cancel, color = colors.textSecondary) }
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
            onClick = {
                selectedRegNumbers = friends.map { it.regNumber }
                showCreateDialog = true
            },
            enabled = friends.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(AmazeTheme.spacing.md))

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Groups, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text(if (friends.isEmpty()) "Add friends first to create groups" else "No groups yet", color = colors.textMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                items(groups, key = { it.id }) { group ->
                    val memberNames = group.memberRegNumbers.mapNotNull { reg ->
                        friends.find { it.regNumber == reg }?.name
                    }
                    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { editingGroup = group }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Groups, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("${memberNames.size} members", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                IconButton(onClick = { FriendsViewModel.deleteGroup(group.id) }) {
                                    Icon(Icons.Rounded.Delete, null, tint = colors.dangerText)
                                }
                            }
                            if (memberNames.isNotEmpty()) {
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
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

