package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.ui.m3e.components.M3EFloatingToolbar
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Global Scaffold for AmazeCC's Material 3 Expressive interface.
 * Hosts the floating bottom toolbar and routes between decoupled screens.
 */
@Composable
fun M3EAppScaffold(
    currentDestination: M3EDestination,
    onDestinationSelected: (M3EDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AmazeM3ETheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(M3ETheme.colors.background)
        ) {
            // Screen Content
            content()

            // Floating Navigation Bar (Level 2 Elevation)
            M3EFloatingToolbar(
                currentDestination = currentDestination,
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
