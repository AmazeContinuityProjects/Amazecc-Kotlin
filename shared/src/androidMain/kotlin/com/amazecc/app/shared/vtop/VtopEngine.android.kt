package com.amazecc.app.shared.vtop

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.amazecc.app.shared.services.AndroidApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

actual val vtopEngineSupported: Boolean = true

/**
 * Offscreen WebView used purely as a cookie jar + JavaScript runtime.
 *
 * The view is attached 1x1 at `INVISIBLE` visibility in the Activity's content view. Attaching
 * matters: a WebView that has never been through the view hierarchy does not reliably dispatch
 * `onPageFinished` or run `evaluateJavascript`. `GONE` is not used because a non-laid-out view
 * can stall the renderer.
 *
 * Loaded page state is cleared (cache + localStorage) but **cookies are kept** on reload, so a
 * retry rides the existing `JSESSIONID` — matching `fkvit`'s `_reloadPage` behaviour.
 */
actual class VtopEngine actual constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var webView: WebView? = null
    private var host: FrameLayout? = null

    /** False when the last [load] timed out before `onPageFinished`. Diagnostics only. */
    private var lastNavigationCompleted = true

    private var loadSignal: CompletableDeferred<Boolean>? = null

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureWebView(): WebView = withContext(Dispatchers.Main) {
        webView?.let { return@withContext it }

        val activity = AndroidApp.activity
            ?: AndroidApp.context?.let { c -> c as? android.app.Activity }
            ?: throw IllegalStateException("No Activity available for the VTOP WebView")

        val ctx: Context = activity
        val wv = WebView(ctx)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            // VTOP's captcha and the prelogin handshake both need a normal browsing profile.
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = UserAgentPool.current()
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loadSignal?.complete(true)
                loadSignal = null
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Sub-resource failures are common on VTOP (missing pdf.js, dashboard.css, and
                // so on) and must not abort navigation.
                if (request?.isForMainFrame == true) {
                    loadSignal?.complete(false)
                    loadSignal = null
                }
            }
        }

        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
            visibility = android.view.View.INVISIBLE
        }
        container.addView(
            wv,
            FrameLayout.LayoutParams(1, 1)
        )

        val decor = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: activity.window.decorView as ViewGroup
        decor.addView(container)

        host = container
        webView = wv
        wv
    }

    actual suspend fun initialize() {
        ensureWebView()
    }

    actual suspend fun load(path: String) {
        val wv = ensureWebView()
        val url = VtopSession.BASE_URL + path
        withContext(Dispatchers.Main) {
            wv.clearCache(true)
            wv.clearHistory()
            val signal = CompletableDeferred<Boolean>()
            loadSignal = signal
            wv.loadUrl(url)
            val finished = withTimeoutOrNull(NAV_TIMEOUT_MS) { signal.await() }
            loadSignal = null
            // A timeout does not throw: the page may still be usable, and the caller's next
            // PAGE_STATE probe reports the truth. Surfacing it as a failure here would mask the
            // more useful "could not read the captcha" style diagnosis.
            lastNavigationCompleted = finished == true
        }
    }

    actual suspend fun evaluate(script: String, timeoutMs: Long): String {
        val wv = ensureWebView()
        return withContext(Dispatchers.Main) {
            val result = withTimeoutOrNull(timeoutMs) {
                CompletableDeferred<String>().also { deferred ->
                    wv.evaluateJavascript(script) { raw ->
                        deferred.complete(unescape(raw))
                    }
                }.await()
            }
            result ?: throw IllegalStateException("evaluateJavascript timed out after ${timeoutMs}ms")
        }
    }

    actual suspend fun setUserAgent(userAgent: String) {
        val wv = ensureWebView()
        withContext(Dispatchers.Main) { wv.settings.userAgentString = userAgent }
    }

    actual suspend fun clearCookies() {
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
        }
    }

    actual suspend fun cookieHeader(): String = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(VtopSession.BASE_URL) ?: ""
    }

    actual fun destroy() {
        val wv = webView
        val container = host
        webView = null
        host = null
        if (container != null) {
            container.removeAllViews()
            (container.parent as? ViewGroup)?.removeView(container)
        }
        wv?.destroy()
    }

    /**
     * `evaluateJavascript` hands back a JSON-encoded value. A script returning the string
     * `{"a":1}` arrives as `"{\"a\":1}"`, so the outer layer has to be unwrapped.
     */
    private fun unescape(raw: String): String {
        if (raw.isEmpty() || raw == "null" || raw == "undefined") return ""
        return try {
            val primitive = json.decodeFromString<JsonPrimitive>(raw)
            primitive.content
        } catch (_: Exception) {
            raw.trim('"')
        }
    }

    private companion object {
        const val NAV_TIMEOUT_MS = 30_000L
    }
}
