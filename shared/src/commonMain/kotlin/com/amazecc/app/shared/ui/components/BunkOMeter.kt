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
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.theme.AmazeTheme
import kotlin.math.floor

@Composable
fun BunkOMeterCard(
    attendance: AttendanceRes?,
    modifier: Modifier = Modifier,
    isInnerCard: Boolean = false
) {
    val colors = AmazeTheme.colors

    val stats = remember(attendance) {
        val courseList = attendance?.attendance ?: emptyList()
        if (courseList.isEmpty()) {
            return@remember BunkStats(0, 0, 0, false)
        }

        var totalBunkable = 0
        var criticalCount = 0
        var warningCount = 0

        courseList.forEach { course ->
            val total = course.totalClasses
            if (total <= 0) return@forEach

            val attended = course.attendedClasses
            val pct = course.attendancePercentage.toDoubleOrNull() ?: 0.0

            if (pct < 75.0) {
                criticalCount++
            } else if (pct < 80.0) {
                warningCount++
            }

            if (pct >= 75.0) {
                val maxBunks = floor((attended.toDouble() / 0.75) - total.toDouble()).toInt()
                if (maxBunks > 0) {
                    totalBunkable += maxBunks
                }
            }
        }

        BunkStats(
            totalBunkable = totalBunkable,
            criticalCount = criticalCount,
            warningCount = warningCount,
            hasData = true
        )
    }

    val (badgeBg, badgeText, badgeIcon, accentColor, statusTitle, statusSubtitle) = when {
        !stats.hasData -> Tuple6(
            colors.accent.copy(alpha = 0.12f),
            colors.accent,
            Icons.Rounded.Shield,
            colors.accent,
            "Bunk-O-Meter Ready",
            "Sync attendance to calculate safe bunk limits."
        )
        stats.criticalCount > 0 -> Tuple6(
            colors.danger.copy(alpha = 0.12f),
            colors.danger,
            Icons.Rounded.Warning,
            colors.danger,
            "⚠️ ${stats.criticalCount} Critical Course${if (stats.criticalCount > 1) "s" else ""}",
            "Attendance is below 75%! Do NOT skip any more classes."
        )
        stats.warningCount > 0 -> Tuple6(
            colors.warning.copy(alpha = 0.12f),
            colors.warning,
            Icons.Rounded.Warning,
            colors.warning,
            "Careful! ${stats.warningCount} course${if (stats.warningCount > 1) "s" else ""} near 75%",
            "You can bunk ~${stats.totalBunkable} total class${if (stats.totalBunkable != 1) "es" else ""} safely overall."
        )
        else -> Tuple6(
            colors.success.copy(alpha = 0.12f),
            colors.success,
            Icons.Rounded.CheckCircle,
            colors.success,
            "Safe to Bunk ~${stats.totalBunkable} Class${if (stats.totalBunkable != 1) "es" else ""}",
            "All courses are safely above 75%. Keep up the margin!"
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
                        fontSize = 14.sp
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

private data class BunkStats(
    val totalBunkable: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val hasData: Boolean
)

private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
