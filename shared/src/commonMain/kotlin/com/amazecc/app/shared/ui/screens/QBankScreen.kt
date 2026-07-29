package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.launch

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
    val showAnswer = remember { mutableStateMapOf<String, Boolean>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    var activeTab by remember { mutableStateOf("Practice Mode") }
    val tabs = listOf("Practice Mode", "Paper Archive")
    var papers by remember { mutableStateOf<List<QBankPaper>>(emptyList()) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    fun loadQuestions(course: QBankCourse) {
        selectedCourse = course
        scope.launch {
            loading = true
            try {
                val qRes = AmazeClient.getQBankQuestions(course.courseCode)
                if (qRes.success) questions = qRes.data else error = qRes.message
                val pRes = AmazeClient.getQBankPapers(course.courseCode)
                if (pRes.success) papers = pRes.data
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val res = AmazeClient.getQBankCourses()
            if (res.success) courses = res.courses else error = res.message
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val sc = selectedCourse
        if (sc != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accent.copy(alpha = 0.08f)).padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (activeQuestionIndex != null) {
                        activeQuestionIndex = null
                    } else {
                        selectedCourse = null; questions = emptyList() 
                    }
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = colors.textPrimary)
                }
                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                Column {
                    Text("QBank: ${sc.courseCode}", style = AmazeTheme.typography.display.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
                    Text(sc.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        } else {
            ScreenHeader(title = "Exam Prep Hub & QBank", description = "Targeted practice papers and exam preparation", showBackButton = true)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: Strings.error, color = colors.danger)
            }
        } else if (selectedCourse == null) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // ── Exam Prep Countdown Banner ──
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
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
                            Text("Exam Preparation Hub", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text("Select a course below to launch interactive question practice", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                Text("Select Course for Practice", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                items(courses, key = { it.courseCode }) { course ->
                    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { loadQuestions(course) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
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
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeTab = tab },
                        label = { Text(tab, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                            containerColor = colors.surface, labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.border, selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            enabled = true, selected = isSelected
                        )
                    )
                }
            }
            if (activeTab == "Practice Mode") {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                    if (questions.isEmpty()) {
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                Text("No questions found for this course.", color = colors.textSecondary)
                            }
                        }
                    }
                } else if (activeQuestionIndex != null) {
                    val qIndex = activeQuestionIndex ?: 0
                    item {
                        QuestionDetailView(
                            question = questions[qIndex],
                            index = qIndex,
                            total = questions.size,
                            userAnswer = userAnswers[questions[qIndex].question_id] ?: "",
                            onAnswerChange = { userAnswers[questions[qIndex].question_id] = it },
                            showCorrect = showAnswer[questions[qIndex].question_id] ?: false,
                            onShowCorrect = { showAnswer[questions[qIndex].question_id] = true },
                            onNext = { if (qIndex < questions.size - 1) activeQuestionIndex = qIndex + 1 },
                            onPrev = { if (qIndex > 0) activeQuestionIndex = qIndex - 1 }
                        )
                    }
                } else {
                    itemsIndexed(questions, key = { _, q -> q.question_id }) { index, q ->
                        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { activeQuestionIndex = index }) {
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
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
            }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
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
                        items(papers, key = { it.link }) { paper ->
                            AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { uriHandler.openUri(paper.link) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.chart1.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
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

@Composable
private fun QuestionDetailView(
    question: QBankQuestion,
    index: Int,
    total: Int,
    userAnswer: String,
    onAnswerChange: (String) -> Unit,
    showCorrect: Boolean,
    onShowCorrect: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Question ${index + 1} of $total", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            LatexViewer(latex = question.question_text, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 300.dp))
            Spacer(Modifier.height(AmazeTheme.spacing.md))
            
            if (!question.options.isNullOrEmpty()) {
                question.options.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.surface).clickable { onAnswerChange(key) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = userAnswer == key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textMuted)
                        )
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
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
            
            if (showCorrect) {
                val isCorrect = userAnswer.trim().equals(question.correct_answer?.trim() ?: "", ignoreCase = true)
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (isCorrect) colors.success.copy(alpha=0.1f) else colors.danger.copy(alpha=0.1f)).padding(12.dp)) {
                    Text(if (isCorrect) "Correct!" else "Incorrect", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = if (isCorrect) colors.success else colors.danger))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("Correct Answer: ${question.correct_answer ?: "Not provided"}", style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                }
            } else {
                Button(
                    onClick = onShowCorrect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Check Answer", color = colors.background)
                }
            }
            
            Spacer(Modifier.height(AmazeTheme.spacing.lg))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = onPrev,
                    enabled = index > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textPrimary, disabledContainerColor = colors.surface.copy(alpha=0.5f))
                ) {
                    Text("Previous")
                }
                Button(
                    onClick = onNext,
                    enabled = index < total - 1,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textPrimary, disabledContainerColor = colors.surface.copy(alpha=0.5f))
                ) {
                    Text("Next")
                }
            }
        }
    }
}
