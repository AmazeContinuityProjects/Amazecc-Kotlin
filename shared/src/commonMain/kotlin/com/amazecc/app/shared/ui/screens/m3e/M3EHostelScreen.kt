package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EHostelAction
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELaundrySlot
import com.amazecc.app.shared.domain.overhaul.M3ELeaveRecord
import com.amazecc.app.shared.domain.overhaul.M3EMessMealItem
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Hostel & Mess Hub.
 * 100% Stateless: receives [M3EHostelState] and emits [M3EHostelAction].
 * Features room allocation hero, daily mess menu, online leave history, and warden contact.
 */
@Composable
fun M3EHostelScreen(
    state: M3EHostelState,
    onAction: (M3EHostelAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Campus Residence",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "${state.hostelDetails.blockName} • Room ${state.hostelDetails.roomNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onAction(M3EHostelAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Hostel Room Hero Card
        item {
            HostelHeroCard(
                details = state.hostelDetails,
                onCallWarden = { onAction(M3EHostelAction.CallWarden) }
            )
        }

        // 3. Tab Switcher
        item {
            M3EConnectedButtonGroup(
                items = M3EHostelTab.entries,
                selectedItem = state.selectedTab,
                onItemSelected = { onAction(M3EHostelAction.SelectTab(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Tab Content
        when (state.selectedTab) {
            M3EHostelTab.MESS_MENU -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Menu (${state.hostelDetails.messType})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Text(
                            text = state.hostelDetails.messCaterer,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary
                        )
                    }
                }

                if (state.todayMessMenu.isEmpty()) {
                    item {
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No mess menu available for today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.todayMessMenu) { meal ->
                        MessMealCard(meal = meal)
                    }
                }
            }

            M3EHostelTab.LEAVE_HISTORY -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Online Leaves (${state.leaves.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )

                        ElevatedButton(
                            onClick = { onAction(M3EHostelAction.ApplyNewLeave) },
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply Leave",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (state.leaves.isEmpty()) {
                    item {
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No leave requests found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.leaves) { leave ->
                        LeaveCard(leave = leave)
                    }
                }
            }

            M3EHostelTab.ROOM_INFO -> {
                item {
                    RoomDetailsCard(
                        details = state.hostelDetails,
                        onCallWarden = { onAction(M3EHostelAction.CallWarden) }
                    )
                }
                item {
                    LaundryScheduleCard(slots = state.laundrySchedule)
                }
            }
        }

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }
}

@Composable
private fun HostelHeroCard(
    details: M3EHostelDetails,
    onCallWarden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hotel,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = details.blockName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = colors.onSurface
                        )
                        Text(
                            text = details.roomType,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Room ${details.roomNumber}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HostelStatItem(label = "Bed", value = details.bedNumber)
                HostelStatItem(label = "Mess Type", value = details.messType)
                HostelStatItem(label = "Caterer", value = details.messCaterer)
            }
        }
    }
}

@Composable
private fun HostelStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            ),
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
    }
}

@Composable
private fun MessMealCard(
    meal: M3EMessMealItem,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = if (meal.isCurrent) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = meal.mealName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    if (meal.isCurrent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NOW SERVING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = colors.onPrimary
                            )
                        }
                    }
                }

                Text(
                    text = meal.timing,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                meal.items.forEach { foodItem ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (meal.isCurrent) colors.primary else colors.onSurfaceVariant)
                        )
                        Text(
                            text = foodItem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveCard(
    leave: M3ELeaveRecord,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val isApproved = leave.status.equals("APPROVED", ignoreCase = true)
    val isPending = leave.status.equals("PENDING", ignoreCase = true)

    val statusBg = when {
        isApproved -> colors.primaryContainer
        isPending -> colors.tertiaryContainer
        else -> colors.errorContainer
    }
    val statusFg = when {
        isApproved -> colors.onPrimaryContainer
        isPending -> colors.onTertiaryContainer
        else -> colors.onError
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = leave.visitPlace,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Text(
                        text = leave.leaveType,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = leave.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${leave.fromDate}  →  ${leave.toDate}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )

            if (leave.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reason: ${leave.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (!leave.remarks.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Remarks: ${leave.remarks}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            }
        }
    }
}

@Composable
private fun RoomDetailsCard(
    details: M3EHostelDetails,
    onCallWarden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hostel Allocation Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            DetailRow(label = "Hostel Block", value = details.blockName)
            DetailRow(label = "Room Number", value = details.roomNumber)
            DetailRow(label = "Room Designation", value = details.roomType)
            DetailRow(label = "Bed Number", value = details.bedNumber)
            DetailRow(label = "Mess Option", value = details.messType)
            DetailRow(label = "Mess Vendor", value = details.messCaterer)

            if (!details.wardenName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Resident Warden",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = details.wardenName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                    }

                    if (!details.wardenPhone.isNullOrBlank()) {
                        ElevatedButton(
                            onClick = onCallWarden,
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Call Warden",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaundryScheduleCard(
    slots: List<M3ELaundrySlot>,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        // M3ESurfaceCard already applies its 16.dp content padding — no extra inner padding.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (slots.isEmpty()) {
                    "Laundry Schedule"
                } else {
                    "Laundry Schedule (${slots.size})"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (slots.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No laundry schedule available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slots.forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (slot.date.isNotBlank()) {
                                Text(
                                    text = "Date ${slot.date}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                            }
                            if (slot.roomNumber.isNotBlank()) {
                                Text(
                                    text = "Rooms ${slot.roomNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
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
private fun DetailRow(
    label: String,
    value: String
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EHostelScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EHostelScreen(
            state = createMockHostelState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3EHostelScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EHostelScreen(
            state = createMockHostelState(),
            onAction = {}
        )
    }
}

private fun createMockHostelState() = M3EHostelState(
    hostelDetails = M3EHostelDetails(
        blockName = "Q Block (Men's Hostel)",
        roomNumber = "514",
        roomType = "2 Bed AC",
        bedNumber = "Bed B",
        messType = "Special Mess",
        messCaterer = "PR Food Services Pvt Ltd",
        wardenName = "Dr. S. Ramesh",
        wardenPhone = "+91 94441 23456"
    ),
    selectedTab = M3EHostelTab.MESS_MENU,
    todayMessMenu = listOf(
        M3EMessMealItem(
            mealName = "Breakfast",
            timing = "07:30 AM - 09:30 AM",
            items = listOf("Masala Dosa with Sambar", "Medu Vada & Chutney", "Bread, Butter & Jam", "Tea / Coffee / Milk"),
            isCurrent = false
        ),
        M3EMessMealItem(
            mealName = "Lunch",
            timing = "12:00 PM - 02:00 PM",
            items = listOf("Jeera Rice & Dal Tadka", "Paneer Butter Masala", "Chicken Chettinad (Non-Veg)", "Curd & Ice Cream"),
            isCurrent = true
        ),
        M3EMessMealItem(
            mealName = "Snacks",
            timing = "04:30 PM - 05:45 PM",
            items = listOf("Veg Samosa with Mint Chutney", "Filter Coffee / Tea"),
            isCurrent = false
        ),
        M3EMessMealItem(
            mealName = "Dinner",
            timing = "07:30 PM - 09:30 PM",
            items = listOf("Phulka Roti", "Mixed Vegetable Kurma", "Egg Curry", "Fresh Fruit Salad"),
            isCurrent = false
        )
    ),
    laundrySchedule = listOf(
        M3ELaundrySlot(date = "1", roomNumber = "112 - 336"),
        M3ELaundrySlot(date = "2", roomNumber = "337 - 561"),
        M3ELaundrySlot(date = "3", roomNumber = "562 - 786")
    ),
    leaves = listOf(
        M3ELeaveRecord(
            leaveId = "LV-2026-8812",
            visitPlace = "Chennai, Tamil Nadu",
            reason = "Family wedding celebration",
            leaveType = "Home Leave",
            fromDate = "24 Nov 2026",
            toDate = "28 Nov 2026",
            status = "Approved",
            remarks = "Proctor approved on 16 Nov"
        ),
        M3ELeaveRecord(
            leaveId = "LV-2026-7241",
            visitPlace = "Katpadi Town",
            reason = "Medical consultation",
            leaveType = "Local Outing",
            fromDate = "12 Nov 2026",
            toDate = "12 Nov 2026",
            status = "Approved",
            remarks = "Returned on time"
        )
    )
)
