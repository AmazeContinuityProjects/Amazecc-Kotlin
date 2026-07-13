package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen() {
    val colors = AmazeTheme.colors

    LaunchedEffect(Unit) {
        delay(1500) // Simulate checking session or initial loading
        AppState.navigateTo(Screen.LOGIN)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AmazeCC",
                style = AmazeTheme.typography.display.copy(
                    fontWeight = FontWeight.Black,
                    color = colors.accent
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "VIT Chennai's Premier App",
                style = AmazeTheme.typography.subheading.copy(
                    color = colors.textSecondary
                )
            )
        }
    }
}