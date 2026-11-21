package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3EReportSections
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportAction
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-153: the report is now a normal screen with an M3E app bar and grouped sections.
 * These tests cover the new hierarchy, real values, honest loading, every export action,
 * the bar's back/refresh controls and the More-hub route.
 */
@OptIn(ExperimentalTestApi::class)
class M3EStudentReportScreenUiTest {

    private fun sampleState() = M3EStudentReportState(
        studentName = "Aravind Kumar",
        registrationNumber = "21BCE1042",
        programme = "B.Tech Computer Science & Engineering",
        school = "SCOPE",
        campus = "Vellore",
        proctorName = "Dr. Anita R",
        proctorEmail = "anita.r@vit.ac.in",
        cgpa = 9.24,
        overallAttendancePct = 91.5,
        completedCredits = 124,
        totalRequiredCredits = 160,
        hasArrears = false,
        arrearsCount = 0
    )

    private val reportToggleLabels = listOf(
        "Academic Overview", "Curriculum", "Subject Dossiers", "Grade History",
        "Attendance Register", "OD Usage", "Exam Schedule", "Events", "Finance",
        "Student Services"
    )

    /** SettingsToggleRow publishes "Toggle <title>" as its merged row contentDescription. */
    private fun toggleRow(label: String): SemanticsMatcher =
        SemanticsMatcher("report toggle row '$label'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.contains("Toggle $label") == true
        }

    // Checked state is proven through the emitted ToggleSection(key, enabled) actions in
    // reportSectionTogglesEmitOnlyToggleSectionActions. A M3 Switch is its own semantics node,
    // so ToggleableState is not reliably merged onto the row's labelled node here.

    private val anyReportToggleRow = SemanticsMatcher("any report section toggle row") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { it.startsWith("Toggle ") } == true
    }

    @Test
    fun rendersGroupedSectionsAndRealValues() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(state = sampleState(), onAction = {})
            }
        }

        // Cover block and its honest disclaimer (no generated-at line is fabricated).
        onNodeWithText("Student Academic Report").assertExists()
        onNodeWithText("Not an official transcript. For informational use only.").assertExists()

        // M3E section labels replace the old pile of equal cards.
        onNodeWithText("Student").assertExists()
        onNodeWithText("Performance").assertExists()
        onNodeWithText("Credits & Standing").assertExists()
        // Flat identity rows with real values only. Assert these BEFORE scrolling: the
        // trailing Report Sections + Export items push the cover block out of composition.
        onNodeWithText("Aravind Kumar").assertExists()
        onNodeWithText("21BCE1042").assertExists()
        onNodeWithText("B.Tech Computer Science & Engineering").assertExists()
        onNodeWithText("SCOPE").assertExists()
        onNodeWithText("Vellore").assertExists()

        // M3E stat tiles and credit/standing facts.
        onNodeWithText("9.24").assertExists()
        onNodeWithText("91.50%").assertExists()
        onNodeWithText("124 / 160 Credits").assertExists()
        onNodeWithText("Clean Record (No Arrears)").assertExists()

        // The export section is the last LazyColumn item; it is not composed until scrolled.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export"))
        onNodeWithText("Export").assertExists()
    }

    @Test
    fun everyExportControlEmitsItsRealAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EStudentReportAction>()

        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        // At most two labelled buttons: PDF and JSON. The other two are icon buttons.
        // Compose only materialises the trailing LazyColumn items after a scroll.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export"))
        onNodeWithText("Download Comprehensive Report (PDF)").performClick()
        onNodeWithText("JSON Data").performClick()
        onNodeWithContentDescription("Export HTML report").performClick()
        onNodeWithContentDescription("Share report").performClick()

        assertTrue(capturedActions.contains(M3EStudentReportAction.ExportPdfReport))
        assertTrue(capturedActions.contains(M3EStudentReportAction.ExportJsonData))
        assertTrue(capturedActions.contains(M3EStudentReportAction.ExportHtmlReport))
        assertTrue(capturedActions.contains(M3EStudentReportAction.ShareReport))
    }

    @Test
    fun loadingStateIsAnHonestSkeletonWithNoInventedNumbers() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState().copy(isLoading = true),
                    onAction = {}
                )
            }
        }

        // M3EPageSkeleton publishes its label on the merged root and on its list skeleton
        // (same contract pinned for circulars). Exactly those two, and no data rows.
        onAllNodesWithContentDescription("Loading academic report").assertCountEquals(2)
        onNodeWithText("9.24").assertDoesNotExist()
        onNodeWithText("91.50%").assertDoesNotExist()
        onNodeWithText("124 / 160 Credits").assertDoesNotExist()
    }

    @Test
    fun appBarBackAndRefreshAreRealControls() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EStudentReportAction>()
        var backClicks = 0

        setContent {
            AmazeM3ETheme {
                M3EStudentReportContent(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) },
                    onBack = { backClicks++ }
                )
            }
        }

        onNodeWithText("Academic Report").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithContentDescription("Refresh").performClick()

        assertEquals(1, backClicks)
        assertTrue(capturedActions.contains(M3EStudentReportAction.Refresh))
    }

    @Test
    fun appBarRefreshActuallyTriggersTheReportDataSync() = runComposeUiTest {
        var syncCount = 0

        setContent {
            AmazeM3ETheme {
                M3EStudentReportHost(onRefreshReportData = { syncCount++ })
            }
        }

        // The bar's Refresh must reach the report-data re-sync, not a conditional no-op.
        onNodeWithContentDescription("Refresh").performClick()
        assertEquals(1, syncCount)
    }

    @Test
    fun moreHubHasAReachableAcademicReportTile() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMoreHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = M3EMoreHubState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithTag("more_hub_list").performScrollToNode(hasText("Academic Report"))
        onNodeWithText("Academic Report").performClick()

        assertTrue(
            capturedActions.any { action ->
                action is M3EMoreHubAction.OpenSubScreen && action.screen == M3ESubScreen.STUDENT_REPORT
            }
        )
    }

    @Test
    fun reportRouteIsRegisteredInTheNavigationEnums() {
        assertEquals("STUDENT_REPORT", Screen.STUDENT_REPORT.name)
        assertEquals("Academic Report", M3ESubScreen.STUDENT_REPORT.label)
        assertEquals("Academics", M3ESubScreen.STUDENT_REPORT.category)
    }

    @Test
    fun reportSectionsGroupRendersAllTenLabelledToggles() =
        runComposeUiTest {
            setContent {
                AmazeM3ETheme {
                    M3EStudentReportScreen(state = sampleState(), onAction = {})
                }
            }

            // The group sits after "Credits & Standing"; Export is the trailing item, so
            // scroll the new item into view before asserting its rows.
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Report Sections"))
            onNodeWithText("Report Sections").assertExists()

            // Exactly one full-width toggle row per section key, in stable keys() order.
            onAllNodes(anyReportToggleRow).assertCountEquals(10)
            reportToggleLabels.forEach { label ->
                onNode(hasScrollToNodeAction())
                    .performScrollToNode(hasContentDescription("Toggle $label"))
                onNode(toggleRow(label)).assertExists()
            }
        }

    @Test
    fun reportSectionTogglesEmitOnlyToggleSectionActions() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EStudentReportAction>()
        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState().copy(sections = M3EReportSections(od = false)),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Report Sections"))

        // Both labelled rows are present; their bound state is proven by the actions below.
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasContentDescription("Toggle Grade History"))
        onNode(toggleRow("Grade History")).assertExists()
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasContentDescription("Toggle OD Usage"))
        onNode(toggleRow("OD Usage")).assertExists()

        // history starts checked=true -> clicking emits ToggleSection("history", false).
        onNodeWithContentDescription("Toggle Grade History").performClick()
        // od starts checked=false -> clicking emits ToggleSection("od", true).
        onNodeWithContentDescription("Toggle OD Usage").performClick()

        assertTrue(capturedActions.contains(M3EStudentReportAction.ToggleSection("history", false)))
        assertTrue(capturedActions.contains(M3EStudentReportAction.ToggleSection("od", true)))
        // No accidental export side effect: every emitted action is a section toggle.
        assertEquals(2, capturedActions.size)
        assertTrue(capturedActions.all { it is M3EStudentReportAction.ToggleSection })
    }
}
