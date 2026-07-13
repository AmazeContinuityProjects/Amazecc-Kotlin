package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

private data class MockFriend(
    val name: String,
    val regNo: String,
    val mutualClasses: Int
)

private data class MockGroup(
    val name: String,
    val memberCount: Int,
    val members: List<String>,
    val nextFreeSlot: String
)

private data class MockSlot(
    val day: String,
    val timeRange: String
)

private data class MockSharedSchedule(
    val friendName: String,
    val status: String,
    val date: String
)

private val mockFriends = listOf(
    MockFriend("Arjun Sharma", "23BCE1001", 4),
    MockFriend("Priya Patel", "23BCE1002", 3),
    MockFriend("Rahul Verma", "23BCE1003", 5),
    MockFriend("Sneha Reddy", "23BCE1004", 2),
    MockFriend("Ankit Singh", "23BCE1005", 6)
)

private val mockGroups = listOf(
    MockGroup("Study Group Alpha", 4, listOf("AS", "PP", "RV", "SR"), "Mon 10:00-11:00"),
    MockGroup("Project Phoenix", 3, listOf("AK", "PP", "SR"), "Wed 14:00-15:30"),
    MockGroup("Weekend Warriors", 5, listOf("AS", "RV", "AK", "SR", "PP"), "Fri 09:00-10:00")
)

private val mockCommonSlots = listOf(
    MockSlot("Monday", "10:00 - 11:00"),
    MockSlot("Monday", "14:00 - 15:00"),
    MockSlot("Tuesday", "11:00 - 12:00"),
    MockSlot("Wednesday", "10:00 - 11:00"),
    MockSlot("Wednesday", "14:00 - 15:30"),
    MockSlot("Thursday", "09:00 - 10:00"),
    MockSlot("Friday", "10:00 - 11:00"),
    MockSlot("Friday", "15:00 - 16:00")
)

private val mockSharedSchedules = listOf(
    MockSharedSchedule("Arjun Sharma", "Approved", "12 Jul 2026"),
    MockSharedSchedule("Priya Patel", "Pending", "10 Jul 2026"),
    MockSharedSchedule("Rahul Verma", "Approved", "08 Jul 2026")
)

@Composable
fun SocialScreen() {
    val colors = AmazeTheme.colors
    val tabs = listOf("Friends", "Groups", "Common Slots", "Share Schedule")
    var activeTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Social & Friends",
            description = "Find friends and match timetables",
            showBackButton = false,
            showSyncButton = true
        )

        TabRow(
            selectedTabIndex = activeTab,
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    text = {
                        Text(
                            tab,
                            style = AmazeTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                0 -> FriendsTab()
                1 -> GroupsTab()
                2 -> CommonSlotsTab()
                3 -> ShareScheduleTab()
            }
        }
    }
}

@Composable
private fun FriendsTab() {
    val colors = AmazeTheme.colors
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "Search by name or reg no..." else "",
                        style = AmazeTheme.typography.body.copy(color = colors.textMuted)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AmazeButton(
                text = "Add",
                onClick = {},
                variant = ButtonVariant.PRIMARY,
                modifier = Modifier.height(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mockFriends) { friend ->
                FriendCard(friend)
            }
        }
    }
}

@Composable
private fun FriendCard(friend: MockFriend) {
    val colors = AmazeTheme.colors
    var showOptions by remember { mutableStateOf(false) }

    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { showOptions = !showOptions }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.name.take(1).uppercase(),
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.name,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = friend.regNo,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
                AmazeBadge(
                    text = "${friend.mutualClasses} mutual",
                    variant = BadgeVariant.INFO
                )
            }

            if (showOptions) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.border)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeButton(
                        text = "View Timetable",
                        onClick = {},
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Message",
                        onClick = {},
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsTab() {
    val colors = AmazeTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        AmazeButton(
            text = "Create Group",
            onClick = {},
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mockGroups) { group ->
                GroupCard(group)
            }
        }
    }
}

@Composable
private fun GroupCard(group: MockGroup) {
    val colors = AmazeTheme.colors

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Group,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "${group.memberCount} members \u00B7 Next: ${group.nextFreeSlot}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    group.members.forEach { initials ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.1f))
                                .border(2.dp, colors.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                AmazeButton(
                    text = "Add Members",
                    onClick = {},
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
}

@Composable
private fun CommonSlotsTab() {
    val colors = AmazeTheme.colors
    var selectedChip by remember { mutableStateOf("All Friends") }
    val chips = listOf("All Friends", "Arjun", "Priya", "Study Group Alpha")

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEach { chip ->
                val selected = selectedChip == chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) colors.accent else colors.surface)
                        .border(
                            if (selected) 0.dp else 1.dp,
                            colors.border,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedChip = chip }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chip,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (selected) Color.White else colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
            days.forEach { day ->
                val daySlots = mockCommonSlots.filter { it.day == day }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day.take(3).uppercase(),
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            ),
                            modifier = Modifier.width(48.dp)
                        )
                        if (daySlots.isEmpty()) {
                            Text(
                                text = "No common slots",
                                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                daySlots.forEach { slot ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = slot.timeRange,
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareScheduleTab() {
    val colors = AmazeTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AmazeButton(
            text = "Share My Schedule",
            onClick = {},
            icon = Icons.Rounded.Share,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Shared Schedules",
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        mockSharedSchedules.forEach { schedule ->
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = schedule.friendName.take(1).uppercase(),
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = schedule.friendName,
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = schedule.date,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                    AmazeBadge(
                        text = schedule.status,
                        variant = if (schedule.status == "Approved") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Share Options",
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Copy Link",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Share a link to your timetable",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Share with Friend",
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Text(
                        text = "Send directly to a friend",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
