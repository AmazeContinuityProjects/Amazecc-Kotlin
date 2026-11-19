package com.amazecc.app.shared.ui.components

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
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.HomeViewMode
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.screens.m3e.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainTabPager(
    tabScreens: List<Screen>,
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit
) {
    val currentPage = remember(currentScreen, tabScreens) {
        (tabScreens.indexOf(currentScreen)).coerceAtLeast(0)
    }

    // Read as a flow, not as `AppState.homeUsesSimplified`, so that changing the
    // home style repaints the tab even if it is already the visible page.
    val homeViewMode by AppState.homeViewMode.collectAsState()

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
        modifier = Modifier.fillMaxSize(),
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state = pagerState),
        beyondViewportPageCount = 1,
        key = { tabScreens[it] }
    ) { page ->
        when (val screen = tabScreens[page]) {
            Screen.HOME -> M3EHomeScreen()
            Screen.ATTENDANCE -> M3EAttendanceScreen()
            Screen.ACADEMICS -> M3EAcademicsScreen()
            Screen.LIBRARIES -> M3ELibrariesScreenHost()
            Screen.PROFILE -> M3EProfileScreen()
            Screen.MORE -> M3EMoreHubScreen()
            Screen.PAYMENTS -> M3EPaymentsScreenHost()
            Screen.HOSTEL -> M3EHostelScreen()
            Screen.CABSHARE -> M3ECabShareScreen()
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
