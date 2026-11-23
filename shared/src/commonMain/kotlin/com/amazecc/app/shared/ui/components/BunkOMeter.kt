package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun BunkOMeterCard(
    courses: List<AttendanceItem>,
    modifier: Modifier = Modifier,
    isInnerCard: Boolean = false,
    targetPct: Float = 75f
) {
    val colors = AmazeTheme.colors

    val stats = remember(courses, targetPct) { bunkStatsOf(courses, targetPct) }
    val mood = bunkMood(stats)
    // A mixed list keeps its real status, but never hides the fact that some courses have no
    // published percentage. With no usable figure at all the dedicated neutral state is used.
    fun unavailableNote(): String = if (stats.unavailableCount > 0 && mood != BunkMood.UNAVAILABLE) {
        " · ${stats.unavailableCount} course${if (stats.unavailableCount > 1) "s" else ""} without published %"
    } else ""

    val (badgeBg, badgeText, badgeIcon, accentColor, statusTitle, statusSubtitle) = when (mood) {
        BunkMood.READY -> Tuple6(
            colors.accent.copy(alpha = 0.12f),
            colors.accent,
            Icons.Rounded.Shield,
            colors.accent,
            "Bunk-O-Meter Ready",
            "Sync attendance to calculate safe bunk limits."
        )
        BunkMood.UNAVAILABLE -> Tuple6(
            colors.textMuted.copy(alpha = 0.12f),
            colors.textMuted,
            Icons.Rounded.Info,
            colors.textMuted,
            "Attendance not available",
            "Percentages aren't published for these courses yet."
        )
        BunkMood.CRITICAL -> Tuple6(
            colors.danger.copy(alpha = 0.12f),
            colors.danger,
            Icons.Rounded.Warning,
            colors.danger,
            "⚠️ ${stats.criticalCount} Critical Course${if (stats.criticalCount > 1) "s" else ""}",
            "Attendance is below ${targetPct.toInt()}%! Do NOT skip any more classes." + unavailableNote()
        )
        BunkMood.WARNING -> Tuple6(
            colors.warning.copy(alpha = 0.12f),
            colors.warning,
            Icons.Rounded.Warning,
            colors.warning,
            "Careful! ${stats.warningCount} course${if (stats.warningCount > 1) "s" else ""} near ${targetPct.toInt()}%",
            "You can bunk ~${stats.totalBunkable} total class${if (stats.totalBunkable != 1) "es" else ""} safely overall." + unavailableNote()
        )
        BunkMood.SAFE -> Tuple6(
            colors.success.copy(alpha = 0.12f),
            colors.success,
            Icons.Rounded.CheckCircle,
            colors.success,
            "Safe to Bunk ~${stats.totalBunkable} Class${if (stats.totalBunkable != 1) "es" else ""}",
            "All courses are safely above ${targetPct.toInt()}%. Keep up the margin!" + unavailableNote()
        )
    }

    val cardContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(badgeIcon, contentDescription = null, tint = badgeText, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BUNK-O-METER",
                        style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusTitle,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = AmazeTheme.fontSize.md
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusSubtitle,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
        }
    }

    if (isInnerCard) {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.04f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                cardContent()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.04f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                cardContent()
            }
        }
    }
}

internal enum class BunkMood { READY, UNAVAILABLE, CRITICAL, WARNING, SAFE }

internal data class BunkStats(
    val totalBunkable: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val hasData: Boolean,
    /** Courses with no published percentage — never counted as a real 0% / CRITICAL course. */
    val unavailableCount: Int = 0,
)

/**
 * D-189: an embedded merged course has two attendance entries and no published blended percentage.
 * Its printed percentage is blank, so the old `?: 0.0` made every such course CRITICAL and gave it
 * a bunk margin derived from two different classes' summed counts. Leave those courses out of the
 * status and margin maths entirely and report them as unavailable instead.
 */
internal fun bunkStatsOf(courses: List<AttendanceItem>, targetPct: Float): BunkStats {
    if (courses.isEmpty()) return BunkStats(0, 0, 0, false)

    var totalBunkable = 0
    var criticalCount = 0
    var warningCount = 0
    var unavailableCount = 0

    courses.forEach { course ->
        val total = course.totalClasses
        if (total <= 0) return@forEach

        val attended = course.attendedClasses
        val pct = course.attendancePercentage.toDoubleOrNull()
        if (pct == null) {
            unavailableCount++
            return@forEach
        }

        when (Projections.attendanceStatus(pct.toFloat(), total, targetPct)) {
            Projections.AttendanceStatus.CRITICAL -> criticalCount++
            Projections.AttendanceStatus.WARNING -> warningCount++
            else -> Unit
        }

        totalBunkable += Projections.bunkableClasses(attended, total, targetPct) ?: 0
    }

    return BunkStats(totalBunkable, criticalCount, warningCount, true, unavailableCount)
}

internal fun bunkMood(stats: BunkStats): BunkMood = when {
    !stats.hasData -> BunkMood.READY
    stats.unavailableCount > 0 &&
        stats.criticalCount == 0 && stats.warningCount == 0 && stats.totalBunkable == 0 -> BunkMood.UNAVAILABLE
    stats.criticalCount > 0 -> BunkMood.CRITICAL
    stats.warningCount > 0 -> BunkMood.WARNING
    else -> BunkMood.SAFE
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
