package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme

/**
 * Stateful host for [M3EContactsScreen].
 *
 * Reads the real whoami `contact` block from [UserStore] — the same snapshot the profile
 * used before D-110 — and forwards email/refresh intents to existing AppState work.
 * No contact row is invented when the payload is absent; the screen then shows its
 * honest empty state.
 */
@Composable
fun M3EContactsHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identity by UserStore.identity.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Same bounded identity warm-up the profile host uses, so a thin identity can fill in.
    LaunchedEffect(Unit) {
        AppState.syncIdentityIfThin()
    }
    AppBackHandler {
        if (!AppState.navigateBack()) AppState.navigateTo(Screen.MORE)
    }

    val contacts = remember(identity.contacts) {
        identity.contacts.mapIndexed { index, contact ->
            M3EContactCard(
                id = "${contact.office.ifBlank { "contact" }}-$index",
                office = contact.office,
                description = contact.description,
                email = contact.email?.takeIf { it.isNotBlank() }
            )
        }
    }

    val state = M3EContactsState(
        contacts = contacts,
        isLoading = isLoading && contacts.isEmpty(),
        errorMessage = error
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EContactsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    M3EContactsAction.Refresh -> AppState.refreshProfile()
                    is M3EContactsAction.OpenEmail -> {
                        try {
                            uriHandler.openUri("mailto:${action.email}")
                        } catch (_: Exception) {
                            // No usable mail app: the address is still visible to copy.
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}
