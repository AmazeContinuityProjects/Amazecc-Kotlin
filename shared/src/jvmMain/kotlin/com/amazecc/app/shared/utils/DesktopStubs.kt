package com.amazecc.app.shared.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
