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
import com.amazecc.app.shared.ui.strings.Strings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.*
import io.ktor.util.decodeBase64Bytes
import com.amazecc.app.shared.utils.toImageBitmap
import com.amazecc.app.shared.ui.components.bouncySpring

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = Strings.profile,
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
    val bankInfo by AppState.bankInfo.collectAsState()
    val dayboarder by AppState.dayboarder.collectAsState()
    val eptSchedule by AppState.eptSchedule.collectAsState()
    val registrationSchedule by AppState.registrationSchedule.collectAsState()
    val apaarId by AppState.apaarId.collectAsState()
    val profileImages by AppState.profileImages.collectAsState()
    val vtopPhotoBase64 by AppState.vtopPhotoBase64.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 18.dp).padding(bottom = BOTTOM_NAV_PADDING),
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
                        ?: profileImages?.student?.photoBase64 
                        ?: profileImages?.profile?.photoBase64 
                        ?: profileImages?.studentPhoto
                        ?: vtopPhotoBase64
                    val decodedBitmap = remember(photoBase64) {
                        if (photoBase64 != null) {
                            try {
                                val cleanBase64 = photoBase64.substringAfter("base64,")
                                    .replace("\n", "")
                                    .replace("\r", "")
                                    .replace(" ", "")
                                cleanBase64.decodeBase64Bytes().toImageBitmap()
                            } catch (e: Exception) { null }
                        } else null
                    }
                    
                    if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = decodedBitmap,
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
                Spacer(Modifier.width(AmazeTheme.spacing.md))
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
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AmazeBadge(text = "ACTIVE ENROLLED", variant = BadgeVariant.SUCCESS)
                        if (profile?.batch?.isNotBlank() == true) {
                            AmazeBadge(text = profile?.batch ?: "", variant = BadgeVariant.INFO)
                        }
                    }
                }
            }
        }

        // Profile Status Grid
        val hasEpt = eptSchedule?.tables?.isNotEmpty() == true
        val hasReg = registrationSchedule?.tables?.isNotEmpty() == true
        val hasBank = bankInfo?.bankDetails != null || bankInfo?.fields?.isNotEmpty() == true
        val hasDay = dayboarder?.fields?.isNotEmpty() == true
        val hasApaar = apaarId?.hasApaar == true
        val viteeeRank = profileImages?.credentials?.ranks?.firstOrNull()?.rank
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Event, null, tint = if (hasEpt) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("EPT Schedule", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(if (hasEpt) "Scheduled" else "No EPT", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AccountBalance, null, tint = if (hasBank) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Bank Info", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(if (hasBank) "Available" else "Not available", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = if (viteeeRank != null) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("VITEEE Rank", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(viteeeRank ?: "N/A", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.HowToReg, null, tint = if (hasReg) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Registration", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(if (hasReg) "Available" else "No Schedule", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Commute, null, tint = if (hasDay) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("Dayboarder", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(if (hasDay) "Active" else "Not active", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
                AmazeCard(variant = CardVariant.DEFAULT) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Badge, null, tint = if (hasApaar) colors.accent else colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Text("APAAR ID", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(if (hasApaar) "Generated" else "Pending", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        
        // Profile info cards
        val p = profile
        if (p != null) {
            ProfileGroupCard("PERSONAL INFORMATION", listOfNotNull(
                ProfileItem(Icons.Rounded.Email, "Email", p.email),
                ProfileItem(Icons.Rounded.Phone, "Mobile", p.mobile),
                ProfileItem(Icons.Rounded.School, "Program", p.program),
                ProfileItem(Icons.Rounded.LocationOn, "Campus", p.campus),
                ProfileItem(Icons.Rounded.DateRange, "Batch", p.batch),
                p.nationality?.let { ProfileItem(Icons.Rounded.Public, "Nationality", it) },
                p.nativeLanguage?.let { ProfileItem(Icons.Rounded.Translate, "Mother Tongue", it) },
                p.religion?.let { ProfileItem(Icons.Rounded.TempleHindu, "Religion", it) },
                p.community?.let { ProfileItem(Icons.Rounded.Groups, "Community", it) },
                p.caste?.let { ProfileItem(Icons.Rounded.AccountBox, "Caste", it) },
                p.physicallyChallenged?.let { ProfileItem(Icons.Rounded.Accessible, "Physically Challenged", it) },
                p.aadharNumber?.let { ProfileItem(Icons.Rounded.Assignment, "Aadhar Number", it) }
            ), colors)

            val extraItems = mutableListOf<ProfileItem>()
            p.section?.let { extraItems.add(ProfileItem(Icons.Rounded.Group, "Section", it)) }
            p.advisorName?.let { extraItems.add(ProfileItem(Icons.Rounded.Person, "Advisor", it)) }
            p.bloodGroup?.let { extraItems.add(ProfileItem(Icons.Rounded.Bloodtype, "Blood Group", it)) }

            if (extraItems.isNotEmpty()) {
                ProfileGroupCard("ACADEMIC & CAMPUS DETAILS", extraItems, colors)
            }
            
            // Faculty details from profileImages
            profileImages?.proctor?.let { proc ->
                val proctorBitmap = remember(proc.photoBase64) {
                    proc.photoBase64?.let { src ->
                        val base64 = src.substringAfter("base64,")
                        val bytes = try { base64.decodeBase64Bytes() } catch (_: Exception) { null }
                        bytes?.toImageBitmap()
                    }
                }
                AmazeCard(modifier = Modifier.fillMaxWidth(), accentStrip = true) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (proctorBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = proctorBitmap,
                                        contentDescription = "Proctor",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Rounded.Person, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.width(AmazeTheme.spacing.md))
                            Column {
                                Text("PROCTOR DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                                proc.details["name"]?.let { Text(it, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)) }
                            }
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        proc.details.forEach { (k, v) ->
                            if (k.lowercase() != "name") {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (k.lowercase()) {
                                        "designation" -> Icons.Rounded.Badge
                                        "school" -> Icons.Rounded.AccountBalance
                                        "mobile" -> Icons.Rounded.Phone
                                        "intercom" -> Icons.Rounded.Call
                                        "email" -> Icons.Rounded.Email
                                        else -> Icons.Rounded.Info
                                    }
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(k, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp))
                                        Text(v, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.md))
            }

            profileImages?.hodDean?.let { dean ->
                dean.people.forEach { person ->
                    val personBitmap = remember(person.photoBase64) {
                        person.photoBase64?.let { src ->
                            val base64 = src.substringAfter("base64,")
                            val bytes = try { base64.decodeBase64Bytes() } catch (_: Exception) { null }
                            bytes?.toImageBitmap()
                        }
                    }
                    AmazeCard(modifier = Modifier.fillMaxWidth(), accentStrip = true) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (personBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = personBitmap,
                                            contentDescription = person.role,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Person, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.width(AmazeTheme.spacing.md))
                                Text(person.role, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                            }
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            person.details.forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (k.lowercase()) {
                                        "designation" -> Icons.Rounded.Badge
                                        "mobile" -> Icons.Rounded.Phone
                                        "intercom" -> Icons.Rounded.Call
                                        "email" -> Icons.Rounded.Email
                                        else -> Icons.Rounded.Info
                                    }
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(k, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp))
                                        Text(v, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                }
            }
        } else {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("Profile not loaded", color = colors.textSecondary)
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
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
                Spacer(Modifier.width(AmazeTheme.spacing.md))
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
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
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
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}



