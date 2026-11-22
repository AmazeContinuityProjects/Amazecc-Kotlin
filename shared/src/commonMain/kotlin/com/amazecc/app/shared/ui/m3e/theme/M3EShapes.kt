package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive (M3E) 10-Step Corner Radius Scale (§2 Shape Specification).
 *
 * | Step | Token | Value | Primary Usage |
 * |---|---|---|---|
 * | 1 | None | 0dp | Squared containers, edge-to-edge full width sheets |
 * | 2 | Extra Small | 4dp | Tooltips, badge counters |
 * | 3 | Small | 8dp | Inner menu corners, chips, compact inputs |
 * | 4 | Medium | 12dp | Selection controls, cards, small dialogs |
 * | 5 | Large | 16dp | Cards, list containers, modals |
 * | 6 | Large Increased | 20dp | Signature M3E cards, action items |
 * | 7 | Extra Large | 28dp | Outer menu corners, floating toolbars, bottom sheets |
 * | 8 | Extra Large Increased | 32dp | Hero containers, prominent dialogs |
 * | 9 | Extra Extra Large | 48dp | Large hero sections, feature banners |
 * | 10 | Full | 9999dp / CircleShape | Pill buttons, chips, FABs, status tags |
 */
@Immutable
object M3ECornerRadius {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val LargeIncreased: Dp = 20.dp
    val ExtraLarge: Dp = 28.dp
    val ExtraLargeIncreased: Dp = 32.dp
    val ExtraExtraLarge: Dp = 48.dp
    val Full: Dp = 9999.dp
}

/**
 * Material 3 Expressive (M3E) Shape Definitions mapped from [M3ECornerRadius].
 */
@Immutable
object M3EShapes {
    val None: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.None)
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.ExtraSmall)
    val Small: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.Small)
    val Medium: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.Medium)
    val Large: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.Large)
    val LargeIncreased: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.LargeIncreased)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.ExtraLarge)
    val ExtraLargeIncreased: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.ExtraLargeIncreased)
    val ExtraExtraLarge: CornerBasedShape = RoundedCornerShape(M3ECornerRadius.ExtraExtraLarge)
    val Full: Shape = CircleShape

    /**
     * M3E Asymmetric Menu Corner Shape: 28dp outer corners, 8dp inner corners.
     * Used for menus anchored to FABs or toolbars.
     */
    val AsymmetricMenu: CornerBasedShape = RoundedCornerShape(
        topStart = M3ECornerRadius.ExtraLarge,
        topEnd = M3ECornerRadius.Small,
        bottomStart = M3ECornerRadius.ExtraLarge,
        bottomEnd = M3ECornerRadius.Small
    )
}

val LocalM3ECornerRadius = staticCompositionLocalOf { M3ECornerRadius }
val LocalM3EShapes = staticCompositionLocalOf { M3EShapes }
