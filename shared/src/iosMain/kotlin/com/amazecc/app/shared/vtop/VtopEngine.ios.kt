package com.amazecc.app.shared.vtop

actual val vtopEngineSupported: Boolean = false

/**
 * iOS has no engine yet.
 *
 * The WebView dependency is the reason iOS was deferred: `WKWebView` has no equivalent of
 * Android's `CookieManager` + synchronous `XMLHttpRequest` pairing without additional work, and
 * the captcha image decoder would need CoreGraphics instead of `android.graphics.Bitmap`.
 *
 * Until it is implemented the iOS build keeps using `VtopSource.REMOTE`. Calling any member
 * throws so a misconfiguration surfaces immediately rather than silently returning empty data.
 */
actual class VtopEngine actual constructor() {

    actual suspend fun initialize() =
        unsupported()

    actual suspend fun load(path: String) =
        unsupported()

    actual suspend fun evaluate(script: String, timeoutMs: Long): String =
        unsupported()

    actual suspend fun setUserAgent(userAgent: String) =
        unsupported()

    actual suspend fun clearCookies() =
        unsupported()

    actual suspend fun cookieHeader(): String =
        unsupported()

    actual fun destroy() = Unit

    private fun unsupported(): Nothing = throw UnsupportedOperationException(
        "Local VTOP access is not implemented on iOS. Keep vtop_source = REMOTE. " +
            "See docs/sep-29-2026/vtop-local-integration-plan.md"
    )
}
