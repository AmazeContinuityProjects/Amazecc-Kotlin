package com.amazecc.app.shared.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.LocalAnimationsEnabled
import com.amazecc.app.shared.ui.components.LocalHapticEnabled
import com.amazecc.app.shared.ui.components.mediumSpring
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The simplified home's surface grammar.
 *
 * The web keeps these in `src/lib/uiTokens.ts` under the note that "exactly
 * three surface grammars (tile / list shell / list row) and one button + chip
 * style" exist so every screen speaks the same visual dialect. This is the
 * Compose spelling of that same set — the tokens themselves live in [HomeTokens].
 *
 * Two things could not be carried across verbatim, and both are named here
 * rather than silently approximated:
 *
 * * `backdrop-blur-xl` has no `Modifier` equivalent. It is a 12+ window-blur
 *   effect that Compose cannot apply to a background, and the rest of this app
 *   does not fake it either. The tiles are translucent washes, which is the part
 *   of the effect that actually reads on a themed background.
 * * `shadow-xs` and `shadow-2xs` collapse onto Compose's single elevation axis,
 *   so surfaces here are separated by their hairline and their translucency.
 */

// ── Press interaction ──

/**
 * The whole press affordance in one modifier: scale, haptics and click.
 *
 * The web spells this out at every call site (`hover:scale-[1.01]
 * active:scale-[0.98] cursor-pointer` on tiles, `active:scale-95` on icon
 * buttons), and getting it wrong per site is how a dead-looking surface ends up
 * on screen. Compose's `clickable` also draws a default ripple the web never
 * had, so the indication is suppressed here and the scale does the work — which
 * is what every other interactive element in this app already does.
 */
@Composable
internal fun Modifier.homePressable(
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    restScale: Float = HomePress.TILE_REST,
    pressedScale: Float = HomePress.TILE_PRESSED,
    hoveredScale: Float = HomePress.TILE_HOVER,
    hovered: Boolean = false,
    haptic: HapticFeedbackType = HapticFeedbackType.TextHandleMove
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animationsEnabled = LocalAnimationsEnabled.current
    val hapticEnabled = LocalHapticEnabled.current
    val hapticFeedback = LocalHapticFeedback.current

    val target = when {
        !enabled -> restScale
        isPressed && animationsEnabled -> pressedScale
        hovered && animationsEnabled -> hoveredScale
        else -> restScale
    }
    val scale by animateFloatAsState(targetValue = target, animationSpec = mediumSpring())

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                if (hapticEnabled) hapticFeedback.performHapticFeedback(haptic)
                onClick()
            }
        )
        .background(Color.Transparent, shape)
}

// ── Tile ──

/**
 * The shared tile surface: a 24dp card, a translucent wash and a hairline.
 *
 * Overflow is clipped so the live-class progress fill inside a class card cannot
 * escape the card's rounded corners.
 */
@Composable
internal fun HomeTileSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(HomeSpace.icon),
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val homeShape = rememberHomeShape()
    val tileShape = shape ?: RoundedCornerShape(homeShape.tile)

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.homePressable(tileShape, onClick = onClick) else Modifier)
            .clip(tileShape)
            .background(palette.surfaceWash)
            .border(1.dp, palette.borderMuted, tileShape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * A tile's three-row body: a header row, an elastic middle, and a footer.
 *
 * The web's `justify-between` around a `my-auto` hero is what pins the big
 * number to the optical centre of a 128dp card regardless of how long the label
 * above it is. A weighted [Spacer] is the same trick.
 */
@Composable
internal fun HomeTileBody(
    modifier: Modifier = Modifier,
    spacing: Dp = HomeSpace.between,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        content()
    }
}

// ── Chips and icon buttons ──

/**
 * The one chip style: uppercase micro-type on a tinted wash with a hairline.
 *
 * The web splits this across `CHIP`, `SECTION_CHIP` (which is the same string)
 * and a dozen inline re-declarations for the tone variants. Here there is one
 * function with a [tone] parameter, because the only thing that varies is which
 * hue the wash, border and ink come from.
 */
@Composable
internal fun HomeChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: HomeTone = HomeTone.NEUTRAL,
    filled: Boolean = true
) {
    val colors = AmazeTheme.colors
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val t = tones[tone] ?: tones.getValue(HomeTone.NEUTRAL)
    val shape = RoundedCornerShape(homeShape.chip)

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (filled) Modifier.background(t.wash) else Modifier)
            .border(1.dp, if (filled) t.border else t.border.copy(alpha = HomeAlpha.CHIP_BORDER), shape)
            .padding(horizontal = HomeSpace.sm, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = type.badge,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The solid variant, where the chip *is* the signal — "Live" on a running class. */
@Composable
internal fun HomeSolidChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onAccent: Color = Color.White
) {
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(homeShape.chip))
            .background(tint)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = type.micro,
            color = onAccent,
            maxLines = 1
        )
    }
}

/** A square icon button: raised surface, hairline, 12dp radius. */
@Composable
internal fun HomeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val homeShape = rememberHomeShape()
    val shape = RoundedCornerShape(homeShape.control)

    Box(
        modifier = modifier
            .homePressable(
                shape = shape,
                onClick = onClick,
                enabled = enabled,
                pressedScale = HomePress.ICON_PRESSED
            )
            .clip(shape)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.borderMuted.copy(alpha = HomeAlpha.HAIRLINE_STRONG), shape)
            .padding(HomeSpace.between),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textSecondary,
            modifier = Modifier.size(HomeSpace.icon)
        )
    }
}

/** A small ghost action — "View all tasks", "Hall details". */
@Composable
internal fun HomeGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val shape = RoundedCornerShape(homeShape.control)
    val ink = tint ?: colors.accent

    Row(
        modifier = modifier
            .homePressable(shape = shape, onClick = onClick, pressedScale = HomePress.ICON_PRESSED)
            .clip(shape)
            .background(palette.surfaceRaised)
            .padding(horizontal = HomeSpace.row, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(HomeSpace.iconLg))
        }
        Text(
            text = text,
            style = type.rowTitleSmall,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Section header ──

/** A 16dp accent icon, a heavy title, an optional count chip and an optional right slot. */
@Composable
internal fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(HomeSpace.icon)
            )
            Spacer(Modifier.width(HomeSpace.sm))
        }
        Text(
            text = title,
            style = type.sectionTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (count != null) {
            Spacer(Modifier.width(HomeSpace.sm))
            HomeChip(text = count.toString(), tone = HomeTone.NEUTRAL)
        }
        if (action != null) {
            Spacer(Modifier.width(HomeSpace.sm))
            action()
        }
    }
}

// ── List shell ──

/**
 * Grouped rows that look like one card: a 16dp surface whose curves appear only
 * on its own outer top and bottom, with hairlines between children.
 *
 * Dividers belong to the shell rather than to each row so a caller cannot forget
 * one. Compose has no `divide-y`, so the rows draw their own trailing rule.
 */
@Composable
internal fun HomeListShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val homeShape = rememberHomeShape()
    val shape = RoundedCornerShape(homeShape.card)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceWash)
            .border(1.dp, palette.borderMuted, shape)
    ) {
        Column { content() }
    }
}

/**
 * One row inside a [HomeListShell].
 *
 * The trailing hairline is inset to the text column so it stops at the icon
 * tile, matching the web's `divide-y` inside a padded shell.
 */
@Composable
internal fun HomeListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .homePressable(
                shape = RoundedCornerShape(0.dp),
                onClick = onClick,
                pressedScale = 1f
            )
            .padding(horizontal = HomeSpace.icon, vertical = HomeSpace.row),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }

    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = HomeSpace.icon),
            color = palette.borderMuted.copy(alpha = HomeAlpha.DIVIDER)
        )
    }
}

// ── Empty state ──

/**
 * The dashed empty state: no background, a dashed hairline, a 32dp radius.
 *
 * A dashed border needs a custom draw, which is why this is a `drawBehind` box
 * rather than a `border(1.dp, …)`. The dash comes from [dashLength] and
 * [gapLength] in density-independent units so it reads the same at every scale.
 */
@Composable
internal fun HomeEmptyPanel(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    action: (@Composable () -> Unit)? = null
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val ink = tint ?: colors.textSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(homeShape.emptyState))
            .homeDashedBorder(
                color = palette.dashed,
                radius = homeShape.emptyState,
                strokeWidth = 1.dp,
                dashLength = 6.dp,
                gapLength = 4.dp
            )
            .padding(HomeSpace.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HomeSpace.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                style = type.rowTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = type.subline,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (action != null) {
                Spacer(Modifier.height(HomeSpace.xs))
                action()
            }
        }
    }
}

/** Draws a dashed rounded hairline, inset by half the stroke so it is not clipped. */
internal fun Modifier.homeDashedBorder(
    color: Color,
    radius: Dp,
    strokeWidth: Dp = 1.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()))
    )
    val inset = stroke.width / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke.width, size.height - stroke.width),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke
    )
}

// ── Carousel ──

/** A 5-second, swipeable, pausable index holder for the insight tile. */
@Stable
internal class HomeCarouselState(initialCount: Int) {
    var index by mutableIntStateOf(0)
        private set

    var paused by mutableStateOf(false)
        private set

    private var count: Int = initialCount

    /** Re-points the holder at a new slide list, resetting an index it no longer has. */
    fun updateCount(newCount: Int) {
        count = newCount
        if (index >= newCount) index = 0
    }

    /**
     * Named [updatePaused] rather than `setPaused` because the generated setter
     * for the [paused] property already has that signature, and a property and a
     * function of one name collide on the JVM.
     */
    fun updatePaused(value: Boolean) {
        paused = value
    }

    fun go(target: Int) {
        if (count <= 0) return
        index = ((target % count) + count) % count
    }

    fun next() = go(index + 1)

    fun prev() = go(index - 1)
}

@Composable
internal fun rememberHomeCarousel(count: Int): HomeCarouselState {
    val state = remember { HomeCarouselState(count) }
    state.updateCount(count)
    return state
}

/**
 * Advances [state] every five seconds while it is not paused and has more than
 * one slide.
 *
 * [state.paused] is a key, so lifting a finger re-arms the interval from zero
 * rather than letting the tick that lands under the reader move the card. That
 * is the same reason the web's `useCarousel` has an `onActiveChange`.
 */
@Composable
internal fun HomeCarouselAutoplay(state: HomeCarouselState, count: Int) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val liveState by rememberUpdatedState(state)
    LaunchedEffect(count, state.paused, animationsEnabled) {
        if (!animationsEnabled || count <= 1 || state.paused) return@LaunchedEffect
        while (true) {
            delay(HomeMotion.CAROUSEL_INTERVAL)
            liveState.next()
        }
    }
}

/**
 * The horizontal swipe every swipeable surface on the home uses.
 *
 * The web's `useHorizontalSwipe` fires after 40dp of travel past an 8px slop.
 * That threshold matters on this screen in particular: the home sits inside a
 * horizontal pager, so "next slide" and "next tab" are the same movement. 40dp
 * is far enough that a vertical scroll, or the incidental drift of a tap on a
 * disc, will not page the card — and the strip's chevrons and the carousel's
 * dots both keep every slide reachable without a gesture at all.
 */
@Composable
internal fun Modifier.homeHorizontalSwipe(
    enabled: Boolean = true,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onActiveChange: (Boolean) -> Unit = {}
): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    val thresholdPx = with(density) { 40.dp.toPx() }
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)
    val onActive by rememberUpdatedState(onActiveChange)

    return this.pointerInput(Unit) {
        var travelled = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
                onActive(true)
            },
            onDragCancel = { onActive(false) },
            onDragEnd = { onActive(false) },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                travelled += dragAmount
                if (!fired && abs(travelled) >= thresholdPx) {
                    fired = true
                    if (travelled < 0) next() else prev()
                }
            }
        )
    }
}

// ── Insight carousel tile ──

/**
 * The rotating stat tile: a label and badge, an elastic headline that slides
 * between slides, and a subline with dot indicators.
 *
 * The headline animates a fixed 6dp rather than a fraction of its own height,
 * because the tile is a fixed 128dp and a fraction of that would throw the
 * number clear off the card on a large text scale.
 */
@Composable
internal fun HomeInsightCarousel(
    slides: List<HomeInsightSlide>,
    state: HomeCarouselState,
    modifier: Modifier = Modifier,
    height: Dp = HomeSpace.tileShort,
    onSlideClick: ((HomeInsightSlide) -> Unit)? = null
) {
    if (slides.isEmpty()) return

    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val density = LocalDensity.current
    val shiftPx = with(density) { HomeSpace.shift.toPx() }.roundToInt()
    val shape = RoundedCornerShape(homeShape.tile)

    val slide = slides.getOrNull(state.index) ?: slides.first()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (onSlideClick != null) {
                    Modifier.homePressable(shape = shape, onClick = { onSlideClick(slide) })
                } else {
                    Modifier
                }
            )
            .homeHorizontalSwipe(
                enabled = slides.size > 1,
                onNext = state::next,
                onPrev = state::prev,
                onActiveChange = state::updatePaused
            )
            .clip(shape)
            .background(palette.surfaceWash)
            .border(1.dp, palette.borderMuted, shape)
            .padding(HomeSpace.icon)
    ) {
        HomeTileBody(spacing = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slide.label.uppercase(),
                    style = type.tileLabel,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (slide.badge != null) {
                    Spacer(Modifier.width(HomeSpace.xs))
                    HomeChip(text = slide.badge, tone = slide.tone)
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = slide.id,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(HomeMotion.SLIDE)) { shiftPx } +
                        fadeIn(animationSpec = tween(HomeMotion.SLIDE)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(HomeMotion.SLIDE)) { -shiftPx } +
                                fadeOut(animationSpec = tween(HomeMotion.SLIDE))
                        )
                },
                label = "insightSlide"
            ) { id ->
                val current = slides.firstOrNull { it.id == id } ?: slide
                val currentTone = tones[current.tone] ?: tones.getValue(HomeTone.NEUTRAL)
                Text(
                    text = current.value,
                    style = type.hero,
                    color = currentTone.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (current.blurred) Modifier.alpha(0.35f) else Modifier)
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slide.sub.orEmpty(),
                    style = type.subline,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (slides.size > 1) {
                    Spacer(Modifier.width(HomeSpace.sm))
                    HomeCarouselDots(slides, state)
                }
            }
        }
    }
}

@Composable
private fun HomeCarouselDots(slides: List<HomeInsightSlide>, state: HomeCarouselState) {
    val colors = AmazeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HomeSpace.xs)
    ) {
        slides.forEachIndexed { index, _ ->
            val active = index == state.index
            val width by animateDpAsState(
                targetValue = if (active) HomeSpace.row else 6.dp,
                animationSpec = HomeMotion.indicator()
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.border.copy(alpha = 0.7f))
                    .homePressable(
                        shape = CircleShape,
                        onClick = { state.go(index) },
                        pressedScale = HomePress.ICON_PRESSED
                    )
            )
        }
    }
}

/**
 * The week strip's week-change animation: 28dp of travel, 200ms, in the
 * direction of travel.
 *
 * Takes the travel distance in pixels rather than reading the density itself,
 * because Compose's `transitionSpec` lambda is not composable — the caller has to
 * measure outside it.
 */
internal fun homeWeekTransitionSpec(slideDirection: Int, slidePx: Float): ContentTransform {
    val travel = (slideDirection * slidePx).roundToInt()
    return (slideInHorizontally(animationSpec = tween(HomeMotion.SLIDE)) { travel } +
        fadeIn(animationSpec = tween(HomeMotion.SLIDE)))
        .togetherWith(
            slideOutHorizontally(animationSpec = tween(HomeMotion.SLIDE)) { -travel } +
                fadeOut(animationSpec = tween(HomeMotion.SLIDE))
        )
}
