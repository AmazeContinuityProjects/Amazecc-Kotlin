package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.ProfileImagesHodDeanPerson
import com.amazecc.app.shared.model.ProfileImagesProctor
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes

private data class ProfileDetailRow(val icon: ImageVector, val label: String, val value: String)

@Composable
fun PersonalInformationPage() {
    val colors = AmazeTheme.colors
    val profile by AppState.studentProfile.collectAsState()

    val rows = buildList {
        val p = profile ?: return@buildList
        add(ProfileDetailRow(Icons.Rounded.Email, "Email", p.email))
        add(ProfileDetailRow(Icons.Rounded.School, "Program", p.program))
        add(ProfileDetailRow(Icons.Rounded.LocationOn, "Campus", p.campus))
        add(ProfileDetailRow(Icons.Rounded.DateRange, "Batch", p.batch))
        p.nationality?.let { add(ProfileDetailRow(Icons.Rounded.Public, "Nationality", it)) }
        p.nativeLanguage?.let { add(ProfileDetailRow(Icons.Rounded.Translate, "Mother Tongue", it)) }
        p.religion?.let { add(ProfileDetailRow(Icons.Rounded.TempleHindu, "Religion", it)) }
        p.community?.let { add(ProfileDetailRow(Icons.Rounded.Groups, "Community", it)) }
        p.caste?.let { add(ProfileDetailRow(Icons.Rounded.AccountBox, "Caste", it)) }
        p.physicallyChallenged?.let { add(ProfileDetailRow(Icons.Rounded.Accessible, "Physically Challenged", it)) }
        p.aadharNumber?.let { add(ProfileDetailRow(Icons.Rounded.Assignment, "Aadhar Number", it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Identity & Contact")
        SettingsGroupCard {
            if (rows.isEmpty()) {
                EmptyStateCard("No personal information loaded yet")
            } else {
                rows.forEachIndexed { index, row ->
                    ProfileDetailRowItem(row)
                    if (index < rows.lastIndex) SettingsRowDivider()
                }
            }
        }
    }
}

@Composable
fun AcademicDetailsPage() {
    val colors = AmazeTheme.colors
    val profile by AppState.studentProfile.collectAsState()

    val rows = buildList {
        val p = profile ?: return@buildList
        p.section?.let { add(ProfileDetailRow(Icons.Rounded.Group, "Section", it)) }
        p.advisorName?.let { add(ProfileDetailRow(Icons.Rounded.Person, "Advisor", it)) }
        p.bloodGroup?.let { add(ProfileDetailRow(Icons.Rounded.Bloodtype, "Blood Group", it)) }
        p.program?.let { add(ProfileDetailRow(Icons.Rounded.School, "Program", it)) }
        p.campus?.let { add(ProfileDetailRow(Icons.Rounded.LocationOn, "Campus", it)) }
        p.batch?.let { add(ProfileDetailRow(Icons.Rounded.DateRange, "Batch", it)) }
        p.regNo?.let { add(ProfileDetailRow(Icons.Rounded.Badge, "Registration Number", it)) }
        p.aadharNumber?.let { add(ProfileDetailRow(Icons.Rounded.Assignment, "Aadhar Number", it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Academic & Campus")
        SettingsGroupCard {
            if (rows.isEmpty()) {
                EmptyStateCard("No academic details loaded yet")
            } else {
                rows.forEachIndexed { index, row ->
                    ProfileDetailRowItem(row)
                    if (index < rows.lastIndex) SettingsRowDivider()
                }
            }
        }
    }
}

@Composable
fun UniversityOfficialsPage() {
    val colors = AmazeTheme.colors
    val profileImages by AppState.profileImages.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val proctor = profileImages?.proctor
        if (proctor != null) {
            SettingsGroupLabel("Proctor")
            SettingsGroupCard {
                OfficialDetailCard(proctor)
            }
        }

        val people = profileImages?.hodDean?.people ?: emptyList()
        if (people.isNotEmpty()) {
            SettingsGroupLabel("HoD & Dean")
            people.forEach { person ->
                SettingsGroupCard {
                    OfficialDetailCard(person)
                }
            }
        }

        if (proctor == null && people.isEmpty()) {
            EmptyStateCard("University officials not loaded yet")
        }
    }
}

@Composable
private fun OfficialDetailCard(proctor: ProfileImagesProctor) {
    val colors = AmazeTheme.colors
    Column(modifier = Modifier.padding(14.dp)) {
        val bitmap = remember(proctor.photoBase64) {
            proctor.photoBase64?.let { src ->
                val base64 = src.substringAfter("base64,")
                val bytes = try { base64.decodeBase64Bytes() } catch (_: Exception) { null }
                bytes?.toImageBitmap()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Proctor",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.Person, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(AmazeTheme.spacing.md))
            Column {
                Text("PROCTOR DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                proctor.details["name"]?.let {
                    Text(it, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
            }
        }
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        proctor.details.forEach { (k, v) ->
            if (k.lowercase() != "name") {
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                OfficialDetailLine(label = k, value = v)
            }
        }
    }
}

@Composable
private fun OfficialDetailCard(person: ProfileImagesHodDeanPerson) {
    val colors = AmazeTheme.colors
    Column(modifier = Modifier.padding(14.dp)) {
        val bitmap = remember(person.photoBase64) {
            person.photoBase64?.let { src ->
                val base64 = src.substringAfter("base64,")
                val bytes = try { base64.decodeBase64Bytes() } catch (_: Exception) { null }
                bytes?.toImageBitmap()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = person.role,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
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
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            OfficialDetailLine(label = k, value = v)
        }
    }
}

@Composable
private fun OfficialDetailLine(label: String, value: String) {
    val colors = AmazeTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val icon = when (label.lowercase()) {
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
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Text(value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = AmazeTheme.fontSize.base))
        }
    }
}

@Composable
private fun ProfileDetailRowItem(row: ProfileDetailRow) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(row.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun EmptyStateCard(message: String) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(message, color = colors.textSecondary)
    }
}
