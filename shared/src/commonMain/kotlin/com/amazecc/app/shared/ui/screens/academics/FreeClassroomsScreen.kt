@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
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
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeDropdown
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import amazecc_app.shared.generated.resources.Res

data class SimpleParsedCourse(
    val code: String,
    val type: String,
    val room: String,
    val slot: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun FreeClassroomsScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    
    val json = Json { ignoreUnknownKeys = true }
    val schema = remember { json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON) }

    val days = listOf("mon" to "Monday", "tue" to "Tuesday", "wed" to "Wednesday", "thu" to "Thursday", "fri" to "Friday")
    var selectedDay by remember { mutableStateOf(days[0].first) }
    
    val timePeriods = remember(schema) {
        schema.theory.mapNotNull { if (it.start.isNotEmpty() && it.end.isNotEmpty()) " - " else null }
    }
    var selectedTime by remember { mutableStateOf(timePeriods.firstOrNull() ?: "") }
    
    val blocks = listOf("All", "AB1", "AB2", "AB3", "SJIT", "TT", "SMV", "SJT", "PRP")
    var selectedBlock by remember { mutableStateOf(blocks[0]) }

    var courses by remember { mutableStateOf<List<SimpleParsedCourse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val parsed = withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/ffcsReport.csv")
                val text = bytes.decodeToString()
                val lines = text.split("\n").drop(1)
                
                lines.mapNotNull { line ->
                    // Naive CSV split ignoring quotes for speed since we only need specific columns
                    // CODE is 1, TYPE is 4, SLOT is 7, VENUE is 8 usually, but FFCS CSV may vary.
                    // Assuming standard format from AmazeCC: ClassNBR, COURSE CODE, TITLE, ...
                    val cols = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.replace("\"", "").trim() }
                    if (cols.size >= 8) {
                        // Looking at React version: CODE, TITLE, TYPE, CREDITS, ROOM/VENUE, SLOT, FACULTY
                        // We will just find them roughly if exact index unknown, or hardcode assuming standard format
                        val code = cols.getOrNull(1) ?: ""
                        val type = cols.getOrNull(2) ?: ""
                        val slot = cols.getOrNull(5) ?: ""
                        val room = cols.getOrNull(4) ?: ""
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
        
        val targetSlots = mutableSetOf<String>()
        val theoryP = schema.theory.find { it.start == reqStart && it.end == reqEnd }
        if (theoryP?.days?.containsKey(selectedDay) == true) targetSlots.add(theoryP.days[selectedDay]!!)
        
        val labP = schema.lab.find { it.start == reqStart && it.end == reqEnd }
        if (labP?.days?.containsKey(selectedDay) == true) targetSlots.add(labP.days[selectedDay]!!)
        
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
        val filtered = if (selectedBlock == "All") free else free.filter { it.startsWith(selectedBlock, ignoreCase = true) }
        
        filtered.sorted().map { Pair(it, roomTypes[it] ?: "Theory") }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                Box(modifier = Modifier.weight(1f)) {
                    AmazeDropdown(
                        label = "Block",
                        selectedOption = selectedBlock,
                        options = blocks,
                        onOptionSelected = { selectedBlock = it }
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableRooms) { (room, type) ->
                        AmazeCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.accent)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = room,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                }
                                Badge(
                                    text = type,
                                    backgroundColor = if (type == "Theory") colors.successSurface else colors.warning.copy(alpha=0.2f),
                                    textColor = if (type == "Theory") colors.successText else colors.warning
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, backgroundColor: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
