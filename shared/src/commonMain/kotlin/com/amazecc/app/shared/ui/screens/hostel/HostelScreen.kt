package com.amazecc.app.shared.ui.screens.hostel

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HostelScreen() {
    val colors = AmazeTheme.colors
    val hostelDetails by AppState.hostelDetails.collectAsState()
    val hostelLeaves by AppState.hostelLeaves.collectAsState()

    var activeSubTab by remember { mutableStateOf("Details") }
    val tabs = listOf("Details", "Mess Menu", "Laundry", "Counselling")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Hostel Hub",
            description = "Manage mess, outings, laundry & counseling",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshHostel
        )

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()
            // Horizontal scrollable tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeSubTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .clickable { activeSubTab = tab }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold).copy(
                                color = if (isSelected) colors.background else colors.textSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp)
            ) {
                when (activeSubTab) {
                    "Details" -> HostelDetailsTab(hostelDetails, hostelLeaves?.leaves ?: emptyList())
                    "Mess Menu" -> HostelMessTab()
                    "Laundry" -> HostelLaundryTab()
                    "Counselling" -> HostelCounsellingTab()
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun HostelDetailsTab(hostelDetails: com.amazecc.app.shared.model.HostelDetails?, leaves: List<com.amazecc.app.shared.model.LeaveItem>) {
    val colors = AmazeTheme.colors
    
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("HOSTEL BOOKING DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Block / Room", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text(
                        if (hostelDetails?.blockName.isNullOrEmpty()) "N/A" 
                        else "${hostelDetails.blockName} / ${hostelDetails.roomNo}", 
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Gender", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    val gender = hostelDetails?.gender
                    Text(if (gender.isNullOrEmpty()) "N/A" else gender, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Mess Facility", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            val messInfo = hostelDetails?.messInfo
            Text(if (messInfo.isNullOrEmpty()) "Not Enrolled" else messInfo, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Outing & Leave History", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    if (leaves.isEmpty()) {
        Text("No leaves applied.", color = colors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
    } else {
        leaves.forEach { leave ->
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(leave.leaveType ?: "Leave", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        AmazeBadge(
                            text = leave.status ?: "PENDING",
                            variant = if (leave.status == "APPROVED" || leave.status == "COMPLETED") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Destination: ${leave.visitPlace ?: "Ã¢â‚¬â€"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text("Reason: ${leave.reason ?: "Ã¢â‚¬â€"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Period: ${leave.from} to ${leave.to}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                }
            }
        }
    }
}

@Composable
fun HostelMessTab() {
    val colors = AmazeTheme.colors
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val meals = listOf("Breakfast", "Lunch", "Snacks", "Dinner")
    val todayIndex = try {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal.coerceIn(0, 6)
    } catch (_: Exception) { 0 }
    var selectedDay by remember { mutableStateOf(todayIndex) }
    var selectedMeal by remember { mutableStateOf(0) }
    var isVeg by remember { mutableStateOf(true) }

    val menuData = mapOf(
        "Breakfast" to listOf("Idli", "Dosa", "Pongal", "Vada", "Poori", "Upma"),
        "Lunch" to listOf("Steamed Rice", "Sambar", "Rasam", "Curd", "Chapati", "Dal Fry"),
        "Snacks" to listOf("Bajji", "Bonda", "Samosa", "Tea", "Coffee", "Milk"),
        "Dinner" to listOf("Chapati", "Dal", "Jeera Rice", "Mixed Veg Curry", "Papad", "Pickle")
    )

    Text("Mess Menu", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        AmazeButton(
            text = if (isVeg) "Veg" else "Non-Veg",
            onClick = { isVeg = !isVeg },
            variant = if (isVeg) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
            icon = if (isVeg) Icons.Rounded.CheckCircle else null
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = selectedDay == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { selectedDay = index }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = day,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.background else colors.textSecondary
                    )
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        meals.forEachIndexed { index, meal ->
            AmazeButton(
                text = meal,
                onClick = { selectedMeal = index },
                variant = if (selectedMeal == index) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    val currentMeal = meals[selectedMeal]
    val items = menuData[currentMeal] ?: emptyList()
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Text(currentMeal, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(10.dp))
        Column {
            items.chunked(3).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isVeg) colors.successSurface else colors.dangerSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = item,
                                style = AmazeTheme.typography.caption.copy(
                                    color = if (isVeg) colors.successText else colors.dangerText,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Feedback & Requests", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))
    
    var feedbackType by remember { mutableStateOf("Food Quality") }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackSubmitted by remember { mutableStateOf(false) }
    
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        AmazeDropdown(
            options = listOf("Food Quality", "Hygiene", "Mess Change Request", "Other"),
            selectedOption = feedbackType,
            onOptionSelected = { feedbackType = it },
            label = "Request Type"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Message",
            style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = feedbackMessage,
            onValueChange = { feedbackMessage = it },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = { Text("Enter your feedback or request details...", style = AmazeTheme.typography.body.copy(color = colors.textMuted)) },
            shape = RoundedCornerShape(AmazeTheme.radius.small),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accent
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        AmazeButton(
            text = if (feedbackSubmitted) "Submitted Successfully" else "Submit",
            onClick = {
                if (feedbackMessage.isNotBlank()) feedbackSubmitted = true
            },
            variant = if (feedbackSubmitted) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            enabled = feedbackMessage.isNotBlank() && !feedbackSubmitted
        )
        if (feedbackSubmitted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your feedback has been submitted.",
                style = AmazeTheme.typography.caption.copy(color = colors.successText)
            )
        }
    }
}

@Composable
fun HostelLaundryTab() {
    val colors = AmazeTheme.colors
    val blocks = listOf("A-Block", "B-Block", "C-Block", "D-Block")
    var selectedBlock by remember { mutableStateOf(0) }

    data class LaundrySlot(val time: String, val status: String, val capacity: Int, val total: Int)
    val slotData = mapOf(
        "A-Block" to listOf(
            LaundrySlot("8:00 - 10:00", "Available", 8, 10),
            LaundrySlot("10:00 - 12:00", "Booked", 0, 10),
            LaundrySlot("14:00 - 16:00", "Available", 6, 10),
            LaundrySlot("16:00 - 18:00", "Available", 4, 10)
        ),
        "B-Block" to listOf(
            LaundrySlot("8:00 - 10:00", "Booked", 0, 10),
            LaundrySlot("10:00 - 12:00", "Available", 7, 10),
            LaundrySlot("14:00 - 16:00", "Booked", 0, 10),
            LaundrySlot("16:00 - 18:00", "Available", 9, 10)
        ),
        "C-Block" to listOf(
            LaundrySlot("8:00 - 10:00", "Available", 5, 10),
            LaundrySlot("10:00 - 12:00", "Available", 3, 10),
            LaundrySlot("14:00 - 16:00", "Available", 10, 10),
            LaundrySlot("16:00 - 18:00", "Booked", 0, 10)
        ),
        "D-Block" to listOf(
            LaundrySlot("8:00 - 10:00", "Booked", 0, 10),
            LaundrySlot("10:00 - 12:00", "Booked", 0, 10),
            LaundrySlot("14:00 - 16:00", "Available", 2, 10),
            LaundrySlot("16:00 - 18:00", "Available", 6, 10)
        )
    )

    Text("Laundry Schedule", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            val isSelected = selectedBlock == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { selectedBlock = index }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = block,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.background else colors.textSecondary
                    )
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    val currentBlock = blocks[selectedBlock]
    val slots = slotData[currentBlock] ?: emptyList()
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalLaundryService, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(currentBlock, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    slots.forEach { slot ->
        AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(slot.time, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Capacity: ${slot.capacity}/${slot.total}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
                AmazeBadge(
                    text = slot.status,
                    variant = if (slot.status == "Available") BadgeVariant.SUCCESS else BadgeVariant.DANGER
                )
            }
        }
    }
}

@Composable
fun HostelCounsellingTab() {
    val colors = AmazeTheme.colors
    val counsellingTypes = listOf("Academic", "Personal", "Career")
    var selectedType by remember { mutableStateOf(counsellingTypes[0]) }
    var description by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    data class CounsellingRequest(val type: String, val description: String, val date: String, val status: String)
    val previousRequests = listOf(
        CounsellingRequest("Academic", "Difficulty understanding DSA concepts", "2026-07-10", "Resolved"),
        CounsellingRequest("Career", "Guidance on internship opportunities", "2026-06-28", "Scheduled"),
        CounsellingRequest("Personal", "Stress management consultation", "2026-06-15", "Completed")
    )

    Text("Faculty Advisor", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(colors.accent.copy(alpha = 0.1f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Dr. Rajesh Kumar", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Professor, CSE Department", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Email, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("rajesh.kumar@vit.ac.in", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Phone, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+91-9876543210", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Request Counselling", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        AmazeDropdown(
            options = counsellingTypes,
            selectedOption = selectedType,
            onOptionSelected = { selectedType = it },
            label = "Counselling Type"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Description",
            style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("Describe your concern...", style = AmazeTheme.typography.body.copy(color = colors.textMuted)) },
            shape = RoundedCornerShape(AmazeTheme.radius.small),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accent
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        AmazeButton(
            text = if (submitted) "Request Submitted" else "Submit Request",
            onClick = {
                if (description.isNotBlank()) submitted = true
            },
            variant = if (submitted) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            enabled = description.isNotBlank() && !submitted
        )
        if (submitted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your counselling request has been submitted successfully.",
                style = AmazeTheme.typography.caption.copy(color = colors.successText)
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Previous Requests", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    previousRequests.forEach { request ->
        AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (request.type) {
                                "Academic" -> Icons.Rounded.School
                                "Career" -> Icons.Rounded.Work
                                else -> Icons.Rounded.Favorite
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(request.type, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(request.description, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(request.date, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
                AmazeBadge(
                    text = request.status,
                    variant = when (request.status) {
                        "Resolved", "Completed" -> BadgeVariant.SUCCESS
                        "Scheduled" -> BadgeVariant.INFO
                        else -> BadgeVariant.WARNING
                    }
                )
            }
        }
    }
}