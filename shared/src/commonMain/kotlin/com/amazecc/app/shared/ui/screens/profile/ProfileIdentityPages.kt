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
import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.Official
import com.amazecc.app.shared.state.UserStore
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
    val identity by UserStore.identity.collectAsState()

    val rows = buildList {
        identity.email?.let { add(ProfileDetailRow(Icons.Rounded.Email, "Email", it)) }
        identity.mobile?.let { add(ProfileDetailRow(Icons.Rounded.Phone, "Mobile", it)) }
        identity.dob?.let { add(ProfileDetailRow(Icons.Rounded.Cake, "Date of Birth", it)) }
        identity.gender?.let { add(ProfileDetailRow(Icons.Rounded.Person, "Gender", it)) }
        identity.program?.let { add(ProfileDetailRow(Icons.Rounded.School, "Program", it)) }
        identity.campus?.let { add(ProfileDetailRow(Icons.Rounded.LocationOn, "Campus", it)) }
        identity.batch?.let { add(ProfileDetailRow(Icons.Rounded.DateRange, "Batch", it)) }
        identity.nationality?.let { add(ProfileDetailRow(Icons.Rounded.Public, "Nationality", it)) }
        identity.nativeLanguage?.let { add(ProfileDetailRow(Icons.Rounded.Translate, "Mother Tongue", it)) }
        identity.nativeState?.let { add(ProfileDetailRow(Icons.Rounded.Map, "Native State", it)) }
        identity.religion?.let { add(ProfileDetailRow(Icons.Rounded.TempleHindu, "Religion", it)) }
        identity.community?.let { add(ProfileDetailRow(Icons.Rounded.Groups, "Community", it)) }
        identity.caste?.let { add(ProfileDetailRow(Icons.Rounded.AccountBox, "Caste", it)) }
        identity.physicallyChallenged?.let { add(ProfileDetailRow(Icons.Rounded.Accessible, "Physically Challenged", it)) }
        identity.aadharNumber?.let { add(ProfileDetailRow(Icons.Rounded.Assignment, "Aadhar Number", it)) }
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

        val familyRows = buildList {
            identity.currentAddress.forEach { add(KeyValueRow("Current Address · ${it.label}", it.value)) }
            identity.permanentAddress.forEach { add(KeyValueRow("Permanent Address · ${it.label}", it.value)) }
            identity.father.forEach { add(KeyValueRow("Father · ${it.label}", it.value)) }
            identity.mother.forEach { add(KeyValueRow("Mother · ${it.label}", it.value)) }
            identity.guardian?.let { add(KeyValueRow("Guardian", it)) }
        }
        if (familyRows.isNotEmpty()) {
            SettingsGroupLabel("Address & Family")
            SettingsGroupCard {
                familyRows.forEachIndexed { index, row ->
                    ProfileDetailRowItem(ProfileDetailRow(Icons.Rounded.Home, row.label, row.value))
                    if (index < familyRows.lastIndex) SettingsRowDivider()
                }
            }
        }
    }
}

@Composable
fun AcademicDetailsPage() {
    val colors = AmazeTheme.colors
    val identity by UserStore.identity.collectAsState()

    val rows = buildList {
        identity.section?.let { add(ProfileDetailRow(Icons.Rounded.Group, "Section", it)) }
        identity.advisorName?.let { add(ProfileDetailRow(Icons.Rounded.Person, "Advisor", it)) }
        identity.bloodGroup?.let { add(ProfileDetailRow(Icons.Rounded.Bloodtype, "Blood Group", it)) }
        identity.program?.let { add(ProfileDetailRow(Icons.Rounded.School, "Program", it)) }
        identity.campus?.let { add(ProfileDetailRow(Icons.Rounded.LocationOn, "Campus", it)) }
        identity.batch?.let { add(ProfileDetailRow(Icons.Rounded.DateRange, "Batch", it)) }
        identity.regNo?.let { add(ProfileDetailRow(Icons.Rounded.Badge, "Registration Number", it)) }
        if (identity.isHosteller) add(ProfileDetailRow(Icons.Rounded.Apartment, "Residence", "Hosteller"))
        identity.aadharNumber?.let { add(ProfileDetailRow(Icons.Rounded.Assignment, "Aadhar Number", it)) }
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
    val identity by UserStore.identity.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val proctor = identity.proctor
        if (proctor != null) {
            SettingsGroupLabel("Proctor")
            SettingsGroupCard {
                OfficialDetailCard(proctor)
            }
        }

        val people = identity.hodDean
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
private fun OfficialDetailCard(official: Official) {
    val colors = AmazeTheme.colors
    Column(modifier = Modifier.padding(14.dp)) {
        val bitmap = remember(official.photoBase64) {
            official.photoBase64?.let { src ->
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
                        contentDescription = official.role,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.Person, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(AmazeTheme.spacing.md))
            Column {
                Text(
                    (official.role ?: "OFFICIAL").uppercase(),
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
                official.name?.let {
                    Text(it, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
            }
        }
        val details = buildList {
            official.designation?.let { add(KeyValueRow("designation", it)) }
            official.email?.let { add(KeyValueRow("email", it)) }
            official.phone?.let { add(KeyValueRow("mobile", it)) }
            official.school?.let { add(KeyValueRow("school", it)) }
            official.cabin?.let { add(KeyValueRow("cabin", it)) }
            official.department?.let { add(KeyValueRow("department", it)) }
            official.intercom?.let { add(KeyValueRow("intercom", it)) }
            official.facultyId?.let { add(KeyValueRow("Faculty ID", it)) }
            official.extras.forEach { add(it) }
        }
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            details.forEachIndexed { index, row ->
                OfficialDetailLine(label = row.label, value = row.value)
                if (index < details.lastIndex) Spacer(Modifier.height(AmazeTheme.spacing.xs))
            }
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