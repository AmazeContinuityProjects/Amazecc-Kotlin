package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.screens.academics.AddTaskDialog

@Composable
fun DashboardScreen() {
    var showManualUpdateResult by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
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
                        onDownload = {
                            AppState.dismissUpdateDialog()
                            uriHandler.openUri(status.release.htmlUrl)
                        }
                    )
                }
                is AppState.UpdateStatus.UpToDate -> {
                    if (showManualUpdateResult) {
                        UpdateResultDialog(
                            status = status,
                            onDismiss = {
                                showManualUpdateResult = false
                                AppState.checkForUpdate()
                            }
                        ) { }
                    }
                }
                is AppState.UpdateStatus.Error -> {
                    if (showManualUpdateResult) {
                        UpdateResultDialog(
                            status = status,
                            onDismiss = {
                                showManualUpdateResult = false
                                AppState.checkForUpdate()
                            }
                        ) { }
                    }
                }
                else -> {}
            }
        },
        commandPaletteTrigger = {
            if (showCommandPalette) {
                AppState.openCommandPalette()
                showCommandPalette = false
            }
        },
        addTaskDialog = {
            if (showAddTaskDialog) {
                AddTaskDialog(onDismiss = { showAddTaskDialog = false })
            }
        }
    )
}
