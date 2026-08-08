package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlin.math.*

private val gradeColorIndex = mapOf(
    "S" to 0, "A" to 1, "B" to 2, "C" to 3, "D" to 4, "E" to 4, "F" to 4, "N" to 4
)

private fun gradeChartColor(index: Int, colors: com.amazecc.app.shared.theme.AmazeColors): Color = when (index) {
    0 -> colors.chart1; 1 -> colors.chart2; 2 -> colors.chart3; 3 -> colors.chart4; else -> colors.chart5
}

private data class TrendPoint(val gpa: Float, val marksPct: Float)
private data class RadarPoint(val label: String, val score: Float)

@Composable
fun GradesScreen() {
    val colors = AmazeTheme.colors
    val allGradesRes by AppState.allGrades.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()

    val gpaRecords = allGradesRes?.grades ?: emptyMap()
    val semesterIds = gpaRecords.filter { (_, v) -> v?.gpa != null && v?.grades?.isNotEmpty() == true }
        .keys.toList().sortedDescending()

    var selectedSemesterId by remember { mutableStateOf(semesterIds.firstOrNull() ?: "") }
    var expandedCourseId by remember { mutableStateOf<String?>(null) }
    val selectedSemester = gpaRecords[selectedSemesterId]
    val gradeList = selectedSemester?.grades ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Grade History",
            description = "All semesters — GPA and course grades",
            showBackButton = true,
            showSyncButton = false
        )
        com.amazecc.app.shared.ui.components.HeaderSpacer()

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (gpaRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.History, null, modifier = Modifier.size(64.dp), tint = colors.textMuted)
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text("No grade history available.", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary))
                    }
                }
                return
            }

            // Performance Analysis Header
            PerformanceHeader(selectedSemester?.gpa ?: "", gradeList.size, gradeList.sumOf { it.details?.size ?: 0 }, colors)

            // Semester Switcher
            SemesterSwitcher(semesterIds, selectedSemesterId, semesterMap, colors) { selectedSemesterId = it; expandedCourseId = null }

            // 3 Charts Row
            ChartsGroup(semesterIds, selectedSemester, selectedSemesterId, gpaRecords, gradeList, colors)

            // Stats Grid
            StatsGrid(gradeList, colors)

            // Grades List
            GradesList(gradeList, expandedCourseId, { expandedCourseId = it }, colors)
        }
    }
}

@Composable
private fun PerformanceHeader(gpa: String, courseCount: Int, assessmentCount: Int, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(AmazeTheme.radius.large)).background(colors.accent).padding(20.dp)
    ) {
        Column {
            Text("Performance Analysis", style = AmazeTheme.typography.smallLabel.copy(color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
            Text("$gpa GPA", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Black, color = Color.White))
            Text("$courseCount Courses · $assessmentCount Assessments", style = AmazeTheme.typography.smallLabel.copy(color = Color.White.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun SemesterSwitcher(
    ids: List<String>, selected: String, semesterMap: Map<String, String>,
    colors: com.amazecc.app.shared.theme.AmazeColors, onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ids.forEach { id ->
            val semName = if (id.endsWith("1")) "FS ${id.substring(4, 6)}" else "WS ${id.substring(4, 6)}"
            val isActive = id == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(if (isActive) colors.accent else colors.surface)
                    .border(if (isActive) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(semName, style = AmazeTheme.typography.smallLabel.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) Color.White else colors.textSecondary
                ))
            }
        }
    }
}

@Composable
private fun ChartsGroup(
    allIds: List<String>, activeSem: com.amazecc.app.shared.model.SemesterGradeResult?,
    activeId: String, records: Map<String, com.amazecc.app.shared.model.SemesterGradeResult?>,
    gradeList: List<GradeItem>, colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val trendData = remember(allIds, records) {
        allIds.map { id ->
            val sem = records[id]
            val g = sem?.gpa?.toFloatOrNull() ?: 0f
            val grades = sem?.grades ?: emptyList()
            val scored = grades.sumOf { (it.grandTotal.toFloatOrNull() ?: 0f).toDouble() }.toFloat()
            val marksPct = if (grades.isNotEmpty()) scored / grades.size else 0f
            TrendPoint(g, marksPct)
        }
    }

    val radarData = remember(gradeList) {
        gradeList.map { RadarPoint(it.courseCode.take(8), it.grandTotal.toFloatOrNull() ?: 0f) }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).height(200.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium)).padding(12.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Subject Performance", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { if (radarData.isNotEmpty()) RadarChartCanvas(radarData, colors) else ChartEmpty(colors) }
                }
            }
            Box(modifier = Modifier.weight(1f).height(200.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium)).padding(12.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("GPA Trend", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { if (trendData.size >= 2) LineChartCanvas(trendData, { it.gpa }, colors.chart1, colors) else ChartEmpty(colors) }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium)).padding(12.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("Marks % Trend", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { if (trendData.isNotEmpty()) BarChartCanvas(trendData, { it.marksPct }, colors.chart2, colors) else ChartEmpty(colors) }
            }
        }
    }
}

@Composable
private fun ChartEmpty(colors: com.amazecc.app.shared.theme.AmazeColors) {
    Text("No data", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
}

@Composable
private fun RadarChartCanvas(data: List<RadarPoint>, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val anim by animateFloatAsState(targetValue = 1f, animationSpec = tween(800))
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        if (data.isEmpty()) return@Canvas
        val cx = size.width / 2f; val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.7f * anim
        val n = data.size; val angleStep = (2f * PI / n).toFloat()

        for (ring in 1..4) {
            val r = radius * ring / 4f
            val p = Path()
            for (i in 0 until n) {
                val a = -PI / 2f + i * angleStep
                val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close(); drawPath(p, colors.border.copy(alpha = 0.3f), style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) {
            val a = -PI / 2f + i * angleStep
            drawLine(colors.border.copy(alpha = 0.3f), Offset(cx, cy), Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat()), strokeWidth = 1.dp.toPx())
        }
        val dp = Path()
        data.forEachIndexed { i, pt ->
            val a = -PI / 2f + i * angleStep; val r = (pt.score / 100f).coerceIn(0f, 1f) * radius
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (i == 0) dp.moveTo(x, y) else dp.lineTo(x, y)
        }
        dp.close()
        drawPath(dp, colors.chart3.copy(alpha = 0.3f))
        drawPath(dp, colors.chart3, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        data.forEachIndexed { i, pt ->
            val a = -PI / 2f + i * angleStep; val r = (pt.score / 100f).coerceIn(0f, 1f) * radius
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            drawCircle(Color.White, 4.dp.toPx(), Offset(x, y))
            drawCircle(colors.chart3, 3.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun LineChartCanvas(data: List<TrendPoint>, value: (TrendPoint) -> Float, lineColor: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val anim by animateFloatAsState(targetValue = 1f, animationSpec = tween(800))
    Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
        if (data.size < 2) return@Canvas
        val values = data.map(value)
        val minV = (values.minOrNull() ?: 0f) * 0.9f; val maxV = (values.maxOrNull() ?: 10f) * 1.1f
        val range = maxV - minV; val stepX = size.width / (data.size - 1).coerceAtLeast(1)

        val fillPath = Path()
        data.forEachIndexed { i, _ ->
            val x = i * stepX; val y = size.height - ((value(data[i]) - minV) / range * size.height)
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo((data.size - 1) * stepX, size.height); fillPath.lineTo(0f, size.height); fillPath.close()
        drawPath(fillPath, lineColor.copy(alpha = 0.15f))

        val linePath = Path()
        data.forEachIndexed { i, _ ->
            val x = i * stepX * anim; val y = size.height - ((value(data[i]) - minV) / range * size.height)
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        drawPath(linePath, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        data.forEachIndexed { i, _ ->
            val x = i * stepX * anim; val y = size.height - ((value(data[i]) - minV) / range * size.height)
            drawCircle(Color.White, 4.dp.toPx(), Offset(x, y))
            drawCircle(lineColor, 3.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun BarChartCanvas(data: List<TrendPoint>, value: (TrendPoint) -> Float, barColor: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val anim by animateFloatAsState(targetValue = 1f, animationSpec = tween(800))
    Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
        if (data.isEmpty()) return@Canvas
        val maxV = data.maxOf { value(it) }.coerceAtLeast(1f)
        val bw = size.width / data.size * 0.6f; val gap = size.width / data.size * 0.2f
        data.forEachIndexed { i, _ ->
            val h = (value(data[i]) / maxV) * size.height * anim
            val x = i * (bw + gap * 2) + gap; val y = size.height - h
            drawRoundRect(barColor, Offset(x, y), Size(bw, h), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        }
    }
}

@Composable
private fun StatsGrid(gradeList: List<GradeItem>, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val scored = gradeList.mapNotNull { it.grandTotal.toFloatOrNull() }.filter { it > 0 }
    val highest = scored.maxOrNull(); val lowest = scored.minOrNull()
    val avg = if (scored.isNotEmpty()) scored.sum() / scored.size else 0f
    val dist = gradeList.groupBy { it.grade }.mapValues { it.value.size }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Highest", highest?.let { "${gradeList.find { g -> g.grandTotal.toFloatOrNull() == it }?.courseCode?.take(6) ?: ""} · ${it.toInt()}%" } ?: "-", Icons.Rounded.EmojiEvents, colors.chart1, colors, Modifier.weight(1f))
        StatCard("Lowest", lowest?.let { "${gradeList.find { g -> g.grandTotal.toFloatOrNull() == it }?.courseCode?.take(6) ?: ""} · ${it.toInt()}%" } ?: "-", Icons.Rounded.ArrowDownward, colors.chart5, colors, Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Avg Score", "${avg.toInt()}%", Icons.Rounded.BarChart, colors.chart2, colors, Modifier.weight(1f))
        StatCard("Grade Spread", dist.entries.joinToString(" ") { "${it.key}:${it.value}" }, Icons.Rounded.Star, colors.chart3, colors, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium)).padding(12.dp)) {
        Column {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            Text(value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
            Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
        }
    }
}

@Composable
private fun GradesList(gradeList: List<GradeItem>, expandedCourseId: String?, onToggle: (String?) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Course Grades", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        if (gradeList.isEmpty()) {
            Text("No courses found for this semester.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        } else {
            gradeList.forEachIndexed { idx, course ->
                val key = "${course.courseCode}-$idx"
                val gradeIdx = gradeColorIndex[course.grade] ?: 4
                val gradeColor = gradeChartColor(gradeIdx, colors)
                val isPass = course.grade !in listOf("F", "N")
                
                GradeCourseCard(course, expandedCourseId == key, { onToggle(if (expandedCourseId == key) null else key) }, gradeColor, isPass, colors)
            }
        }
    }
}

@Composable
private fun GradeCourseCard(course: GradeItem, isOpen: Boolean, onToggle: () -> Unit, gradeColor: Color, isPass: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val gColor = if (isPass) colors.chart1 else colors.chart5

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium)).clickable { onToggle() }) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().drawBehind {
                val sw = 4.dp.toPx()
                drawRoundRect(gradeColor, Offset(0f, 0f), Size(sw, size.height))
            }.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (isOpen) 0.dp else 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(gradeColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text(course.grade, style = AmazeTheme.typography.subheading.copy(color = gradeColor, fontWeight = FontWeight.Black))
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${course.courseCode} · ${course.courseTitle}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.accent.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(course.courseType, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Medium))
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(gColor.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("Overall ${course.grandTotal}%", style = AmazeTheme.typography.smallLabel.copy(color = gColor, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                }
            }

            if (isOpen) {
                Box(modifier = Modifier.fillMaxWidth().drawBehind { drawRect(colors.border.copy(alpha = 0.5f), Offset(0f, 0f), Size(size.width, 1.dp.toPx())) }.padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val details = course.details
                        if (!details.isNullOrEmpty()) {
                            Text("Assessments", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                details.chunked(2).forEach { chunk ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        chunk.forEach { detail ->
                                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.05f)).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small)).padding(6.dp), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(com.amazecc.app.shared.ui.components.shortenAssessmentName(detail.component), style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                                    Text("${detail.scoredMark}", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                                    Text("/${detail.maxMark}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        course.range?.let { range ->
                            Box(modifier = Modifier.fillMaxWidth().drawBehind { drawRect(colors.border.copy(alpha = 0.5f), Offset(0f, 0f), Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp)) {
                                Column {
                                    Text("Grade Ranges", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("S" to range.S, "A" to range.A, "B" to range.B, "C" to range.C, "D" to range.D, "E" to range.E, "F" to range.F).chunked(3).forEach { chunk ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                chunk.forEach { (g, r) ->
                                                    val c = gradeChartColor(gradeColorIndex[g] ?: 4, colors)
                                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(c.copy(alpha = 0.08f)).border(1.dp, c.copy(alpha = 0.2f), RoundedCornerShape(AmazeTheme.radius.xs)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(g, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Black, color = c))
                                                            Text(r, style = AmazeTheme.typography.smallLabel.copy(color = c.copy(alpha = 0.7f), fontSize = AmazeTheme.fontSize.micro))
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
                }
            }
        }
    }
}
