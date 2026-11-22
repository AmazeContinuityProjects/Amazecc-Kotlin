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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.PaperInference
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.utils.inferPaperDetails
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.recognizeDocumentText
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.utils.rememberFileSharer
import com.amazecc.app.shared.utils.rememberImagePicker
import com.amazecc.app.shared.utils.rememberPhotoCapture
import com.amazecc.app.shared.utils.suggestedDocumentName
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Validation, JPEG checks, off-Main PDF work, on-device reading, real filenames and honest
 * notices all live here. OCR/inference are real platform calls and report real failures.
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

    // D-171: a generation key plus job cancellation make sure a slow result for an older image
    // can never overwrite the reading of a newer one. Both stay host-owned, outside the Dialog.
    var analysisGeneration by remember { mutableIntStateOf(0) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    /** Runs real on-device OCR/inference on [bytes]; results are guarded by a generation key. */
    fun startAnalysis(bytes: ByteArray) {
        val generation = ++analysisGeneration
        val titleAtStart = state.title
        analysisJob?.cancel()
        state = state.copy(
            inference = null,
            ocrText = null,
            isAnalyzing = true,
            analysisNotice = null,
        )
        analysisJob = scope.launch {
            fun stillCurrent(): Boolean = isCurrentAnalysis(analysisGeneration, generation, state.imageBytes, bytes)

            val lines = try {
                withContext(Dispatchers.Default) { recognizeDocumentText(bytes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stillCurrent()) {
                    state = state.copy(
                        isAnalyzing = false,
                        inference = null,
                        ocrText = null,
                        analysisNotice = UNAVAILABLE_ANALYSIS_NOTICE,
                    )
                }
                return@launch
            }
            if (!stillCurrent()) return@launch
            if (lines.isEmpty()) {
                state = state.copy(
                    isAnalyzing = false,
                    inference = null,
                    ocrText = null,
                    analysisNotice = "No text could be read from this image on this device.",
                )
                return@launch
            }
            val text = lines.joinToString("\n")
            val inference = try {
                withContext(Dispatchers.Default) { inferPaperDetails(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (!stillCurrent()) return@launch
            if (inference == null) {
                // Honest partial result: keep the real OCR text, claim no structured details.
                state = state.copy(
                    isAnalyzing = false,
                    inference = null,
                    ocrText = text,
                    analysisNotice = UNAVAILABLE_ANALYSIS_NOTICE,
                )
                return@launch
            }
            state = applyInferenceResult(state, inference, text, titleAtStart)
        }
    }

    /** Commits a decoded image and starts reading it only when the image bytes actually changed. */
    fun commitImage(next: M3EDocumentScannerState) {
        val previousBytes = state.imageBytes
        state = next
        val newBytes = next.imageBytes
        if (newBytes != null && newBytes !== previousBytes) startAnalysis(newBytes)
    }

    // Launchers are deliberately registered here, outside the Dialog created below.
    val pickFromGallery = rememberImagePicker { bytes -> commitImage(acceptCapturedBytes(state, bytes)) }
    val takePhoto = rememberPhotoCapture { bytes -> commitImage(acceptCapturedBytes(state, bytes)) }

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
        val inference = current.inference
        scope.launch {
            state = state.copy(isExporting = true, status = "Generating PDF…", errorMessage = null, shareNotice = null)
            try {
                val pdf = withContext(Dispatchers.Default) {
                    DocumentScannerPdf.buildPdf(jpeg, crop, width, height, title, note)
                }
                val name = scannerPdfFileName(inference)
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
            M3EDocumentScannerAction.LoadPasted -> commitImage(acceptBase64Input(state))
            is M3EDocumentScannerAction.SetCrop -> state = state.copy(crop = clampCrop(action.crop))
            is M3EDocumentScannerAction.SetTitle -> state = state.copy(title = action.value)
            is M3EDocumentScannerAction.SetNote -> state = state.copy(note = action.value)
            M3EDocumentScannerAction.ExportPdf -> exportPdf(share = false)
            M3EDocumentScannerAction.SharePdf -> exportPdf(share = true)
            M3EDocumentScannerAction.Analyze -> {
                val bytes = state.imageBytes
                if (bytes == null) {
                    // No image: honest no-op, never fabricate an analysis result.
                    state = state.copy(isAnalyzing = false, analysisNotice = "Import or capture an image before analysing it.")
                } else {
                    startAnalysis(bytes)
                }
            }
            M3EDocumentScannerAction.Clear -> {
                analysisGeneration++ // invalidate any in-flight read before wiping the image
                analysisJob?.cancel()
                state = M3EDocumentScannerState()
            }
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

/** Pure guard: a stale read must never overwrite a newer image or a cleared document. */
internal fun isCurrentAnalysis(
    currentGeneration: Int,
    generation: Int,
    currentBytes: ByteArray?,
    bytes: ByteArray,
): Boolean = currentGeneration == generation && currentBytes === bytes

/** Pure state transition for a successful read; never invents a title the user changed. */
internal fun applyInferenceResult(
    state: M3EDocumentScannerState,
    inference: PaperInference,
    ocrText: String,
    titleAtStart: String,
): M3EDocumentScannerState = state.copy(
    isAnalyzing = false,
    inference = inference,
    ocrText = ocrText,
    analysisNotice = null,
    title = if (titleAtStart.isBlank() && state.title.isBlank()) inference.suggestedName else state.title,
)

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

internal const val UNAVAILABLE_ANALYSIS_NOTICE = "On-device analysis is unavailable on this device."

/**
 * Distinct, real filename for every export so a previous PDF is never silently overwritten.
 *
 * D-171: when the device actually inferred paper details, the pure [suggestedDocumentName]
 * helper supplies the base; a real timestamp keeps each export unique. Without an inference
 * the original `amazecc-scan-<millis>.pdf` contract is unchanged. Pass [timestampMillis] in
 * tests to make this a fully deterministic pure function.
 */
internal fun scannerPdfFileName(
    inference: PaperInference? = null,
    timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val base = if (inference == null) "amazecc-scan" else suggestedDocumentName(inference)
    return "$base-$timestampMillis.pdf"
}
