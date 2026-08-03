package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DoorSliding
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.data.CampusSchemas
import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.model.CampusSchema
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

data class SimpleParsedCourse(
    val code: String,
    val type: String,
    val room: String,
    val slot: String
)

private fun extractBlockName(room: String): String {
    val clean = room.trim().uppercase().replace("\r", "")
    if (clean.isEmpty() || clean == "NIL" || clean == "N/A" || clean.contains("ONLINE")) return ""
    return when {
        clean.contains("-") -> clean.substringBefore("-").trim()
        clean.contains(" ") -> clean.substringBefore(" ").trim()
        else -> clean.takeWhile { it.isLetter() || it.isDigit() }
    }
}

private fun timeToMinutes(timeStr: String): Int {
    if (timeStr.isEmpty()) return 0
    val parts = timeStr.trim().replace("\r", "").split(" ")
    if (parts.isEmpty()) return 0
    val time = parts[0]
    val period = parts.getOrNull(1) ?: ""
    val timeParts = time.split(":")
    if (timeParts.isEmpty()) return 0
    var hours = timeParts[0].toIntOrNull() ?: 0
    val minutes = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
    if (period.equals("PM", ignoreCase = true) && hours != 12) hours += 12
    if (period.equals("AM", ignoreCase = true) && hours == 12) hours = 0
    return hours * 60 + minutes
}

private fun parseCsv(text: String): List<SimpleParsedCourse> {
    val lines = text.replace("\r", "").split("\n").drop(1)
    return lines.mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        var inQuotes = false
        val cols = mutableListOf<String>()
        val current = StringBuilder()
        for (char in line) {
            if (char == '\"') {
                inQuotes = !inQuotes
            } else if (char == ',' && !inQuotes) {
                cols.add(current.toString().trim())
                current.clear()
            } else {
                current.append(char)
            }
        }
        cols.add(current.toString().trim())

        if (cols.size >= 7) {
            val code = cols.getOrNull(0) ?: ""
            val type = cols.getOrNull(2) ?: ""
            val slot = cols.getOrNull(4) ?: ""
            val room = cols.getOrNull(6) ?: ""
            if (code.isNotEmpty() && room.isNotEmpty()) SimpleParsedCourse(code, type, room, slot) else null
        } else null
    }
}

@Composable
fun FreeClassroomsScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val json = Json { ignoreUnknownKeys = true }

    val schema = remember {
        try {
            json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON)
        } catch (_: Exception) {
            CampusSchema()
        }
    }

    val days = listOf(
        "mon" to "Monday",
        "tue" to "Tuesday",
        "wed" to "Wednesday",
        "thu" to "Thursday",
        "fri" to "Friday"
    )

    val timePeriods = remember(schema) {
        val periods = schema.theory.mapNotNull {
            if (it.start.isNotEmpty() && it.end.isNotEmpty() && it.lunch != true) "${it.start} - ${it.end}" else null
        }.distinct()
        if (periods.isNotEmpty()) periods else listOf(
            "8:00 AM - 8:50 AM",
            "8:55 AM - 9:45 AM",
            "9:50 AM - 10:40 AM",
            "10:45 AM - 11:35 AM",
            "11:40 AM - 12:30 PM",
            "2:00 PM - 2:50 PM",
            "2:55 PM - 3:45 PM",
            "3:50 PM - 4:40 PM",
            "4:45 PM - 5:35 PM",
            "5:40 PM - 6:30 PM"
        )
    }

    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val todayIsoDay = now.dayOfWeek.isoDayNumber
    val currentDayKey = if (todayIsoDay in 1..5) days[todayIsoDay - 1].first else "mon"

    val currentSlotPeriod = remember(schema, now) {
        val nowMinutes = now.hour * 60 + now.minute
        var found = ""
        for (p in schema.theory) {
            if (p.start.isNotEmpty() && p.end.isNotEmpty() && p.lunch != true) {
                val startMins = timeToMinutes(p.start)
                val endMins = timeToMinutes(p.end)
                if (nowMinutes in (startMins - 15)..endMins) {
                    found = "${p.start} - ${p.end}"
                    break
                }
            }
        }
        found.ifEmpty { timePeriods.first() }
    }

    var selectedDay by remember { mutableStateOf(currentDayKey) }
    var selectedTime by remember { mutableStateOf(currentSlotPeriod) }
    var searchRoomQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, Theory, Lab
    var selectedBlock by remember { mutableStateOf("ALL") }

    var courses by remember { mutableStateOf<List<SimpleParsedCourse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        loading = true
        loadError = false
        try {
            val parsed = withContext(Dispatchers.Default) {
                parseCsv(FfcsReportData.CSV_DATA)
            }
            courses = parsed
            if (parsed.isEmpty()) loadError = true
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = true
        } finally {
            loading = false
        }
    }

    // Calculate free rooms by block
    data class RoomCounts(val theory: Int, val lab: Int)

    val freeRoomsByBlock = remember(selectedDay, selectedTime, courses, schema) {
        if (courses.isEmpty() || selectedTime.isEmpty()) return@remember emptyMap<String, Pair<List<String>, List<String>>>()

        val reqTimeSplit = selectedTime.split(" - ")
        if (reqTimeSplit.size < 2) return@remember emptyMap<String, Pair<List<String>, List<String>>>()
        val reqStart = reqTimeSplit[0].trim()
        val reqEnd = reqTimeSplit[1].trim()

        val reqStartMins = timeToMinutes(reqStart)
        val reqEndMins = timeToMinutes(reqEnd)

        val targetSlots = mutableSetOf<String>()

        for (p in schema.theory) {
            val pStart = timeToMinutes(p.start)
            val pEnd = timeToMinutes(p.end)
            if (pStart < reqEndMins && pEnd > reqStartMins) {
                val slotsStr = p.days[selectedDay] ?: ""
                slotsStr.split("+").forEach { targetSlots.add(it.trim().uppercase()) }
            }
        }

        for (p in schema.lab) {
            val pStart = timeToMinutes(p.start)
            val pEnd = timeToMinutes(p.end)
            if (pStart < reqEndMins && pEnd > reqStartMins) {
                val slotsStr = p.days[selectedDay] ?: ""
                slotsStr.split("+").forEach { targetSlots.add(it.trim().uppercase()) }
            }
        }

        if (targetSlots.isEmpty()) return@remember emptyMap<String, Pair<List<String>, List<String>>>()

        val allRooms = mutableSetOf<String>()
        val occupiedRooms = mutableSetOf<String>()
        val roomCounts = mutableMapOf<String, RoomCounts>()

        for (c in courses) {
            val r = c.room.uppercase().trim()
            if (r.isEmpty() || r == "NIL" || r.contains("ONLINE") || r == "N/A" || r == "UNK-UNK") continue
            allRooms.add(r)

            val current = roomCounts.getOrPut(r) { RoomCounts(0, 0) }
            val t = c.type.uppercase()
            if (t.contains("LA") || t == "LO" || c.slot.uppercase().contains("L")) {
                roomCounts[r] = RoomCounts(current.theory, current.lab + 1)
            } else {
                roomCounts[r] = RoomCounts(current.theory + 1, current.lab)
            }

            val cSlots = c.slot.split("+").map { it.trim().uppercase() }
            if (targetSlots.any { tSlot -> cSlots.contains(tSlot) }) {
                occupiedRooms.add(r)
            }
        }

        val freeRooms = allRooms.filter { !occupiedRooms.contains(it) }

        val grouped = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for (room in freeRooms) {
            val block = extractBlockName(room)
            val bk = if (block.isNotBlank()) block else "OTHER"
            val counts = roomCounts[room] ?: RoomCounts(0, 0)
            val type = if (counts.lab > counts.theory) "Lab" else "Theory"
            grouped.getOrPut(bk) { mutableListOf() }.add(room to type)
        }

        grouped.mapValues { (_, rooms) ->
            val theory = rooms.filter { it.second == "Theory" }.map { it.first }.sorted()
            val lab = rooms.filter { it.second == "Lab" }.map { it.first }.sorted()
            theory to lab
        }
    }

    val freeRoomsTotal = remember(freeRoomsByBlock) {
        freeRoomsByBlock.values.sumOf { (theory, lab) -> theory.size + lab.size }
    }

    val dynamicBlocks = remember(freeRoomsByBlock) {
        if (freeRoomsByBlock.isEmpty()) listOf("ALL" to 0)
        else {
            val list = freeRoomsByBlock.entries.map { (block, rooms) ->
                block to (rooms.first.size + rooms.second.size)
            }.sortedByDescending { it.second }
            listOf("ALL" to freeRoomsTotal) + list
        }
    }

    val filteredRooms = remember(freeRoomsByBlock, selectedBlock, selectedTypeFilter, searchRoomQuery) {
        val filtered = if (selectedBlock.equals("ALL", ignoreCase = true)) {
            freeRoomsByBlock
        } else {
            freeRoomsByBlock.filterKeys { it.equals(selectedBlock, ignoreCase = true) }
        }

        val result = mutableListOf<Pair<String, String>>()
        for ((_, rooms) in filtered) {
            val (theory, lab) = rooms
            if (selectedTypeFilter == "ALL" || selectedTypeFilter == "Theory") {
                theory.forEach { room -> result.add(room to "Theory") }
            }
            if (selectedTypeFilter == "ALL" || selectedTypeFilter == "Lab") {
                lab.forEach { room -> result.add(room to "Lab") }
            }
        }

        if (searchRoomQuery.isNotBlank()) {
            result.filter { (room, _) -> room.contains(searchRoomQuery, ignoreCase = true) }
        } else {
            result
        }
    }

    val isCurrentSlotSelected = selectedDay == currentDayKey && selectedTime == currentSlotPeriod

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Free Classrooms",
            description = "Find live available empty rooms on campus",
            showBackButton = true,
            showSyncButton = false,
            onBackOverride = onBack
        )

        // Single Top-Level LazyVerticalGrid for FULL PAGE SCROLLING
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeaderSpacer()
            }

            // ── Live Status Banner ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isCurrentSlotSelected) Color(0xFF22C55E).copy(alpha = 0.15f) else colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrentSlotSelected) Icons.Rounded.NearMe else Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = if (isCurrentSlotSelected) Color(0xFF22C55E) else colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCurrentSlotSelected) "LIVE NOW" else "SELECTED SLOT",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentSlotSelected) Color(0xFF22C55E) else colors.accent,
                                        fontSize = 10.sp
                                    )
                                )
                                if (isCurrentSlotSelected) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                }
                            }
                            Text(
                                text = "$freeRoomsTotal Free Rooms",
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Text(
                                text = "${days.find { it.first == selectedDay }?.second ?: "Monday"} · $selectedTime",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                        if (!isCurrentSlotSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f), CircleShape)
                                    .clickable {
                                        selectedDay = currentDayKey
                                        selectedTime = currentSlotPeriod
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "FREE NOW",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = Color(0xFF22C55E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Day Selector Bar (Segmented Tabs) ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    days.forEach { (key, label) ->
                        val isSelected = selectedDay == key
                        val isToday = key == currentDayKey
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
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border,
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedDay = key }
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label.take(3).uppercase(),
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.background else colors.textPrimary,
                                        fontSize = 11.sp
                                    )
                                )
                                if (isToday && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Time Slot Chips (Horizontal Scrollable) ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timePeriods.forEach { period ->
                        val isSelected = selectedTime == period
                        val isCurrentSlot = period == currentSlotPeriod && selectedDay == currentDayKey
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = bouncySpring()
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(
                                    when {
                                        isSelected -> colors.accent
                                        isCurrentSlot -> Color(0xFF22C55E).copy(alpha = 0.15f)
                                        else -> colors.surface
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isSelected -> colors.accent
                                        isCurrentSlot -> Color(0xFF22C55E).copy(alpha = 0.4f)
                                        else -> colors.border
                                    },
                                    RoundedCornerShape(AmazeTheme.radius.small)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedTime = period }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = period,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = when {
                                        isSelected -> colors.background
                                        isCurrentSlot -> Color(0xFF22C55E)
                                        else -> colors.textSecondary
                                    },
                                    fontWeight = if (isSelected || isCurrentSlot) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // ── Search & Type Segmented Filters ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AmazeTextField(
                            value = searchRoomQuery,
                            onValueChange = { searchRoomQuery = it },
                            label = "",
                            placeholder = "Search room (e.g. 101, AB1)...",
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = if (searchRoomQuery.isNotEmpty()) {
                                {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        contentDescription = "Clear",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp).clickable { searchRoomQuery = "" }
                                    )
                                }
                            } else null
                        )
                    }

                    // Type selector chips (ALL, Theory, Lab)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("ALL", "Theory", "Lab").forEach { typeOpt ->
                            val isSelected = selectedTypeFilter == typeOpt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surface)
                                    .border(
                                        1.dp,
                                        if (isSelected) colors.accent else colors.border,
                                        RoundedCornerShape(AmazeTheme.radius.small)
                                    )
                                    .clickable { selectedTypeFilter = typeOpt }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = typeOpt,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = if (isSelected) colors.accent else colors.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Building Block Pills ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dynamicBlocks.forEach { (block, count) ->
                        val isSelected = selectedBlock.equals(block, ignoreCase = true)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = bouncySpring()
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border,
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedBlock = block }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (block == "ALL") "ALL ($count)" else "$block ($count)",
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = if (isSelected) colors.background else colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // ── Results Summary Header ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else if (loadError) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.MeetingRoom, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("Unable to load classroom data", color = colors.textSecondary, fontWeight = FontWeight.Bold)
                            Text("No course timetable data available", color = colors.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(AmazeTheme.spacing.md))
                            OutlinedButton(onClick = { refreshTrigger++ }) {
                                Text(Strings.retry)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredRooms.size} FREE CLASSROOMS",
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                fontSize = 11.sp
                            )
                        )
                        IconButton(
                            onClick = { refreshTrigger++ },
                            modifier = Modifier.size(28.dp).background(colors.accent.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = Strings.refresh, tint = colors.accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (!loading && !loadError) {
                if (filteredRooms.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.DoorSliding, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                Text("No free classrooms found", color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                Text("Try selecting a different time slot or building block.", color = colors.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    itemsIndexed(filteredRooms, key = { idx, pair -> "${pair.first}-${pair.second}-$idx" }) { _, (room, type) ->
                        FreeClassroomGridCard(room = room, type = type, colors = colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomGridCard(
    room: String,
    type: String,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = bouncySpring()
    )

    val isLab = type.equals("Lab", ignoreCase = true)
    val block = extractBlockName(room)
    val cardColor = if (isLab) Color(0xFFA855F7) else Color(0xFF22C55E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(cardColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLab) Icons.Rounded.Computer else Icons.Rounded.DoorSliding,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    AmazeBadge(
                        text = if (isLab) "Lab" else "Theory",
                        variant = if (isLab) BadgeVariant.WARNING else BadgeVariant.SUCCESS
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = room,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (block.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Block: $block",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
