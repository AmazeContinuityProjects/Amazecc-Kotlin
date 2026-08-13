package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.DownloadProgressSheet
import com.amazecc.app.shared.ui.components.ErrorReportSheet
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.utils.rememberPdfOpener
import com.amazecc.app.shared.utils.rememberUrlOpener
import com.amazecc.app.shared.utils.showDownloadCompleteNotification
import com.amazecc.app.shared.utils.toFixed
import kotlinx.coroutines.launch

private fun normalizeType(raw: String?): String = when (raw?.uppercase()) {
    "TH" -> "Theory"; "LO" -> "Lab"; "ETL" -> "Embedded"; "PJT" -> "Project"
    "SS" -> "Soft Skill"; "OC" -> "Online"; else -> raw ?: "Other"
}

private fun categoryIcon(code: String, name: String): ImageVector {
    val n = "$code $name".lowercase()
    return when {
        n.contains("basic science") || n.contains("chemistry") || n.contains("physics") ||
            n.contains("biology") || n.contains("math") -> Icons.Rounded.Science
        n.contains("engineering") || n.contains("design") || n.contains("drawing") ||
            n.contains("workshop") -> Icons.Rounded.Build
        n.contains("project") || n.contains("thesis") -> Icons.Rounded.Work
        n.contains("intern") -> Icons.Rounded.Handshake
        n.contains("soft skill") || n.contains("language") || n.contains("humanit") ||
            n.contains("social") -> Icons.Rounded.RecordVoiceOver
        n.contains("online") || n.contains("elective") || n.contains("open") -> Icons.Rounded.Laptop
        n.contains("core") || n.contains("profession") -> Icons.Rounded.School
        else -> Icons.Rounded.School
    }
}

private fun categoryTint(index: Int, colors: AmazeColors): Color = when (index % 6) {
    0 -> colors.chart1
    1 -> colors.chart2
    2 -> colors.chart3
    3 -> colors.info
    4 -> colors.warning
    else -> colors.chart4
}

private fun fmtCredits(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else v.toFixed(1)

private sealed interface DownloadSheetState {
    data class Error(val error: String, val courseCode: String) : DownloadSheetState
    data class Info(val message: String) : DownloadSheetState
}

private fun encodeUrlComponent(s: String): String = buildString {
    val bytes = s.encodeToByteArray()
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c in "-_.~".map { it.code }) {
            append(c.toChar())
        } else {
            append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun buildIssueUrl(title: String, body: String): String =
    "https://github.com/AmazeContinuityProjects/Amazecc-Kotlin/issues/new?title=${encodeUrlComponent(title)}&body=${encodeUrlComponent(body)}"

private data class CurriculumItemUi(
    val code: String,
    val name: String,
    val credits: Int,
    val type: String?,
    val isCompleted: Boolean
)

private data class CurriculumBasketUi(
    val title: String,
    val credits: Int,
    val items: List<CurriculumItemUi>
)

private data class CurriculumCategoryUi(
    val code: String,
    val name: String,
    val required: Int,
    val earned: Int,
    val baskets: List<CurriculumBasketUi>
)

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

    val categoryTree = remember(details, categories, categoryEarnedCredits, completedCourseCodes) {
        if (details.isNotEmpty()) {
            details.map { d ->
                CurriculumCategoryUi(
                    code = d.code,
                    name = d.name,
                    required = categories.find { it.code == d.code }?.maxCredits
                        ?: d.baskets.sumOf { it.credits },
                    earned = categoryEarnedCredits[d.code] ?: 0,
                    baskets = d.baskets.map { b ->
                        CurriculumBasketUi(
                            title = b.title,
                            credits = b.credits,
                            items = b.items.map { item ->
                                CurriculumItemUi(
                                    code = item.code,
                                    name = item.name,
                                    credits = item.credits,
                                    type = item.type,
                                    isCompleted = item.code in completedCourseCodes
                                )
                            }
                        )
                    }
                )
            }
        } else {
            categories.map { c ->
                CurriculumCategoryUi(
                    code = c.code,
                    name = c.name,
                    required = c.maxCredits.coerceAtLeast(1),
                    earned = categoryEarnedCredits[c.code] ?: 0,
                    baskets = emptyList()
                )
            }
        }
    }

    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    var expandedBaskets by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var downloadingSyllabus by remember { mutableStateOf<String?>(null) }
    var downloadSheet by remember { mutableStateOf<DownloadSheetState?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    // Hidden search activated via the header search icon
    val localSearchTick by AppState.localSearchTick.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(localSearchTick) {
        if (localSearchTick > 0) {
            searchActive = true
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val filteredTree = remember(searchQuery, categoryTree) {
        if (searchQuery.isBlank()) categoryTree
        else {
            val q = searchQuery.lowercase()
            categoryTree.mapNotNull { cat ->
                val catMatch = cat.code.lowercase().contains(q) || cat.name.lowercase().contains(q)
                val filteredBaskets = cat.baskets.mapNotNull { b ->
                    val items = b.items.filter {
                        it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
                    }
                    if (items.isNotEmpty()) b.copy(items = items) else null
                }
                if (catMatch || filteredBaskets.isNotEmpty()) cat.copy(baskets = filteredBaskets) else null
            }
        }
    }

    val pdfOpener = rememberPdfOpener()
    val urlOpener = rememberUrlOpener()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {

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
                        Text("Tap sync to load", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                    }
                }
            } else {
                // ── Degree Progress hero ──
                CurriculumHeroCard(
                    earnedPct = earnedPct,
                    ongoingPct = ongoingPct,
                    totalEarned = totalEarned,
                    ongoingCredits = ongoingCredits,
                    remainingCredits = remainingCredits,
                    totalRequired = totalRequired,
                    expectedGrad = expectedGrad,
                    colors = colors
                )

                // ── Categories ──
                SettingsGroupLabel("Categories")

                AnimatedVisibility(
                    visible = searchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    searchActive = false
                                    keyboardController?.hide()
                                    true
                                } else {
                                    false
                                }
                            },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                    )
                }

                if (filteredTree.isEmpty() && searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("No matching courses", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                        }
                    }
                }

                filteredTree.forEachIndexed { index, cat ->
                    val isOpen = expandedCategories.contains(cat.code)
                    CurriculumCategoryCard(
                        cat = cat,
                        index = index,
                        isOpen = isOpen,
                        expandedBaskets = expandedBaskets,
                        searching = searchQuery.isNotBlank(),
                        downloadingSyllabus = downloadingSyllabus,
                        onToggleCategory = {
                            expandedCategories = if (isOpen) expandedCategories - cat.code else expandedCategories + cat.code
                        },
                        onToggleBasket = { basketKey ->
                            expandedBaskets = if (expandedBaskets.contains(basketKey)) expandedBaskets - basketKey else expandedBaskets + basketKey
                        },
                        onOpenCourse = { AppState.openCourseDetail(it) },
                        onDownload = { itemCode ->
                            downloadingSyllabus = itemCode
                            downloadProgress = 0f
                            scope.launch {
                                val result = AmazeClient.getSyllabusPdf(itemCode) { progress ->
                                    downloadProgress = progress
                                }
                                val download = result.download
                                if (download != null) {
                                    showDownloadCompleteNotification("Syllabus_$itemCode.${download.extension}")
                                    val opened = pdfOpener("Syllabus_$itemCode.${download.extension}", download.bytes)
                                    downloadSheet = if (opened) {
                                        null
                                    } else {
                                        DownloadSheetState.Info(
                                            "The PDF is saved in Downloads but couldn't be opened automatically - a PDF viewer may be missing."
                                        )
                                    }
                                } else {
                                    downloadSheet = DownloadSheetState.Error(
                                        result.error ?: "Unknown error",
                                        itemCode
                                    )
                                }
                                downloadingSyllabus = null
                                downloadProgress = null
                            }
                        },
                        colors = colors
                    )
                }
            }
            Spacer(Modifier.height(AmazeTheme.spacing.md))
        }
    }

    val inProgress = downloadingSyllabus
    if (downloadProgress != null && inProgress != null) {
        DownloadProgressSheet(
            fileName = "Syllabus_$inProgress.pdf",
            progress = downloadProgress ?: 0f
        )
    }

    when (val sheet = downloadSheet) {
        is DownloadSheetState.Error -> ErrorReportSheet(
            icon = Icons.Rounded.Error,
            iconTint = colors.danger,
            title = "Download failed",
            message = sheet.error,
            detail = "Course: ${sheet.courseCode}",
            onReport = {
                urlOpener(buildIssueUrl("Syllabus download failed: ${sheet.courseCode}", sheet.error))
                downloadSheet = null
            },
            onDismiss = { downloadSheet = null }
        )
        is DownloadSheetState.Info -> ErrorReportSheet(
            icon = Icons.Rounded.CheckCircle,
            iconTint = colors.success,
            title = "Downloaded",
            message = sheet.message,
            onDismiss = { downloadSheet = null }
        )
        null -> Unit
    }
}

@Composable
private fun CurriculumHeroCard(
    earnedPct: Float,
    ongoingPct: Float,
    totalEarned: Float,
    ongoingCredits: Float,
    remainingCredits: Int,
    totalRequired: Int,
    expectedGrad: String,
    colors: AmazeColors
) {
    val heroGradient = remember(colors) {
        Brush.linearGradient(colors = listOf(colors.accent, colors.accent.copy(alpha = 0.6f)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(heroGradient)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Degree Progress",
                    color = Color.White.copy(alpha = 0.9f),
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (remainingCredits <= 0) "Ready to Graduate" else "$expectedGrad left",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(100.dp)) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val stroke = 28f
                        val r = (size.minDimension - stroke) / 2
                        val topLeft = Offset((size.width - r * 2 - stroke) / 2, (size.height - r * 2 - stroke) / 2)
                        val arcSize = Size(r * 2 + stroke, r * 2 + stroke)
                        drawArc(Color.White.copy(alpha = 0.25f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        drawArc(Color.White, -90f, earnedPct * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        if (ongoingPct > 0f)
                            drawArc(Color.White.copy(alpha = 0.55f), -90f + earnedPct * 360f, ongoingPct * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(earnedPct * 100).toInt()}%", fontWeight = FontWeight.Black, color = Color.White, fontSize = AmazeTheme.fontSize.xl)
                            Text(Strings.done, color = Color.White.copy(alpha = 0.8f), fontSize = AmazeTheme.fontSize.micro)
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStatBox("Earned", fmtCredits(totalEarned), Modifier.weight(1f))
                        HeroStatBox("In Progress", fmtCredits(ongoingCredits), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStatBox("Remaining", "$remainingCredits", Modifier.weight(1f))
                        HeroStatBox("Required", "$totalRequired", Modifier.weight(1f))
                    }
                }
            }

            Column {
                LinearProgressIndicator(
                    progress = { (earnedPct + ongoingPct).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroLegendDot(1f, "Earned")
                    HeroLegendDot(0.55f, "In Progress")
                    HeroLegendDot(0.25f, "Remaining")
                }
            }
        }
    }
}

@Composable
private fun HeroStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(AmazeTheme.radius.medium))
            .padding(10.dp)
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.md, color = Color.White)
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun HeroLegendDot(alpha: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(Color.White.copy(alpha = alpha)))
        Spacer(Modifier.width(AmazeTheme.spacing.xs))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = AmazeTheme.fontSize.micro)
    }
}

@Composable
private fun CurriculumCategoryCard(
    cat: CurriculumCategoryUi,
    index: Int,
    isOpen: Boolean,
    expandedBaskets: Set<String>,
    searching: Boolean,
    downloadingSyllabus: String?,
    onToggleCategory: () -> Unit,
    onToggleBasket: (String) -> Unit,
    onOpenCourse: (String) -> Unit,
    onDownload: (String) -> Unit,
    colors: AmazeColors
) {
    val required = cat.required.coerceAtLeast(1)
    val pct = (cat.earned.toFloat() / required).coerceAtMost(1f)
    val isComplete = cat.earned >= required
    val totalCourses = cat.baskets.sumOf { it.items.size }
    val completedCourses = cat.baskets.sumOf { b -> b.items.count { it.isCompleted } }
    val tint = categoryTint(index, colors)

    AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = onToggleCategory, contentPadding = PaddingValues(0.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(categoryIcon(cat.code, cat.name), null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat.name, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("$cat.earned of $required credits")
                            if (totalCourses > 0) append(" · $totalCourses courses")
                            if (completedCourses > 0) append(" · $completedCourses done")
                        },
                        color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isComplete) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.success.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("DONE", color = colors.success, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro)
                    }
                } else {
                    Text("${(pct * 100).toInt()}%", fontWeight = FontWeight.Black, color = colors.accent, fontSize = AmazeTheme.fontSize.sm)
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (isOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    null, tint = colors.textMuted, modifier = Modifier.size(20.dp)
                )
            }

            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                    color = if (isComplete) colors.success else colors.accent,
                    trackColor = colors.border
                )
            }

            if (isOpen) {
                if (cat.baskets.isEmpty()) {
                    Text(
                        if (searching) "No matching courses" else "No course details",
                        color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    )
                } else {
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    cat.baskets.forEach { basket ->
                        val basketKey = "${cat.code}|${basket.title}"
                        val basketOpen = expandedBaskets.contains(basketKey)
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleBasket(basketKey) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (basketOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    null, tint = colors.textMuted, modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(basket.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = AmazeTheme.fontSize.sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${basket.items.size} courses · ${basket.credits} cr", color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
                                }
                            }
                            if (basketOpen) {
                                basket.items.forEach { item ->
                                    CurriculumItemRow(
                                        item = item,
                                        downloadingSyllabus = downloadingSyllabus,
                                        onOpenCourse = onOpenCourse,
                                        onDownload = onDownload,
                                        colors = colors
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = colors.border.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun typeColor(type: String?, colors: AmazeColors): Color = when (normalizeType(type)) {
    "Lab" -> colors.success
    "Embedded" -> colors.chart3
    "Project" -> colors.info
    "Soft Skill" -> colors.warning
    "Online" -> colors.chart1
    else -> colors.info
}

private fun typeIcon(type: String?): ImageVector = when (normalizeType(type)) {
    "Lab" -> Icons.Rounded.Science
    "Embedded" -> Icons.Rounded.Layers
    "Project" -> Icons.Rounded.Work
    "Soft Skill" -> Icons.Rounded.RecordVoiceOver
    "Online" -> Icons.Rounded.Laptop
    else -> Icons.Rounded.AutoStories
}

@Composable
private fun CurriculumItemRow(
    item: CurriculumItemUi,
    downloadingSyllabus: String?,
    onOpenCourse: (String) -> Unit,
    onDownload: (String) -> Unit,
    colors: AmazeColors
) {
    val isCompleted = item.isCompleted
    val tColor = typeColor(item.type, colors)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(if (isCompleted) colors.success.copy(alpha = 0.06f) else colors.surface)
            .border(
                1.dp,
                if (isCompleted) colors.success.copy(alpha = 0.35f) else colors.border.copy(alpha = 0.6f),
                RoundedCornerShape(AmazeTheme.radius.small)
            )
            .clickable { onOpenCourse(item.code) }
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(
                        if (isCompleted) colors.success.copy(alpha = 0.15f)
                        else tColor.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCompleted) Icons.Rounded.CheckCircle else typeIcon(item.type),
                    null,
                    tint = if (isCompleted) colors.success else tColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = AmazeTheme.fontSize.sm, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        item.code, color = colors.accent, fontSize = AmazeTheme.fontSize.micro,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.accent.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (item.type != null) {
                        Text(
                            normalizeType(item.type), color = tColor, fontSize = AmazeTheme.fontSize.micro,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(tColor.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("${item.credits} cr", color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .clickable(enabled = downloadingSyllabus != item.code) { onDownload(item.code) },
                contentAlignment = Alignment.Center
            ) {
                if (downloadingSyllabus == item.code) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                } else {
                    Icon(Icons.Rounded.Download, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}