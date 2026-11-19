package com.amazecc.app.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventHubEvent(
    val eid: String,
    val title: String,
    val eligibility: String,
    val type: String,
    val date: String,
    val location: String,
    val price: String,
    val time: String? = null,
    val posterUrl: String? = null
)

/**
 * One row of `/EventHub/profile` (`#regTable`).
 *
 * The first five fields are the original wire shape and keep their `@SerialName` aliases.
 * Everything after [time] is the richer payload the AmazeCC-API profile route returns; the
 * `*Link` fields are the legacy aliases the older DOM parser emitted and are kept so existing
 * cached snapshots and any screen still reading them keep working.
 */
@Serializable
data class EventHubRegisteredEvent(
    val eid: String,
    @SerialName("name") val title: String,
    @SerialName("venue") val location: String,
    val date: String? = null,
    val time: String? = null,
    val orderId: String? = null,
    val paymentStatus: String? = null,
    val receiptId: String? = null,
    val certificateId: String? = null,
    val receiptUrl: String? = null,
    val certificateUrl: String? = null,
    val payNowUrl: String? = null,
    val payLaterLink: String? = null,
    // Legacy aliases — now absolute URLs, or null (mirrors AmazeCC-API /api/events/profile).
    val receiptLink: String? = null,
    val certificateLink: String? = null,
    val payNowLink: String? = null
)

@Serializable
data class EventHubRegisteredEventsRes(
    val events: List<EventHubRegisteredEvent> = emptyList(),
    val success: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

/** `/EventHub/eventPreview` result. The poster is a public URL built from the event id. */
@Serializable
data class EventHubPreview(
    val eid: String,
    val imageSrc: String? = null,
    val description: String? = null,
    val metaDetails: Map<String, String>? = null
)

/**
 * Outcome of `/EventHub/registerEvent`.
 *
 * `status` is one of: `success`, `already_registered`, `payment_required`, `payment_form`,
 * `redirect`, `session_expired`, `invalid_credentials`, `error`.
 * `url` is an allow-listed Event Hub URL; `html` is a `<base>`-injected payment form.
 */
@Serializable
data class EventHubRegisterRes(
    val status: String,
    val message: String? = null,
    val url: String? = null,
    val html: String? = null
)
