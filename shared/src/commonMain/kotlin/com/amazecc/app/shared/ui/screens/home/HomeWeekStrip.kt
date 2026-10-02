package com.amazecc.app.shared.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.design.*
import com.amazecc.app.shared.utils.AttendanceDay

/**
 * The home page's week strip.
 *
 * Seven circles, one per day, each carrying three facts: which day it is, what
 * the date is, and what kind of day it is. The first two are text. The third
 * used to be a pill that spelled it out — `Exam`, `Off`, `MON`, `3 cls`, `Free`
 * — and that is the reason [HomeWeekFlavour] exists.
 *
 * ## Why a tint and not a word
 *
 * The word was competing with the date for the same 10px band of a cell barely
 * 46dp wide, and it lost: at that size a five-character pill is a smudge. Worse,
 * the five words were five unrelated visual weights — `Exam` was solid red on
 * white, `Off` was a pale amber ghost, `3 cls` was unboxed grey — so the strip
 * read as a set of arbitrary badges rather than as one encoding. A fill makes
 * every day kind the same shape, so the eye compares colours instead of strings.
 *
 * The colours are deliberately *not* the obvious ones. An exam is amber and a
 * holiday is red, which reads backwards from "exams are the scary thing" — and
 * that is the point. In this app red is reserved for a genuine loss of a
 * teaching day, and an exam is a normal working day that happens to be graded.
 *
 * Nothing is lost in the swap. The word moves into the disc's content
 * description (see [HomeWeekDay.contentDescription]), and the sub-header under
 * the strip narrates the selected day in full, so the tint is a way in and never
 * the only way to find out.
 */

/**
 * One fill, one ink and one edge, per kind of day.
 *
 * The washes are 10% — a wash, not a paint. The strip sits directly on the page
 * next to the exam card, and a fully saturated disc would out-shout the thing
 * the strip exists to warn you about.
 */
@androidx.compose.runtime.Immutable
internal data class HomeWeekTint(
    val fill: Color,
    val ink: Color,
    val edge: Color
)

@Composable
internal fun homeWeekTint(flavour: HomeWeekFlavour, colors: AmazeColors = AmazeTheme.colors): HomeWeekTint =
    when (flavour) {
        HomeWeekFlavour.EXAM -> HomeWeekTint(
            fill = colors.warning.copy(alpha = HomeAlpha.WASH),
            ink = colors.warningText,
            edge = colors.warning.copy(alpha = HomeAlpha.RING)
        )
        HomeWeekFlavour.HOLIDAY -> HomeWeekTint(
            fill = colors.danger.copy(alpha = HomeAlpha.WASH),
            ink = colors.dangerText,
            edge = colors.danger.copy(alpha = HomeAlpha.RING)
        )
        HomeWeekFlavour.REORDERED -> HomeWeekTint(
            fill = colors.accent.copy(alpha = HomeAlpha.WASH),
            ink = colors.accent,
            edge = colors.accent.copy(alpha = HomeAlpha.RING)
        )
        // No hue. A teaching day is the default state and must recede — it is a
        // plain disc with a hairline, and it is the one circle in a normal week
        // that you can look straight past.
        HomeWeekFlavour.TEACHING -> HomeWeekTint(
            fill = Color.Transparent,
            ink = colors.textPrimary,
            edge = colors.border
        )
    }

/**
 * Seven circles, one per day of [days], each tinted by its
 * [HomeWeekDay.flavour].
 *
 * The tracks are equal so the gaps stay even, but each circle is capped: seven
 * 46dp discs already fill a 390dp phone, and on a tablet the uncapped version
 * would be a swimming pool. The cap sits on the inner element, which is centred
 * in its track, so the row never grows past the phone width.
 *
 * The whole strip is also swipeable, and pages the same week the chevrons do.
 * The swipe never fires on a tap: [Modifier.homeHorizontalSwipe] waits for 40dp
 * of travel, while a disc click is a down and an up.
 */
@Composable
internal fun HomeWeekStrip(
    days: List<HomeWeekDay>,
    selectedDay: AttendanceDay,
    classCounts: Map<AttendanceDay, Int>,
    onSelectDay: (AttendanceDay) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {}
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()
    var slideDirection by remember { mutableIntStateOf(1) }
    // Measured here rather than inside the transition: `transitionSpec` is not a
    // composable scope, so the density has to be read before it.
    val slidePx = with(LocalDensity.current) { HomeSpace.slide.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .homeHorizontalSwipe(
                onNext = {
                    slideDirection = 1
                    onSwipeNext()
                },
                onPrev = {
                    slideDirection = -1
                    onSwipePrev()
                }
            )
    ) {
        AnimatedContent(
            // The first date is the week's identity: page by page it changes, and
            // paging within one week never re-animates.
            targetState = days.firstOrNull()?.date.toString(),
            transitionSpec = { homeWeekTransitionSpec(slideDirection, slidePx) },
            label = "weekStrip"
        ) { _ ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HomeSpace.hairline)
            ) {
                days.forEach { day ->
                    val isSelected = day.dayCode == selectedDay
                    val flavour = day.flavour()
                    val tint = homeWeekTint(flavour, colors)
                    // Today keeps its green number only when nothing more
                    // important is going on that day. An exam outranks "it is
                    // Tuesday" for attention, and the today-ring still marks it.
                    val ink = if (flavour == HomeWeekFlavour.TEACHING && day.isToday && !isSelected) {
                        colors.successText
                    } else {
                        tint.ink
                    }
                    val count = classCounts[day.detectedDayOrder ?: day.dayCode] ?: 0

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .sizeIn(maxWidth = HomeSpace.dayDisc)
                                .aspectRatio(1f)
                                .homePressable(
                                    shape = CircleShape,
                                    onClick = { onSelectDay(day.dayCode) },
                                    restScale = if (isSelected) HomePress.DISC_SELECTED else HomePress.DISC_REST,
                                    pressedScale = HomePress.DISC_PRESSED,
                                    hoveredScale = HomePress.DISC_SELECTED
                                )
                                .clip(CircleShape)
                                .background(tint.fill)
                                .then(
                                    // Selection outranks today, and the disc
                                    // grows. Compose draws a border inside the
                                    // shape, which is the equivalent of the web's
                                    // `ring-2 ring-inset`.
                                    if (isSelected) {
                                        Modifier.border(2.dp, colors.accent, CircleShape)
                                    } else if (day.isToday) {
                                        Modifier.border(1.dp, colors.success, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, tint.edge, CircleShape)
                                    }
                                )
                                .semantics { contentDescription = day.contentDescription(count) },
                            contentAlignment = Alignment.Center
                        ) {
                            // The disc's own description already says all of
                            // this, so the two labels are hidden from the
                            // accessibility tree rather than read out twice.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(HomeSpace.xs),
                                modifier = Modifier.clearAndSetSemantics { }
                            ) {
                                Text(
                                    text = day.dayCode.name,
                                    style = type.dayCode,
                                    color = colors.textMuted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                Text(
                                    text = day.dayNumber.toString(),
                                    style = type.weekDate,
                                    color = ink,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
