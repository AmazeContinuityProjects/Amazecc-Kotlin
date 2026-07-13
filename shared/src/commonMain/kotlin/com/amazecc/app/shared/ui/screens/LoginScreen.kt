package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeTextField
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Brand Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.surface, shape = MaterialTheme.shapes.medium)
                .border(1.dp, colors.border, shape = MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ω",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = colors.accent
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "AmazeCC",
            style = AmazeTheme.typography.display.copy(
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        )
        
        Text(
            text = "Unified Student Operating System",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Error message banner
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.dangerSurface, shape = MaterialTheme.shapes.small)
                    .border(1.dp, colors.danger, shape = MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.dangerText,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Credentials
        AmazeTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = "VTOP Registration Number",
            placeholder = "e.g., 25BYB1043",
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textSecondary) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AmazeTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = "Password",
            placeholder = "••••••••",
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Captcha is solved automatically",
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AmazeButton(
            text = if (isSubmitting) "Logging in..." else "Secure Login",
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
                            SessionManager.saveSession(
                                cookies = response.cookies,
                                csrf = response.csrf,
                                authorizedID = response.authorizedID,
                                clubToken = response.clubToken
                            )
                            // Set Demo/Real client mode based on credentials
                            if (username.lowercase() == "demo" || username.uppercase() == "DEMO123") {
                                AmazeClient.setUseMockData(true)
                            } else {
                                AmazeClient.setUseMockData(false)
                            }
                            // Load student data and transition to dashboard
                            AppState.loadAllData()
                            AppState.navigateTo(Screen.HOME)
                        } else {
                            errorMessage = response.message ?: "Authentication failed."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Connection error: ${e.message}"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(
            onClick = {
                // Instantly load demo session
                scope.launch {
                    isSubmitting = true
                    val demoRes = AmazeClient.login("DEMO123", "password")
                    SessionManager.saveSession(
                        cookies = demoRes.cookies!!,
                        csrf = demoRes.csrf!!,
                        authorizedID = demoRes.authorizedID!!,
                        clubToken = null
                    )
                    AmazeClient.setUseMockData(true)
                    AppState.loadAllData()
                    AppState.navigateTo(Screen.HOME)
                    isSubmitting = false
                }
            }
        ) {
            Text(
                text = "Explore in Demo Mode",
                style = AmazeTheme.typography.body.copy(
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
