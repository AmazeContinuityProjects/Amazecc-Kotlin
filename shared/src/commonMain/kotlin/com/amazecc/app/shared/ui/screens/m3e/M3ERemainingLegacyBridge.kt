package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stateful host for the last routed legacy screen without an M3E equivalent.
 * All side effects live here; the screen itself stays 100% stateless.
 */
@Composable
fun M3ECourseManagementScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var responses by remember { mutableStateOf<Map<String, ArrearResponse>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadAll() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val loaded = mutableMapOf<String, ArrearResponse>()
            M3E_COURSE_MANAGEMENT_ENDPOINTS.forEach { endpoint ->
                loaded[endpoint] = runCatching {
                    withContext(Dispatchers.Default) {
                        when (endpoint) {
                            "course-option-change" -> AmazeClient.getCourseOptionChange()
                            "exc-registration" -> AmazeClient.getExcRegistration()
                            "minor-honour" -> AmazeClient.getMinorHonour()
                            "course-completion" -> AmazeClient.getCourseCompletion()
                            else -> ArrearResponse(success = false, message = "Unknown endpoint")
                        }
                    }
                }.getOrElse { error ->
                    ArrearResponse(success = false, message = error.message, error = error.toString())
                }
            }
            responses = loaded
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    AppBackHandler {
        if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS)
    }

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseManagementScreen(
            state = M3ECourseManagementState(
                selectedTab = selectedTab,
                responses = responses,
                isLoading = isLoading,
                errorMessage = errorMessage
            ),
            onAction = { action ->
                when (action) {
                    is M3ECourseManagementAction.SelectTab -> selectedTab = action.index
                    is M3ECourseManagementAction.Refresh -> loadAll()
                    is M3ECourseManagementAction.Back -> {
                        if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS)
                    }
                }
            },
            modifier = modifier
        )
    }
}
