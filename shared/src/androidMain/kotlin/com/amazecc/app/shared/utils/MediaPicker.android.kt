package com.amazecc.app.shared.utils

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * The manifest already declares `androidx.core.content.FileProvider` with authority
 * `${applicationId}.fileprovider` and `@xml/file_paths` containing `<cache-path path="." />`,
 * so every temporary file below `cacheDir` is shareable without touching the manifest.
 */
private fun authority(context: android.content.Context): String = "${context.packageName}.fileprovider"

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        onResult(bytes)
    }
    return { launcher.launch("image/*") }
}

@Composable
actual fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    // Holds the destination between launch and result. `remember` keeps it alive across
    // recompositions, so the callback always reads the file this launch actually targeted.
    val pendingFile = remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingFile.value
        pendingFile.value = null
        if (!saved || file == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        runCatching { if (file.exists()) file.delete() }
        onResult(bytes)
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
        pendingFile.value = file
        val uri: Uri = FileProvider.getUriForFile(context, authority(context), file)
        launcher.launch(uri)
    }
}

@Composable
actual fun rememberFileSharer(): (fileName: String, bytes: ByteArray, mimeType: String) -> Boolean {
    val context = LocalContext.current
    return { fileName, bytes, mimeType ->
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeName = fileName.substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('.')
                .ifBlank { "document.pdf" }
            val file = File(dir, safeName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, authority(context), file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // No chooser is shown unless a real target can handle the payload.
            val canShare = intent.resolveActivity(context.packageManager) != null
            if (canShare) context.startActivity(Intent.createChooser(intent, "Share $safeName"))
            canShare
        }.getOrDefault(false)
    }
}
