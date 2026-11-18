package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class M3EOnboardingScreenUiTest {

    @Test
    fun testOnboardingRendersWelcomeAndNavigates() = runComposeUiTest {
        setContent {
            OnboardingScreen()
        }

        onNodeWithText("Welcome to AmazeCC").assertExists()
        onNodeWithText("Next").performClick()
        onNodeWithText("Discover AmazeCC").assertExists()
    }
}
