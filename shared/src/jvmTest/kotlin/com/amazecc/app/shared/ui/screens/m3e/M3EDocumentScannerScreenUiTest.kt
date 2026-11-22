package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
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

        onNodeWithTag(SCANNER_EXPORT_TAG).performClick()
        onRoot().performTouchInput { swipeUp() }
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

        // Capabilities are described exactly: camera/gallery on Android only, no OCR/edge detection.
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
    }
}
