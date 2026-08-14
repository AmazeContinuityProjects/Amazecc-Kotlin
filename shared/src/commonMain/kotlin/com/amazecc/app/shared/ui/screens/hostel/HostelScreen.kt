package com.amazecc.app.shared.ui.screens.hostel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.HostelInfo
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HostelScreen() {
    val colors = AmazeTheme.colors
    val hostelRes by AppState.hostelDetails.collectAsState()
    val hostelInfo = hostelRes?.hostelInfo
    val leaves = hostelRes?.leaveHistory ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp)
            ) {
                HostelDetailsSection(hostelInfo, leaves)
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                HostelMessSection()
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                HostelLaundrySection()
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                HostelCounsellingSection()
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))
            }
        }
    }
}

@Composable
fun ExpandableSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    val colors = AmazeTheme.colors
    var expanded by remember { mutableStateOf(false) }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Text(title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.textMuted
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    content()
                }
            }
        }
    }
}

@Composable
fun HostelDetailsSection(hostelInfo: HostelInfo?, leaves: List<com.amazecc.app.shared.model.LeaveItem>) {
    val colors = AmazeTheme.colors
    
    if (hostelInfo == null) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.large)).background(colors.chart3.copy(alpha = 0.15f)).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Hostel information not available. Pull down to sync.", color = colors.textSecondary)
        }
        return
    }

    HeroCard(colors = colors, tint = colors.chart3, modifier = Modifier.fillMaxWidth(), spacing = 0.dp) { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(p.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Apartment, null, tint = p.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Text("Hostel Allocation", color = p.textSecondary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sectionGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Block / Room", style = AmazeTheme.typography.caption.copy(color = p.textSecondary))
                Text(
                    if (hostelInfo.blockName.isNullOrEmpty()) "N/A"
                    else "${hostelInfo.blockName} / ${hostelInfo.roomNo}",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = p.text)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Gender", style = AmazeTheme.typography.caption.copy(color = p.textSecondary))
                val gender = hostelInfo.gender
                Text(if (gender.isNullOrEmpty()) "N/A" else gender, style = AmazeTheme.typography.body.copy(color = p.text, fontWeight = FontWeight.SemiBold))
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
        Text("Mess Facility", style = AmazeTheme.typography.caption.copy(color = p.textSecondary))
        val messInfo = hostelInfo.messInfo
        Text(if (messInfo.isNullOrEmpty()) "Not Enrolled" else messInfo, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = p.text))
    }

    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
    
    ExpandableSection("Outing & Leave History", Icons.Rounded.DirectionsWalk) {
        if (leaves.isEmpty()) {
            Text("No leaves applied.", color = colors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val grouped = leaves.groupBy { leave ->
                val from = parseLeaveDate(leave.from)
                val to = parseLeaveDate(leave.to)
                val status = leave.status?.uppercase() ?: ""
                when {
                    from != null && to != null && from <= now && now <= to -> "Active"
                    from != null && from > now -> "Upcoming"
                    status.contains("PENDING") || status.contains("REQUESTED") || status.contains("APPLIED") -> "Pending"
                    else -> "Past"
                }
            }
            listOf("Active", "Upcoming", "Pending", "Past").forEach { group ->
                val items = grouped[group] ?: return@forEach
                if (items.isEmpty()) return@forEach
                Text(
                    group.uppercase(),
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                items.forEach { leave ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(leave.leaveType ?: "Leave", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            val status = leave.status?.uppercase() ?: ""
                            AmazeBadge(
                                text = leave.status ?: "PENDING",
                                variant = when {
                                    status.contains("APPROVED") || status.contains("COMPLETED") || group == "Active" -> BadgeVariant.SUCCESS
                                    status.contains("CANCELLED") || status.contains("REJECTED") -> BadgeVariant.INFO
                                    else -> BadgeVariant.WARNING
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text("Destination: ${leave.visitPlace ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("Reason: ${leave.reason ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                        Text("Period: ${leave.from} to ${leave.to}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private fun parseLeaveDate(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.trim().split(" ")
    if (parts.size != 2) return null
    val dateParts = parts[0].split("-")
    val timeParts = parts[1].split(":")
    if (dateParts.size != 3 || timeParts.size != 2) return null
    val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )
    val day = dateParts[0].toIntOrNull() ?: return null
    val month = months[dateParts[1].uppercase()] ?: return null
    val year = dateParts[2].toIntOrNull() ?: return null
    val hour = timeParts[0].toIntOrNull() ?: 0
    val minute = timeParts[1].toIntOrNull() ?: 0
    return try {
        LocalDateTime(year, month, day, hour, minute)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostelMessSection() {
    val colors = AmazeTheme.colors
    val hostelRes by AppState.hostelDetails.collectAsState()
    val menuRes by AppState.messMenu.collectAsState()
    val hostelInfo = hostelRes?.hostelInfo

    val dayNames = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    val mealTimes = listOf("7:30 - 9:00 AM", "12:30 - 2:00 PM", "4:30 - 5:30 PM", "7:30 - 9:00 PM")
    val todayIndex = try {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal.coerceIn(0, 6)
    } catch (_: Exception) { 0 }
    val now = try {
        val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        t.hour * 60 + t.minute
    } catch (_: Exception) { -1 }
    val activeMeal = when (now) {
        in 450..540 -> 0
        in 750..840 -> 1
        in 990..1050 -> 2
        in 1170..1260 -> 3
        else -> -1
    }
    val weekOfMonth = try {
        val day = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfMonth
        ((day - 1) / 7) + 1
    } catch (_: Exception) { 1 }

    var selectedDay by remember { mutableStateOf(todayIndex) }
    var selectedType by remember {
        mutableStateOf(
            when {
                hostelInfo?.messInfo?.contains("SPECIAL", ignoreCase = true) == true -> 2
                hostelInfo?.messInfo?.contains("NON", ignoreCase = true) == true -> 1
                else -> 0
            }
        )
    }

    val typeLabels = listOf("Veg", "Non-Veg", "Special")
    val gender = hostelInfo?.gender
    val typeKey = if (selectedType == 1) "NON-VEG" else if (selectedType == 2) "SPECIAL" else "VEG"

    LaunchedEffect(hostelInfo?.messInfo) {
        if (hostelInfo?.messInfo != null) {
            selectedType = when {
                hostelInfo.messInfo.contains("SPECIAL", ignoreCase = true) -> 2
                hostelInfo.messInfo.contains("NON", ignoreCase = true) -> 1
                else -> 0
            }
        }
    }

    LaunchedEffect(gender, typeKey) {
        AppState.refreshMessMenu(gender, typeKey)
    }

    ExpandableSection("Mess Menu", Icons.Rounded.RestaurantMenu) {
        if (hostelInfo?.isHosteller == false) {
            Text(
                "Mess menu is available for hostel residents only.",
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@ExpandableSection
        }

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            typeLabels.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedType == index,
                    onClick = { selectedType = index },
                    label = { Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                        containerColor = colors.surface, labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.border, selectedBorderColor = Color.Transparent,
                        enabled = true, selected = selectedType == index
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        val days = menuRes?.list ?: emptyList()
        if (days.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayNames.forEachIndexed { index, day ->
                    FilterChip(
                        selected = selectedDay == index,
                        onClick = { selectedDay = index },
                        label = { Text(day.take(3), style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                            containerColor = colors.surface, labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.border, selectedBorderColor = Color.Transparent,
                            enabled = true, selected = selectedDay == index
                        )
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed { index, day ->
                    FilterChip(
                        selected = selectedDay == index,
                        onClick = { selectedDay = index },
                        label = { Text((day.Day ?: dayNames.getOrElse(index) { "" }).take(3), style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                            containerColor = colors.surface, labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.border, selectedBorderColor = Color.Transparent,
                            enabled = true, selected = selectedDay == index
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        val day = days.getOrNull(selectedDay) ?: days.firstOrNull()
        val meals = listOf(
            day?.Breakfast to "Breakfast",
            day?.Lunch to "Lunch",
            day?.Snacks to "Snacks",
            day?.Dinner to "Dinner"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.chunked(2).forEach { rowMeals ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowMeals.forEach { (raw, mealName) ->
                        val mealIndex = meals.indexOfFirst { it.second == mealName }
                        val isActive = mealIndex == activeMeal
                        val items = parseMealItems(raw, weekOfMonth)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(if (isActive) colors.accent.copy(alpha = 0.12f) else colors.surface)
                                .then(
                                    if (isActive) Modifier.border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.small))
                                    else Modifier
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(mealName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    if (isActive) {
                                        AmazeBadge(text = "Now", variant = BadgeVariant.SUCCESS)
                                    }
                                }
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                Text(mealTimes[mealIndex], style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                                if (items.isEmpty()) {
                                    Text("No items listed.", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                } else {
                                    items.forEachIndexed { i, item ->
                                        Text(
                                            "${i + 1}. $item",
                                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowMeals.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun parseMealItems(raw: String?, weekOfMonth: Int): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val qualifier = Regex("""\s*\((?:[Ww]eeks?\s*)?\d+(?:\s*&\s*\d+)*\)\s*""")
    return raw.split("\n")
        .mapNotNull { line ->
            val weekNumbers = qualifier.findAll(line)
                .flatMap { m ->
                    Regex("""\d+""").findAll(m.value).mapNotNull { it.value.toIntOrNull() }
                }
                .toList()
            if (weekNumbers.isNotEmpty() && weekOfMonth !in weekNumbers) return@mapNotNull null
            val cleaned = qualifier.replace(line, "").trim()
            if (cleaned.isBlank()) return@mapNotNull null
            Regex("""^\d+[.)]\s*""").replace(cleaned, "").trim().ifBlank { null }
        }
}

@Composable
fun HostelLaundrySection() {
    val colors = AmazeTheme.colors
    val hostelRes by AppState.hostelDetails.collectAsState()
    val laundryRes by AppState.laundrySchedule.collectAsState()
    val hostelInfo = hostelRes?.hostelInfo
    val gender = hostelInfo?.gender

    val maleBlocks = listOf("A", "C", "D1", "D2", "E")
    val femaleBlocks = listOf("B", "C")
    val blocks = if (gender.equals("FEMALE", true)) femaleBlocks else maleBlocks

    val defaultBlock = hostelInfo?.blockName?.firstOrNull()?.toString()?.let { prefix ->
        blocks.firstOrNull { it == prefix } ?: blocks.firstOrNull()
    } ?: blocks.firstOrNull() ?: "A"
    var selectedBlock by remember { mutableStateOf(defaultBlock) }

    LaunchedEffect(gender, selectedBlock) {
        AppState.refreshLaundrySchedule(gender, selectedBlock)
    }

    val today = try {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    } catch (_: Exception) { null }
    val todayDate = today?.dayOfMonth

    ExpandableSection("Laundry Schedule", Icons.Rounded.LocalLaundryService) {
        if (hostelInfo?.isHosteller == false) {
            Text(
                "Laundry schedule is available for hostel residents only.",
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@ExpandableSection
        }

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                FilterChip(
                    selected = selectedBlock == block,
                    onClick = { selectedBlock = block },
                    label = { Text("$block-Block", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                        containerColor = colors.surface, labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.border, selectedBorderColor = Color.Transparent,
                        enabled = true, selected = selectedBlock == block
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        val slots = laundryRes?.list ?: emptyList()
        if (slots.isEmpty()) {
            Text("No laundry schedule available right now.", color = colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
            return@ExpandableSection
        }

        val myRoom = hostelInfo?.roomNo?.trim()
        val mySlots = slots.filter { slot ->
            val room = slot.RoomNumber ?: return@filter false
            val parts = room.replace(" ", "").split("-")
            if (parts.size != 2) return@filter false
            val lo = parts[0].toIntOrNull() ?: return@filter false
            val hi = parts[1].toIntOrNull() ?: return@filter false
            val roomNum = myRoom?.toIntOrNull() ?: return@filter false
            roomNum in lo..hi
        }
        val myDates = mySlots.mapNotNull { it.Date?.toIntOrNull() }
        val nextSlot = slots
            .mapNotNull { it.Date?.toIntOrNull() }
            .sorted()
            .firstOrNull { todayDate == null || it >= todayDate }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mySlots.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "Your laundry days: ${myDates.sorted().joinToString(", ")}",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                        mySlots.sortedBy { it.Date?.toIntOrNull() ?: 0 }.forEach { slot ->
                            Text(
                                "${slot.Date} — ${slot.RoomNumber}",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
            }
            if (nextSlot != null) {
                val nextLabel = if (todayDate != null && nextSlot == todayDate) "Today" else "Next: $nextSlot"
                Text(
                    "$nextLabel (day $nextSlot)",
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                )
            }

            val year = today?.year ?: 2026
            val month = today?.month?.ordinal?.plus(1) ?: 1
            val daysInMonth = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            }
            val firstOffset = today?.let {
                LocalDate(it.year, it.month, 1).dayOfWeek.ordinal
            } ?: 0

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${monthName(month)} $year",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    "● Laundry day   ◐ Your day",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                )
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
            val slotDates = slots.mapNotNull { it.Date?.toIntOrNull() }.toSet()
            val headerLabels = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                headerLabels.forEach { label ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                    }
                }
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
            val cells = MutableList(firstOffset) { null } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { weekCells ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekCells.forEach { dayNum ->
                        if (dayNum == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val isLaundryDay = dayNum in slotDates
                            val isMyDay = dayNum in myDates
                            val isToday = dayNum == todayDate
                            val cellModifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                            val bg = when {
                                isMyDay -> colors.accent
                                isLaundryDay -> colors.accent.copy(alpha = 0.15f)
                                else -> colors.surface
                            }
                            val fg = when {
                                isMyDay -> Color.White
                                isLaundryDay -> colors.accent
                                else -> colors.textSecondary
                            }
                            Box(
                                modifier = cellModifier
                                    .background(bg)
                                    .then(
                                        if (isToday) Modifier.border(1.dp, colors.accent.copy(alpha = 0.6f), CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayNum.toString(),
                                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = if (isMyDay || isToday) FontWeight.Bold else FontWeight.Medium, color = fg)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
    5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
    9 -> "September"; 10 -> "October"; 11 -> "November"; else -> "December"
}

@Composable
fun HostelCounsellingSection() {
    val colors = AmazeTheme.colors
    val counsellingRes by AppState.hostelCounselling.collectAsState()

    LaunchedEffect(Unit) {
        AppState.refreshHostelCounselling()
    }

    ExpandableSection("Counselling & Support", Icons.Rounded.SupportAgent) {
        val res = counsellingRes
        if (res != null && res.success && res.tables.isNotEmpty()) {
            res.tables.forEach { table ->
                DataTableCard(table = table, colors = colors)
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.surface)
                    .padding(12.dp)
            ) {
                Text(
                    res?.message?.takeIf { it.isNotBlank() } ?: "Counselling schedule unavailable. Contact your hostel office for details.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                .background(colors.surface)
                .padding(12.dp)
        ) {
            Column {
                Text("Hostel Administration", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                ContactRow(Icons.Rounded.Person, "Chief Warden", "Dr. K. Srinivasan", colors)
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                ContactRow(Icons.Rounded.Phone, "Emergency", "+91 44 3993 1555  |  +91 44 3993 1666  |  +91 44 3993 1108", colors)
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                ContactRow(Icons.Rounded.OpenInNew, "VTOP Portal", "vtop.vit.ac.in", colors)
            }
        }
    }
}

@Composable
private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            Text(value, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
    }
}
