package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text

/**
 * Desktop has no system back button. Mirrors the iOS actual: a no-op, because providing a
 * `BackHandler` without a `NavigationEventDispatcherOwner` owner crashes the Compose runtime.
 */
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/**
 * Desktop has no WebView-backed renderer for the latex bundle. Rendering the source is enough to
 * keep layout and state honest while developing; the Android actual renders it properly.
 */
@Composable
actual fun LatexViewer(latex: String, modifier: Modifier) {
    Text(text = latex, modifier = modifier)
}
