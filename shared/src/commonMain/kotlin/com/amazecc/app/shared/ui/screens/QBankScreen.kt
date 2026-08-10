package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QUIZ_MCQ_SECONDS = 60
private const val QUIZ_TEXT_SECONDS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<QBankCourse>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<QBankCourse?>(null) }
    var questions by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var activeQuestionIndex by remember { mutableStateOf<Int?>(null) }
    val userAnswers = remember { mutableStateMapOf<String, String>() }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val flagged = remember { mutableStateMapOf<String, Boolean>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf("Practice Mode") }
    val tabs = listOf("Practice Mode", "Paper Archive")
    var papers by remember { mutableStateOf<List<QBankPaper>>(emptyList()) }
    var flaggedFilter by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val qbankCourseTarget by AppState.qbankCourseTarget.collectAsState()

    fun resetQuizState() {
        userAnswers.clear()
        revealed.clear()
        flagged.clear()
        activeQuestionIndex = null
        flaggedFilter = false
    }

    fun loadQuestionSet(course: QBankCourse) {
        selectedCourse = course
        questions = emptyList()
        papers = emptyList()
        resetQuizState()
        scope.launch {
            loading = true
            try {
                val qRes = AmazeClient.getQBankQuestions(course.courseCode)
                if (qRes.success) questions = qRes.data.distinctBy { it.question_id } else error = qRes.message
                val pRes = AmazeClient.getQBankPapers(course.courseCode)
                if (pRes.success) papers = pRes.data
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    fun refreshCourses() {
        scope.launch {
            loading = true
            try {
                val res = AmazeClient.getQBankCourses()
                if (res.success) courses = res.courses.distinctBy { it.courseCode } else error = res.message
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val res = AmazeClient.getQBankCourses()
            if (res.success) courses = res.courses.distinctBy { it.courseCode } else error = res.message
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    LaunchedEffect(qbankCourseTarget) {
        val code = qbankCourseTarget ?: return@LaunchedEffect
        AppState.consumeQBankCourseTarget()
        val match = courses.firstOrNull { it.courseCode.equals(code, ignoreCase = true) }
        if (match != null) {
            loadQuestionSet(match)
            activeQuestionIndex = 0
        } else {
            loading = true
            try {
                val res = AmazeClient.getQBankCourses()
                if (res.success) {
                    courses = res.courses.distinctBy { it.courseCode }
                    courses.firstOrNull { it.courseCode.equals(code, ignoreCase = true) }?.let {
                        loadQuestionSet(it)
                        activeQuestionIndex = 0
                    }
                } else error = res.message
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    val currentQuestion = activeQuestionIndex?.let { questions.getOrNull(it) }
    val currentRevealed = currentQuestion?.let { revealed[it.question_id] == true } ?: false
    val quizTotalSeconds = if (currentQuestion?.options.isNullOrEmpty()) QUIZ_TEXT_SECONDS else QUIZ_MCQ_SECONDS
    var secondsLeft by remember(currentQuestion?.question_id) { mutableStateOf(quizTotalSeconds) }

    LaunchedEffect(currentQuestion?.question_id, currentRevealed) {
        if (currentQuestion == null || currentRevealed) return@LaunchedEffect
        secondsLeft = quizTotalSeconds
        while (secondsLeft > 0) {
            delay(1000)
            if (revealed[currentQuestion.question_id] == true) break
            secondsLeft--
        }
        if (secondsLeft <= 0 && revealed[currentQuestion.question_id] != true) {
            revealed[currentQuestion.question_id] = true
        }
    }

    val sc = selectedCourse

    ScreenHeader(
        title = when {
            activeQuestionIndex != null -> "Quiz Mode"
            sc != null -> sc.courseCode
            else -> "Exam Prep Hub & QBank"
        },
        description = when {
            activeQuestionIndex != null -> "${questions.size} questions · ${quizTotalSeconds}s each"
            sc != null -> sc.courseTitle
            else -> "Targeted practice papers and exam preparation"
        },
        showBackButton = true,
        showSyncButton = true,
        onRefresh = { if (sc != null) loadQuestionSet(sc) else refreshCourses() },
        onBackOverride = {
            when {
                activeQuestionIndex != null -> activeQuestionIndex = null
                sc != null -> {
                    selectedCourse = null
                    questions = emptyList()
                    papers = emptyList()
                    activeTab = "Practice Mode"
                    resetQuizState()
                }
                else -> AppState.navigateBack()
            }
        },
        enabledScreens = setOf(Screen.QBANK)
    )

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
                        Button(onClick = { error = null; if (sc != null) loadQuestionSet(sc) else refreshCourses() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            sc == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    item { HeaderSpacer() }

                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                        .background(colors.warning.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Timer, null, tint = colors.warning, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Exam Preparation Hub",
                                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    )
                                    Text(
                                        "Tap a course to launch practice mode with timed questions, flag for review and instant answer checks",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Select Course for Practice",
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                    }

                    if (courses.isEmpty()) {
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    Text("No courses in the exam prep archive yet.", color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    items(courses, key = { it.courseCode }) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { loadQuestionSet(course) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                        .background(colors.accent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Article, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
                }
            }

            activeQuestionIndex != null && currentQuestion != null -> {
                val qIndex = activeQuestionIndex ?: 0
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    item { HeaderSpacer() }
                    item {
                        QuizModeView(
                            question = currentQuestion,
                            index = qIndex,
                            total = questions.size,
                            userAnswer = userAnswers[currentQuestion.question_id] ?: "",
                            onAnswerChange = { userAnswers[currentQuestion.question_id] = it },
                            revealed = currentRevealed,
                            onReveal = { revealed[currentQuestion.question_id] = true },
                            flagged = flagged[currentQuestion.question_id] ?: false,
                            onToggleFlag = { flagged[currentQuestion.question_id] = !(flagged[currentQuestion.question_id] ?: false) },
                            secondsLeft = secondsLeft,
                            totalSeconds = quizTotalSeconds,
                            onNext = { if (qIndex < questions.size - 1) activeQuestionIndex = qIndex + 1 },
                            onPrev = { if (qIndex > 0) activeQuestionIndex = qIndex - 1 }
                        )
                    }
                    item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
                }
            }

            else -> {
                val flaggedCount = questions.count { flagged[it.question_id] == true }
                val visibleQuestions = if (flaggedFilter) questions.filter { flagged[it.question_id] == true } else questions
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    item { HeaderSpacer() }

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
                        if (flaggedCount > 0) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !flaggedFilter,
                                        onClick = { flaggedFilter = false },
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
                                        onClick = { flaggedFilter = true },
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
                                }
                            }
                        }

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
                    item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
                    }
                }
            }
        }
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
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val colors = AmazeTheme.colors
    val isCorrect = userAnswer.isNotBlank() && isAnswerCorrect(question, userAnswer)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Question ${index + 1} of $total", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            IconButton(onClick = onToggleFlag) {
                Icon(
                    if (flagged) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                    "Flag for review",
                    tint = if (flagged) colors.warning else colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
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
                    OutlinedTextField(
                        value = userAnswer,
                        onValueChange = onAnswerChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type your answer here...", color = colors.textMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }

                Spacer(Modifier.height(AmazeTheme.spacing.lg))

                if (revealed) {
                    val isCorrectFinal = userAnswer.isNotBlank() && isAnswerCorrect(question, userAnswer)
                    val bannerColor = if (isCorrectFinal) colors.success else colors.danger
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(bannerColor.copy(alpha = 0.12f)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCorrectFinal) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                null,
                                tint = bannerColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(
                                if (isCorrectFinal) "Correct!" else "Incorrect",
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = bannerColor)
                            )
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Text(
                            "Correct Answer: ${correctAnswerLabel(question)}",
                            style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        )
                    }
                } else {
                    Button(
                        onClick = onReveal,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = userAnswer.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            disabledContainerColor = colors.accent.copy(alpha = 0.35f)
                        )
                    ) {
                        Text("Check Answer", color = colors.background)
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    Text(
                        if (userAnswer.isBlank()) "Select an option first, or wait for the timer to reveal the answer." else "Check your answer against the correct logic",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }

                Spacer(Modifier.height(AmazeTheme.spacing.lg))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = onPrev,
                        enabled = index > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surface,
                            contentColor = colors.textPrimary,
                            disabledContainerColor = colors.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = onNext,
                        enabled = index < total - 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.background,
                            disabledContainerColor = colors.accent.copy(alpha = 0.35f)
                        )
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}
