package com.amazecc.app.android

import android.app.Application
import com.amazecc.app.shared.services.AndroidApp

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.init(this)
    }
}
