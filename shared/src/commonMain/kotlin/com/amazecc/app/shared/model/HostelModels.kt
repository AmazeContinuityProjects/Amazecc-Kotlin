package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Hostel(
    val gender: String? = null,
    val isHosteller: Boolean? = null,
    val blockName: String? = null,
    val roomNo: String? = null,
    val messInfo: String? = null
)

@Serializable
data class LeaveItem(
    val leaveId: String? = null,
    val visitPlace: String? = null,
    val reason: String? = null,
    val leaveType: String? = null,
    val from: String? = null,
    val to: String? = null,
    val status: String? = null,
    val remarks: String? = null
)
