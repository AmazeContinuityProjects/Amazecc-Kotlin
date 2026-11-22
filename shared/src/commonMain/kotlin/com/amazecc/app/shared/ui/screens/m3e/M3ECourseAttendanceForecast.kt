package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.AttendancePredictor
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.buildCourseAttendanceForecast
import com.amazecc.app.shared.model.CalendarDay as PublishedDay
import com.amazecc.app.shared.model.CalendarEvent as PublishedEvent
import com.amazecc.app.shared.model.CalendarMonth as PublishedMonth
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.AttendanceTimetable
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * D-170 course-level attendance forecast, the Kotlin face of the web `Predictor` tab.
 *
 * Every number comes from [buildCourseAttendanceForecast] over the *published* calendar and the
 * half's real attendance. A component with no attendance, no slots or no calendar renders the
 * honest empty copy — no invented milestone counts and no placeholder percentages. Nothing here
 * writes to the snapshot or triggers a fetch.
 */

internal data class M3EMilestoneCount(val label: String, val count: Int)

internal data class M3EAttendanceHero(
    val attended: Int,
    val total: Int,
    val currentPct: Double,
    val predictedPct: Double,
    val safeBunks: Int,
    val classesNeeded: Int,
    val isLab: Boolean,
)

internal data class M3ECourseForecastSnapshot(
    val hero: M3EAttendanceHero,
    val milestones: List<M3EMilestoneCount>,
)

/** Pure projection used by both the screen and the JVM acceptance tests. */
internal fun buildCourseForecastSnapshot(
    component: EmbeddedCourseHalf,
    months: List<PublishedMonth>,
    yearByMonth: Map<Int, Int>,
    now: LocalDateTime,
    targetPercent: Double,
): M3ECourseForecastSnapshot? {
    val forecast = buildCourseAttendanceForecast(component, months, yearByMonth, now, targetPercent)
        ?: return null
    val attendance = component.attendance ?: return null
    if (attendance.total <= 0) return null

    // "END" means no artificial ceiling; the projection itself already halves lab margins.
    val result = forecast.project(skips = emptySet(), cutoff = "END")
    val milestones = listOf(
        "CAT1" to "Classes before CAT I",
        "CAT2" to "Classes before CAT II",
        "LID" to "Classes before Mid Term Test",
        "END" to "Classes before FAT",
    ).map { (cutoff, label) -> M3EMilestoneCount(label, forecast.meetingsThrough(cutoff).size) }

    return M3ECourseForecastSnapshot(
        hero = M3EAttendanceHero(
            attended = result.attended,
            total = result.total,
            currentPct = result.currentPct,
            predictedPct = result.predictedPct,
            safeBunks = result.safeBunks,
            classesNeeded = result.classesNeeded,
            isLab = result.isLab,
        ),
        milestones = milestones,
    )
}

/** One margin headline, chosen from the real projection — never a fabricated "safe" label. */
internal fun forecastMarginHeadline(hero: M3EAttendanceHero): String = when {
    hero.classesNeeded > 0 ->
        "Need ${hero.classesNeeded} class${if (hero.classesNeeded == 1) "" else "es"}"
    hero.safeBunks > 0 -> "${hero.safeBunks} bunkable"
    else -> "On edge"
}

/**
 * The published months for this term. The snapshot keeps every named calendar verbatim; pick
 * the one that names the selected semester, else fall back to the first one, and only then to
 * the flattened single calendar (which carries a label but no explicit year/month number).
 */
internal fun publishedMonthsFor(domain: DomainSnapshot, semesterId: String?): List<PublishedMonth> {
    val named = domain.schedule.calendarsList?.calendars.orEmpty()
    val chosen = if (semesterId.isNullOrBlank()) {
        named.firstOrNull()
    } else {
        named.firstOrNull { it.name.equals(semesterId, ignoreCase = true) }
            ?: named.firstOrNull { it.name.contains(semesterId, ignoreCase = true) }
            ?: named.firstOrNull()
    }
    if (chosen != null && chosen.months.isNotEmpty()) return chosen.months
    return domain.schedule.calendar.map { month ->
        PublishedMonth(
            month = month.label,
            days = month.days.map { day ->
                PublishedDay(
                    date = day.date,
                    events = day.events.map { event ->
                        PublishedEvent(
                            type = event.type,
                            text = event.text,
                            color = event.color,
                            category = event.category,
                        )
                    },
                )
            },
        )
    }
}

/** Defensive year lookup: explicit fields first, then the trailing year in the month label. */
internal fun buildYearByMonth(months: List<PublishedMonth>): Map<Int, Int> = buildMap {
    months.forEach { month ->
        val number = month.monthNumber ?: AttendanceTimetable.parseMonthNumber(month.month)
            ?: return@forEach
        val year = month.year
            ?: month.month.trim().substringAfterLast(' ').toIntOrNull()?.takeIf { it >= 2000 }
            ?: return@forEach
        put(number, year)
    }
}

@Composable
fun M3ECourseAttendanceForecast(
    semesterId: String?,
    half: EmbeddedCourseHalf,
    attendanceTargetPercent: Double,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val domain by AppState.domain.collectAsState()
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val months = remember(domain, semesterId) { publishedMonthsFor(domain, semesterId) }
    val yearByMonth = remember(months) { buildYearByMonth(months) }
    val snapshot = remember(months, yearByMonth, half, now, attendanceTargetPercent) {
        buildCourseForecastSnapshot(half, months, yearByMonth, now, attendanceTargetPercent)
    }
    M3EForecastContent(snapshot = snapshot, modifier = modifier, onBack = onBack)
}

/** Stateless face, deliberately exposed so the empty and real-fixture states are testable. */
@Composable
internal fun M3EForecastContent(
    snapshot: M3ECourseForecastSnapshot?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onSurface,
                    )
                }
            }
            Text(
                text = "Attendance forecast",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
            )

            if (snapshot == null) {
                Text(
                    text = "No attendance data available for this course.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                val hero = snapshot.hero
                // Theory reads blue (primary), lab reads emerald (success) — theme tokens only.
                val accentContainer = if (hero.isLab) colors.successContainer else colors.primaryContainer
                val onAccent = if (hero.isLab) colors.onSuccessContainer else colors.onPrimaryContainer

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ForecastHeroTile(
                        label = "Attendance",
                        value = "${AttendancePredictor.formatPct(hero.currentPct, decimals = false)}%",
                        detail = "${hero.attended}/${hero.total} attended",
                        container = accentContainer,
                        onContainer = onAccent,
                        modifier = Modifier.weight(1f),
                    )
                    ForecastHeroTile(
                        label = "Margin",
                        value = forecastMarginHeadline(hero),
                        detail = "Predicted ${AttendancePredictor.formatPct(hero.predictedPct, decimals = false)}%",
                        container = accentContainer,
                        onContainer = onAccent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Milestones",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
                // Mobile rule: at most two milestone cards on a row.
                snapshot.milestones.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { milestone ->
                            MilestoneTile(
                                label = milestone.label,
                                count = milestone.count,
                                container = colors.surfaceContainerLow,
                                onContainer = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastHeroTile(
    label: String,
    value: String,
    detail: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onContainer)
        Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = onContainer)
        Text(text = detail, fontSize = 11.sp, color = onContainer)
    }
}

@Composable
private fun MilestoneTile(
    label: String,
    count: Int,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 12.sp, color = onContainer, modifier = Modifier.weight(1f))
        // A real zero is printed as "0"; it is never a dash or a placeholder.
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onContainer)
    }
}
