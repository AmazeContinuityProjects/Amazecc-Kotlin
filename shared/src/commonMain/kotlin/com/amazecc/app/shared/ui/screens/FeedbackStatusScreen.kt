package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.FeedbackSemester
import com.amazecc.app.shared.model.FeedbackTableRow
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class SemesterFeedbackState(
    val semester: FeedbackSemester,
    val rows: List<FeedbackTableRow>,
    val isExpanded: Boolean = true
)

@Composable
fun FeedbackStatusScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var semestersData by remember { mutableStateOf<List<SemesterFeedbackState>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        refreshSession()
        loadFeedback { data, err ->
            semestersData = data
            error = err
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Feedback Status",
            description = "Course feedback status",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = {
                scope.launch {
                    loading = true
                    error = null
                    refreshSession()
                    loadFeedback { data, err ->
                        semestersData = data
                        error = err
                        loading = false
                    }
                }
            }
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    Text("Loading feedback status...", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text(error ?: "Unknown error", color = colors.danger)
                    }
                }
            }
        } else if (semestersData.isEmpty() || semestersData.all { it.rows.isEmpty() }) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    Text("No feedback data available", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
            ) {
                item { HeaderSpacer() }
                items(semestersData, key = { it.semester.value }) { semData ->
                    FeedbackSemesterCard(
                        state = semData,
                        onToggleExpand = {
                            semestersData = semestersData.map {
                                if (it.semester.value == semData.semester.value) it.copy(isExpanded = !it.isExpanded) else it
                            }
                        },
                        colors = colors
                    )
                }
            }
        }
    }
}

private suspend fun refreshSession() {
    val creds = SettingsManager.getCredentials() ?: return
    try {
        val loginRes = AmazeClient.login(creds.first, creds.second)
        if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
            SessionManager.saveSession(
                cookies = loginRes.cookies,
                csrf = loginRes.csrf,
                authorizedID = loginRes.authorizedID,
                clubToken = loginRes.clubToken
            )
            SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
            SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
            loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
        }
    } catch (_: Exception) { }
}

private suspend fun loadFeedback(onResult: (List<SemesterFeedbackState>, String?) -> Unit) {
    try {
        val initialRes = AmazeClient.getFeedbackStatus()
        if (!initialRes.success) {
            onResult(emptyList(), initialRes.error ?: "Failed to load feedback status")
            return
        }

        val sems = initialRes.semesters ?: emptyList()
        val initialRows = initialRes.feedbackTable ?: emptyList()
        val initialSelectedIdx = sems.indexOfFirst { it.selected }

        val stateList = coroutineScope {
            sems.mapIndexed { idx, sem ->
                async {
                    if (idx == initialSelectedIdx) {
                        SemesterFeedbackState(sem, initialRows, isExpanded = true)
                    } else {
                        val res = AmazeClient.getFeedbackStatus(sem.value)
                        SemesterFeedbackState(sem, res.feedbackTable ?: emptyList(), isExpanded = false)
                    }
                }
            }.awaitAll()
        }

        onResult(stateList, null)
    } catch (e: Exception) {
        onResult(emptyList(), e.message)
    }
}

@Composable
private fun FeedbackSemesterCard(
    state: SemesterFeedbackState,
    onToggleExpand: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val doneCount = state.rows.count {
        (it.midSemester?.lowercase()?.contains("given") == true) ||
        (it.teeSemester?.lowercase()?.contains("given") == true)
    }
    val totalCount = state.rows.size
    val isComplete = doneCount == totalCount && totalCount > 0

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                        contentDescription = "Expand", tint = colors.textSecondary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Text(state.semester.text, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Box(
                    modifier = Modifier.background(
                        if (isComplete) colors.success.copy(alpha = 0.15f) else colors.danger.copy(alpha = 0.15f),
                        RoundedCornerShape(AmazeTheme.radius.xs)
                    ).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("$doneCount/$totalCount done", style = AmazeTheme.typography.caption.copy(
                        color = if (isComplete) colors.success else colors.danger, fontWeight = FontWeight.Bold
                    ))
                }
            }

            AnimatedVisibility(
                visible = state.isExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.rows.isEmpty()) {
                        Text("No courses found", style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
                    } else {
                        state.rows.forEach { row -> FeedbackRowItem(row, colors) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackRowItem(row: FeedbackTableRow, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val midGiven = row.midSemester?.lowercase()?.contains("given") == true
    val teeGiven = row.teeSemester?.lowercase()?.contains("given") == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.05f), RoundedCornerShape(AmazeTheme.radius.small))
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Book, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
            Text(row.feedbackType ?: "N/A", style = AmazeTheme.typography.body.copy(color = colors.textPrimary), maxLines = 1)
        }
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FeedbackBadge(text = "Mid Sem", isGiven = midGiven, colors = colors)
            FeedbackBadge(text = "TEE", isGiven = teeGiven, colors = colors)
        }
    }
}

@Composable
private fun FeedbackBadge(text: String, isGiven: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(
        modifier = Modifier.background(
            if (isGiven) colors.success.copy(alpha = 0.15f) else colors.danger.copy(alpha = 0.15f),
            RoundedCornerShape(AmazeTheme.radius.xs)
        ).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGiven) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null, tint = if (isGiven) colors.success else colors.danger, modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
        Text(text, style = AmazeTheme.typography.caption.copy(color = if (isGiven) colors.success else colors.danger, fontWeight = FontWeight.Bold))
    }
}
