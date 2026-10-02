package com.amazecc.app.shared.vtop

import androidx.compose.ui.graphics.ImageBitmap
import com.amazecc.app.shared.utils.toImageBitmap
import java.util.Base64

/**
 * Desktop decoding for captcha/profile data URIs, via ImageIO rather than android.graphics.
 */
actual fun decodeDataUriToImageBitmap(dataUri: String): ImageBitmap? {
    val comma = dataUri.indexOf(',')
    if (!dataUri.startsWith("data:") || comma < 0) return null
    val payload = dataUri.substring(comma + 1)
    val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
    return bytes.toImageBitmap()
}
