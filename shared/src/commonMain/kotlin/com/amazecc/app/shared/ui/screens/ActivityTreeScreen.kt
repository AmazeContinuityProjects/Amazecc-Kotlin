package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.ActivityTree
import com.amazecc.app.shared.model.HeatMapEntry
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

fun generateMockHeatmapData(): List<HeatMapEntry> {
    val tree = ActivityTree()
    tree.increment(2026, 1, 5); tree.increment(2026, 1, 5)
    tree.increment(2026, 1, 12); tree.increment(2026, 1, 15)
    tree.increment(2026, 2, 3); tree.increment(2026, 2, 10); tree.increment(2026, 2, 10); tree.increment(2026, 2, 10)
    tree.increment(2026, 3, 8); tree.increment(2026, 3, 22); tree.increment(2026, 3, 22)
    tree.increment(2026, 4, 1); tree.increment(2026, 4, 5); tree.increment(2026, 4, 15); tree.increment(2026, 4, 20)
    tree.increment(2026, 5, 10); tree.increment(2026, 5, 12); tree.increment(2026, 5, 18)
    tree.increment(2026, 6, 3); tree.increment(2026, 6, 7); tree.increment(2026, 6, 14)
    tree.increment(2026, 6, 21); tree.increment(2026, 6, 28)
    tree.increment(2026, 7, 1); tree.increment(2026, 7, 1); tree.increment(2026, 7, 5)
    tree.increment(2026, 7, 10); tree.increment(2026, 7, 13)
    return tree.toHeatMap()
}

@Composable
fun ActivityTreeScreen() {
    val colors = AmazeTheme.colors
    val heatmapData = remember { generateMockHeatmapData() }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun getHeatColor(count: Int): Color {
        return when {
            count == 0 -> colors.surface
            count <= 2 -> Color(0xFFBBF7D0)
            count <= 4 -> Color(0xFF4ADE80)
            count <= 6 -> Color(0xFF22C55E)
            else -> Color(0xFF16A34A)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = "Activity Tree", description = "Your engagement heatmap", showBackButton = true)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("This Year's Activity", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(16.dp))

                        // Month labels
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            months.forEach { month ->
                                Text(month, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 9.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // Heatmap cells in a grid-like layout
                        // Group by month for better display
                        val groupedByMonth = heatmapData.groupBy { entry ->
                            try { entry.date.split("/")[1].toInt() } catch (_: Exception) { 0 }
                        }

                        months.forEachIndexed { monthIndex, _ ->
                            val entries = groupedByMonth[monthIndex + 1] ?: emptyList()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Show up to 31 cells per month (days)
                                for (day in 1..31) {
                                    val entry = entries.find { e ->
                                        try { e.date.split("/")[2].toInt() == day } catch (_: Exception) { false }
                                    }
                                    val heatColor = if (entry != null) getHeatColor(entry.count) else colors.surface
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(heatColor)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Legend
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Less", style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 10.sp))
                            listOf(colors.surface, Color(0xFFBBF7D0), Color(0xFF4ADE80), Color(0xFF22C55E), Color(0xFF16A34A)).forEach { color ->
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            }
                            Text("More", style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 10.sp))
                        }
                    }
                }
            }

            // Stats summary
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Summary", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${heatmapData.size}", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Text("Days Active", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${heatmapData.sumOf { it.count }}", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Text("Total Actions", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${heatmapData.maxOfOrNull { it.count } ?: 0}", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Text("Best Day", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }

            // Recent activity entries
            item {
                Text("Activity Log", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            }

            heatmapData.take(20).forEach { entry ->
                item {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(getHeatColor(entry.count)), contentAlignment = Alignment.Center) {
                                Text("${entry.count}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (entry.count == 0) colors.textMuted else Color(0xFF111827)))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(entry.date, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
