package com.amazecc.app.shared.vtop

/**
 * Desktop stub for the VTOP WebView.
 *
 * There is no embedded browser on desktop, so the local VTOP path is unavailable here and the app
 * falls back to `VtopSource.REMOTE` - the same behaviour iOS already has. This matters for the
 * parser tests: `VtopScripts` builds JavaScript *strings* and `VtopPage`/`VtopCourseCode`/
 * `VtopRows` are pure Kotlin, so all of them are testable on the JVM without a live engine.
 *
 * Scraping real VTOP markup for those tests is done by
 * `../AmazeCC-API/scripts/vtop-dump.mjs`, which runs from a machine on an Indian network.
 */
actual val vtopEngineSupported: Boolean = false

private const val NO_ENGINE =
    "The VTOP WebView engine is only available on Android; use VtopSource.REMOTE on desktop"

actual class VtopEngine {

    actual suspend fun initialize(): Unit = throw UnsupportedOperationException(NO_ENGINE)

    actual suspend fun load(path: String): Unit = throw UnsupportedOperationException(NO_ENGINE)

    actual suspend fun evaluate(script: String, timeoutMs: Long): String =
        throw UnsupportedOperationException(NO_ENGINE)

    actual suspend fun setUserAgent(userAgent: String): Unit =
        throw UnsupportedOperationException(NO_ENGINE)

    actual suspend fun clearCookies(): Unit = throw UnsupportedOperationException(NO_ENGINE)

    actual suspend fun cookieHeader(): String = throw UnsupportedOperationException(NO_ENGINE)

    actual fun destroy() = Unit
}
