package com.amazecc.app.shared.ui.screens.m3e

import amazecc_app.shared.generated.resources.Res
import amazecc_app.shared.generated.resources.ic_launcher
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.milliseconds

/**
 * Material 3 Expressive launch screen.
 *
 * All side effects (the timed navigation) stay in this stateful wrapper; [M3ESplashContent]
 * is stateless and renders only from its inputs.
 */
@Composable
fun M3ESplashScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // D-073: `/?signin=1` is represented by the entry intent already parked by App/AppState.
        // Honour it before the timed reveal so a return trip reaches the login form immediately;
        // "Try demo" enters the demo workspace instead of sitting behind the splash.
        when (AppState.pendingEntryIntent.value) {
            AppState.EntryIntent.LOGIN -> {
                AppState.closeGuestLanding()
                AppState.clearPendingEntryIntent()
                AppState.navigateTo(Screen.LOGIN)
                return@LaunchedEffect
            }
            AppState.EntryIntent.DEMO -> {
                AppState.closeGuestLanding()
                AppState.clearPendingEntryIntent()
                AppState.enterDemoMode()
                return@LaunchedEffect
            }
            null -> Unit
        }
        // A parked sign-in return from a previous session also opens the login form first.
        if (SignInReturn.peek() != null) {
            AppState.closeGuestLanding()
            AppState.navigateTo(Screen.LOGIN)
            return@LaunchedEffect
        }
        delay(300.milliseconds)
        revealed = true
        delay(1200.milliseconds)
        AppState.navigateTo(Screen.LOGIN)
    }

    AmazeM3ETheme(isDark = isDark) {
        M3ESplashContent(revealed = revealed, modifier = modifier)
    }
}

@Composable
private fun M3ESplashContent(revealed: Boolean, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.86f,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "SplashLogoScale"
    )
    val shape = RoundedCornerShape(28.dp) // M3E extra-extra-large increased token

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(shape)
                    .background(colors.primaryContainer)
                    .border(1.dp, colors.outlineVariant, shape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = "AmazeCC Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "AmazeCC",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = colors.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "UNIFIED STUDENT OPERATING SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "An unofficial community initiative",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun M3ESplashScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ESplashContent(revealed = true)
    }
}
