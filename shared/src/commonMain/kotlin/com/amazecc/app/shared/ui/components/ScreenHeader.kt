package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun ScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true
) {
    val colors = AmazeTheme.colors
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(colors.accent.copy(alpha = 0.08f))
            .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showBackButton) {
                    IconButton(onClick = { AppState.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                    Text(
                        text = description,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                    if (isLoading && syncStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = syncStatus ?: "",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            
            if (showSyncButton) {
                IconButton(
                    onClick = { AppState.loadAllData() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accent.copy(alpha = 0.1f), CircleShape)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Sync Data",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── 1. UNIFIED ACADEMICS SCREEN (TABS: ATTENDANCE, MARKS, SCHEDULE) ──


