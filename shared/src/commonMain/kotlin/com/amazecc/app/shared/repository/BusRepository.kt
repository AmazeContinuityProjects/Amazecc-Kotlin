package com.amazecc.app.shared.repository

import com.amazecc.app.shared.model.BusRoute
import com.amazecc.app.shared.model.BusStop

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
