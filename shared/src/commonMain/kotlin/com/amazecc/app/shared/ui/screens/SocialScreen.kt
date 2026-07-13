package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun SocialScreen() {
    val colors = AmazeTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Social & Friends",
            description = "Find friends and match timetables",
            showBackButton = false,
            showSyncButton = true
        )
        
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Timetable Matcher", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Find common free slots with your friends instantly.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(16.dp))
                    AmazeButton("Match Timetables", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Rounded.People, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Friend Search", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Search for other AmazeCC users to connect.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(16.dp))
                    AmazeButton("Search Friends", onClick = {}, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}