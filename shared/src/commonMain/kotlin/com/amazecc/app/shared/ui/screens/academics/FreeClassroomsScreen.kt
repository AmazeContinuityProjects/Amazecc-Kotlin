package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        } catch (e: Exception) {
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

    var selectedDay by remember { mutableStateOf(days[0].first) }
    var selectedTime by remember { mutableStateOf(timePeriods.first()) }
    var searchRoomQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, Theory, Lab
    var selectedBlock by remember { mutableStateOf("ALL") }

    // Auto-select Current Day and Current Time
    LaunchedEffect(Unit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = now.dayOfWeek.isoDayNumber
        if (dayOfWeek in 1..5) {
            selectedDay = days[dayOfWeek - 1].first
            val nowMinutes = now.hour * 60 + now.minute
            var foundPeriod = ""
            for (p in schema.theory) {
                if (p.start.isNotEmpty() && p.end.isNotEmpty() && p.lunch != true) {
                    val startMins = timeToMinutes(p.start)
                    val endMins = timeToMinutes(p.end)
                    if (nowMinutes in (startMins - 15)..endMins) {
                        foundPeriod = "${p.start} - ${p.end}"
                        break
                    }
                }
            }
            if (foundPeriod.isNotEmpty()) {
                selectedTime = foundPeriod
            }
        }
    }

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

    // Free Rooms Calculation (exact match approach matching reference amazecc)
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
        val selDayUpper = selectedDay.uppercase()

        for (p in schema.theory) {
            val pStart = timeToMinutes(p.start)
            val pEnd = timeToMinutes(p.end)
            if (pStart < reqEndMins && pEnd > reqStartMins) {
                val slotsStr = p.days[selDayUpper] ?: ""
                slotsStr.split("+").forEach { targetSlots.add(it.trim().uppercase()) }
            }
        }
        
        for (p in schema.lab) {
            val pStart = timeToMinutes(p.start)
            val pEnd = timeToMinutes(p.end)
            if (pStart < reqEndMins && pEnd > reqStartMins) {
                val slotsStr = p.days[selDayUpper] ?: ""
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

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Free Classrooms",
            description = "Find empty classrooms by day & time",
            showBackButton = true,
            showSyncButton = false
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            HeaderSpacer()

            // ── Room Search Bar ──
            AmazeTextField(
                value = searchRoomQuery,
                onValueChange = { searchRoomQuery = it },
                label = "",
                placeholder = "Search room number (e.g. 101, AB1)...",
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Day & Time Selectors ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AmazeDropdown(
                        label = "Day",
                        selectedOption = days.find { it.first == selectedDay }?.second ?: "Monday",
                        options = days.map { it.second },
                        onOptionSelected = { sel -> selectedDay = days.find { it.second == sel }?.first ?: "mon" }
                    )
                }
                Box(modifier = Modifier.weight(1.3f)) {
                    AmazeDropdown(
                        label = "Time Slot",
                        selectedOption = selectedTime,
                        options = timePeriods,
                        onOptionSelected = { selectedTime = it }
                    )
                }
                Box(modifier = Modifier.weight(0.9f)) {
                    AmazeDropdown(
                        label = "Type",
                        selectedOption = if (selectedTypeFilter == "ALL") "All Types" else selectedTypeFilter,
                        options = listOf("All Types", "Theory", "Lab"),
                        onOptionSelected = { selectedTypeFilter = if (it == "All Types") "ALL" else it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Building Block Pills with Free Counts ──
            Text(
                "FREE BUILDING BLOCKS",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (block == "ALL") "ALL ($count)" else "$block ($count)",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) colors.background else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (loadError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MeetingRoom, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Unable to load classroom data.", color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        Text("No course timetable data available.", color = colors.textMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { refreshTrigger++ }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                // ── Free Classrooms Results Summary ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredRooms.size} Free Rooms Available",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    IconButton(
                        onClick = { refreshTrigger++ },
                        modifier = Modifier.size(32.dp).background(colors.accent.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.accent, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredRooms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.MeetingRoom, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No free classrooms found.", color = colors.textSecondary, fontWeight = FontWeight.Bold)
                            Text("Try selecting a different time slot or block.", color = colors.textMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(filteredRooms) { (room, type) ->
                            FreeClassroomCard(room, type, colors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomCard(room: String, type: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = room,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AmazeBadge(
                    text = type,
                    variant = if (type == "Theory") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                )
            }
        }
    }
}
