package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SocialClient
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * D-193 follow-up — a real destination for the "Sign in with Amaze ID" affordance on Social and
 * Friend Finder.
 *
 * Before this route, `SignInReturn.prepare` parked the return screen and opened [Screen.LOGIN],
 * which is a VTOP-only form: it never persisted an Amaze ID token, so Friend Finder's local
 * session gate reappeared forever. This host reuses the genuinely working sign-in surface
 * ([M3EAmazeIdSignInScreen]) in a sheet and adds an explicit **Done** control. Done only closes
 * the [SignInReturn] round trip when [SocialClient.hasAmazeIdSession] confirms a saved token;
 * otherwise it shows honest "not signed in yet" copy. There is no fake success state.
 *
 * Launcher registration: none lives here. [M3EAmazeIdSignInScreen] uses the plain platform seams
 * (`openExternalUrl` / `shareText`), not an ActivityResult launcher, so there is nothing to hoist
 * out of [M3EDismissibleSheet]'s Dialog subcomposition.
 */
@Composable
fun M3EAmazeIdSignInHost(modifier: Modifier = Modifier) {
    // Collect the token so the status line and the Done control recompose as soon as one of the
    // real sign-in methods in M3EAmazeIdSignInScreen validates and saves a token. The click
    // handler still calls SocialClient.hasAmazeIdSession() fresh — that is the authoritative gate.
    val accessToken by SessionManager.amazeAccessToken.collectAsState()
    val hasSavedToken = !accessToken.isNullOrBlank()
    var prematureDoneMessage by remember { mutableStateOf<String?>(null) }
    val colors = M3ETheme.colors

    // A premature "Done" warning is stale the moment a real token lands.
    LaunchedEffect(hasSavedToken) {
        if (hasSavedToken) prematureDoneMessage = null
    }

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = {
            // Cancelling abandons the round trip; never leave a stale note to hijack a later
            // sign-in. (Done consumes the note itself via SignInReturn.completeSignIn.)
            SignInReturn.clear()
            if (!AppState.navigateBack()) AppState.navigateTo(Screen.HOME)
        },
        title = "Sign in with Amaze ID"
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EAmazeIdSignInScreen()

            Text(
                text = if (hasSavedToken) {
                    "Amaze ID token saved on this device. Tap Done to continue."
                } else {
                    "Not signed in yet. Complete one of the sign-in methods above, then tap Done."
                },
                color = if (hasSavedToken) colors.primary else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            prematureDoneMessage?.let { message ->
                Text(
                    text = message,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    // Never complete the round trip on an optimistic assumption: only a real,
                    // locally saved Amaze ID session may take the student back to their surface.
                    if (SocialClient.hasAmazeIdSession()) {
                        SignInReturn.completeSignIn(Screen.HOME)
                    } else {
                        prematureDoneMessage =
                            "Not signed in yet — no Amaze ID token was saved. Sign in above first."
                    }
                }) {
                    Text("Done")
                }
            }
        }
    }
}
