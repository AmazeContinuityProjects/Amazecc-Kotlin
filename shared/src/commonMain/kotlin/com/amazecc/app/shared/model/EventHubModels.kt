package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EventHubEvent(
    val eid: String,
    val id: String? = null,
    val title: String,
    val eligibility: String,
    val type: String,
    val date: String,
    val location: String,
    val price: String,
    val time: String? = null,
    val posterUrl: String? = null,
    val registeredDetails: JsonObject? = null,
    val isPastEvent: Boolean? = null
)

@Serializable
data class EventHubPreview(
    val eid: String,
    val id: String? = null,
    val posterUrl: String? = null,
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

