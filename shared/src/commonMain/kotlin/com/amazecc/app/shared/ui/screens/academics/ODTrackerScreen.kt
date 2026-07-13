package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.ODEntry
import com.amazecc.app.shared.model.ODListItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

private val tabLabels = listOf("Overview", "Entries")

private data class ODMetrics(
    val totalODs: Int,
    val labHours: Int,
    val theoryHours: Int,
    val wastedHours: Int,
    val recoveredHours: Int
)

@Composable
fun ODTrackerScreen() {
    val colors = AmazeTheme.colors
    var activeTab by remember { mutableStateOf(0) }
    val attendance by AppState.attendance.collectAsState()

    val metrics = remember {
        ODMetrics(
            totalODs = 12,
            labHours = 8,
            theoryHours = 16,
            wastedHours = 4,
            recoveredHours = 20
        )
    }

    val mockEntries = remember {
        listOf(
            ODListItem(
                date = "2026-07-10",
                courses = listOf(
                    ODEntry("DSA Lab", "LAB", 2),
                    ODEntry("Engineering Mathematics", "TH", 1)
                ),
                total = 3
            ),
            ODListItem(
                date = "2026-07-08",
                courses = listOf(
                    ODEntry("Physics Lab", "LAB", 2),
                    ODEntry("Chemistry", "TH", 1),
                    ODEntry("Data Structures", "TH", 1)
                ),
                total = 4
            ),
            ODListItem(
                date = "2026-07-05",
                courses = listOf(
                    ODEntry("Workshop Practice", "LAB", 2)
                ),
                total = 2
            ),
            ODListItem(
                date = "2026-07-03",
                courses = listOf(
                    ODEntry("Digital Logic Lab", "LAB", 2),
                    ODEntry("Discrete Mathematics", "TH", 1),
                    ODEntry("English", "TH", 1)
                ),
                total = 4
            ),
            ODListItem(
                date = "2026-06-28",
                courses = listOf(
                    ODEntry("Soft Skills", "TH", 1),
                    ODEntry("Physics Lab", "LAB", 2)
                ),
                total = 3
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "OD Tracker",
            description = "Track on-duty hours",
            showBackButton = true,
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
            tabLabels.forEachIndexed { idx, label ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = {
                        Text(label, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        when (activeTab) {
            0 -> OverviewTab(metrics = metrics, colors = colors)
            1 -> EntriesTab(entries = mockEntries, colors = colors)
        }
    }
}

@Composable
private fun OverviewTab(
    metrics: ODMetrics,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Total ODs",
                    value = "${metrics.totalODs}",
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    iconColor = colors.accent,
                    colors = colors
                )
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Lab Hours",
                    value = "${metrics.labHours}h",
                    icon = Icons.Rounded.Science,
                    iconColor = colors.info,
                    colors = colors
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Theory Hours",
                    value = "${metrics.theoryHours}h",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    iconColor = colors.warning,
                    colors = colors
                )
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Wasted Hours",
                    value = "${metrics.wastedHours}h",
                    icon = Icons.Rounded.CancelScheduleSend,
                    iconColor = colors.danger,
                    colors = colors
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    modifier = Modifier.weight(1f),
                    title = "Recovered Hours",
                    value = "${metrics.recoveredHours}h",
                    icon = Icons.Rounded.Restore,
                    iconColor = colors.success,
                    colors = colors
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "OD Summary",
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SummaryRow("Total Hours", "${metrics.labHours + metrics.theoryHours}h", colors)
                    SummaryRow("Wasted Hours", "${metrics.wastedHours}h", colors)
                    SummaryRow("Recovered Hours", "${metrics.recoveredHours}h", colors)
                    HorizontalDivider(color = colors.border)
                    SummaryRow(
                        "Net Impact",
                        "${metrics.labHours + metrics.theoryHours - metrics.wastedHours}h",
                        colors,
                        isHighlight = true
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun KPICard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    title,
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Text(
                value,
                style = AmazeTheme.typography.display.copy(
                    color = colors.textPrimary,
                    fontSize = 28.sp
                )
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AmazeTheme.typography.body.copy(
                color = if (isHighlight) colors.textPrimary else colors.textSecondary,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
            )
        )
        Text(
            value,
            style = AmazeTheme.typography.body.copy(
                color = if (isHighlight) colors.accent else colors.textPrimary,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun EntriesTab(
    entries: List<ODListItem>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.EventBusy,
                    null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No OD entries found",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            entries.forEach { item ->
                item {
                    ODDateGroup(group = item, colors = colors)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ODDateGroup(
    group: ODListItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        group.date,
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${group.total}h",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            HorizontalDivider(color = colors.border)

            group.courses.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val typeIcon = if (entry.type == "LAB") Icons.Rounded.Science else Icons.AutoMirrored.Rounded.MenuBook
                        val typeColor = if (entry.type == "LAB") colors.info else colors.warning
                        Icon(
                            typeIcon,
                            null,
                            tint = typeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                entry.title,
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                entry.type,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (entry.type) {
                                    "LAB" -> colors.info.copy(alpha = 0.1f)
                                    else -> colors.warning.copy(alpha = 0.1f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${entry.hours}h",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (entry.type == "LAB") colors.info else colors.warning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
