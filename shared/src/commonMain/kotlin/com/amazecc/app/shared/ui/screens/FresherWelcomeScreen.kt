package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

data class FresherSection(val title: String, val description: String, val icon: ImageVector, val color: androidx.compose.ui.graphics.Color)

@Composable
fun FresherWelcomeScreen() {
    val colors = AmazeTheme.colors
    val sections = listOf(
        FresherSection("Campus", "VIT has a sprawling campus with state-of-the-art labs, libraries, sports facilities, and hostels. Get your ID card and explore!", Icons.Rounded.School, colors.accent),
        FresherSection("Academics", "Attend orientation, understand the CBCS system, manage course registration, and track attendance and grades via VTOP.", Icons.AutoMirrored.Rounded.MenuBook, colors.success),
        FresherSection("Hostel", "Hostellers get accommodation in blocks. Mess timings, laundry, and counselling are managed through the Hostel module.", Icons.Rounded.Apartment, colors.warning),
        FresherSection("Transport", "College buses run on fixed routes. Day-boarders can register for bus passes through the Transport module.", Icons.Rounded.DirectionsBus, colors.danger),
        FresherSection("Events", "Join clubs, attend hackathons, cultural fests, and tech talks. Stay updated via the Events and Social modules.", Icons.Rounded.Celebration, colors.info)
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = "Fresher's Welcome", description = "Get started with campus life", showBackButton = true)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Star, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Welcome to VIT!", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Here's everything you need to know to get started.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }

            items(sections) { section ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(section.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(section.icon, null, tint = section.color, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(section.title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            Text(section.description, style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 20.sp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(AmazeTheme.spacing.md)) }
        }
    }
}
