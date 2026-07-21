package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.config.SlotMap
import kotlinx.datetime.*
import kotlinx.serialization.json.*

data class CourseGroup(
    val courseCode: String,
    val courseTitle: String,
    val semesterSubId: String,
    val semesterName: String,
    val theory: MarksCourseItem? = null,
    val lab: MarksCourseItem? = null,
    val theoryAtt: AttendanceItem? = null,
    val labAtt: AttendanceItem? = null
)

@Composable
fun CourseDetailScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val courseCode = AppState.selectedCourseCode.value ?: ""
    val semesterId = AppState.selectedCourseSemester.value
    val allSemesterMarks by AppState.allSemesterMarks.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val timetable by AppState.timetable.collectAsState()
    val calendar by AppState.calendar.collectAsState()

    val mainSemesterId = semesterId ?: attendanceRes?.semesterId ?: "CH20262701"

    val group = remember(courseCode, mainSemesterId, allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes) {
        findCourseGroup(courseCode, mainSemesterId, allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes, timetable)
    }

    val isEmbedded = (group?.theory != null && group?.lab != null) || (group?.theoryAtt != null && group?.labAtt != null)

    // Fetch QCM data for this course
    var qcmTables by remember { mutableStateOf<List<QcmTable>>(emptyList()) }
    var qcmLoading by remember { mutableStateOf(false) }
    var qcmError by remember { mutableStateOf<String?>(null) }

    fun fetchQcm() {
        if (qcmLoading) return
        qcmLoading = true
        qcmError = null
        kotlinx.coroutines.MainScope().launch {
            try {
                val res = AmazeClient.getQcmView()
                if (res.success) {
                    qcmTables = res.data ?: emptyList()
                    if (qcmTables.isEmpty()) qcmError = "No QCM data available"
                } else qcmError = res.message
            } catch (e: Exception) { qcmError = e.message }
            qcmLoading = false
        }
    }

    var innerTab by remember { mutableStateOf("overview") }
    val tabs = listOf("overview", "grades", "marks", "attendance", "notes", "qbank")
    val tabLabels = mapOf("overview" to "Overview", "grades" to "Grade History", "marks" to "Marks", "attendance" to "Attendance", "notes" to "Notes", "qbank" to "QBank")

    if (group == null) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Course not found", color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                AmazeButton("Go Back", onClick = onBack)
            }
        }
        return
    }

    val theoryAtt = group.theoryAtt
    val labAtt = group.labAtt
    val mainAtt = theoryAtt ?: labAtt
    val theoryMarks = group.theory
    val labMarks = group.lab

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = group.courseCode,
            description = group.courseTitle,
            showBackButton = true,
            showSyncButton = false
        )

        // Tab row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val selected = innerTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.surface)
                        .clickable { innerTab = tab; if (tab == "overview" && qcmTables.isEmpty() && !qcmLoading) fetchQcm() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        tabLabels[tab] ?: tab,
                        color = if (selected) colors.background else colors.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        when (innerTab) {
            "overview" -> OverviewTab(group, theoryAtt, labAtt, mainAtt, isEmbedded, qcmTables, qcmLoading, qcmError, { fetchQcm() }, colors)
            "grades" -> GradeHistoryTab(courseCode, allGrades, colors)
            "marks" -> MarksTab(theoryMarks, labMarks, isEmbedded, mainSemesterId, allGrades, colors)
            "attendance" -> AttendanceTab(group, theoryAtt, labAtt, mainAtt, isEmbedded, calendar, colors)
            "notes" -> NotesTab(courseCode, colors)
            "qbank" -> QBankTab(courseCode, colors)
        }
    }
}

@Composable
private fun OverviewTab(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    qcmTables: List<QcmTable>,
    qcmLoading: Boolean,
    qcmError: String?,
    fetchQcm: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Attendance Overview
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Attendance Overview", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (isEmbedded) {
                        theoryAtt?.let { att ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Theory", fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6), fontSize = 12.sp)
                                    Text("${att.attendedClasses} / ${att.totalClasses} classes", color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Text(att.attendancePercentage, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                        labAtt?.let { att ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Lab", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 12.sp)
                                    Text("${att.attendedClasses} / ${att.totalClasses} classes", color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Text(att.attendancePercentage, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        mainAtt?.let { att ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(att.attendancePercentage, fontWeight = FontWeight.Black, fontSize = 36.sp, color = colors.accent)
                                Text("${att.attendedClasses} / ${att.totalClasses} classes attended", color = colors.textSecondary, fontSize = 12.sp)
                            }
                        } ?: Text("No attendance data", color = colors.textMuted)
                    }
                }
            }
        }

        // Course Details
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Course Details", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    val mainCourse = group.theory ?: group.lab
                    DetailRow("Type", if (isEmbedded) "Embedded" else (mainCourse?.courseType ?: "-"), colors)
                    DetailRow("Slot", mainCourse?.slot ?: "-", colors)
                    DetailRow("Faculty", mainCourse?.faculty ?: "-", colors)
                    DetailRow("System", mainCourse?.courseSystem ?: "-", colors)
                    mainAtt?.credits?.let { DetailRow("Credits", it, colors) }
                    if (isEmbedded) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.1f)).padding(12.dp)
                        ) {
                            Column {
                                Text("Components", fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                group.theory?.let { Text("${it.courseType} — Class: ${it.classNbr.takeLast(4)}", fontSize = 12.sp, color = colors.textPrimary) }
                                group.lab?.let { Text("${it.courseType} — Class: ${it.classNbr.takeLast(4)}", fontSize = 12.sp, color = colors.textPrimary) }
                            }
                        }
                    }
                }
            }
        }

        // QCM Section
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality Circle Meeting (QCM)", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (qcmTables.isEmpty() && !qcmLoading) {
                            AmazeButton("Load", onClick = fetchQcm, variant = ButtonVariant.SECONDARY, modifier = Modifier.height(32.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        qcmLoading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        qcmError != null -> Text(qcmError!!, color = colors.textMuted, fontSize = 12.sp)
                        qcmTables.isEmpty() -> Text("Tap Load to fetch QCM data", color = colors.textMuted, fontSize = 12.sp)
                        else -> for (table in qcmTables) {
                            for (rowJson in table.rows) {
                                val obj = rowJson.jsonObject
                                val qcmNo = obj["qcmNo"]?.jsonPrimitive?.contentOrNull ?: obj["QCM No"]?.jsonPrimitive?.contentOrNull
                                val action = obj["actionTaken"]?.jsonPrimitive?.contentOrNull ?: obj["Action Taken"]?.jsonPrimitive?.contentOrNull
                                val suggestions = obj["suggestions"]?.jsonPrimitive?.contentOrNull ?: obj["Suggestions"]?.jsonPrimitive?.contentOrNull
                                val facultyReply = obj["facultyReply"]?.jsonPrimitive?.contentOrNull ?: obj["Faculty Reply"]?.jsonPrimitive?.contentOrNull
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(12.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("QCM ${qcmNo ?: ""}", fontWeight = FontWeight.Bold, color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            action?.let { AmazeBadge(it, variant = BadgeVariant.INFO) }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        suggestions?.let { Text(it, color = colors.textPrimary, fontSize = 12.sp) }
                                        facultyReply?.let {
                                            Spacer(Modifier.height(4.dp))
                                            Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                                                Column {
                                                    Text("Faculty Reply", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 10.sp)
                                                    Text(it, color = colors.textSecondary, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.textMuted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun GradeHistoryTab(courseCode: String, allGrades: AllGradesRes?, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val gradeItems = remember(allGrades) {
        val items = mutableListOf<Pair<String, GradeItem?>>()
        allGrades?.grades?.forEach { (semId, semResult) ->
            semResult?.grades?.forEach { grade ->
                if (grade.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == courseCode) {
                    items.add(semId to grade)
                }
            }
        }
        items.sortedByDescending { it.first }
    }

    if (gradeItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.History, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("No grade history available", color = colors.textSecondary)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(gradeItems) { (semId, grade) ->
            val semName = AppState.semesterMap[semId] ?: semId
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(semName, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 12.sp)
                        grade?.let { g ->
                            Text("Total: ${g.grandTotal}", fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                    grade?.let { g ->
                        val gradeColor = when (g.grade.firstOrNull()?.uppercase()) {
                            "S" -> Color(0xFF10B981); "A" -> Color(0xFF3B82F6); "B" -> Color(0xFF8B5CF6)
                            "C" -> Color(0xFFF59E0B); "D" -> Color(0xFFF97316); "E" -> Color(0xFFEF4444)
                            "F" -> Color(0xFFDC2626); else -> colors.textPrimary
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(g.grade, fontWeight = FontWeight.Black, fontSize = 22.sp, color = gradeColor)
                            Text("Grade", fontSize = 10.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarksTab(
    theoryMarks: MarksCourseItem?,
    labMarks: MarksCourseItem?,
    isEmbedded: Boolean,
    semesterId: String,
    allGrades: AllGradesRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val assessments = remember(theoryMarks, labMarks) {
        val list = mutableListOf<Pair<String, List<AssessmentItem>>>()
        if (theoryMarks != null) list.add("Theory" to theoryMarks.assessments)
        if (labMarks != null) list.add("Lab" to labMarks.assessments)
        if (!isEmbedded && theoryMarks != null && labMarks == null) { list.clear(); list.add("Assessments" to theoryMarks.assessments) }
        list
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
        // Stats cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val allAsm = (theoryMarks?.assessments ?: emptyList()) + (labMarks?.assessments ?: emptyList())
                val totalWeighted = allAsm.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
                val totalWeightPct = allAsm.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
                val projected = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0
                val maxPossible = 100 - (totalWeightPct - totalWeighted).toInt()

                StatBox("Course Type", if (isEmbedded) "Embedded" else (theoryMarks?.courseType ?: "-"), colors, Modifier.weight(1f))
                StatBox("Projected", "$projected%", colors, Modifier.weight(1f))
                StatBox("Max", "$maxPossible%", colors, Modifier.weight(1f))
            }
        }

        assessments.forEach { (label, asms) ->
            item {
                Text(label, fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 13.sp)
            }
            if (asms.isEmpty()) {
                item {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No assessments", color = colors.textMuted, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(asms) { asm ->
                    val pct = if (asm.maxMark.toDoubleOrNull() != null && asm.maxMark.toDouble() > 0)
                        ((asm.scoredMark.toDoubleOrNull() ?: 0.0) / asm.maxMark.toDouble()) * 100 else 0.0
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(asm.title, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                                Text("Weightage: ${asm.weightagePercent}%", color = colors.textSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${asm.scoredMark} / ${asm.maxMark}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                                Text("${pct.toInt()}%", color = if (pct >= 70) Color(0xFF10B981) else Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Grade Insights
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grade Insights", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        AmazeBadge("BETA", variant = BadgeVariant.INFO)
                    }
                    Spacer(Modifier.height(12.dp))
                    val gradeBoundaries = listOf("S ≥ 90%", "A ≥ 80%", "B ≥ 70%", "C ≥ 60%", "D ≥ 50%", "E ≥ 40%")
                    gradeBoundaries.forEach { boundary ->
                        Text(boundary, fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    AmazeCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = colors.accent)
            Text(label, fontSize = 9.sp, color = colors.textMuted)
        }
    }
}

@Composable
private fun AttendanceTab(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    calendar: CalendarRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var scope by remember(isEmbedded) { mutableStateOf(if (isEmbedded) "theory" else "all") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isEmbedded) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("theory" to "Theory", "lab" to "Lab").forEach { (key, label) ->
                    val sel = scope == key
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) colors.accent else colors.surface)
                            .clickable { scope = key }.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = if (sel) colors.background else colors.textSecondary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
            }
        }

        val activeAtt = when (scope) {
            "theory" -> theoryAtt; "lab" -> labAtt; else -> mainAtt
        }

        if (activeAtt == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attendance data for this scope", color = colors.textMuted)
            }
            return
        }

        val historyList = remember(activeAtt.viewLinkRaw) {
            try {
                val raw = activeAtt.viewLinkRaw
                val list = mutableListOf<Pair<String, String>>()
                if (raw is JsonArray) {
                    raw.forEach { elem ->
                        val obj = elem.jsonObject
                        val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        list.add(date to status)
                    }
                } else if (raw is JsonObject) {
                    raw.forEach { (date, statusElem) ->
                        val stat = statusElem.jsonPrimitive.content
                        list.add(date to stat)
                    }
                }
                list
            } catch (_: Exception) { emptyList() }
        }

        var attendanceNotes by remember { mutableStateOf(SettingsManager.getAttendanceNotes()) }
        fun toggleNote(date: String) {
            val key = "${activeAtt.courseCode}|$date"
            val current = attendanceNotes[key] ?: false
            attendanceNotes = attendanceNotes.toMutableMap().apply { put(key, !current) }
            SettingsManager.saveAttendanceNote(key, !current)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
            // Stats
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("Attended", activeAtt.attendedClasses.toString(), Color(0xFF10B981), colors)
                    StatChip("Total", activeAtt.totalClasses.toString(), colors.accent, colors)
                    StatChip("Avg", activeAtt.attendancePercentage, Color(0xFF3B82F6), colors)
                }
            }

            // Predictor mode
            item {
                PredictorSection(activeAtt, calendar, colors)
            }

            // Attendance log
            if (historyList.isNotEmpty()) {
                item {
                    Text("Attendance Log (${historyList.size})", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                }
                items(historyList.sortedByDescending { it.first }) { (date, status) ->
                    val isPresent = status.lowercase() in listOf("present", "p")
                    val isOd = status.lowercase() in listOf("on duty", "od")
                    val hasNotes = attendanceNotes["${activeAtt.courseCode}|$date"] ?: false

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(date, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        if (isPresent) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Present", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isOd) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("On Duty", color = Color(0xFF3B82F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(colors.danger.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Absent", color = colors.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { toggleNote(date) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (hasNotes) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                        contentDescription = if (hasNotes) "Got notes" else "Mark as noted",
                                        tint = if (hasNotes) Color(0xFF10B981) else colors.warning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Missing class overview with notes progress
            val missingCount = historyList.count { (_, status) ->
                status.lowercase() !in listOf("present", "p")
            }
            val notedCount = historyList.count { (date, status) ->
                status.lowercase() !in listOf("present", "p") && attendanceNotes["${activeAtt.courseCode}|$date"] == true
            }
            item {
                if (missingCount > 0) {
                    AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = if (notedCount == missingCount) Color(0xFF10B981).copy(alpha = 0.08f) else colors.danger.copy(alpha = 0.08f)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (notedCount == missingCount) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                null,
                                tint = if (notedCount == missingCount) Color(0xFF10B981) else colors.danger,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$missingCount missed classes", fontWeight = FontWeight.Bold, color = if (notedCount == missingCount) Color(0xFF10B981) else colors.danger, fontSize = 13.sp)
                                Text(
                                    if (notedCount == missingCount) "All marked as noted!" else "$notedCount of $missingCount noted",
                                    color = colors.textSecondary, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictorSection(course: AttendanceItem, calendar: CalendarRes?, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var mode by remember { mutableStateOf("CAT1") }
    var skipDates by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val calendarMonths = calendar?.months ?: emptyList()
    val keyDates = remember(calendarMonths) {
        val map = mutableMapOf<String, Triple<Int, Int, Int>>()
        val monthIndex = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
        for (month in calendarMonths) {
            val m = month.month.lowercase().take(3); val monthNum = monthIndex[m] ?: continue
            val y = month.month.split(" ").lastOrNull()?.toIntOrNull() ?: continue
            for (day in month.days) {
                day.events.forEach { ev ->
                    val t = ev.text.lowercase()
                    when {
                        "cat i" in t || "cat 1" in t || "continuous assessment test - i" in t || "cat-i" in t -> map["CAT1"] = Triple(y, monthNum, day.date)
                        "cat ii" in t || "cat 2" in t || "continuous assessment test - ii" in t || "cat-ii" in t -> map["CAT2"] = Triple(y, monthNum, day.date)
                        "lid for laboratory" in t || ("last instructional day" in t && "laboratory" in t) -> map["LID_LAB"] = Triple(y, monthNum, day.date)
                        "lid for theory" in t || ("last instructional day" in t && "theory" in t) -> map["LID_TH"] = Triple(y, monthNum, day.date)
                    }
                }
            }
        }
        map
    }

    val isLab = course.courseType.contains("Lab", ignoreCase = true) || course.courseType.contains("Embedded", ignoreCase = true)

    val cutoff = when (mode) {
        "CAT1" -> keyDates["CAT1"]
        "CAT2" -> keyDates["CAT2"]
        "LID" -> if (isLab) keyDates["LID_LAB"] else keyDates["LID_TH"]
        else -> null
    }

    val courseDays = remember(course) {
        val slotName = course.slotName.uppercase()
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT").filter { slotName.contains(it) }
    }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val allWorkingDays = remember(calendarMonths) {
        val results = mutableListOf<Triple<Int, Int, Int>>()
        val monthIndex = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
        for (month in calendarMonths) {
            val m = month.month.lowercase().take(3); val monthNum = monthIndex[m] ?: continue
            val y = month.month.split(" ").lastOrNull()?.toIntOrNull() ?: continue
            for (day in month.days) {
                val isWorking = day.events.any { it.type.lowercase() == "instructional day" || it.type.lowercase().contains("working") }
                val isHoliday = day.events.any { it.type.lowercase().contains("holiday") }
                if (isWorking && !isHoliday) results.add(Triple(y, monthNum, day.date))
            }
        }
        results
    }

    val futureClassDates = remember(allWorkingDays, courseDays, cutoff) {
        allWorkingDays.filter { (y, m, d) ->
            if (cutoff != null) {
                val dv = y * 10000 + m * 100 + d
                val cv = cutoff.first * 10000 + cutoff.second * 100 + cutoff.third
                if (dv > cv) return@filter false
            }
            val dt = try { LocalDate(y, m, d) } catch (_: Exception) { return@filter false }
            val abbr = when (dt.dayOfWeek) {
                DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
                DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
                else -> ""
            }
            abbr in courseDays
        }
    }

    val multiplier = if (isLab) 2 else 1
    val futureCount = futureClassDates.size * multiplier
    val skipCount = skipDates.size * multiplier

    val predictedAttended = course.attendedClasses + (futureCount - skipCount)
    val predictedTotal = course.totalClasses + futureCount
    val predictedPct = if (predictedTotal > 0) predictedAttended.toDouble() / predictedTotal * 100 else 0.0

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CAT1", "CAT2", "LID").forEach { m ->
                    val sel = mode == m
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) colors.accent else colors.surface).clickable { mode = m }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(m, color = if (sel) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Future classes: ${futureClassDates.size}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            futureClassDates.take(10).forEach { (y, m, d) ->
                val key = y * 10000 + m * 100 + d
                val skipped = key in skipDates
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (skipped) colors.danger.copy(alpha = 0.1f) else colors.surface)
                        .clickable { skipDates = if (key in skipDates) skipDates - key else skipDates + key }.padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$m/$d", fontSize = 12.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (skipped) Text("SKIP", color = colors.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        else Text("ATTEND", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Projected", fontSize = 11.sp, color = colors.textMuted)
                    Text("$predictedAttended / $predictedTotal", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                }
                Text("${predictedPct.toInt()}%", fontWeight = FontWeight.Black, fontSize = 24.sp, color = when { predictedPct >= 85 -> Color(0xFF10B981); predictedPct >= 75 -> Color(0xFFF59E0B); else -> Color(0xFFEF4444) })
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 10.sp, color = colors.textSecondary)
    }
}

@Composable
private fun NotesTab(courseCode: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var notes by remember { mutableStateOf("") }
    var homeworkReminders by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notes for Missed Classes", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("What was covered?") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                )
            }
        }

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Homework Reminders", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = homeworkReminders, onValueChange = { homeworkReminders = it },
                    label = { Text("Upcoming assignments, deadlines...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                )
            }
        }

        AmazeButton("Save Notes", onClick = { /* persist */ }, icon = Icons.Rounded.Save, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun QBankTab(courseCode: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var questions by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(courseCode) {
        loading = true
        try {
            val res = AmazeClient.getQBankQuestions(courseCode)
            if (res.success) questions = res.data else error = res.message
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            error != null -> Text(error!!, color = colors.danger, modifier = Modifier.align(Alignment.Center))
            questions.isEmpty() -> Text("No question bank data for $courseCode", color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(questions) { q ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(q.question_text, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                            q.topic_name?.let { Text("Topic: $it", color = colors.textSecondary, fontSize = 12.sp) }
                            Text("Type: ${q.question_type}", color = colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun findCourseGroup(
    courseCode: String,
    semesterId: String,
    allSemesterMarks: Map<String, MarksRes>,
    allSemesterAttendance: Map<String, AttendanceRes?>,
    marksRes: MarksRes?,
    attendanceRes: AttendanceRes?,
    timetable: TimetableRes?
): CourseGroup? {
    val cleanCode = courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()

    // Try current semester first
    val currentMarksCourses = marksRes?.marks ?: allSemesterMarks[semesterId]?.marks ?: emptyList()
    val currentAttList = attendanceRes?.attendance ?: allSemesterAttendance[semesterId]?.attendance ?: emptyList()

    val theoryM = currentMarksCourses.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
    val labM = currentMarksCourses.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
    val theoryA = currentAttList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
    val labA = currentAttList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }

    if (theoryM != null || labM != null || theoryA != null || labA != null) {
        val semName = AppState.semesterMap[semesterId] ?: semesterId
        return CourseGroup(cleanCode, (theoryM?.courseTitle ?: labM?.courseTitle ?: theoryA?.courseTitle ?: labA?.courseTitle ?: cleanCode), semesterId, semName, theoryM, labM, theoryA, labA)
    }

    // Search all semesters from marks/attendance data
    for ((semId, marks) in allSemesterMarks) {
        val attList = allSemesterAttendance[semId]?.attendance ?: emptyList()
        val tM = marks.marks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
        val lM = marks.marks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
        val tA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
        val lA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
        if (tM != null || lM != null || tA != null || lA != null) {
            val semName = AppState.semesterMap[semId] ?: semId
            return CourseGroup(cleanCode, (tM?.courseTitle ?: lM?.courseTitle ?: tA?.courseTitle ?: lA?.courseTitle ?: cleanCode), semId, semName, tM, lM, tA, lA)
        }
    }

    // Fallback: search grades-only semesters (courses with no marks/attendance data)
    val allGradesRes = AppState.allGrades.value
    val gradesMap = allGradesRes?.grades
    if (gradesMap != null) {
        for ((semId, semResult) in gradesMap) {
            if (semId == "curriculum" || semId == "effectiveGrades") continue
            semResult?.grades?.forEach { grade ->
                val gCleanCode = grade.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
                if (gCleanCode == cleanCode) {
                    val semName = AppState.semesterMap[semId] ?: semId
                    return CourseGroup(
                        courseCode = cleanCode,
                        courseTitle = grade.courseTitle,
                        semesterSubId = semId,
                        semesterName = semName,
                        theory = MarksCourseItem(courseCode = grade.courseCode, courseTitle = grade.courseTitle, courseType = grade.courseType)
                    )
                }
            }
        }
    }

    return null
}
