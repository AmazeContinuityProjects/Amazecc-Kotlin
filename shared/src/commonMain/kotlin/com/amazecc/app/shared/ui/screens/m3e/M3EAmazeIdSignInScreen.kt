package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private enum class AmazeIdMode(val label: String) {
    DEVICE("Device code"), CODE("Code + PKCE"), TOKEN("Paste token")
}

/**
 * D-002 — reachable Amaze ID sign-in surface. Self-contained: it reads and
 * writes SettingsManager/SessionManager directly, so it can be embedded in M3E
 * Settings, the login screen or a dialog without any new action/state plumbing.
 *
 * Every button performs a real network operation through [AmazeIdAuth]; a token
 * is persisted only after userinfo validates it. No fake success state exists.
 */
@Composable
fun M3EAmazeIdSignInScreen(modifier: Modifier = Modifier) {
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        val scope = rememberCoroutineScope()
        var baseUrlText by remember { mutableStateOf(AmazeIdAuth.baseUrl()) }
        var clientIdText by remember { mutableStateOf(AmazeIdAuth.clientId()) }
        var redirectUriText by remember { mutableStateOf(AmazeIdAuth.redirectUri()) }
        var mode by remember { mutableStateOf(AmazeIdMode.DEVICE) }
        var busy by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var signedInLabel by remember { mutableStateOf<String?>(null) }

        val accessToken by SessionManager.amazeAccessToken.collectAsState()
        val isSignedIn = !accessToken.isNullOrBlank()

        // Device flow state
        var device by remember { mutableStateOf<AmazeIdAuth.DeviceCodeRes?>(null) }
        var secondsLeft by remember { mutableStateOf(0L) }

        // Authorization-code flow state
        var authorizeUrl by remember { mutableStateOf<String?>(null) }
        var shownState by remember { mutableStateOf<String?>(null) }
        var callbackInput by remember { mutableStateOf("") }

        // Direct-token flow state
        var tokenInput by remember { mutableStateOf("") }
        var dpopInput by remember { mutableStateOf("") }
        var confirmationInput by remember { mutableStateOf("") }

        // Poll the device token endpoint while a device code is active.
        LaunchedEffect(device?.device_code) {
            val code = device?.device_code ?: return@LaunchedEffect
            val expires = device?.expires_in ?: 600L
            var interval = (device?.interval ?: 5L).coerceIn(2, 30)
            val started = Clock.System.now().toEpochMilliseconds()
            while (Clock.System.now().toEpochMilliseconds() - started < expires * 1000) {
                delay(interval * 1000)
                when (val poll = AmazeIdAuth.pollDeviceToken(code)) {
                    is AmazeIdAuth.DevicePoll.Pending -> Unit
                    is AmazeIdAuth.DevicePoll.SlowDown -> interval = poll.seconds
                    is AmazeIdAuth.DevicePoll.SignedIn -> {
                        signedInLabel = poll.displayName
                        device = null
                        info = "Signed in as ${poll.displayName}. Guarded AmazeCC endpoints will now work."
                        error = null
                        return@LaunchedEffect
                    }
                    is AmazeIdAuth.DevicePoll.Failed -> {
                        device = null
                        error = poll.message
                        return@LaunchedEffect
                    }
                }
                secondsLeft = maxOf(0, expires - (Clock.System.now().toEpochMilliseconds() - started) / 1000)
            }
            device = null
            error = "The sign-in code expired. Start a new device sign-in."
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    SectionLabel("AMAZE ID", Icons.Rounded.Lock)
                    Text(
                        text = "One Amaze ID unlocks the guarded AmazeCC services (social, cab share, QBank upload, profile). " +
                            "This is a real OIDC sign-in against auth.amazecc.com — no local shortcut.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    // D-109: Amaze ID is a different auth from VTOP. Signing in here does NOT store VTOP
                    // credentials, so sync would still fail with "session expired" — say so up front.
                    Text(
                        text = "Note: Amaze ID is separate from your VTOP login. Timetable/attendance/marks sync requires " +
                            "a separate VTOP sign-in from the main Sign In screen; signing in here does not enable sync.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    if (isSignedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = signedInLabel ?: "Amaze ID token saved on this device",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary,
                            )
                        }
                        TextButton(onClick = {
                            AmazeIdAuth.signOut()
                            signedInLabel = null
                            device = null
                            info = "Amaze ID signed out on this device."
                        }) {
                            Text("Sign out of Amaze ID", color = colors.error)
                        }
                    } else {
                        Text("Status: not signed in", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }

            // Server / target picker
            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    SectionLabel("TARGET SERVER", Icons.Rounded.OpenInNew)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeIdAuth.SERVERS.forEach { server ->
                            FilterChip(
                                selected = baseUrlText == server.baseUrl,
                                onClick = {
                                    baseUrlText = server.baseUrl
                                    AmazeIdAuth.setBaseUrl(server.baseUrl)
                                    device = null
                                    authorizeUrl = null
                                    error = null
                                },
                                label = { Text(server.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                        FilterChip(
                            selected = AmazeIdAuth.isCustomBaseUrl,
                            onClick = { error = null },
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    OutlinedTextField(
                        value = baseUrlText,
                        onValueChange = {
                            baseUrlText = it
                            AmazeIdAuth.setBaseUrl(it)
                            device = null
                            authorizeUrl = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Auth server base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(colors),
                    )
                    OutlinedTextField(
                        value = clientIdText,
                        onValueChange = {
                            clientIdText = it
                            AmazeIdAuth.setClientId(it)
                            device = null
                            authorizeUrl = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OAuth client ID") },
                        supportingText = {
                            Text(
                                "Default: amazecc-web. A native client registered with the device_code grant is recommended (see docs).",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(colors),
                    )
                    OutlinedTextField(
                        value = redirectUriText,
                        onValueChange = {
                            redirectUriText = it
                            AmazeIdAuth.setRedirectUri(it)
                            device = null
                            authorizeUrl = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Registered redirect URI (native handler)") },
                        supportingText = {
                            Text(
                                "Must match a redirect URI registered on the OAuth client, e.g. amazecc://auth/callback. " +
                                    "Device sign-in does not use it; code + PKCE does.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(colors),
                    )
                }
            }

            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                    SectionLabel("SIGN-IN METHOD", Icons.Rounded.Lock)
                    M3EConnectedButtonGroup(
                        items = AmazeIdMode.entries,
                        selectedItem = mode,
                        onItemSelected = { mode = it; error = null; info = null },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (mode) {
                        AmazeIdMode.DEVICE -> DeviceSection(
                            busy = busy,
                            device = device,
                            secondsLeft = secondsLeft,
                            onStart = {
                                busy = true; error = null; info = null
                                scope.launch {
                                    val result = AmazeIdAuth.requestDeviceCode()
                                    busy = false
                                    result.onSuccess {
                                        device = it
                                        secondsLeft = it.expires_in
                                    }.onFailure { error = it.message ?: "Could not start device sign-in." }
                                }
                            },
                            onOpen = { device?.let { openExternalUrl(it.verification_uri_complete ?: it.verification_uri) } },
                            onShare = { device?.let { shareText("Amaze ID code: ${it.user_code}\n${it.verification_uri}") } },
                        )

                        AmazeIdMode.CODE -> AuthorizationCodeSection(
                            busy = busy,
                            authorizeUrl = authorizeUrl,
                            shownState = shownState,
                            callbackInput = callbackInput,
                            onCallbackChange = { callbackInput = it },
                            onBegin = {
                                error = null; info = null
                                val challenge = AmazeIdAuth.beginAuthorizationCode()
                                authorizeUrl = challenge.authorizeUrl
                                shownState = challenge.state
                                openExternalUrl(challenge.authorizeUrl)
                            },
                            onSubmit = {
                                busy = true; error = null; info = null
                                scope.launch {
                                    val outcome = runCatching {
                                        val raw = callbackInput.trim()
                                        if (raw.startsWith('{')) {
                                            val token = AmazeIdAuth.parseTokenResponse(raw).getOrThrow()
                                            AmazeIdAuth.validateAndStore(token.access_token, null, token.confirmation).getOrThrow()
                                        } else {
                                            val callback = AmazeIdAuth.parseCallback(raw).getOrThrow()
                                            AmazeIdAuth.verifyState(callback.state).getOrThrow()
                                            val token = AmazeIdAuth.exchangeAuthorizationCode(callback.code).getOrThrow()
                                            AmazeIdAuth.validateAndStore(token.access_token, null, token.confirmation).getOrThrow()
                                        }
                                    }
                                    busy = false
                                    outcome.onSuccess {
                                        signedInLabel = it
                                        authorizeUrl = null
                                        callbackInput = ""
                                        info = "Signed in as $it."
                                    }.onFailure { error = it.message ?: "Sign-in failed." }
                                }
                            },
                        )

                        AmazeIdMode.TOKEN -> TokenSection(
                            busy = busy,
                            tokenInput = tokenInput,
                            dpopInput = dpopInput,
                            confirmationInput = confirmationInput,
                            onTokenChange = { tokenInput = it },
                            onDpopChange = { dpopInput = it },
                            onConfirmationChange = { confirmationInput = it },
                            onSubmit = {
                                if (tokenInput.isBlank()) {
                                    error = "Paste an access token first."
                                    return@TokenSection
                                }
                                busy = true; error = null; info = null
                                scope.launch {
                                    val result = AmazeIdAuth.validateAndStore(
                                        tokenInput.trim(),
                                        dpopInput.takeIf { it.isNotBlank() },
                                        confirmationInput.takeIf { it.isNotBlank() },
                                    )
                                    busy = false
                                    result.onSuccess {
                                        signedInLabel = it
                                        tokenInput = ""
                                        info = "Token verified with Amaze ID. Signed in as $it."
                                    }.onFailure { error = it.message ?: "Token validation failed." }
                                }
                            },
                        )
                    }

                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
                            Text("Contacting Amaze ID…", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                    info?.let { FeedbackBanner(it, isError = false) }
                    error?.let { FeedbackBanner(it, isError = true) }

                    Text(
                        text = "Platform note: the browser's registered /auth/callback/ redirect and IndexedDB DPoP key are not " +
                            "available to a native KMP app. Device code and pasted-code PKCE are the honest native equivalents; " +
                            "both exchange real tokens at the live auth server. DPoP-bound tokens are supported by pasting the " +
                            "proof header and confirmation value in the token tab.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSection(
    busy: Boolean,
    device: AmazeIdAuth.DeviceCodeRes?,
    secondsLeft: Long,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = M3ETheme.colors
    if (device == null) {
        ActionButton("Start device sign-in", busy, onStart)
        Text(
            "You will receive a short code to approve in any browser. No app callback handler is required.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    } else {
        Text("Enter this code at the sign-in page:", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(
            device.user_code,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
            color = colors.primary,
        )
        if (secondsLeft > 0) {
            Text("Expires in ${secondsLeft}s", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedButton(onClick = onOpen, shape = CircleShape) {
                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open sign-in page")
            }
            ElevatedButton(onClick = onShare, shape = CircleShape) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share code")
            }
        }
        Text("Waiting for approval… this screen updates automatically.", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        TextButton(onClick = onStart, enabled = !busy) { Text("Get a new code") }
    }
}

@Composable
private fun AuthorizationCodeSection(
    busy: Boolean,
    authorizeUrl: String?,
    shownState: String?,
    callbackInput: String,
    onCallbackChange: (String) -> Unit,
    onBegin: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = M3ETheme.colors
    ActionButton("Open Amaze ID sign-in (PKCE)", busy, onBegin)
    authorizeUrl?.let {
        Text("If the browser did not open, copy this URL:", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.primary)
        Text("State: ${shownState.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
    OutlinedTextField(
        value = callbackInput,
        onValueChange = onCallbackChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste callback URL, ?code=&state=, or token JSON") },
        supportingText = { Text("The state must match the value shown above.", style = MaterialTheme.typography.labelSmall) },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(colors),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    ActionButton("Exchange code & verify", busy, onSubmit)
}

@Composable
private fun TokenSection(
    busy: Boolean,
    tokenInput: String,
    dpopInput: String,
    confirmationInput: String,
    onTokenChange: (String) -> Unit,
    onDpopChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = tokenInput,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Access token") },
        visualTransformation = PasswordVisualTransformation(),
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(colors),
    )
    OutlinedTextField(
        value = dpopInput,
        onValueChange = onDpopChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("DPoP proof header (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(colors),
    )
    OutlinedTextField(
        value = confirmationInput,
        onValueChange = onConfirmationChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("DPoP confirmation / cnf JSON (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(colors),
    )
    Text(
        "The token is validated with a live userinfo call before it is saved. A DPoP-bound token needs both the proof header and confirmation value.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
    )
    ActionButton("Verify & save token", busy, onSubmit)
}

@Composable
private fun ActionButton(label: String, busy: Boolean, onClick: () -> Unit) {
    val colors = M3ETheme.colors
    ElevatedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = CircleShape,
        colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun FeedbackBanner(text: String, isError: Boolean) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) colors.errorContainer else colors.primaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isError) colors.error else colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) colors.error else colors.onPrimaryContainer)
    }
}

@Composable
private fun SectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = M3ETheme.colors.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = M3ETheme.colors.onSurface)
    }
}

@Composable
private fun fieldColors(colors: M3EColorPalette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.onSurface,
    unfocusedTextColor = colors.onSurface,
    focusedContainerColor = colors.surfaceContainerHigh,
    unfocusedContainerColor = colors.surfaceContainerLow,
    focusedBorderColor = colors.primary,
    unfocusedBorderColor = colors.outlineVariant,
    focusedLabelColor = colors.primary,
    unfocusedLabelColor = colors.onSurfaceVariant,
)
