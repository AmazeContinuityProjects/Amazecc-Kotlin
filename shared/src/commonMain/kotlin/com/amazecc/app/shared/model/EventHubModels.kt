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

@Serializable
data class EventHubRegisteredEvent(
    val eid: String,
    @SerialName("name") val title: String,
    @SerialName("venue") val location: String,
    val orderId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val paymentStatus: String? = null,
    val receiptLink: String? = null,
    val certificateLink: String? = null,
    val payNowLink: String? = null,
    val payLaterLink: String? = null
)

@Serializable
data class EventHubRegisteredEventsRes(
    val events: List<EventHubRegisteredEvent> = emptyList(),
    val success: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class EventHubPreview(
    val eid: String,
    val imageSrc: String? = null,
    val description: String? = null,
    val metaDetails: Map<String, String>? = null
)

@Serializable
data class EventHubRegisterRes(
    val status: String,
    val message: String? = null,
    val url: String? = null,
    val html: String? = null
)

