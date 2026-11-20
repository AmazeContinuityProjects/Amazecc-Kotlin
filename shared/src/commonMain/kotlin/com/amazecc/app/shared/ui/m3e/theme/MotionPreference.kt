package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * D-084: web parity for `MotionConfig reducedMotion="user"` + the
 * `prefers-reduced-motion` CSS media query.
 *
 * The value is the *effective* reduce-motion decision for the current
 * composition: true when the OS setting is on OR when the user switched
 * animations off in Settings. [AmazeM3ETheme] is the single provider, so every
 * M3E motion spec (and the skeleton/sheet primitives) read one value.
 */
val LocalMotionReduced = compositionLocalOf { false }

/** Platform `prefers-reduced-motion` equivalent. */
@Composable
expect fun platformPrefersReducedMotion(): Boolean
