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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import com.amazecc.app.shared.utils.openExternalUrl
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** The real VIT portal — used only by the recovery help link, never a fake reset endpoint. */
private const val VTOP_PORTAL_URL = "https://vtopcc.vit.ac.in/vtop"

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
    // D-120: engine/captcha tuning and recovery guidance are opt-in so the primary
    // sign-in task is not buried under student-facing infrastructure details.
    var showAdvanced by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }

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
            username = creds.first.uppercase()
            password = creds.second
        }
        if (creds != null && AppState.restoreSession()) {
            val target = if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING
            SignInReturn.completeSignIn(target)
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
                    SignInReturn.completeSignIn(target)
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
            // D-120: without imePadding the keyboard can cover the captcha field
            // and the sign-in actions at the bottom of this form.
            modifier = Modifier.fillMaxSize().imePadding(),
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
                        modifier = Modifier.fillMaxWidth(),
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
                            enabled = !isSubmitting,
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
                            enabled = !isSubmitting,
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
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !isSubmitting
                                ) {
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

                        // D-120: engine selection, manual captcha and the captcha
                        // field are advanced tuning, not part of the primary task.
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showAdvanced) "Hide advanced options" else "Advanced: engine & captcha",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary
                            )
                        }
                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    },
                                    enabled = !isSubmitting
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
                                    },
                                    enabled = !isSubmitting
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
                                    enabled = !isSubmitting,
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
                            } // advanced Column
                        } // advanced AnimatedVisibility

                        Spacer(Modifier.height(4.dp))

                        // Expressive Pill Sign In Button
                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter both your VTOP username and password."
                                    return@Button
                                }
                                // Normalise on submit as well as on input, so restored credentials
                                // and programmatic fills cannot bypass the uppercase contract.
                                val normalizedUsername = username.trim().uppercase()
                                username = normalizedUsername

                                scope.launch {
                                    isSubmitting = true
                                    errorMessage = null
                                    try {
                                        val response = AmazeClient.login(normalizedUsername, password, captchaHandler)
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
                                            SettingsManager.saveCredentials(normalizedUsername, password)
                                            SignInReturn.completeSignIn(if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING)
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

            // 4. Sign-in recovery guidance. Real actions only: the portal link opens
            // the official VIT site; the rest is honest guidance (no fake reset).
            item(key = "sign_in_recovery") {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOWEST,
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showRecovery = !showRecovery },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showRecovery) "Hide sign-in help" else "Can't sign in? Recovery help",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary
                            )
                        }
                        AnimatedVisibility(
                            visible = showRecovery,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Forgot your VTOP password? Reset it on the official VIT VTOP portal, then sign in here again. AmazeCC cannot reset VTOP passwords.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { openExternalUrl(VTOP_PORTAL_URL) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Open official VTOP portal",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primary
                                    )
                                }
                                Text(
                                    text = "No registration number? VIT issues it at admission — check your VIT student app, fee receipt, or contact the university helpdesk. AmazeCC cannot issue one; you can explore the app in Demo Mode meanwhile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. Demo Mode Action Card
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
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(colors.outlineVariant)
                    )
                ) {
                    Text(
                        text = "Explore in Demo Mode",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 6. Footer info
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
