package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.launch

/**
 * Exam Prep Hub & QBank: course list (with local practice-performance card) then
 * the full per-course workspace ([QBankCourseWorkspace]) with the course
 * preselected — practice, quiz, review, analysis and paper archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<QBankCourse>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<QBankCourse?>(null) }
    var startInQuiz by remember { mutableStateOf(false) }
    var qbankStats by remember { mutableStateOf(SettingsManager.getQBankStats()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val qbankCourseTarget by AppState.qbankCourseTarget.collectAsState()

    fun refreshCourses() {
        qbankStats = SettingsManager.getQBankStats()
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
            selectedCourse = match
            startInQuiz = true
        } else {
            loading = true
            try {
                val res = AmazeClient.getQBankCourses()
                if (res.success) {
                    courses = res.courses.distinctBy { it.courseCode }
                    courses.firstOrNull { it.courseCode.equals(code, ignoreCase = true) }?.let {
                        selectedCourse = it
                        startInQuiz = true
                    }
                } else error = res.message
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    val sc = selectedCourse

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (sc == null) {
            ScreenHeader(
                title = "Exam Prep Hub & QBank",
                description = "Targeted practice papers and exam preparation",
                showBackButton = true,
                showSyncButton = true,
                onRefresh = { refreshCourses() },
                onBackOverride = { AppState.navigateBack() },
                enabledScreens = setOf(Screen.QBANK)
            )
        }

        when {
            sc != null -> {
                QBankCourseWorkspace(
                    courseCode = sc.courseCode,
                    courseTitle = sc.courseTitle,
                    startInQuiz = startInQuiz,
                    onExit = {
                        selectedCourse = null
                        startInQuiz = false
                        qbankStats = SettingsManager.getQBankStats()
                    }
                )
            }

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
                        Button(onClick = { error = null; refreshCourses() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
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

                    val practicedCourses = qbankStats.values.filter { it.attempts > 0 }.sortedByDescending { it.lastPracticedAt }
                    if (practicedCourses.isNotEmpty()) {
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                                .background(colors.success.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.TrendingUp, null, tint = colors.success, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Practice Performance",
                                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                            )
                                            Text(
                                                "Local accuracy across your practice sessions",
                                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    practicedCourses.forEach { c ->
                                        val acc = if (c.attempts == 0) 0f else c.correct.toFloat() / c.attempts
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                .clickable {
                                                    selectedCourse = courses.firstOrNull { it.courseCode == c.courseCode }
                                                    startInQuiz = false
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                c.courseCode,
                                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
                                                modifier = Modifier.width(96.dp)
                                            )
                                            LinearProgressIndicator(
                                                progress = { acc },
                                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                                                color = colors.success,
                                                trackColor = colors.border
                                            )
                                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                            Text(
                                                "${c.correct}/${c.attempts}",
                                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
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

                    itemsIndexed(courses, key = { index, course -> "${course.courseCode}_$index" }) { _, course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {
                            selectedCourse = course
                            startInQuiz = false
                        }) {
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
        }
    }
}