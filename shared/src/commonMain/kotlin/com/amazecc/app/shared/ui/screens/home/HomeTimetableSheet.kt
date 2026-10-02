package com.amazecc.app.shared.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.design.*
import com.amazecc.app.shared.utils.AttendanceDay

/**
 * The sheet plus its scrim.
 *
 * Owning the scrim here rather than in the screen means the dismiss affordance
 * is one thing: tapping outside closes, system back closes, and the close button
 * closes. The sheet itself never navigates away underneath the reader, because
 * a link inside a dismissible overlay that swaps the surface out from under it
 * is a trap to tap by accident.
 */
@Composable
internal fun HomeTimetableOverlay(
    rows: List<HomeTimetableRow>,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // A fraction of the viewport, not a fixed height: a 640dp sheet fits a
        // large phone and swallows a small one whole.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            HomeTimetableSheet(rows = rows, onDismiss = onDismiss)
        }
    }
}

/**
 * The full weekly timetable, as a slot matrix.
 *
 * The web opens this in a bottom sheet from the "Full Weekly Timetable" action.
 * It is a sheet rather than a page because the whole point is comparing the week
 * against the day you are looking at: the sheet sits over the home, so the
 * selected day and the grid are visible at the same time.
 *
 * The close affordance lives in the sheet's own header rather than in a floating
 * corner button, next to the thing it closes.
 */
@Composable
internal fun HomeTimetableSheet(
    rows: List<HomeTimetableRow>,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()

    AppBackHandler(enabled = true, onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = homeShape.emptyState, topEnd = homeShape.emptyState))
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — icon tile, title, subtitle, close.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HomeSpace.icon)
                    .clip(RoundedCornerShape(homeShape.card))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.borderMuted, RoundedCornerShape(homeShape.card))
                    .padding(HomeSpace.row),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(homeShape.control))
                        .background(colors.accent.copy(alpha = HomeAlpha.WASH))
                        .border(1.dp, colors.accent.copy(alpha = HomeAlpha.HAIRLINE), RoundedCornerShape(homeShape.control)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(HomeSpace.card)
                    )
                }
                Spacer(Modifier.width(HomeSpace.row))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Full Weekly Timetable",
                        style = type.examTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Slot matrix, timeslots & classroom venues",
                        style = type.subline,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(HomeSpace.sm))
                HomeIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close timetable",
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(HomeSpace.row))

            if (rows.isEmpty()) {
                HomeEmptyPanel(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "No timetable synced",
                    description = "Sync your current semester to see the weekly slot matrix.",
                    modifier = Modifier.padding(horizontal = HomeSpace.icon)
                )
            } else {
                HomeTimetableGrid(
                    rows = rows,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = HomeSpace.row, vertical = HomeSpace.xs)
                )
            }

            Spacer(Modifier.height(HomeSpace.row))
        }
    }
}

/**
 * The slot matrix: a slot-and-time column followed by seven day columns.
 *
 * The day columns share the remaining width equally, so the grid is as readable
 * on a 360dp phone as on a tablet — which the horizontal-scroll "full grid"
 * alternative is not, because its columns end up narrower than a course code.
 */
@Composable
private fun HomeTimetableGrid(
    rows: List<HomeTimetableRow>,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val cellShape = RoundedCornerShape(homeShape.chip)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Column headings
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Slot",
                style = type.dayCode,
                color = colors.textMuted,
                maxLines = 1,
                modifier = Modifier.width(64.dp)
            )
            AttendanceDay.entries.forEach { day ->
                Text(
                    text = day.name.take(3),
                    style = type.dayCode,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(HomeSpace.xs))

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The slot and its time, so a row is readable without a legend.
                Column(modifier = Modifier.width(64.dp)) {
                    Text(
                        text = row.slot,
                        style = type.micro,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = row.time,
                        style = type.dayCode,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                AttendanceDay.entries.forEach { day ->
                    val card = row.cells[day]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(cellShape)
                            .then(
                                if (card == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .background(colors.accent.copy(alpha = HomeAlpha.WASH))
                                        .border(1.dp, colors.accent.copy(alpha = HomeAlpha.HAIRLINE), cellShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (card != null) {
                            Text(
                                text = card.courseCode,
                                style = type.dayCode.copy(fontSize = type.dayCode.fontSize * 0.9f),
                                color = colors.accent,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
