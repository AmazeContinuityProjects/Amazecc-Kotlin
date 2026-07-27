package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun ScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    syncModules: Set<SyncModule> = emptySet(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(title, description, showBackButton, showSyncButton, onRefresh, syncModules) {
        AppState.headerTitle.value = title
        AppState.headerDescription.value = description
        AppState.headerShowBack.value = showBackButton
        AppState.headerShowSync.value = showSyncButton
        AppState.headerOnRefresh.value = onRefresh
        AppState.headerSyncModules.value = syncModules
    }
}

@Composable
fun HeaderSpacer(modifier: Modifier = Modifier) {
    val liveClass by AppState.currentLiveClass.collectAsState()
    val baseHeight = 78.dp
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (liveClass != null) baseHeight + 70.dp else baseHeight,
        animationSpec = bouncySpring()
    )
    Spacer(modifier = modifier.fillMaxWidth().statusBarsPadding().height(height))
}

@Composable
fun FloatingScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true,
    isScrolled: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    syncModules: Set<SyncModule> = emptySet(),
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val moduleStates by SyncEngine.moduleStates.collectAsState()
    val appHeaderRefresh by AppState.headerOnRefresh.collectAsState()

    val headerElevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isScrolled) 20.dp else 10.dp,
        animationSpec = bouncySpring()
    )
    val bgAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isScrolled) 0.96f else 0.88f,
        animationSpec = bouncySpring()
    )
    val borderAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isScrolled) 0.45f else 0.22f,
        animationSpec = bouncySpring()
    )

    val effectiveModules = remember(syncModules) {
        if (syncModules.isNotEmpty()) syncModules
        else AppState.headerSyncModules.value
    }

    val isModuleLoading = remember(effectiveModules, moduleStates, isLoading) {
        if (effectiveModules.isEmpty()) isLoading
        else isLoading || effectiveModules.any { moduleStates[it]?.status == SyncStatus.LOADING }
    }

    val moduleSyncText = remember(effectiveModules, moduleStates) {
        effectiveModules.firstOrNull { moduleStates[it]?.status == SyncStatus.LOADING }
            ?.let { moduleStates[it]?.let { "Syncing ${it.status.name}..." } }
    }

    val effectiveRefresh = onRefresh ?: appHeaderRefresh

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(headerElevation, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.navBackground.copy(alpha = bgAlpha))
            .border(1.dp, colors.accent.copy(alpha = borderAlpha), RoundedCornerShape(26.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showBackButton) {
                    IconButton(
                        onClick = { AppState.navigateBack() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accent.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            style = AmazeTheme.typography.caption.copy(
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isModuleLoading && (moduleSyncText ?: syncStatus) != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = moduleSyncText ?: syncStatus ?: "",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { AppState.openCommandPalette() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.accent.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search App",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showSyncButton) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val syncAction: () -> Unit = {
                        if (effectiveRefresh != null) {
                            effectiveRefresh()
                        } else if (effectiveModules.isNotEmpty()) {
                            SyncEngine.setShowSyncDialog(true)
                            AppState.loadAllData()
                        } else {
                            AppState.loadAllData()
                        }
                    }
                    IconButton(
                        onClick = syncAction,
                        enabled = !isModuleLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accent.copy(alpha = if (isModuleLoading) 0.20f else 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Sync Data",
                            tint = colors.accent,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    if (isModuleLoading) {
                                        rotationZ = rotationAngle
                                    }
                                }
                        )
                    }
                }
            }
        }
        
        val currentLiveClass by AppState.currentLiveClass.collectAsState()
        val tick by AppState.liveClassTick.collectAsState()
        
        androidx.compose.animation.AnimatedVisibility(
            visible = currentLiveClass != null,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
        ) {
            currentLiveClass?.let { cls ->
                Spacer(modifier = Modifier.height(14.dp))
                DynamicIslandLiveClass(
                    cls = cls,
                    tick = tick,
                    colors = colors
                )
            }
        }
        } // Closing for Column
    }
}

@Composable
fun DynamicIslandLiveClass(
    cls: CourseAttendanceInfo,
    tick: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val livePulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "livePulse")
    val liveBgAlpha by livePulse.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.80f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "liveBgAlpha"
    )
    
    val remaining = remember(tick) { com.amazecc.app.shared.utils.AttendanceTimetable.remainingMinutes(cls.time) }
    val minsStr = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m" else "${remaining}m"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.5.dp, colors.accent.copy(alpha = liveBgAlpha), RoundedCornerShape(32.dp))
            .clickable { cls.courseCode?.let { AppState.openCourseDetail(it) } }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = liveBgAlpha))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "LIVE NOW",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = cls.courseTitle ?: "Class",
                        style = AmazeTheme.typography.body.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$minsStr left",
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

// ── 1. UNIFIED ACADEMICS SCREEN (TABS: ATTENDANCE, MARKS, SCHEDULE) ──

