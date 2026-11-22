package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

/**
 * iOS has no Compose-interop camera/photo-picker seam wired here yet. These actuals
 * fail closed: they report "nothing picked" / "cannot share" immediately instead of
 * hanging on a launcher that never returns or claiming a file was captured.
 */
@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

@Composable
actual fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

@Composable
actual fun rememberFileSharer(): (fileName: String, bytes: ByteArray, mimeType: String) -> Boolean =
    { _, _, _ -> false }
