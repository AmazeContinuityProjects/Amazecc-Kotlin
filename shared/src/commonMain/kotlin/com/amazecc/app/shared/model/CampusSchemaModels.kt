package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CampusSchema(
    val theory: List<TimetablePeriod> = emptyList(),
    val lab: List<TimetablePeriod> = emptyList()
)

@Serializable
data class TimetablePeriod(
    val start: String = "",
    val end: String = "",
    val days: Map<String, String> = emptyMap(),
    val lunch: Boolean? = null
)
