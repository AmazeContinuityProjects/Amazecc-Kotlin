package com.amazecc.app.shared.ui.screens.m3e

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EFfcsBlockedSlotUi
import com.amazecc.app.shared.domain.overhaul.M3EFfcsFacultyPriorityUi
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRankingFactor
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRankingMode
import com.amazecc.app.shared.domain.overhaul.M3EFfcsRetryUi
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the FFCS planner. */
enum class M3EFfcsTab(val label: String) {
    COURSES("Courses"), TIMETABLE("Timetable"), RESULTS("Results")
}

/** One selectable course offering (collapsed by course code). */
@Immutable
data class M3EFfcsCourse(
    val code: String,
    val title: String,
    val credits: String,
    val offeringCount: Int,
    val isSelected: Boolean = false
)

/** A generated, clash-free timetable option. */
@Immutable
data class M3EFfcsResult(val id: String, val name: String, val summary: String)

/** Immutable, stateless state for [M3EFfcsPlannerScreen]. */
@Immutable
data class M3EFfcsPlannerState(
    val activeTab: M3EFfcsTab = M3EFfcsTab.COURSES,
    val courses: List<M3EFfcsCourse> = emptyList(),
    val filteredCourses: List<M3EFfcsCourse> = emptyList(),
    val searchQuery: String = "",
    val selectedCount: Int = 0,
    val results: List<M3EFfcsResult> = emptyList(),
    val selectedResultId: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    /** Close Gaps / Late Start / Early Finish generator filters (web parity: FFCS preferences). */
    val closeGaps: Boolean = false,
    val lateStart: Boolean = false,
    val earlyFinish: Boolean = false,
    /** Search text over generated variant results (web parity: variantSearchQuery). */
    val resultsSearchQuery: String = "",
    /** Host-owned notice shown under the export control. */
    val exportNotice: String? = null,
    // ── D-184 real capability controls (null / empty means "not requested") ──
    /** Null = no ranking profile requested; the typed engine keeps its un-ranked path. */
    val rankingMode: M3EFfcsRankingMode? = null,
    /** Only the factors the user actually enabled; empty = no factor ranking. */
    val enabledRankingFactors: Set<M3EFfcsRankingFactor> = emptySet(),
    /** Per-course faculty preference, best first. */
    val facultyPriority: List<M3EFfcsFacultyPriorityUi> = emptyList(),
    /** Draft text for the course code whose faculty priority is being edited. */
    val priorityCourseInput: String = "",
    /** Draft text for the faculty name being added to that course's priority. */
    val priorityFacultyInput: String = "",
    /** Real blocked offering keys from the ViewModel. */
    val blockedSlots: List<M3EFfcsBlockedSlotUi> = emptyList(),
    /** Draft text for a new offering key to block. */
    val blockedSlotInput: String = "",
    /** Drop-ladder opt-in and the outcome of the last real attempt. */
    val dropRetry: M3EFfcsRetryUi = M3EFfcsRetryUi()
)

/** User intents emitted by [M3EFfcsPlannerScreen]. */
sealed interface M3EFfcsPlannerAction {
    data class SelectTab(val tab: M3EFfcsTab) : M3EFfcsPlannerAction
    data class SearchQueryChanged(val query: String) : M3EFfcsPlannerAction
    data class ToggleCourse(val code: String) : M3EFfcsPlannerAction
    data object Generate : M3EFfcsPlannerAction
    data class SelectResult(val id: String) : M3EFfcsPlannerAction
    data class SetCloseGaps(val enabled: Boolean) : M3EFfcsPlannerAction
    data class SetLateStart(val enabled: Boolean) : M3EFfcsPlannerAction
    data class SetEarlyFinish(val enabled: Boolean) : M3EFfcsPlannerAction
    data class SetResultsSearchQuery(val query: String) : M3EFfcsPlannerAction
    data object ExportIcs : M3EFfcsPlannerAction
    data object ExportHtmlDossier : M3EFfcsPlannerAction
    data object Refresh : M3EFfcsPlannerAction
    data object Back : M3EFfcsPlannerAction
    // ── D-184 ──
    /** Cycle to the next real ranking profile (Default → the six web profiles). */
    data object CycleRankingMode : M3EFfcsPlannerAction
    data class ToggleRankingFactor(val factor: M3EFfcsRankingFactor) : M3EFfcsPlannerAction
    data class SetPriorityCourseInput(val text: String) : M3EFfcsPlannerAction
    data class SetPriorityFacultyInput(val text: String) : M3EFfcsPlannerAction
    data class AddFacultyPriority(val code: String, val faculty: String) : M3EFfcsPlannerAction
    data class PromoteFaculty(val code: String, val faculty: String) : M3EFfcsPlannerAction
    data class ClearFacultyPriority(val code: String?) : M3EFfcsPlannerAction
    data class SetBlockedSlotInput(val text: String) : M3EFfcsPlannerAction
    data class AddBlockedSlot(val key: String) : M3EFfcsPlannerAction
    data object ClearBlockedSlots : M3EFfcsPlannerAction
    data class SetDropRetryEnabled(val enabled: Boolean) : M3EFfcsPlannerAction
    data object RetryWithDrop : M3EFfcsPlannerAction
}

/**
 * Material 3 Expressive FFCS Planner — pick courses, generate clash-free timetables.
 * 100% stateless: accepts [M3EFfcsPlannerState], emits [M3EFfcsPlannerAction].
 */
@Composable
fun M3EFfcsPlannerScreen(
    state: M3EFfcsPlannerState,
    onAction: (M3EFfcsPlannerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FFCS Planner", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Pick courses → clash-free timetables", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3EFfcsPlannerAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3EFfcsTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3EFfcsPlannerAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            when (state.activeTab) {
                M3EFfcsTab.COURSES -> {
                    item(key = "selected_banner") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.School, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${state.selectedCount} selected", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                                    Text("Courses chosen for generation", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item(key = "search") {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(M3EFfcsPlannerAction.SearchQueryChanged(it)) },
                            placeholder = { Text("Search code, title or faculty") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "filters") {
                        M3EFfcsFilterChips(
                            closeGaps = state.closeGaps,
                            lateStart = state.lateStart,
                            earlyFinish = state.earlyFinish,
                            onAction = onAction
                        )
                    }
                    item(key = "ranking") {
                        M3EFfcsRankingControls(
                            mode = state.rankingMode,
                            enabledFactors = state.enabledRankingFactors,
                            onAction = onAction
                        )
                    }
                    item(key = "faculty_priority") {
                        M3EFfcsFacultyPriorityControls(
                            priorities = state.facultyPriority,
                            courseInput = state.priorityCourseInput,
                            facultyInput = state.priorityFacultyInput,
                            onAction = onAction
                        )
                    }
                    item(key = "blocked_slots") {
                        M3EFfcsBlockedSlotControls(
                            blockedSlots = state.blockedSlots,
                            input = state.blockedSlotInput,
                            onAction = onAction
                        )
                    }
                    if (state.isLoading) {
                        item(key = "loading") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("Loading course catalog...", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.filteredCourses, key = { it.code }) { course ->
                            M3ESurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerLevel = if (course.isSelected) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.STANDARD,
                                borderColor = if (course.isSelected) colors.primary else null,
                                onClick = { onAction(M3EFfcsPlannerAction.ToggleCourse(course.code)) }
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(CircleShape).background(if (course.isSelected) colors.primary else colors.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (course.isSelected) {
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${course.code} · ${course.credits} cr", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                        Text(course.title, fontSize = 13.sp, color = colors.onSurface, maxLines = 2)
                                        Text("${course.offeringCount} offering${if (course.offeringCount == 1) "" else "s"}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                M3EFfcsTab.TIMETABLE -> {
                    item(key = "timetable_hint") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Timetable view", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Text(
                                    "Select courses, then generate to see ranked clash-free grids here.",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                M3EFfcsTab.RESULTS -> {
                    item(key = "generate") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            borderColor = colors.primary,
                            onClick = { onAction(M3EFfcsPlannerAction.Generate) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.primary)
                                Text(
                                    if (state.isGenerating) "Generating options..." else "Generate clash-free options",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                    item(key = "drop_retry") {
                        M3EFfcsDropRetryControls(dropRetry = state.dropRetry, onAction = onAction)
                    }
                    item(key = "results_search") {
                        OutlinedTextField(
                            value = state.resultsSearchQuery,
                            onValueChange = { onAction(M3EFfcsPlannerAction.SetResultsSearchQuery(it)) },
                            placeholder = { Text("Search variants") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "export_ics") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3EFfcsPlannerAction.ExportIcs) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FileDownload,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Export ICS",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                    item(key = "export_html_dossier") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3EFfcsPlannerAction.ExportHtmlDossier) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FileDownload,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Export HTML dossier",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                    state.exportNotice?.let { notice ->
                        item(key = "export_notice") {
                            M3ESurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerLevel = SurfaceContainerLevel.LOW
                            ) {
                                Text(notice, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.errorMessage != null) {
                        item(key = "error") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW, borderColor = colors.error) {
                                Text(state.errorMessage ?: "Something went wrong", fontSize = 13.sp, color = colors.error)
                            }
                        }
                    }
                    if (state.results.isEmpty()) {
                        item(key = "no_results") {
                            M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                                Text("No options generated yet.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.results, key = { it.id }) { result ->
                            val selected = result.id == state.selectedResultId
                            M3ESurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerLevel = if (selected) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.STANDARD,
                                borderColor = if (selected) colors.primary else null,
                                onClick = { onAction(M3EFfcsPlannerAction.SelectResult(result.id)) }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(result.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                    Text(result.summary, fontSize = 12.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

/** Close Gaps / Late Start / Early Finish generator filter toggles (web parity). */
@Composable
private fun M3EFfcsFilterChips(
    closeGaps: Boolean,
    lateStart: Boolean,
    earlyFinish: Boolean,
    onAction: (M3EFfcsPlannerAction) -> Unit
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Filters",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant
        )
        FilterChip(
            selected = closeGaps,
            onClick = { onAction(M3EFfcsPlannerAction.SetCloseGaps(!closeGaps)) },
            label = { Text("Close Gaps", fontSize = 11.sp) }
        )
        FilterChip(
            selected = lateStart,
            onClick = { onAction(M3EFfcsPlannerAction.SetLateStart(!lateStart)) },
            label = { Text("Late Start", fontSize = 11.sp) }
        )
        FilterChip(
            selected = earlyFinish,
            onClick = { onAction(M3EFfcsPlannerAction.SetEarlyFinish(!earlyFinish)) },
            label = { Text("Early Finish", fontSize = 11.sp) }
        )
    }
}

/** Ranking profile + ordered factor chips. Every control emits a real action. */
@Composable
private fun M3EFfcsRankingControls(
    mode: M3EFfcsRankingMode?,
    enabledFactors: Set<M3EFfcsRankingFactor>,
    onAction: (M3EFfcsPlannerAction) -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ranking", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onAction(M3EFfcsPlannerAction.CycleRankingMode) }) {
                    Text("Ranking: ${mode?.label ?: "Default"}", fontSize = 12.sp)
                }
            }
            Text("Factors (position = weight)", fontSize = 11.sp, color = colors.onSurfaceVariant)
            val factors = M3EFfcsRankingFactor.entries
            factors.chunked(4).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chunk.forEach { factor ->
                        FilterChip(
                            selected = factor in enabledFactors,
                            onClick = { onAction(M3EFfcsPlannerAction.ToggleRankingFactor(factor)) },
                            label = { Text(factor.label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    }
}

/** Per-course faculty preference. Empty priority means no preference is sent to the engine. */
@Composable
private fun M3EFfcsFacultyPriorityControls(
    priorities: List<M3EFfcsFacultyPriorityUi>,
    courseInput: String,
    facultyInput: String,
    onAction: (M3EFfcsPlannerAction) -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Faculty priority", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = courseInput,
                    onValueChange = { onAction(M3EFfcsPlannerAction.SetPriorityCourseInput(it)) },
                    placeholder = { Text("Course code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = facultyInput,
                    onValueChange = { onAction(M3EFfcsPlannerAction.SetPriorityFacultyInput(it)) },
                    placeholder = { Text("Faculty name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (courseInput.isNotBlank() && facultyInput.isNotBlank()) {
                        onAction(M3EFfcsPlannerAction.AddFacultyPriority(courseInput.trim(), facultyInput.trim()))
                    }
                }) { Text("Add priority", fontSize = 12.sp) }
                OutlinedButton(onClick = { onAction(M3EFfcsPlannerAction.ClearFacultyPriority(null)) }) {
                    Text("Clear priorities", fontSize = 12.sp)
                }
            }
            priorities.forEach { entry ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("${entry.code}: ${entry.orderedFaculty.joinToString(" > ")}", fontSize = 11.sp, color = colors.onSurface)
                    if (entry.orderedFaculty.size > 1) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = {
                                onAction(M3EFfcsPlannerAction.PromoteFaculty(entry.code, entry.orderedFaculty[1]))
                            }) { Text("Raise ${entry.orderedFaculty[1]}", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** Blocked offering keys already held by the ViewModel, plus a real add/clear affordance. */
@Composable
private fun M3EFfcsBlockedSlotControls(
    blockedSlots: List<M3EFfcsBlockedSlotUi>,
    input: String,
    onAction: (M3EFfcsPlannerAction) -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blocked slots", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { onAction(M3EFfcsPlannerAction.SetBlockedSlotInput(it)) },
                    placeholder = { Text("FACULTY|SLOT|ROOM") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (input.isNotBlank()) onAction(M3EFfcsPlannerAction.AddBlockedSlot(input.trim()))
                }) { Text("Block slot", fontSize = 12.sp) }
            }
            if (blockedSlots.isEmpty()) {
                Text("No slots blocked.", fontSize = 11.sp, color = colors.onSurfaceVariant)
            } else {
                blockedSlots.forEach { blocked ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(blocked.label, fontSize = 11.sp, color = colors.onSurface)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onAction(M3EFfcsPlannerAction.ClearBlockedSlots) }) {
                        Text("Clear blocked", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Drop-ladder opt-in and a retry that is only offered after a real retryable failure. */
@Composable
private fun M3EFfcsDropRetryControls(
    dropRetry: M3EFfcsRetryUi,
    onAction: (M3EFfcsPlannerAction) -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Drop ladder", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(
                "Retry without the least-preferred course when a week cannot fit. Locked courses are never dropped.",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onAction(M3EFfcsPlannerAction.SetDropRetryEnabled(!dropRetry.enabled)) }) {
                    Text(if (dropRetry.enabled) "Drop retry: On" else "Drop retry: Off", fontSize = 12.sp)
                }
                if (dropRetry.canRetry) {
                    OutlinedButton(onClick = { onAction(M3EFfcsPlannerAction.RetryWithDrop) }) {
                        Text("Retry with drop", fontSize = 12.sp)
                    }
                }
            }
            if (dropRetry.droppedCodes.isNotEmpty()) {
                Text(
                    "Left out on the last attempt: ${dropRetry.droppedCodes.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

private fun mockFfcsState() = M3EFfcsPlannerState(
    courses = listOf(
        M3EFfcsCourse("CSE3002", "Internet and Web Programming", "4", 3, true),
        M3EFfcsCourse("MAT2001", "Complex Variables & Linear Algebra", "4", 2)
    ),
    filteredCourses = listOf(
        M3EFfcsCourse("CSE3002", "Internet and Web Programming", "4", 3, true),
        M3EFfcsCourse("MAT2001", "Complex Variables & Linear Algebra", "4", 2)
    ),
    selectedCount = 1,
    results = listOf(M3EFfcsResult("opt_1", "Option 1", "2 half-days · 3 gaps · 0 clashes"))
)

@Preview
@Composable
private fun M3EFfcsPlannerScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EFfcsPlannerScreen(state = mockFfcsState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EFfcsPlannerScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EFfcsPlannerScreen(state = mockFfcsState(), onAction = {}) }
}
