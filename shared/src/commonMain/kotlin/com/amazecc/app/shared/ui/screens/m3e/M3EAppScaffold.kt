package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.ui.m3e.components.M3EFloatingToolbar
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 width classes, using the same thresholds as
 * `WindowWidthSizeClass` (Compact < 600dp, Medium 600–839dp, Expanded >= 840dp).
 *
 * D-119: computed from [LocalWindowInfo] rather than importing
 * `material3-window-size-class`, which is not a declared dependency of
 * `commonMain` in this project. Adding a build dependency would be outside this
 * file, so the behaviour is identical without one.
 */
private enum class M3EWindowWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
private fun rememberWindowWidthClass(): M3EWindowWidthClass {
    val widthDp = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
    return when {
        widthDp < 600f -> M3EWindowWidthClass.COMPACT
        widthDp < 840f -> M3EWindowWidthClass.MEDIUM
        else -> M3EWindowWidthClass.EXPANDED
    }
}

/** Space reserved on expanded windows so screen content never sits under the rail. */
private val RailReservedWidth = 216.dp

/**
 * Global Scaffold for AmazeCC's Material 3 Expressive interface.
 *
 * D-119: one canonical scaffold — there is no second nav scaffold inside it.
 * Navigation is supplied exclusively by [M3EFloatingToolbar], which switches
 * from a bottom bar on compact/medium windows to a rail on expanded windows.
 * System-bar and IME insets are applied to the content/nav layer so neither is
 * hidden behind the status bar, navigation bar or keyboard.
 */
@Composable
fun M3EAppScaffold(
    currentDestination: M3EDestination,
    onDestinationSelected: (M3EDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AmazeM3ETheme {
        val useRail = rememberWindowWidthClass() == M3EWindowWidthClass.EXPANDED

        // Edge-to-edge background; the inset-aware layer below keeps interactive
        // content clear of status bar, nav bar and IME without shrinking the paint.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(M3ETheme.colors.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (useRail) Modifier.padding(start = RailReservedWidth) else Modifier)
                ) {
                    content()
                }

                // Single canonical navigation surface (bar on compact, rail on expanded).
                M3EFloatingToolbar(
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationSelected,
                    useRail = useRail,
                    modifier = if (useRail) {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun M3EAppScaffoldPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAppScaffold(
            currentDestination = M3EDestination.HOME,
            onDestinationSelected = {}
        ) {
            Box(Modifier.fillMaxSize())
        }
    }
}
