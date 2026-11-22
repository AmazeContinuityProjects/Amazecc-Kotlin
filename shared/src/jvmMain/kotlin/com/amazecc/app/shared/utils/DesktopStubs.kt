package com.amazecc.app.shared.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.amazecc.app.shared.domain.GeotagPhoto
import com.amazecc.app.shared.domain.PaperInference
import com.amazecc.app.shared.domain.ScannedDocument
import java.awt.Desktop
import java.io.File
import javax.imageio.ImageIO

// ── platform capabilities that only exist on a phone ─────────────────────────

/** Desktop has no notification surface wired up; reminders are a mobile feature. */
actual fun isAndroid(): Boolean = false

actual suspend fun requestNotificationPermissions(): Boolean = false

actual suspend fun scheduleLocalNotification(id: Int, title: String, body: String, triggerTimeMs: Long) = Unit

actual suspend fun clearPendingNotifications() = Unit

actual suspend fun createNotificationChannels() = Unit

actual suspend fun testLocalNotification() = Unit

actual suspend fun showDownloadCompleteNotification(fileName: String) = Unit

/** No widget host on desktop. */
actual fun pushWidgetUpdates() = Unit

actual fun rescheduleAlarmsFromCache() = Unit

/** Sync alarms are an Android AlarmManager concern. */
actual fun scheduleSyncAlarm(triggerAtMillis: Long, kind: String) = Unit

actual fun cancelSyncAlarms() = Unit

// ── image decoding ────────────────────────────────────────────────────────────

actual fun ByteArray.toImageBitmap(): ImageBitmap? = runCatching {
    ImageIO.read(java.io.ByteArrayInputStream(this))?.toComposeImageBitmap()
}.getOrNull()

// ── D-166 on-device document intelligence (desktop unsupported) ─────────────

/**
 * No ML Kit OCR or Gemini Nano bridge exists on desktop. Returning empty/null
 * keeps the common API honest: the UI must report the feature as unavailable.
 */
actual suspend fun recognizeDocumentText(bytes: ByteArray): List<String> = emptyList()

actual suspend fun inferPaperDetails(text: String): PaperInference? = null

// ── file + URL + share ────────────────────────────────────────────────────────

/**
 * Desktop genuinely can save files, and doing it for real is the point of running here: exports
 * land on disk where a test (or a human) can inspect them. Writes to `./build/desktop-exports`.
 */
@Composable
actual fun rememberFileSaver(): (fileName: String, bytes: ByteArray) -> Boolean = { fileName, bytes ->
    runCatching {
        val dir = File("build/desktop-exports").apply { mkdirs() }
        File(dir, fileName).writeBytes(bytes)
        true
    }.getOrDefault(false)
}

@Composable
actual fun rememberPdfOpener(): (fileName: String, bytes: ByteArray) -> Boolean = { fileName, bytes ->
    runCatching {
        val dir = File("build/desktop-exports").apply { mkdirs() }
        val out = File(dir, fileName).apply { writeBytes(bytes) }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(out)
        }
        true
    }.getOrDefault(false)
}

/**
 * Desktop has no equivalent of the Android document picker wired into Compose. Returns a launcher
 * that reports "cancelled" so callers take their empty-path branch instead of hanging.
 */
@Composable
actual fun rememberFileImporter(onResult: (String?) -> Unit): () -> Unit = { onResult(null) }

/**
 * Desktop has no camera / photo picker / system share sheet wired into Compose. Fail closed so
 * the host can show an honest "unavailable on this platform" notice instead of hanging or
 * pretending a picture was captured.
 */
@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

@Composable
actual fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

@Composable
actual fun rememberFileSharer(): (fileName: String, bytes: ByteArray, mimeType: String) -> Boolean =
    { _, _, _ -> false }

@Composable
actual fun rememberUrlOpener(): (url: String) -> Unit = { url ->
    runCatching {
        if (Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            Desktop.getDesktop().browse(java.net.URI(url))
        }
    }
}

@Composable
actual fun ShareIcsButton(icsContent: String) {
    // There is no system share sheet on desktop; the file saver covers the export path.
    Column { Text("Export ICS is available on mobile") }
}

// ── D-167 geotagged camera (desktop unsupported) ──────────────────────────────

/** No camera or location hardware: report "nothing captured" instead of a fake photo. */
@Composable
actual fun rememberGeotagCamera(onCaptured: (GeotagPhoto?) -> Unit): () -> Unit = { onCaptured(null) }

/** There is no desktop launcher/dock pinning API wired here. */
@Composable
actual fun rememberShortcutPinner(): (label: String) -> Boolean = { false }

/** No map renderer on desktop; show coordinates honestly. */
@Composable
actual fun GeotagMap(latitude: Double, longitude: Double, modifier: Modifier) {
    Column(modifier = modifier) {
        Text("Map unavailable on this device")
        Text("Latitude: $latitude, Longitude: $longitude")
    }
}

// ── D-174 auto document scan (desktop unsupported) ────────────────────────────

/**
 * Desktop has neither ML Kit nor an OpenCV native bridge wired here. Report "nothing scanned"
 * immediately so the host keeps its honest manual import/crop path — no fake page or source.
 */
@Composable
actual fun rememberDocumentScanner(onResult: (ScannedDocument?) -> Unit): () -> Unit = { onResult(null) }

/** No OpenCV native bridge on desktop either — return null honestly, never a fake straightened page. */
actual suspend fun straightenDocumentWithOpenCV(bytes: ByteArray): ByteArray? = null
