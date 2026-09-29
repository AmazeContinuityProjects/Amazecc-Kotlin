package com.amazecc.app.shared.vtop

/** False on platforms with no engine implementation; the app then keeps using `VtopSource.REMOTE`. */
expect val vtopEngineSupported: Boolean

/**
 * A real browser engine with a cookie jar and a JavaScript runtime.
 *
 * The WebView (or WKWebView) is never displayed. It exists for three reasons that a plain HTTP
 * client cannot cover:
 *
 *  1. `JSESSIONID` is managed by the platform cookie store and must be replayed across requests.
 *  2. `_csrf` and `authorizedIDX` are only available from the *rendered* DOM.
 *  3. VTOP may serve a reCAPTCHA, which needs a genuine page context to execute.
 *
 * Android is the only target with a real implementation. The iOS actual is a stub — see
 * `docs/sep-29-2026/vtop-local-integration-plan.md` section 5.
 */
expect class VtopEngine() {

    /** Creates the underlying view. Safe to call repeatedly. */
    suspend fun initialize()

    /** Navigates to [path] relative to the VTOP `/vtop` base. */
    suspend fun load(path: String)

    /**
     * Runs [script] and returns its raw return value as text.
     *
     * Implementations must un-escape the platform's JSON-encoded result, so callers get back
     * exactly the string the script returned.
     */
    suspend fun evaluate(script: String, timeoutMs: Long = 15_000): String

    suspend fun setUserAgent(userAgent: String)

    /** Drops all cookies, including `JSESSIONID`. */
    suspend fun clearCookies()

    /** Current cookies as a `Cookie` header value. Diagnostics only. */
    suspend fun cookieHeader(): String

    fun destroy()
}
