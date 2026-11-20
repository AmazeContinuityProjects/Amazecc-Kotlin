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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Immutable, stateless state for [M3EClubDetailScreen]. */
@Immutable
data class M3EClubDetailState(
    val clubId: String = "",
    val name: String = "",
    val description: String = "",
    val website: String? = null,
    val instagram: String? = null,
    val whatsapp: String? = null,
    val isEnrolled: Boolean = false,
    val isLoading: Boolean = false,
    /** Present only when this club is linked to a real event-hub registration. */
    val eventId: String? = null,
    /** The club's own application site, used when there is no linked event. */
    val applyUrl: String? = null,
    val registrationStatus: M3EEventRegistrationUi? = null,
    val notice: String? = null
)

/** User intents emitted by [M3EClubDetailScreen]. */
sealed interface M3EClubDetailAction {
    /** Register through the event hub using the club's linked event id. */
    data object RegisterEvent : M3EClubDetailAction
    data class OpenLink(val url: String) : M3EClubDetailAction
    data object Refresh : M3EClubDetailAction
    data object Back : M3EClubDetailAction
    data object DismissStatus : M3EClubDetailAction
    data object DismissNotice : M3EClubDetailAction
}

/**
 * Material 3 Expressive Club Detail screen.
 * 100% stateless: accepts [M3EClubDetailState], emits [M3EClubDetailAction].
 */
@Composable
fun M3EClubDetailScreen(
    state: M3EClubDetailState,
    onAction: (M3EClubDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading && state.clubId.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (state.clubId.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Club not found", fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
        val applyUrl = state.applyUrl
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(state.name.firstOrNull()?.uppercase() ?: "C", fontSize = 32.sp, fontWeight = FontWeight.Black, color = colors.onPrimaryContainer)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.name.ifBlank { "Club" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!state.website.isNullOrBlank()) {
                                    IconButton(onClick = { onAction(M3EClubDetailAction.OpenLink(state.website)) }) {
                                        Icon(Icons.Rounded.Language, contentDescription = "Website", tint = colors.primary)
                                    }
                                }
                                if (!state.instagram.isNullOrBlank()) {
                                    val url = if (state.instagram.startsWith("http")) state.instagram else "https://instagram.com/${state.instagram.removePrefix("@")}"
                                    IconButton(onClick = { onAction(M3EClubDetailAction.OpenLink(url)) }) {
                                        Icon(Icons.Rounded.CameraAlt, contentDescription = "Instagram", tint = colors.primary)
                                    }
                                }
                                if (!state.whatsapp.isNullOrBlank()) {
                                    IconButton(onClick = { onAction(M3EClubDetailAction.OpenLink(state.whatsapp)) }) {
                                        Icon(Icons.Rounded.Chat, contentDescription = "WhatsApp", tint = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "about") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("About Us", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(
                            state.description.ifBlank { "No description provided." },
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            applyUrl?.takeIf { it.isNotBlank() }?.let { siteUrl ->
                item(key = "hiring") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW,
                        onClick = { onAction(M3EClubDetailAction.OpenLink(siteUrl)) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WorkOutline, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                                Text("Applications", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                            Text(
                                "Apply on the club's own site.",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "feed") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Club Feed", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Feed integration coming soon", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            item(key = "enroll") {
                val siteUrl = applyUrl?.takeIf { it.isNotBlank() }
                val hasEvent = !state.eventId.isNullOrBlank()
                val canApply = hasEvent || siteUrl != null
                val cardText = when {
                    hasEvent && state.isEnrolled -> "Registered"
                    hasEvent -> "Register for Event"
                    siteUrl != null -> "Apply on club's own site"
                    else -> "Applications are handled on the club's own site"
                }
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.STANDARD,
                    borderColor = if (hasEvent && state.isEnrolled) colors.success else colors.primary,
                    onClick = if (canApply) {
                        {
                            if (hasEvent) {
                                onAction(M3EClubDetailAction.RegisterEvent)
                            } else if (siteUrl != null) {
                                onAction(M3EClubDetailAction.OpenLink(siteUrl))
                            }
                        }
                    } else null
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (hasEvent && state.isEnrolled) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            text = cardText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasEvent && state.isEnrolled) colors.success else colors.primary
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
        }

        val status = state.registrationStatus
        if (status != null) {
            val normalized = status.status.trim().lowercase()
            val isPayment = normalized == "payment_required" || normalized == "payment_form"
            val isRedirect = normalized == "redirect"
            val title = when {
                normalized == "success" -> "Registration confirmed"
                normalized == "already_registered" -> "Already registered"
                isPayment -> "Payment required"
                isRedirect -> "Continue on the event hub"
                normalized in setOf("session_expired", "invalid_credentials", "error") -> "Registration could not be completed"
                else -> "Registration update"
            }
            val paymentUrl = status.paymentUrl?.takeIf { it.isNotBlank() }
            M3EDismissibleSheet(
                isOpen = true,
                onDismiss = { onAction(M3EClubDetailAction.DismissStatus) },
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
                        status.message?.takeIf { it.isNotBlank() }
                            ?: if (isPayment) "Complete payment to confirm your seat." else "The event hub returned an update for your registration.",
                        color = colors.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if ((isPayment || isRedirect) && paymentUrl != null) {
                            TextButton(onClick = { onAction(M3EClubDetailAction.DismissStatus) }) {
                                Text("Later", color = colors.onSurfaceVariant)
                            }
                            TextButton(onClick = { onAction(M3EClubDetailAction.OpenLink(paymentUrl)) }) {
                                Text(if (isPayment) "Pay now" else "Continue", color = colors.primary)
                            }
                        } else {
                            TextButton(onClick = { onAction(M3EClubDetailAction.DismissStatus) }) {
                                Text("OK", color = colors.primary)
                            }
                        }
                    }
                }
            }
        }

        val notice = state.notice
        if (!notice.isNullOrBlank()) {
            M3EDismissibleSheet(
                isOpen = true,
                onDismiss = { onAction(M3EClubDetailAction.DismissNotice) },
                title = "Club applications"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(notice, color = colors.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onAction(M3EClubDetailAction.DismissNotice) }) {
                            Text("OK", color = colors.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun mockClubDetailState() = M3EClubDetailState(
    clubId = "club_1",
    name = "ACM Student Chapter",
    description = "A community of developers building projects, competing and learning together.",
    instagram = "@amazecc"
)

@Preview
@Composable
private fun M3EClubDetailScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EClubDetailScreen(state = mockClubDetailState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EClubDetailScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EClubDetailScreen(state = mockClubDetailState(), onAction = {}) }
}
