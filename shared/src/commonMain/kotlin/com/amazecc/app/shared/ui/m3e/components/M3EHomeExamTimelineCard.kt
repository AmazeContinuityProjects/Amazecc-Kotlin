package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.overhaul.M3EHomeExamItem
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-132: a single real exam on the selected home day, shown in the class timeline in
 * place of a regular lecture. Stateless — every visible value comes from [item], which
 * the host builds from the day's actual `ExamItem`s. Blank venue/seat fields are omitted
 * rather than padded with placeholders, so the card never invents a location.
 *
 * Visually it mirrors [M3EClassTimelineCard]'s sibling in the home screen (icon rows,
 * course code + title) but takes its asymmetric shape and warning tint from
 * [M3EStatTone.WARNING], marking the day as exam-headed without a fabricated countdown.
 */
@Composable
fun M3EHomeExamTimelineCard(
    item: M3EHomeExamItem,
    modifier: Modifier = Modifier,
    /** No real exam action exists on Home today; only pass one when the host adds it. */
    onClick: (() -> Unit)? = null
) {
    val colors = M3ETheme.colors
    // One tone drives the asymmetric shape, the accent and the EXAM badge container.
    val tone = M3EStatTone.WARNING
    val (accent, badgeContainer) = tone.statAccentAndIconBg(colors)
    val shape = tone.statCardShape()

    // D-085: when a real action is wired, TalkBack gets a button role and an explicit label.
    val clickable = if (onClick != null) {
        Modifier.clickableA11y(onClickLabel = "View exam details for ${item.courseCode}") { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "EXAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onWarningContainer
                    )
                }
                if (item.examSeries.isNotBlank()) {
                    Text(
                        text = item.examSeries,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }

            if (item.courseCode.isNotBlank()) {
                Text(
                    text = item.courseCode,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            if (item.courseTitle.isNotBlank()) {
                Text(
                    text = item.courseTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }

            if (item.examTime.isNotBlank()) {
                ExamDetailLine(Icons.Rounded.AccessTime, item.examTime)
            }
            if (item.venue.isNotBlank()) {
                ExamDetailLine(Icons.Rounded.LocationOn, item.venue)
            }
            if (item.seatNo.isNotBlank()) {
                ExamDetailLine(Icons.Rounded.EventSeat, "Seat ${item.seatNo}")
            }
        }
    }
}

@Composable
private fun ExamDetailLine(icon: ImageVector, text: String) {
    val colors = M3ETheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun M3EHomeExamTimelineCardPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EHomeExamTimelineCard(
            item = M3EHomeExamItem(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                examSeries = "CAT-1",
                examTime = "09:30 AM - 11:15 AM",
                venue = "SJT 412",
                seatNo = "B-14"
            )
        )
    }
}
