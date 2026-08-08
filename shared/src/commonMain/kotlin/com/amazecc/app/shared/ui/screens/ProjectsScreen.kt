package com.amazecc.app.shared.ui.screens

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.api.AmazeClient

@Composable
fun ProjectsScreen() {
    KeyValueResponseScreen(
        title = "Projects",
        description = "Academic projects and guides",
        loadingText = "Loading projects...",
        load = { AmazeClient.getProjects() }
    )
}
