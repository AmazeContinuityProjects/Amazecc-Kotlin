package com.amazecc.app.shared.services

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidApp {
    private var _context: Context? = null

    val context: Context?
        get() = _context

    val isInitialized: Boolean
        get() = _context != null

    fun init(ctx: Context) {
        _context = ctx.applicationContext
    }
}
