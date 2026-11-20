package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.LocalMotionReduced
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Insight Carousel Card.
 * Designed to fit a half-screen width on mobile alongside a constant stat card (e.g. Attendance).
 * Cycles smoothly through secondary insights (Cumulative GPA, Safe Bunks, Credits Earned)
 * with animated pill dot indicators, swipe gesture support, and automatic rotation.
 *
 * D-130: there is deliberately no pause control. Automatic rotation is
 * suppressed under [LocalMotionReduced] (motion sensitivity); the dots are
 * decorative indicators rather than 4dp clickable targets. Swiping still works.
 */
@Composable
fun M3EInsightCarouselCard(
    items: List<M3EStatItem>,
    modifier: Modifier = Modifier,
    autoAdvanceDelayMillis: Long = 4500L
) {
    if (items.isEmpty()) return

    val colors = M3ETheme.colors
    val reduceMotion = LocalMotionReduced.current
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size }
    )

    // Auto-advance timer: off entirely for reduced motion, and it never fights
    // an in-progress swipe. There is no pause control; reduced motion is the
    // only suppression path.
    LaunchedEffect(pagerState, items.size, reduceMotion) {
        if (items.size <= 1 || reduceMotion) return@LaunchedEffect
        while (isActive) {
            delay(autoAdvanceDelayMillis)
            if (!pagerState.isScrollInProgress && !reduceMotion) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    val currentItem = items[pagerState.currentPage.coerceIn(0, items.size - 1)]
    val cardShape = currentItem.tone.statCardShape(complementary = true)
    val accentColor = currentItem.tone.statAccentAndIconBg(colors).first

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(colors.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val item = items[pageIndex]
                val (itemAccent, itemIconBg) = item.tone.statAccentAndIconBg(colors)

                M3EStatCardContent(
                    stat = item,
                    accentColor = itemAccent,
                    iconBg = itemIconBg,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (item.onClick != null) {
                                Modifier.clickableA11y(onClickLabel = "Open ${item.label}") {
                                    item.onClick?.invoke()
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                )
            }

            // Decorative dot indicators at the bottom; no pause control.
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                        .semantics {
                            contentDescription = "Slide ${pagerState.currentPage + 1} of ${items.size}"
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, _ ->
                        val isSelected = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 14.dp else 4.dp,
                            animationSpec = M3ETheme.motion.effectsDefault(),
                            label = "MiniDotWidth"
                        )
                        val dotBg = if (isSelected) {
                            accentColor
                        } else {
                            colors.outlineVariant.copy(alpha = 0.5f)
                        }

                        // D-118: decorative indicator, not a 4dp clickable target.
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(50.dp))
                                .background(dotBg)
                        )
                        if (index < items.size - 1) {
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Stat Cards Carousel (legacy / wide horizontal layout).
 * Swipeable horizontal container for [M3EStatItem] cards with animated dot indicators.
 */
@Composable
fun M3EStatCardsCarousel(
    stats: List<M3EStatItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp)
) {
    if (stats.isEmpty()) return

    val colors = M3ETheme.colors
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Current page indicator based on first visible item
    val activeIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(stats, key = { _, stat -> stat.id }) { _, stat ->
                val cardWidthModifier = if (stats.size > 1) {
                    Modifier.fillParentMaxWidth(0.92f)
                } else {
                    Modifier.fillParentMaxWidth(1f)
                }

                M3EStatCard(
                    stat = stat,
                    modifier = cardWidthModifier
                )
            }
        }

        // Animated Pill Dot Indicators (only shown when multiple slides exist)
        if (stats.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Card ${activeIndex + 1} of ${stats.size}"
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stats.forEachIndexed { index, _ ->
                    val isSelected = index == activeIndex
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 6.dp,
                        animationSpec = M3ETheme.motion.effectsDefault(),
                        label = "DotWidth"
                    )
                    val dotBg = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f)

                    // Decorative; the row announces the active position.
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(50.dp))
                            .background(dotBg)
                    )
                    if (index < stats.size - 1) {
                        Spacer(Modifier.width(5.dp))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun M3EStatCardsCarouselPreview() {
    AmazeM3ETheme(isDark = false) {
        val cgpa = 9.18
        val sampleStats = listOf(
            M3EStatItem(
                id = "stat_1",
                label = "Cumulative GPA",
                value = cgpa.toString(),
                sub = M3EStatSubtitle.fromMetric(cgpa.toString(), "CGPA"),
                tone = M3EStatTone.PRIMARY,
                icon = Icons.Rounded.School
            ),
            M3EStatItem(
                id = "stat_2",
                label = "Overall Attendance",
                value = "94.2%",
                sub = M3EStatSubtitle.fromData("Safe margin: can miss 14 lectures"),
                tone = M3EStatTone.GOOD,
                icon = Icons.Rounded.CheckCircle
            ),
            M3EStatItem(
                id = "stat_3",
                label = "Credits Earned",
                value = "118 / 160",
                sub = M3EStatSubtitle.fromData("118 of 160 credits earned"),
                tone = M3EStatTone.SECONDARY,
                icon = Icons.Rounded.Insights
            )
        )

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EStatCard(
                stat = sampleStats[1],
                modifier = Modifier
                    .weight(1f)
                    .height(136.dp)
            )
            M3EInsightCarouselCard(
                items = listOf(sampleStats[0], sampleStats[2]),
                modifier = Modifier
                    .weight(1f)
                    .height(136.dp)
            )
        }
    }
}
