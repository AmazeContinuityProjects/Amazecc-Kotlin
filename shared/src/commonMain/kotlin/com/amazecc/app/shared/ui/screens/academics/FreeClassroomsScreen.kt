package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.amazecc.app.shared.ui.components.PageHeaderContainer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeClassroomsScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    
    val json = Json { ignoreUnknownKeys = true }
    val schema = remember { json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON) }

    val days = listOf("mon" to "Monday", "tue" to "Tuesday", "wed" to "Wednesday", "thu" to "Thursday", "fri" to "Friday")
    var selectedDay by remember { mutableStateOf(days[0].first) }
    
    val timePeriods = remember(schema) {
        schema.theory.mapNotNull { if (it.start.isNotEmpty() && it.end.isNotEmpty()) "${it.start} - ${it.end}" else null }
    }
    var selectedTime by remember { mutableStateOf(timePeriods.firstOrNull() ?: "") }
    
    val blocks = listOf("All", "AB1", "AB2", "AB3", "SJIT", "TT", "SMV", "SJT", "PRP")
    var selectedBlock by remember { mutableStateOf(blocks[0]) }

    // Mock data for rooms since we can't parse a 5MB CSV in real-time easily without Ktor file downloads or bundling
    val availableRooms = remember(selectedDay, selectedTime, selectedBlock) {
        val mockedRooms = listOf("AB1-201", "AB1-304", "AB2-105", "TT-412", "SJT-505", "SJT-102")
        if (selectedBlock == "All") mockedRooms else mockedRooms.filter { it.startsWith(selectedBlock) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Free Classrooms",
            description = "Find an empty spot to sit"
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
            
            if (availableRooms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No free classrooms found.", color = colors.textMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableRooms) { room ->
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
                                    text = "Theory",
                                    backgroundColor = colors.successSurface,
                                    textColor = colors.successText
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
