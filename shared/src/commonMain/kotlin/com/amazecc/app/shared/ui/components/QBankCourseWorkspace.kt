package com.amazecc.app.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private const val QUIZ_MCQ_SECONDS = 60
private const val QUIZ_TEXT_SECONDS = 120

/**
 * Full QBank course workspace: question list (All/Flagged/Analysis/Review),
 * timed quiz mode, missed-question review, topic analysis, paper archive with
 * paper upload, and the practice session summary. Shared by the main QBank
 * screen (standalone) and per-course tabs (embedded, course preselected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankCourseWorkspace(
    courseCode: String,
    courseTitle: String,
    embedded: Boolean = false,
    startInQuiz: Boolean = false,
    onExit: () -> Unit = {}
) {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var activeQuestionIndex by remember { mutableStateOf<Int?>(null) }
    val userAnswers = remember { mutableStateMapOf<String, String>() }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val flagged = remember { mutableStateMapOf<String, Boolean>() }
    val sessionResults = remember { mutableStateMapOf<String, Boolean?>() }
    val sessionTimeSpent = remember { mutableStateMapOf<String, Long>() }
    val questionStartedAt = remember { mutableStateMapOf<String, Long>() }
    var showSessionSummary by remember { mutableStateOf(false) }
    var quizTimerEnabled by remember { mutableStateOf(SettingsManager.isQuizTimerEnabled()) }
    var qbankStats by remember { mutableStateOf(SettingsManager.getQBankStats()) }
    var showAnalysis by remember { mutableStateOf(false) }
    var reviewQueue by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var reviewIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf("Practice Mode") }
    val tabs = listOf("Practice Mode", "Paper Archive")
    var papers by remember { mutableStateOf<List<QBankPaper>>(emptyList()) }
    var flaggedFilter by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var showPaperUpload by remember { mutableStateOf(false) }
    var paperTitle by remember { mutableStateOf("") }
    var paperLink by remember { mutableStateOf("") }
    var paperType by remember { mutableStateOf("CAT 1") }
    var paperUploadStatus by remember { mutableStateOf<String?>(null) }
    var paperUploading by remember { mutableStateOf(false) }

    fun resetQuizState() {
        userAnswers.clear()
        revealed.clear()
        flagged.clear()
        sessionResults.clear()
        sessionTimeSpent.clear()
        questionStartedAt.clear()
        activeQuestionIndex = null
        reviewQueue = emptyList()
        reviewIndex = null
        flaggedFilter = false
        showAnalysis = false
        showSessionSummary = false
    }

    fun timeSpentMs(qid: String): Long {
        val start = questionStartedAt[qid] ?: return 0L
        return (Clock.System.now().toEpochMilliseconds() - start).coerceAtLeast(0L)
    }

    fun recordAnswer(q: QBankQuestion, overwrite: Boolean = false) {
        if (!overwrite && sessionResults.containsKey(q.question_id)) return
        val spentMs = timeSpentMs(q.question_id)
        sessionTimeSpent[q.question_id] = spentMs
        val correct = if (isDescriptive(q)) null else isAnswerCorrect(q, userAnswers[q.question_id] ?: "")
        sessionResults[q.question_id] = correct
        SettingsManager.recordQBankAnswer(
            courseCode = courseCode,
            topic = q.topic_name,
            correct = correct,
            timeSpentSec = (spentMs / 1000L).toInt(),
            questionId = q.question_id
        )
        qbankStats = SettingsManager.getQBankStats()
    }

    fun launchReview() {
        val missedIds = (qbankStats[courseCode]?.missedQuestionIds ?: emptyList()).toSet()
        val queue = questions.filter { missedIds.contains(it.question_id) }
        if (queue.isNotEmpty()) {
            reviewQueue = queue
            reviewIndex = 0
            flaggedFilter = false
            showAnalysis = false
            queue.forEach { q ->
                revealed.remove(q.question_id)
                userAnswers.remove(q.question_id)
                questionStartedAt.remove(q.question_id)
            }
        }
    }

    fun load() {
        questions = emptyList()
        papers = emptyList()
        resetQuizState()
        scope.launch {
            loading = true
            try {
                val qRes = AmazeClient.getQBankQuestions(courseCode)
                if (qRes.success) questions = qRes.data.distinctBy { it.question_id } else error = qRes.message
                val pRes = AmazeClient.getQBankPapers(courseCode)
                if (pRes.success) papers = pRes.data
            } catch (e: Exception) { error = e.message }
            loading = false
            if (startInQuiz && questions.isNotEmpty()) activeQuestionIndex = 0
        }
    }

    LaunchedEffect(courseCode) { load() }

    val inReview = reviewIndex != null
    val currentQuestion = if (inReview) {
        reviewIndex?.let { reviewQueue.getOrNull(it) }
    } else {
        activeQuestionIndex?.let { questions.getOrNull(it) }
    }
    val currentRevealed = currentQuestion?.let { revealed[it.question_id] == true } ?: false
    val quizTotalSeconds = if (currentQuestion?.options.isNullOrEmpty()) QUIZ_TEXT_SECONDS else QUIZ_MCQ_SECONDS
    var secondsLeft by remember(currentQuestion?.question_id) { mutableStateOf(quizTotalSeconds) }

    LaunchedEffect(currentQuestion?.question_id) {
        currentQuestion?.let { q ->
            questionStartedAt[q.question_id] = Clock.System.now().toEpochMilliseconds()
        }
    }

    LaunchedEffect(currentQuestion?.question_id, currentRevealed, quizTimerEnabled) {
        if (currentQuestion == null || currentRevealed || !quizTimerEnabled) return@LaunchedEffect
        secondsLeft = quizTotalSeconds
        while (secondsLeft > 0) {
            delay(1000)
            if (revealed[currentQuestion.question_id] == true) break
            secondsLeft--
        }
        if (secondsLeft <= 0 && revealed[currentQuestion.question_id] != true) {
            revealed[currentQuestion.question_id] = true
            recordAnswer(currentQuestion, overwrite = inReview)
        }
    }

    val sessionAttempts = sessionResults.size
    val sessionCorrect = sessionResults.values.count { it == true }
    val sessionReviewed = sessionResults.values.count { it == null }

    val handleExit: () -> Unit = {
        when {
            inReview && sessionAttempts > 0 && !showSessionSummary -> showSessionSummary = true
            inReview -> {
                reviewIndex = null
                reviewQueue = emptyList()
            }
            activeQuestionIndex != null && sessionAttempts > 0 && !showSessionSummary -> showSessionSummary = true
            activeQuestionIndex != null -> activeQuestionIndex = null
            else -> onExit()
        }
    }

    AppBackHandler(enabled = true) {
        handleExit()
    }

    if (!embedded) {
        ScreenHeader(
            title = when {
                inReview -> "Review Missed"
                activeQuestionIndex != null -> "Quiz Mode"
                else -> courseCode
            },
            description = when {
                inReview -> "${reviewQueue.size} missed questions · ${quizTotalSeconds}s each"
                activeQuestionIndex != null -> "${questions.size} questions · ${quizTotalSeconds}s each"
                else -> courseTitle
            },
            showBackButton = true,
            showSyncButton = true,
            onRefresh = { load() },
            onBackOverride = handleExit,
            enabledScreens = setOf(Screen.QBANK)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CloudOff, null, tint = colors.danger, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text(error ?: Strings.error, color = colors.dangerText)
                        Spacer(Modifier.height(AmazeTheme.spacing.md))
                        Button(onClick = { error = null; load() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            (activeQuestionIndex != null || inReview) && currentQuestion != null -> {
                val queue = if (inReview) reviewQueue else questions
                val qIndex = if (inReview) reviewIndex ?: 0 else activeQuestionIndex ?: 0
                val queueStatuses = queue.map { q ->
                    when {
                        revealed[q.question_id] == true && sessionResults.containsKey(q.question_id) ->
                            when (sessionResults[q.question_id]) {
                                true -> QuizQueueStatus.CORRECT
                                false -> QuizQueueStatus.WRONG
                                null -> QuizQueueStatus.REVIEWED
                            }
                        else -> QuizQueueStatus.PENDING
                    }
                }
                val queueFlags = queue.map { flagged[it.question_id] == true }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    if (embedded) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = handleExit) {
                                    Icon(Icons.Rounded.ExitToApp, null, tint = colors.textSecondary)
                                }
                                Text(
                                    if (inReview) "Review Missed" else "Quiz Mode",
                                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                            }
                        }
                    } else {
                        item { HeaderSpacer() }
                    }
                    item {
                        QuestionQueueStrip(
                            total = queue.size,
                            currentIndex = qIndex,
                            statuses = queueStatuses,
                            flags = queueFlags,
                            attempts = sessionAttempts,
                            correctCount = sessionCorrect,
                            onSelect = { if (it in queue.indices) { if (inReview) reviewIndex = it else activeQuestionIndex = it } }
                        )
                    }
                    item {
                        QuizModeView(
                            question = currentQuestion,
                            index = qIndex,
                            total = queue.size,
                            userAnswer = userAnswers[currentQuestion.question_id] ?: "",
                            onAnswerChange = { userAnswers[currentQuestion.question_id] = it },
                            revealed = currentRevealed,
                            onReveal = {
                                recordAnswer(currentQuestion, overwrite = inReview)
                                revealed[currentQuestion.question_id] = true
                            },
                            flagged = flagged[currentQuestion.question_id] ?: false,
                            onToggleFlag = { flagged[currentQuestion.question_id] = !(flagged[currentQuestion.question_id] ?: false) },
                            secondsLeft = secondsLeft,
                            totalSeconds = quizTotalSeconds,
                            timerEnabled = quizTimerEnabled,
                            onToggleTimer = {
                                quizTimerEnabled = !quizTimerEnabled
                                SettingsManager.setQuizTimerEnabled(quizTimerEnabled)
                            },
                            timeSpentSec = ((sessionTimeSpent[currentQuestion.question_id] ?: 0L) / 1000L).toInt(),
                            onNext = {
                                if (qIndex < queue.size - 1) {
                                    if (inReview) reviewIndex = qIndex + 1 else activeQuestionIndex = qIndex + 1
                                }
                            },
                            onPrev = {
                                if (qIndex > 0) {
                                    if (inReview) reviewIndex = qIndex - 1 else activeQuestionIndex = qIndex - 1
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
                }
            }

            else -> {
                val flaggedCount = questions.count { flagged[it.question_id] == true }
                val visibleQuestions = if (flaggedFilter) questions.filter { flagged[it.question_id] == true } else questions
                val missedIds = qbankStats[courseCode]?.missedQuestionIds?.toSet() ?: emptySet()
                val reviewableMissed = questions.count { missedIds.contains(it.question_id) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    if (!embedded) item { HeaderSpacer() }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEach { tab ->
                                val isSelected = activeTab == tab
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeTab = tab },
                                    label = { Text(tab, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accent,
                                        selectedLabelColor = Color.White,
                                        containerColor = colors.surface,
                                        labelColor = colors.textSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = colors.border,
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }

                    if (activeTab == "Practice Mode") {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !flaggedFilter && !showAnalysis,
                                    onClick = { flaggedFilter = false; showAnalysis = false },
                                    label = { Text("All (${questions.size})", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accent,
                                        selectedLabelColor = Color.White,
                                        containerColor = colors.surface,
                                        labelColor = colors.textSecondary
                                    )
                                )
                                FilterChip(
                                    selected = flaggedFilter,
                                    onClick = { flaggedFilter = true; showAnalysis = false },
                                    label = { Text("Flagged ($flaggedCount)", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.warning,
                                        selectedLabelColor = Color.White,
                                        containerColor = colors.surface,
                                        labelColor = colors.textSecondary
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Flag,
                                            null,
                                            tint = if (flaggedFilter) Color.White else colors.warning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = showAnalysis,
                                    onClick = { showAnalysis = true; flaggedFilter = false },
                                    label = { Text("Analysis", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.success,
                                        selectedLabelColor = Color.White,
                                        containerColor = colors.surface,
                                        labelColor = colors.textSecondary
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Insights,
                                            null,
                                            tint = if (showAnalysis) Color.White else colors.success,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                                if (reviewableMissed > 0) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { launchReview() },
                                        label = { Text("Review ($reviewableMissed)", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.danger,
                                            selectedLabelColor = Color.White,
                                            containerColor = colors.surface,
                                            labelColor = colors.danger
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                null,
                                                tint = colors.danger,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (showAnalysis) {
                            item {
                                AnalysisView(
                                    stats = qbankStats[courseCode] ?: SettingsManager.QuizCourseStats(courseCode = courseCode),
                                    questionTopics = questions.mapNotNull { it.topic_name }.distinct().sorted(),
                                    reviewCount = reviewableMissed,
                                    onReview = { launchReview() }
                                )
                            }
                        } else {
                            if (visibleQuestions.isEmpty()) {
                            item {
                                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(
                                            if (flaggedFilter) Icons.Rounded.Flag else Icons.Rounded.SearchOff,
                                            null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        Text(
                                            if (flaggedFilter) "No flagged questions for this course." else "No questions found for this course.",
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(visibleQuestions, key = { _, q -> q.question_id }) { _, q ->
                                val isAnswered = !(userAnswers[q.question_id] ?: "").isBlank()
                                val isFlagged = flagged[q.question_id] ?: false
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { activeQuestionIndex = questions.indexOfFirst { it.question_id == q.question_id }.coerceAtLeast(0) }
                                ) {
                                    Column {
                                        LatexViewer(latex = q.question_text, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 200.dp))
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (q.question_type.isNotBlank()) AmazeBadge(text = q.question_type, variant = BadgeVariant.INFO)
                                            if (q.marks != null) AmazeBadge(text = "${q.marks} marks", variant = BadgeVariant.SUCCESS)
                                            if (q.topic_name != null) AmazeBadge(text = q.topic_name, variant = BadgeVariant.WARNING)
                                        }
                                        if (q.exam_semester != null) {
                                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                            Text(q.exam_semester, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AnimatedVisibility(visible = isAnswered, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "Answered",
                                                        style = AmazeTheme.typography.caption.copy(color = colors.success, fontWeight = FontWeight.SemiBold)
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(visible = isFlagged, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Flag, null, tint = colors.warning, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "Flagged",
                                                        style = AmazeTheme.typography.caption.copy(color = colors.warning, fontWeight = FontWeight.SemiBold)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (papers.isEmpty()) {

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Share a Paper?",
                                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                                TextButton(onClick = { showPaperUpload = !showPaperUpload }) {
                                    Text(
                                        if (showPaperUpload) Strings.cancel else "Upload",
                                        color = colors.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (showPaperUpload) {
                            item {
                                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text("Share a Paper", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        OutlinedTextField(
                                            value = paperTitle, onValueChange = { paperTitle = it },
                                            label = { Text("Title") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(AmazeTheme.radius.small),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                                        )
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        OutlinedTextField(
                                            value = paperLink, onValueChange = { paperLink = it },
                                            label = { Text("Link (GDrive, Dropbox...)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(AmazeTheme.radius.small),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                                        )
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        Text("Paper Type", fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("CAT 1", "CAT 2", "FAT", "Quiz", "Assignment").forEach { t ->
                                                val sel = paperType == t
                                                Box(
                                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                        .background(if (sel) colors.accent else colors.surface)
                                                        .clickable { paperType = t }.padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(t, color = if (sel) Color.White else colors.textPrimary, fontSize = AmazeTheme.fontSize.micro, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        AmazeButton(
                                            if (paperUploading) "Uploading..." else Strings.submit,
                                            onClick = {
                                                paperUploading = true
                                                paperUploadStatus = null
                                                scope.launch {
                                                    try {
                                                        val res = AmazeClient.postQBankPaper(courseCode, paperTitle, paperLink, paperType)
                                                        paperUploadStatus = if (res?.success == true) "Paper uploaded!" else res?.message ?: "Upload failed"
                                                    } catch (e: Exception) { paperUploadStatus = "Error: ${e.message}" }
                                                    paperUploading = false
                                                }
                                            },
                                            enabled = paperTitle.isNotBlank() && paperLink.isNotBlank() && !paperUploading,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        paperUploadStatus?.let {
                                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                            val isSuccess = it.contains("uploaded", ignoreCase = true) || it.contains("success", ignoreCase = true)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                                    null, tint = if (isSuccess) colors.success else colors.danger, modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                                Text(it, color = if (isSuccess) colors.success else colors.danger, fontSize = AmazeTheme.fontSize.xs)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            item {
                                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        Text("No papers found in the archive.", color = colors.textSecondary)
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(papers, key = { _, p -> "${p.type}-${p.title}-${p.link}" }) { _, paper ->
                                AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { uriHandler.openUri(paper.link) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                                .background(colors.chart1.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Article, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                        Column(Modifier.weight(1f)) {
                                            Text(paper.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                            Text(paper.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        }
                                        Icon(Icons.Rounded.OpenInNew, null, tint = colors.textMuted)
                                    }
                                }
                            }
                        }
                        }
                    item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
                    }
                }
            }
        }
    }

    if (showSessionSummary) {
        val reviewCourseCode = courseCode
        val reviewMissedCount = if (reviewCourseCode != null) {
            val ids = (qbankStats[reviewCourseCode]?.missedQuestionIds ?: emptyList()).toSet()
            questions.count { ids.contains(it.question_id) }
        } else 0
        SessionSummaryDialog(
            questions = questions,
            results = sessionResults,
            timeSpent = sessionTimeSpent,
            missedCount = reviewMissedCount,
            onKeepPracticing = { showSessionSummary = false },
            onReviewMissed = {
                showSessionSummary = false
                launchReview()
            },
            onFinish = {
                showSessionSummary = false
                activeQuestionIndex = null
                reviewIndex = null
                reviewQueue = emptyList()
            }
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/**
 * Grading logic: for MCQ-type questions the user picks an option key and
 * `correct_answer` stores the matching option key (case-insensitive). If the
 * stored key is not found among the option keys, fall back to matching the
 * option value itself. For free-text questions compare the typed answer.
 */
private fun isAnswerCorrect(question: QBankQuestion, userAnswer: String): Boolean {
    if (userAnswer.isBlank()) return false
    val correct = question.correct_answer?.trim() ?: return false
    val opts = question.options
    if (opts.isNullOrEmpty()) {
        return userAnswer.trim().equals(correct, ignoreCase = true)
    }
    val keyMatch = opts.keys.firstOrNull { it.equals(correct, ignoreCase = true) }
    return if (keyMatch != null) {
        userAnswer.equals(keyMatch, ignoreCase = true)
    } else {
        opts.values.any { it.trim().equals(userAnswer.trim(), ignoreCase = true) }
    }
}

private fun correctAnswerLabel(question: QBankQuestion): String {
    val correct = question.correct_answer?.trim() ?: return "Not provided"
    val opts = question.options
    if (opts.isNullOrEmpty()) return correct
    val keyMatch = opts.keys.firstOrNull { it.equals(correct, ignoreCase = true) }
    if (keyMatch != null) {
        val value = opts[keyMatch]
        return "${keyMatch.uppercase()}. $value"
    }
    opts.values.firstOrNull { it.trim().equals(correct, ignoreCase = true) }?.let { return it }
    return correct
}

@Composable
private fun QuizModeView(
    question: QBankQuestion,
    index: Int,
    total: Int,
    userAnswer: String,
    onAnswerChange: (String) -> Unit,
    revealed: Boolean,
    onReveal: () -> Unit,
    flagged: Boolean,
    onToggleFlag: () -> Unit,
    secondsLeft: Int,
    totalSeconds: Int,
    timerEnabled: Boolean,
    onToggleTimer: () -> Unit,
    timeSpentSec: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val colors = AmazeTheme.colors
    val descriptive = isDescriptive(question)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Question ${index + 1} of $total",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                )
                if (question.topic_name != null) {
                    Text(question.topic_name, style = AmazeTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (timerEnabled) {
                    Surface(
                        shape = RoundedCornerShape(AmazeTheme.radius.xs),
                        color = if (secondsLeft <= 10) colors.danger.copy(alpha = 0.12f) else colors.surface,
                        border = BorderStroke(1.dp, if (secondsLeft <= 10) colors.danger else colors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                null,
                                tint = if (secondsLeft <= 10) colors.danger else colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatTime(secondsLeft),
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = if (secondsLeft <= 10) colors.danger else colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onToggleTimer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (timerEnabled) Icons.Rounded.TimerOff else Icons.Rounded.Timer,
                        if (timerEnabled) "Disable timer" else "Enable timer",
                        tint = if (timerEnabled) colors.textMuted else colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleFlag, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (flagged) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                        "Flag for review",
                        tint = if (flagged) colors.warning else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.sm))

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LatexViewer(latex = question.question_text, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 300.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.md))

                if (!question.options.isNullOrEmpty()) {
                    // Sort options so letters render in a stable order
                    val sortedKeys = question.options!!.keys.sortedBy { it.lowercase() }
                    sortedKeys.forEach { key ->
                        val value = question.options!![key] ?: return@forEach
                        val isSelected = userAnswer == key
                        val isCorrectKey = revealed && key.equals(question.correct_answer?.trim(), ignoreCase = true)
                        val isWrongPick = revealed && isSelected && !key.equals(question.correct_answer?.trim(), ignoreCase = true)
                        val borderColor = when {
                            isCorrectKey -> colors.success
                            isWrongPick -> colors.danger
                            isSelected -> colors.accent
                            else -> colors.border
                        }
                        val bgColor = when {
                            isCorrectKey -> colors.success.copy(alpha = 0.12f)
                            isWrongPick -> colors.danger.copy(alpha = 0.12f)
                            isSelected -> colors.accent.copy(alpha = 0.08f)
                            else -> colors.surface
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .border(1.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(bgColor)
                                .clickable { onAnswerChange(key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCorrectKey -> colors.success
                                            isWrongPick -> colors.danger
                                            isSelected -> colors.accent
                                            else -> colors.border.copy(alpha = 0.6f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key.uppercase(),
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        color = if (isSelected || isCorrectKey || isWrongPick) Color.White else colors.textPrimary,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(
                                value,
                                style = AmazeTheme.typography.body.copy(
                                    color = if (isSelected && !revealed) colors.accent else colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (isCorrectKey) {
                                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(20.dp))
                            } else if (isWrongPick) {
                                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                Icon(Icons.Rounded.Cancel, null, tint = colors.danger, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.accent.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(
                            if (revealed) "Model answer is shown below — review it, then move on."
                            else "Descriptive question — think through your answer, then reveal the model answer.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                }

                Spacer(Modifier.height(AmazeTheme.spacing.lg))

                if (revealed) {
                    val isCorrectFinal = !descriptive && userAnswer.isNotBlank() && isAnswerCorrect(question, userAnswer)
                    val bannerColor = when {
                        descriptive -> colors.warning
                        isCorrectFinal -> colors.success
                        else -> colors.danger
                    }
                    val bannerIcon = when {
                        descriptive -> Icons.Rounded.Visibility
                        isCorrectFinal -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.Cancel
                    }
                    val bannerTitle = when {
                        descriptive -> "Reviewed"
                        isCorrectFinal -> "Correct!"
                        else -> "Incorrect"
                    }
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(bannerColor.copy(alpha = 0.12f)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(bannerIcon, null, tint = bannerColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(
                                bannerTitle,
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = bannerColor)
                            )
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Text(
                            "${if (descriptive) "Model Answer" else "Correct Answer"}: ${correctAnswerLabel(question)}",
                            style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        )
                        if (timeSpentSec > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Time spent: ${formatTime(timeSpentSec)}",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                } else if (!descriptive && userAnswer.isBlank()) {
                    Text(
                        if (timerEnabled) "Select an option first, or wait for the timer (${formatTime(totalSeconds)}) to reveal the answer."
                        else "Select an option to check your answer.",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }

                Spacer(Modifier.height(AmazeTheme.spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onPrev, enabled = index > 0) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous question")
                    }
                    if (!revealed) {
                        Button(
                            onClick = onReveal,
                            enabled = descriptive || userAnswer.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.background,
                                disabledContainerColor = colors.accent.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(if (descriptive) "Reveal Answer" else "Check Answer")
                        }
                    } else {
                        val isCorrectFinal = !descriptive && userAnswer.isNotBlank() && isAnswerCorrect(question, userAnswer)
                        val resColor = when {
                            descriptive -> colors.warning
                            isCorrectFinal -> colors.success
                            else -> colors.danger
                        }
                        val resIcon = when {
                            descriptive -> Icons.Rounded.Visibility
                            isCorrectFinal -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Cancel
                        }
                        val resLabel = when {
                            descriptive -> "Reviewed"
                            isCorrectFinal -> "Correct"
                            else -> "Incorrect"
                        }
                        Surface(
                            shape = RoundedCornerShape(AmazeTheme.radius.xs),
                            color = resColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, resColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(resIcon, null, tint = resColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(resLabel, style = AmazeTheme.typography.smallLabel.copy(color = resColor, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    FilledTonalIconButton(onClick = onNext, enabled = index < total - 1) {
                        Icon(Icons.Rounded.ChevronRight, "Next question")
                    }
                }
            }
        }
    }
}

private fun isDescriptive(question: QBankQuestion): Boolean = question.options.isNullOrEmpty()

private enum class QuizQueueStatus { PENDING, CORRECT, WRONG, REVIEWED }

@Composable
private fun QuestionQueueStrip(
    total: Int,
    currentIndex: Int,
    statuses: List<QuizQueueStatus>,
    flags: List<Boolean>,
    attempts: Int,
    correctCount: Int,
    onSelect: (Int) -> Unit
) {
    val colors = AmazeTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (attempts > 0) "Score: $correctCount/$attempts" else "No answers yet",
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
            )
            Text(
                "Tap a number to jump",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(total) { i ->
                val status = statuses.getOrElse(i) { QuizQueueStatus.PENDING }
                val isCurrent = i == currentIndex
                val bg = when {
                    isCurrent -> colors.accent
                    status == QuizQueueStatus.CORRECT -> colors.success
                    status == QuizQueueStatus.WRONG -> colors.danger
                    status == QuizQueueStatus.REVIEWED -> colors.warning
                    else -> colors.surface
                }
                val fg = when {
                    isCurrent || status == QuizQueueStatus.CORRECT || status == QuizQueueStatus.WRONG -> Color.White
                    else -> colors.textPrimary
                }
                Box(modifier = Modifier.size(34.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(bg)
                            .border(1.dp, if (isCurrent) colors.accent else colors.border, CircleShape)
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = AmazeTheme.typography.smallLabel.copy(color = fg, fontWeight = FontWeight.Bold))
                    }
                    if (flags.getOrElse(i) { false }) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.warning)
                                .border(1.dp, colors.background, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

private enum class SwotQuadrant { STRENGTH, WEAKNESS, OPPORTUNITY, THREAT }

private fun classifyTopic(st: SettingsManager.QuizTopicStat, cfg: SettingsManager.SwotConfig): SwotQuadrant {
    val acc = if (st.attempts == 0) 0f else st.correct.toFloat() / st.attempts
    return when {
        st.attempts >= cfg.strengthMinAttempts && acc >= cfg.strengthAccuracy / 100f -> SwotQuadrant.STRENGTH
        st.attempts >= cfg.weaknessMinAttempts && acc < cfg.weaknessAccuracy / 100f -> SwotQuadrant.WEAKNESS
        st.attempts < cfg.strengthMinAttempts -> SwotQuadrant.OPPORTUNITY
        else -> SwotQuadrant.THREAT
    }
}

@Composable
private fun SwotCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, topics: List<SettingsManager.QuizTopicStat>, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = AmazeTheme.typography.smallLabel.copy(color = color, fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(8.dp))
            if (topics.isEmpty()) {
                Text("Nothing here yet", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            } else {
                topics.sortedByDescending { it.attempts }.forEach { st ->
                    val acc = if (st.attempts == 0) 0f else st.correct.toFloat() / st.attempts
                    Text(
                        "${st.topic} · ${st.correct}/${st.attempts} (${(acc * 100).toInt()}%)",
                        style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                    Text(
                        "avg ${formatTime(if (st.attempts == 0) 0 else (st.totalTimeSec / st.attempts).toInt())}/q",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisView(
    stats: SettingsManager.QuizCourseStats,
    questionTopics: List<String>,
    reviewCount: Int,
    onReview: () -> Unit
) {
    val colors = AmazeTheme.colors
    var config by remember { mutableStateOf(SettingsManager.getSwotConfig()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val allTopics = (questionTopics + stats.topics.keys).distinct().sorted()
    val overallAcc = if (stats.attempts == 0) 0f else stats.correct.toFloat() / stats.attempts

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Insights, null, tint = colors.success, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Topic Analysis (SWOT)",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Text(
                        "Built from your local practice history",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
                IconButton(onClick = { showConfigDialog = true }) {
                    Icon(Icons.Rounded.Tune, "Configure SWOT thresholds", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(overallAcc * 100).toInt()}% accuracy",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                LinearProgressIndicator(
                    progress = { overallAcc },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                    color = colors.success,
                    trackColor = colors.border
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${stats.attempts} attempts · ${stats.topics.size} topic${if (stats.topics.size != 1) "s" else ""} practiced",
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
            )
            if (reviewCount > 0) {
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Review $reviewCount Missed Question${if (reviewCount != 1) "s" else ""}")
                }
            }
        }
    }

    if (allTopics.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Insights, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text("Practice some questions to see your strengths and weaknesses.", color = colors.textSecondary)
            }
        }
    } else {
        val topicStats = allTopics.map { topic -> stats.topics[topic] ?: SettingsManager.QuizTopicStat(topic = topic) }
        val strengths = topicStats.filter { classifyTopic(it, config) == SwotQuadrant.STRENGTH }
        val weaknesses = topicStats.filter { classifyTopic(it, config) == SwotQuadrant.WEAKNESS }
        val opportunities = topicStats.filter { classifyTopic(it, config) == SwotQuadrant.OPPORTUNITY }
        val threats = topicStats.filter { classifyTopic(it, config) == SwotQuadrant.THREAT }

        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwotCard("Strengths", Icons.Rounded.TrendingUp, colors.success, strengths, Modifier.weight(1f))
                SwotCard("Weaknesses", Icons.Rounded.TrendingDown, colors.danger, weaknesses, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwotCard("Opportunities", Icons.Rounded.Lightbulb, colors.accent, opportunities, Modifier.weight(1f))
                SwotCard("Threats", Icons.Rounded.Warning, colors.warning, threats, Modifier.weight(1f))
            }
        }
    }

    if (showConfigDialog) {
        SwotConfigDialog(
            initial = config,
            onDismiss = { showConfigDialog = false },
            onSave = { cfg ->
                config = cfg
                SettingsManager.saveSwotConfig(cfg)
                showConfigDialog = false
            }
        )
    }
}

@Composable
private fun SwotConfigDialog(
    initial: SettingsManager.SwotConfig,
    onDismiss: () -> Unit,
    onSave: (SettingsManager.SwotConfig) -> Unit
) {
    val colors = AmazeTheme.colors
    val defaults = SettingsManager.SwotConfig()
    var strengthMinAttempts by remember { mutableStateOf(initial.strengthMinAttempts.toString()) }
    var strengthAccuracy by remember { mutableStateOf(initial.strengthAccuracy.toString()) }
    var weaknessMinAttempts by remember { mutableStateOf(initial.weaknessMinAttempts.toString()) }
    var weaknessAccuracy by remember { mutableStateOf(initial.weaknessAccuracy.toString()) }

    fun parse(value: String, fallback: Int, min: Int, max: Int): Int = value.toIntOrNull()?.coerceIn(min, max) ?: fallback

    fun resetToDefaults() {
        strengthMinAttempts = defaults.strengthMinAttempts.toString()
        strengthAccuracy = defaults.strengthAccuracy.toString()
        weaknessMinAttempts = defaults.weaknessMinAttempts.toString()
        weaknessAccuracy = defaults.weaknessAccuracy.toString()
    }

    @Composable
    fun ConfigField(label: String, value: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
            label = { Text(label, color = colors.textSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SWOT Analysis Settings", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tune the thresholds used to classify topics into Strengths, Weaknesses, Opportunities and Threats.",
                    color = colors.textSecondary
                )
                ConfigField("Strengths — min attempts (${defaults.strengthMinAttempts})", strengthMinAttempts, { strengthMinAttempts = it })
                ConfigField("Strengths — accuracy % (${defaults.strengthAccuracy})", strengthAccuracy, { strengthAccuracy = it })
                ConfigField("Weaknesses — min attempts (${defaults.weaknessMinAttempts})", weaknessMinAttempts, { weaknessMinAttempts = it })
                ConfigField("Weaknesses — accuracy % (${defaults.weaknessAccuracy})", weaknessAccuracy, { weaknessAccuracy = it })
                TextButton(onClick = { resetToDefaults() }) {
                    Text("Reset to Defaults", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    SettingsManager.SwotConfig(
                        strengthMinAttempts = parse(strengthMinAttempts, defaults.strengthMinAttempts, 1, 20),
                        strengthAccuracy = parse(strengthAccuracy, defaults.strengthAccuracy, 1, 100),
                        weaknessMinAttempts = parse(weaknessMinAttempts, defaults.weaknessMinAttempts, 1, 20),
                        weaknessAccuracy = parse(weaknessAccuracy, defaults.weaknessAccuracy, 1, 100)
                    )
                )
            }) {
                Text("Save", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}

@Composable
private fun SessionSummaryDialog(
    questions: List<QBankQuestion>,
    results: Map<String, Boolean?>,
    timeSpent: Map<String, Long>,
    missedCount: Int,
    onKeepPracticing: () -> Unit,
    onReviewMissed: () -> Unit,
    onFinish: () -> Unit
) {
    val colors = AmazeTheme.colors
    val total = results.size
    val correct = results.values.count { it == true }
    val reviewed = results.values.count { it == null }
    val totalMs = timeSpent.values.sum()

    val topicMap = mutableMapOf<String, Pair<Int, Int>>()
    results.forEach { (qid, res) ->
        val q = questions.firstOrNull { it.question_id == qid }
        val topic = q?.topic_name?.takeIf { it.isNotBlank() } ?: "General"
        val cur = topicMap[topic] ?: (0 to 0)
        topicMap[topic] = (cur.first + 1) to (cur.second + if (res == true) 1 else 0)
    }
    val topicRows = topicMap.entries.sortedByDescending { if (it.value.first == 0) 0f else it.value.second.toFloat() / it.value.first }

    AlertDialog(
        onDismissRequest = onKeepPracticing,
        title = { Text("Practice Session Done", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You got $correct of $total right${if (reviewed > 0) " and reviewed $reviewed descriptive" else ""} in ${formatTime((totalMs / 1000L).toInt())}.",
                    color = colors.textSecondary
                )
                if (topicRows.isEmpty()) {
                    Text("No answers recorded this session.", color = colors.textMuted)
                } else {
                    Text("Per-topic breakdown", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold))
                    topicRows.forEach { (topic, pair) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(topic, style = AmazeTheme.typography.caption.copy(color = colors.textPrimary), modifier = Modifier.weight(1f))
                            Text(
                                "${pair.second}/${pair.first}",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
                if (missedCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onReviewMissed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Review $missedCount Missed Question${if (missedCount != 1) "s" else ""}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFinish) {
                Text("Finish", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepPracticing) {
                Text("Keep Practicing", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}
