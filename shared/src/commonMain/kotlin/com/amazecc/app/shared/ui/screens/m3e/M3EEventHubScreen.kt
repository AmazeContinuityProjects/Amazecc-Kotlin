package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EEventCategory
import com.amazecc.app.shared.domain.overhaul.M3EEventHubAction
import com.amazecc.app.shared.domain.overhaul.M3EEventHubState
import com.amazecc.app.shared.domain.overhaul.M3EEventItem
import com.amazecc.app.shared.domain.overhaul.M3EEventPreviewUi
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.domain.overhaul.M3ERegisteredEventItem
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import io.kamel.core.ExperimentalKamelApi
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Fest & Campus Event Hub.
 * 100% Stateless: accepts [M3EEventHubState] and emits [M3EEventHubAction].
 * Features Riviera/Gravitas events catalog, venue timelines, price tags, and instant registration.
 */
@Composable
fun M3EEventHubScreen(
    state: M3EEventHubState,
    onAction: (M3EEventHubAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Event Hub",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = colors.onSurface
                        )
                        Text(
                            text = "Riviera, Gravitas & Campus Fests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3EEventHubAction.Refresh) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. Search Field
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EEventHubAction.SearchQueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search events, fests, or venues...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    )
                )
            }

            // 3. Category Selector
            item {
                M3EConnectedButtonGroup(
                    items = M3EEventCategory.entries,
                    selectedItem = state.selectedCategory,
                    onItemSelected = { onAction(M3EEventHubAction.SelectCategory(it)) },
                    labelProvider = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Loading feedback from the real sync flow.
            if (state.isLoading) {
                item(key = "event_hub_loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                        Text(
                            text = "Loading events…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Sync/profile error from AppState — never silently swallowed.
            val errorMessage = state.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                item(key = "event_hub_error") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 18.dp,
                        borderColor = colors.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Couldn't refresh events",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Registered Events — real registrations from AppState.registeredEvents.
            if (state.registeredEvents.isNotEmpty()) {
                item(key = "registered_events_header") {
                    Text(
                        text = "Registered Events",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                }
                items(state.registeredEvents) { registration ->
                    RegisteredEventCard(
                        registration = registration,
                        onOpenLink = { onAction(M3EEventHubAction.OpenExternalLink(it)) }
                    )
                }
            }

            // 4. Counter Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Events (${state.filteredEvents.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Text(
                        text = "${state.registeredCount} Registered",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary
                    )
                }
            }

            // 5. Events List
            if (state.filteredEvents.isEmpty()) {
                item {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No events found in this category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.filteredEvents) { event ->
                    EventCard(
                        event = event,
                        onRegister = { onAction(M3EEventHubAction.RegisterEvent(event.id)) },
                        onClick = { onAction(M3EEventHubAction.EventClicked(event.id)) }
                    )
                }
            }

            // Bottom Spacing for floating navigation bar clearance (140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }

        val preview = state.preview
        if (preview != null) {
            EventPreviewDialog(
                preview = preview,
                onDismiss = { onAction(M3EEventHubAction.DismissPreview) },
                onOpenPoster = { onAction(M3EEventHubAction.OpenExternalLink(it)) }
            )
        }

        val registration = state.registrationResult
        if (registration != null) {
            EventRegistrationDialog(
                result = registration,
                onDismiss = { onAction(M3EEventHubAction.DismissRegistrationStatus) },
                onOpenPayment = { onAction(M3EEventHubAction.OpenExternalLink(it)) }
            )
        }
    }
}

@Composable
private fun RegisteredEventCard(
    registration: M3ERegisteredEventItem,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 18.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = registration.title.ifBlank { "Registered event" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            val meta = listOfNotNull(
                registration.date?.takeIf { it.isNotBlank() },
                registration.time?.takeIf { it.isNotBlank() },
                registration.venue.takeIf { it.isNotBlank() }
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            val paymentStatus = registration.paymentStatus
            if (!paymentStatus.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Payment: $paymentStatus",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
            }

            val links = mutableListOf<Pair<String, String>>()
            registration.receiptUrl?.takeIf { it.isNotBlank() }?.let { links.add("Receipt" to it) }
            registration.certificateUrl?.takeIf { it.isNotBlank() }?.let { links.add("Certificate" to it) }
            registration.payNowUrl?.takeIf { it.isNotBlank() }?.let { links.add("Pay now" to it) }
            if (links.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    links.forEach { (label, url) ->
                        ElevatedButton(
                            onClick = { onOpenLink(url) },
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                                contentColor = colors.onSurface
                            )
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventPreviewDialog(
    preview: M3EEventPreviewUi,
    onDismiss: () -> Unit,
    onOpenPoster: (String) -> Unit
) {
    val colors = M3ETheme.colors
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        title = preview.title.ifBlank { "Event details" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                val posterUrl = preview.imageUrl
                if (!posterUrl.isNullOrBlank()) {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.HIGH,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EventPosterImage(
                            url = posterUrl,
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )
                        TextButton(onClick = { onOpenPoster(posterUrl) }) {
                            Text("Open poster full size", color = colors.primary)
                        }
                    }
                }
                val description = preview.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                }
                preview.metaDetails.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            if (description.isNullOrBlank() && preview.metaDetails.isEmpty() && posterUrl.isNullOrBlank()) {
                Text(
                    text = "No further details are available for this event.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Close", color = colors.primary) }
            }
        }
    }
}

/** Inline event poster using the project's existing Kamel dependency (no new deps). */
@OptIn(ExperimentalKamelApi::class)
@Composable
private fun EventPosterImage(url: String, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    KamelImage(
        resource = asyncPainterResource(data = url),
        contentDescription = "Event poster",
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh),
        contentScale = ContentScale.Crop,
        onLoading = { CircularProgressIndicator() },
        onFailure = {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = "Poster unavailable",
                tint = colors.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun EventRegistrationDialog(
    result: M3EEventRegistrationUi,
    onDismiss: () -> Unit,
    onOpenPayment: (String) -> Unit
) {
    val colors = M3ETheme.colors
    val status = result.status.trim().lowercase()
    val isPayment = status == "payment_required" || status == "payment_form"
    val isRedirect = status == "redirect"
    val isError = status == "session_expired" || status == "invalid_credentials" || status == "error"
    val title = when {
        status == "success" -> "Registration confirmed"
        status == "already_registered" -> "Already registered"
        isPayment -> "Payment required"
        isRedirect -> "Continue on the event hub"
        isError -> "Registration could not be completed"
        else -> "Registration update"
    }
    val body = result.message?.takeIf { it.isNotBlank() }
        ?: when {
            status == "success" -> "Your seat is confirmed. Your receipt and certificate appear in Registered Events once available."
            status == "already_registered" -> "You are already registered for this event."
            isPayment -> "Complete the payment on the event's site to confirm your seat."
            isRedirect -> "Finish the registration on the event hub's site."
            isError -> "Please try again after refreshing your event hub session."
            else -> "The event hub returned an update for your registration."
        }
    val paymentUrl = result.paymentUrl?.takeIf { it.isNotBlank() }
    val actionLabel = when {
        isPayment -> "Pay now"
        isRedirect -> "Continue"
        else -> "OK"
    }
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        title = title
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if ((isPayment || isRedirect) && paymentUrl != null) {
                    TextButton(onClick = onDismiss) { Text("Later", color = colors.onSurfaceVariant) }
                    TextButton(onClick = { onOpenPayment(paymentUrl) }) { Text(actionLabel, color = colors.primary) }
                } else {
                    TextButton(onClick = onDismiss) { Text(actionLabel, color = colors.primary) }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: M3EEventItem,
    onRegister: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 22.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Fest & Category Tags Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (event.festName.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = event.festName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = colors.onPrimaryContainer
                            )
                        }
                    }

                    if (event.category != M3EEventCategory.ALL) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = event.category.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.sp
                                ),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (event.isFree) colors.tertiaryContainer else colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = event.price,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (event.isFree) colors.onTertiaryContainer else colors.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Event Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${event.date} • ${event.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Venue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = event.venue,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: Register or Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (event.certificateEligible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ConfirmationNumber,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Certificate Eligible",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (event.isRegistered) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Registered",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    ElevatedButton(
                        onClick = onRegister,
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Text(
                            text = "Register",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EEventHubScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EEventHubScreen(
            state = createMockEventHubState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3EEventHubScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EEventHubScreen(
            state = createMockEventHubState(),
            onAction = {}
        )
    }
}

private fun createMockEventHubState(): M3EEventHubState {
    val sampleEvents = listOf(
        M3EEventItem(
            id = "evt_01",
            title = "Battle of the Bands - Pro Night Qualifiers",
            festName = "Riviera 2027",
            category = M3EEventCategory.CULTURAL,
            date = "15 Feb 2027",
            time = "05:30 PM",
            venue = "Outdoor Stadium (Main Stage)",
            price = "Free Entry",
            isFree = true,
            isRegistered = true,
            certificateEligible = true,
            orderId = "RIV-88910"
        ),
        M3EEventItem(
            id = "evt_02",
            title = "VIT Hack 2026: 36-Hour National Hackathon",
            festName = "Gravitas 2026",
            category = M3EEventCategory.HACKATHONS,
            date = "22 Sep 2026",
            time = "09:00 AM",
            venue = "Anna Auditorium & TT Labs",
            price = "₹200",
            isFree = false,
            isRegistered = false,
            certificateEligible = true
        ),
        M3EEventItem(
            id = "evt_03",
            title = "Autonomous Robotics with ROS2 & SLAM Workshop",
            festName = "RoboVITics Fest",
            category = M3EEventCategory.WORKSHOPS,
            date = "05 Dec 2026",
            time = "10:00 AM",
            venue = "SMV 112 Robotics Lab",
            price = "₹150",
            isFree = false,
            isRegistered = false,
            certificateEligible = true
        )
    )

    return M3EEventHubState(
        events = sampleEvents,
        filteredEvents = sampleEvents,
        selectedCategory = M3EEventCategory.ALL,
        searchQuery = "",
        registeredCount = 1
    )
}
