package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.domain.overhaul.M3ECabShareAction
import com.amazecc.app.shared.domain.overhaul.M3ECabShareFilter
import com.amazecc.app.shared.domain.overhaul.M3ECabShareHub
import com.amazecc.app.shared.domain.overhaul.M3ECabShareRide
import com.amazecc.app.shared.domain.overhaul.M3ECabShareState
import com.amazecc.app.shared.domain.overhaul.M3EGenderPreference
import com.amazecc.app.shared.model.CabShareTrip
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.dialPhoneNumber

/**
 * D-039 stateful host for cab-share trip search, creation, joining and match handling.
 *
 * The lightweight bridge in `M3EStateBridge` already covers create/join/refresh, but it has
 * no server-side trip search and no `cabHandleMatchAction` for the requests carried on the
 * student's own trips. This host wires all four real AppState operations. App.kt only needs
 * to route `Screen.CABSHARE` here (one-line change reported to the owner).
 */
@Composable
fun M3ECabShareHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val cabHubs by AppState.cabHubs.collectAsState()
    val isLoading by AppState.cabLoading.collectAsState()
    val identity by UserStore.identity.collectAsState()

    var searchResults by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var myTrips by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(M3ECabShareFilter.ALL) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        AppState.fetchCabHubs()
        // A blank date is a legitimate "all trips" search (AmazeClient omits the param).
        AppState.cabSearchTripsNew(null, null, "") { found -> searchResults = found }
        AppState.cabRefreshMyTripsNew { mine, joined ->
            myTrips = (mine + joined).distinctBy { it.trip_id }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val trips = remember(searchResults, myTrips) {
        (myTrips + searchResults).distinctBy { it.trip_id }
    }
    val hubs = remember(cabHubs) {
        cabHubs.map { M3ECabShareHub(it.hub_id.toString(), it.hub_name, "Hub #${it.hub_id}") }
    }
    val rides = remember(trips, identity.regNo) {
        trips.map { trip ->
            val isMine = trip.reg_number == identity.regNo || trip.owner_name == identity.displayName
            M3ECabShareRide(
                tripId = trip.trip_id.toString(),
                creatorName = trip.owner_name.takeIf { it.isNotBlank() }
                    ?: trip.name.takeIf { it.isNotBlank() } ?: "Student",
                creatorRegNo = trip.reg_number,
                hubName = trip.hub_name.takeIf { it.isNotBlank() }
                    ?: trip.from_hub_name.takeIf { it.isNotBlank() } ?: "Campus",
                travelDate = trip.travel_date,
                preferredTime = trip.preferred_time,
                toleranceHours = trip.tolerance_hours.toInt().coerceAtLeast(1),
                availableSeats = (4 - trip.requests.size).coerceAtLeast(1),
                totalSeats = 4,
                genderPreference = M3EGenderPreference.ANY,
                notes = trip.notes.takeIf { it.isNotBlank() },
                creatorPhone = trip.owner_phone.takeIf { it.isNotBlank() },
                isUserCreator = isMine,
                isJoined = trip.match_status != null,
                status = trip.status.uppercase()
            )
        }
    }
    val pendingMatches = remember(myTrips) {
        myTrips.flatMap { trip ->
            trip.requests
                .filter { it.status.equals("pending", ignoreCase = true) }
                .map { request -> M3ECabMatchUi(request.match_id, request.name, trip.trip_id.toString()) }
        }
    }
    val filtered = remember(rides, searchQuery, activeFilter) {
        rides.filter { ride ->
            val matchesFilter = when (activeFilter) {
                M3ECabShareFilter.ALL -> true
                M3ECabShareFilter.AIRPORT -> ride.hubName.contains("Airport", ignoreCase = true)
                M3ECabShareFilter.RAILWAY ->
                    ride.hubName.contains("Station", ignoreCase = true) ||
                        ride.hubName.contains("Katpadi", ignoreCase = true)
                M3ECabShareFilter.MY_TRIPS -> ride.isUserCreator || ride.isJoined
            }
            val matchesQuery = searchQuery.isBlank() ||
                ride.hubName.contains(searchQuery, ignoreCase = true) ||
                ride.creatorName.contains(searchQuery, ignoreCase = true) ||
                ride.travelDate.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val state = M3ECabShareState(
        hubs = hubs,
        rides = filtered,
        activeFilter = activeFilter,
        searchQuery = searchQuery,
        isCreateDialogOpen = isCreateDialogOpen,
        isLoading = isLoading,
        errorMessage = notice
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECabShareScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECabShareAction.SelectFilter -> activeFilter = action.filter
                    is M3ECabShareAction.SearchQueryChanged -> searchQuery = action.query
                    is M3ECabShareAction.OpenCreateDialog -> isCreateDialogOpen = true
                    is M3ECabShareAction.CloseCreateDialog -> isCreateDialogOpen = false
                    is M3ECabShareAction.JoinTrip -> {
                        action.tripId.toLongOrNull()?.let { id ->
                            AppState.cabRequestJoinNew(id) { success, message ->
                                notice = message
                                if (success) refresh()
                            }
                        }
                    }
                    is M3ECabShareAction.ContactCreator -> {
                        if (action.phoneNumber.isNotBlank()) dialPhoneNumber(action.phoneNumber)
                        else notice = "This creator has not shared a contact number."
                    }
                    is M3ECabShareAction.Refresh -> refresh()
                }
            },
            modifier = modifier,
            onCreateRide = { draft ->
                val fromId = draft.fromHubId.toIntOrNull()
                val toId = draft.toHubId.toIntOrNull()
                if (fromId == null || toId == null) {
                    notice = "Choose a valid pick-up and drop hub."
                } else {
                    notice = null
                    AppState.cabCreateTripNew(
                        fromHubId = fromId,
                        toHubId = toId,
                        date = draft.date.trim(),
                        time = draft.time.trim(),
                        tolerance = draft.tolerance.toDoubleOrNull() ?: 1.0,
                        seats = draft.seats.toIntOrNull()?.coerceIn(1, 6) ?: 3,
                        gender = draft.gender,
                        notes = draft.notes.trim(),
                        onResult = { success, message ->
                            notice = message
                            if (success) {
                                isCreateDialogOpen = false
                                refresh()
                            }
                        }
                    )
                }
            },
            pendingMatches = pendingMatches,
            onMatchAction = { matchId, matchAction ->
                AppState.cabHandleMatchAction(matchId, matchAction) { success, message ->
                    notice = message
                    if (success) refresh()
                }
            }
        )
    }
}
