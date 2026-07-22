package com.amazecc.app.shared.services

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
}
