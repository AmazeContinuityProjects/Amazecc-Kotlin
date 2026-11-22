package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.utils.rememberFileSharer
import com.amazecc.app.shared.utils.rememberImagePicker
import com.amazecc.app.shared.utils.rememberPhotoCapture
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * D-009 — stateful host for the document scanner.
 *
 * This composable is rendered by `AdvancedSettingsHub` **outside** any `Dialog`. All three
 * ActivityResult seams (gallery, camera, share) are registered here, in the host composition,
 * and the sheet is created below them. The stateless [M3EDocumentScannerScreen] is the only
 * thing inside `M3EDismissibleSheet`'s `Dialog` content — no launcher and no nested theme
 * wrapper live in that subcomposition. That is the crash regression fix.
 *
 * Validation, JPEG checks, off-Main PDF work, real filenames and honest notices all live here.
 */
@Composable
fun M3EDocumentScannerHost(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(M3EDocumentScannerState()) }
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()
    val shareFile = rememberFileSharer()

    // Launchers are deliberately registered here, outside the Dialog created below.
    val pickFromGallery = rememberImagePicker { bytes -> state = acceptCapturedBytes(state, bytes) }
    val takePhoto = rememberPhotoCapture { bytes -> state = acceptCapturedBytes(state, bytes) }

    fun exportPdf(share: Boolean) {
        val current = state
        val source = current.imageBytes
        val bitmap = current.image
        if (source == null || bitmap == null) {
            state = current.copy(status = null, errorMessage = "Import or capture an image first.")
            return
        }
        if (!DocumentScannerPdf.isJpeg(source)) {
            state = current.copy(
                status = null,
                errorMessage = "PDF export needs the original JPEG bytes. Re-save this image as JPEG and import it again.",
            )
            return
        }
        val jpeg = source
        val crop = current.crop
        val width = bitmap.width
        val height = bitmap.height
        val title = current.title
        val note = current.note
        scope.launch {
            state = state.copy(isExporting = true, status = "Generating PDF…", errorMessage = null, shareNotice = null)
            try {
                val pdf = withContext(Dispatchers.Default) {
                    DocumentScannerPdf.buildPdf(jpeg, crop, width, height, title, note)
                }
                val name = scannerPdfFileName()
                val sizeKb = pdf.size / 1024
                if (share) {
                    val shared = shareFile(name, pdf, "application/pdf")
                    state = if (shared) {
                        state.copy(
                            isExporting = false,
                            status = "Prepared $name ($sizeKb KB).",
                            shareNotice = "Shared $name ($sizeKb KB).",
                            errorMessage = null,
                        )
                    } else {
                        state.copy(
                            isExporting = false,
                            status = null,
                            shareNotice = "Sharing is not available on this device.",
                        )
                    }
                } else {
                    val saved = saveFile(name, pdf)
                    if (saved) openDocumentBytes(pdf, name, "application/pdf")
                    state = if (saved) {
                        state.copy(isExporting = false, status = "Saved $name ($sizeKb KB).", errorMessage = null)
                    } else {
                        state.copy(isExporting = false, status = null, errorMessage = "The PDF could not be saved on this device.")
                    }
                }
            } catch (e: Exception) {
                state = state.copy(
                    isExporting = false,
                    status = null,
                    errorMessage = "Could not generate the PDF: ${e.message ?: "unknown error"}",
                )
            }
        }
    }

    fun handleAction(action: M3EDocumentScannerAction) {
        when (action) {
            M3EDocumentScannerAction.PickFromGallery -> pickFromGallery()
            M3EDocumentScannerAction.TakePhoto -> takePhoto()
            is M3EDocumentScannerAction.UpdateBase64 -> state = state.copy(base64Input = action.value, errorMessage = null)
            M3EDocumentScannerAction.LoadPasted -> state = acceptBase64Input(state)
            is M3EDocumentScannerAction.SetCrop -> state = state.copy(crop = clampCrop(action.crop))
            is M3EDocumentScannerAction.SetTitle -> state = state.copy(title = action.value)
            is M3EDocumentScannerAction.SetNote -> state = state.copy(note = action.value)
            M3EDocumentScannerAction.ExportPdf -> exportPdf(share = false)
            M3EDocumentScannerAction.SharePdf -> exportPdf(share = true)
            M3EDocumentScannerAction.Clear -> state = M3EDocumentScannerState()
        }
    }

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        modifier = modifier,
        title = "Document scanner",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            M3EDocumentScannerScreen(state = state, onAction = { handleAction(it) })
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

/** Accept bytes from gallery/camera and decode them, reporting exactly what happened. */
private fun acceptCapturedBytes(current: M3EDocumentScannerState, bytes: ByteArray?): M3EDocumentScannerState {
    if (bytes == null || bytes.isEmpty()) {
        return current.copy(status = null, errorMessage = "No image was selected.")
    }
    val decoded = runCatching { bytes.toImageBitmap() }.getOrNull()
        ?: return current.copy(status = null, errorMessage = "The image could not be decoded on this device.")
    return current.copy(
        imageBytes = bytes,
        image = decoded,
        crop = DocumentScannerPdf.Crop.FULL,
        status = "Image loaded (${decoded.width}×${decoded.height}). Drag the handles to crop.",
        errorMessage = if (DocumentScannerPdf.isJpeg(bytes)) null
        else "This image is not a JPEG. PDF export embeds the original JPEG bytes, so please re-save it as JPEG before exporting.",
        shareNotice = null,
    )
}

/** Base64 / data-URI fallback used where no camera or picker seam exists. */
private fun acceptBase64Input(current: M3EDocumentScannerState): M3EDocumentScannerState {
    val raw = current.base64Input.trim()
    val clean = raw.substringAfter("base64,", missingDelimiterValue = raw).filter { !it.isWhitespace() }
    if (clean.isBlank()) {
        return current.copy(status = null, errorMessage = "No image data found. Paste a data URI or base64 image.")
    }
    val bytes = runCatching { clean.decodeBase64Bytes() }.getOrNull()
    if (bytes == null || bytes.isEmpty()) {
        return current.copy(status = null, errorMessage = "That does not look like valid base64 image data.")
    }
    return acceptCapturedBytes(current, bytes)
}

/** Distinct, real filename for every export so a previous PDF is never silently overwritten. */
private fun scannerPdfFileName(): String = "amazecc-scan-${Clock.System.now().toEpochMilliseconds()}.pdf"
