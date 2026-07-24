package com.amazecc.app.shared.ui.screens

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.*
import io.ktor.util.decodeBase64Bytes
import com.amazecc.app.shared.ui.components.bouncySpring

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Profile",
            description = "Your personal information",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshProfile
        )

        ProfileContent(colors)
    }
}

@Composable
private fun ProfileContent(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val radius = AmazeTheme.radius
    val spacing = AmazeTheme.spacing
    val profile by AppState.studentProfile.collectAsState()
    val authorizedID by SessionManager.authorizedID.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 18.dp).padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        com.amazecc.app.shared.ui.components.HeaderSpacer()
        // Avatar & name card
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            variant = CardVariant.DEFAULT
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .border(2.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val photoBase64 = profile?.photoBase64
                    if (photoBase64 != null) {
                        val cleanBase64 = photoBase64.substringAfter("base64,")
                        io.kamel.image.KamelImage(
                            resource = io.kamel.image.asyncPainterResource(data = cleanBase64.decodeBase64Bytes()),
                            contentDescription = "Profile Image",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (authorizedID ?: "?").take(2).uppercase(),
                            style = AmazeTheme.typography.display.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile?.name ?: authorizedID ?: "Student",
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile?.regNo ?: authorizedID ?: "",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AmazeBadge(text = "ACTIVE ENROLLED", variant = BadgeVariant.SUCCESS)
                        if (profile?.batch?.isNotBlank() == true) {
                            AmazeBadge(text = profile?.batch ?: "", variant = BadgeVariant.INFO)
                        }
                    }
                }
            }
        }

        // Profile info cards
        val p = profile
        if (p != null) {
            ProfileGroupCard("PERSONAL INFORMATION", listOf(
                ProfileItem(Icons.Rounded.Email, "Email", p.email),
                ProfileItem(Icons.Rounded.Phone, "Mobile", p.mobile),
                ProfileItem(Icons.Rounded.School, "Program", p.program),
                ProfileItem(Icons.Rounded.LocationOn, "Campus", p.campus),
                ProfileItem(Icons.Rounded.DateRange, "Batch", p.batch),
            ), colors)

            val extraItems = mutableListOf<ProfileItem>()
            p.section?.let { extraItems.add(ProfileItem(Icons.Rounded.Group, "Section", it)) }
            p.advisorName?.let { extraItems.add(ProfileItem(Icons.Rounded.Person, "Advisor", it)) }
            p.bloodGroup?.let { extraItems.add(ProfileItem(Icons.Rounded.Bloodtype, "Blood Group", it)) }

            if (extraItems.isNotEmpty()) {
                ProfileGroupCard("ACADEMIC & CAMPUS DETAILS", extraItems, colors)
            }
        } else {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Profile not loaded", color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    AmazeButton("Load Profile", onClick = { AppState.loadAllData() }, variant = ButtonVariant.SECONDARY)
                }
            }
        }

        // Settings button
        AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { AppState.navigateTo(Screen.SETTINGS) }) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Settings", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Theme, sync, credentials & preferences", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(spacing.md))
    }
}

private data class ProfileItem(val icon: ImageVector, val label: String, val value: String)

@Composable
private fun ProfileGroupCard(title: String, items: List<ProfileItem>, colors: com.amazecc.app.shared.theme.AmazeColors) {
    AmazeCard(modifier = Modifier.fillMaxWidth(), accentStrip = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = title,
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.10f)).border(1.dp, colors.accent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}



