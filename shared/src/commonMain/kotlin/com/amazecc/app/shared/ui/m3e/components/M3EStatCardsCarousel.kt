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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Stat Cards Carousel.
 * Swipeable horizontal container for [M3EStatItem] cards with character and animated dot indicators.
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
                sub = "Top 5% in School of Computer Science",
                tone = M3EStatTone.PRIMARY,
                icon = Icons.Rounded.School
            ),
            M3EStatItem(
                id = "stat_2",
                label = "Overall Attendance",
                value = "94.2%",
                sub = "Safe margin: can miss 14 more lectures",
                tone = M3EStatTone.GOOD,
                icon = Icons.Rounded.CheckCircle
            ),
            M3EStatItem(
                id = "stat_3",
                label = "Credits Earned",
                value = "118 / 160",
                sub = "On track for early 8th sem project",
                tone = M3EStatTone.TERTIARY,
                icon = Icons.Rounded.Insights
            )
        )

        M3EStatCardsCarousel(
            stats = sampleStats,
            modifier = Modifier.padding(16.dp)
        )
    }
}
