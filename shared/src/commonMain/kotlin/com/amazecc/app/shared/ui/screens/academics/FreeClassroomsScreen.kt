package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.data.CampusSchemas
import com.amazecc.app.shared.model.CampusSchema
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeDropdown
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import amazecc_app.shared.generated.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.bouncySpring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

data class SimpleParsedCourse(
    val code: String,
    val type: String,
    val room: String,
    val slot: String
)

private fun extractBlockName(room: String): String {
    val clean = room.trim().uppercase()
    if (clean.isEmpty() || clean == "NIL" || clean == "N/A" || clean.contains("ONLINE")) return ""
    return when {
        clean.contains("-") -> clean.substringBefore("-").trim()
        clean.contains(" ") -> clean.substringBefore(" ").trim()
        else -> clean.takeWhile { it.isLetter() || it.isDigit() }
    }
}

private fun timeToMinutes(timeStr: String): Int {
    if (timeStr.isEmpty()) return 0
    val parts = timeStr.trim().split(" ")
    if (parts.size < 2) return 0
    val time = parts[0]
    val period = parts[1]
    val timeParts = time.split(":")
    if (timeParts.size < 2) return 0
    var hours = timeParts[0].toIntOrNull() ?: 0
    val minutes = timeParts[1].toIntOrNull() ?: 0
    if (period == "PM" && hours != 12) hours += 12
    if (period == "AM" && hours == 12) hours = 0
    return hours * 60 + minutes
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun FreeClassroomsScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val authId by SessionManager.authorizedID.collectAsState()
    
    if (authId == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Free Classrooms", description = "Find an empty spot to sit", showBackButton = true)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Login Required", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(8.dp))
                    Text("Please login to VTOP to view free classrooms and your timetable slots.", color = colors.textSecondary, modifier = Modifier.padding(bottom = 24.dp))
                    Button(onClick = { AppState.navigateTo(Screen.LOGIN) }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                        Text("Go to Login", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }
    
    val json = Json { ignoreUnknownKeys = true }
    val schema = remember { 
        try {
            json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON) 
        } catch (e: Exception) {
            CampusSchema()
        }
    }

    val days = listOf("mon" to "Monday", "tue" to "Tuesday", "wed" to "Wednesday", "thu" to "Thursday", "fri" to "Friday")
    val timePeriods = remember(schema) {
        schema.theory.mapNotNull { if (it.start.isNotEmpty() && it.end.isNotEmpty() && it.lunch != true) "${it.start} - ${it.end}" else null }
    }
    
    var selectedDay by remember { mutableStateOf(days[0].first) }
    var selectedTime by remember { mutableStateOf(timePeriods.firstOrNull() ?: "") }
    
    LaunchedEffect(Unit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = now.dayOfWeek.isoDayNumber
        if (dayOfWeek in 1..5) {
            val dayIndex = dayOfWeek - 1
            selectedDay = days[dayIndex].first
            
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

    val dynamicBlocks = remember(courses) {
        if (courses.isEmpty()) listOf("ALL")
        else {
            val extracted = courses
                .map { extractBlockName(it.room) }
                .filter { it.isNotBlank() && it.length in 2..8 }
                .toSet()
                .sorted()
            listOf("ALL") + extracted
        }
    }
    var selectedBlock by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val parsed = withContext(Dispatchers.Default) {
                val bytes = try {
                    HttpClient().use { client ->
                        client.get("https://amazecc.vit.ac.in/ffcs/ffcsReport.csv").readBytes()
                    }
                } catch (e: Exception) {
                    try {
                        Res.readBytes("files/ffcsReport.csv")
                    } catch (innerE: Exception) {
                        null
                    }
                }
                
                if (bytes == null) return@withContext emptyList<SimpleParsedCourse>()
                
                val text = bytes.decodeToString()
                val lines = text.split("\n").drop(1)
                
                lines.mapNotNull { line ->
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
                        if (code.isNotEmpty()) SimpleParsedCourse(code, type, room, slot) else null
                    } else null
                }
            }
            courses = parsed
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            loading = false
        }
    }

    val availableRooms = remember(selectedDay, selectedTime, selectedBlock, courses) {
        if (courses.isEmpty() || selectedTime.isEmpty()) return@remember emptyList<Pair<String, String>>()
        
        val reqTimeSplit = selectedTime.split(" - ")
        if (reqTimeSplit.size < 2) return@remember emptyList<Pair<String, String>>()
        val reqStart = reqTimeSplit[0]
        val reqEnd = reqTimeSplit[1]
        val reqStartMin = timeToMinutes(reqStart)
        val reqEndMin = timeToMinutes(reqEnd)
        
        val targetSlots = mutableSetOf<String>()
        schema.theory.forEach { p ->
            if (p.start.isNotEmpty() && p.end.isNotEmpty()) {
                val pStart = timeToMinutes(p.start)
                val pEnd = timeToMinutes(p.end)
                if (pStart < reqEndMin && pEnd > reqStartMin) {
                    if (p.days.containsKey(selectedDay)) {
                        (p.days[selectedDay] ?: return@forEach).split("+").forEach { targetSlots.add(it.trim()) }
                    }
                }
            }
        }
        
        schema.lab.forEach { p ->
            if (p.start.isNotEmpty() && p.end.isNotEmpty()) {
                val pStart = timeToMinutes(p.start)
                val pEnd = timeToMinutes(p.end)
                if (pStart < reqEndMin && pEnd > reqStartMin) {
                    if (p.days.containsKey(selectedDay)) {
                        (p.days[selectedDay] ?: return@forEach).split("+").forEach { targetSlots.add(it.trim()) }
                    }
                }
            }
        }
        
        if (targetSlots.isEmpty()) return@remember emptyList<Pair<String, String>>()
        
        val allRooms = mutableSetOf<String>()
        val occupiedRooms = mutableSetOf<String>()
        val roomTypes = mutableMapOf<String, String>()
        
        for (c in courses) {
            val r = c.room.uppercase()
            if (r.isEmpty() || r == "NIL" || r.contains("ONLINE") || r == "N/A") continue
            allRooms.add(r)
            
            val t = c.type.uppercase()
            val isLab = t.contains("LA") || t == "LO" || c.slot.uppercase().contains("L")
            roomTypes[r] = if (isLab) "Lab" else "Theory"
            
            val cSlots = c.slot.split("+").map { it.trim().uppercase() }
            val isOccupied = targetSlots.any { cSlots.contains(it.uppercase()) }
            if (isOccupied) {
                occupiedRooms.add(r)
            }
        }
        
        val free = allRooms.filter { !occupiedRooms.contains(it) }
        val filtered = if (selectedBlock.uppercase() == "ALL") {
            free
        } else {
            free.filter { room ->
                val extracted = extractBlockName(room)
                extracted.equals(selectedBlock, ignoreCase = true) || room.startsWith(selectedBlock, ignoreCase = true)
            }
        }
        
        filtered.sorted().map { Pair(it, roomTypes[it] ?: "Theory") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Free Classrooms",
            description = "Find an empty spot to sit",
            showBackButton = true,
            showSyncButton = false
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()
            Text(
                "SELECT BLOCK",
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
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dynamicBlocks.forEach { block ->
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
                            text = block,
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AmazeDropdown(
                        label = "Day",
                        selectedOption = days.find { it.first == selectedDay }?.second ?: "",
                        options = days.map { it.second },
                        onOptionSelected = { sel -> selectedDay = days.find { it.second == sel }?.first ?: "mon" }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AmazeDropdown(
                        label = "Time",
                        selectedOption = selectedTime,
                        options = timePeriods,
                        onOptionSelected = { selectedTime = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (availableRooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No free classrooms found for this slot.", color = colors.textMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(availableRooms) { (room, type) ->
                        FreeClassroomCard(room, type, colors)
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
