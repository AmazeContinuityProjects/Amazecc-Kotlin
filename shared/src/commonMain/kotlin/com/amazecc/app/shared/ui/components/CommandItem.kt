package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Mutable context passed to a sub-search's [SubSearchSpec.search], letting a multi-step
 * sub-search (e.g. faculty directory: school → faculty) remember its current step.
 */
class SubSearchContext {
    val store = mutableMapOf<String, Any?>()
}

/**
 * A dedicated search performed inside the command palette itself — used for large
 * (FFCS, curriculum, rooms) or network-backed (library catalog, faculty directory)
 * data sets that would otherwise overload the global index.
 */
class SubSearchSpec(
    val key: String,
    val label: String,
    val placeholder: String,
    val icon: ImageVector? = null,
    val search: suspend (query: String, ctx: SubSearchContext) -> List<CommandItem>
)

data class CommandItem(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val category: String? = null,
    val detail: (@Composable () -> Unit)? = null,
    val rightSlot: (@Composable () -> Unit)? = null,
    val subSearch: SubSearchSpec? = null,
    /** When true the palette stays open after select (used to advance a sub-search step). */
    val keepPaletteOpen: Boolean = false,
    val onSelect: () -> Unit = {}
)
