package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.screens.DashboardScreen
import com.amazecc.app.shared.ui.screens.ProfileScreen
import com.amazecc.app.shared.ui.screens.QBankScreen
import com.amazecc.app.shared.ui.screens.SocialScreen
import com.amazecc.app.shared.ui.screens.academics.AcademicsScreen
import com.amazecc.app.shared.ui.screens.academics.AttendanceScreen
import com.amazecc.app.shared.ui.screens.cabshare.CabShareScreen
import com.amazecc.app.shared.ui.screens.events.EventHubScreen
import com.amazecc.app.shared.ui.screens.hostel.HostelScreen
import com.amazecc.app.shared.ui.screens.libraries.LibrariesScreen
import com.amazecc.app.shared.ui.screens.more.MoreScreen
import com.amazecc.app.shared.ui.screens.payments.PaymentsScreen
import com.amazecc.app.shared.ui.screens.transport.TransportScreen

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

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { tabScreens.size }
    )

    var settledPage by remember { mutableStateOf(pagerState.currentPage) }

    LaunchedEffect(currentScreen) {
        val targetIndex = tabScreens.indexOf(currentScreen)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page != settledPage && page in tabScreens.indices) {
            settledPage = page
            val screen = tabScreens[page]
            if (screen != currentScreen) {
                onScreenChange(screen)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        when (val screen = tabScreens[page]) {
            Screen.HOME -> DashboardScreen()
            Screen.ATTENDANCE -> AttendanceScreen()
            Screen.ACADEMICS -> AcademicsScreen()
            Screen.LIBRARIES -> LibrariesScreen()
            Screen.PROFILE -> ProfileScreen()
            Screen.MORE -> MoreScreen()
            Screen.PAYMENTS -> PaymentsScreen()
            Screen.HOSTEL -> HostelScreen()
            Screen.CABSHARE -> CabShareScreen()
            Screen.TRANSPORT -> TransportScreen()
            Screen.EVENTS -> EventHubScreen()
            Screen.QBANK -> QBankScreen()
            Screen.SOCIAL -> SocialScreen()
            else -> {}
        }
    }
}
