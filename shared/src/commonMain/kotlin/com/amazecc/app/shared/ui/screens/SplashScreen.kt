package com.amazecc.app.shared.ui.screens

import amazecc_app.shared.generated.resources.Res
import amazecc_app.shared.generated.resources.ic_launcher
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SplashScreen() {
    val colors = AmazeTheme.colors
    var logoScale by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(300.milliseconds)
        logoScale = 1f
        delay(1200.milliseconds)
        AppState.navigateTo(Screen.LOGIN)
    }

    val logoGradient = remember(colors) {
        Brush.linearGradient(
            colors = listOf(
                colors.accent,
                colors.accent.copy(alpha = 0.6f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .shadow(20.dp, RoundedCornerShape(AmazeTheme.radius.large))
                    .clip(RoundedCornerShape(AmazeTheme.radius.large))
                    .background(logoGradient)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.large)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = "AmazeCC Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))

            Text(
                text = "AmazeCC",
                style = AmazeTheme.typography.display.copy(
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    fontSize = AmazeTheme.fontSize.display
                )
            )

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))

            Text(
                text = "UNIFIED STUDENT OPERATING SYSTEM",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textSecondary,
                    letterSpacing = 1.5.sp
                )
            )

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))

            Text(
                text = "An unofficial community initiative",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted.copy(alpha = 0.6f)
                )
            )
        }
    }
}