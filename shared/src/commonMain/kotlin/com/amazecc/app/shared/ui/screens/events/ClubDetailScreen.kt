package com.amazecc.app.shared.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.ClubItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.utils.toImageBitmap

@Composable
fun ClubDetailScreen() {
    val colors = AmazeTheme.colors
    val clubsRes by AppState.clubs.collectAsState()
    val clubId by AppState.selectedClubId.collectAsState()
    
    val club = clubsRes?.clubs?.find { it.id == clubId }

    if (club == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Club Details", description = "Club not found", showBackButton = true, showSyncButton = false, enabledScreens = setOf(Screen.CLUB_DETAIL))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Club not found", color = colors.textMuted)
            }
        }
        return
    }

    var isEnrolled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            item { HeaderSpacer() }
            item {
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.large))
                            .background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!club.logoUrl.isNullOrEmpty()) {
                            var logoBitmap by remember(club.logoUrl) { mutableStateOf<ImageBitmap?>(null) }
                            LaunchedEffect(club.logoUrl) {
                                val bytes = AmazeClient.getImageBytes(club.logoUrl)
                                logoBitmap = bytes?.toImageBitmap()
                            }
                            val bm = logoBitmap
                            if (bm != null) {
                                Image(
                                    bitmap = bm,
                                    contentDescription = "Club Logo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(club.name?.firstOrNull()?.uppercase() ?: "C", style = AmazeTheme.typography.heading.copy(color = colors.accent))
                            }
                        } else {
                            Text(club.name?.firstOrNull()?.uppercase() ?: "C", style = AmazeTheme.typography.heading.copy(color = colors.accent))
                        }
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sectionGap))
                    Column {
                        Text(club.name ?: "Unnamed Club", style = AmazeTheme.typography.heading.copy(color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                        Text("VIT Chennai Chapter", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!club.website.isNullOrBlank()) {
                                Icon(Icons.Rounded.Language, contentDescription = "Website", tint = colors.accent, modifier = Modifier.size(24.dp).clickable { uriHandler.openUri(club.website) })
                            }
                            if (!club.instagram.isNullOrBlank()) {
                                val instaUrl = if (club.instagram.startsWith("http")) club.instagram else "https://instagram.com/${club.instagram.removePrefix("@")}"
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Instagram", tint = colors.accent, modifier = Modifier.size(24.dp).clickable { uriHandler.openUri(instaUrl) })
                            }
                            if (!club.whatsapp.isNullOrBlank()) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "WhatsApp", tint = colors.accent, modifier = Modifier.size(24.dp).clickable { uriHandler.openUri(club.whatsapp) })
                            }
                        }
                    }
                }
            }
            
            item {
                HorizontalDivider(color = colors.border, thickness = 1.dp)
            }
            
            item {
                Text("About Us", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Text(
                    text = club.description ?: "No description provided.",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 24.sp)
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.surface.copy(alpha = 0.65f))
                        .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.medium))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WorkOutline, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text("Hiring Information", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Currently recruiting for technical and management roles. Check out our feed for application links!", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }

            item {
                Text("Club Feed", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.surface.copy(alpha = 0.65f))
                        .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.medium)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Feed Integration Coming Soon", color = colors.textMuted)
                }
            }

            item {
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))
                Button(
                    onClick = { isEnrolled = !isEnrolled },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(AmazeTheme.radius.medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnrolled) colors.border else colors.accent,
                        contentColor = if (isEnrolled) colors.textSecondary else Color.White
                    )
                ) {
                    Icon(if (isEnrolled) Icons.Rounded.CheckCircle else Icons.Rounded.Add, null)
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Text(if (isEnrolled) "Enrolled" else "Enroll in Club", style = AmazeTheme.typography.subheading)
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xl))
            }
        }
        
        ScreenHeader(title = club.name ?: "Club Details", description = "Club Information", showBackButton = true, showSyncButton = false, enabledScreens = setOf(Screen.CLUB_DETAIL))
    }
}
