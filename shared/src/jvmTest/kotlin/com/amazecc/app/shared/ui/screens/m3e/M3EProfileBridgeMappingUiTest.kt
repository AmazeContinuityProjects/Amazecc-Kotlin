package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.model.ApaarInfo
import com.amazecc.app.shared.model.BankInfo
import com.amazecc.app.shared.model.DayboarderInfo
import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.AccountCredential
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.state.IdentitySource
import com.amazecc.app.shared.state.UserStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Integration coverage for the risky part of the profile bridge: mapping the real
 * [StudentIdentity] aggregate (bank / APAAR / dayboarder / credentials decrypted rows)
 * into what the stateless profile screen renders.
 */
@OptIn(ExperimentalTestApi::class)
class M3EProfileBridgeMappingUiTest {

    @BeforeTest
    fun setup() {
        UserStore.clear()
    }

    @AfterTest
    fun tearDown() {
        UserStore.clear()
    }

    @Test
    fun testRealIdentityFragmentsAreMappedIntoProfileCards() = runComposeUiTest {
        UserStore.merge(
            StudentIdentity(
                regNo = "21BCE1001",
                name = "Aditya Sharma",
                campus = "Vellore Campus",
                bank = BankInfo(
                    name = "SBI",
                    fields = listOf(KeyValueRow(label = "Account Number", value = "1234567890"))
                ),
                apaar = ApaarInfo(
                    hasApaar = true,
                    fields = listOf(KeyValueRow(label = "APAAR ID", value = "APAAR123"))
                ),
                dayboarder = DayboarderInfo(
                    isDayboarder = true,
                    fields = listOf(KeyValueRow(label = "Campus", value = "Vellore"))
                ),
                credentials = listOf(
                    AccountCredential(account = "VTOP", username = "21BCE1001", password = "secret")
                )
            ),
            IdentitySource.ME
        )

        setContent { M3EProfileScreen() }

        // Each string is derived by the bridge from a real model field — not a placeholder.
        onNodeWithText("SBI (1234567890)").assertExists()
        onNodeWithText("APAAR123").assertExists()
        onNodeWithText("Day Boarder (Vellore)").assertExists()
        onNodeWithText("Aditya Sharma").assertExists()
    }
}
