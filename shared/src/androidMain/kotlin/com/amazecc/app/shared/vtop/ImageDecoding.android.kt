package com.amazecc.app.shared.vtop

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream

actual fun decodeDataUriToImageBitmap(dataUri: String): ImageBitmap? {
    val comma = dataUri.indexOf(',')
    val payload = if (comma >= 0) dataUri.substring(comma + 1) else dataUri
    val bytes = try {
        Base64.decode(payload, Base64.DEFAULT)
    } catch (_: Exception) {
        return null
    }
    val bitmap = try {
        BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
    } catch (_: Throwable) {
        null
    } ?: return null
    return bitmap.asImageBitmap()
}
