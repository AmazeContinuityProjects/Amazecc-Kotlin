package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.layout.*
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun CabShareScreen() {
    val colors = AmazeTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "CabShare",
            description = "Find and share rides with other students",
            showBackButton = false,
            showSyncButton = true
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text("CabShare feature is coming soon.", color = colors.textSecondary)
        }
    }
}
