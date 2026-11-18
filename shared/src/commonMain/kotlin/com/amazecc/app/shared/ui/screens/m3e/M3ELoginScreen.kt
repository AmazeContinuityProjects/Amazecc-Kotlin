package com.amazecc.app.shared.ui.screens.m3e

import amazecc_app.shared.generated.resources.Res
import amazecc_app.shared.generated.resources.ic_launcher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.amazecc.app.shared.ui.components.VtopCaptchaPrompt
import com.amazecc.app.shared.ui.components.VtopMigrationPrompt
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.vtop.AutoCaptchaHandler
import com.amazecc.app.shared.vtop.UiCaptchaHandler
import com.amazecc.app.shared.vtop.Vtop
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * Material 3 Expressive Login Screen for AmazeCC.
 * Built with bold asymmetric hero shapes, expressive container levels,
 * responsive typography, full credential lifecycle management, on-device scraper
 * toggle, automated captcha handling, and migration notice.
 */
@Composable
fun M3ELoginScreen(
    modifier: Modifier = Modifier
) {
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var manualCaptcha by remember { mutableStateOf(SettingsManager.isVtopManualCaptcha()) }
    var captchaText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vtopSource by remember { mutableStateOf(AmazeClient.vtopSource) }

    val captchaRequest by Vtop.captchaRequest.collectAsState()
    val isLocalVtop = vtopSource == VtopSource.LOCAL

    val captchaHandler = remember {
        AutoCaptchaHandler(
            delegate = UiCaptchaHandler,
            forceManual = { manualCaptcha || SettingsManager.isVtopManualCaptcha() },
            manualAnswer = { captchaText }
        )
    }

    var showMigrationPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!SettingsManager.isVtopSourcePrompted() && Vtop.isSupported) {
            showMigrationPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        val creds = SettingsManager.getCredentials()
        if (creds != null) {
            username = creds.first
            password = creds.second
        }
        if (creds != null && AppState.restoreSession()) {
            val target = if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING
            AppState.navigateTo(target)
        } else if (creds != null && SessionManager.authorizedID.value != null) {
            scope.launch {
                isSubmitting = true
                val response = AmazeClient.login(creds.first, creds.second, captchaHandler)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Hero Brand Identity Header
            item(key = "hero_brand") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                ) {
                    // Expressive asymmetric launcher container
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 12.dp))
                            .background(colors.primaryContainer)
                            .border(
                                width = 1.5.dp,
                                color = colors.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_launcher),
                            contentDescription = "AmazeCC Logo",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Text(
                        text = "AmazeCC",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = colors.onBackground
                    )

                    Text(
                        text = "Unified Student Operating System",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // 2. Error Message Banner
            if (errorMessage != null) {
                item(key = "error_banner") {
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.errorContainer)
                                .border(1.dp, colors.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.error
                                )
                            }
                        }
                    }
                }
            }

            // 3. Main Login Form Card
            item(key = "login_form_card") {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    cornerRadius = 28.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "VTOP Student Portal",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.onSurface
                            )
                            Text(
                                text = "Sign in to securely sync timetable, attendance and grades",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        // Registration Number Field
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.uppercase()
                                errorMessage = null
                            },
                            label = { Text("Registration Number") },
                            placeholder = { Text("e.g. 21BCE1042") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outlineVariant,
                                focusedContainerColor = colors.surfaceContainerHigh,
                                unfocusedContainerColor = colors.surfaceContainerLow,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.onSurfaceVariant,
                                focusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text("VTOP Password") },
                            placeholder = { Text("Enter your password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outlineVariant,
                                focusedContainerColor = colors.surfaceContainerHigh,
                                unfocusedContainerColor = colors.surfaceContainerLow,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.onSurfaceVariant,
                                focusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            )
                        )

                        // VTOP Engine Selector (On-Device Local Scraper vs Remote API Cloud)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLocalVtop) Icons.Rounded.Devices else Icons.Rounded.Cloud,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (isLocalVtop) "On-Device Engine (Local)" else "Remote API Gateway",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = if (isLocalVtop) "Zero credentials leave this phone" else "Cloud-accelerated proxy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        val newSource = if (isLocalVtop) VtopSource.REMOTE else VtopSource.LOCAL
                                        AmazeClient.setVtopSource(newSource)
                                        vtopSource = newSource
                                    }
                                ) {
                                    Text(
                                        text = if (isLocalVtop) "Switch to Remote" else "Switch to Local",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primary
                                    )
                                }
                            }
                        }

                        // Local Captcha Options
                        if (isLocalVtop) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ConfirmationNumber,
                                        contentDescription = null,
                                        tint = if (manualCaptcha) colors.warning else colors.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (manualCaptcha) "Type captcha manually" else "Auto-solving captcha",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        manualCaptcha = !manualCaptcha
                                        if (!manualCaptcha) captchaText = ""
                                    }
                                ) {
                                    Text(
                                        text = if (manualCaptcha) "Solve for me" else "Type manually",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primary
                                    )
                                }
                            }

                            if (manualCaptcha) {
                                OutlinedTextField(
                                    value = captchaText,
                                    onValueChange = {
                                        captchaText = it.uppercase().filter(Char::isLetterOrDigit).take(8)
                                    },
                                    label = { Text("Captcha Solution") },
                                    placeholder = { Text("Enter characters") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.onSurface,
                                        unfocusedTextColor = colors.onSurface,
                                        focusedBorderColor = colors.primary,
                                        unfocusedBorderColor = colors.outlineVariant,
                                        focusedContainerColor = colors.surfaceContainerHigh,
                                        unfocusedContainerColor = colors.surfaceContainerLow,
                                        focusedLabelColor = colors.primary,
                                        unfocusedLabelColor = colors.onSurfaceVariant,
                                        focusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                        unfocusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Expressive Pill Sign In Button
                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    errorMessage = "Registration number and password are required"
                                    return@Button
                                }

                                scope.launch {
                                    isSubmitting = true
                                    errorMessage = null
                                    try {
                                        val response = AmazeClient.login(username, password, captchaHandler)
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
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Signing In...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Demo Mode Action Card
            item(key = "demo_mode_button") {
                OutlinedButton(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(colors.outlineVariant)
                    ),
                    enabled = !isSubmitting
                ) {
                    Text(
                        text = "Explore in Demo Mode",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 5. Footer info
            item(key = "footer") {
                Text(
                    text = "AmazeCC • An unofficial community initiative",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }

    // Modal Prompts
    if (showMigrationPrompt) {
        VtopMigrationPrompt(
            onChoose = { source ->
                AmazeClient.setVtopSource(source)
                SettingsManager.setVtopSourcePrompted(true)
                vtopSource = source
                showMigrationPrompt = false
            }
        )
    }

    if (isLocalVtop && captchaRequest != null) {
        VtopCaptchaPrompt(challenge = captchaRequest!!)
    }
    }
}
