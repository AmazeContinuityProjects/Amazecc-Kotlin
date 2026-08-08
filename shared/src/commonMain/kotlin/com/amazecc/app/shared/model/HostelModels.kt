package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class LeaveItem(
    val visitPlace: String? = null,
    val reason: String? = null,
    val leaveType: String? = null,
    val from: String? = null,
    val to: String? = null,
    val status: String? = null
)
