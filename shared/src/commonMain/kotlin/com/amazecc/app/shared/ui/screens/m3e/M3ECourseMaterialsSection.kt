package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.CachedCourseMaterialRow
import com.amazecc.app.shared.domain.CachedCoursePage
import com.amazecc.app.shared.domain.CoursePageCache
import com.amazecc.app.shared.domain.coursePageCacheAge
import com.amazecc.app.shared.domain.parseCoursePageRows
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * D-170: the Kotlin face of the web `vtop` course-materials tab.
 *
 * Cache-first, like every fetched surface in this app: on composition the single persisted
 * [CachedCoursePage] is read and rendered immediately when it matches this semester + course.
 * There is deliberately **no fetch on mount** — the user presses Reload. A failed reload keeps
 * the cached rows on screen behind an honest banner rather than replacing them with an error.
 * The Kotlin client can fetch the course page but cannot download material files yet, so there
 * are no download buttons and no invented filenames, dates or attachment counts.
 */
@Composable
fun M3ECourseMaterialsSection(
    semesterId: String?,
    courseCode: String,
    modifier: Modifier = Modifier,
) {
    var entry by remember(semesterId, courseCode) {
        mutableStateOf(CoursePageCache.read(semesterId, courseCode))
    }
    var errorMessage by remember(semesterId, courseCode) { mutableStateOf<String?>(null) }
    var isLoading by remember(semesterId, courseCode) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    M3ECourseMaterialsContent(
        entry = entry,
        errorMessage = errorMessage,
        isLoading = isLoading,
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        onReload = {
            scope.launch {
                isLoading = true
                errorMessage = null
                val rows = withContext(Dispatchers.Default) {
                    AmazeClient.getCoursePage(courseCode, semesterId)?.let(::parseCoursePageRows)
                }
                if (rows == null) {
                    errorMessage = if (entry != null) {
                        "Reload failed — showing cached data"
                    } else {
                        "Could not load the course page from VTOP."
                    }
                } else {
                    val fresh = CachedCoursePage(
                        semesterId = semesterId,
                        courseCode = courseCode,
                        fetchedAt = Clock.System.now().toEpochMilliseconds(),
                        rows = rows,
                    )
                    CoursePageCache.write(fresh)
                    entry = fresh
                }
                isLoading = false
            }
        },
        modifier = modifier,
    )
}

/** Stateless face: cache entry + real error state in, UI out. Exposed for JVM tests. */
@Composable
internal fun M3ECourseMaterialsContent(
    entry: CachedCoursePage?,
    errorMessage: String?,
    isLoading: Boolean,
    nowMillis: Long,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Course Materials",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                    )
                    if (entry != null) {
                        // Age is computed from the real fetchedAt stamp, never a freshness claim.
                        Text(
                            text = "Cached · ${coursePageCacheAge(entry.fetchedAt, nowMillis)}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Button(onClick = onReload, enabled = !isLoading) {
                    Text(if (isLoading) "Reloading..." else "Reload")
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.error,
                )
            }

            when {
                entry == null -> {
                    Text(
                        text = "Not fetched yet",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                    )
                    Text(
                        text = "Tap Reload to pull the course page from VTOP.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }

                entry.rows.isEmpty() -> {
                    Text(
                        text = "No materials published for this course.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MaterialsStatCard(
                            label = "Sessions",
                            value = entry.rows.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MaterialsStatCard(
                            label = "Materials",
                            value = entry.rows.sumOf { it.materials.size }.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    entry.rows.forEachIndexed { index, row ->
                        CourseMaterialRowCard(row = row, sessionNumber = index + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialsStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
    }
}

@Composable
private fun CourseMaterialRowCard(row: CachedCourseMaterialRow, sessionNumber: Int) {
    val colors = M3ETheme.colors
    val dateSlot = listOfNotNull(
        row.date.takeIf(String::isNotBlank),
        row.slot.takeIf(String::isNotBlank),
    ).joinToString(" · ")
    val labels = row.materials.mapNotNull { it.label.takeIf(String::isNotBlank) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = row.topic.ifBlank { "Session $sessionNumber" },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Text(
            text = dateSlot.ifBlank { "Date not published" },
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = "${row.materials.size} material${if (row.materials.size == 1) "" else "s"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )
        if (labels.isNotEmpty()) {
            Text(text = labels.joinToString(", "), fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}
