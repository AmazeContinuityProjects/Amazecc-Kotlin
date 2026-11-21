package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.EmbeddedLinkCode
import com.amazecc.app.shared.domain.MarkComponent
import com.amazecc.app.shared.domain.PredictorComponent
import com.amazecc.app.shared.domain.computeComponentStats
import com.amazecc.app.shared.domain.predictorAssessmentFromVtop
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Inline panels behind the Course sections hub on the embedded course subpage.
 *
 * The D-134 per-component panels (attendance log + predictor) are strict projections of the
 * [EmbeddedCourseHalf] rows already carried by `M3ECourseDetailState.halves` (built by
 * `EmbeddedLinkMerge`). The D-141 course-level panels (Grades, Review Course Details) project only
 * fields already on the state. Nothing here invents a mark, a log, a date or a grade: an absent
 * payload renders an honest empty state. Exactly as the web `CourseDetailSubpage` does, the
 * component panels only split the *attendance log* and *predictor* inputs by component, while
 * marks and grades stay common on the parent screen.
 */

/**
 * D-141 hierarchical back header shared by every expanded inline panel on the course hub.
 *
 * This control only clears the *local* "which panel is expanded" selection in the caller — it
 * never emits a global `Back` action. That gives the web-style hierarchy
 * `sub-panel -> "Course sections" hub -> (global back button -> leave the course)`. Its
 * content description deliberately differs from the global button's `"Back"` so tests that
 * click the global button by exact description stay unambiguous.
 */
@Composable
internal fun M3EInlinePanelBackHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to course sections",
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "Back to course sections",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.primary
        )
    }
}

/** Which side of an embedded pair this row is. Uses the canonical classifier, never a code string test. */
internal fun isLabHalf(half: EmbeddedCourseHalf): Boolean =
    half.half.equals("L", ignoreCase = true) ||
        EmbeddedLinkCode.componentOf(
            rawCode = half.code,
            typeHint = half.type,
            slot = half.slots.firstOrNull(),
            title = half.title,
        ).isLab ||
        half.slots.any { it.trim().startsWith("L", ignoreCase = true) }

/**
 * The (theory, lab) pair when — and only when — the state actually carries both halves.
 * This is composition, not a "contains Lab in the name" heuristic.
 */
internal fun embeddedPair(halves: List<EmbeddedCourseHalf>): Pair<EmbeddedCourseHalf, EmbeddedCourseHalf>? {
    val lab = halves.firstOrNull(::isLabHalf) ?: return null
    val theory = halves.firstOrNull { !isLabHalf(it) } ?: return null
    return theory to lab
}

internal fun m3eComponentLabel(half: EmbeddedCourseHalf): String = if (isLabHalf(half)) "Lab" else "Theory"

/** Real attendance of one half, formatted for a menu-row subtitle or the panel headline. */
internal fun componentAttendanceText(half: EmbeddedCourseHalf): String {
    val att = half.attendance ?: return "Attendance not published"
    val pct = attendancePercent(att) ?: return "Attendance not published"
    return "${pct.toFixed(2, "%")} · ${att.attended}/${att.total} classes"
}

private fun attendancePercent(att: Attendance?): Double? {
    if (att == null) return null
    val printed = att.percentage.replace("%", "").trim().toDoubleOrNull()
    if (printed != null) return printed
    return if (att.total > 0) round(att.attended * 10000.0 / att.total) / 100.0 else null
}

/**
 * Bunk margin for one component, ported from `CourseDetailSubpage.tsx:1253-1279`.
 * A lab meeting is a 2-hour session, so both "needed" and "bunkable" counts are halved for a lab
 * half — the same lab=2-session rule the rest of the app uses for lab hours. The caller passes the
 * live effective target from [com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState]; the
 * 75% default is only for standalone previews, never for a real course screen.
 */
internal fun componentMarginText(
    half: EmbeddedCourseHalf,
    attendanceTargetPercent: Double = 75.0,
): String {
    val att = half.attendance ?: return "Attendance not published"
    val total = att.total
    if (total <= 0) return "No classes held yet"

    val attended = att.attended
    val pct = attended * 100.0 / total
    val threshold = (attendanceTargetPercent / 100.0).coerceIn(0.01, 0.99)
    val isLab = isLabHalf(half)
    val unit = if (isLab) "lab" else "class"

    if (pct < attendanceTargetPercent) {
        val needed = ceil((threshold * total - attended) / (1.0 - threshold)).toInt().coerceAtLeast(0)
        val sessions = if (isLab) ceil(needed / 2.0).toInt() else needed
        val plural = if (sessions == 1) "" else if (isLab) "s" else "es"
        return "Need $sessions more $unit$plural to reach ${attendanceTargetPercent.toInt()}%"
    }

    val canMiss = floor(attended / threshold - total).toInt()
    val sessions = if (isLab) floor(canMiss / 2.0).toInt() else canMiss
    return if (sessions <= 0) {
        "No misses left · at ${attendanceTargetPercent.toInt()}% margin"
    } else {
        "$sessions bunkable · safe above ${attendanceTargetPercent.toInt()}%"
    }
}

/** Attendance log, margin and heatmap-style progress for one real embedded half. */
@Composable
fun M3EComponentLogPanel(
    half: EmbeddedCourseHalf,
    modifier: Modifier = Modifier,
    attendanceTargetPercent: Double = 75.0,
    /** D-141: non-null only when opened from the course hub; clears the local panel selection. */
    onBack: (() -> Unit)? = null,
) {
    val colors = M3ETheme.colors
    val label = m3eComponentLabel(half)
    val att = half.attendance
    val pct = attendancePercent(att)

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) {
                M3EInlinePanelBackHeader(onBack = onBack)
            }
            Text(
                text = "$label attendance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            if (att == null || pct == null) {
                Text(
                    text = "Attendance has not been published for the $label component.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                Text(
                    text = pct.toFixed(2, "%"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = colors.primary
                )
                M3EWavyLinearProgress(
                    progress = (pct / 100.0).toFloat().coerceIn(0f, 1f),
                    label = "$label attendance",
                    activeColor = colors.primary
                )
                Text(
                    text = "${att.attended}/${att.total} classes attended",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = componentMarginText(half, attendanceTargetPercent),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary
                )
            }

            Text(
                text = "Attendance log",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            val logs = att?.logs.orEmpty()
            if (logs.isEmpty()) {
                Text(
                    text = "No attendance logs published for this component.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            } else {
                logs.forEach { day ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${day.date} · ${day.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-half assessment rows and their scored/pending/lost split, derived from the half's REAL
 * `marks` with the shared [computeComponentStats] predictor helper. A half with no published
 * assessments shows an honest empty state instead of a preset regimen with invented numbers.
 */
@Composable
fun M3EComponentPredictorPanel(
    half: EmbeddedCourseHalf,
    modifier: Modifier = Modifier,
    /** D-141: non-null only when opened from the course hub; clears the local panel selection. */
    onBack: (() -> Unit)? = null,
) {
    val colors = M3ETheme.colors
    val label = m3eComponentLabel(half)
    val isLab = isLabHalf(half)
    val predictorComponent = if (isLab) PredictorComponent.LAB else PredictorComponent.THEORY
    val markComponent = if (isLab) MarkComponent.LAB else MarkComponent.THEORY

    val rawAssessments = half.marks?.assessments.orEmpty().mapIndexed { index, assessment ->
        predictorAssessmentFromVtop(
            id = "${half.code.ifBlank { label }}-${index + 1}",
            title = assessment.name,
            maxMark = assessment.maxMark,
            weightagePercent = assessment.weightagePercent,
            scoredMark = assessment.score,
            component = predictorComponent,
        )
    }
    val credits = half.credits?.trim()?.toDoubleOrNull() ?: 1.0
    val stats = if (rawAssessments.isEmpty()) {
        null
    } else {
        // Deliberately NOT normalised or FAT-inferred: only published rows are shown, so the
        // split stays honest to the marks VTOP actually released for this half.
        computeComponentStats(rawAssessments, markComponent, credits)
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) {
                M3EInlinePanelBackHeader(onBack = onBack)
            }
            Text(
                text = "$label predictor",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            if (stats == null) {
                Text(
                    text = "No assessments published for the $label component yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Scored ${stats.scoredWeight.toFixed(2)} · Pending ${stats.pendingWeight.toFixed(2)} · " +
                        "Lost ${stats.lostWeight.toFixed(2)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                Text(
                    text = "Max still possible ${stats.maxPossibleWeight.toFixed(2)} of " +
                        "${stats.totalWeightConfigured.toFixed(2)} configured weight",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Assessments",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                stats.assessments.forEach { assessment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = assessment.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Weightage ${assessment.weightagePercent.toFixed(2)}%",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (assessment.isPending) {
                                "Pending"
                            } else {
                                "${assessment.percentage.toFixed(1)}%"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * D-141 "Grades" destination for the Course sections hub.
 *
 * `M3ECourseDetailState` deliberately carries no published final-grade field — it has internal
 * scored/max, assessments and a FAT target grade, but no released final grade. This panel is
 * therefore a *real* inline destination that always reports the honest no-data state; it is not a
 * dead control and it invents no letter/grade. If a published final grade is added to the state
 * later, only this panel needs to learn how to project it.
 */
@Composable
fun M3ECourseGradesPanel(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            M3EInlinePanelBackHeader(onBack = onBack)
            Text(
                text = "Grades",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = "Not available — no published final grade for this course",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * D-141 "Review Course Details" destination for the Course sections hub.
 *
 * Every value below is a real field already held by [com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState]
 * and already shown on the overview — this panel re-projects those same values, never a mock or
 * a fabricated duplicate. Nullable fields degrade to an explicit "Not published" instead of a
 * placeholder value.
 */
@Composable
fun M3ECourseReviewPanel(
    facultyName: String,
    facultyDesignation: String?,
    facultyCabin: String?,
    facultyEmail: String?,
    venue: String,
    slot: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            M3EInlinePanelBackHeader(onBack = onBack)
            Text(
                text = "Review Course Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            M3EFactRow(label = "Faculty", value = facultyName)
            if (!facultyDesignation.isNullOrBlank()) {
                M3EFactRow(label = "Designation", value = facultyDesignation)
            }
            M3EFactRow(label = "Cabin", value = facultyCabin ?: "Not published")
            M3EFactRow(label = "Email", value = facultyEmail ?: "Not published")
            M3EFactRow(label = "Venue", value = venue)
            M3EFactRow(label = "Slot", value = slot)
        }
    }
}

/** One label/value pair inside [M3ECourseReviewPanel], painted on the card surface. */
@Composable
private fun M3EFactRow(label: String, value: String) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
    }
}
