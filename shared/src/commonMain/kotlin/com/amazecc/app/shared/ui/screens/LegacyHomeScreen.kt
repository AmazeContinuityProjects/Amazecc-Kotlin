package com.amazecc.app.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.components.UpdateDialog
import com.amazecc.app.shared.ui.components.WidgetDashboard

/**
 * The widget dashboard — the home screen this app shipped before the simplified
 * one, kept as a screen rather than deleted.
 *
 * ## What "deprecated" means here
 *
 * It is a normal, reachable destination: [com.amazecc.app.shared.state.Screen.HOME_LEGACY]
 * in the route table, a card in the App Library, and a switch in Settings →
 * Dashboard Layout. Nothing it owns has been removed. The widget order and the
 * hidden-widget set are still persisted under
 * [com.amazecc.app.shared.repository.SettingsManager.KEY_DASHBOARD_WIDGETS] and
 * still drive the widget list here, so a user who spent time arranging this
 * dashboard finds it exactly as they left it.
 *
 * What changed is the default. `Screen.HOME` renders
 * [com.amazecc.app.shared.ui.screens.home.SimplifiedHomeScreen] unless the user
 * has explicitly asked for this one, so the question the home screen answers
 * first is "what is happening today" rather than "here are eight widgets you
 * can configure".
 *
 * The update dialog lives here for the same reason it always did: this screen is
 * where the update check has always been kicked off from, and keeping it here
 * means opting into this dashboard has no side effects on the simplified one.
 */
@Composable
fun LegacyHomeScreen() {
    val updateStatus by AppState.updateStatus.collectAsState()

    LaunchedEffect(Unit) {
        AppState.checkForUpdate()
    }

    WidgetDashboard(
        updateDialog = {
            when (val status = updateStatus) {
                is AppState.UpdateStatus.Available -> {
                    UpdateDialog(
                        release = status.release,
                        currentVersion = status.currentVersion,
                        onDismiss = { AppState.dismissUpdateDialog() },
                        onDownload = { AppState.dismissUpdateDialog() }
                    )
                }
                else -> {}
            }
        }
    )
}
