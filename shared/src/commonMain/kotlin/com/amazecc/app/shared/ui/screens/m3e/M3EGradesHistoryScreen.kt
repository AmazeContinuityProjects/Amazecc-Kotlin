package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.EmbeddedCourseSemantics
import com.amazecc.app.shared.domain.EmbeddedEntry
import com.amazecc.app.shared.domain.EmbeddedHalf
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.GradeComponent
import com.amazecc.app.shared.domain.overhaul.M3EGradeBreakdownRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseDetail
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeEmbeddedHalf
import com.amazecc.app.shared.domain.overhaul.M3EGradeInsightSlide
import com.amazecc.app.shared.domain.overhaul.M3EGradePointTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeScaleRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeScreen
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermOption
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeRecord
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EInsightCarouselCard
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3EStatSubtitle
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.round

/**
 * Material 3 Expressive grade history — the three-page port of the web app's
 * `MarksHistoryTab`. One [M3EPageShell] hosts all three pages; only the body changes.
 *
 * 100% stateless: accepts [M3EGradesHistoryState] and emits [M3EGradesHistoryAction].
 * All navigation and data fetching live in the bridge host.
 */
@Composable
fun M3EGradesHistoryScreen(
    state: M3EGradesHistoryState,
    onAction: (M3EGradesHistoryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSubpage = state.screen != M3EGradeScreen.OVERVIEW
    val title = when (state.screen) {
        M3EGradeScreen.INSIGHTS -> "Grade Insights"
        M3EGradeScreen.COURSE -> state.courseDetail?.courseCode ?: "Course"
        M3EGradeScreen.OVERVIEW -> "Grade History"
    }
    val subtitle: String? = when (state.screen) {
        M3EGradeScreen.INSIGHTS -> "How your grades are spread, across every term."
        M3EGradeScreen.COURSE -> state.courseDetail?.courseTitle
        M3EGradeScreen.OVERVIEW -> "Every term's results in one place."
    }

    M3EPageShell(
        modifier = modifier,
        eyebrow = "Academics · Grades",
        title = title,
        subtitle = subtitle,
        onBack = if (isSubpage) ({ onAction(M3EGradesHistoryAction.BackToOverview) }) else null,
        actions = @Composable {
            if (state.screen == M3EGradeScreen.OVERVIEW) {
                IconButton(onClick = { onAction(M3EGradesHistoryAction.Refresh) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Sync grades",
                        tint = M3ETheme.colors.onSurface
                    )
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when (state.screen) {
                M3EGradeScreen.OVERVIEW -> gradeOverviewContent(state, onAction)
                M3EGradeScreen.INSIGHTS -> gradeInsightsContent(state)
                M3EGradeScreen.COURSE -> gradeCourseContent(state)
            }
        }
    }
}

// ── Overview ─────────────────────────────────────────────────────────────────

private fun LazyListScope.gradeOverviewContent(
    state: M3EGradesHistoryState,
    onAction: (M3EGradesHistoryAction) -> Unit
) {
    item(key = "grade_hero") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EGradeStatTile(
                label = "Cumulative",
                value = state.cumulativeValue,
                badge = state.cumulativeBadge,
                sub = M3EStatSubtitle.fromData(state.cumulativeSubtitle.ifBlank { "Not available" }),
                tone = M3EGradeTone.BLUE,
                modifier = Modifier.weight(1f)
            )
            val slides = state.insightSlides.map { slide -> slide.toStatItem() }
            M3EInsightCarouselCard(
                items = slides,
                modifier = Modifier.weight(1f).height(152.dp)
            )
        }
    }

    item(key = "grade_hero_note") {
        Text(
            text = "GPA is the figure VTOP published for that term. Cumulative is credit-weighted across every term so far, so it moves less than a single term's average does.",
            style = MaterialTheme.typography.bodySmall,
            color = M3ETheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }

    item(key = "courses_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.MenuBook,
            title = "Courses",
            count = state.courseRows.size
        )
    }
    if (state.termRows.size > 1) {
        item(key = "term_chips") {
            val ids = state.termOptions.map { it.termId }
            val selected = state.selectedTermId?.takeIf { ids.contains(it) } ?: ids.lastOrNull()
            if (selected != null) {
                M3EConnectedButtonGroup(
                    items = ids,
                    selectedItem = selected,
                    onItemSelected = { onAction(M3EGradesHistoryAction.SelectTerm(it)) },
                    labelProvider = { id -> state.termOptions.firstOrNull { it.termId == id }?.shortLabel ?: id.take(12) }
                )
            }
        }
    }
    if (state.courseRows.isEmpty()) {
        item(key = "courses_empty") {
            M3EGradeEmptyCard(
                icon = Icons.Rounded.MenuBook,
                title = "No courses for this term",
                description = "VTOP has not published a course list for this semester yet."
            )
        }
    } else {
        items(items = state.courseRows, key = { "course_${it.courseCode}" }) { course ->
            M3EGradeCourseRowItem(
                row = course,
                onClick = { onAction(M3EGradesHistoryAction.OpenCourse(course.courseCode)) }
            )
        }
    }

    item(key = "across_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.TrendingUp,
            title = "Across terms",
            count = state.termRows.size
        )
    }
    if (state.termRows.isEmpty()) {
        item(key = "terms_empty") {
            M3EGradeEmptyCard(
                icon = Icons.Rounded.TrendingUp,
                title = "No grades yet",
                description = "Pull your grades from VTOP and every term's GPA will collect here."
            )
        }
    } else {
        items(items = state.termRows, key = { "term_${it.termId}" }) { term ->
            M3EGradeTermRowItem(
                term = term,
                isActive = term.termId == state.selectedTermId,
                onClick = { onAction(M3EGradesHistoryAction.SelectTerm(term.termId)) }
            )
        }
    }

    item(key = "insights_nav") {
        M3ESurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerLevel = SurfaceContainerLevel.STANDARD,
            onClick = { onAction(M3EGradesHistoryAction.OpenInsights) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(M3ETheme.colors.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        tint = M3ETheme.colors.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Grade insights",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = M3ETheme.colors.onSurface
                    )
                    Text(
                        text = "Grade distribution, theory against lab, and score by term",
                        fontSize = 11.sp,
                        color = M3ETheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = M3ETheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Insights ─────────────────────────────────────────────────────────────────

private fun LazyListScope.gradeInsightsContent(state: M3EGradesHistoryState) {
    item(key = "insights_stats") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EGradeStatTile(
                label = "Terms recorded",
                value = state.termRows.count { it.courseCount > 0 }.toString(),
                badge = "All time",
                sub = if (state.termRows.size > 1) {
                    M3EStatSubtitle.fromMetric(state.termRows.size.toString(), "terms published")
                } else {
                    M3EStatSubtitle.fromData("so far")
                },
                tone = M3EGradeTone.BLUE,
                modifier = Modifier.weight(1f)
            )
            val bestTerm = state.termRows.filter { it.gpa > 0 }.maxByOrNull { it.gpa }
            M3EGradeStatTile(
                label = "Best term",
                value = bestTerm?.gpa?.toFixed(2) ?: "—",
                badge = bestTerm?.label,
                sub = M3EStatSubtitle.fromData(
                    if (bestTerm != null) "highest GPA on record" else "no GPA published yet"
                ),
                tone = M3EGradeTone.GREEN,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item(key = "distribution_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.WorkspacePremium,
            title = "Grade distribution",
            count = state.distributionRows.sumOf { it.count }
        )
    }
    if (state.distributionRows.isEmpty()) {
        item(key = "distribution_empty") {
            M3EGradeEmptyCard(icon = Icons.Rounded.WorkspacePremium, title = "No letter grades published yet")
        }
    } else {
        items(items = state.distributionRows, key = { "dist_${it.grade}" }) { row ->
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    M3EGradeToneBadge(text = row.grade, tone = row.tone, modifier = Modifier.width(40.dp))
                    val barColor = gradeToneAccent(row.tone)
                    M3EWavyLinearProgress(
                        progress = (row.share.toFloat() / 100f).coerceIn(0f, 1f),
                        label = "Grade distribution",
                        activeColor = barColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = row.count.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = M3ETheme.colors.onSurface,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = "${row.share.toFixed(0)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = M3ETheme.colors.onSurfaceVariant,
                        modifier = Modifier.width(38.dp)
                    )
                }
            }
        }
    }

    item(key = "types_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.Science,
            title = "Theory against lab",
            count = state.typeAverages.size
        )
    }
    if (state.typeAverages.isEmpty()) {
        item(key = "types_empty") {
            M3EGradeEmptyCard(icon = Icons.Rounded.Science, title = "No scored courses to compare")
        }
    } else {
        items(items = state.typeAverages, key = { "type_${it.type}" }) { avg ->
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = avg.type,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = M3ETheme.colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${avg.count} course${if (avg.count == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = M3ETheme.colors.onSurfaceVariant
                        )
                    }
                    val hue = when {
                        avg.avg >= 80.0 -> M3EGradeTone.GREEN
                        avg.avg >= 65.0 -> M3EGradeTone.AMBER
                        else -> M3EGradeTone.RED
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        M3EWavyLinearProgress(
                            progress = (avg.avg.toFloat() / 100f).coerceIn(0f, 1f),
                            label = "Average grade",
                            activeColor = gradeToneAccent(hue),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${avg.avg.toFixed(1)}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = M3ETheme.colors.onSurface
                        )
                    }
                }
            }
        }
    }

    item(key = "term_scores_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.DateRange,
            title = "Score by term",
            count = state.termScores.size
        )
    }
    items(items = state.termScores, key = { "score_${it.termId}" }) { score ->
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = score.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = M3ETheme.colors.onSurface,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val hue = when {
                    score.avgScore >= 80.0 -> M3EGradeTone.GREEN
                    score.avgScore >= 65.0 -> M3EGradeTone.AMBER
                    else -> M3EGradeTone.RED
                }
                M3EWavyLinearProgress(
                    progress = (score.avgScore.toFloat() / 100f).coerceIn(0f, 1f),
                    label = "Assessment average",
                    activeColor = gradeToneAccent(hue),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (score.scoredCount > 0) "${score.avgScore.toFixed(1)}%" else "—",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = M3ETheme.colors.onSurface
                )
            }
        }
    }
}

// ── Course subpage ───────────────────────────────────────────────────────────

private fun LazyListScope.gradeCourseContent(state: M3EGradesHistoryState) {
    val course = state.courseDetail
    if (course == null) {
        item(key = "course_missing") {
            M3EGradeEmptyCard(
                icon = Icons.Rounded.School,
                title = "Course unavailable",
                description = "This course is not part of the published grade history."
            )
        }
        return
    }

    item(key = "course_stats") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EGradeStatTile(
                label = "Overall",
                value = course.overallText,
                badge = course.courseType.ifBlank { null },
                sub = M3EStatSubtitle.fromData("as recorded by VTOP"),
                tone = M3EGradeTone.GREY,
                modifier = Modifier.weight(1f)
            )
            M3EGradeStatTile(
                label = "Grade",
                value = course.grade,
                badge = course.pointsLabel,
                sub = M3EStatSubtitle.fromMetric(
                    course.assessmentCount.toString(),
                    if (course.assessmentCount == 1) "assessment" else "assessments"
                ),
                tone = M3EGradeComputations.toneForGrade(course.grade),
                modifier = Modifier.weight(1f)
            )
        }
    }

    item(key = "course_keyvalues") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            M3EGradeKeyValueTile(label = "Course", value = course.courseCode, modifier = Modifier.weight(1f))
            M3EGradeKeyValueTile(
                label = "Type",
                value = course.courseType.ifBlank { "—" },
                modifier = Modifier.weight(1f)
            )
            // Aggregate credits from the effective-grade row, never a hidden ETL/ETH/ELA half.
            M3EGradeKeyValueTile(
                label = "Credits",
                value = course.creditsLabel ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (course.isEmbedded) {
        if (course.embeddedHalves.isEmpty()) {
            item(key = "embedded_note") {
                M3EGradeInfoCard(
                    text = "VTOP records embedded courses as one blended grade, so the theory and lab halves are not part of the published record. Refresh to pull this term's marks and split them."
                )
            }
        } else {
            // The halves are real marks, but VTOP grades the pair once. Say so here too — the
            // no-halves copy below only covers a pair whose marks have not been pulled yet.
            item(key = "embedded_explanation") {
                M3EGradeInfoCard(
                    text = "VTOP publishes an embedded course as one blended grade; these are the two halves it blends."
                )
            }
            item(key = "embedded_header") {
                M3EGradeSectionHeader(
                    icon = Icons.Rounded.Layers,
                    title = "Theory and lab",
                    count = course.embeddedHalves.size
                )
            }
            item(key = "embedded_tiles") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    course.embeddedHalves.forEach { half ->
                        M3EGradeStatTile(
                            label = half.label,
                            value = half.scoreText,
                            badge = half.creditsLabel,
                            sub = M3EStatSubtitle.fromMetric(
                                half.assessmentCount.toString(),
                                if (half.assessmentCount == 1) "assessment" else "assessments"
                            ),
                            tone = M3EGradeTone.GREY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            course.embeddedHalves.forEach { half ->
                item(key = "half_assessments_${half.label}") {
                    M3EGradeSectionHeader(
                        icon = if (half.label.equals("Theory", ignoreCase = true)) Icons.Rounded.MenuBook else Icons.Rounded.Science,
                        title = "${half.label} assessments",
                        count = half.assessmentCount
                    )
                }
                items(items = half.assessments, key = { "half_${half.label}_${it.component}" }) { row ->
                    M3EGradeAssessmentRow(row = row)
                }
            }
        }
    }

    item(key = "breakdown_header") {
        M3EGradeSectionHeader(
            icon = Icons.Rounded.WorkspacePremium,
            title = "Published breakdown",
            count = course.breakdown.size
        )
    }
    if (course.breakdown.isEmpty()) {
        item(key = "breakdown_empty") {
            M3EGradeEmptyCard(
                icon = Icons.Rounded.WorkspacePremium,
                title = "No assessment breakdown",
                description = "VTOP published a final grade for this course without the per-assessment marks behind it."
            )
        }
    } else if (course.assessmentGroups != null) {
        // VTOP publishes each Mark Title table separately; show the split it recorded rather
        // than one weight-ordered pile that hides which half each assessment belongs to.
        course.assessmentGroups!!.forEach { group ->
            item(key = "assessment_group_${group.type}") {
                M3EGradeSectionHeader(
                    icon = when {
                        group.type.contains("theory", ignoreCase = true) -> Icons.Rounded.MenuBook
                        group.type.contains("lab", ignoreCase = true) -> Icons.Rounded.Science
                        else -> Icons.Rounded.Layers
                    },
                    title = "${group.type} assessments",
                    count = group.assessments.size
                )
            }
            items(items = group.assessments, key = { "${group.type}_${it.component}" }) { row ->
                M3EGradeAssessmentRow(row = row)
            }
        }
    } else {
        items(items = course.breakdown, key = { "breakdown_${it.component}" }) { row ->
            M3EGradeAssessmentRow(row = row)
        }
    }

    if (course.reconciliationText != null) {
        item(key = "breakdown_reconciliation") {
            M3EGradeInfoCard(text = course.reconciliationText!!)
        }
    }

    if (course.gradeScale.isNotEmpty()) {
        item(key = "scale_header") {
            M3EGradeSectionHeader(
                icon = Icons.Rounded.School,
                title = "Grade scale",
                count = course.gradeScale.size
            )
        }
        items(items = course.gradeScale, key = { "scale_${it.grade}" }) { row ->
            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    M3EGradeToneBadge(text = row.grade, tone = row.tone, modifier = Modifier.width(40.dp))
                    Text(
                        text = row.range,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = M3ETheme.colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (row.isYours) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(M3ETheme.colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Yours",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = M3ETheme.colors.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared M3E building blocks ───────────────────────────────────────────────

private fun M3EGradeInsightSlide.toStatItem(): M3EStatItem = M3EStatItem(
    id = id,
    label = label,
    value = value,
    // D-114/D-118: the slide's sub is derived in buildState from real term data;
    // wrap it in the typed token so no UI author can substitute a fabricated claim.
    sub = M3EStatSubtitle.fromData(sub.ifBlank { "Not available" }),
    tone = when (tone) {
        M3EGradeTone.GREEN -> M3EStatTone.GOOD
        M3EGradeTone.BLUE -> M3EStatTone.PRIMARY
        M3EGradeTone.AMBER -> M3EStatTone.WARNING
        else -> M3EStatTone.NEUTRAL
    }
)

/**
 * D-114: (container, text-on-container) for a grade hue. The foreground MUST be
 * the measured `on*Container` token — using the fill on a light container failed
 * AA (Classic Light AMBER #FEEFC3 + #E37400 = 2.71:1). All pairs below are >=7:1
 * in both appearances.
 */
@Composable
private fun gradeToneContainerAndOn(tone: M3EGradeTone): Pair<Color, Color> {
    val colors = M3ETheme.colors
    return when (tone) {
        M3EGradeTone.AMBER -> colors.warningContainer to colors.onWarningContainer
        M3EGradeTone.GREEN -> colors.successContainer to colors.onSuccessContainer
        M3EGradeTone.BLUE -> colors.primaryContainer to colors.onPrimaryContainer
        M3EGradeTone.CYAN -> colors.secondaryContainer to colors.onSecondaryContainer
        M3EGradeTone.VIOLET -> colors.tertiaryContainer to colors.onTertiaryContainer
        M3EGradeTone.RED -> colors.errorContainer to colors.onErrorContainer
        M3EGradeTone.GREY -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
}

/**
 * The vivid hue for graphics only (wave progress). Text never uses this: on a
 * Classic Light surface the AMBER fill is only 2.54:1, so a big metric value has
 * to use [gradeToneContainerAndOn] instead.
 */
@Composable
private fun gradeToneAccent(tone: M3EGradeTone): Color {
    val colors = M3ETheme.colors
    return when (tone) {
        M3EGradeTone.AMBER -> colors.warning
        M3EGradeTone.GREEN -> colors.success
        M3EGradeTone.BLUE -> colors.primary
        M3EGradeTone.CYAN -> colors.secondary
        M3EGradeTone.VIOLET -> colors.tertiary
        M3EGradeTone.RED -> colors.error
        M3EGradeTone.GREY -> colors.onSurfaceVariant
    }
}

@Composable
private fun gradePointColor(tone: M3EGradePointTone?): Color {
    val colors = M3ETheme.colors
    return when (tone) {
        M3EGradePointTone.SUCCESS -> colors.success
        M3EGradePointTone.PRIMARY -> colors.primary
        M3EGradePointTone.WARNING -> colors.warning
        M3EGradePointTone.NONE, null -> colors.outlineVariant
    }
}

@Composable
private fun M3EGradeToneBadge(
    text: String,
    tone: M3EGradeTone,
    modifier: Modifier = Modifier
) {
    val (container, foreground) = gradeToneContainerAndOn(tone)
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.ifBlank { "—" },
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun M3EGradeSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(text = count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun M3EGradeStatTile(
    label: String,
    value: String,
    badge: String?,
    sub: M3EStatSubtitle?,
    tone: M3EGradeTone,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // D-114: text needs the measured on-container token, not the vivid fill.
    val accent = gradeToneContainerAndOn(tone).second
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.height(152.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = value,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null && sub.text.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun M3EGradeKeyValueTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = modifier, containerLevel = SurfaceContainerLevel.LOW) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
            Text(
                text = value.ifBlank { "—" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun M3EGradeCourseRowItem(
    row: M3EGradeCourseRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (row.isEmbedded) Icons.Rounded.Layers else Icons.Rounded.School,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.courseCode,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    maxLines = 1
                )
                Text(
                    text = row.courseTitle,
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.isEmbedded) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "T+L", fontSize = 9.sp, fontWeight = FontWeight.Black, color = colors.onPrimaryContainer)
                }
            }
            Text(
                text = row.grandTotal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            M3EGradeToneBadge(text = row.grade.ifBlank { "—" }, tone = row.tone)
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun M3EGradeTermRowItem(
    term: M3EGradeTermRow,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val valueColor by animateColorAsState(
        targetValue = if (isActive) colors.primary else colors.onSurface,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "M3EGradeTermValueColor"
    )
    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = term.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            M3EWavyLinearProgress(
                progress = (term.gpa.toFloat() / 10f).coerceIn(0f, 1f),
                label = "Semester GPA progress",
                activeColor = gradePointColor(term.pointTone),
                trackColor = colors.surfaceContainerHighest,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (term.gpa > 0) term.gpa.toFixed(2) else "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
                modifier = Modifier.width(42.dp)
            )
        }
    }
}

@Composable
private fun M3EGradeAssessmentRow(
    row: M3EGradeBreakdownRow,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.component,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.weightagePercent.ifBlank { "-" }}% weightage",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = "${row.scoredMark.ifBlank { "-" }}/${row.maxMark.ifBlank { "-" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun M3EGradeEmptyCard(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (!description.isNullOrBlank()) {
                Text(text = description, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun M3EGradeInfoCard(text: String, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOWEST) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
            Text(text = text, fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}

// ── Pure state derivation from the real grade payload ───────────────────────

/** One term as published by `/api/remote/grades`, reduced to the fields the pages read. */
private data class M3EGradeTermData(
    val id: String,
    val label: String,
    val gpa: Double,
    val courses: List<GradeItem>
)

private fun GradeItem.asCourseRow(): M3EGradeCourseRow = M3EGradeCourseRow(
    courseCode = courseCode,
    courseTitle = courseTitle,
    grandTotal = "${M3EGradeComputations.numOrDash(grandTotal)}%",
    score = M3EGradeComputations.num(grandTotal),
    grade = grade,
    tone = M3EGradeComputations.toneForGrade(grade),
    isEmbedded = M3EGradeComputations.isEmbedded(courseType),
    courseType = courseType
)

/** Domain fallback only: a snapshot course carries the same real facts as a GradeItem. */
private fun fallbackGradeItem(course: com.amazecc.app.shared.domain.CourseGrade): GradeItem = GradeItem(
    courseCode = course.courseCode,
    courseTitle = course.courseTitle,
    courseType = course.courseType,
    grandTotal = course.grandTotal,
    grade = course.grade,
    details = course.details?.map { component: GradeComponent ->
        GradeBreakdown(
            component = component.name,
            maxMark = component.maxMark,
            weightagePercent = component.weightagePercent,
            status = component.status,
            scoredMark = component.score,
            weightageMark = component.weightage
        )
    },
    range = course.range?.let { bands ->
        GradeRange(
            S = bands.s, A = bands.a, B = bands.b, C = bands.c,
            D = bands.d, E = bands.e, F = bands.f
        )
    }
)

private fun shortTermLabel(label: String): String {
    val parts = label.split(" ")
    return if (parts.size >= 2 && parts[1].length >= 2) "${parts[0]} '${parts[1].takeLast(2)}" else label.take(14)
}

/**
 * Build the immutable, page-ready state from real data.
 *
 * `allGrades` (`AllGradesRes`) is authoritative for terms, courses, grades, published CGPA and
 * credits; the domain snapshot is the fallback and the only source of per-course embedded halves.
 * The host re-calls this with its local page stack, so no navigation state is stored here.
 */
internal fun buildGradesHistoryState(
    snapshot: DomainSnapshot,
    allGrades: AllGradesRes? = null,
    screen: M3EGradeScreen = M3EGradeScreen.OVERVIEW,
    selectedCourseCode: String? = null,
    selectedTermId: String? = null,
    expandedSemesterId: String? = null,
    isLoading: Boolean = false
): M3EGradesHistoryState {
    val domainRows = GradeHistory.semesterRows(snapshot)
    val publishedTerms = allGrades?.allSemesters
        ?.mapNotNull { (id, result) ->
            result?.let {
                M3EGradeTermData(
                    id = id,
                    label = GradeHistory.semesterName(id),
                    gpa = GradeHistory.num(it.gpa) ?: 0.0,
                    courses = it.grades
                )
            }
        }
        ?.sortedBy { GradeHistory.semesterSortKey(it.id) }
        .orEmpty()
    val terms = if (publishedTerms.isNotEmpty()) {
        publishedTerms
    } else {
        domainRows.map { row ->
            M3EGradeTermData(row.id, row.label, row.gpa, row.courses.map(::fallbackGradeItem))
        }
    }

    val allCourseRows = terms.flatMap { it.courses }.map { it.asCourseRow() }
    val activeTerm = terms.firstOrNull { it.id == selectedTermId }
        ?: terms.reversed().firstOrNull { it.courses.isNotEmpty() }
        ?: terms.lastOrNull()

    // VTOP's effective-grade table publishes one aggregate row per course (BAEEE101, 4.0 cr)
    // and hides the ETL/ETH/ELA halves in a collapsed sub-table whose credit cells are blank
    // (grades.html:201-259). Credit totals must come from the aggregate row exactly once;
    // summing every snapshot course would count an embedded course up to three times.
    fun baseCode(code: String): String = code.substringBefore("(").trim().uppercase()

    val publishedCreditsByTerm: Map<String, Map<String, Int>> = terms.associate { term ->
        term.id to term.courses
            .mapNotNull { item ->
                val credits = M3EGradeComputations.num(item.credits)?.toInt()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                baseCode(item.courseCode) to credits
            }
            .toMap()
    }

    fun creditsOf(semesterId: String, courseCode: String): Int {
        publishedCreditsByTerm[semesterId]?.get(baseCode(courseCode))?.let { return it }
        return snapshot.academics.semesters[semesterId]?.courses?.values
            ?.firstOrNull { baseCode(it.code) == baseCode(courseCode) }
            ?.credits?.trim()?.toDoubleOrNull()?.toInt() ?: 0
    }

    fun attemptedCredits(semesterId: String): Int {
        val published = publishedCreditsByTerm[semesterId]
        if (!published.isNullOrEmpty()) return published.values.sum()
        // No published marks for this term yet: still take one figure per base code so hidden
        // halves cannot inflate the attempted total.
        return snapshot.academics.semesters[semesterId]?.courses?.values
            ?.groupBy { baseCode(it.code) }
            ?.values
            ?.sumOf { halves ->
                halves.firstNotNullOfOrNull { c ->
                    c.credits?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                } ?: 0
            } ?: 0
    }

    // Counted over the published terms so the total still works when the marks snapshot is
    // absent — the per-term records below are only the snapshot's projection.
    val attemptedTotal = terms.sumOf { attemptedCredits(it.id) }
    var gpaSum = 0.0
    var gpaTerms = 0
    val semesterRecords = domainRows.map { row ->
        val courses = row.courses.map { c ->
            M3ESemesterGradeCourse(
                courseCode = c.courseCode,
                courseTitle = c.courseTitle,
                credits = creditsOf(row.id, c.courseCode),
                grade = c.grade,
                gradePoints = GradeHistory.gradePoints(c.grade) ?: 0,
                courseType = c.courseType
            )
        }
        val earned = courses.filter { (GradeHistory.gradePoints(it.grade) ?: 0) > 0 }.sumOf { it.credits }
        if (row.gpa > 0) {
            gpaSum += row.gpa
            gpaTerms += 1
        }
        M3ESemesterGradeRecord(
            semesterId = row.id,
            semesterName = row.label,
            sgpa = row.gpa,
            cgpaAtEnd = if (gpaTerms == 0) 0.0 else (gpaSum / gpaTerms * 100).let { round(it) / 100 },
            creditsEarned = earned,
            courses = courses
        )
    }

    val termRows = terms.map { term ->
        val scored = term.courses.mapNotNull { M3EGradeComputations.num(it.grandTotal) }
        M3EGradeTermRow(
            termId = term.id,
            label = term.label,
            gpa = term.gpa,
            courseCount = term.courses.size,
            scoredCount = scored.size,
            avgScore = if (scored.isEmpty()) 0.0 else M3EGradeComputations.round2(scored.sum() / scored.size),
            pointTone = M3EGradeComputations.pointTone(term.gpa)
        )
    }
    val distributionRows = M3EGradeComputations.distribution(allCourseRows)
    val typeAverages = M3EGradeComputations.typeAverages(allCourseRows)
    val termScores = M3EGradeComputations.termScores(termRows)

    val insightSlides = mutableListOf<M3EGradeInsightSlide>()
    activeTerm?.takeIf { it.courses.isNotEmpty() }?.let { term ->
        insightSlides += M3EGradeInsightSlide(
            id = "term",
            label = "This term",
            value = if (term.gpa > 0) term.gpa.toFixed(2) else "—",
            sub = "${term.label} · ${term.courses.size} courses",
            badge = "GPA",
            tone = M3EGradeComputations.insightTone(term.gpa)
        )
    }
    val bestTerm = terms.filter { it.gpa > 0 }.maxByOrNull { it.gpa }
    bestTerm?.let { term ->
        insightSlides += M3EGradeInsightSlide(
            id = "best",
            label = "Best term",
            value = term.gpa.toFixed(2),
            sub = term.label,
            badge = "Peak",
            tone = M3EGradeTone.GREEN
        )
    }
    insightSlides += M3EGradeInsightSlide(
        id = "terms",
        label = "Terms recorded",
        value = terms.count { it.courses.isNotEmpty() }.toString(),
        sub = if (terms.size > 1) "across ${terms.size} published" else "so far",
        badge = "History",
        tone = M3EGradeTone.GREY
    )

    val summary = allGrades?.overall?.cgpa
    val cumulative = GradeHistory.cumulativeGpa(
        rows = domainRows,
        cgpa = allGrades?.publishedCgpa,
        creditsEarned = summary?.creditsEarned,
        creditsRequired = summary?.creditsRegistered
    )
    val cumulativeValue = if (cumulative.value > 0) cumulative.value.toFixed(2) else "—"
    val cumulativeBadge = if (cumulative.creditsRequired > 0) {
        "${cumulative.creditsEarned.toFixed(0)}/${cumulative.creditsRequired.toFixed(0)} cr"
    } else null
    val cumulativeSubtitle = when (cumulative.source) {
        GradeHistory.CumulativeSource.VTOP -> "Credit-weighted CGPA"
        GradeHistory.CumulativeSource.DERIVED -> "Computed from your grades"
        GradeHistory.CumulativeSource.NONE -> "Mean of terms, not weighted"
    }

    val courseDetail = selectedCourseCode?.let { code ->
        activeTerm?.courses?.firstOrNull { it.courseCode == code }
            ?: terms.firstNotNullOfOrNull { term -> term.courses.firstOrNull { it.courseCode == code } }
    }?.let { item ->
        val termId = terms.firstOrNull { it.courses.any { c -> c.courseCode == item.courseCode } }?.id
        buildGradeCourseDetail(item, termId, snapshot)
    }

    val publishedCounts = summary?.grades
    val distributionByLetter = distributionRows.associate { it.grade to it.count }
    return M3EGradesHistoryState(
        cumulativeGpa = cumulative.value,
        totalCreditsEarned = summary?.creditsEarned
            ?.let { M3EGradeComputations.num(it)?.toInt() }
            ?: semesterRecords.sumOf { it.creditsEarned },
        totalCreditsAttempted = attemptedTotal,
        sGradesCount = publishedCounts?.S ?: (distributionByLetter["S"] ?: 0),
        aGradesCount = publishedCounts?.A ?: (distributionByLetter["A"] ?: 0),
        bGradesCount = publishedCounts?.B ?: (distributionByLetter["B"] ?: 0),
        semesters = semesterRecords,
        expandedSemesterId = expandedSemesterId,
        isLoading = isLoading,
        screen = screen,
        selectedCourseCode = selectedCourseCode,
        selectedTermId = activeTerm?.id,
        cumulativeValue = cumulativeValue,
        cumulativeBadge = cumulativeBadge,
        cumulativeSubtitle = cumulativeSubtitle,
        termOptions = terms.map { M3EGradeTermOption(it.id, it.label, shortTermLabel(it.label)) },
        courseRows = M3EGradeComputations.sortByGrade(
            activeTerm?.courses.orEmpty().map { it.asCourseRow() }
        ),
        termRows = termRows,
        insightSlides = insightSlides,
        distributionRows = distributionRows,
        typeAverages = typeAverages,
        termScores = termScores,
        courseDetail = courseDetail
    )
}

/**
 * D-189 G4 — reconcile an embedded course's two marks halves against the one total VTOP published.
 *
 * VTOP grades the pair once, on the credit-weighted blend of its halves; the halves are shown only
 * to explain that published grade. Both halves must carry marks **and** their own credits, and a
 * published total must exist — otherwise there is no honest reconciliation to print, never a
 * guessed 1:1 ratio. The blend is the exact [EmbeddedCourseSemantics.creditWeighted] number: no
 * rounding to a precision VTOP did not publish, and no letter derived from it.
 */
private fun embeddedHalvesReconciliation(
    halves: List<EmbeddedCourseHalf>,
    publishedTotal: String,
): String? {
    val published = M3EGradeComputations.num(publishedTotal) ?: return null
    val theory = halves.firstOrNull { it.half.startsWith("T", ignoreCase = true) } ?: return null
    val lab = halves.firstOrNull { it.half.startsWith("L", ignoreCase = true) } ?: return null
    val theoryScore = theory.marks?.total ?: return null
    val labScore = lab.marks?.total ?: return null
    if (!theoryScore.isFinite() || !labScore.isFinite()) return null
    val theoryCredits = M3EGradeComputations.num(theory.credits) ?: return null
    val labCredits = M3EGradeComputations.num(lab.credits) ?: return null
    val blend = EmbeddedCourseSemantics.creditWeighted(
        listOf(
            EmbeddedEntry(
                code = theory.code, half = EmbeddedHalf.THEORY,
                credits = theoryCredits, earnedMarks = theoryScore,
            ),
            EmbeddedEntry(
                code = lab.code, half = EmbeddedHalf.LAB,
                credits = labCredits, earnedMarks = labScore,
            ),
        )
    ) { it.earnedMarks } ?: return null
    return "Weighted by ${unroundedNumber(theoryCredits)} and ${unroundedNumber(labCredits)} credits, " +
        "the halves come to ${unroundedNumber(blend)}, against the ${unroundedNumber(published)} VTOP recorded"
}

/** Shortest honest decimal for a published number: no rounding, just a trailing `.0` dropped. */
private fun unroundedNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Build the course subpage model from the real GradeItem and the snapshot's embedded halves. */
private fun buildGradeCourseDetail(
    item: GradeItem,
    termId: String?,
    snapshot: DomainSnapshot
): M3EGradeCourseDetail {
    val details = item.details.orEmpty()
    val flatRows = details.map { d ->
        M3EGradeBreakdownRow(
            component = d.component,
            weightagePercent = d.weightagePercent,
            scoredMark = d.scoredMark,
            maxMark = d.maxMark,
            weightage = M3EGradeComputations.num(d.weightagePercent) ?: 0.0,
            type = d.type
        )
    }
    // The web keeps the flat list when the deployment stripped the per-table `type` tag;
    // a real tag switches the same rows into Theory / Lab / Component N sections.
    val groups = M3EGradeComputations.groupAssessmentsByType(flatRows)
    val breakdown = if (groups != null) flatRows else M3EGradeComputations.byWeightage(flatRows)
    val gradeScale = item.range?.let { r ->
        listOf(
            "S" to r.S, "A" to r.A, "B" to r.B, "C" to r.C,
            "D" to r.D, "E" to r.E, "F" to r.F
        ).map { (letter, range) ->
            M3EGradeScaleRow(
                grade = letter,
                range = range,
                tone = M3EGradeComputations.toneForGrade(letter),
                isYours = letter.equals(item.grade.trim(), ignoreCase = true)
            )
        }
    }.orEmpty()

    val embedded = M3EGradeComputations.isEmbedded(item.courseType)
    // The published row carries the aggregate code; a legacy snapshot may key its halves with a
    // "(L)"/"(T)" suffix. Match on the base code so the halves still attach to the right course.
    val baseCode = item.courseCode.substringBefore("(").trim().uppercase()
    val domainCourse = termId?.let { tid ->
        snapshot.academics.semesters[tid]?.courses?.values
            ?.firstOrNull { it.code.substringBefore("(").trim().uppercase() == baseCode }
    } ?: snapshot.academics.semesters.values.firstNotNullOfOrNull { sem ->
        sem.courses.values.firstOrNull { it.code.substringBefore("(").trim().uppercase() == baseCode }
    }
    val halves = if (embedded) {
        domainCourse?.halves.orEmpty()
            .filter { it.half.startsWith("T", ignoreCase = true) || it.half.startsWith("L", ignoreCase = true) }
            .map { half ->
                val assessments = half.marks?.assessments.orEmpty().map { a ->
                    M3EGradeBreakdownRow(
                        component = a.name,
                        weightagePercent = a.weightagePercent,
                        scoredMark = a.score,
                        maxMark = a.maxMark,
                        weightage = M3EGradeComputations.num(a.weightagePercent) ?: 0.0
                    )
                }
                M3EGradeEmbeddedHalf(
                    label = if (half.half.startsWith("T", ignoreCase = true)) "Theory" else "Lab",
                    scoreText = half.marks?.total?.let { "${M3EGradeComputations.round2(it)}%" } ?: "—",
                    creditsLabel = half.credits?.takeIf { it.isNotBlank() }?.let { "$it cr" },
                    assessmentCount = assessments.size,
                    assessments = M3EGradeComputations.byWeightage(assessments)
                )
            }
    } else emptyList()

    // D-189 G4: a real embedded pair reconciles against its published total. Keep the flat
    // weightage note only when the halves cannot be reconciled (missing marks/credits/total).
    val embeddedReconciliation =
        if (embedded) embeddedHalvesReconciliation(domainCourse?.halves.orEmpty(), item.grandTotal) else null

    return M3EGradeCourseDetail(
        courseCode = item.courseCode,
        courseTitle = item.courseTitle,
        courseType = item.courseType,
        overallText = "${M3EGradeComputations.numOrDash(item.grandTotal)}%",
        grade = item.grade.ifBlank { "—" },
        pointsLabel = M3EGradeComputations.gradePointsLabel(item.grade),
        assessmentCount = details.size,
        breakdown = breakdown,
        gradeScale = gradeScale,
        embeddedHalves = halves,
        isEmbedded = embedded,
        creditsLabel = item.credits?.takeIf { it.isNotBlank() }?.let { "$it cr" },
        assessmentGroups = groups,
        reconciliationText = embeddedReconciliation
            ?: M3EGradeComputations.reconciliationNote(item.grandTotal, details)
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun createMockGradesHistoryState() = M3EGradesHistoryState(
    cumulativeGpa = 9.24,
    totalCreditsEarned = 124,
    totalCreditsAttempted = 128,
    sGradesCount = 14,
    aGradesCount = 18,
    bGradesCount = 4,
    semesters = emptyList(),
    cumulativeValue = "9.24",
    cumulativeBadge = "124/160 cr",
    cumulativeSubtitle = "Credit-weighted CGPA",
    termOptions = listOf(M3EGradeTermOption("t2", "Fall 2025-26"), M3EGradeTermOption("t1", "Winter 2024-25")),
    courseRows = listOf(
        M3EGradeCourseRow("CSE3002", "Internet and Web Programming", "92.5%", 92.5, "S", M3EGradeTone.AMBER, false, "Embedded Theory"),
        M3EGradeCourseRow("MAT2001", "Complex Variables", "88.0%", 88.0, "A", M3EGradeTone.GREEN, false, "Theory Only")
    ),
    termRows = listOf(
        M3EGradeTermRow("t2", "Fall 2025-26", 9.35, 2, 2, 90.25, M3EGradePointTone.SUCCESS),
        M3EGradeTermRow("t1", "Winter 2024-25", 8.4, 2, 2, 84.0, M3EGradePointTone.PRIMARY)
    ),
    selectedTermId = "t2",
    insightSlides = listOf(
        M3EGradeInsightSlide("term", "This term", "9.35", "Fall 2025-26 · 2 courses", "GPA", M3EGradeTone.GREEN),
        M3EGradeInsightSlide("best", "Best term", "9.35", "Fall 2025-26", "Peak", M3EGradeTone.GREEN),
        M3EGradeInsightSlide("terms", "Terms recorded", "2", "across 2 published", "History", M3EGradeTone.GREY)
    )
)

@Preview
@Composable
private fun M3EGradesHistoryScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EGradesHistoryScreen(state = createMockGradesHistoryState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EGradesHistoryScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EGradesHistoryScreen(state = createMockGradesHistoryState(), onAction = {})
    }
}
