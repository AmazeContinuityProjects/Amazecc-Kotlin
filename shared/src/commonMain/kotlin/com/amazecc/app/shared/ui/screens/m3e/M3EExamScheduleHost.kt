package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.openExternalUrl

/**
 * Stateful host for the exam schedule subpage. Lead wires App.kt here instead of the legacy
 * bridge function. All work (fetch + mapping) happens off the UI thread through the suspend
 * client; the stateless [M3EExamScheduleScreen] only ever sees immutable snapshot values.
 */
@Composable
fun M3EExamScheduleHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val domain by AppState.domain.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf(ExamSeries.ALL) }
    var scheduleRes by remember { mutableStateOf<ExamScheduleRes?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    // The raw schedule map is the only place exam *series* survive — the domain snapshot flattens
    // it away. Fetch it directly; AppState's sync path keeps the shared cache warm in parallel.
    LaunchedEffect(selectedSemId, refreshTick) {
        isFetching = true
        loadError = null
        try {
            scheduleRes = AmazeClient.getExamSchedule(selectedSemId)
        } catch (e: Exception) {
            loadError = e.message ?: "Unable to load exam schedule"
            scheduleRes = null
        }
        isFetching = false
    }

    val entries = remember(scheduleRes) {
        scheduleRes?.let { M3EExamScheduleComputations.buildEntries(it) }.orEmpty()
    }
    val availableSeries = remember(scheduleRes) {
        M3EExamScheduleComputations.availableSeries(scheduleRes?.schedule?.keys.orEmpty())
    }

    // A stale filter must not leave the user staring at an empty page after a refresh swaps series.
    LaunchedEffect(availableSeries) {
        if (selectedSeries !in availableSeries) selectedSeries = ExamSeries.ALL
    }

    val filtered = remember(entries, selectedSeries) {
        M3EExamScheduleComputations.filterBySeries(entries, selectedSeries)
    }
    val groups = remember(filtered) { M3EExamScheduleComputations.groupBySeries(filtered) }
    val counts = remember(filtered) { M3EExamScheduleComputations.statusCounts(filtered) }
    val nextUpcoming = remember(filtered) { M3EExamScheduleComputations.nextUpcoming(filtered) }

    // DomainSnapshot carries the human-readable semester name; fall back through the id map.
    val semesterName = semesterMap[selectedSemId]
        ?: domain.academics.semesters[selectedSemId]?.name
        ?: selectedSemId

    val state = M3EExamScheduleState(
        semesterName = semesterName,
        selectedSeries = selectedSeries,
        nextUpcomingExam = nextUpcoming,
        exams = entries,
        filteredExams = filtered,
        isLoading = isFetching || isLoading,
        errorMessage = loadError?.takeIf { scheduleRes == null },
        groupedEntries = groups,
        availableSeries = availableSeries,
        // ExamItem / ExamScheduleRes expose no hall-ticket URL today. The state field and the
        // OpenHallTicket action exist so the UI can surface one the moment the payload gains it;
        // until then the button is omitted.
        hallTicketUrl = null,
        statusCounts = counts
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EExamScheduleScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EExamScheduleAction.SelectSeries -> selectedSeries = action.series
                    is M3EExamScheduleAction.Refresh -> {
                        AppState.selectExamSemester(selectedSemId)
                        refreshTick++
                    }
                    is M3EExamScheduleAction.OpenHallTicket -> openExternalUrl(action.url)
                    is M3EExamScheduleAction.ExamClicked -> Unit
                    M3EExamScheduleAction.ExportCalendar -> Unit
                }
            },
            modifier = modifier
        )
    }
}
