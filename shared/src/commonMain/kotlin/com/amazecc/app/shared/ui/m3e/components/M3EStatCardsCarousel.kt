package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Insight Carousel Card.
 * Designed to fit a half-screen width on mobile alongside a constant stat card (e.g. Attendance).
 * Cycles smoothly through secondary insights (Cumulative GPA, Safe Bunks, Credits Earned)
 * with animated pill dot indicators, swipe gesture support, and automatic rotation.
 */
@Composable
fun M3EInsightCarouselCard(
    items: List<M3EStatItem>,
    modifier: Modifier = Modifier,
    autoAdvanceDelayMillis: Long = 4500L
) {
    if (items.isEmpty()) return

    val colors = M3ETheme.colors
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size }
    )

    // Auto-advance timer (pauses when user is actively dragging)
    LaunchedEffect(pagerState, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (isActive) {
            delay(autoAdvanceDelayMillis)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    val currentItem = items[pagerState.currentPage.coerceIn(0, items.size - 1)]

    // Asymmetric M3E character shape complementary to the constant stat card
    val cardShape = when (currentItem.tone) {
        M3EStatTone.PRIMARY -> RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
        M3EStatTone.SECONDARY -> RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
        M3EStatTone.TERTIARY -> RoundedCornerShape(topStart = 12.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 12.dp)
        M3EStatTone.GOOD -> RoundedCornerShape(topStart = 26.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 26.dp)
        M3EStatTone.WARNING -> RoundedCornerShape(topStart = 12.dp, topEnd = 24.dp, bottomEnd = 20.dp, bottomStart = 24.dp)
        M3EStatTone.CRITICAL -> RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp)
        M3EStatTone.NEUTRAL -> RoundedCornerShape(20.dp)
    }

    val accentColor = when (currentItem.tone) {
        M3EStatTone.PRIMARY -> colors.primary
        M3EStatTone.SECONDARY -> colors.secondary
        M3EStatTone.TERTIARY -> colors.tertiary
        M3EStatTone.GOOD -> colors.success
        M3EStatTone.WARNING -> colors.warning
        M3EStatTone.CRITICAL -> colors.error
        M3EStatTone.NEUTRAL -> colors.onSurface
    }

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
                val (itemAccent, itemIconBg) = when (item.tone) {
                    M3EStatTone.PRIMARY -> Pair(colors.primary, colors.primaryContainer)
                    M3EStatTone.SECONDARY -> Pair(colors.secondary, colors.secondaryContainer)
                    M3EStatTone.TERTIARY -> Pair(colors.tertiary, colors.tertiaryContainer)
                    M3EStatTone.GOOD -> Pair(colors.success, colors.successContainer)
                    M3EStatTone.WARNING -> Pair(colors.warning, colors.warningContainer)
                    M3EStatTone.CRITICAL -> Pair(colors.error, colors.errorContainer)
                    M3EStatTone.NEUTRAL -> Pair(colors.onSurface, colors.surfaceContainerHighest)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = item.onClick != null) { item.onClick?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: Label + Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(itemIconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = itemAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Metric Value & Subtitle
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = itemAccent,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1
                        )

                        if (!item.sub.isNullOrBlank()) {
                            Text(
                                text = item.sub,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Mini dot indicators at bottom
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
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
                        val dotBg = if (isSelected) accentColor else colors.outlineVariant.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(50.dp))
                                .background(dotBg)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
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
                modifier = Modifier.fillMaxWidth(),
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
        val sampleStats = listOf(
            M3EStatItem(
                id = "stat_1",
                label = "Cumulative GPA",
                value = "9.18",
                sub = "Top 5% in School of CS",
                tone = M3EStatTone.PRIMARY,
                icon = Icons.Rounded.School
            ),
            M3EStatItem(
                id = "stat_2",
                label = "Overall Attendance",
                value = "94.2%",
                sub = "Safe margin: can miss 14 lectures",
                tone = M3EStatTone.GOOD,
                icon = Icons.Rounded.CheckCircle
            ),
            M3EStatItem(
                id = "stat_3",
                label = "Credits Earned",
                value = "118 / 160",
                sub = "On track for graduation",
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
