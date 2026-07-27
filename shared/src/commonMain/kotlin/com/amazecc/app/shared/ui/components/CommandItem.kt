package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class CommandItem(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val category: String? = null,
    val detail: (@Composable () -> Unit)? = null,
    val rightSlot: (@Composable () -> Unit)? = null,
    val onSelect: () -> Unit
)
