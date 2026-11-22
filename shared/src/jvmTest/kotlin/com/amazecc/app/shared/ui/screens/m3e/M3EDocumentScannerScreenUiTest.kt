package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.PaperInference
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.suggestedDocumentName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-009 — deterministic tests for the stateless scanner and the crash-regression host.
 *
 * No launchers, coroutines or file IO run here: each test feeds a real-shaped immutable
 * snapshot and asserts only the emitted [M3EDocumentScannerAction]s and honest copy.
 */
@OptIn(ExperimentalTestApi::class)
class M3EDocumentScannerScreenUiTest {

    private fun sampleState() = M3EDocumentScannerState(
        // JPEG SOI + APP0 marker: real shape, and isJpeg() agrees.
        imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
        crop = DocumentScannerPdf.Crop(0.1f, 0.1f, 0.9f, 0.9f),
        title = "Semester receipt",
        note = "Paid on 12 Aug",
        base64Input = "/9j/4AAQSkZJRg==",
        status = "Image loaded (640×480). Drag the handles to crop.",
    )

    /** Real-shaped output of inferPaperDetails: every field is a genuine extracted value. */
    private fun fullInference() = PaperInference(
        assessmentType = "CIA-1",
        subject = "Database Systems",
        subjectCode = "CSE301",
        classCodes = listOf("CSE-A", "CSE-B"),
        faculties = listOf("Dr. Rao", "Dr. Mehta"),
        documentDate = "2026-08-12",
        suggestedName = "CSE301-CIA-1-2026-08-12",
        insights = listOf("Printed question paper", "Two class sections listed"),
        confidence = 0.86f,
    )

    /** Unsupported/partial inference: no fabricated values, only the required suggestedName. */
    private fun bareInference() = PaperInference(suggestedName = "Scanned-Document")

    @Test
    fun captureControlsExistAndEmitTheirActions() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentScannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Choose photo from gallery").assertExists()
        onNodeWithText("Take photo").assertExists()

        onNodeWithText("Choose photo from gallery").performClick()
        onNodeWithText("Take photo").performClick()

        assertEquals(2, actions.size)
        assertTrue(actions[0] is M3EDocumentScannerAction.PickFromGallery)
        assertTrue(actions[1] is M3EDocumentScannerAction.TakePhoto)
    }

    @Test
    fun pastedBase64EmitsUpdateAndLoadPasted() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentScannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(base64Input = ""),
                    onAction = { actions.add(it) },
                )
            }
        }

        onNodeWithTag(SCANNER_BASE64_FIELD_TAG).performTextInput("/9j/4AAQSkZJRg==")
        onNodeWithText("Load pasted image").performClick()

        val updates = actions.filterIsInstance<M3EDocumentScannerAction.UpdateBase64>()
        assertTrue(updates.any { it.value == "/9j/4AAQSkZJRg==" })
        assertTrue(actions.any { it is M3EDocumentScannerAction.LoadPasted })
    }

    @Test
    fun titleAndNoteEmitTheirActions() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentScannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(title = "", note = ""),
                    onAction = { actions.add(it) },
                )
            }
        }

        onNodeWithTag(SCANNER_TITLE_FIELD_TAG).performTextInput("Receipt")
        onNodeWithTag(SCANNER_NOTE_FIELD_TAG).performTextInput("Paid in cash")

        assertTrue(actions.any { it is M3EDocumentScannerAction.SetTitle && it.value == "Receipt" })
        assertTrue(actions.any { it is M3EDocumentScannerAction.SetNote && it.value == "Paid in cash" })
    }

    @Test
    fun exportAndShareEmitTheirActions() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentScannerAction>()
        setContent {
            AmazeM3ETheme {
                // The stateless screen no longer scrolls; its host owns the bounded scroller.
                Column(modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    M3EDocumentScannerScreen(state = sampleState(), onAction = { actions.add(it) })
                }
            }
        }

        onRoot().performTouchInput { swipeUp() }
        onNodeWithTag(SCANNER_EXPORT_TAG).performClick()
        onNodeWithTag(SCANNER_SHARE_TAG).performClick()

        assertTrue(actions.any { it is M3EDocumentScannerAction.ExportPdf }, "export missing; actions=$actions")
        assertTrue(actions.any { it is M3EDocumentScannerAction.SharePdf }, "share missing; actions=$actions")
    }

    @Test
    fun emptyAndErrorCopyIsHonest() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = M3EDocumentScannerState(
                        errorMessage = "The image could not be decoded on this device.",
                        shareNotice = "Sharing is not available on this device.",
                    ),
                    onAction = {},
                )
            }
        }

        // Real capture paths, not the old fake "import base64 text file" primary control.
        onNodeWithText("Choose photo from gallery").assertExists()
        onNodeWithText("Take photo").assertExists()
        onAllNodesWithText("Import base64 / data-URI text file").assertCountEquals(0)

        // Capabilities are described exactly: camera/gallery on Android; OCR on supported
        // devices; automatic edge detection is still unavailable.
        onNodeWithText(
            "Automatic edge detection is unavailable",
            substring = true,
        ).assertExists()
        onNodeWithText("The image could not be decoded on this device.").assertExists()
        onNodeWithText("Sharing is not available on this device.").assertExists()
    }

    @Test
    fun hostComposesWithoutThrowing() = runComposeUiTest {
        // No outer theme on purpose: the host must not depend on one and must not throw while
        // registering its launchers outside M3EDismissibleSheet's Dialog.
        setContent { M3EDocumentScannerHost(onDismiss = {}) }

        onNodeWithText("Document scanner").assertExists()
        onNodeWithText("DOCUMENT SCANNER").assertExists()
        onNodeWithText("Choose photo from gallery").assertExists()
        onNodeWithText("Take photo").assertExists()
        onNodeWithText("ON-DEVICE READING").assertExists()
    }

    // ---- D-171: real on-device reading UI ----

    @Test
    fun inferenceChipsRenderFromRealPlatformResult() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(inference = fullInference()),
                    onAction = {},
                )
            }
        }

        onNodeWithTag(SCANNER_READING_TAG).assertExists()
        onNodeWithText("ON-DEVICE READING").assertExists()

        onNodeWithText("Assessment").assertExists()
        onNodeWithText("CIA-1").assertExists()
        onNodeWithText("Subject").assertExists()
        onNodeWithText("Database Systems").assertExists()
        onNodeWithText("Subject code").assertExists()
        onNodeWithText("CSE301").assertExists()
        onNodeWithText("Class codes").assertExists()
        onNodeWithText("CSE-A, CSE-B").assertExists()
        onNodeWithText("Faculties").assertExists()
        onNodeWithText("Dr. Rao, Dr. Mehta").assertExists()
        onNodeWithText("Document date").assertExists()
        onNodeWithText("2026-08-12").assertExists()
        onNodeWithText("Confidence").assertExists()
        onNodeWithText("86%").assertExists()
        onNodeWithText("Printed question paper").assertExists()
        onNodeWithText("Two class sections listed").assertExists()
    }

    @Test
    fun absentInferenceFieldsAreNotShown() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(inference = bareInference(), ocrText = null),
                    onAction = {},
                )
            }
        }

        onNodeWithText("ON-DEVICE READING").assertExists()
        listOf("Assessment", "Subject code", "Class codes", "Faculties", "Document date", "Confidence").forEach { label ->
            onNodeWithText(label, substring = true).assertDoesNotExist()
        }
        onAllNodesWithText("N/A").assertCountEquals(0)
        onAllNodesWithText("null").assertCountEquals(0)
    }

    @Test
    fun noTextNoticeRendersHonestly() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(analysisNotice = "No text could be read from this image on this device."),
                    onAction = {},
                )
            }
        }
        onNodeWithText("No text could be read from this image on this device.").assertExists()
    }

    @Test
    fun unavailableAnalysisNoticeRendersHonestly() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = sampleState().copy(
                        inference = null,
                        ocrText = "CIA-1 raw printed lines",
                        analysisNotice = UNAVAILABLE_ANALYSIS_NOTICE,
                    ),
                    onAction = {},
                )
            }
        }
        onNodeWithText(UNAVAILABLE_ANALYSIS_NOTICE).assertExists()
        onNodeWithText("Read text: CIA-1 raw printed lines").assertExists()
    }

    @Test
    fun progressRowReflectsRealAnalysisState() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(state = sampleState().copy(isAnalyzing = true), onAction = {})
            }
        }
        onNodeWithTag(SCANNER_ANALYSIS_PROGRESS_TAG).assertExists()
        onNodeWithText("Reading this document on-device…").assertExists()
        onNodeWithTag(SCANNER_ANALYZE_TAG).assertIsNotEnabled()
    }

    @Test
    fun reanalyseEmitsAnalyzeAction() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentScannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }
        onNodeWithText("Re-analyse").performClick()
        assertTrue(actions.any { it is M3EDocumentScannerAction.Analyze }, "Analyze missing; actions=$actions")
    }

    @Test
    fun reanalyseIsDisabledWithoutAnImage() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentScannerScreen(
                    state = M3EDocumentScannerState(imageBytes = null),
                    onAction = {},
                )
            }
        }
        onNodeWithTag(SCANNER_ANALYZE_TAG).assertIsNotEnabled()
    }

    @Test
    fun pdfFileNameUsesOnlyRealInferenceFacts() {
        assertEquals("amazecc-scan-123.pdf", scannerPdfFileName(inference = null, timestampMillis = 123L))

        val inference = fullInference()
        val expected = "${suggestedDocumentName(inference)}-123.pdf"
        assertEquals(expected, scannerPdfFileName(inference = inference, timestampMillis = 123L))
        assertTrue(scannerPdfFileName(inference, 123L).startsWith("CSE301"))

        val bare = bareInference()
        assertEquals(
            "${suggestedDocumentName(bare)}-123.pdf",
            scannerPdfFileName(inference = bare, timestampMillis = 123L),
        )
        assertTrue(!scannerPdfFileName(bare, 123L).contains("null"))
    }

    @Test
    fun staleAnalysisResultIsRejectedByGenerationOrByteIdentity() {
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(isCurrentAnalysis(1, 1, bytes, bytes))
        assertFalse(isCurrentAnalysis(2, 1, bytes, bytes)) // newer read started
        assertFalse(isCurrentAnalysis(1, 1, byteArrayOf(1, 2, 3), bytes)) // different image instance
        assertFalse(isCurrentAnalysis(1, 1, null, bytes)) // document cleared
    }

    @Test
    fun applyInferenceResultPrefillsOnlyAnUntouchedBlankTitle() {
        val inference = fullInference()
        val blank = sampleState().copy(title = "")
        assertEquals(
            inference.suggestedName,
            applyInferenceResult(blank, inference, "raw text", titleAtStart = "").title,
        )
        val typedDuringRead = blank.copy(title = "My own title")
        assertEquals(
            "My own title",
            applyInferenceResult(typedDuringRead, inference, "raw text", titleAtStart = "").title,
        )
        val clearedDuringRead = blank.copy(title = "")
        assertEquals(
            "",
            applyInferenceResult(clearedDuringRead, inference, "raw text", titleAtStart = "Earlier typed").title,
        )
    }
}
