package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3EHorizontalTimetableGrid
import com.amazecc.app.shared.ui.m3e.components.M3EVerticalTimetableGrid
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/** Shared LIFO id: the opener MUST push/pop this on `AppState.overlayStack`, or the sheet's dismissal guard blocks every route. */
internal const val HOME_TIMETABLE_OVERLAY_ID = "home-timetable"

/** Density choice for the full-week timetable sheet; maps to the grids' `denseGrid` flag. */
private enum class TimetableDensity(val label: String) {
    FULL("Full"),
    COMPACT("Compact")
}

/**
 * The home "View Full Timetable" entry point, matching the web app's
 * `BottomSheet overlayId="home-timetable"`.
 *
 * This is deliberately a thin composition: it reuses [M3EDismissibleSheet] for all
 * dismissal routes and insets, [M3EConnectedButtonGroup] for both switchers, and the
 * two existing dense grids. No grid engine is rebuilt here and no data is invented —
 * everything rendered comes from [state].
 */
@Composable
fun M3ETimetableSheet(
    state: M3ETimetableState,
    onAction: (M3ETimetableAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    val colors = M3ETheme.colors
    val viewModes = listOf(TimetableViewMode.VERTICAL, TimetableViewMode.HORIZONTAL)

    // SCHEDULE is the full screen's day-list mode; it has no grid counterpart, so inside
    // this sheet it resolves to the vertical grid and the switcher always has a selection.
    val selectedViewMode = when (state.viewMode) {
        TimetableViewMode.HORIZONTAL -> TimetableViewMode.HORIZONTAL
        else -> TimetableViewMode.VERTICAL
    }
    val selectedDensity = if (state.denseGrid) TimetableDensity.COMPACT else TimetableDensity.FULL

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        modifier = modifier,
        title = "Full Weekly Timetable",
        overlayId = HOME_TIMETABLE_OVERLAY_ID
    ) {
        Text(
            text = "Slot matrix, timeslots & classroom venues",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Horizontal / vertical view changer.
            M3EConnectedButtonGroup(
                items = viewModes,
                selectedItem = selectedViewMode,
                onItemSelected = { onAction(M3ETimetableAction.SetViewMode(it)) },
                labelProvider = { mode ->
                    when (mode) {
                        TimetableViewMode.VERTICAL -> "Vertical"
                        TimetableViewMode.HORIZONTAL -> "Horizontal"
                        else -> mode.label
                    }
                }
            )

            // Full / Compact density switcher.
            M3EConnectedButtonGroup(
                items = TimetableDensity.entries,
                selectedItem = selectedDensity,
                onItemSelected = { onAction(M3ETimetableAction.SetDenseGrid(it == TimetableDensity.COMPACT)) },
                labelProvider = { it.label }
            )

            // A refresh failure is shown inline in sheet mode: a second Dialog here would stack
            // two sheets and force the user through two dismissals.
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = colors.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // The dense grids are naturally taller than a phone screen. Cap the body and scroll
            // it so the grabber, title and both switchers stay on screen (web's 88dvh cap).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedViewMode) {
                    TimetableViewMode.HORIZONTAL -> M3EHorizontalTimetableGrid(
                        days = state.days,
                        onSlotClick = { onAction(M3ETimetableAction.SlotClicked(it)) },
                        denseGrid = state.denseGrid
                    )
                    else -> M3EVerticalTimetableGrid(
                        days = state.days,
                        selectedDayIndex = state.selectedDayIndex,
                        onSelectDay = { onAction(M3ETimetableAction.SelectDay(it)) },
                        onSlotClick = { onAction(M3ETimetableAction.SlotClicked(it)) },
                        denseGrid = state.denseGrid
                    )
                }
            }
        }
    }
}
