package com.amazecc.app.shared.vtop

import androidx.compose.ui.graphics.ImageBitmap

/**
 * iOS stub — the local VTOP layer is Android-only, so the captcha preview is not needed here.
 * See `docs/sep-29-2026/vtop-local-integration-plan.md` section 5.
 */
actual fun decodeDataUriToImageBitmap(dataUri: String): ImageBitmap? = null
