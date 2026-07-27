package com.amazecc.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.runtime.*
import com.amazecc.app.shared.state.AppState

@Composable
fun EventPalette(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    // In the future, this can query event data from AppState
    val commands = remember {
        listOf(
            CommandItem(
                id = "event-search-1",
                label = "Search Events",
                description = "Find hackathons, fests, and club activities",
                icon = Icons.Rounded.Event,
                category = "Actions",
                onSelect = { /* Trigger deep search */ }
            )
        )
    }

    CommandPalette(
        isOpen = isOpen,
        onClose = onClose,
        commands = commands,
        placeholder = "Search upcoming events..."
    )
}
