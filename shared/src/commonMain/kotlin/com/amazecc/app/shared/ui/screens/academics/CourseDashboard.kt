package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.CourseGroup
import com.amazecc.app.shared.domain.CourseMarks
import com.amazecc.app.shared.domain.buildCourseGroups
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge
import com.amazecc.app.shared.utils.toFixed

@Composable
fun CourseDashboardScreen(onBack: () -> Unit) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    AmazeM3ETheme(isDark = isDark) {
        val colors = AmazeTheme.colors
        val semesterMap by AppState.semesterMap.collectAsState()
        val domain by AppState.domain.collectAsState()

        AppBackHandler { onBack() }

        var selectedSemester by remember { mutableStateOf("All") }

    val semesterGroups = remember(domain, semesterMap, selectedSemester) {
        buildCourseGroups(domain, semesterMap, selectedSemester)
    }

    val filteredGroups = remember(semesterGroups, selectedSemester) {
        semesterGroups.filter { group ->
            if (group.courseCode.isBlank()) return@filter false
            val semMatch = selectedSemester == "All" || group.semesterSubId == selectedSemester
            semMatch
        }.groupBy { it.semesterSubId }
    }

    val semesterIds = listOf("All") + semesterGroups.map { it.semesterSubId }.distinct()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Course Dashboard",
            description = "All courses across semesters",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = AppState::refreshAllAcademic,
            onBackOverride = onBack,
            enabledScreens = setOf(Screen.ACADEMICS, Screen.COURSE_DASHBOARD)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
        ) {
            item {
                HeaderSpacer()
            }

            // Semester filter chips item
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    semesterIds.forEach { semId ->
                        val isSelected = selectedSemester == semId
                        val label = if (semId == "All") "All Semesters" else semesterMap[semId]?.let {
                            val parts = it.split(" ")
                            if (parts.size >= 2) "${parts[0]} ${parts[1].take(4)}" else it.take(20)
                        } ?: semId.take(10)

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = bouncySpring()
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else colors.surface)
                                .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.6f), CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedSemester = semId }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) colors.onAccent else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.sm,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
            }

            item {
                val isLoading by AppState.isLoading.collectAsState()
                val pastSynced by AppState.pastSemestersSynced.collectAsState()
                if (!isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(if (pastSynced) colors.surface else colors.accent.copy(alpha = 0.08f))
                            .clickable { AppState.refreshPastSemesters() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (pastSynced) Icons.Rounded.CheckCircle else Icons.Rounded.HistoryToggleOff,
                                null, tint = if (pastSynced) colors.success else colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(
                                if (pastSynced) "Past semesters loaded. Tap to refresh data"
                                else "Load past semester attendance & marks",
                                color = if (pastSynced) colors.textSecondary else colors.accent,
                                fontSize = AmazeTheme.fontSize.base, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Rounded.Refresh, null, tint = if (pastSynced) colors.textMuted else colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                }
            }

            if (filteredGroups.keys.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("No courses found", color = colors.textMuted, fontSize = AmazeTheme.fontSize.md, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                filteredGroups.forEach { (semId, courses) ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = courses.firstOrNull()?.semesterName ?: semesterMap[semId] ?: semId,
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                    .background(colors.accent.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${courses.size} courses", fontSize = AmazeTheme.fontSize.xs, color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    itemsIndexed(courses, key = { index, course -> "${course.courseCode}_$index" }) { _, course ->
                        CourseDetailCard(
                            course = course,
                            onClick = { AppState.openCourseDetail(course.courseCode, course.semesterSubId) }
                        )
                    }
                }
            }
        }
    }
}
}

data class CleanedTitle(
    val cleanTitle: String,
    val typeBadgeOverride: String? = null
)

fun cleanCourseTitle(rawTitle: String): CleanedTitle {
    var title = rawTitle.trim()
    var badge: String? = null

    if (title.contains("(Project)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Project\\s*\\)"), "").trim()
        badge = "PJ"
    } else if (title.contains("(Online Course)", ignoreCase = true) || title.contains("(Online)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Online\\s*(Course)?\\s*\\)"), "").trim()
        badge = "OC"
    } else if (title.contains("(Theory)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Theory\\s*\\)"), "").trim()
        badge = "TH"
    } else if (title.contains("(Lab)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Lab\\s*\\)"), "").trim()
        badge = "LO"
    }

    title = title.replace(Regex("\\s+"), " ").trim()
    return CleanedTitle(cleanTitle = title, typeBadgeOverride = badge)
}

@Composable
private fun CourseDetailCard(course: CourseGroup, onClick: () -> Unit) {
    val colors = AmazeTheme.colors

    val model = course.cardModel()
    val isEmbedded = model.isEmbedded
    val displayTitle = cleanCourseTitle(course.courseTitle).cleanTitle

    // The two palettes the card used to spell out separately, now driven by one band set.
    val headlineColor = when (model.headlineTone) {
        CardTone.GOOD -> colors.success
        CardTone.MID -> colors.warning
        CardTone.LOW -> colors.danger
    }
    val label = model.badge
    val badgeColor = when (label) {
        "PJ" -> Color(0xFF8B5CF6)
        "OC" -> Color(0xFF06B6D4)
        "LO" -> colors.success
        "EMB" -> colors.chart3
        else -> colors.accent
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.large))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = badgeColor,
                                fontWeight = FontWeight.Black,
                                fontSize = AmazeTheme.fontSize.xs
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = course.courseCode,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    )
                }

                if (model.headlineVisible) {
                    val c = headlineColor
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.copy(alpha = 0.12f))
                            .border(1.dp, c.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = model.headlinePct.toFixed(2, "%"),
                            style = AmazeTheme.typography.caption.copy(color = c, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xs)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayTitle,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }

            val parsedFac = model.faculty

            if (!isEmbedded && parsedFac != null && parsedFac.name.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parsedFac.name,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!parsedFac.school.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.accent.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = parsedFac.school,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }

            if (isEmbedded) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.4f)))
                Spacer(Modifier.height(8.dp))
                embeddedRow(model.halves[0], colors.chart2, colors)
                Spacer(Modifier.height(6.dp))
                embeddedRow(model.halves[1], colors.chart4, colors)
            }
        }
    }
}

@Composable
private fun embeddedRow(
    row: CourseHalfRow,
    accent: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val c = when (row.tone) {
        CardTone.GOOD -> colors.chart1
        CardTone.MID -> colors.chart3
        CardTone.LOW -> colors.chart5
    }
    val parsedFac = row.faculty

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(row.half.badge, style = AmazeTheme.typography.smallLabel.copy(color = accent, fontWeight = FontWeight.Black))
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            )
            if (parsedFac != null && parsedFac.name.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parsedFac.name, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                    if (!parsedFac.school.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.accent.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = parsedFac.school,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        if (row.attendancePct > 0.0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs)).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(row.attendancePct.toFixed(2, "%"), style = AmazeTheme.typography.caption.copy(color = c, fontWeight = FontWeight.Black))
            }
        }
    }
}

