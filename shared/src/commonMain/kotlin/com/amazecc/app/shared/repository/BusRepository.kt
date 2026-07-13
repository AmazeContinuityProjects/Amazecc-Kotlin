package com.amazecc.app.shared.repository

import kotlinx.serialization.Serializable

@Serializable
data class BusStop(
    val stopName: String,
    val pickupTime: String,
    val stopOrder: Int
)

@Serializable
data class BusRoute(
    val id: String,
    val type: String,
    val route: String,
    val boardingPoints: List<String>,
    val stops: List<BusStop>? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val supervisorName: String? = null,
    val supervisorPhone: String? = null,
    val driverInchargeName: String? = null,
    val driverInchargePhone: String? = null,
    val busLocation: String? = null,
    val placements: List<String>? = null
)

class BusRepository {

    fun searchBuses(buses: List<BusRoute>, query: String): List<BusRoute> {
        if (query.isBlank()) return buses
        val q = query.lowercase().trim()
        
        return buses.filter { bus ->
            bus.route.lowercase().contains(q) ||
            bus.boardingPoints.any { it.lowercase().contains(q) } ||
            (bus.driverName ?: "").lowercase().contains(q) ||
            (bus.driverPhone ?: "").contains(q)
        }
    }

    fun getBusDetailStops(bus: BusRoute): List<BusStop> {
        return bus.stops?.sortedBy { it.stopOrder } ?: emptyList()
    }
}
