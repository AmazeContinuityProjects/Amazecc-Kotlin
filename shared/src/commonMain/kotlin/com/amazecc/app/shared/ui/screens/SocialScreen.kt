package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.nfc.LocalNfcManager
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.utils.Friend
import com.amazecc.app.shared.utils.SocialUtils
import kotlinx.coroutines.delay

@Composable
fun SocialScreen() {
    val colors = AmazeTheme.colors
    val nfcManager = LocalNfcManager.current
    
    // We will just use a dummy list or load from shared preferences if possible.
    // For this port, we hold state in memory (or ideally in AppState/Settings).
    var friends by remember { mutableStateOf(emptyList<Friend>()) }
    var isSharing by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var scannedMessage by remember { mutableStateOf<String?>(null) }
    
    val attendance by AppState.attendance.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Friends Directory",
            description = "Share your schedule & see where friends are",
            showBackButton = false,
            showSyncButton = false
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmazeButton(
                text = if (isSharing) "Stop Sharing" else "Tap to Share",
                icon = Icons.Rounded.Share,
                onClick = {
                    if (isSharing) {
                        nfcManager?.stopSharing()
                        isSharing = false
                    } else {
                        val attendanceList = attendance?.attendance ?: emptyList()
                        val shareData = SocialUtils.exportScheduleCode(
                            attendanceList, 
                            "My Name", 
                            "20BCC0001" // In a real app, fetch from user profile
                        )
                        nfcManager?.startSharing(shareData)
                        isSharing = true
                        isListening = false
                        nfcManager?.stopListening()
                    }
                },
                variant = if (isSharing) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            
            AmazeButton(
                text = if (isListening) "Listening..." else "Scan Friend",
                icon = Icons.Rounded.Search,
                onClick = {
                    if (isListening) {
                        nfcManager?.stopListening()
                        isListening = false
                    } else {
                        isListening = true
                        isSharing = false
                        nfcManager?.stopSharing()
                        nfcManager?.startListening { data ->
                            scannedMessage = data
                            isListening = false
                            try {
                                val newFriend = SocialUtils.importScheduleCode(data)
                                friends = friends + newFriend
                            } catch (e: Exception) {
                                // Ignore or show error
                            }
                        }
                    }
                },
                variant = if (isListening) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (friends.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No friends added yet. Tap to share or scan!", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(friends) { friend ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(friend.name, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text(friend.regNumber, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Classes shared: ", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            nfcManager?.stopSharing()
            nfcManager?.stopListening()
        }
    }
}
