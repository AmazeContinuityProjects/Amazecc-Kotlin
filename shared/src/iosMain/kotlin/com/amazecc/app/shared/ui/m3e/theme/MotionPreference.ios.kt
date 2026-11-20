package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibility

/** iOS `UIAccessibility.isReduceMotionEnabled` is the platform equivalent. */
@Composable
actual fun platformPrefersReducedMotion(): Boolean = UIAccessibility.isReduceMotionEnabled
