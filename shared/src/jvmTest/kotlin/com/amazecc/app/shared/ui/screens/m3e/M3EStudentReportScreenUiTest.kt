package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportAction
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testStudentReportRendersIdentityAndMetrics() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Student Academic Report").assertExists()
        onNodeWithText("Aravind Kumar").assertExists()
        onNodeWithText("21BCE1042 • B.Tech Computer Science & Engineering").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("91.50%").assertExists()
        onNodeWithText("124 / 160 Credits").assertExists()
        onNodeWithText("Clean Record (No Arrears)").assertExists()
    }

    @Test
    fun testExportPdfClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EStudentReportAction>()

        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Download Comprehensive Report (PDF)").performClick()

        assertTrue(capturedActions.contains(M3EStudentReportAction.ExportPdfReport))
    }

    @Test
    fun testExportJsonClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EStudentReportAction>()

        setContent {
            AmazeM3ETheme {
                M3EStudentReportScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("JSON Data").performClick()

        assertTrue(capturedActions.contains(M3EStudentReportAction.ExportJsonData))
    }
}
