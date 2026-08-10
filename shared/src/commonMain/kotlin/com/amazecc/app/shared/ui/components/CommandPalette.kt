package com.amazecc.app.shared.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay

/**
 * Two-stage global search handler:
 *  - Main: the index of every screen, module, course, task, route & search tool.
 *  - SubSearch: a dedicated in-palette search for a big/network-backed data set
 *    (library catalog, faculty directory, FFCS, curriculum, free rooms...).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommandPalette(
    isOpen: Boolean,
    onClose: () -> Unit,
    commands: List<CommandItem>,
    placeholder: String = "Search courses, buses, dues, actions..."
) {
    if (!isOpen) return

    val colors = AmazeTheme.colors
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Sub-search stage state ──
    var subSpec by remember { mutableStateOf<SubSearchSpec?>(null) }
    var subCtx by remember { mutableStateOf<SubSearchContext?>(null) }
    var subResults by remember { mutableStateOf<List<CommandItem>>(emptyList()) }
    var subLoading by remember { mutableStateOf(false) }

    val isInSubStage = subSpec != null

    val activeCommands = if (isInSubStage) subResults else commands

    // Filtered main list (or sub results list when staged)
    val filteredCommands = remember(query, activeCommands, isInSubStage) {
        if (isInSubStage) {
            activeCommands
        } else if (query.isBlank()) {
            commands.take(50) // Limit default view for performance
        } else {
            val q = query.lowercase()
            commands.filter {
                it.label.lowercase().contains(q) ||
                it.description?.lowercase()?.contains(q) == true ||
                it.category?.lowercase()?.contains(q) == true
            }.take(50)
        }
    }

    val groupedCommands = remember(filteredCommands) {
        filteredCommands.groupBy { it.category ?: "Suggestions" }
    }

    var selectedIndex by remember(filteredCommands) { mutableStateOf(if (filteredCommands.isNotEmpty()) 0 else -1) }

    // Run the sub-search whenever query or stage changes (debounced)
    LaunchedEffect(subSpec, subCtx, query) {
        val spec = subSpec ?: return@LaunchedEffect
        val ctx = subCtx ?: return@LaunchedEffect
        if (query.isBlank()) {
            subLoading = false
            subResults = emptyList()
            return@LaunchedEffect
        }
        subLoading = true
        delay(250) // debounce network-backed lookups
        subResults = try {
            spec.search(query, ctx)
        } catch (_: Exception) {
            emptyList()
        }
        subLoading = false
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            query = ""
            subSpec = null
            subCtx = null
            subResults = emptyList()
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val listState = rememberLazyListState()

    val runSelect: (CommandItem) -> Unit = { item ->
        if (item.subSearch != null) {
            // Open the sub-search stage instead of closing
            subCtx = SubSearchContext()
            subSpec = item.subSearch
            subResults = emptyList()
        } else if (item.keepPaletteOpen) {
            item.onSelect()
        } else {
            item.onSelect()
            onClose()
        }
    }

    // Handle keyboard navigation globally inside the dialog
    val handleKeyEvent: (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionDown -> {
                    if (selectedIndex < filteredCommands.size - 1) selectedIndex++
                    true
                }
                Key.DirectionUp -> {
                    if (selectedIndex > 0) selectedIndex--
                    true
                }
                Key.Enter -> {
                    if (selectedIndex in filteredCommands.indices) {
                        runSelect(filteredCommands[selectedIndex])
                    }
                    true
                }
                Key.Escape -> {
                    if (isInSubStage) {
                        subSpec = null
                        subCtx = null
                        subResults = emptyList()
                    } else {
                        onClose()
                    }
                    true
                }
                else -> false
            }
        } else false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .onKeyEvent(handleKeyEvent),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume click to prevent closing */ }
                    )
            ) {
                // Search Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isInSubStage) {
                        IconButton(
                            onClick = {
                                subSpec = null
                                subCtx = null
                                subResults = emptyList()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to all search",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = AmazeTheme.typography.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (selectedIndex in filteredCommands.indices) {
                                    runSelect(filteredCommands[selectedIndex])
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = if (isInSubStage) subSpec?.placeholder ?: placeholder else placeholder,
                                    style = AmazeTheme.typography.body,
                                    color = colors.textSecondary
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .clickable { query = "" }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Esc", style = AmazeTheme.typography.smallLabel, color = colors.textSecondary)
                        }
                    }
                }

                HorizontalDivider(color = colors.border)

                // Sub-stage context banner (e.g. faculty directory step label)
                if (isInSubStage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.accent.copy(alpha = 0.08f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = subSpec?.icon ?: Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = subSpec?.label ?: "Search",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "  ·  Esc to go back",
                            style = AmazeTheme.typography.smallLabel,
                            color = colors.textMuted,
                            maxLines = 1
                        )
                    }
                }

                val selectedItem = filteredCommands.getOrNull(selectedIndex)

                // Spotlight Detail View (iOS Spotlight style)
                AnimatedVisibility(
                    visible = selectedItem?.detail != null && !isInSubStage,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    selectedItem?.detail?.let { detailContent ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.background.copy(alpha = 0.5f))
                                .padding(16.dp)
                        ) {
                            detailContent()
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }

                // Command List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (isInSubStage && subLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (filteredCommands.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.Search, null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No results found",
                                        style = AmazeTheme.typography.body,
                                        color = colors.textSecondary
                                    )
                                    if (isInSubStage && query.isBlank()) {
                                        Text(
                                            "Type something to search",
                                            style = AmazeTheme.typography.smallLabel,
                                            color = colors.textMuted
                                        )
                                    } else {
                                        Text(
                                            "Type to refine your search",
                                            style = AmazeTheme.typography.smallLabel,
                                            color = colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isInSubStage) {
                        itemsIndexed(
                            items = filteredCommands,
                            key = { index, item -> item.id }
                        ) { index, item ->
                            val isSelected = selectedIndex == index
                            CommandItemRow(
                                item = item,
                                isSelected = isSelected,
                                onClick = { runSelect(item) },
                                onHover = { selectedIndex = index }
                            )
                        }
                    } else {
                        var globalIndex = 0
                        groupedCommands.forEach { (category, items) ->
                            item {
                                Text(
                                    text = category,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            items(
                                count = items.size,
                                key = { localIndex -> items[localIndex].id }
                            ) { localIndex ->
                                val item = items[localIndex]
                                val currentIndex = globalIndex++
                                val isSelected = selectedIndex == currentIndex

                                CommandItemRow(
                                    item = item,
                                    isSelected = isSelected,
                                    onClick = { runSelect(item) },
                                    onHover = {
                                        selectedIndex = currentIndex
                                    }
                                )
                            }
                        }
                    }
                }

                // Footer
                HorizontalDivider(color = colors.border)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShortcutHint("↑↓", "Navigate")
                        ShortcutHint("↵", "Select")
                        if (isInSubStage) {
                            ShortcutHint("Esc", "Back")
                        }
                    }
                    Text(
                        if (isInSubStage) "Focused Search" else "Command Palette",
                        style = AmazeTheme.typography.smallLabel,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandItemRow(
    item: CommandItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit
) {
    val colors = AmazeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.accent.copy(alpha = 0.1f) else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = AmazeTheme.typography.body.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description != null) {
                Text(
                    text = item.description,
                    style = AmazeTheme.typography.smallLabel,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.rightSlot != null) {
            Spacer(modifier = Modifier.width(12.dp))
            item.rightSlot.invoke()
        }
    }
}

@Composable
private fun ShortcutHint(keys: String, label: String) {
    val colors = AmazeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(keys, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
        }
        Text(label, style = AmazeTheme.typography.smallLabel, color = colors.textSecondary)
    }
}