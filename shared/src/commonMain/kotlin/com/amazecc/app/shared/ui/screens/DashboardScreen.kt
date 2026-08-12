package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.components.*

@Composable
fun DashboardScreen() {
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