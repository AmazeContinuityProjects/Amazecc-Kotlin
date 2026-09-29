package com.amazecc.app.shared.vtop

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a `data:image/...;base64,` URI into an [ImageBitmap].
 *
 * VTOP serves the captcha as an inline data URI rather than a fetchable URL, so this exists only
 * for the manual-entry dialog. Returns null when the payload cannot be decoded.
 */
expect fun decodeDataUriToImageBitmap(dataUri: String): ImageBitmap?
