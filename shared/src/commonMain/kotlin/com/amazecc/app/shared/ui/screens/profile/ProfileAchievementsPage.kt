package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.AccountCredential
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider

@Composable
fun CredentialsAndRanksPage() {
    val colors = AmazeTheme.colors
    val identity by UserStore.identity.collectAsState()
    val ranks = identity.ranks
    val linked = identity.credentials
    var expandedAccount by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ranks.isEmpty() && linked.isEmpty()) {
            EmptyStateCard("No credentials or ranks available")
            return
        }

        if (ranks.isNotEmpty()) {
            SettingsGroupLabel("Ranks")
            SettingsGroupCard {
                ranks.forEachIndexed { index, rank ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            rank.name,
                            modifier = Modifier.weight(1f),
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rank.rank,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.xs
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < ranks.lastIndex) SettingsRowDivider()
                }
            }
        }

        if (linked.isNotEmpty()) {
            SettingsGroupLabel("Linked Credentials")
            SettingsGroupCard {
                linked.forEachIndexed { index, cred ->
                    CredentialRow(
                        cred = cred,
                        expanded = expandedAccount == cred.account,
                        onToggle = {
                            expandedAccount = if (expandedAccount == cred.account) null else cred.account
                        }
                    )
                    if (index < linked.lastIndex) SettingsRowDivider()
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    cred: AccountCredential,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = AmazeTheme.colors
    val uriHandler = LocalUriHandler.current
    val hasVenue = cred.venueDate.isNotBlank() || cred.seatLocation.isNotBlank()
    val hasPassword = cred.password.isNotBlank()
    val hasUrl = cred.url?.isNotBlank() == true

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Link, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cred.account,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    cred.username,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            if (hasPassword) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Visibility, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cred.password,
                        style = AmazeTheme.typography.body.copy(color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasVenue) {
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                Text(
                    buildString {
                        if (cred.venueDate.isNotBlank()) append("Exam: ${cred.venueDate}")
                        if (cred.seatLocation.isNotBlank()) {
                            if (isNotEmpty()) append("  •  ")
                            append("Seat: ${cred.seatLocation}")
                        }
                    },
                    style = AmazeTheme.typography.caption.copy(color = colors.accent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasUrl) {
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                AmazeButton(
                    text = "Open Link",
                    onClick = { uriHandler.openUri(cred.url ?: "") },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
