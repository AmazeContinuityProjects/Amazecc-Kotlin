package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EPageSkeleton
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One real campus contact row from whoami `contact`. */
@Immutable
data class M3EContactCard(
    val id: String,
    val office: String,
    val description: String,
    val email: String? = null
)

object M3EContactsTestTags {
    const val CONTACT_CARD = "m3e_contacts_card"
}

/** Immutable, screen-owned state for [M3EContactsScreen]. */
@Immutable
data class M3EContactsState(
    val contacts: List<M3EContactCard> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** User intents emitted by [M3EContactsScreen]. */
sealed interface M3EContactsAction {
    data object Refresh : M3EContactsAction
    data class OpenEmail(val email: String) : M3EContactsAction
}

/**
 * Dedicated Contacts page (D-110). The profile only links here; the actual office rows,
 * descriptions and addresses all come from the real whoami snapshot via the host.
 * 100% stateless: receives [M3EContactsState] and emits [M3EContactsAction].
 */
@Composable
fun M3EContactsScreen(
    state: M3EContactsState,
    onAction: (M3EContactsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Contacts",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Campus offices and who to reach",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3EContactsAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh contacts",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EPageSkeleton(label = "Loading contacts", tiles = 1, rows = 3)
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item(key = "error") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error
                        )
                    }
                }
            }
        }

        items(state.contacts, key = { it.id }) { contact ->
            ContactCard(
                contact = contact,
                onEmail = { email -> onAction(M3EContactsAction.OpenEmail(email)) }
            )
        }

        if (!state.isLoading && state.contacts.isEmpty() && state.errorMessage.isNullOrBlank()) {
            item(key = "empty") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No campus contacts available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }
}

/**
 * A single contact record. The description title sits above its body — the two are
 * stacked, not side by side — so long office descriptions wrap without a cramped row.
 */
@Composable
private fun ContactCard(
    contact: M3EContactCard,
    onEmail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(M3EContactsTestTags.CONTACT_CARD),
        containerLevel = SurfaceContainerLevel.LOW
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Business,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = orDash(contact.office),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // Term description: title on its own line, body directly below it.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = orDash(contact.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }

            contact.email?.takeIf { it.isNotBlank() }?.let { email ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEmail(email) }) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = "Email $email",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Missing contact data renders as an em dash, never a plausible-looking placeholder. */
private fun orDash(value: String): String = value.ifBlank { "—" }

// ==========================================
// PREVIEWS
// ==========================================

private fun previewContactsState() = M3EContactsState(
    contacts = listOf(
        M3EContactCard(
            id = "accommodation",
            office = "Accommodation Office",
            description = "Hostel allotment, room changes and residence queries.",
            email = "hostel@vit.ac.in"
        ),
        M3EContactCard(
            id = "examinations",
            office = "Examination Office",
            description = "Seating arrangements, hall tickets and revaluation windows."
        )
    )
)

@Preview
@Composable
private fun M3EContactsScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EContactsScreen(state = previewContactsState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EContactsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EContactsScreen(state = previewContactsState(), onAction = {})
    }
}
