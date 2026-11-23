package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

/**
 * Availability of a classroom in the currently selected period.
 *
 * The free-classroom finder derives this from the timetable data already in the
 * domain snapshot — there is no dedicated server endpoint or VTOP scraper for
 * room occupancy (see [com.amazecc.app.shared.api.AmazeClient.getFreeClassrooms]).
 * There is deliberately no UNKNOWN state: when the source has not published a
 * fact, the screen says so instead of inventing a status.
 */
@Serializable
enum class FreeClassroomStatus {
    FREE,
    OCCUPIED
}

/**
 * One campus room as the free-classroom finder renders it.
 *
 * Field names mirror the M3E screen contract exactly: only these accessors are
 * used by the stateful host's mapping block.
 */
@Serializable
data class FreeClassroomRoom(
    val roomNumber: String,
    val buildingCode: String,
    val floor: Int = 0,
    val capacity: Int = 0,
    val isAirConditioned: Boolean = false,
    /** Blank until a real derivation publishes a status; never the literal word "unknown". */
    val availabilityStatus: String = "",
    val isCurrentlyAvailable: Boolean = false,
    val nextOccupiedTime: String? = null
)

/**
 * Result of the offline free-classroom derivation.
 *
 * `rooms` is empty when the domain snapshot has no timetable data yet; the host
 * retries the refresh on an empty list, so that is a valid "not ready" answer
 * rather than a failure.
 */
@Serializable
data class FreeClassroomsRes(
    val success: Boolean = true,
    val rooms: List<FreeClassroomRoom> = emptyList(),
    /** Three-letter day the derivation answered for, e.g. `MON`. */
    val day: String? = null,
    /** Human-readable period (or "Outside class hours") used for the answer. */
    val period: String? = null,
    val error: String? = null,
    val message: String? = null
)
