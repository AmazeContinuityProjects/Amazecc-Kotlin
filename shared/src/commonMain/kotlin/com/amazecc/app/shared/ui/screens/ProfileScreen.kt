package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.screens.profile.*
import com.amazecc.app.shared.ui.strings.Strings

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors

    var currentSubScreen by remember { mutableStateOf<ProfileSubScreen?>(null) }

    val currentTab by AppState.currentScreen.collectAsState()
    AppBackHandler(enabled = currentSubScreen != null && currentTab == Screen.PROFILE) {
        currentSubScreen = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = currentSubScreen?.title ?: Strings.profile,
            description = currentSubScreen?.description ?: "Your personal information",
            showBackButton = currentSubScreen != null,
            showSyncButton = currentSubScreen == null,
            onRefresh = { AppState.refreshProfile() },
            onBackOverride = if (currentSubScreen != null) { { currentSubScreen = null } } else null,
            enabledScreens = setOf(Screen.PROFILE)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderSpacer()

            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    if (targetState == null) {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut())
                    }
                },
                label = "profileNav"
            ) { sub ->
                if (sub == null) {
                    ProfileHub(
                        onOpenSubScreen = { currentSubScreen = it }
                    )
                } else {
                    when (sub) {
                        ProfileSubScreen.PERSONAL_INFO -> PersonalInformationPage()
                        ProfileSubScreen.ACADEMIC_DETAILS -> AcademicDetailsPage()
                        ProfileSubScreen.UNIVERSITY_OFFICIALS -> UniversityOfficialsPage()
                        ProfileSubScreen.EPT_SCHEDULE -> EptSchedulePage()
                        ProfileSubScreen.REGISTRATION -> RegistrationSchedulePage()
                        ProfileSubScreen.UNIVERSITY_DAY -> UniversityDayPage()
                        ProfileSubScreen.BANK_DETAILS -> BankDetailsPage()
                        ProfileSubScreen.DAYBOARDER -> DayboarderPage()
                        ProfileSubScreen.APAAR_ID -> ApaarIdPage()
                        ProfileSubScreen.CREDENTIALS -> CredentialsAndRanksPage()
                    }
                }
            }

            FooterSpacer()
        }
    }
}
