package com.amazecc.app.shared.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private fun saveToDownloads(context: Context, fileName: String, bytes: ByteArray): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { os -> os.write(bytes) } ?: return null
        return uri
    }
    @Suppress("DEPRECATION")
    val file = java.io.File(context.cacheDir, fileName)
    file.writeBytes(bytes)
    return Uri.fromFile(file)
}

@Composable
actual fun rememberFileSaver(): (fileName: String, bytes: ByteArray) -> Boolean {
    val context = LocalContext.current
    return { fileName, bytes ->
        try {
            saveToDownloads(context, fileName, bytes) != null
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
actual fun rememberPdfOpener(): (fileName: String, bytes: ByteArray) -> Boolean {
    val context = LocalContext.current
    return { fileName, bytes ->
        try {
            val uri = saveToDownloads(context, fileName, bytes)
            if (uri == null) {
                false
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open $fileName"))
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
