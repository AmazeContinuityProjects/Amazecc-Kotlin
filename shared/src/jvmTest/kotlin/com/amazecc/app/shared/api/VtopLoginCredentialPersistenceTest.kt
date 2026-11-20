package com.amazecc.app.shared.api

import com.amazecc.app.shared.model.LoginResponse
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.vtop.VtopSession
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-109 regression: a successful VTOP credential sign-in must leave
 * [SettingsManager.getCredentials] non-null, regardless of which UI path called
 * [AmazeClient.login]. This is the value the sync gate reads to re-authenticate;
 * when it was null every module reported "Your VTOP session has expired".
 *
 * These tests drive the contract entirely offline through the `loginResponseOverride`
 * test seam — no network, no WebView, no captcha.
 */
class VtopLoginCredentialPersistenceTest {

    private var previousSource: VtopSource = VtopSource.LOCAL

    @BeforeTest
    fun setUp() {
        previousSource = AmazeClient.vtopSource
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.REMOTE)
        AmazeClient.loginResponseOverride = null
        AmazeClient.sessionLoginOverride = null
        VtopSession.clear()
        clearCredentials()
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.loginResponseOverride = null
        AmazeClient.sessionLoginOverride = null
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(previousSource)
        VtopSession.clear()
        clearCredentials()
    }

    private fun clearCredentials() {
        SettingsManager.remove(SettingsManager.KEY_USERNAME)
        SettingsManager.remove(SettingsManager.KEY_PASSWORD)
    }

    private fun successResponse() = LoginResponse(
        success = true,
        message = "Login successful!",
        cookies = "JSESSIONID=fresh",
        csrf = "fresh_csrf",
        authorizedID = "21BCE1234",
    )

    private fun failureResponse() = LoginResponse(
        success = false,
        message = "Invalid credentials",
        error = "invalid_credentials",
    )

    @Test
    fun successfulLoginPersistsUsernameAndPassword() = runBlocking {
        assertNull(SettingsManager.getCredentials(), "Precondition: no credentials stored")

        AmazeClient.loginResponseOverride = { _, _ -> successResponse() }
        val response = AmazeClient.login("21BCE1234", "s3cret")

        assertTrue(response.success)
        assertEquals(
            Pair("21BCE1234", "s3cret"),
            SettingsManager.getCredentials(),
            "A successful login must persist exactly the credentials that were used",
        )
    }

    @Test
    fun credentialsAreStoredExactlyAsSuppliedByCaller() = runBlocking {
        // The M3E login screen uppercases the registration number before calling login().
        AmazeClient.loginResponseOverride = { _, _ -> successResponse() }
        AmazeClient.login("21BCE1234", " Pa55w0rd ")

        assertEquals(
            Pair("21BCE1234", " Pa55w0rd "),
            SettingsManager.getCredentials(),
            "Credentials are stored exactly as the login call supplied them (password not trimmed)",
        )
    }

    @Test
    fun failedLoginDoesNotPersistCredentials() = runBlocking {
        assertNull(SettingsManager.getCredentials(), "Precondition: no credentials stored")

        AmazeClient.loginResponseOverride = { _, _ -> failureResponse() }
        val response = AmazeClient.login("21BCE1234", "wrong")

        assertFalse(response.success)
        assertNull(
            SettingsManager.getCredentials(),
            "A failed login must never leave usable credentials behind",
        )
    }

    @Test
    fun failedLoginDoesNotOverwriteExistingCredentials() = runBlocking {
        SettingsManager.saveCredentials("21BCE0001", "original")
        AmazeClient.loginResponseOverride = { _, _ -> failureResponse() }

        AmazeClient.login("21BCE9999", "replacement")

        assertEquals(
            Pair("21BCE0001", "original"),
            SettingsManager.getCredentials(),
            "A failed re-auth must not clobber a previously saved successful sign-in",
        )
    }

    @Test
    fun successFromEitherVtopSourcePersistsCredentials() = runBlocking {
        AmazeClient.loginResponseOverride = { _, _ -> successResponse() }

        AmazeClient.setVtopSource(VtopSource.REMOTE)
        AmazeClient.login("21BCE1234", "remote_pw")
        assertEquals(Pair("21BCE1234", "remote_pw"), SettingsManager.getCredentials())

        clearCredentials()
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        // The override bypasses the WebView but the persistence step must still run for LOCAL.
        AmazeClient.login("21BCE1234", "local_pw")
        assertEquals(Pair("21BCE1234", "local_pw"), SettingsManager.getCredentials())
    }

    @Test
    fun mockAndOfflineCacheLoginsDoNotPersistCredentials() = runBlocking {
        AmazeClient.loginResponseOverride = { _, _ -> successResponse() }

        AmazeClient.setUseMockData(true)
        AmazeClient.login("21BCE1234", "demo_pw")
        assertNull(SettingsManager.getCredentials(), "Demo/mock login is not a real credential sign-in")

        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)
        AmazeClient.login("21BCE1234", "offline_pw")
        assertNull(SettingsManager.getCredentials(), "Offline-cache login is not a real credential sign-in")
    }

    @Test
    fun persistedCredentialsSatisfyTheSyncAuthGate() = runBlocking {
        // Reproduces the reported symptom end-to-end: sign in, then run a sweep that forces a
        // fresh session. refreshSession() reads getCredentials(); if login() failed to persist,
        // it returns false immediately and AppState marks every module "session expired".
        AmazeClient.loginResponseOverride = { _, _ -> successResponse() }
        val loginResult = AmazeClient.login("21BCE1234", "gate_pw")
        assertTrue(loginResult.success)
        assertNotNull(SettingsManager.getCredentials(), "Sign-in must leave credentials for the gate")

        // Ensure no unrelated seam interferes; the gate must reach refreshSession -> login().
        AmazeClient.sessionLoginOverride = null
        val authenticated = AmazeClient.ensureSession(forceFresh = true)

        assertTrue(
            authenticated,
            "A successful sign-in must let the forced-fresh sync gate authenticate offline",
        )
    }
}
