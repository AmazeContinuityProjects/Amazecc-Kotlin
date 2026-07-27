package com.amazecc.app.shared.ffcs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.utils.TimeMath

private fun hexToColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return Color.Gray
    val r = clean.substring(0, 2).toIntOrNull(16) ?: 128
    val g = clean.substring(2, 4).toIntOrNull(16) ?: 128
    val b = clean.substring(4, 6).toIntOrNull(16) ?: 128
    return Color(r, g, b)
}

private val theoryPeriods = listOf(
    "8:00-8:50", "8:55-9:45", "9:50-10:40", "10:45-11:35", "11:40-12:30",
    "2:00-2:50", "2:55-3:45", "3:50-4:40", "4:45-5:35"
)

@Composable
fun FfcsTimetableGrid(
    courses: List<AddedCourse>,
    blockedSlots: Set<String>,
    onToggleBlockSlot: (String) -> Unit,
    selectedGapDetails: List<GapDetail>? = null,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val days = listOf("MON", "TUE", "WED", "THU", "FRI")
    val dayLabels = mapOf("MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri")

    AmazeCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(52.dp)) {
                    Spacer(Modifier.height(20.dp))
                    days.forEach { _ ->
                        Spacer(Modifier.height(38.dp))
                    }
                }

                days.forEach { day ->
                    Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayLabels[day] ?: day,
                            style = AmazeTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.accent
                            )
                        )
                        Spacer(Modifier.height(4.dp))

                        theoryPeriods.forEach { timeRange ->
                            val slotKey = "$day|$timeRange"
                            val isBlocked = blockedSlots.contains(slotKey)
                            val isGap = selectedGapDetails?.any {
                                it.day == day && it.startMin <= TimeMath.toMinutes(timeRange.split("-")[0])
                                    && it.endMin >= TimeMath.toMinutes(timeRange.split("-").getOrElse(1) { "" })
                            } == true

                            val courseHere = courses.firstOrNull { c ->
                                c.slots.any { slot ->
                                    val dayMap = SlotMap.map[day] ?: emptyMap()
                                    val time = dayMap[slot]
                                    time == timeRange
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(94.dp)
                                    .height(34.dp)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isBlocked -> colors.danger.copy(alpha = 0.25f)
                                            isGap -> Color(0xFFFCD34D).copy(alpha = 0.4f)
                                            courseHere != null -> hexToColor(courseHere.color).copy(alpha = 0.2f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        when {
                                            isBlocked -> colors.danger.copy(alpha = 0.5f)
                                            courseHere != null -> colors.accent.copy(alpha = 0.3f)
                                            else -> colors.border.copy(alpha = 0.3f)
                                        },
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable { onToggleBlockSlot(slotKey) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBlocked) {
                                    Icon(
                                        Icons.Rounded.Block,
                                        null,
                                        tint = colors.danger.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (courseHere != null) {
                                    val courseColor = hexToColor(courseHere.color)
                                    Text(
                                        courseHere.code.take(8),
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = courseColor
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF10B981).copy(alpha = 0.2f)))
                Text("Free", style = AmazeTheme.typography.smallLabel.copy(fontSize = 9.sp, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent.copy(alpha = 0.2f)))
                Text("Occupied", style = AmazeTheme.typography.smallLabel.copy(fontSize = 9.sp, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.danger.copy(alpha = 0.25f)))
                Text("Blocked", style = AmazeTheme.typography.smallLabel.copy(fontSize = 9.sp, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFCD34D).copy(alpha = 0.4f)))
                Text("Gap", style = AmazeTheme.typography.smallLabel.copy(fontSize = 9.sp, color = colors.textSecondary))
            }
        }
    }
}

fun getCoursesForDayPeriod(courses: List<AddedCourse>, day: String, timeRange: String): List<AddedCourse> {
    val dayMap = SlotMap.map[day] ?: return emptyList()
    return courses.filter { c ->
        c.slots.any { slot -> dayMap[slot] == timeRange }
    }
}
