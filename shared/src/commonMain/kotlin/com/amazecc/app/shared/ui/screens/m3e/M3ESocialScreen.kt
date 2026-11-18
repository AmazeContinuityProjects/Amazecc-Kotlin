package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the Social hub. */
enum class M3ESocialTab(val label: String) {
    SHARE("Share"), FRIENDS("Friends"), SLOTS("Free Slots"), GROUPS("Groups")
}

/** A paired peer (privacy-first: no timetable exposed until paired). */
@Immutable
data class M3ESocialFriend(val name: String, val regNumber: String, val nickname: String, val classCount: Int)

/** A schedule-sharing group. */
@Immutable
data class M3ESocialGroup(val id: String, val name: String, val memberNames: List<String>)

/** Immutable, stateless state for [M3ESocialScreen]. */
@Immutable
data class M3ESocialState(
    val activeTab: M3ESocialTab = M3ESocialTab.SHARE,
    val shareCode: String = "",
    val friends: List<M3ESocialFriend> = emptyList(),
    val groups: List<M3ESocialGroup> = emptyList(),
    val commonFreeSlots: List<String> = emptyList(),
    val isAddFriendDialogOpen: Boolean = false,
    val isCreateGroupDialogOpen: Boolean = false,
    val addFriendCode: String = "",
    val newGroupName: String = "",
    val isLoading: Boolean = false
)

/** User intents emitted by [M3ESocialScreen]. */
sealed interface M3ESocialAction {
    data class SelectTab(val tab: M3ESocialTab) : M3ESocialAction
    data class UpdateAddFriendCode(val code: String) : M3ESocialAction
    data class UpdateNewGroupName(val name: String) : M3ESocialAction
    data object OpenAddFriendDialog : M3ESocialAction
    data object CloseAddFriendDialog : M3ESocialAction
    data object OpenCreateGroupDialog : M3ESocialAction
    data object CloseCreateGroupDialog : M3ESocialAction
    data object AddFriend : M3ESocialAction
    data object CreateGroup : M3ESocialAction
    data class RemoveFriend(val regNumber: String) : M3ESocialAction
    data class DeleteGroup(val groupId: String) : M3ESocialAction
    data object CopyShareCode : M3ESocialAction
    data object Refresh : M3ESocialAction
    data object Back : M3ESocialAction
}

/**
 * Material 3 Expressive Social hub — pairing, free-slot discovery and groups.
 * 100% stateless: accepts [M3ESocialState], emits [M3ESocialAction].
 */
@Composable
fun M3ESocialScreen(
    state: M3ESocialState,
    onAction: (M3ESocialAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Social", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Compare schedules, find mutual free hours", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3ESocialAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3ESocialTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3ESocialAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            when (state.activeTab) {
                M3ESocialTab.SHARE -> {
                    item(key = "share_hero") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerLowest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.QrCode2, contentDescription = "Share QR", tint = colors.primary, modifier = Modifier.size(64.dp))
                                }
                                Text("Share Your Schedule", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text("Friends can scan your code to see your free slots", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                if (state.shareCode.isNotBlank()) {
                                    Text(
                                        state.shareCode,
                                        fontSize = 10.sp,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    item(key = "share_copy") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3ESocialAction.CopyShareCode) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = colors.primary)
                                Text("Copy schedule code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }
                    }
                }
                M3ESocialTab.FRIENDS -> {
                    item(key = "add_friend") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3ESocialAction.OpenAddFriendDialog) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary)
                                Text("Add friend via schedule code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }
                    }
                    if (state.friends.isEmpty()) {
                        item(key = "no_friends") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("No friends added yet. Pair with a classmate to begin.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.friends, key = { it.regNumber }) { friend ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(friend.name.firstOrNull()?.uppercase() ?: "F", fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                        Text("${friend.regNumber} · ${friend.classCount} shared classes", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onAction(M3ESocialAction.RemoveFriend(friend.regNumber)) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove friend", tint = colors.error)
                                    }
                                }
                            }
                        }
                    }
                }
                M3ESocialTab.SLOTS -> {
                    item(key = "slots_header") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Common Free Slots", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text("Times when you and all paired friends are free", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.commonFreeSlots.isEmpty()) {
                        item(key = "no_slots") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("Add friends to compute common free hours.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.commonFreeSlots, key = { it }) { slot ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Text(slot, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.success)
                            }
                        }
                    }
                }
                M3ESocialTab.GROUPS -> {
                    item(key = "create_group") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3ESocialAction.OpenCreateGroupDialog) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.primary)
                                Text("Create a group", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }
                    }
                    if (state.groups.isEmpty()) {
                        item(key = "no_groups") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("No groups yet. Group schedules to share with many friends at once.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.groups, key = { it.id }) { group ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                        Text("${group.memberNames.size} members", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onAction(M3ESocialAction.DeleteGroup(group.id)) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete group", tint = colors.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }

    if (state.isAddFriendDialogOpen) {
        AlertDialog(
            onDismissRequest = { onAction(M3ESocialAction.CloseAddFriendDialog) },
            title = { Text("Add Friend") },
            text = {
                OutlinedTextField(
                    value = state.addFriendCode,
                    onValueChange = { onAction(M3ESocialAction.UpdateAddFriendCode(it)) },
                    placeholder = { Text("Paste a schedule code") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(M3ESocialAction.AddFriend) }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(M3ESocialAction.CloseAddFriendDialog) }) { Text("Cancel") }
            }
        )
    }

    if (state.isCreateGroupDialogOpen) {
        AlertDialog(
            onDismissRequest = { onAction(M3ESocialAction.CloseCreateGroupDialog) },
            title = { Text("Create Group") },
            text = {
                OutlinedTextField(
                    value = state.newGroupName,
                    onValueChange = { onAction(M3ESocialAction.UpdateNewGroupName(it)) },
                    placeholder = { Text("Group name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(M3ESocialAction.CreateGroup) }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(M3ESocialAction.CloseCreateGroupDialog) }) { Text("Cancel") }
            }
        )
    }
}

private fun mockSocialState() = M3ESocialState(
    friends = listOf(M3ESocialFriend("Aravind Kumar", "21BCE1042", "", 3)),
    groups = listOf(M3ESocialGroup("g1", "Lunch Buddies", listOf("Aravind Kumar"))),
    commonFreeSlots = listOf("Tue · 14:00 - 15:00", "Fri · 09:00 - 10:00")
)

@Preview
@Composable
private fun M3ESocialScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3ESocialScreen(state = mockSocialState(), onAction = {}) }
}

@Preview
@Composable
private fun M3ESocialScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3ESocialScreen(state = mockSocialState(), onAction = {}) }
}
