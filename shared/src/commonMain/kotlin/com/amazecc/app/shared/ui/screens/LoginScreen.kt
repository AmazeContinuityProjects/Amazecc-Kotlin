package com.amazecc.app.shared.ui.screens

import amazecc_app.shared.generated.resources.Res
import amazecc_app.shared.generated.resources.ic_launcher
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeTextField
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(true) }
    var logoScale by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val creds = SettingsManager.getCredentials()
        if (creds != null) {
            username = creds.first
            password = creds.second
        }
        if (creds != null && AppState.restoreSession()) {
            val target = if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING
            AppState.navigateTo(target)
        } else {
            if (creds != null && SessionManager.authorizedID.value != null) {
                scope.launch {
                    isSubmitting = true
                    val response = AmazeClient.login(creds.first, creds.second)
                    if (response.success && response.cookies != null && response.csrf != null && response.authorizedID != null) {
                        SessionManager.saveSession(response.cookies, response.csrf, response.authorizedID, response.clubToken)
                        SettingsManager.setString(SettingsManager.SESSION_COOKIES, response.cookies)
                        SettingsManager.setString(SettingsManager.SESSION_CSRF, response.csrf)
                        SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, response.authorizedID)
                        response.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                        val target = if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING
                        AppState.navigateTo(target)
                    }
                    isSubmitting = false
                }
            }
        }
        isRestoring = false
        delay(100.milliseconds)
        logoScale = 1f
    }

    val radialGradient = remember {
                Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(500f, 200f),
                    radius = 800f
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(radialGradient)
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale)
                    .shadow(12.dp, RoundedCornerShape(AmazeTheme.radius.large))
                    .clip(RoundedCornerShape(AmazeTheme.radius.large))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(colors.accent, colors.accent.copy(alpha = 0.6f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.large)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = Strings.appName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))

            Text(
                text = Strings.appName,
                style = AmazeTheme.typography.display.copy(
                    color = colors.textPrimary,
                    fontSize = AmazeTheme.fontSize.x3l,
                    fontWeight = FontWeight.Black
                )
            )

            Text(
                text = "Unified Student Operating System",
                style = AmazeTheme.typography.caption.copy(
                    color = colors.textSecondary,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(Modifier.height(36.dp))

            // Error banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.dangerSurface)
                        .border(1.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.small))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = colors.dangerText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(
                            text = errorMessage ?: "",
                            style = AmazeTheme.typography.caption.copy(
                                color = colors.dangerText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Form card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(AmazeTheme.radius.large))
                    .clip(RoundedCornerShape(AmazeTheme.radius.large))
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.large))
                    .padding(22.dp)
            ) {
                Column {
                    Text(
                        text = "Welcome back",
                        style = AmazeTheme.typography.subheading.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Sign in to your VTOP account",
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.textMuted
                        )
                    )

                    Spacer(Modifier.height(22.dp))

                    AmazeTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = Strings.login,
                        placeholder = "e.g. FUNNYBABY",
                        leadingIcon = {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(Modifier.height(AmazeTheme.spacing.md))

                    AmazeTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = Strings.password,
                        placeholder = Strings.password,
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) Strings.ok else "Show password",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )

                    Spacer(Modifier.height(AmazeTheme.spacing.md))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircleOutline,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AmazeTheme.spacing.xs))
                        Text(
                            text = "Captcha is solved automatically",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.textMuted
                            )
                        )
                    }

                    Spacer(Modifier.height(AmazeTheme.spacing.lg))

                    AmazeButton(
                        text = if (isSubmitting) Strings.loggingIn else Strings.signIn,
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Registration number and password are required"
                                return@AmazeButton
                            }

                            scope.launch {
                                isSubmitting = true
                                errorMessage = null
                                try {
                                    val response = AmazeClient.login(username, password)
                                    if (response.success && response.cookies != null && response.csrf != null && response.authorizedID != null) {
                                        AmazeClient.setUseMockData(false)
                                        SessionManager.saveSession(
                                            cookies = response.cookies,
                                            csrf = response.csrf,
                                            authorizedID = response.authorizedID,
                                            clubToken = response.clubToken
                                        )
                                        SettingsManager.setString(SettingsManager.SESSION_COOKIES, response.cookies)
                                        SettingsManager.setString(SettingsManager.SESSION_CSRF, response.csrf)
                                        SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, response.authorizedID)
                                        response.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                                        SettingsManager.saveCredentials(username, password)
                                        AppState.navigateTo(if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING)
                                    } else {
                                        errorMessage = if (response.message.contains("401") || response.message.contains("Unauthorized", ignoreCase = true)) {
                                            "Invalid credentials. Please check your Registration Number and Password."
                                        } else {
                                            response.message.ifBlank { "Authentication failed. Please try again." }
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Connection error: ${e.message}"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward
                    )
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.lg))

            TextButton(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        errorMessage = null
                        AppState.enterDemoMode { success, message ->
                            if (!success) errorMessage = message
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
            ) {
                Text(
                    text = "Explore in Demo Mode",
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Text(
                text = "AmazeCC • An unofficial community initiative",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(AmazeTheme.spacing.lg))
        }
    }
}
