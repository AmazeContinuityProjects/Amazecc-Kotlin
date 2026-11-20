package com.amazecc.app.shared.domain.overhaul

import com.amazecc.app.shared.domain.EmbeddedLinkCode
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

    /** The one key a course is counted on: `(T)`, `(L)` and bare-code rows all collapse here. */
    fun canonicalCourseKey(code: String): String = EmbeddedLinkCode.baseCode(code)

    /**
     * De-duplicate attendance cards on the canonical base code, keeping the richest report of a
     * course rather than adding two rows of the same half. Callers that need the two halves of an
     * embedded pair combined should build their cards from `Projections.mergedCourses`; this
     * guard is for the `BCSE101(T)` + `BCSE101` double-report that used to inflate the summary.
     */
    fun deduplicateByBaseCode(cards: List<M3ECourseAttendanceCardState>): List<M3ECourseAttendanceCardState> {
        val byBase = LinkedHashMap<String, M3ECourseAttendanceCardState>()
        cards.forEach { card ->
            val key = EmbeddedLinkCode.baseCode(card.courseCode).ifEmpty { card.courseCode }
            val current = byBase[key]
            // More classes held means more information; an already-linked card wins a tie.
            if (current == null || card.totalClasses > current.totalClasses ||
                (card.totalClasses == current.totalClasses && card.isEmbedded && !current.isEmbedded)
            ) {
                byBase[key] = card
            }
        }
        return byBase.values.toList()
    }

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

        deduplicateByBaseCode(courses).forEach { course ->
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
     * The one attendance threshold band shared by the hub card, the home headline and the
     * per-course deep dive. SAFE requires a 10-point margin above the live target; merely
     * being at/above the target is NEAR_TARGET, and below it is AT_RISK. Having these
     * thresholds in one place prevents two surfaces from calling the same percentage safe.
     */
    enum class AttendanceBand { SAFE, NEAR_TARGET, AT_RISK }

    fun attendanceBand(pct: Double, targetPct: Float): AttendanceBand = when {
        pct >= targetPct + 10.0 -> AttendanceBand.SAFE
        pct >= targetPct -> AttendanceBand.NEAR_TARGET
        else -> AttendanceBand.AT_RISK
    }

    /**
     * Resolve the live target. A value threaded into state wins; otherwise recover the
     * target the host already published in [statusText] (e.g. "... (target 85%)"), so the
     * UI never falls back to a hardcoded 75 that contradicts the status it displays.
     */
    fun resolveAttendanceTarget(explicitTarget: Float?, statusText: String): Float {
        if (explicitTarget != null && explicitTarget in 1f..100f) return explicitTarget
        val match = Regex(
            "(?:target\\s*(\\d{1,3})\\s*%|(\\d{1,3})\\s*%\\s*target)",
            RegexOption.IGNORE_CASE
        ).find(statusText)
        // groupValues[0] is the full match (e.g. "target 85%") which is not a number;
        // skip it and read the actual capture group(s).
        val parsed = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toFloatOrNull()
        return parsed?.takeIf { it in 1f..100f } ?: 75f
    }

    /** True only when the host actually published attendance data for this stat. */
    fun hasAttendanceData(statusText: String): Boolean =
        statusText.isNotBlank() && !statusText.contains("not available", ignoreCase = true)

    /**
     * Determine card tone from percentage, derived from the same band used everywhere else.
     */
    fun toneForPercentage(pct: Double, targetPct: Float = 75f): M3ECardTone =
        when (attendanceBand(pct, targetPct)) {
            AttendanceBand.SAFE -> M3ECardTone.GOOD
            AttendanceBand.NEAR_TARGET -> M3ECardTone.WARNING
            AttendanceBand.AT_RISK -> M3ECardTone.CRITICAL
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

    /**
     * Filter fest & campus events on Dispatchers.Default.
     */
    suspend fun filterEvents(
        events: List<M3EEventItem>,
        category: M3EEventCategory,
        query: String
    ): List<M3EEventItem> = withContext(Dispatchers.Default) {
        events.filter { event ->
            val matchesCategory = when (category) {
                M3EEventCategory.ALL -> true
                M3EEventCategory.MY_REGISTRATIONS -> event.isRegistered
                else -> event.category == category
            }

            val matchesQuery = query.isBlank() ||
                event.title.contains(query, ignoreCase = true) ||
                event.venue.contains(query, ignoreCase = true) ||
                event.festName.contains(query, ignoreCase = true)

            matchesCategory && matchesQuery
        }
    }

    /**
     * Filter clubs & chapters on Dispatchers.Default.
     */
    suspend fun filterClubs(
        clubs: List<M3EClubItem>,
        category: M3EClubCategory,
        query: String
    ): List<M3EClubItem> = withContext(Dispatchers.Default) {
        clubs.filter { club ->
            val matchesCategory = when (category) {
                M3EClubCategory.ALL -> true
                M3EClubCategory.RECRUITING -> club.isRecruiting
                else -> club.category == category
            }

            val matchesQuery = query.isBlank() ||
                club.name.contains(query, ignoreCase = true) ||
                club.studentLead.contains(query, ignoreCase = true) ||
                club.facultyCoordinator.contains(query, ignoreCase = true) ||
                club.description.contains(query, ignoreCase = true)

            matchesCategory && matchesQuery
        }
    }

    /**
     * Filter unified tasks on Dispatchers.Default.
     */
    suspend fun filterTasks(
        tasks: List<M3ETaskItem>,
        filter: M3ETasksFilter,
        query: String
    ): List<M3ETaskItem> = withContext(Dispatchers.Default) {
        tasks.filter { task ->
            val matchesFilter = when (filter) {
                M3ETasksFilter.ALL -> true
                M3ETasksFilter.PENDING -> task.status == M3ETaskStatus.PENDING
                M3ETasksFilter.IN_PROGRESS -> task.status == M3ETaskStatus.IN_PROGRESS
                M3ETasksFilter.DONE -> task.status == M3ETaskStatus.DONE
                M3ETasksFilter.ASSIGNMENTS -> task.kind == M3ETaskKind.DIGITAL_ASSIGNMENT
            }

            val matchesQuery = query.isBlank() ||
                task.title.contains(query, ignoreCase = true) ||
                task.courseCode.contains(query, ignoreCase = true) ||
                task.courseTitle.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }
}
