package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.rememberFileSaver

/**
 * Stateful host for the exam schedule subpage. Lead wires App.kt here instead of the legacy
 * bridge function. All work (fetch + mapping) happens off the UI thread through the suspend
 * client; the stateless [M3EExamScheduleScreen] only ever sees immutable snapshot values.
 */
@Composable
fun M3EExamScheduleHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    // The exam schedule has its own semester selector. Reading the academic semester here
    // fetched the wrong papers (usually none) even though exams existed for the exam semester.
    val selectedExamSemId by AppState.selectedExamSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val domain by AppState.domain.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf(ExamSeries.ALL) }
    var scheduleRes by remember { mutableStateOf<ExamScheduleRes?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var selectedExam by remember { mutableStateOf<M3EExamEntry?>(null) }
    var exportNotice by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    // Page entry must not trigger a network sync. The central Sync All button (SyncModule
    // EXAM_SCHEDULE) keeps the cache warm; the screen only reads that cache on entry.
    LaunchedEffect(selectedExamSemId) {
        loadError = null
        scheduleRes = AmazeClient.fallbackCachedExamSchedule(selectedExamSemId)
    }

    // Fetching is strictly user-initiated via the Refresh action.
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        isFetching = true
        loadError = null
        try {
            scheduleRes = AmazeClient.getExamSchedule(selectedExamSemId)
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
    val semesterName = semesterMap[selectedExamSemId]
        ?: domain.academics.semesters[selectedExamSemId]?.name
        ?: selectedExamSemId

    val state = M3EExamScheduleState(
        semesterName = semesterName,
        selectedSeries = selectedSeries,
        nextUpcomingExam = nextUpcoming,
        exams = entries,
        filteredExams = filtered,
        isLoading = isFetching || isLoading,
        // A failed fetch returns a non-null ExamScheduleRes(success=false), so exception-only
        // error detection rendered those failures as "No exams scheduled". Read both sources.
        // Export feedback is informational and passed separately; it must not hide the timeline.
        errorMessage = examScheduleErrorMessage(loadError, scheduleRes),
        groupedEntries = groups,
        availableSeries = availableSeries,
        // ExamItem / ExamScheduleRes expose no hall-ticket URL today. The state field and the
        // OpenHallTicket action exist so the UI can surface one the moment the payload gains it;
        // until then the button is omitted.
        hallTicketUrl = null,
        statusCounts = counts
    )

    /**
     * D-062: every export writes real bytes produced from the exact rows on screen. ICS delegates
     * to the D-040 builder rather than duplicating calendar logic; PNG/PDF come from the pure
     * [M3EExamScheduleExports] writers. Failures are surfaced as informational notices.
     */
    fun exportExams(request: ExamExportRequest) {
        scope.launch {
            isExporting = true
            exportNotice = try {
                val extension = when (request.format) {
                    ExamExportFormat.ICS -> "ics"
                    ExamExportFormat.PDF -> "pdf"
                    ExamExportFormat.PNG -> "png"
                }
                val fileName = M3EExamScheduleExports.fileName(extension, request.seriesLabel)
                val bytes: ByteArray? = when (request.format) {
                    ExamExportFormat.ICS -> buildExamIcs(request.exams)?.toByteArray()
                    ExamExportFormat.PDF -> {
                        // D-149: text PDFs have no per-page engine callback, so only report the
                        // honest generic state while the bytes are built off the UI thread.
                        exportNotice = "Generating PDF…"
                        withContext(Dispatchers.Default) {
                            M3EExamScheduleExports.buildPdf(
                                request.seriesLabel ?: "Exam Schedule", request.exams
                            )
                        }
                    }
                    ExamExportFormat.PNG -> M3EExamScheduleExports.buildPng(
                        request.seriesLabel ?: "Exam Schedule", request.exams
                    )
                }
                when {
                    bytes == null -> "No dated exams are available to export."
                    saveFile(fileName, bytes) -> "${request.format.name} saved to Downloads as $fileName."
                    else -> "Could not save $fileName."
                }
            } catch (e: Exception) {
                "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                isExporting = false
            }
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EExamScheduleScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EExamScheduleAction.SelectSeries -> selectedSeries = action.series
                    is M3EExamScheduleAction.Refresh -> {
                        // Refreshing must act on the exam semester, not the academic one.
                        AppState.selectExamSemester(selectedExamSemId)
                        refreshTick++
                    }
                    is M3EExamScheduleAction.OpenHallTicket -> openExternalUrl(action.url)
                    is M3EExamScheduleAction.ExamClicked -> selectedExam = action.exam
                    M3EExamScheduleAction.ExportCalendar ->
                        exportExams(ExamExportRequest(ExamExportFormat.ICS, null, entries))
                }
            },
            onExport = { request -> exportExams(request) },
            isExporting = isExporting,
            exportNotice = exportNotice,
            modifier = modifier,
            onExamClicked = { exam -> selectedExam = exam }
        )
    }

    if (selectedExam != null) {
        AmazeM3ETheme(isDark = isDark) {
            M3EDismissibleSheet(
                isOpen = true,
                onDismiss = { selectedExam = null },
                title = "${selectedExam?.courseCode} — ${selectedExam?.examSeries?.label}"
            ) {
                val exam = selectedExam
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (exam != null) {
                        Text(exam.courseTitle)
                        Text("Date: ${exam.date}")
                        if (exam.examTime.isNotBlank()) Text("Time: ${exam.examTime}")
                        if (exam.venue.isNotBlank()) Text("Venue: ${exam.venue}")
                        if (exam.seatNumber.isNotBlank()) Text("Seat: ${exam.seatNumber}")
                        if (exam.reportingTime.isNotBlank()) Text("Reporting: ${exam.reportingTime}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { selectedExam = null }) { Text("Close") }
                    }
                }
            }
        }
    }
}

/**
 * Builds a minimal RFC 5545 calendar from real, dated exam entries only. Papers whose date or
 * start time VTOP never published are skipped rather than assigned an invented slot.
 */
internal fun buildExamIcs(entries: List<M3EExamEntry>): String? {
    val events = entries.mapNotNull { entry ->
        val date = M3EExamScheduleComputations.parseDate(entry.date) ?: return@mapNotNull null
        val startMinutes = M3EExamScheduleComputations.timeRange(entry.examTime).first
            ?: M3EExamScheduleComputations.clockMinutes(entry.reportingTime)
            ?: return@mapNotNull null
        val endMinutes = M3EExamScheduleComputations.timeRange(entry.examTime).second
            ?: (startMinutes + M3EExamScheduleComputations.durationMinutes(entry.examSeries.label))
        val stamp = { m: Int -> "${(m / 60).toString().padStart(2, '0')}${(m % 60).toString().padStart(2, '0')}00" }
        val day = "${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}"
        val escape = { value: String -> value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n") }
        val end = if (endMinutes in (startMinutes + 1)..1439) endMinutes else startMinutes + 60
        // RFC 5545: local DATE-TIME values are basic format `YYYYMMDDTHHMMSS`, and
        // DTSTAMP is the UTC creation time with a trailing Z (same as the FFCS exporter).
        val utcNow = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val utcDay = "${utcNow.year}${utcNow.monthNumber.toString().padStart(2, '0')}${utcNow.dayOfMonth.toString().padStart(2, '0')}"
        val dtstamp = "${utcDay}T${utcNow.hour.toString().padStart(2, '0')}${utcNow.minute.toString().padStart(2, '0')}${utcNow.second.toString().padStart(2, '0')}Z"
        listOf(
            "BEGIN:VEVENT",
            "UID:${entry.id}@amazecc",
            "DTSTAMP:$dtstamp",
            "DTSTART:${day}T${stamp(startMinutes)}",
            "DTEND:${day}T${stamp(end)}",
            "SUMMARY:${escape("${entry.courseCode} ${entry.examSeries.label}")}",
            "DESCRIPTION:${escape(entry.courseTitle)}",
            "LOCATION:${escape(entry.venue)}",
            "END:VEVENT"
        ).joinToString("\r\n")
    }
    if (events.isEmpty()) return null
    return listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//AmazeCC//Exam Schedule//EN")
        .plus(events)
        .plus("END:VCALENDAR")
        .joinToString("\r\n")
}

/**
 * Pure seam for the host's error state. An exception is kept in [loadError]; a fetched but
 * unsuccessful payload is non-null, so failure must be read from its `error` / `message` too.
 */
internal fun examScheduleErrorMessage(loadError: String?, scheduleRes: ExamScheduleRes?): String? =
    loadError ?: scheduleRes?.takeIf { !it.success }
        ?.let { it.error ?: it.message ?: "Unable to load exam schedule" }
