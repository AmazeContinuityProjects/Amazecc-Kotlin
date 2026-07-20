package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AmazePageScaffold(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Content with padding so it scrolls under the floating top header (100.dp) and bottom nav bar (100.dp)
        content(PaddingValues(top = 110.dp, bottom = 110.dp))

        // Floating Header
        ScreenHeader(
            title = title,
            description = description,
            showBackButton = showBackButton,
            showSyncButton = showSyncButton,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
