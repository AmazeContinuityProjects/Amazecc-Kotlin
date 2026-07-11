package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeTextField
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ButtonVariant
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val onboardingCompleted by SessionManager.onboardingCompleted.collectAsState()
    if (onboardingCompleted) {
        AuthScreen()
    } else {
        OnboardingScreen(onFinish = { SessionManager.completeOnboarding() })
    }
}

@Composable
fun OnboardingIllustration(pageIndex: Int, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    
    val infiniteTransition = rememberInfiniteTransition()
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .clip(RoundedCornerShape(radius.extraLarge))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(radius.extraLarge))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (pageIndex) {
            0 -> {
                // Dashboard Illustration
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(radius.medium))
                            .background(colors.elevatedSurface)
                            .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Attendance", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp))
                                Text("85.4%", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { 0.85f },
                                    color = colors.success,
                                    strokeWidth = 3.dp,
                                    trackColor = colors.border,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(radius.small))
                                .background(colors.elevatedSurface)
                                .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GPA", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 8.sp))
                                Text("9.12", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 11.sp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(radius.small))
                                .background(colors.elevatedSurface)
                                .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Credits", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 8.sp))
                                Text("124", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
            1 -> {
                // Sync Illustration
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                rotationZ = rotationAngle
                            }
                            .border(1.5.dp, colors.accent.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.elevatedSurface)
                            .border(1.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    val dotOffsets = listOf(
                        Pair((-35).dp, (-35).dp),
                        Pair(40.dp, (-25).dp),
                        Pair((-30).dp, 35.dp),
                        Pair(35.dp, 30.dp)
                    )
                    dotOffsets.forEachIndexed { i, offset ->
                        val floatTransition = rememberInfiniteTransition()
                        val animOffset by floatTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000 + i * 200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = offset.first, y = offset.second + animOffset.dp)
                                .size(6.dp)
                                .background(colors.accent, CircleShape)
                        )
                    }
                }
            }
            2 -> {
                // Privacy / Security Illustration
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .border(1.dp, colors.success.copy(alpha = 0.2f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .graphicsLayer {
                                scaleX = 2f - pulseScale
                                scaleY = 2f - pulseScale
                            }
                            .border(1.5.dp, colors.success.copy(alpha = 0.4f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(colors.successSurface)
                            .border(1.5.dp, colors.success, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = colors.successText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedAmbientBackground(modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    val infiniteTransition = rememberInfiniteTransition()
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        drawCircle(
            color = colors.accent.copy(alpha = 0.05f),
            radius = width * 0.4f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.2f + offset1,
                y = height * 0.3f + offset2
            )
        )
        
        drawCircle(
            color = colors.info.copy(alpha = 0.04f),
            radius = width * 0.5f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.8f + offset2,
                y = height * 0.7f + offset1
            )
        )
    }
}

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = AmazeTheme.colors
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            title = "AmazeCC Student OS",
            description = "A mobile-first workspace tailored specifically for VIT Chennai student life.",
            icon = Icons.Rounded.PhoneAndroid,
            bullets = listOf("Academics, attendance, timetable & marks", "Intelligent dashboards centered around your day")
        ),
        OnboardingPage(
            title = "Intelligent Sync",
            description = "Pulls academic data from VTOP and caches it locally so you can browse offline.",
            icon = Icons.Rounded.CloudDone,
            bullets = listOf("Active progress feedback during session syncs", "Instant data access with zero loading delay")
        ),
        OnboardingPage(
            title = "Encrypted & Secure",
            description = "Your credentials connect directly to the portal and stay safe on your device.",
            icon = Icons.Rounded.PrivacyTip,
            bullets = listOf("DEMO mode available for instant testing", "Clear session securely at any time")
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedAmbientBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text("Skip", color = colors.accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 3 })
                },
                modifier = Modifier.weight(1f)
            ) { activePage ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingIllustration(pageIndex = activePage)
                    
                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = pages[activePage].title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary, 
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pages[activePage].description,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 13.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        pages[activePage].bullets.forEach { bullet ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                    .background(colors.surface.copy(alpha = 0.8f))
                                    .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = bullet,
                                    style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    val selected = index == page
                    val dotColor by animateColorAsState(if (selected) colors.accent else colors.border)
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (selected) 24.dp else 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(dotColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AmazeButton(
                text = if (page == pages.lastIndex) "Get Started" else "Next",
                onClick = {
                    if (page == pages.lastIndex) onFinish() else page += 1
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AuthScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }
    val logoScale = remember { Animatable(1f) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedAmbientBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                        .clickable {
                            scope.launch {
                                logoScale.animateTo(0.9f, spring())
                                logoScale.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", fontSize = 30.sp, fontWeight = FontWeight.Black, color = colors.accent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AmazeCC Mobile",
                        style = AmazeTheme.typography.heading.copy(
                            color = colors.textPrimary, 
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        text = "Official Student Workspace",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 12.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AmazeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeOffset.value }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.elevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.School, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign in to VTOP",
                                style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            )
                            Text(
                                text = "Use credentials to sync active courses",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMessage != null) {
                        ErrorBanner(message = errorMessage ?: "")
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    AmazeTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = "VTOP Registration Number",
                        placeholder = "e.g. 24BCE1022",
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                        isError = errorMessage != null && username.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AmazeTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = "VTOP Password",
                        placeholder = "Password",
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                        isPassword = true,
                        isError = errorMessage != null && password.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AmazeBadge(text = "Security Synced", variant = BadgeVariant.SUCCESS)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-captcha bypass active",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AmazeButton(
                        text = if (isSubmitting) "Syncing Profile..." else "Connect VTOP Session",
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Username and password cannot be empty."
                                scope.launch {
                                    repeat(3) {
                                        shakeOffset.animateTo(12f, spring(stiffness = Spring.StiffnessHigh))
                                        shakeOffset.animateTo(-12f, spring(stiffness = Spring.StiffnessHigh))
                                    }
                                    shakeOffset.animateTo(0f)
                                }
                                return@AmazeButton
                            }
                            scope.launch {
                                isSubmitting = true
                                errorMessage = null
                                logoScale.animateTo(0.95f, tween(100))
                                try {
                                    val response = AmazeClient.login(username, password)
                                    if (response.success && response.cookies != null && response.csrf != null && response.authorizedID != null) {
                                        SessionManager.saveSession(
                                            cookies = response.cookies,
                                            csrf = response.csrf,
                                            authorizedID = response.authorizedID,
                                            clubToken = response.clubToken
                                        )
                                        AmazeClient.setUseMockData(username.lowercase() == "demo" || username.uppercase() == "DEMO123")
                                        AppState.switchTopLevel(Screen.POST_LOGIN_ONBOARDING)
                                    } else {
                                        errorMessage = response.message ?: "Authentication failed. Check your VTOP credentials."
                                        launch {
                                            repeat(3) {
                                                shakeOffset.animateTo(12f, spring(stiffness = Spring.StiffnessHigh))
                                                shakeOffset.animateTo(-12f, spring(stiffness = Spring.StiffnessHigh))
                                            }
                                            shakeOffset.animateTo(0f)
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Network connection failed. Check server status."
                                    launch {
                                        repeat(3) {
                                            shakeOffset.animateTo(12f, spring(stiffness = Spring.StiffnessHigh))
                                            shakeOffset.animateTo(-12f, spring(stiffness = Spring.StiffnessHigh))
                                        }
                                        shakeOffset.animateTo(0f)
                                    }
                                } finally {
                                    isSubmitting = false
                                    logoScale.animateTo(1f, spring())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    )

                    if (isSubmitting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            horizontalArrangement = Arrangement.Center, 
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Establishing secure VTOP handshake...", 
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colors.border)
                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                errorMessage = null
                                try {
                                    val demoRes = AmazeClient.login("DEMO123", "password")
                                    SessionManager.saveSession(
                                        cookies = demoRes.cookies ?: "demo",
                                        csrf = demoRes.csrf ?: "demo",
                                        authorizedID = demoRes.authorizedID ?: "DEMO123",
                                        clubToken = null
                                    )
                                    AmazeClient.setUseMockData(true)
                                    AppState.switchTopLevel(Screen.POST_LOGIN_ONBOARDING)
                                } catch (e: Exception) {
                                    errorMessage = "Demo mode could not start."
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Explore Demo Account", color = colors.accent, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "VTOP credentials are used only to establish session cookies. Antigravity does not collect or store your password on any remote server.",
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 11.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.dangerSurface)
            .border(1.dp, colors.danger, RoundedCornerShape(AmazeTheme.radius.small))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = colors.dangerText, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = AmazeTheme.typography.caption.copy(color = colors.dangerText, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

private data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val bullets: List<String>
)
