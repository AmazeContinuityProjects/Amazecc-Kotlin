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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One Moodle assignment, mapped from the scraped MoodleAssignment. */
@Immutable
data class M3EMoodleAssignment(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val title: String,
    val due: String,
    val isDone: Boolean,
    val url: String? = null,
    val teachers: List<String> = emptyList()
)

/** Immutable, screen-owned state for [M3EMoodleScreen]. */
@Immutable
data class M3EMoodleState(
    val assignments: List<M3EMoodleAssignment> = emptyList(),
    val showLogin: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
    val hasSavedCredentials: Boolean = false,
    val isLoading: Boolean = false
)

/** User intents emitted by [M3EMoodleScreen]. */
sealed interface M3EMoodleAction {
    data class UpdateUsername(val value: String) : M3EMoodleAction
    data class UpdatePassword(val value: String) : M3EMoodleAction
    data object SubmitCredentials : M3EMoodleAction
    data object ClearCredentials : M3EMoodleAction
    data class OpenAssignment(val url: String) : M3EMoodleAction
    data object Refresh : M3EMoodleAction
}

/**
 * Material 3 Expressive Moodle LMS screen.
 * 100% stateless: receives [M3EMoodleState] and emits [M3EMoodleAction].
 * The host collects AppState.moodleData (online scraper / offline cache) and owns
 * the credential side effects via SettingsManager / AmazeClient.
 */
@Composable
fun M3EMoodleScreen(
    state: M3EMoodleState,
    onAction: (M3EMoodleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    if (state.showLogin) {
        MoodleLoginContent(state = state, onAction = onAction, modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Moodle LMS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = colors.onSurface
                        )
                        Text(
                            text = "Assignments & coursework",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onAction(M3EMoodleAction.Refresh) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reconnect to Moodle",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (state.isLoading) {
                item(key = "progress") {
                    M3EWavyLinearProgress(
                        progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                        label = "Assignment completion"
                    )
                }
            }

            // A failed sync must surface the real error first — never the cheerful
            // "all caught up" empty state as though the sync succeeded.
            state.errorMessage?.let { message ->
                item(key = "sync_error") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.error.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Moodle sync failed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.error
                                )
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            TextButton(onClick = { onAction(M3EMoodleAction.Refresh) }) {
                                Text("Retry sync", color = colors.primary)
                            }
                        }
                    }
                }
            }

            if (state.assignments.isEmpty()) {
                // Only celebrate an empty inbox when we actually know the sync succeeded.
                if (state.errorMessage == null) {
                    item(key = "empty") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No pending assignments",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "You're all caught up!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(state.assignments, key = { it.id }) { assignment ->
                    MoodleAssignmentCard(
                        assignment = assignment,
                        onOpen = { url -> onAction(M3EMoodleAction.OpenAssignment(url)) }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

@Composable
private fun MoodleLoginContent(
    state: M3EMoodleState,
    onAction: (M3EMoodleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Connect to Moodle",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = colors.onSurface
                )
                Text(
                    text = "Save credentials to sync assignments persistently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }

        item(key = "login_card") {
            M3ESurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerLevel = SurfaceContainerLevel.HIGH,
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onAction(M3EMoodleAction.UpdateUsername(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Registration Number / Username") },
                        singleLine = true,
                        shape = CircleShape,
                        colors = moodleFieldColors(colors)
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(M3EMoodleAction.UpdatePassword(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape,
                        colors = moodleFieldColors(colors)
                    )

                    state.errorMessage?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error
                            )
                        }
                    }

                    if (state.isAuthenticating) {
                        M3EWavyLinearProgress(
                            progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                            label = "Moodle sign-in"
                        )
                    } else {
                        ElevatedButton(
                            onClick = { onAction(M3EMoodleAction.SubmitCredentials) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) {
                            Text(
                                text = if (state.hasSavedCredentials) "Update & Sync Assignments" else "Save & Sync Assignments",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (state.hasSavedCredentials) {
                            TextButton(
                                onClick = { onAction(M3EMoodleAction.ClearCredentials) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear Saved Credentials", color = colors.error)
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

@Composable
private fun moodleFieldColors(
    colors: com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerLow,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant
    )

@Composable
private fun MoodleAssignmentCard(
    assignment: M3EMoodleAssignment,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = if (assignment.isDone) SurfaceContainerLevel.LOW else SurfaceContainerLevel.HIGH
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.courseCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = colors.primary
                    )
                    Text(
                        text = assignment.courseTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = assignment.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (assignment.teachers.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = assignment.teachers.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (assignment.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = if (assignment.isDone) "Submitted" else "Pending",
                    tint = if (assignment.isDone) colors.primary else colors.tertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = assignment.due.ifBlank { "No deadline" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                assignment.url?.let { url ->
                    TextButton(onClick = { onOpen(url) }) {
                        Text("Open in Browser", color = colors.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewMoodleState() = M3EMoodleState(
    assignments = listOf(
        M3EMoodleAssignment(
            id = "a1",
            courseCode = "BAMAT209",
            courseTitle = "Mathematical Foundations for Computation",
            title = "Digital Assignment 2",
            due = "Sunday, 4 October 2026, 12:00 AM",
            isDone = false,
            url = "https://moodle.vit.ac.in/mod/assign/view.php?id=1",
            teachers = listOf("Dr. S. Jayalakshmi")
        )
    )
)

@Preview
@Composable
private fun M3EMoodleScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EMoodleScreen(state = previewMoodleState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EMoodleLoginPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EMoodleScreen(
            state = M3EMoodleState(showLogin = true, hasSavedCredentials = true, username = "21BCE1042"),
            onAction = {}
        )
    }
}

/** D-119 host contract: a real sync failure is passed with showLogin = false. */
@Preview
@Composable
private fun M3EMoodleSyncFailurePreview() {
    AmazeM3ETheme(isDark = false) {
        M3EMoodleScreen(
            state = M3EMoodleState(
                showLogin = false,
                errorMessage = "Moodle is unreachable (HTTP 503). Cached assignments may be stale."
            ),
            onAction = {}
        )
    }
}
