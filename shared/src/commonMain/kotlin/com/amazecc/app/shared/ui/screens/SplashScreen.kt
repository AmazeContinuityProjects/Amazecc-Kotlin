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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashScreen() {
    val colors = AmazeTheme.colors
    var logoScale by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(300)
        logoScale = 1f
        delay(1200)
        AppState.navigateTo(Screen.LOGIN)
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
                    .shadow(20.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.7f),
                                colors.accent.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(2.dp, colors.accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = "AmazeCC Logo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AmazeCC",
                style = AmazeTheme.typography.display.copy(
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    fontSize = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Unified Student Operating System",
                style = AmazeTheme.typography.caption.copy(
                    color = colors.textSecondary,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "An unofficial community initiative",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted.copy(alpha = 0.6f)
                )
            )
        }
    }
}