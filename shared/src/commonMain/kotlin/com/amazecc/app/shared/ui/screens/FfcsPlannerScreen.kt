package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ButtonVariant

@Composable
fun FfcsPlannerScreen() {
    val colors = AmazeTheme.colors
    
    var showGrid by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "FFCS Planner",
            description = "Build conflict-free schedules effortlessly",
            showBackButton = false,
            showSyncButton = false
        )
        
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            if (!showGrid) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add target courses to start generating timetables.",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    AmazeButton(
                        text = "Auto Generate",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = { showGrid = true },
                        variant = ButtonVariant.PRIMARY
                    )
                }
            } else {
                var scale by remember { mutableStateOf(1f) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 3f)
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                ) {
                    Text("Timetable Generator Results", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Mock Timetable Grid representation
                    AmazeCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Generated Grid UI goes here...", color = colors.textSecondary)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AmazeButton(
                        text = "Reset Planner",
                        onClick = { showGrid = false },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
