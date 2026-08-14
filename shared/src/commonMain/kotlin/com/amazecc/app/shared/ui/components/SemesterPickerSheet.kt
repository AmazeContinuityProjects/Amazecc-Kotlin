package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme

/**
 * Push-up bottom sheet for picking the active semester, mirroring the
 * CoursePickerSheet pattern used by the Tasks screen.
 *
 * Each option shows the raw semester id (e.g. "CH20262707") plus the decoded
 * name (e.g. "Summer Semester 2026-27") as a hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterPickerSheet(
    semIds: List<String>,
    selectedId: String,
    colors: AmazeColors,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val semesterMap by AppState.semesterMap.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val allOptions = remember(semIds) {
        semIds.sortedDescending().map { id ->
            id to (semesterMap[id] ?: AppState.deriveSemesterName(id))
        }
    }
    val filteredOptions = remember(allOptions, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allOptions
        else allOptions.filter { (id, name) -> id.lowercase().contains(q) || name.lowercase().contains(q) }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedContainerColor = colors.background.copy(alpha = 0.5f),
        unfocusedContainerColor = colors.background.copy(alpha = 0.3f),
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.Rounded.CalendarMonth,
                title = "Select Semester",
                subtitle = "Your active semester syncs all app data",
                colors = colors,
                onClose = onDismiss
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(colors.accent.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "IDs hint their name — e.g. CH20262707 is Summer Semester 2026-27",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search semesters...", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            if (filteredOptions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No semesters match \"$searchQuery\"", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOptions, key = { it.first }) { (id, name) ->
                        val isSelected = id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                .background(if (isSelected) colors.accent.copy(alpha = 0.1f) else colors.background.copy(alpha = 0.4f))
                                .border(1.dp, if (isSelected) colors.accent.copy(alpha = 0.45f) else colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.medium))
                                .clickable { onSelect(id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(id, style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                Text(
                                    name,
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = colors.textMuted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}