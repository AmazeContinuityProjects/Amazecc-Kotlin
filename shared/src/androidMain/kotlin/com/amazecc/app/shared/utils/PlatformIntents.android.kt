package com.amazecc.app.shared.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.amazecc.app.shared.services.AndroidApp
import java.io.File
import java.util.UUID

/** Launches [intent] from the application context; returns false when nothing can handle it. */
private fun launch(intent: Intent): Boolean {
    val context: Context = AndroidApp.context ?: return false
    return runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/** Strips path separators and anything outside a conservative filename alphabet. */
private fun safeFileName(fileName: String): String {
    val base = fileName.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.')
    return "${UUID.randomUUID()}-${base.ifBlank { "document" }}"
}

actual fun openExternalUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    return launch(Intent(Intent.ACTION_VIEW, uri))
}

actual fun dialPhoneNumber(number: String): Boolean {
    if (number.isBlank()) return false
    val uri = runCatching { Uri.parse("tel:$number") }.getOrNull() ?: return false
    return launch(Intent(Intent.ACTION_DIAL, uri))
}

actual fun openDocumentBytes(bytes: ByteArray, fileName: String, mimeType: String): Boolean {
    val context: Context = AndroidApp.context ?: return false
    return runCatching {
        val dir = File(context.cacheDir, "platform_intents").apply { mkdirs() }
        val file = File(dir, safeFileName(fileName))
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

actual fun shareText(text: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return launch(intent)
}
