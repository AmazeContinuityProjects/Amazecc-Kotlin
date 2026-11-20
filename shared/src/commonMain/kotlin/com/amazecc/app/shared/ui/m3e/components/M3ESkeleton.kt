package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.LocalMotionReduced
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-074: Compose equivalent of the web `aria-busy="true"` skeleton contract.
 * Compose has no built-in aria-busy attribute, so we publish a dedicated
 * semantics key alongside the spoken loading label. Test/automation code can
 * assert [SkeletonBusy] to prove a surface is in its loading state.
 */
val SkeletonBusy = SemanticsPropertyKey<Boolean>("SkeletonBusy")
var SemanticsPropertyReceiver.skeletonBusy by SkeletonBusy

/** Shared shimmer: static under reduced motion (D-084). */
@Composable
private fun skeletonAlpha(): Float {
    if (LocalMotionReduced.current) return 0.6f
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "M3ESkeletonShimmer")
    val alpha: Float by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "M3ESkeletonAlpha"
    )
    return alpha
}

@Composable
private fun SkeletonBar(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false
) {
    val colors = M3ETheme.colors
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(if (circle) RoundedCornerShape(50) else RoundedCornerShape(height / 2))
            .background(colors.onSurfaceVariant.copy(alpha = skeletonAlpha() * 0.22f))
    )
}

/**
 * Shape-preserving list placeholder. Mirrors the web `ListSkeleton`: a small
 * leading marker, two text bars per row and an optional trailing rail.
 *
 * @param label announced to screen readers, e.g. "Loading attendance".
 */
@Composable
fun M3EListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    leading: SkeletonLeading = SkeletonLeading.NONE,
    trailing: Boolean = false,
    label: String = "Loading list"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                skeletonBusy = true
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows.coerceAtLeast(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (leading) {
                    SkeletonLeading.DOT -> SkeletonBar(width = 12.dp, height = 12.dp, circle = true)
                    SkeletonLeading.BOOK -> SkeletonBar(width = 36.dp, height = 48.dp)
                    SkeletonLeading.NONE -> Unit
                }
                if (leading != SkeletonLeading.NONE) Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBar(width = 120.dp, height = 12.dp, modifier = Modifier.fillMaxWidth(0.72f))
                    SkeletonBar(width = 90.dp, height = 10.dp, modifier = Modifier.fillMaxWidth(0.45f))
                }
                if (trailing) {
                    Spacer(Modifier.width(12.dp))
                    SkeletonBar(width = 56.dp, height = 16.dp)
                }
            }
        }
    }
}

enum class SkeletonLeading { NONE, DOT, BOOK }

/**
 * Page-shaped placeholder: PageShell's eyebrow/title/subtitle, a row of stat
 * tiles and a [M3EListSkeleton]. Drop-in replacement for a bare spinner while a
 * screen's code/data loads, so layout does not jump (web `PageSkeleton`).
 */
@Composable
fun M3EPageSkeleton(
    modifier: Modifier = Modifier,
    label: String = "Loading",
    tiles: Int = 2,
    rows: Int = 4
) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                skeletonBusy = true
            }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBar(width = 80.dp, height = 12.dp)
            SkeletonBar(width = 192.dp, height = 28.dp)
            SkeletonBar(width = 128.dp, height = 12.dp)
        }
        if (tiles > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(tiles.coerceAtMost(4)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surfaceContainerLow)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SkeletonBar(width = 64.dp, height = 10.dp)
                        SkeletonBar(width = 80.dp, height = 28.dp)
                        SkeletonBar(width = 96.dp, height = 10.dp)
                    }
                }
            }
        }
        M3EListSkeleton(rows = rows, leading = SkeletonLeading.DOT, trailing = true, label = label)
    }
}

/** Single generic bar for narrow inline loading slots (a button, a chip). */
@Composable
fun M3EInlineSkeleton(
    width: Dp = 64.dp,
    height: Dp = 16.dp,
    label: String = "Loading",
    color: Color = M3ETheme.colors.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = skeletonAlpha() * 0.22f))
            .semantics(mergeDescendants = true) {
                contentDescription = label
                skeletonBusy = true
            }
    )
}

@Preview
@Composable
private fun M3EPageSkeletonPreview() {
    com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme(isDark = false) {
        Column(Modifier.padding(16.dp).background(M3ETheme.colors.background)) {
            M3EPageSkeleton(label = "Loading attendance")
        }
    }
}
