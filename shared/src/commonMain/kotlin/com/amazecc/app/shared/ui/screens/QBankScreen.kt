package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun QBankScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<QBankCourse>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<QBankCourse?>(null) }
    var questions by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadQuestions(course: QBankCourse) {
        selectedCourse = course
        scope.launch {
            loading = true
            try {
                val res = AmazeClient.getQBankQuestions(course.courseCode)
                if (res.success) questions = res.data else error = res.message
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
        if (selectedCourse != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accent.copy(alpha = 0.08f)).padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedCourse = null; questions = emptyList() }) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = colors.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("QBank: ${selectedCourse!!.courseCode}", style = AmazeTheme.typography.display.copy(fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.textPrimary))
                    Text(selectedCourse!!.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        } else {
            ScreenHeader(title = "Question Bank", description = "Access past year question papers", showBackButton = true)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Error", color = colors.danger)
            }
        } else if (selectedCourse == null) {
            Text("Select a course", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.padding(16.dp))
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(courses) { course ->
                    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { loadQuestions(course) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Article, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (questions.isEmpty()) {
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No questions found for this course.", color = colors.textSecondary)
                            }
                        }
                    }
                } else {
                    items(questions) { q ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(q.question_text, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (q.question_type != null) AmazeBadge(text = q.question_type, variant = BadgeVariant.INFO)
                                    if (q.marks != null) AmazeBadge(text = "${q.marks} marks", variant = BadgeVariant.SUCCESS)
                                    if (q.topic_name != null) AmazeBadge(text = q.topic_name, variant = BadgeVariant.WARNING)
                                }
                                if (q.exam_semester != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(q.exam_semester, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
