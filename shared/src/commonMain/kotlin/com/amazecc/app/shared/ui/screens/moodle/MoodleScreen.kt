package com.amazecc.app.shared.ui.screens.moodle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.MoodleAssignment
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun MoodleScreen() {
    val colors = AmazeTheme.colors
    val moodleData by AppState.moodleData.collectAsState()
    
    var showLogin by remember { mutableStateOf(moodleData == null) }
    
    // Automatically show login if data is cleared
    LaunchedEffect(moodleData) {
        if (moodleData == null) {
            showLogin = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Moodle LMS",
            description = "Track your assignments and coursework",
            showBackButton = true,
            showSyncButton = !showLogin,
            onRefresh = { showLogin = true }
        )

        if (showLogin) {
            Column(modifier = Modifier.fillMaxSize()) {
                com.amazecc.app.shared.ui.components.HeaderSpacer()
                MoodleLoginView(
                    onLoginSuccess = { showLogin = false }
                )
            }
        } else {
            val assignments = moodleData?.data ?: emptyList()
            if (assignments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.successText, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No pending assignments", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("You're all caught up!", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = AmazeTheme.spacing.pageHorizontal),
                    verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item {
                        com.amazecc.app.shared.ui.components.HeaderSpacer()
                    }
                    items(assignments) { assignment ->
                        MoodleAssignmentCard(assignment)
                    }
                }
            }
        }
    }
}

@Composable
fun MoodleLoginView(onLoginSuccess: () -> Unit) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val spacing = AmazeTheme.spacing
    val scope = rememberCoroutineScope()
    
    val storedCreds = SettingsManager.getMoodleCredentials()
    var username by remember { mutableStateOf(storedCreds?.first ?: "") }
    var password by remember { mutableStateOf(storedCreds?.second ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.accent, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(spacing.lg))
        Text("Connect to Moodle", style = AmazeTheme.typography.heading.copy(color = colors.textPrimary))
        Text("Enter your credentials to sync assignments persistently", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
        
        Spacer(modifier = Modifier.height(spacing.xl))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Registration Number / Username") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            shape = RoundedCornerShape(radius.small)
        )
        
        Spacer(modifier = Modifier.height(spacing.md))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            shape = RoundedCornerShape(radius.small)
        )
        
        val em = errorMessage
        if (em != null) {
            Spacer(modifier = Modifier.height(spacing.md))
            Text(em, style = AmazeTheme.typography.caption.copy(color = colors.dangerText))
        }
        
        Spacer(modifier = Modifier.height(spacing.xl))
        
        if (isLoading) {
            CircularProgressIndicator(color = colors.accent)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmazeButton(
                    text = if (storedCreds != null) "Update & Sync Assignments" else "Save & Sync Assignments",
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both fields"
                            return@AmazeButton
                        }
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            AppState.saveMoodleCredentials(username, password)
                            val res = AmazeClient.fetchMoodleData(username, password)
                            isLoading = false
                            if (res.success) {
                                AppState.updateMoodleData(res)
                                onLoginSuccess()
                            } else {
                                errorMessage = res.message ?: "Authentication failed"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (storedCreds != null) {
                    OutlinedButton(
                        onClick = {
                            SettingsManager.clearMoodleCredentials()
                            username = ""
                            password = ""
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(radius.small)
                    ) {
                        Text("Clear Saved Credentials", style = AmazeTheme.typography.body.copy(color = colors.dangerText))
                    }
                }
            }
        }
    }
}

@Composable
fun MoodleAssignmentCard(assignment: MoodleAssignment) {
    val colors = AmazeTheme.colors
    
    val courseCode = assignment.courseCode
    val courseTitle = assignment.courseTitle
    val taskName = assignment.taskTitle
    
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (courseCode.isNotEmpty()) {
                            Text(
                                text = courseCode,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = courseTitle,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (taskName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = taskName,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = assignment.name,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    if (assignment.teachers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = assignment.teachers.joinToString(", "),
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (assignment.done) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.successText)
                } else {
                    Icon(Icons.Rounded.Warning, null, tint = colors.warningText)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(assignment.due, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary))
                }
                if (!assignment.url.isNullOrEmpty()) {
                    Text("Open in Browser", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                }
            }
        }
    }
}
