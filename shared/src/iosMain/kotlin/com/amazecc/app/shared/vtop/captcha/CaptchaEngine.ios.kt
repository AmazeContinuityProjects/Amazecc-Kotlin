package com.amazecc.app.shared.vtop.captcha

/**
 * iOS stub.
 *
 * Image decode and resize would need CoreGraphics rather than `android.graphics.Bitmap`, which
 * is one of the reasons iOS was deferred from the local VTOP layer. Until that exists the iOS
 * build keeps using the remote proxy and manual captcha entry.
 */
actual class CaptchaEngine actual constructor() {

    actual val isReady: Boolean = false

    actual suspend fun solve(dataUri: String): CaptchaPrediction? = null
}
