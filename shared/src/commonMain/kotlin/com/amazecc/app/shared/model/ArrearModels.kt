package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiTable(
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val title: String? = null,
    val caption: String? = null
)

@Serializable
data class KeyValuePair(
    val label: String,
    val value: String
)

@Serializable
data class ApiMessage(
    val message: String,
    val type: String = "info"
)

@Serializable
data class ArrearResponse(
    val success: Boolean = true,
    val tables: List<ApiTable> = emptyList(),
    val keyValuePairs: List<KeyValuePair> = emptyList(),
    val messages: List<ApiMessage> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CircularItem(
    val id: String? = null,
    val title: String? = null,
    val children: List<CircularItem>? = null
)

@Serializable
data class CircularsRes(
    val success: Boolean = true,
    val circulars: List<CircularItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)
