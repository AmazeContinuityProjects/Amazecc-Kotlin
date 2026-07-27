package com.amazecc.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.runtime.*
import com.amazecc.app.shared.state.AppState

@Composable
fun LibraryPalette(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    // In the future, this can query a Koha catalog API or local library data.
    // For now, it provides a placeholder search experience for Library resources.
    val commands = remember {
        listOf(
            CommandItem(
                id = "lib-search-1",
                label = "Search Catalog",
                description = "Find books, journals, and media",
                icon = Icons.Rounded.Book,
                category = "Actions",
                onSelect = { /* Trigger deep search */ }
            )
        )
    }

    CommandPalette(
        isOpen = isOpen,
        onClose = onClose,
        commands = commands,
        placeholder = "Search Library catalog..."
    )
}
