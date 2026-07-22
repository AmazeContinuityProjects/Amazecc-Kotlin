package com.amazecc.app.shared.utils

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFileSaver(): (fileName: String, bytes: ByteArray) -> Boolean {
    val context = LocalContext.current
    return { fileName, bytes ->
        try {
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os -> os.write(bytes) }
                    true
                } else false
            } else {
                @Suppress("DEPRECATION")
                val file = java.io.File(context.cacheDir, fileName)
                file.writeBytes(bytes)
                true
            }
            saved
        } catch (_: Exception) {
            false
        }
    }
}
