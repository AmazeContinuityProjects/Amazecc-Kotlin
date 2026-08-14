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

@Serializable
data class MessMenuDay(
    val Day: String? = null,
    val Breakfast: String? = null,
    val Lunch: String? = null,
    val Snacks: String? = null,
    val Dinner: String? = null
)

@Serializable
data class MessMenuRes(
    val list: List<MessMenuDay> = emptyList()
)

@Serializable
data class LaundrySlotItem(
    val Date: String? = null,
    val RoomNumber: String? = null
)

@Serializable
data class LaundryRes(
    val list: List<LaundrySlotItem> = emptyList()
)
