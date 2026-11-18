package com.amazecc.app.shared.domain.overhaul

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.math.floor

/**
 * High-performance computation engine for AmazeCC's M3 Expressive overhaul.
 * Fully multi-threaded: executes heavy attendance arithmetic, schedule slot mapping,
 * and filtering on [Dispatchers.Default] so the Compose UI thread is never blocked.
 */
object M3EComputations {

    /**
     * Compute bunk margin for a single course.
     * Formula:
     *   bunkable = floor((attended - (target / 100) * total) / (target / 100))
     *   needed = ceil(((target / 100) * total - attended) / (1 - (target / 100)))
     */
    suspend fun calculateBunkMargin(
        attended: Int,
        total: Int,
        targetPct: Float = 75f
    ): Pair<Int, String> = withContext(Dispatchers.Default) {
        if (total <= 0) return@withContext 0 to "No classes held"

        val targetRatio = targetPct / 100.0
        val currentPct = (attended.toDouble() / total.toDouble()) * 100.0

        if (currentPct >= targetPct) {
            val bunkable = floor((attended - targetRatio * total) / targetRatio).toInt().coerceAtLeast(0)
            bunkable to if (bunkable > 0) "Can bunk $bunkable more class${if (bunkable > 1) "es" else ""}" else "On the limit"
        } else {
            val deficit = targetRatio * total - attended
            val needed = kotlin.math.ceil(deficit / (1.0 - targetRatio)).toInt().coerceAtLeast(1)
            -needed to "Attend next $needed class${if (needed > 1) "es" else ""} to hit ${targetPct.toInt()}%"
        }
    }

    /**
     * Computes the full bunk summary for all courses concurrently.
     */
    suspend fun computeBunkSummary(
        courses: List<M3ECourseAttendanceCardState>,
        targetPct: Float = 75f
    ): M3EBunkSummary = withContext(Dispatchers.Default) {
        var totalBunkable = 0
        var critical = 0
        var warning = 0

        courses.forEach { course ->
            if (course.totalClasses > 0) {
                if (course.percentage < targetPct) {
                    critical++
                } else if (course.percentage < targetPct + 5f) {
                    warning++
                }
                val (margin, _) = calculateBunkMargin(course.attendedClasses, course.totalClasses, targetPct)
                if (margin > 0) {
                    totalBunkable += margin
                }
            }
        }

        M3EBunkSummary(
            totalBunkableClasses = totalBunkable,
            criticalCoursesCount = critical,
            warningCoursesCount = warning,
            targetPercentage = targetPct
        )
    }

    /**
     * Filter courses based on active filter and query string on Dispatchers.Default.
     */
    suspend fun filterCourses(
        courses: List<M3ECourseAttendanceCardState>,
        filter: AttendanceFilter,
        query: String
    ): List<M3ECourseAttendanceCardState> = withContext(Dispatchers.Default) {
        courses.filter { course ->
            val matchesFilter = when (filter) {
                AttendanceFilter.ALL -> true
                AttendanceFilter.SAFE -> course.tone == M3ECardTone.GOOD
                AttendanceFilter.AT_RISK -> course.tone != M3ECardTone.GOOD
                AttendanceFilter.EMBEDDED -> course.isEmbedded
            }

            val matchesQuery = query.isBlank() ||
                course.courseCode.contains(query, ignoreCase = true) ||
                course.courseTitle.contains(query, ignoreCase = true) ||
                course.faculty.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    /**
     * Determine card tone from percentage.
     */
    fun toneForPercentage(pct: Double, targetPct: Float = 75f): M3ECardTone = when {
        pct >= targetPct + 10.0 -> M3ECardTone.GOOD
        pct >= targetPct -> M3ECardTone.WARNING
        else -> M3ECardTone.CRITICAL
    }

    /**
     * Filter bus routes on Dispatchers.Default.
     */
    suspend fun filterBusRoutes(
        routes: List<M3EBusRoute>,
        filter: M3ETransportFilter,
        query: String
    ): List<M3EBusRoute> = withContext(Dispatchers.Default) {
        routes.filter { route ->
            val matchesFilter = when (filter) {
                M3ETransportFilter.ALL -> true
                M3ETransportFilter.AC -> route.isAc
                M3ETransportFilter.NON_AC -> !route.isAc
                M3ETransportFilter.EVENING_5PM -> route.placements.any { it.dispersalTime.contains("5", ignoreCase = true) }
                M3ETransportFilter.EVENING_6PM -> route.placements.any { it.dispersalTime.contains("6", ignoreCase = true) }
            }

            val matchesQuery = query.isBlank() ||
                route.routeNumber.contains(query, ignoreCase = true) ||
                route.routeName.contains(query, ignoreCase = true) ||
                route.driverName.contains(query, ignoreCase = true) ||
                route.stops.any { it.stopName.contains(query, ignoreCase = true) }

            matchesFilter && matchesQuery
        }
    }

    /**
     * Filter cab share rides on Dispatchers.Default.
     */
    suspend fun filterCabShareRides(
        rides: List<M3ECabShareRide>,
        filter: M3ECabShareFilter,
        query: String
    ): List<M3ECabShareRide> = withContext(Dispatchers.Default) {
        rides.filter { ride ->
            val matchesFilter = when (filter) {
                M3ECabShareFilter.ALL -> true
                M3ECabShareFilter.AIRPORT -> ride.hubName.contains("Airport", ignoreCase = true)
                M3ECabShareFilter.RAILWAY -> ride.hubName.contains("Station", ignoreCase = true) ||
                    ride.hubName.contains("Railway", ignoreCase = true) ||
                    ride.hubName.contains("Katpadi", ignoreCase = true)
                M3ECabShareFilter.MY_TRIPS -> ride.isUserCreator || ride.isJoined
            }

            val matchesQuery = query.isBlank() ||
                ride.hubName.contains(query, ignoreCase = true) ||
                ride.creatorName.contains(query, ignoreCase = true) ||
                ride.travelDate.contains(query, ignoreCase = true) ||
                (ride.notes?.contains(query, ignoreCase = true) == true)

            matchesFilter && matchesQuery
        }
    }

    /**
     * Filter classroom slots on Dispatchers.Default.
     */
    suspend fun filterClassrooms(
        classrooms: List<M3EClassroomSlot>,
        building: M3EClassroomBuilding,
        onlyAvailableNow: Boolean,
        query: String
    ): List<M3EClassroomSlot> = withContext(Dispatchers.Default) {
        classrooms.filter { room ->
            val matchesBuilding = when (building) {
                M3EClassroomBuilding.ALL -> true
                else -> room.buildingCode.equals(building.code, ignoreCase = true)
            }

            val matchesAvailability = !onlyAvailableNow || room.isCurrentlyAvailable

            val matchesQuery = query.isBlank() ||
                room.roomNumber.contains(query, ignoreCase = true) ||
                room.buildingCode.contains(query, ignoreCase = true)

            matchesBuilding && matchesAvailability && matchesQuery
        }
    }
}
