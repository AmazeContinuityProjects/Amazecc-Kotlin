package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

actual val AppBackHandlerDismissesOverlaysOnEscape: Boolean = false
