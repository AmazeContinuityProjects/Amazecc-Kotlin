package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amazecc.app.shared.state.SyncCategory
import com.amazecc.app.shared.state.SyncConfigProfile
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.theme.AmazeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsDialog(
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val profiles by SyncEngine.profiles.collectAsState()
    val activeProfileId by SyncEngine.activeProfileId.collectAsState()

    var selectedProfileId by remember(activeProfileId) { mutableStateOf(activeProfileId) }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: SyncEngine.activeProfile

    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var renameInputText by remember { mutableStateOf("") }

    val groupedModules = remember {
        SyncCategory.entries.associateWith { cat ->
            SyncModule.entries.filter { it.category == cat }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Settings, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Sync Configurations",
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Text(
                                "Select, edit, or create custom profiles",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Profile Selector Chips
                Text(
                    text = "CONFIG PROFILES",
                    style = AmazeTheme.typography.smallLabel.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isSelected = profile.id == selectedProfileId
                        val isActive = profile.id == activeProfileId

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedProfileId = profile.id },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        fontSize = AmazeTheme.fontSize.sm,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isActive) {
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.success)
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Tune,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isActive) colors.success else colors.accent
                                )
                            }
                        )
                    }
                    item(key = "create_new") {
                        FilterChip(
                            selected = false,
                            onClick = {
                                newProfileName = "Custom Config ${profiles.size - 2}"
                                showNewProfileDialog = true
                            },
                            label = { Text("+ New Config", fontSize = AmazeTheme.fontSize.sm, color = colors.accent, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.accent.copy(alpha = 0.12f))
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Active Profile Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.background.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedProfile.name,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            if (selectedProfile.id == activeProfileId) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "ACTIVE",
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        fontWeight = FontWeight.Black,
                                        color = colors.success,
                                        fontSize = AmazeTheme.fontSize.micro
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.success.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${selectedProfile.enabledModules.size} / ${SyncModule.entries.size} modules active",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selectedProfile.isBuiltIn) {
                            IconButton(
                                onClick = { SyncEngine.resetProfileToBuiltin(selectedProfile.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Restore, "Reset to Defaults", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (selectedProfile.id != activeProfileId) {
                            TextButton(
                                onClick = { SyncEngine.setActiveProfile(selectedProfile.id) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Use Profile", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                            }
                        }
                        if (!selectedProfile.isBuiltIn) {
                            IconButton(
                                onClick = {
                                    renameInputText = selectedProfile.name
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, "Rename", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { SyncEngine.deleteProfile(selectedProfile.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Quick Enable/Disable All Chips for Selected Profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProfile.enabledModules.size == SyncModule.entries.size,
                        onClick = { SyncEngine.setAllModulesEnabled(true, selectedProfile.id) },
                        label = { Text("Enable All", fontSize = AmazeTheme.fontSize.xs) }
                    )
                    FilterChip(
                        selected = selectedProfile.enabledModules.isEmpty(),
                        onClick = { SyncEngine.setAllModulesEnabled(false, selectedProfile.id) },
                        label = { Text("Disable All", fontSize = AmazeTheme.fontSize.xs) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Module Switches List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedModules.forEach { (category, modules) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = category.displayName.uppercase(),
                                style = AmazeTheme.typography.smallLabel.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                            )
                            modules.forEach { module ->
                                val isEnabled = selectedProfile.enabledModules.contains(module)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                        .background(colors.background.copy(alpha = 0.4f))
                                        .clickable { SyncEngine.setModuleEnabled(module, !isEnabled, selectedProfile.id) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = module.displayName,
                                        style = AmazeTheme.typography.body.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = AmazeTheme.fontSize.sm,
                                            color = colors.textPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { SyncEngine.setModuleEnabled(module, it, selectedProfile.id) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.surface,
                                            checkedTrackColor = colors.accent,
                                            uncheckedThumbColor = colors.textMuted,
                                            uncheckedTrackColor = colors.background
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Done Button
                AmazeButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
            }
        }
    }

    // New Profile Dialog
    if (showNewProfileDialog) {
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("New Sync Config Profile", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
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
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            val created = SyncEngine.createProfile(newProfileName.trim())
                            selectedProfileId = created.id
                            showNewProfileDialog = false
                        }
                    }
                ) {
                    Text("Create & Use", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Rename Profile Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Rename Profile", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
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
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            SyncEngine.updateProfileName(selectedProfile.id, renameInputText.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}
