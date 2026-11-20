package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.runtime.Composable

/**
 * Desktop has no single cross-toolkit `prefers-reduced-motion` probe, so the
 * platform contribution is false. The in-app "Animations" setting still feeds
 * [LocalMotionReduced] through [AmazeM3ETheme] on every target.
 */
@Composable
actual fun platformPrefersReducedMotion(): Boolean = false
