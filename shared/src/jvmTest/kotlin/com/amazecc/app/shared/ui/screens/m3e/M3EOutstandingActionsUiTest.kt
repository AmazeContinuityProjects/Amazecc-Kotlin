package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamStatus
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.ui.m3e.components.M3ESyncNotification
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** D-036/D-062/D-063/D-066/D-067: headless checks for the newly wired interactions. */
@OptIn(ExperimentalTestApi::class)
class M3EOutstandingActionsUiTest {

    private fun exam(
        code: String = "CSE3002",
        series: ExamSeries = ExamSeries.CAT1,
        status: ExamStatus = ExamStatus.UPCOMING
    ) = M3EExamEntry(
        id = "exam-$code",
        courseCode = code,
        courseTitle = "Internet and Web Programming",
        examSeries = series,
        date = "19-Nov-2026",
        day = "Thursday",
        session = "FN",
        slot = "B1",
        reportingTime = "09:00 AM",
        examTime = "09:15 AM - 12:30 PM",
        venue = "SJT 101",
        seatNumber = "A12",
        status = status
    )

    private fun examState() = M3EExamScheduleState(
        semesterName = "Fall 2026-27",
        exams = listOf(exam(), exam("CSE3003", ExamSeries.FAT)),
        filteredExams = listOf(exam(), exam("CSE3003", ExamSeries.FAT)),
        availableSeries = listOf(ExamSeries.ALL, ExamSeries.CAT1, ExamSeries.FAT)
    )

    @BeforeTest
    fun setUp() {
        SyncEngine.resetAllStates()
        SyncEngine.setShowSyncDialog(false, minimized = false)
        AppState.clearServedFromCache()
    }

    @AfterTest
    fun tearDown() {
        SyncEngine.setShowSyncDialog(false, minimized = false)
        AppState.clearServedFromCache()
    }

    @Test
    fun examHeaderEmitsRealExportRequests() = runComposeUiTest {
        val requests = mutableListOf<ExamExportRequest>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = examState(), onAction = {}, onExport = { requests.add(it) })
            }
        }

        onNodeWithContentDescription("Download exam schedule as PNG").performClick()
        onNodeWithContentDescription("Download exam schedule as PDF").performClick()
        onNodeWithContentDescription("Export to Calendar").assertExists()
        onNodeWithContentDescription("Copy course codes").assertExists()

        assertEquals(2, requests.size)
        assertEquals(ExamExportFormat.PNG, requests[0].format)
        assertEquals(ExamExportFormat.PDF, requests[1].format)
        assertEquals(2, requests[0].exams.size)
    }

    @Test
    fun perSeriesExportCarriesOnlyThatSeries() = runComposeUiTest {
        val requests = mutableListOf<ExamExportRequest>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = examState(), onAction = {}, onExport = { requests.add(it) })
            }
        }

        onAllNodesWithText("Add to Calendar")[0].performClick()

        val request = requests.single()
        assertEquals(ExamExportFormat.ICS, request.format)
        assertEquals("CAT-1", request.seriesLabel)
        assertEquals(listOf("CSE3002"), request.exams.map { it.courseCode })
    }

    @Test
    fun sortAndStatusFiltersAreInteractive() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = examState(), onAction = {}, onExport = {})
            }
        }

        onNodeWithText("By course code").performClick()
        // Target the filter chip by tag: "Completed" also appears as an exam status badge.
        onNodeWithTag("exam_status_filter_COMPLETED", useUnmergedTree = true).performClick()
        onNodeWithText("No completed exams in All Exams").assertExists()
    }

    @Test
    fun pdfAndPngContainRealExamData() {
        val entries = listOf(exam())
        val pdf = M3EExamScheduleExports.buildPdf("Exam Schedule", entries)
        assertTrue(pdf.decodeToString().startsWith("%PDF-1.4"))
        assertTrue(pdf.decodeToString().contains("CSE3002"))
        assertTrue(pdf.decodeToString().endsWith("%%EOF\n"))

        val png = M3EExamScheduleExports.buildPng("Exam Schedule", entries)
        assertTrue(png.take(8).toByteArray().contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        ))
        val asAscii = png.decodeToString()
        assertTrue(asAscii.contains("IHDR"))
        assertTrue(asAscii.contains("IDAT"))
        assertTrue(asAscii.contains("IEND"))
    }

    @Test
    fun perSeriesIcsReusesD040Builder() {
        val cat = exam()
        val fat = exam("CSE3003", ExamSeries.FAT)
        val ics = buildExamIcs(listOf(cat))
        assertNotNull(ics)
        assertTrue(ics.contains("SUMMARY:CSE3002 CAT-1"))
        assertTrue(!ics.contains("CSE3003"))
    }

    @Test
    fun catalogShowsRealPagingState() = runComposeUiTest {
        val results = (1..20).map { M3ELibraryBook(id = "$it", title = "Catalog book $it") }
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = M3ELibrariesState(
                        activeTab = M3ELibraryTab.CATALOG,
                        searchQuery = "kotlin",
                        hasSearched = true,
                        searchResults = results,
                        totalResults = 45
                    ),
                    onAction = {}
                )
            }
        }

        // The pager sits below 20 result cards in a LazyColumn; scroll to the real pager item
        // before asserting its state instead of expecting an off-screen node to be composed.
        onNodeWithText("20 of 45 results").assertExists()
        onNodeWithText("↑↓ navigate · Enter open").assertExists()
        onNodeWithTag("library_list").performScrollToNode(hasText("Page 1 of 3"))
        onNodeWithText("Page 1 of 3").assertExists()
        onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        onNodeWithContentDescription("Next page").assertExists()
    }

    @Test
    fun connectionBannerSurfacesCachedDataAndDismisses() = runComposeUiTest {
        AppState.markServedFromCache("Attendance")
        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = {})
            }
        }

        onNodeWithText("You're offline.").assertExists()
        onNodeWithText("Serving Attendance from local cache").assertExists()
        onNodeWithContentDescription("Dismiss connection notice").performClick()
        onNodeWithText("You're offline.").assertDoesNotExist()
    }

    @Test
    fun syncSheetExposesLiveApiTargetPicker() = runComposeUiTest {
        SyncEngine.setShowSyncDialog(true, minimized = false)
        setContent {
            AmazeM3ETheme {
                M3ESyncNotification(onDismiss = {})
            }
        }

        onNodeWithText("API target").assertExists()
        onNodeWithText("Primary (recommended)").assertExists()
        onNodeWithText("Backup (legacy mirror)").assertExists()
        onNodeWithText("Custom URL").assertExists()
    }
}
