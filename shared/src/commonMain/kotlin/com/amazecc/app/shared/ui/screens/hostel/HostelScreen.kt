package com.amazecc.app.shared.ui.screens.hostel

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.HostelInfo
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.datetime.Clock
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
            leaves.forEach { leave ->
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostelMessSection() {
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

    ExpandableSection("Mess Menu", Icons.Rounded.RestaurantMenu) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly Menu", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isVeg = !isVeg }) {
                Icon(Icons.Rounded.Eco, null, tint = if (isVeg) colors.successText else colors.textMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                Text(if (isVeg) "Veg" else "Non-Veg", style = AmazeTheme.typography.smallLabel.copy(color = if (isVeg) colors.successText else colors.textMuted, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { index, day ->
                FilterChip(
                    selected = selectedDay == index,
                    onClick = { selectedDay = index },
                    label = { Text(day, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
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
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            meals.forEachIndexed { index, meal ->
                FilterChip(
                    selected = selectedMeal == index,
                    onClick = { selectedMeal = index },
                    label = { Text(meal, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.15f), selectedLabelColor = colors.accent,
                        containerColor = Color.Transparent, labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.border, selectedBorderColor = Color.Transparent,
                        enabled = true, selected = selectedMeal == index
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        val currentMeal = meals[selectedMeal]
        val items = menuData[currentMeal] ?: emptyList()
        
        Column {
            items.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = item,
                                style = AmazeTheme.typography.caption.copy(
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            }
        }
    }
}

@Composable
fun HostelLaundrySection() {
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
        )
    )

    ExpandableSection("Laundry Schedule", Icons.Rounded.LocalLaundryService) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEachIndexed { index, block ->
                FilterChip(
                    selected = selectedBlock == index,
                    onClick = { selectedBlock = index },
                    label = { Text(block, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                        containerColor = colors.surface, labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.border, selectedBorderColor = Color.Transparent,
                        enabled = true, selected = selectedBlock == index
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        val currentBlock = blocks[selectedBlock]
        val slots = slotData[currentBlock] ?: slotData["A-Block"] ?: emptyList()
        
        slots.forEach { slot ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(slot.time, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
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
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun HostelCounsellingSection() {
    val colors = AmazeTheme.colors
    
    ExpandableSection("Faculty Advisor & Counselling", Icons.Rounded.SupportAgent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.accent.copy(alpha = 0.1f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("Dr. Rajesh Kumar", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Professor, CSE Department", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Email, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                    Text("rajesh.kumar@vit.ac.in", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sectionGap))
        AmazeButton(
            text = "Request Counselling",
            onClick = { /* Handle Click */ },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.SECONDARY
        )
    }
}
