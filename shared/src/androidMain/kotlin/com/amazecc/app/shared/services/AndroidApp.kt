package com.amazecc.app.shared.services

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidApp {
    private var _context: Context? = null
    private var _activity: android.app.Activity? = null

    val context: Context?
        get() = _context

    /**
     * The foreground Activity, when one exists. Needed by [com.amazecc.app.shared.vtop.VtopEngine]
     * because a WebView built from the application context cannot be attached to a window,
     * which stops `evaluateJavascript` from running reliably.
     */
    val activity: android.app.Activity?
        get() = _activity

    val isInitialized: Boolean
        get() = _context != null

    fun init(ctx: Context) {
        _context = ctx.applicationContext
    }

    fun attachActivity(activity: android.app.Activity) {
        _activity = activity
    }

    fun detachActivity(activity: android.app.Activity) {
        if (_activity === activity) _activity = null
    }
}
