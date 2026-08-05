package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.ui.components.AmazeTextField
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.utils.rememberFileSaver
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

private fun normalizeType(raw: String?): String = when (raw?.uppercase()) {
    "TH" -> "Theory"; "LO" -> "Lab"; "ETL" -> "Embedded"; "PJT" -> "Project"
    "SS" -> "Soft Skill"; "OC" -> "Online"; else -> raw ?: "Other"
}

@Composable
fun CurriculumScreen() {
    val colors = AmazeTheme.colors
    val curriculumData by AppState.curriculum.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val marksRes by AppState.marks.collectAsState()

    val categories = curriculumData?.categories ?: emptyList()
    val details = curriculumData?.details ?: emptyList()
    val totalEarned = marksRes?.cgpa?.creditsEarned?.toFloatOrNull() ?: (curriculumData?.totalCredits ?: 0).toFloat()
    val totalRequired = 160

    val attendanceRes by AppState.attendance.collectAsState()
    val ongoingCredits = attendanceRes?.attendance?.mapNotNull { it.credits?.toFloatOrNull() }?.sum() ?: 0f

    val remainingCredits = (totalRequired.toFloat() - totalEarned - ongoingCredits).coerceAtLeast(0f).toInt()
    val earnedPct = if (totalRequired > 0) (totalEarned / totalRequired).coerceAtMost(1f) else 0f
    val ongoingPct = if (totalRequired > 0) (ongoingCredits / totalRequired).coerceAtMost(1f - earnedPct) else 0f
    val expectedGrad = if (remainingCredits <= 0) "Ready" else "${((remainingCredits + 23) / 24).coerceAtLeast(1)} sem"

    val completedCourseCodes = remember(allGrades) {
        val codes = mutableSetOf<String>()
        allGrades?.grades?.values?.forEach { semesterResult ->
            semesterResult?.grades?.forEach { gradeItem ->
                if (gradeItem.grade !in listOf("F", "N", "")) {
                    codes.add(gradeItem.courseCode)
                }
            }
        }
        codes
    }

    val categoryEarnedCredits = remember(details, completedCourseCodes) {
        val earned = mutableMapOf<String, Int>()
        for (catDetail in details) {
            var total = 0
            for (basket in catDetail.baskets) {
                for (item in basket.items) {
                    if (item.code in completedCourseCodes) {
                        total += item.credits
                    }
                }
            }
            earned[catDetail.code] = total
        }
        earned
    }

    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    var expandedBaskets by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var downloadingSyllabus by remember { mutableStateOf<String?>(null) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadMessage) {
        if (downloadMessage != null) {
            delay(2500.milliseconds)
            downloadMessage = null
        }
    }

    val allCourses = remember(details) {
        details.flatMap { cat ->
            cat.baskets.flatMap { basket ->
                basket.items.map { item ->
                    Triple(cat.code, cat.name to basket.title, item)
                }
            }
        }
    }

    val filteredCategories = remember(searchQuery, details) {
        if (searchQuery.isBlank()) categories
        else {
            val q = searchQuery.lowercase()
            categories.filter { cat ->
                cat.code.lowercase().contains(q) ||
                cat.name.lowercase().contains(q) ||
                allCourses.any { (code, _, item) ->
                    code == cat.code && (
                        item.code.lowercase().contains(q) ||
                        item.name.lowercase().contains(q)
                    )
                }
            }
        }
    }

    val saveFile = rememberFileSaver()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Curriculum",
            description = "Track your degree requirements",
            showBackButton = true,
            showSyncButton = true,
            syncModules = setOf(SyncModule.CURRICULUM)
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSpacer()

            if (curriculumData == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.School, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("No curriculum data", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Tap sync to load", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                // ── Summary Card ──
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Donut
                            Box(modifier = Modifier.size(100.dp)) {
                                Canvas(modifier = Modifier.size(100.dp)) {
                                    val stroke = 28f
                                    val r = (size.minDimension - stroke) / 2
                                    val topLeft = Offset((size.width - r * 2 - stroke) / 2, (size.height - r * 2 - stroke) / 2)
                                    val arcSize = Size(r * 2 + stroke, r * 2 + stroke)
                                    drawArc(colors.border, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                                    drawArc(colors.chart3, -90f, earnedPct * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                                    if (ongoingPct > 0f)
                                        drawArc(colors.chart4, -90f + earnedPct * 360f, ongoingPct * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                                }
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${(earnedPct * 100).toInt()}%", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 18.sp)
                                        Text(Strings.done, color = colors.textMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(AmazeTheme.spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text("Degree Progress", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                                Text("Credit plan overview", color = colors.textSecondary, fontSize = 11.sp)
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    MetricBox("Earned", "${totalEarned.toFloat().let { if (it == it.toInt().toFloat()) it.toInt().toString() else String.format("%.1f", it) }}", colors.chart3, colors)
                                    MetricBox("In Prog.", "${ongoingCredits.let { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) }}", colors.chart4, colors)
                                    MetricBox("Remain.", "${remainingCredits}", colors.textMuted, colors)
                                    MetricBox("Req.", "$totalRequired", colors.textPrimary, colors)
                                    MetricBox("Grad.", expectedGrad, colors.success, colors)
                                }
                            }
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(colors.chart3, "Earned", colors)
                            LegendDot(colors.chart4, "In Progress", colors)
                            LegendDot(colors.textMuted, "Remaining", colors)
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    }
                }

                // ── Credit Baskets ──
                Text("Credit Baskets", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                categories.forEach { cat ->
                    val required = cat.maxCredits.coerceAtLeast(1)
                    val earned = categoryEarnedCredits[cat.code] ?: 0
                    val pct = (earned.toFloat() / required).coerceAtMost(1f)
                    val isComplete = earned >= required
                    val catDetail = details.find { it.code == cat.code }
                    val baskets = catDetail?.baskets ?: emptyList()
                    val isOpen = expandedCategories.contains(cat.code)

                    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        expandedCategories = if (isOpen) expandedCategories - cat.code else expandedCategories + cat.code
                    }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cat.name, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1)
                                    if (baskets.isNotEmpty())
                                        Text("${baskets.size} basket(s)", color = colors.textMuted, fontSize = 10.sp)
                                }
                                Text("${(pct * 100).toInt()}%", fontWeight = FontWeight.Bold, color = if (isComplete) colors.success else colors.accent, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                                color = if (isComplete) colors.success else colors.accent, trackColor = colors.border,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("$earned earned", color = colors.textSecondary, fontSize = 10.sp)
                                Text("$required req.", color = colors.textSecondary, fontSize = 10.sp)
                            }
                            if (isOpen && baskets.isNotEmpty()) {
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                baskets.forEach { basket ->
                                    val basketKey = "${cat.code}|${basket.title}"
                                    val basketOpen = expandedBaskets.contains(basketKey)
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.surface).clickable {
                                                expandedBaskets = if (basketOpen) expandedBaskets - basketKey else expandedBaskets + basketKey
                                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(if (basketOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                            Text(basket.title, color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                            Text("${basket.credits} cr", color = colors.textMuted, fontSize = 11.sp)
                                        }
                                        if (basketOpen) {
                                            basket.items.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(item.code, color = colors.textMuted, fontSize = 10.sp)
                                                        Text(item.name, color = colors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (item.type != null) {
                                                            Text(normalizeType(item.type), color = colors.info, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.info.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp))
                                                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                                        }
                                                        Text("${item.credits} cr", color = colors.textMuted, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Course Details by Category ──
                if (details.isNotEmpty()) {
                    Text("Course Details by Category", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by code, name...", color = colors.textMuted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                        trailingIcon = if (searchQuery.isNotEmpty()) ({
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, null, tint = colors.textMuted)
                            }
                        }) else null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                    )

                    filteredCategories.forEach { cat ->
                        val earnedCredits = categoryEarnedCredits[cat.code] ?: 0
                        val pct = if (cat.maxCredits > 0) (earnedCredits.toFloat() / cat.maxCredits).coerceAtMost(1f) else 0f
                        val isOpen = expandedCategories.contains("detail|${cat.code}")
                        val catDetail = details.find { it.code == cat.code }
                        val baskets = if (searchQuery.isNotBlank()) {
                            catDetail?.baskets?.map { b ->
                                b.copy(items = b.items.filter { item ->
                                    val q = searchQuery.lowercase()
                                    item.code.lowercase().contains(q) || item.name.lowercase().contains(q)
                                })
                            }?.filter { it.items.isNotEmpty() } ?: emptyList()
                        } else catDetail?.baskets ?: emptyList()

                        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = {
                            expandedCategories = if (isOpen) expandedCategories - "detail|${cat.code}" else expandedCategories + "detail|${cat.code}"
                        }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.code, fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 11.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.accent.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp))
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Text(cat.name, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text("$earnedCredits/${cat.maxCredits}", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                    Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                                    color = colors.accent, trackColor = colors.border,
                                )

                                if (isOpen && baskets.isNotEmpty()) {
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                                    baskets.forEach { basket ->
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(basket.title, fontWeight = FontWeight.Medium, color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                                Text("${basket.credits} cr", color = colors.textMuted, fontSize = 11.sp)
                                            }
                                            basket.items.forEach { item ->
                                                val isCompleted = item.code in completedCourseCodes
                                                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(item.code, color = if (isCompleted) colors.success else colors.textMuted, fontSize = 10.sp)
                                                        Text(item.name, color = colors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (isCompleted) {
                                                            Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                                        }
                                                        Text("${item.credits} cr", color = if (isCompleted) colors.success else colors.textMuted, fontSize = 11.sp)
                                                        Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                                    IconButton(
                                                        onClick = {
                                                            downloadingSyllabus = item.code
                                                            scope.launch {
                                                                downloadMessage = null
                                                                val bytes = AmazeClient.getSyllabusPdf(item.code)
                                                                if (bytes != null) {
                                                                    val saved = saveFile("Syllabus_${item.code}.${bytes.extension}", bytes.bytes)
                                                                    downloadMessage = if (saved) "Downloaded ${item.code}" else "Failed to save ${item.code}"
                                                                } else {
                                                                    downloadMessage = "Download failed for ${item.code}"
                                                                }
                                                                downloadingSyllabus = null
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                        enabled = downloadingSyllabus != item.code
                                                    ) {
                                                        if (downloadingSyllabus == item.code) {
                                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
                                                        } else {
                                                            Icon(Icons.Rounded.Download, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isOpen && baskets.isEmpty()) {
                                    Text("No course details", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            downloadMessage?.let { msg ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(
                    if (msg.startsWith("Downloaded")) colors.success.copy(alpha = 0.15f) else colors.danger.copy(alpha = 0.15f)
                ).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (msg.startsWith("Downloaded")) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                            null, tint = if (msg.startsWith("Downloaded")) colors.success else colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AmazeTheme.spacing.xs))
                        Text(msg, color = colors.textPrimary, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.md))
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, color: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(IntrinsicSize.Min)) {
        Text(value, fontWeight = FontWeight.Black, color = color, fontSize = 13.sp)
        Text(label, color = colors.textMuted, fontSize = 8.sp)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(color))
        Spacer(Modifier.width(AmazeTheme.spacing.xs))
        Text(label, color = colors.textMuted, fontSize = 10.sp)
    }
}
