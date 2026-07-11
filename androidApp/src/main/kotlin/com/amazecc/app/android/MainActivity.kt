package com.amazecc.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.amazecc.app.android.nfc.AndroidNfcManager
import com.amazecc.app.shared.MainView
import com.amazecc.app.shared.nfc.LocalNfcManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val nfcManager = AndroidNfcManager(this)
        
        setContent {
            CompositionLocalProvider(LocalNfcManager provides nfcManager) {
                MainView()
            }
        }
    }
}
