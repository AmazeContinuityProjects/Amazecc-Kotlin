package com.amazecc.app.shared.ffcs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

private val FfcsGridDays = SlotMap.weekdays

private object FfcsTimetableBands {
    val bands: List<String> by lazy {
        val ranges = mutableSetOf<String>()
        FfcsGridDays.forEach { day ->
            (SlotMap.map[day] ?: emptyMap()).values.forEach { ranges.add(it) }
        }
        ranges.sortedBy { TimeMath.toMinutes(it.substringBefore("-")) }
    }
}

@Composable
fun FfcsTimetableGrid(
    courses: List<AddedCourse>,
    blockedSlots: Set<String>,
    onToggleBlockSlots: (List<String>) -> Unit,
    selectedGapDetails: List<GapDetail>? = null,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val days = FfcsGridDays
    val bands = FfcsTimetableBands.bands
    val dayLabels = SlotMap.dayLabels
    val headerH = 40.dp
    val rowH = 36.dp

    AmazeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.width(54.dp)) {
                    Spacer(Modifier.height(headerH))
                    bands.forEach { band ->
                        Box(
                            modifier = Modifier.fillMaxWidth().height(rowH),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                band.replace("-", "\n"),
                                style = AmazeTheme.typography.smallLabel.copy(
                                    fontSize = AmazeTheme.fontSize.micro,
                                    lineHeight = 12.sp,
                                    color = colors.textMuted
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                days.forEach { day ->
                    Column(modifier = Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(headerH), contentAlignment = Alignment.Center) {
                            Text(
                                dayLabels[day] ?: day,
                                style = AmazeTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AmazeTheme.fontSize.micro,
                                    color = colors.accent
                                )
                            )
                        }

                        bands.forEach { band ->
                            val dayMap = SlotMap.map[day] ?: emptyMap()
                            val slotCodesForBand = dayMap.filter { it.value == band }.keys.toList()
                            val courseHere = courses.firstOrNull { c ->
                                c.slots.any { slot -> dayMap[slot] == band }
                            }

                            val blockKeys: List<String> = if (courseHere != null) {
                                courseHere.slots.distinct()
                                    .filter { slot -> dayMap[slot] == band }
                                    .map { slot -> "$day|$slot" }
                            } else {
                                slotCodesForBand.map { slot -> "$day|$slot" }
                            }

                            val isBlocked = blockKeys.isNotEmpty() && blockKeys.any { it in blockedSlots }
                            val isGap = selectedGapDetails?.any {
                                it.day == day && it.startMin <= TimeMath.toMinutes(band.substringBefore("-"))
                                    && it.endMin >= TimeMath.toMinutes(band.substringAfter("-"))
                            } == true

                            Box(
                                modifier = Modifier
                                    .width(94.dp)
                                    .height(rowH)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isBlocked -> colors.danger.copy(alpha = 0.25f)
                                            isGap -> colors.warning.copy(alpha = 0.4f)
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
                                    .then(
                                        if (blockKeys.isNotEmpty()) Modifier.clickable { onToggleBlockSlots(blockKeys) }
                                        else Modifier
                                    ),
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
                                    Text(
                                        courseHere.code.take(8),
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            fontSize = AmazeTheme.fontSize.micro,
                                            fontWeight = FontWeight.Bold,
                                            color = hexToColor(courseHere.color)
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
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.success.copy(alpha = 0.2f)))
                Text("Free", style = AmazeTheme.typography.smallLabel.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent.copy(alpha = 0.2f)))
                Text("Occupied", style = AmazeTheme.typography.smallLabel.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.danger.copy(alpha = 0.25f)))
                Text("Blocked", style = AmazeTheme.typography.smallLabel.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary))
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.warning.copy(alpha = 0.4f)))
                Text("Gap", style = AmazeTheme.typography.smallLabel.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary))
            }
        }
    }
}
