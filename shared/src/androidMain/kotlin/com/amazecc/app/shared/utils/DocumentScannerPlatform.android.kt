package com.amazecc.app.shared.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.amazecc.app.shared.domain.ScannedDocument
import com.amazecc.app.shared.domain.ScanSource
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val ML_KIT_NOTICE = "Straightened and cleaned by ML Kit Document Scanner"

/**
 * D-174 — Android auto document scan.
 *
 * Primary path: the ML Kit Document Scanner (FULL mode: auto crop + image cleaning), started
 * from an `IntentSender` and parsed from the real Activity result. Content-URI reads run on
 * [Dispatchers.IO]; the callback returns to Main.
 *
 * We intentionally do NOT try to mine `data.data` / `EXTRA_STREAM` for an OpenCV pass here: the
 * structured ML Kit result does not carry those extras, so that old fallback was unreachable and
 * misleading. OpenCV is now a real, explicit, user-triggered path via [straightenDocumentWithOpenCV]
 * on an image the user has already loaded. A cancelled/unreadable scan delivers `null`: the host
 * keeps its manual crop path and shows honest copy. Nothing is faked.
 */
@Composable
actual fun rememberDocumentScanner(onResult: (ScannedDocument?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    val scope = rememberCoroutineScope()

    // Registered in the host composition (never inside the scanner sheet's Dialog).
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val document = readMlKitResult(data, resolver)
            withContext(Dispatchers.Main) { onResult(document) }
        }
    }

    return {
        scope.launch(Dispatchers.IO) {
            val activity = context.findActivity()
            // getStartScanIntent takes a real Activity; Compose LocalContext usually wraps one.
            // Await the Play services Task off Main so a first-run model download cannot stall UI.
            val intentSender = if (activity != null) runCatching {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    // JPEG only: the PDF we export is rebuilt from the JPEG by DocumentScannerPdf,
                    // so asking the scanner for a PDF we never read is wasted work and memory.
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity).await()
            }.getOrNull() else null

            withContext(Dispatchers.Main) {
                if (intentSender != null) {
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else {
                    // No Activity, no scanner on the device, or the Task failed. Nothing was
                    // captured, so there is no input to process — report an honest cancellation.
                    onResult(null)
                }
            }
        }
    }
}

/**
 * D-174 — explicit OpenCV fallback, called by the host when the user taps the labelled control.
 *
 * Delegates to the real on-device pipeline in [scanDocumentWithOpenCV] (edge detection,
 * perspective rectification, adaptive-threshold cleanup, JPEG re-encode). Switching to
 * [Dispatchers.Default] keeps the native work off Main even if a caller forgets to. A `null`
 * return means OpenCV is unavailable or found no page — never a fabricated image.
 */
actual suspend fun straightenDocumentWithOpenCV(bytes: ByteArray): ByteArray? =
    withContext(Dispatchers.Default) {
        scanDocumentWithOpenCV(bytes)?.imageBytes
    }

/** Resolve the Activity behind a Compose `LocalContext` (which is often a ContextThemeWrapper). */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        current = runCatching { (current as? ContextWrapper)?.baseContext }.getOrNull()
    }
    return null
}

/** Parse the real ML Kit result and stream the page image URI. Never called on Main. */
private fun readMlKitResult(data: Intent, resolver: ContentResolver): ScannedDocument? = try {
    val scan = GmsDocumentScanningResult.fromActivityResultIntent(data)
    val imageUri = scan?.pages?.firstOrNull()?.imageUri
    if (imageUri == null) {
        null
    } else {
        val imageBytes = readBytes(resolver, imageUri)
        if (imageBytes == null) {
            null
        } else {
            ScannedDocument(
                imageBytes = imageBytes,
                // The PDF bytes are deliberately not read or held: the host rebuilds the PDF from
                // the JPEG with the current crop. Keep the field, but leave it null (no wasted IO).
                pdfBytes = null,
                source = ScanSource.ML_KIT,
                notice = ML_KIT_NOTICE,
            )
        }
    }
} catch (t: Throwable) {
    // Scanner API missing on this device / malformed result — report nothing, never fabricate.
    null
}

private fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray? = try {
    resolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.isNotEmpty() }
} catch (t: Throwable) {
    null
}
