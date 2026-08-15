package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel
import com.amazecc.app.shared.ui.screens.settings.SettingsRow
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes

@Composable
fun ProfileHub(
    onOpenSubScreen: (ProfileSubScreen) -> Unit
) {
    val colors = AmazeTheme.colors
    val identity by UserStore.identity.collectAsState()

    val hasEpt = identity.eptTables.isNotEmpty()
    val hasReg = identity.registrationTables.isNotEmpty() || identity.registrationFields.isNotEmpty()
    val hasDay = identity.dayboarder?.isDayboarder == true
    val hasApaar = identity.apaar?.hasApaar == true
    val viteeeRank = identity.ranks.firstOrNull()?.rank
    val hasCredentials = identity.credentials.isNotEmpty() || identity.ranks.isNotEmpty()

    fun valueFor(sub: ProfileSubScreen): String? = when (sub) {
        ProfileSubScreen.PERSONAL_INFO -> null
        ProfileSubScreen.ACADEMIC_DETAILS -> identity.section
        ProfileSubScreen.UNIVERSITY_OFFICIALS -> proctorName(identity.proctor?.name) ?: officialsSummary(identity.proctor != null, identity.hodDean.size)
        ProfileSubScreen.EPT_SCHEDULE -> if (hasEpt) "Scheduled" else "Not scheduled"
        ProfileSubScreen.REGISTRATION -> if (hasReg) "Available" else "No schedule"
        ProfileSubScreen.UNIVERSITY_DAY -> if (identity.universityDayTables.isNotEmpty() || identity.universityDayFields.isNotEmpty()) "Available" else "No details"
        ProfileSubScreen.DAYBOARDER -> if (hasDay) "Active" else "Not active"
        ProfileSubScreen.APAAR_ID -> if (hasApaar) "Generated" else "Pending"
        ProfileSubScreen.CREDENTIALS -> if (viteeeRank.isNullOrBlank()) (if (hasCredentials) "Linked" else null) else viteeeRank
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileHeroCard()

        if (!identity.hasIdentity) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("Profile not loaded", color = colors.textSecondary)
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    AmazeButton("Load Profile", onClick = { AppState.loadAllData() }, variant = ButtonVariant.SECONDARY)
                }
            }
        }

        ProfileGroup.entries.forEach { group ->
            val subs = ProfileSubScreen.entries.filter { it.group == group }
            if (group == ProfileGroup.GENERAL) {
                SettingsGroupLabel(group.label)
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Rounded.Settings,
                        title = "App Settings",
                        subtitle = "Theme, sync, credentials & preferences",
                        value = null,
                        tint = colors.accent,
                        onClick = { AppState.navigateTo(Screen.SETTINGS) }
                    )
                }
            } else if (subs.isNotEmpty()) {
                SettingsGroupLabel(group.label)
                SettingsGroupCard {
                    subs.forEachIndexed { index, sub ->
                        SettingsRow(
                            icon = sub.icon,
                            title = sub.title,
                            subtitle = sub.description,
                            value = valueFor(sub),
                            tint = colors.accent,
                            onClick = { onOpenSubScreen(sub) }
                        )
                        if (index < subs.lastIndex) SettingsRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroCard() {
    val colors = AmazeTheme.colors
    val identity by UserStore.identity.collectAsState()

    AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .border(2.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val photoBase64 = identity.photoBase64
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
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "Profile Image",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = identity.initials,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Black,
                            fontSize = AmazeTheme.fontSize.x2l
                        )
                    )
                }
            }
            Spacer(Modifier.width(AmazeTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = identity.displayName,
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = identity.displayRegNo,
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmazeBadge(text = "ACTIVE ENROLLED", variant = BadgeVariant.SUCCESS)
                    if (identity.batch?.isNotBlank() == true) {
                        AmazeBadge(text = identity.batch ?: "", variant = BadgeVariant.INFO)
                    }
                }
            }
        }
    }
}

private fun proctorName(name: String?): String? = name?.takeIf { it.isNotBlank() }

private fun officialsSummary(hasProctor: Boolean, hodDeanCount: Int): String? = when {
    hasProctor && hodDeanCount > 0 -> "Proctor · $hodDeanCount"
    hasProctor -> "Proctor"
    hodDeanCount > 0 -> "$hodDeanCount HoD/Dean"
    else -> null
}