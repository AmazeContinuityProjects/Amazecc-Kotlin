package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

// ── FEATURE TOGGLE COMPOSITION LOCALS ──

/**
 * When true, haptic feedback fires on button presses, card clicks, and nav tab switches.
 * Controlled by the user preference in Settings → Interactions.
 */
val LocalHapticEnabled = compositionLocalOf { true }

/**
 * When true, bouncy spring press-scale animations play on interactive elements.
 * Controlled by the user preference in Settings → Interactions.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * Provides both interaction toggles at once from AppState StateFlows.
 * Called at the AmazeTheme composition root so all children inherit it.
 */
@Composable
fun ProvideInteractionPrefs(
    hapticEnabled: Boolean,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHapticEnabled provides hapticEnabled,
        LocalAnimationsEnabled provides animationsEnabled,
        content = content
    )
}

// ── SPRING SPECS ──

private val _bouncySpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessMedium
)
private val _mediumSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * High bouncy spring spec for juicy press and tap interactions.
 * Cached instance to avoid per-frame allocation.
 */
@Suppress("UNCHECKED_CAST")
fun <T> bouncySpring(): SpringSpec<T> = _bouncySpring as SpringSpec<T>

/**
 * Smooth medium bouncy spring spec for tab switches and expansions.
 * Cached instance to avoid per-frame allocation.
 */
@Suppress("UNCHECKED_CAST")
fun <T> mediumSpring(): SpringSpec<T> = _mediumSpring as SpringSpec<T>

// ── TEXT UTILITIES ──

/**
 * Shortens long assessment names into standard crisp acronyms:
 * - "Continuous Assessment Test - I" -> "CAT - I"
 * - "Continuous Assessment Test - II" -> "CAT - II"
 * - "Formative Assessment Test" -> "FAT"
 * - "Final Assessment Test" -> "FAT"
 * - "Digital Assignment" -> "DA"
 */
fun shortenAssessmentName(name: String): String {
    return name
        .replace("Continuous Assessment Test - I", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test - II", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test-I", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test-II", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test 1", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test 2", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test", "CAT", ignoreCase = true)
        .replace("Formative Assessment Test", "FAT", ignoreCase = true)
        .replace("Final Assessment Test", "FAT", ignoreCase = true)
        .replace("Digital Assignment", "DA", ignoreCase = true)
}
