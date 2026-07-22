package com.amazecc.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amazecc.app.android.nfc.AndroidNfcManager
import com.amazecc.app.shared.services.AndroidApp
import com.amazecc.app.shared.MainView
import com.amazecc.app.shared.nfc.LocalNfcManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.init(this)
        
        val nfcManager = AndroidNfcManager(this)
        
        setContent {
            val currentScreen by AppState.currentScreen.collectAsState()
            val isRootScreen = currentScreen == Screen.HOME || currentScreen == Screen.LOGIN || currentScreen == Screen.SPLASH
            
            BackHandler(enabled = !isRootScreen) {
                AppState.navigateBack()
            }

            CompositionLocalProvider(LocalNfcManager provides nfcManager) {
                MainView()
            }
        }
    }
}
