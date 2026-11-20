package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.LatexViewer
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

// ---------------------------------------------------------------------------
// Immutable state models — every value is a snapshot copied by the host.
// ---------------------------------------------------------------------------

@Immutable
data class M3EQBankPaperItem(val title: String, val link: String, val type: String)

@Immutable
data class M3EQBankQuestionItem(
    val id: String,
    val text: String,
    val type: String,
    val options: Map<String, String>?,
    val correctAnswer: String?,
    val marks: Int?,
    val topic: String?,
    val examSemester: String?
)

enum class M3EQBankWorkspaceTab(val label: String) {
    PRACTICE("Practice Mode"),
    ARCHIVE("Paper Archive")
}

enum class M3EQBankPracticeFilter(val label: String) {
    ALL("All"),
    FLAGGED("Flagged"),
    ANALYSIS("Analysis")
}

enum class M3EQBankWorkspaceView { OVERVIEW, QUIZ, REVIEW }

enum class M3EQBankQueueStatus { PENDING, CORRECT, WRONG, REVIEWED }

@Immutable
data class M3EQBankTopicStatItem(
    val topic: String,
    val attempts: Int,
    val correct: Int,
    val totalTimeSec: Long
)

@Immutable
data class M3EQBankCourseStatsItem(
    val courseCode: String,
    val attempts: Int = 0,
    val correct: Int = 0,
    val topics: List<M3EQBankTopicStatItem> = emptyList(),
    val missedIds: List<String> = emptyList()
)

@Immutable
data class M3EQBankSwotConfigItem(
    val strengthMinAttempts: Int = 3,
    val strengthAccuracy: Int = 70,
    val weaknessMinAttempts: Int = 2,
    val weaknessAccuracy: Int = 50
)

@Immutable
data class M3EQBankUploadState(
    val isOpen: Boolean = false,
    val title: String = "",
    val link: String = "",
    val type: String = "CAT 1",
    val status: String? = null,
    val isUploading: Boolean = false
)

@Immutable
data class M3EQBankWorkspaceState(
    val courseCode: String,
    val courseTitle: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val questions: List<M3EQBankQuestionItem> = emptyList(),
    val papers: List<M3EQBankPaperItem> = emptyList(),
    val tab: M3EQBankWorkspaceTab = M3EQBankWorkspaceTab.PRACTICE,
    val view: M3EQBankWorkspaceView = M3EQBankWorkspaceView.OVERVIEW,
    val filter: M3EQBankPracticeFilter = M3EQBankPracticeFilter.ALL,
    val activeIndex: Int = 0,
    val userAnswers: Map<String, String> = emptyMap(),
    val revealed: Map<String, Boolean> = emptyMap(),
    val flagged: Map<String, Boolean> = emptyMap(),
    val sessionResults: Map<String, Boolean?> = emptyMap(),
    val secondsLeft: Int = 0,
    val totalSeconds: Int = 60,
    val timerEnabled: Boolean = true,
    val stats: M3EQBankCourseStatsItem? = null,
    val reviewQueue: List<M3EQBankQuestionItem> = emptyList(),
    val showSummary: Boolean = false,
    val upload: M3EQBankUploadState = M3EQBankUploadState(),
    val swotConfig: M3EQBankSwotConfigItem = M3EQBankSwotConfigItem(),
    val showSwotConfig: Boolean = false
)

/** User intents emitted by [M3EQBankWorkspaceScreen]; all effects live in the host. */
sealed interface M3EQBankWorkspaceAction {
    data object Back : M3EQBankWorkspaceAction
    data object Refresh : M3EQBankWorkspaceAction
    data class SelectTab(val tab: M3EQBankWorkspaceTab) : M3EQBankWorkspaceAction
    data class SelectFilter(val filter: M3EQBankPracticeFilter) : M3EQBankWorkspaceAction
    data class OpenQuestion(val index: Int) : M3EQBankWorkspaceAction
    data class SetAnswer(val questionId: String, val value: String) : M3EQBankWorkspaceAction
    data object Reveal : M3EQBankWorkspaceAction
    data object ToggleFlag : M3EQBankWorkspaceAction
    data object ToggleTimer : M3EQBankWorkspaceAction
    data object NextQuestion : M3EQBankWorkspaceAction
    data object PreviousQuestion : M3EQBankWorkspaceAction
    data class SelectQueueIndex(val index: Int) : M3EQBankWorkspaceAction
    data object LaunchReview : M3EQBankWorkspaceAction
    data object DismissSummary : M3EQBankWorkspaceAction
    data object FinishSession : M3EQBankWorkspaceAction
    data object ReviewMissed : M3EQBankWorkspaceAction
    data object ToggleUploadForm : M3EQBankWorkspaceAction
    data class UpdateUploadTitle(val value: String) : M3EQBankWorkspaceAction
    data class UpdateUploadLink(val value: String) : M3EQBankWorkspaceAction
    data class UpdateUploadType(val value: String) : M3EQBankWorkspaceAction
    data object SubmitUpload : M3EQBankWorkspaceAction
    data class OpenPaper(val paper: M3EQBankPaperItem) : M3EQBankWorkspaceAction
    data object OpenSwotConfig : M3EQBankWorkspaceAction
    data object CloseSwotConfig : M3EQBankWorkspaceAction
    data class SaveSwotConfig(val config: M3EQBankSwotConfigItem) : M3EQBankWorkspaceAction
}

// ---------------------------------------------------------------------------
// Pure grading / formatting helpers (shared with the stateful host).
// ---------------------------------------------------------------------------

internal fun qbankIsDescriptive(question: M3EQBankQuestionItem): Boolean =
    question.options.isNullOrEmpty()

internal fun qbankIsAnswerCorrect(question: M3EQBankQuestionItem, userAnswer: String): Boolean {
    if (userAnswer.isBlank()) return false
    val correct = question.correctAnswer?.trim() ?: return false
    val opts = question.options
    if (opts.isNullOrEmpty()) return userAnswer.trim().equals(correct, ignoreCase = true)
    val keyMatch = opts.keys.firstOrNull { it.equals(correct, ignoreCase = true) }
    return if (keyMatch != null) {
        userAnswer.equals(keyMatch, ignoreCase = true)
    } else {
        opts.values.any { it.trim().equals(userAnswer.trim(), ignoreCase = true) }
    }
}

internal fun qbankCorrectAnswerLabel(question: M3EQBankQuestionItem): String {
    val correct = question.correctAnswer?.trim() ?: return "Not provided"
    val opts = question.options
    if (opts.isNullOrEmpty()) return correct
    val keyMatch = opts.keys.firstOrNull { it.equals(correct, ignoreCase = true) }
    if (keyMatch != null) return "${keyMatch.uppercase()}. ${opts[keyMatch] ?: ""}"
    opts.values.firstOrNull { it.trim().equals(correct, ignoreCase = true) }?.let { return it }
    return correct
}

private fun qbankFormatTime(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

private enum class QBankSwotQuadrant { STRENGTH, WEAKNESS, OPPORTUNITY, THREAT }

private fun classifyTopic(st: M3EQBankTopicStatItem, cfg: M3EQBankSwotConfigItem): QBankSwotQuadrant {
    val acc = if (st.attempts == 0) 0f else st.correct.toFloat() / st.attempts
    return when {
        st.attempts >= cfg.strengthMinAttempts && acc >= cfg.strengthAccuracy / 100f -> QBankSwotQuadrant.STRENGTH
        st.attempts >= cfg.weaknessMinAttempts && acc < cfg.weaknessAccuracy / 100f -> QBankSwotQuadrant.WEAKNESS
        st.attempts < cfg.strengthMinAttempts -> QBankSwotQuadrant.OPPORTUNITY
        else -> QBankSwotQuadrant.THREAT
    }
}

// ---------------------------------------------------------------------------
// Stateless workspace screen.
// ---------------------------------------------------------------------------

/**
 * Material 3 Expressive question-paper workspace for a single course.
 * 100% stateless: accepts [M3EQBankWorkspaceState], emits [M3EQBankWorkspaceAction].
 * No network, storage, navigation or URI side effects happen here.
 */
@Composable
fun M3EQBankWorkspaceScreen(
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(0.7f), containerLevel = SurfaceContainerLevel.LOW) {
                        Text("Loading question bank…", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        M3EWavyLinearProgress(
                            progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                            label = "Question bank loading"
                        )
                    }
                }
            }

            state.errorMessage != null && !state.isOffline && state.questions.isEmpty() && state.papers.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(0.9f), containerLevel = SurfaceContainerLevel.LOW) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = colors.error, modifier = Modifier.size(40.dp))
                            Text(state.errorMessage ?: "Unable to load", fontSize = 14.sp, color = colors.error)
                            Button(onClick = { onAction(M3EQBankWorkspaceAction.Refresh) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            else -> WorkspaceContent(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun WorkspaceContent(
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit
) {
    val colors = M3ETheme.colors
    val inQuiz = state.view == M3EQBankWorkspaceView.QUIZ || state.view == M3EQBankWorkspaceView.REVIEW

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onAction(M3EQBankWorkspaceAction.Back) },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.courseCode, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    if (state.courseTitle.isNotBlank()) {
                        Text(state.courseTitle, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconButton(
                    onClick = { onAction(M3EQBankWorkspaceAction.Refresh) },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (state.isOffline) {
            item(key = "offline") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW, borderColor = colors.warning) {
                    Text(
                        "Offline — papers and questions need a connection. Your local practice stats below are still available.",
                        fontSize = 13.sp,
                        color = colors.warning
                    )
                }
            }
        }

        if (inQuiz) {
            item(key = "quiz") {
                QuizView(state = state, onAction = onAction)
            }
        } else {
            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3EQBankWorkspaceTab.entries,
                    selectedItem = state.tab,
                    onItemSelected = { onAction(M3EQBankWorkspaceAction.SelectTab(it)) },
                    labelProvider = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.tab == M3EQBankWorkspaceTab.PRACTICE) {
                item(key = "practice_stats") {
                    PracticeStatsCard(state)
                }
                item(key = "filters") {
                    M3EConnectedButtonGroup(
                        items = M3EQBankPracticeFilter.entries,
                        selectedItem = state.filter,
                        onItemSelected = { onAction(M3EQBankWorkspaceAction.SelectFilter(it)) },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (state.filter == M3EQBankPracticeFilter.ANALYSIS) {
                    item(key = "analysis") {
                        AnalysisView(state = state, onAction = onAction)
                    }
                } else {
                    val visible = if (state.filter == M3EQBankPracticeFilter.FLAGGED) {
                        state.questions.filter { state.flagged[it.id] == true }
                    } else {
                        state.questions
                    }
                    if (visible.isEmpty()) {
                        item(key = "no_questions") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        if (state.filter == M3EQBankPracticeFilter.FLAGGED) "No flagged questions yet." else "No questions found for this course.",
                                        fontSize = 14.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(visible, key = { it.id }) { question -> QuestionListItem(question, state, onAction) }
                    }
                }

                val missedCount = state.stats?.missedIds?.size ?: 0
                if (missedCount > 0 && state.filter != M3EQBankPracticeFilter.ANALYSIS) {
                    item(key = "review_missed") {
                        Button(
                            onClick = { onAction(M3EQBankWorkspaceAction.LaunchReview) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Review $missedCount missed question${if (missedCount != 1) "s" else ""}")
                        }
                    }
                }
            } else {
                item(key = "archive") {
                    ArchiveContent(state = state, onAction = onAction)
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(BOTTOM_NAV_PADDING)) }
    }

    if (state.showSummary) SessionSummaryDialog(state = state, onAction = onAction)
    if (state.showSwotConfig) SwotConfigDialog(state = state, onAction = onAction)
}

@Composable
private fun PracticeStatsCard(state: M3EQBankWorkspaceState) {
    val colors = M3ETheme.colors
    val stats = state.stats
    val attempts = stats?.attempts ?: 0
    val correct = stats?.correct ?: 0
    val accuracy = if (attempts == 0) 0f else correct.toFloat() / attempts
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Practice performance", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
            Text((accuracy * 100).toFixed(2, "%"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = colors.primary)
            M3EWavyLinearProgress(
                progress = accuracy,
                activeColor = colors.primary,
                trackColor = colors.surfaceContainerHighest,
                label = "Assignment accuracy"
            )
            Text("$correct correct of $attempts attempted · ${state.questions.size} questions available", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestionListItem(
    question: M3EQBankQuestionItem,
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit
) {
    val colors = M3ETheme.colors
    val isAnswered = state.userAnswers[question.id]?.isNotBlank() == true
    val isFlagged = state.flagged[question.id] == true
    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onAction(M3EQBankWorkspaceAction.OpenQuestion(state.questions.indexOfFirst { it.id == question.id }.coerceAtLeast(0))) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LatexViewer(latex = question.text, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 160.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (question.type.isNotBlank()) QBankBadge(question.type, colors.primary)
                if (question.marks != null) QBankBadge("${question.marks} marks", colors.success)
                if (!question.topic.isNullOrBlank()) QBankBadge(question.topic, colors.warning)
            }
            if (!question.examSemester.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(question.examSemester, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (isAnswered || isFlagged) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isAnswered) Text("Answered", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.success)
                    if (isFlagged) Text("Flagged", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.warning)
                }
            }
        }
    }
}

@Composable
private fun QBankBadge(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Paper archive + upload
// ---------------------------------------------------------------------------

@Composable
private fun ArchiveContent(
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit
) {
    val colors = M3ETheme.colors
    val upload = state.upload

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share a Paper?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Contribute a past paper for your course", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                TextButton(onClick = { onAction(M3EQBankWorkspaceAction.ToggleUploadForm) }) {
                    Text(if (upload.isOpen) "Cancel" else "Upload", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (upload.isOpen) {
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Share a Paper", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    OutlinedTextField(
                        value = upload.title,
                        onValueChange = { onAction(M3EQBankWorkspaceAction.UpdateUploadTitle(it)) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upload.link,
                        onValueChange = { onAction(M3EQBankWorkspaceAction.UpdateUploadLink(it)) },
                        label = { Text("Link (GDrive, Dropbox…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Paper type", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    val types = listOf("CAT 1", "CAT 2", "FAT", "Quiz", "Assignment")
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { type ->
                            val selected = upload.type == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.primary else colors.surfaceContainerHigh)
                                    .clickable { onAction(M3EQBankWorkspaceAction.UpdateUploadType(type)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(type, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) colors.onPrimary else colors.onSurface)
                            }
                        }
                    }
                    Button(
                        onClick = { onAction(M3EQBankWorkspaceAction.SubmitUpload) },
                        enabled = upload.title.isNotBlank() && upload.link.isNotBlank() && !upload.isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (upload.isUploading) "Uploading…" else "Submit paper")
                    }
                    upload.status?.let { status ->
                        val isSuccess = status.contains("uploaded", ignoreCase = true) || status.contains("success", ignoreCase = true)
                        Text(status, fontSize = 12.sp, color = if (isSuccess) colors.success else colors.error)
                    }
                }
            }
        }

        if (state.papers.isEmpty()) {
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("No papers found in the archive.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
            state.papers.forEach { paper ->
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = { onAction(M3EQBankWorkspaceAction.OpenPaper(paper)) }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(paper.type, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(paper.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Quiz mode
// ---------------------------------------------------------------------------

@Composable
private fun QuizView(
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit
) {
    val colors = M3ETheme.colors
    val queue = if (state.view == M3EQBankWorkspaceView.REVIEW) state.reviewQueue else state.questions
    val question = queue.getOrNull(state.activeIndex)

    if (question == null) {
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
            Text("This question is no longer available.", fontSize = 14.sp, color = colors.onSurfaceVariant)
        }
        return
    }

    val descriptive = qbankIsDescriptive(question)
    val revealed = state.revealed[question.id] == true
    val userAnswer = state.userAnswers[question.id] ?: ""
    val flagged = state.flagged[question.id] == true
    val isCurrent = state.activeIndex
    val statuses = queue.map { q ->
        when {
            state.revealed[q.id] == true && state.sessionResults.containsKey(q.id) ->
                when (state.sessionResults[q.id]) {
                    true -> M3EQBankQueueStatus.CORRECT
                    false -> M3EQBankQueueStatus.WRONG
                    null -> M3EQBankQueueStatus.REVIEWED
                }
            else -> M3EQBankQueueStatus.PENDING
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Question ${isCurrent + 1} of ${queue.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                        if (!question.topic.isNullOrBlank()) Text(question.topic, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    if (state.timerEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (state.secondsLeft <= 10) colors.error.copy(alpha = 0.15f) else colors.surfaceContainerHighest)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(qbankFormatTime(state.secondsLeft), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (state.secondsLeft <= 10) colors.error else colors.onSurface)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { onAction(M3EQBankWorkspaceAction.ToggleTimer) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (state.timerEnabled) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                            contentDescription = if (state.timerEnabled) "Timer on" else "Timer off",
                            tint = if (state.timerEnabled) colors.onSurfaceVariant else colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { onAction(M3EQBankWorkspaceAction.ToggleFlag) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (flagged) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                            contentDescription = if (flagged) "Unflag question" else "Flag question",
                            tint = if (flagged) colors.warning else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                M3EWavyLinearProgress(
                    progress = (state.activeIndex + 1).toFloat() / queue.size.coerceAtLeast(1),
                    activeColor = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    label = "Question queue progress"
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    queue.indices.forEach { i ->
                        val status = statuses.getOrElse(i) { M3EQBankQueueStatus.PENDING }
                        val bg = when {
                            i == state.activeIndex -> colors.primary
                            status == M3EQBankQueueStatus.CORRECT -> colors.success
                            status == M3EQBankQueueStatus.WRONG -> colors.error
                            status == M3EQBankQueueStatus.REVIEWED -> colors.warning
                            else -> colors.surfaceContainerHighest
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable { onAction(M3EQBankWorkspaceAction.SelectQueueIndex(i)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (i == state.activeIndex || status == M3EQBankQueueStatus.CORRECT || status == M3EQBankQueueStatus.WRONG) colors.onPrimary else colors.onSurface)
                        }
                    }
                }
            }
        }

        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LatexViewer(latex = question.text, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 260.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (question.type.isNotBlank()) QBankBadge(question.type, colors.primary)
                    if (question.marks != null) QBankBadge("${question.marks} marks", colors.success)
                }

                if (!question.options.isNullOrEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    question.options.keys.sortedBy { it.lowercase() }.forEach { key ->
                        val value = question.options[key] ?: return@forEach
                        val isSelected = userAnswer == key
                        val isCorrectKey = revealed && key.equals(question.correctAnswer?.trim(), ignoreCase = true)
                        val isWrongPick = revealed && isSelected && !key.equals(question.correctAnswer?.trim(), ignoreCase = true)
                        QuizOptionRow(
                            key = key,
                            value = value,
                            isSelected = isSelected,
                            isCorrectKey = isCorrectKey,
                            isWrongPick = isWrongPick,
                            onClick = { onAction(M3EQBankWorkspaceAction.SetAnswer(question.id, key)) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (revealed) "Model answer shown below — review it, then move on."
                        else "Descriptive question — think through your answer, then reveal the model answer.",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                if (revealed) {
                    val isCorrectFinal = !descriptive && userAnswer.isNotBlank() && qbankIsAnswerCorrect(question, userAnswer)
                    val bannerColor = when { descriptive -> colors.warning; isCorrectFinal -> colors.success; else -> colors.error }
                    val bannerTitle = when { descriptive -> "Reviewed"; isCorrectFinal -> "Correct!"; else -> "Incorrect" }
                    Spacer(Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bannerColor.copy(alpha = 0.12f)).padding(12.dp)) {
                        Column {
                            Text(bannerTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bannerColor)
                            Spacer(Modifier.height(4.dp))
                            Text("${if (descriptive) "Model answer" else "Correct answer"}: ${qbankCorrectAnswerLabel(question)}", fontSize = 13.sp, color = colors.onSurface)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onAction(M3EQBankWorkspaceAction.PreviousQuestion) }, enabled = state.activeIndex > 0) {
                        Text("‹", fontSize = 22.sp)
                    }
                    if (!revealed) {
                        Button(
                            onClick = { onAction(M3EQBankWorkspaceAction.Reveal) },
                            enabled = descriptive || userAnswer.isNotBlank()
                        ) {
                            Text(if (descriptive) "Reveal answer" else "Check answer")
                        }
                    } else {
                        val isCorrectFinal = !descriptive && userAnswer.isNotBlank() && qbankIsAnswerCorrect(question, userAnswer)
                        val resultColor = when { descriptive -> colors.warning; isCorrectFinal -> colors.success; else -> colors.error }
                        val resultLabel = when { descriptive -> "Reviewed"; isCorrectFinal -> "Correct"; else -> "Incorrect" }
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(resultColor.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(resultLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = resultColor)
                        }
                    }
                    FilledTonalIconButton(onClick = { onAction(M3EQBankWorkspaceAction.NextQuestion) }, enabled = state.activeIndex < queue.size - 1) {
                        Text("›", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizOptionRow(
    key: String,
    value: String,
    isSelected: Boolean,
    isCorrectKey: Boolean,
    isWrongPick: Boolean,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val borderColor = when { isCorrectKey -> colors.success; isWrongPick -> colors.error; isSelected -> colors.primary; else -> colors.outlineVariant }
    val bgColor = when {
        isCorrectKey -> colors.success.copy(alpha = 0.12f)
        isWrongPick -> colors.error.copy(alpha = 0.12f)
        isSelected -> colors.primary.copy(alpha = 0.08f)
        else -> colors.surfaceContainerLow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(
                when { isCorrectKey -> colors.success; isWrongPick -> colors.error; isSelected -> colors.primary; else -> colors.surfaceContainerHighest }
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(key.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected || isCorrectKey || isWrongPick) colors.onPrimary else colors.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 14.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// SWOT analysis + config dialog
// ---------------------------------------------------------------------------

@Composable
private fun AnalysisView(
    state: M3EQBankWorkspaceState,
    onAction: (M3EQBankWorkspaceAction) -> Unit
) {
    val colors = M3ETheme.colors
    val stats = state.stats
    val questionTopics = state.questions.mapNotNull { it.topic }.distinct().sorted()
    val allTopics = (questionTopics + (stats?.topics?.map { it.topic } ?: emptyList())).distinct().sorted()
    val attempts = stats?.attempts ?: 0
    val correct = stats?.correct ?: 0
    val accuracy = if (attempts == 0) 0f else correct.toFloat() / attempts
    val missedCount = stats?.missedIds?.size ?: 0

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Topic analysis (SWOT)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text("Built from your local practice history", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { onAction(M3EQBankWorkspaceAction.OpenSwotConfig) }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Configure SWOT thresholds", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Text("${(accuracy * 100).toFixed(2)}% accuracy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                M3EWavyLinearProgress(
                    progress = accuracy,
                    activeColor = colors.success,
                    trackColor = colors.surfaceContainerHighest,
                    label = "Topic accuracy"
                )
                Text("$attempts attempts · ${allTopics.size} topics practiced", fontSize = 12.sp, color = colors.onSurfaceVariant)
                if (missedCount > 0) {
                    Button(onClick = { onAction(M3EQBankWorkspaceAction.LaunchReview) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Review $missedCount missed")
                    }
                }
            }
        }

        if (allTopics.isEmpty()) {
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                Text("Practice some questions to see your strengths and weaknesses.", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        } else {
            val topicStats = allTopics.map { topic -> stats?.topics?.firstOrNull { it.topic == topic } ?: M3EQBankTopicStatItem(topic = topic, attempts = 0, correct = 0, totalTimeSec = 0L) }
            val cfg = state.swotConfig
            val strengths = topicStats.filter { classifyTopic(it, cfg) == QBankSwotQuadrant.STRENGTH }
            val weaknesses = topicStats.filter { classifyTopic(it, cfg) == QBankSwotQuadrant.WEAKNESS }
            val opportunities = topicStats.filter { classifyTopic(it, cfg) == QBankSwotQuadrant.OPPORTUNITY }
            val threats = topicStats.filter { classifyTopic(it, cfg) == QBankSwotQuadrant.THREAT }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { SwotCard("Strengths", colors.success, strengths) }
                Box(Modifier.weight(1f)) { SwotCard("Weaknesses", colors.error, weaknesses) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { SwotCard("Opportunities", colors.primary, opportunities) }
                Box(Modifier.weight(1f)) { SwotCard("Threats", colors.warning, threats) }
            }
        }
    }
}

@Composable
private fun SwotCard(title: String, color: Color, topics: List<M3EQBankTopicStatItem>) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(6.dp))
            if (topics.isEmpty()) {
                Text("Nothing here yet", fontSize = 12.sp, color = colors.onSurfaceVariant)
            } else {
                topics.sortedByDescending { it.attempts }.forEach { st ->
                    val acc = if (st.attempts == 0) 0 else (st.correct.toFloat() / st.attempts * 100).toInt()
                    Text("${st.topic} · ${st.correct}/${st.attempts} ($acc%)", fontSize = 12.sp, color = colors.onSurface, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SwotConfigDialog(state: M3EQBankWorkspaceState, onAction: (M3EQBankWorkspaceAction) -> Unit) {
    val cfg = state.swotConfig
    var strengthAttempts by remember { mutableStateOf(cfg.strengthMinAttempts.toString()) }
    var strengthAccuracy by remember { mutableStateOf(cfg.strengthAccuracy.toString()) }
    var weaknessAttempts by remember { mutableStateOf(cfg.weaknessMinAttempts.toString()) }
    var weaknessAccuracy by remember { mutableStateOf(cfg.weaknessAccuracy.toString()) }

    fun parse(value: String, fallback: Int) = value.toIntOrNull()?.coerceIn(1, 100) ?: fallback
    fun digitsOnly(value: String) = value.filter { ch -> ch.isDigit() }

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = { onAction(M3EQBankWorkspaceAction.CloseSwotConfig) },
        title = "SWOT thresholds"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(value = strengthAttempts, onValueChange = { strengthAttempts = digitsOnly(it) }, label = { Text("Strengths — min attempts") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = strengthAccuracy, onValueChange = { strengthAccuracy = digitsOnly(it) }, label = { Text("Strengths — accuracy %") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = weaknessAttempts, onValueChange = { weaknessAttempts = digitsOnly(it) }, label = { Text("Weaknesses — min attempts") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = weaknessAccuracy, onValueChange = { weaknessAccuracy = digitsOnly(it) }, label = { Text("Weaknesses — accuracy %") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3EQBankWorkspaceAction.CloseSwotConfig) }) { Text("Cancel") }
                TextButton(onClick = {
                    onAction(
                        M3EQBankWorkspaceAction.SaveSwotConfig(
                            M3EQBankSwotConfigItem(
                                strengthMinAttempts = parse(strengthAttempts, cfg.strengthMinAttempts),
                                strengthAccuracy = parse(strengthAccuracy, cfg.strengthAccuracy),
                                weaknessMinAttempts = parse(weaknessAttempts, cfg.weaknessMinAttempts),
                                weaknessAccuracy = parse(weaknessAccuracy, cfg.weaknessAccuracy)
                            )
                        )
                    )
                }) { Text("Save") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session summary dialog
// ---------------------------------------------------------------------------

@Composable
private fun SessionSummaryDialog(state: M3EQBankWorkspaceState, onAction: (M3EQBankWorkspaceAction) -> Unit) {
    val colors = M3ETheme.colors
    val results = state.sessionResults
    val total = results.size
    val correct = results.values.count { it == true }
    val reviewed = results.values.count { it == null }
    val missedCount = state.stats?.missedIds?.size ?: 0

    val topicRows = state.questions.map { it.id to (it.topic?.takeIf { t -> t.isNotBlank() } ?: "General") }.toMap()
    val breakdown = results.entries
        .map { (id, res) -> (topicRows[id] ?: "General") to (if (res == true) 1 else 0) }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.size to it.value.sum() }
        .toList()
        .sortedByDescending { if (it.second.first == 0) 0f else it.second.second.toFloat() / it.second.first }

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = { onAction(M3EQBankWorkspaceAction.DismissSummary) },
        title = "Practice session done"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("You got $correct of $total right${if (reviewed > 0) " and reviewed $reviewed descriptive" else ""}.", fontSize = 13.sp, color = colors.onSurfaceVariant)
            breakdown.forEach { (topic, pair) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(topic, fontSize = 12.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Text("${pair.second}/${pair.first}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                }
            }
            if (missedCount > 0) {
                Button(onClick = { onAction(M3EQBankWorkspaceAction.ReviewMissed) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Review $missedCount missed")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(M3EQBankWorkspaceAction.DismissSummary) }) { Text("Keep practising") }
                TextButton(onClick = { onAction(M3EQBankWorkspaceAction.FinishSession) }) { Text("Finish") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews (neutral sample data, light + dark)
// ---------------------------------------------------------------------------

private fun sampleWorkspaceState() = M3EQBankWorkspaceState(
    courseCode = "CSE3002",
    courseTitle = "Internet and Web Programming",
    questions = listOf(
        M3EQBankQuestionItem("q1", "What does HTTP stand for?", "MCQ", mapOf("a" to "Hyper Text Transfer Protocol", "b" to "High Text Transfer Protocol"), "a", 1, "Web", "Winter 2025"),
        M3EQBankQuestionItem("q2", "Explain the REST architectural style.", "Descriptive", null, "Representational state transfer", 10, "Web", "Winter 2025")
    ),
    papers = listOf(M3EQBankPaperItem("CAT 1 — 2025", "https://example.com/paper", "CAT 1")),
    stats = M3EQBankCourseStatsItem("CSE3002", 4, 3, listOf(M3EQBankTopicStatItem("Web", 4, 3, 240)), listOf("q2"))
)

@Preview
@Composable
private fun M3EQBankWorkspaceScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EQBankWorkspaceScreen(state = sampleWorkspaceState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EQBankWorkspaceScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EQBankWorkspaceScreen(state = sampleWorkspaceState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EQBankWorkspaceQuizPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EQBankWorkspaceScreen(
            state = sampleWorkspaceState().copy(
                view = M3EQBankWorkspaceView.QUIZ,
                activeIndex = 0,
                totalSeconds = 60,
                secondsLeft = 42,
                userAnswers = mapOf("q1" to "a")
            ),
            onAction = {}
        )
    }
}
