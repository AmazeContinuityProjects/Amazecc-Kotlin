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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.amazecc.app.android.nfc.AndroidNfcManager
import com.amazecc.app.shared.services.AndroidApp
import com.amazecc.app.shared.MainView
import com.amazecc.app.shared.nfc.LocalNfcManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.components.LocalNotificationPermissionManager
import com.amazecc.app.shared.ui.components.NotificationPermissionManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.attachActivity(this)
        val nfcManager = AndroidNfcManager(this)
        
        // D-193: both delivery paths use the one pending queue. `target_screen` is encoded by
        // AppState into an amazecc://tab link so the same parser, navigation and clearing rules
        // apply; direct navigateTo here would bypass that queue and lose invite handling.
        deliver(intent?.data?.toString(), intent?.getStringExtra("target_screen"))

        setContent {
            val isAppLibraryOpen by AppState.isAppLibraryOpen.collectAsState()
            val pendingLink by AppState.incomingDeepLink.collectAsState()
            LaunchedEffect(pendingLink) {
                if (pendingLink.isNullOrBlank()) return@LaunchedEffect
                // Cold start: let App()'s one-shot startup effects (durable history restore and
                // guest landing) settle first, or they would overwrite the deep-link destination.
                delay(150)
                AppState.consumeIncomingDeepLink()
            }

            BackHandler(enabled = AppState.canNavigateBack() || isAppLibraryOpen) {
                if (isAppLibraryOpen) {
                    AppState.closeAppLibrary()
                } else {
                    AppState.navigateBack()
                }
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

    /** One delivery point for launch intents and `onNewIntent` links (D-193). */
    private fun deliver(dataUri: String?, targetScreen: String?) {
        if (!dataUri.isNullOrBlank() || !targetScreen.isNullOrBlank()) {
            AppState.setPendingDeepLink(uri = dataUri, targetScreen = targetScreen)
        }
    }

    override fun onNewIntent(newIntent: android.content.Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        deliver(newIntent.data?.toString(), newIntent.getStringExtra("target_screen"))
    }

    override fun onResume() {
        super.onResume()
        // Foreground catch-up: an Activity now exists, so a due sync can perform the strict fresh
        // VTOP login that a background alarm sweep deliberately cannot. No-op when nothing is due.
        AppState.checkDueSync()
    }

    override fun onDestroy() {
        AndroidApp.detachActivity(this)
        super.onDestroy()
    }
}
