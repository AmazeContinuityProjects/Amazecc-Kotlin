package com.amazecc.app.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.amazecc.app.android.nfc.AndroidNfcManager
import com.amazecc.app.shared.services.AndroidApp
import com.amazecc.app.shared.MainView
import com.amazecc.app.shared.nfc.LocalNfcManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.LocalNotificationPermissionManager
import com.amazecc.app.shared.ui.components.NotificationPermissionManager

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

            val notifPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted or denied — no action needed; toggle state handles UX */ }

            val notificationPermissionManager = remember(notifPermissionLauncher) {
                object : NotificationPermissionManager {
                    override fun requestPermission() {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalNfcManager provides nfcManager,
                LocalNotificationPermissionManager provides notificationPermissionManager
            ) {
                MainView()
            }
        }
    }
}
