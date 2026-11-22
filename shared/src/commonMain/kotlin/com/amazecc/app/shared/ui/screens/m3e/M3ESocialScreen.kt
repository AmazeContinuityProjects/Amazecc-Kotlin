package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ELoadingIndicator
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.QRCodeGenerator
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the Social hub. */
enum class M3ESocialTab(val label: String) {
    SHARE("Share"), PEOPLE("People"), CLASSES("Classes Together"),
    SLOTS("Free Slots"), FRIENDS("Friends"), GROUPS("Groups")
}

/** A locally imported friend (privacy-first: no timetable exposed until paired). */
@Immutable
data class M3ESocialFriend(val name: String, val regNumber: String, val nickname: String, val classCount: Int)

/** A schedule-sharing group. */
@Immutable
data class M3ESocialGroup(val id: String, val name: String, val memberNames: List<String>)

/** A server-backed pairing: handle + grant, never a fabricated timetable. */
@Immutable
data class M3ESocialPeerUi(
    val handle: String,
    val name: String,
    val visibility: String,
    val shared: Boolean,
    val lastPublishedAt: String? = null,
    val semesterId: String? = null,
    val grantId: String = "",
    val secret: String = ""
)

/** One Classes Together section (a class + the peers enrolled in it). */
@Immutable
data class M3ESocialClassUi(
    val classId: String,
    val courseCode: String,
    val courseTitle: String,
    val slot: String,
    val faculty: String,
    val people: List<String>
)

/** Two students who share >= 2 classes. */
@Immutable
data class M3ESocialOverlapUi(
    val handle: String,
    val name: String,
    val shared: Int,
    val identical: Boolean,
    val sharedClassIds: List<String>
)

/** A peer timetable read through a real grant. */
@Immutable
data class M3ESocialTimetableUi(
    val handle: String,
    val title: String,
    val semesterLabel: String,
    val courses: List<String>,
    val stale: Boolean,
    val coarse: Boolean
)

/** Immutable, stateless state for [M3ESocialScreen]. */
@Immutable
data class M3ESocialState(
    val activeTab: M3ESocialTab = M3ESocialTab.SHARE,
    val shareCode: String = "",
    // Server-backed identity / pairing state (D-006/D-064).
    val handle: String = "",
    val identityLabel: String = "",
    val serverError: String? = null,
    val needsSignIn: Boolean = false,
    val isServerLoading: Boolean = false,
    val peers: List<M3ESocialPeerUi> = emptyList(),
    val serverClasses: List<M3ESocialClassUi> = emptyList(),
    val overlaps: List<M3ESocialOverlapUi> = emptyList(),
    val serverFreeSlots: List<M3ESocialFreeSlotUi> = emptyList(),
    val freeNowHandles: List<String> = emptyList(),
    val currentSlotLabel: String = "",
    val joined: Boolean = false,
    val discoverable: Boolean = false,
    val openPeerHandle: String? = null,
    val peerTimetable: M3ESocialTimetableUi? = null,
    // Local, code-based friends/groups (unchanged behaviour).
    val friends: List<M3ESocialFriend> = emptyList(),
    val groups: List<M3ESocialGroup> = emptyList(),
    val commonFreeSlots: List<String> = emptyList(),
    val isAddFriendDialogOpen: Boolean = false,
    val isCreateGroupDialogOpen: Boolean = false,
    val addFriendCode: String = "",
    val newGroupName: String = "",
    // Handle share / code import dialogs.
    val isAddHandleDialogOpen: Boolean = false,
    val addHandleInput: String = "",
    val isImportDialogOpen: Boolean = false,
    val importCodeInput: String = "",
    val formError: String? = null,
    val isLoading: Boolean = false,
    // Honest outcome of the "Save QR" export (D-145). Null until the user asks to save.
    val qrExportNotice: String? = null
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

    // Server-backed social protocol.
    data object RefreshServer : M3ESocialAction
    data object SignIn : M3ESocialAction
    data object CopyHandle : M3ESocialAction
    data object CopyInviteLink : M3ESocialAction
    data object SaveQr : M3ESocialAction
    data object OpenAddHandleDialog : M3ESocialAction
    data object CloseAddHandleDialog : M3ESocialAction
    data class UpdateAddHandleInput(val input: String) : M3ESocialAction
    data object AddHandle : M3ESocialAction
    data object OpenImportDialog : M3ESocialAction
    data object CloseImportDialog : M3ESocialAction
    data class UpdateImportCode(val code: String) : M3ESocialAction
    data object ImportCode : M3ESocialAction
    data object PublishClasses : M3ESocialAction
    data object LeaveClasses : M3ESocialAction
    data class SetDiscoverable(val discoverable: Boolean) : M3ESocialAction
    data class RevokeGrant(val grantId: String, val secret: String) : M3ESocialAction
    data class SetGrantVisibility(val grantId: String, val visibility: String) : M3ESocialAction
    data class OpenPeer(val handle: String) : M3ESocialAction
    data object ClosePeer : M3ESocialAction
}

/**
 * Material 3 Expressive Social hub — handle sharing, pairing, Classes Together and
 * common-free discovery. 100% stateless: accepts [M3ESocialState], emits [M3ESocialAction].
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
                    if (state.isServerLoading || state.isLoading) {
                        M3ELoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            size = 24.dp,
                            color = colors.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                onAction(M3ESocialAction.Refresh)
                                onAction(M3ESocialAction.RefreshServer)
                            },
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                        }
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

            val error = state.serverError
            if (error != null) {
                item(key = "server_error") {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error, fontSize = 13.sp, color = colors.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onAction(M3ESocialAction.RefreshServer) }) {
                                    Text("Retry", color = colors.primary)
                                }
                                if (state.needsSignIn) {
                                    TextButton(onClick = { onAction(M3ESocialAction.SignIn) }) {
                                        Text("Sign in with Amaze ID", color = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
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
                    item(key = "handle_share") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                    Text("Your Amaze ID handle", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                }
                                if (state.handle.isNotBlank()) {
                                    val inviteLink = remember(state.handle) { M3ESocialProto.inviteLink(state.handle) }
                                    val matrix = remember(inviteLink) { QRCodeGenerator.generate(inviteLink) }
                                    if (matrix != null) {
                                        HandleQrCanvas(
                                            matrix = matrix,
                                            modifier = Modifier.size(168.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)
                                        )
                                    }
                                    Text(state.handle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                    Text(
                                        "Scanning another phone's camera is not available here — paste their handle or share code instead.",
                                        fontSize = 11.sp, color = colors.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { onAction(M3ESocialAction.CopyHandle) }) { Text("Copy handle", color = colors.primary) }
                                        TextButton(onClick = { onAction(M3ESocialAction.CopyInviteLink) }) { Text("Copy invite link", color = colors.primary) }
                                        // D-145: only offer an image save when a real matrix exists.
                                        if (matrix != null) {
                                            TextButton(onClick = { onAction(M3ESocialAction.SaveQr) }) { Text("Save QR", color = colors.primary) }
                                        }
                                    }
                                    state.qrExportNotice?.let { notice ->
                                        Text(notice, fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    }
                                } else {
                                    Text(
                                        if (state.needsSignIn) "Sign in with Amaze ID to get a handle."
                                        else "No handle yet. Sync Social to publish your week and claim one.",
                                        fontSize = 13.sp, color = colors.onSurfaceVariant
                                    )
                                    if (state.needsSignIn) {
                                        TextButton(onClick = { onAction(M3ESocialAction.SignIn) }) { Text("Sign in", color = colors.primary) }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "add_handle") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3ESocialAction.OpenAddHandleDialog) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.PersonSearch, contentDescription = null, tint = colors.primary)
                                Text("Pair with a handle or invite link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }
                    }
                    item(key = "import_code") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3ESocialAction.OpenImportDialog) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = colors.primary)
                                Text("Import a schedule code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }
                    }
                }
                M3ESocialTab.PEOPLE -> {
                    item(key = "people_header") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("People", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text(
                                    if (state.handle.isBlank()) "Sign in and sync to claim your handle."
                                    else "You are ${state.handle}${if (state.identityLabel.isNotBlank()) " · ${state.identityLabel}" else ""}",
                                    fontSize = 12.sp, color = colors.onSurfaceVariant
                                )
                                if (state.currentSlotLabel.isNotBlank()) {
                                    Text(
                                        "Free now (${state.currentSlotLabel}): ${state.freeNowHandles.size} paired peer${if (state.freeNowHandles.size == 1) "" else "s"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (state.freeNowHandles.isEmpty()) colors.onSurfaceVariant else colors.success
                                    )
                                }
                            }
                        }
                    }
                    if (state.peers.isEmpty()) {
                        item(key = "no_peers") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text(
                                    "No pairings yet. Share your handle, then pair with a classmate to compare schedules.",
                                    fontSize = 14.sp, color = colors.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.peers, key = { it.handle }) { peer ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(peer.name.firstOrNull()?.uppercase() ?: "P", fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(peer.name.ifBlank { peer.handle }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            Text(
                                                "${peer.handle} · ${peer.visibility.orEmpty().uppercase()}${if (!peer.semesterId.isNullOrBlank()) " · ${peer.semesterId}" else ""}",
                                                fontSize = 11.sp, color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { onAction(M3ESocialAction.OpenPeer(peer.handle)) }) { Text("Timetable", color = colors.primary) }
                                        TextButton(onClick = {
                                            onAction(M3ESocialAction.SetGrantVisibility(peer.grantId, if (peer.visibility.equals("full", true)) "coarse" else "full"))
                                        }) { Text(if (peer.visibility.equals("full", true)) "Coarse" else "Full", color = colors.primary) }
                                        if (peer.grantId.isNotBlank()) {
                                            TextButton(onClick = { onAction(M3ESocialAction.RevokeGrant(peer.grantId, peer.secret)) }) {
                                                Text("Revoke", color = colors.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                M3ESocialTab.CLASSES -> {
                    item(key = "classes_status") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Classes Together", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text(
                                    if (state.joined) "Joined · ${if (state.discoverable) "discoverable" else "hidden"}"
                                    else "Not joined. Publish your registered timetable to find classmates.",
                                    fontSize = 12.sp, color = colors.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onAction(M3ESocialAction.PublishClasses) }) { Text("Publish / refresh", color = colors.primary) }
                                    if (state.joined) {
                                        TextButton(onClick = { onAction(M3ESocialAction.SetDiscoverable(!state.discoverable)) }) {
                                            Text(if (state.discoverable) "Go hidden" else "Go discoverable", color = colors.primary)
                                        }
                                        TextButton(onClick = { onAction(M3ESocialAction.LeaveClasses) }) { Text("Leave", color = colors.error) }
                                    }
                                }
                            }
                        }
                    }
                    state.serverClasses.forEach { section ->
                        item(key = "class_${section.classId}") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${section.courseCode} · ${section.slot}",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
                                    )
                                    Text(section.courseTitle.ifBlank { "Class ${section.classId}" }, fontSize = 12.sp, color = colors.onSurfaceVariant)
                                    if (section.faculty.isNotBlank()) Text(section.faculty, fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    if (section.people.isNotEmpty()) {
                                        Text(
                                            section.people.joinToString(", "),
                                            fontSize = 11.sp, color = colors.primary, maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "overlaps_header") {
                        Text("Shared timetables", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                    if (state.overlaps.isEmpty()) {
                        item(key = "no_overlaps") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("No shared-class matches yet.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.overlaps, key = { it.handle }) { overlap ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.School, contentDescription = null, tint = colors.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(overlap.name.ifBlank { overlap.handle }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                        Text(
                                            "${overlap.shared} shared classes${if (overlap.identical) " · identical timetable" else ""}",
                                            fontSize = 11.sp, color = colors.onSurfaceVariant
                                        )
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
                    if (state.serverFreeSlots.isNotEmpty()) {
                        item(key = "server_slots_header") {
                            Text("From paired timetables", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                        items(state.serverFreeSlots, key = { "${it.day}-${it.timeRange}" }) { slot ->
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${slot.day} · ${slot.timeRange}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.success)
                                    if (slot.freeWith.isNotEmpty()) {
                                        Text("Free with ${slot.freeWith.joinToString(", ")}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "local_slots_header") {
                        Text("From imported schedule codes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
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

    M3EDismissibleSheet(
        isOpen = state.isAddFriendDialogOpen,
        onDismiss = { onAction(M3ESocialAction.CloseAddFriendDialog) },
        title = "Add Friend"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.addFriendCode,
                onValueChange = { onAction(M3ESocialAction.UpdateAddFriendCode(it)) },
                placeholder = { Text("Paste a schedule code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3ESocialAction.CloseAddFriendDialog) }) { Text("Cancel") }
                TextButton(onClick = { onAction(M3ESocialAction.AddFriend) }) { Text("Add") }
            }
        }
    }

    M3EDismissibleSheet(
        isOpen = state.isAddHandleDialogOpen,
        onDismiss = { onAction(M3ESocialAction.CloseAddHandleDialog) },
        title = "Pair with a classmate"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Paste their AMZ handle or invite link. Pairing is mutual and revocable.", fontSize = 13.sp)
            OutlinedTextField(
                value = state.addHandleInput,
                onValueChange = { onAction(M3ESocialAction.UpdateAddHandleInput(it)) },
                placeholder = { Text("AMZ-XXXX-XXXX") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.formError?.let { Text(it, fontSize = 12.sp, color = colors.error) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3ESocialAction.CloseAddHandleDialog) }) { Text("Cancel") }
                TextButton(onClick = { onAction(M3ESocialAction.AddHandle) }) { Text("Look up") }
            }
        }
    }

    M3EDismissibleSheet(
        isOpen = state.isImportDialogOpen,
        onDismiss = { onAction(M3ESocialAction.CloseImportDialog) },
        title = "Import schedule code"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Paste a v5/v6 code or an amazecc share link. This imports a local comparison copy; pairing still needs a handle.", fontSize = 13.sp)
            OutlinedTextField(
                value = state.importCodeInput,
                onValueChange = { onAction(M3ESocialAction.UpdateImportCode(it)) },
                placeholder = { Text("v5|… or amazecc://share#s=…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.formError?.let { Text(it, fontSize = 12.sp, color = colors.error) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3ESocialAction.CloseImportDialog) }) { Text("Cancel") }
                TextButton(onClick = { onAction(M3ESocialAction.ImportCode) }) { Text("Import") }
            }
        }
    }

    M3EDismissibleSheet(
        isOpen = state.isCreateGroupDialogOpen,
        onDismiss = { onAction(M3ESocialAction.CloseCreateGroupDialog) },
        title = "Create Group"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.newGroupName,
                onValueChange = { onAction(M3ESocialAction.UpdateNewGroupName(it)) },
                placeholder = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3ESocialAction.CloseCreateGroupDialog) }) { Text("Cancel") }
                TextButton(onClick = { onAction(M3ESocialAction.CreateGroup) }) { Text("Create") }
            }
        }
    }

    M3EDismissibleSheet(
        isOpen = state.openPeerHandle != null,
        onDismiss = { onAction(M3ESocialAction.ClosePeer) },
        title = state.peerTimetable?.title ?: "Peer timetable"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tt = state.peerTimetable
            when {
                tt == null -> Text("Loading the timetable through your grant…", fontSize = 13.sp)
                tt.coarse -> Text("This peer shares only busy/free slots (coarse visibility).", fontSize = 13.sp)
                tt.courses.isEmpty() -> Text("No courses are published for ${tt.semesterLabel}.", fontSize = 13.sp)
                else -> tt.courses.forEach { Text(it, fontSize = 12.sp) }
            }
            if (tt?.stale == true) Text("This timetable is stale — ask the peer to re-sync.", fontSize = 12.sp, color = colors.error)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3ESocialAction.ClosePeer) }) { Text("Close") }
            }
        }
    }
}

/** Renders the invite QR on a white quiet zone; pure drawing, no clipboard or camera side effects. */
@Composable
private fun HandleQrCanvas(matrix: List<BooleanArray>, modifier: Modifier = Modifier) {
    val size = matrix.size
    if (size == 0) return
    Canvas(modifier = modifier) {
        val w = this.size.width / size
        if (w <= 0f) return@Canvas
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (matrix[row][col]) {
                    drawRect(color = Color.Black, topLeft = Offset(col * w, row * w), size = Size(w, w))
                }
            }
        }
    }
}

private fun mockSocialState() = M3ESocialState(
    handle = "AMZ-7K2P-9RTW",
    identityLabel = "Student",
    peers = listOf(
        M3ESocialPeerUi("AMZ-2ABC-3DEF", "Aravind Kumar", "full", true, semesterId = "CH20262701")
    ),
    friends = listOf(M3ESocialFriend("Aravind Kumar", "21BCE1042", "", 3)),
    groups = listOf(M3ESocialGroup("g1", "Lunch Buddies", listOf("Aravind Kumar"))),
    commonFreeSlots = listOf("Tue · 14:00 - 15:00", "Fri · 09:00 - 10:00"),
    serverFreeSlots = listOf(M3ESocialFreeSlotUi("Tuesday", "2:00 - 3:45", listOf("AMZ-2ABC-3DEF")))
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
