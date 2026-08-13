package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.FeedbackSemester
import com.amazecc.app.shared.model.FeedbackTableRow
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.components.bouncySpring
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class SemesterFeedbackState(
    val semester: FeedbackSemester,
    val rows: List<FeedbackTableRow>,
    val courseCount: Int = 0,
    val isExpanded: Boolean = true
)

@Composable
fun FeedbackStatusScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var semestersData by remember { mutableStateOf<List<SemesterFeedbackState>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSemValue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        refreshSession()
        loadFeedback { data, err ->
            semestersData = data
            error = err
            loading = false
            if (data.isNotEmpty()) {
                selectedSemValue = data.firstOrNull { it.semester.selected }?.semester?.value ?: data.firstOrNull()?.semester?.value
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Feedback Status",
            description = "Course feedback status & history",
            showBackButton = true,
            showSyncButton = true,
            enabledScreens = setOf(Screen.FEEDBACK_STATUS),
            onRefresh = {
                scope.launch {
                    loading = true
                    error = null
                    refreshSession()
                    loadFeedback { data, err ->
                        semestersData = data
                        error = err
                        loading = false
                    }
                }
            }
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    Text(
                        "Fetching feedback status...",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    )
                }
            }
            return
        }

        if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                AmazeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.danger.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                        Text(
                            "Unable to Load Feedback",
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            error ?: "Unknown error occurred while fetching feedback data.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            return
        }

        if (semestersData.isEmpty() || semestersData.all { it.rows.isEmpty() }) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AssignmentTurnedIn, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    Text(
                        "No Feedback Data Available",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Feedback forms for the active semester may not be open yet.",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }
            return
        }

        val displayList = remember(semestersData, selectedSemValue) {
            if (selectedSemValue == null || semestersData.size <= 1) semestersData
            else semestersData.filter { it.semester.value == selectedSemValue }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HeaderSpacer() }

            // Semester Selector Filter Row (Exam Schedule Style)
            if (semestersData.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        semestersData.forEach { semState ->
                            val isActive = semState.semester.value == selectedSemValue
                            val isComplete = semState.rows.isNotEmpty() && semState.rows.all { row ->
                                (row.midSemester?.lowercase()?.contains("given") == true) &&
                                (row.teeSemester?.lowercase()?.contains("given") == true)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .background(if (isActive) colors.accent else colors.surface)
                                    .border(if (isActive) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small))
                                    .clickable {
                                        selectedSemValue = semState.semester.value
                                        semestersData = semestersData.map {
                                            if (it.semester.value == semState.semester.value) it.copy(isExpanded = true) else it
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        semState.semester.text,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isActive) Color.White else colors.textSecondary
                                        )
                                    )
                                    if (isComplete) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            null,
                                            tint = if (isActive) Color.White else colors.success,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hero Card (Exam Schedule Style)
            item {
                FeedbackHeroCard(semestersData = semestersData, colors = colors)
            }

            items(displayList, key = { it.semester.value }) { semData ->
                FeedbackSemesterGroupCard(
                    state = semData,
                    onToggleExpand = {
                        semestersData = semestersData.map {
                            if (it.semester.value == semData.semester.value) it.copy(isExpanded = !it.isExpanded) else it
                        }
                    },
                    colors = colors
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private suspend fun refreshSession() {
    val creds = SettingsManager.getCredentials() ?: return
    try {
        val loginRes = AmazeClient.login(creds.first, creds.second)
        if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
            SessionManager.saveSession(
                cookies = loginRes.cookies,
                csrf = loginRes.csrf,
                authorizedID = loginRes.authorizedID,
                clubToken = loginRes.clubToken
            )
            SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
            SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
            loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
        }
    } catch (_: Exception) { }
}

private suspend fun loadFeedback(onResult: (List<SemesterFeedbackState>, String?) -> Unit) {
    try {
        // ── Prefer locally-cached data for the semester list ──
        val cachedGrades = AppState.allGrades.value
        val localSemIds = cachedGrades?.grades?.keys
            ?.filter { it != "curriculum" && it != "effectiveGrades" }
            ?.toList() ?: emptyList()

        val selectedSem = AppState.selectedSemester.value
        val sems: List<FeedbackSemester>
        val initialSelectedIdx: Int
        if (localSemIds.isNotEmpty()) {
            sems = localSemIds.map { semId ->
                val name = AppState.semesterMap.value[semId] ?: AppState.deriveSemesterName(semId)
                FeedbackSemester(text = name, value = semId, selected = semId == selectedSem)
            }
            initialSelectedIdx = sems.indexOfFirst { it.selected }
        } else {
            val initialRes = AmazeClient.getFeedbackStatus()
            sems = initialRes.semesters ?: emptyList()
            initialSelectedIdx = sems.indexOfFirst { it.selected }
        }

        val stateList = coroutineScope {
            sems.mapIndexed { idx, sem ->
                async {
                    // Feedback is per semester, not per course: one status set per semester
                    // covering Course (Mid/End) and Curriculum (Mid/End) submissions.
                    val localGradeList = cachedGrades?.grades?.get(sem.value)?.grades
                    var courseCount = localGradeList?.size ?: 0

                    var midCourseGiven = false
                    var teeCourseGiven = false
                    var midCurrGiven = false
                    var teeCurrGiven = false

                    val statusOf = { given: Boolean -> if (given) "Given" else "Not Given" }

                    if (localGradeList != null) {
                        // Local course list available — fetch only the per-semester statuses
                        try {
                            val gradesRes = AmazeClient.getGrades(sem.value)
                            if (gradesRes.success && gradesRes.feedback != null) {
                                midCourseGiven = gradesRes.feedback.MidSem?.Course == true
                                teeCourseGiven = gradesRes.feedback.EndSem?.Course == true
                                midCurrGiven = gradesRes.feedback.MidSem?.Curriculum == true
                                teeCurrGiven = gradesRes.feedback.EndSem?.Curriculum == true
                            }
                        } catch (_: Exception) { }
                    } else {
                        // No local data — try the network for the course list and statuses
                        try {
                            val gradesRes = AmazeClient.getGrades(sem.value)
                            if (gradesRes.success && !gradesRes.effectiveGrades.isNullOrEmpty()) {
                                courseCount = gradesRes.effectiveGrades.filter {
                                    !it.basketTitle.isNullOrBlank() && it.basketTitle != "Course Title"
                                }.size
                                midCourseGiven = gradesRes.feedback?.MidSem?.Course == true
                                teeCourseGiven = gradesRes.feedback?.EndSem?.Course == true
                                midCurrGiven = gradesRes.feedback?.MidSem?.Curriculum == true
                                teeCurrGiven = gradesRes.feedback?.EndSem?.Curriculum == true
                            }
                        } catch (_: Exception) { }
                    }

                    val finalRows = if (courseCount > 0 ||
                        midCourseGiven || teeCourseGiven || midCurrGiven || teeCurrGiven
                    ) {
                        listOf(
                            FeedbackTableRow(
                                feedbackType = "Course Feedback",
                                midSemester = statusOf(midCourseGiven),
                                teeSemester = statusOf(teeCourseGiven)
                            ),
                            FeedbackTableRow(
                                feedbackType = "Curriculum Feedback",
                                midSemester = statusOf(midCurrGiven),
                                teeSemester = statusOf(teeCurrGiven)
                            )
                        )
                    } else {
                        emptyList()
                    }

                    SemesterFeedbackState(
                        sem,
                        finalRows,
                        courseCount = courseCount,
                        isExpanded = (idx == initialSelectedIdx || idx == 0)
                    )
                }
            }.awaitAll()
        }

        onResult(stateList, null)
    } catch (e: Exception) {
        onResult(emptyList(), e.message)
    }
}

// ── Exam Schedule Inspired Hero Banner Card ──
@Composable
private fun FeedbackHeroCard(semestersData: List<SemesterFeedbackState>, colors: AmazeColors) {
    val totalSemesters = semestersData.size
    val midGivenCount = semestersData.count { state ->
        state.rows.firstOrNull { it.feedbackType == "Course Feedback" }?.midSemester?.lowercase()?.contains("given") == true
    }
    val teeGivenCount = semestersData.count { state ->
        state.rows.firstOrNull { it.feedbackType == "Course Feedback" }?.teeSemester?.lowercase()?.contains("given") == true
    }
    val totalFeedbacksPossible = totalSemesters * 2
    val totalGiven = midGivenCount + teeGivenCount

    val completionFraction by animateFloatAsState(
        targetValue = if (totalFeedbacksPossible > 0) totalGiven / totalFeedbacksPossible.toFloat() else 0f,
        animationSpec = tween(800)
    )

    val heroGradient = remember(colors) {
        Brush.linearGradient(
            colors = listOf(colors.accent, colors.accent.copy(alpha = 0.65f))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(heroGradient)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AssignmentTurnedIn, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Course Feedback Status",
                        color = Color.White,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                    )
                    Text(
                        "VIT Academic Evaluation System",
                        color = Color.White.copy(alpha = 0.8f),
                        style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.micro)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (totalGiven == totalFeedbacksPossible && totalFeedbacksPossible > 0) "ALL GIVEN"
                        else "${(completionFraction * 100).toInt()}% DONE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.micro
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HeroStatItem("Semesters", "$totalSemesters")
                HeroStatItem("Mid Sem Done", "$midGivenCount/$totalSemesters")
                HeroStatItem("TEE Done", "$teeGivenCount/$totalSemesters")
            }

            LinearProgressIndicator(
                progress = { completionFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun HeroStatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

// ── Exam Schedule Inspired Semester Group Card ──
@Composable
private fun FeedbackSemesterGroupCard(
    state: SemesterFeedbackState,
    onToggleExpand: () -> Unit,
    colors: AmazeColors
) {
    val totalCount = state.rows.size
    val midGiven = state.rows.count { it.midSemester?.lowercase()?.contains("given") == true }
    val teeGiven = state.rows.count { it.teeSemester?.lowercase()?.contains("given") == true }
    val isComplete = midGiven == totalCount && teeGiven == totalCount && totalCount > 0

    val courseRow = state.rows.firstOrNull { it.feedbackType == "Course Feedback" }
    val subtitle = buildString {
        append("${state.courseCount} course${if (state.courseCount != 1) "s" else ""}")
        val mid = courseRow?.midSemester
        val tee = courseRow?.teeSemester
        if (mid != null && tee != null) append(" • Mid Sem: $mid, TEE: $tee")
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            // Header Row (Exam Group Header Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.semester.text.uppercase(),
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(if (isComplete) colors.success.copy(alpha = 0.15f) else colors.danger.copy(alpha = 0.15f))
                        .border(1.dp, if (isComplete) colors.success.copy(alpha = 0.3f) else colors.danger.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isComplete) "COMPLETED" else "INCOMPLETE",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isComplete) colors.success else colors.danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro
                        )
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (state.isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = state.isExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))

                    if (state.rows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No courses found for this semester",
                                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                            )
                        }
                    } else {
                        state.rows.forEachIndexed { index, row ->
                            if (index > 0) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.4f)))
                            }
                            FeedbackCourseRow(row = row, colors = colors)
                        }
                    }
                }
            }
        }
    }
}

// ── Exam Schedule Row Inspired Feedback Course Row ──
@Composable
private fun FeedbackCourseRow(row: FeedbackTableRow, colors: AmazeColors) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = bouncySpring()
    )

    val midGiven = row.midSemester?.lowercase()?.contains("given") == true
    val teeGiven = row.teeSemester?.lowercase()?.contains("given") == true
    val isFullyGiven = midGiven && teeGiven
    val anyNotGiven = !midGiven || !teeGiven

    val iconTint = if (isFullyGiven) colors.success else colors.danger

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { expanded = !expanded }
            .background(
                when {
                    anyNotGiven -> colors.danger.copy(alpha = 0.07f)
                    expanded -> colors.accentSurface.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (anyNotGiven) Modifier
                    .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.small))
                    .padding(4.dp)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (anyNotGiven) colors.danger.copy(alpha = 0.16f) else iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (anyNotGiven) Icons.Rounded.WarningAmber else Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(if (anyNotGiven) 22.dp else 20.dp)
                )
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.feedbackType ?: "Course Feedback",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPillBadge(text = "Mid Sem", isGiven = midGiven, colors = colors)
                    StatusPillBadge(text = "TEE", isGiven = teeGiven, colors = colors)
                    if (anyNotGiven) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.danger)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "NOT GIVEN",
                                style = AmazeTheme.typography.caption.copy(color = Color.White, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Mid Sem Feedback", row.midSemester ?: "Not Submitted", isGiven = midGiven, colors = colors, modifier = Modifier.weight(1f))
                    MetricTile("TEE Feedback", row.teeSemester ?: "Not Submitted", isGiven = teeGiven, colors = colors, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusPillBadge(text: String, isGiven: Boolean, colors: AmazeColors) {
    val badgeBg = if (isGiven) colors.success.copy(alpha = 0.14f) else colors.danger.copy(alpha = 0.14f)
    val badgeColor = if (isGiven) colors.success else colors.danger

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
            .background(badgeBg)
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGiven) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = AmazeTheme.typography.caption.copy(color = badgeColor, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    isGiven: Boolean,
    colors: AmazeColors,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isGiven) colors.success.copy(alpha = 0.3f) else colors.danger.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.small))
            .padding(10.dp)
    ) {
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isGiven) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    null,
                    tint = if (isGiven) colors.success else colors.danger,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    value,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isGiven) colors.success else colors.danger,
                        fontSize = AmazeTheme.fontSize.sm
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
