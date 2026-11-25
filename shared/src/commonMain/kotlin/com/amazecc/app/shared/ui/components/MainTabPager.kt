package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.screens.m3e.*
import kotlin.math.abs

/**
 * D-078: document-wide horizontal swipe, ported from the web
 * `primitives/useHorizontalSwipe.ts` constants (min 40, slop 8, axis bias 1.2).
 *
 * The gesture lives on the pager itself (not on a child), so a nested list or
 * button cannot swallow it. [enabled] is the overlay guard: while any overlay
 * is registered in the LIFO stack the swipe is inert, so dragging over a sheet
 * or the command palette can never change the tab underneath.
 *
 * The drag commits once per gesture; a tap or a mostly-vertical scroll does
 * nothing, matching the web axis check.
 */
private fun Modifier.tabSwipe(
    enabled: Boolean,
    previous: () -> Unit,
    next: () -> Unit
): Modifier = pointerInput(enabled, previous, next) {
    if (!enabled) return@pointerInput
    val slopPx = 8.dp.toPx()
    val minSwipePx = 40.dp.toPx()
    var totalX = 0f
    var totalY = 0f
    var horizontal = false
    var fired = false

    detectDragGestures(
        onDragStart = {
            totalX = 0f
            totalY = 0f
            horizontal = false
            fired = false
        },
        onDragEnd = {
            totalX = 0f
            totalY = 0f
            horizontal = false
            fired = false
        },
        onDragCancel = {
            totalX = 0f
            totalY = 0f
            horizontal = false
            fired = false
        }
    ) { _, dragAmount ->
        totalX += dragAmount.x
        totalY += dragAmount.y
        if (!horizontal && abs(totalX) >= slopPx && abs(totalX) > abs(totalY) * 1.2f) {
            horizontal = true
        }
        if (horizontal && !fired && abs(totalX) >= minSwipePx) {
            fired = true
            if (totalX < 0f) next() else previous()
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainTabPager(
    tabScreens: List<Screen>,
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    academicsContent: @Composable () -> Unit = { M3EAcademicsScreen() }
) {
    val currentPage = remember(currentScreen, tabScreens) {
        (tabScreens.indexOf(currentScreen)).coerceAtLeast(0)
    }

    // D-078: any registered overlay (command palette, sheet, help) disables the
    // document-wide tab swipe until it is dismissed.
    val openOverlays by AppState.overlayStack.collectAsState()

    // Captured once per navigation so a swipe that lands while the pager is
    // already animating cannot jump two pages.
    val swipePrevious = remember(currentPage, tabScreens, onScreenChange) {
        { if (currentPage > 0) onScreenChange(tabScreens[currentPage - 1]) }
    }
    val swipeNext = remember(currentPage, tabScreens, onScreenChange) {
        { if (currentPage < tabScreens.lastIndex) onScreenChange(tabScreens[currentPage + 1]) }
    }

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { tabScreens.size }
    )

    LaunchedEffect(currentScreen) {
        val targetIndex = tabScreens.indexOf(currentScreen)
        if (targetIndex >= 0 && pagerState.settledPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (page in tabScreens.indices) {
            val screen = tabScreens[page]
            if (screen != currentScreen) {
                onScreenChange(screen)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .tabSwipe(
                enabled = openOverlays.isEmpty() && tabScreens.size > 1,
                previous = swipePrevious,
                next = swipeNext
            ),
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state = pagerState),
        beyondViewportPageCount = 1,
        key = { page -> if (page in tabScreens.indices) tabScreens[page] else page }
    ) { page ->
        when (val screen = tabScreens[page]) {
            Screen.HOME -> M3EHomeScreen()
            Screen.ATTENDANCE -> M3EAttendanceScreen()
            Screen.ACADEMICS -> academicsContent()
            Screen.LIBRARIES -> M3ELibrariesScreenHost()
            Screen.PROFILE -> M3EProfileScreen()
            Screen.MORE -> M3EMoreHubScreen()
            Screen.PAYMENTS -> M3EPaymentsScreenHost()
            Screen.HOSTEL -> M3EHostelScreen()
            Screen.CABSHARE -> M3ECabShareHost()
            Screen.TRANSPORT -> M3ETransportScreen()
            Screen.EVENTS -> M3EEventHubScreen()
            Screen.QBANK -> M3EQBankWorkspaceHost()
            Screen.SOCIAL -> M3ESocialScreenHost()
            Screen.FFCS_PLANNER -> M3EFfcsPlannerScreenHost()
            Screen.FREE_CLASSROOMS -> M3EFreeClassroomsHost()
            Screen.CALENDAR -> M3ECalendarScreenHost()
            else -> {}
        }
    }
}
