package com.amazecc.app.shared.ui.screens

import amazecc_app.shared.generated.resources.Res
import amazecc_app.shared.generated.resources.ic_launcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashScreen() {
    val colors = AmazeTheme.colors

    LaunchedEffect(Unit) {
        delay(1500L)
        AppState.navigateTo(Screen.LOGIN)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = painterResource(Res.drawable.ic_launcher),
                contentDescription = "AmazeCC Logo",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "AmazeCC (Kotlin)",
                style = AmazeTheme.typography.display.copy(
                    fontWeight = FontWeight.Black,
                    color = colors.accent
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "An unofficial community initiative",
                style = AmazeTheme.typography.subheading.copy(
                    color = colors.textSecondary
                )
            )
        }
    }
}