package com.amazecc.app.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

object SessionManager {
    /**
     * Stand-in for the cookie string when the session is held on-device.
     *
     * In `VtopSource.LOCAL` the real `JSESSIONID` never leaves the WebView's `CookieManager`,
     * so there is nothing to store here. The marker keeps `isLoggedIn` and the existing login
     * screen checks working without a second session shape.
     */
    const val LOCAL_SESSION_MARKER = "vtop-local"

    // Amaze ID (D-011). These are the OAuth access token and the compact DPoP proof header used
    // by the web client for the guarded prefixes (cabshare/social/profile/qbank upload/...).
    // Full OIDC interactive sign-in is deliberately out of scope; this stores tokens supplied by
    // an embedding host (e.g. the auth callback) and attaches them to outgoing requests.
    private const val KEY_AMAZE_ACCESS_TOKEN = "amaze_id_access_token"
    private const val KEY_AMAZE_DPOP_HEADER = "amaze_id_dpop_header"
    /** Non-null when the access token is DPoP-bound (OAuth `confirmation` claim). */
    private const val KEY_AMAZE_CONFIRMATION = "amaze_id_confirmation"

    private val _cookies = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_COOKIES))
    val cookies: StateFlow<String?> = _cookies.asStateFlow()

    private val _csrf = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_CSRF))
    val csrf: StateFlow<String?> = _csrf.asStateFlow()

    private val _authorizedID = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_AUTHORIZED_ID))
    val authorizedID: StateFlow<String?> = _authorizedID.asStateFlow()

    private val _clubToken = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_CLUB_TOKEN))
    val clubToken: StateFlow<String?> = _clubToken.asStateFlow()

    private val _sessionCreatedAt = MutableStateFlow(SettingsManager.getLong(SettingsManager.SESSION_CREATED_AT))
    val sessionCreatedAt: StateFlow<Long> = _sessionCreatedAt.asStateFlow()

    private val _amazeAccessToken = MutableStateFlow(SettingsManager.getNullableString(KEY_AMAZE_ACCESS_TOKEN))
    val amazeAccessToken: StateFlow<String?> = _amazeAccessToken.asStateFlow()

    private val _amazeDpopHeader = MutableStateFlow(SettingsManager.getNullableString(KEY_AMAZE_DPOP_HEADER))
    val amazeDpopHeader: StateFlow<String?> = _amazeDpopHeader.asStateFlow()

    /** Present only for DPoP-bound tokens; drives `Authorization: DPoP` (D-011). */
    private val _amazeTokenConfirmation = MutableStateFlow(SettingsManager.getNullableString(KEY_AMAZE_CONFIRMATION))
    val amazeTokenConfirmation: StateFlow<String?> = _amazeTokenConfirmation.asStateFlow()

    /** Raised when a guarded endpoint answers 401; UI can surface "sign in with Amaze ID". */
    private val _amazeAuthRequired = MutableStateFlow(false)
    val amazeAuthRequired: StateFlow<Boolean> = _amazeAuthRequired.asStateFlow()

    val hasAmazeIdAuth: Boolean
        get() = !_amazeAccessToken.value.isNullOrBlank()

    // Settings
    val currentTheme = MutableStateFlow("system") // system, light, dark
    val currentAccent = MutableStateFlow("ocean") // ocean, forest, lavender, sunset

    val isLoggedIn: Boolean
        get() = _cookies.value != null && _csrf.value != null && _authorizedID.value != null

    /** True when the active session is held in the on-device VTOP engine rather than a cookie string. */
    val isLocalSession: Boolean
        get() = _cookies.value == LOCAL_SESSION_MARKER

    /** True when there is a real cookie string to send to the server. */
    val hasRemoteCookies: Boolean
        get() = !_cookies.value.isNullOrBlank() && !isLocalSession

    fun saveSession(cookies: String, csrf: String, authorizedID: String, clubToken: String?) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
        SettingsManager.setString(SettingsManager.SESSION_COOKIES, cookies)
        SettingsManager.setString(SettingsManager.SESSION_CSRF, csrf)
        SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, authorizedID)
        if (clubToken != null) SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, clubToken)
        markSessionFresh()
    }

    fun saveInMemorySession(cookies: String, csrf: String, authorizedID: String, clubToken: String? = null) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
        markSessionFresh()
    }

    fun saveEventHubSession(jsessionid: String) {
        _clubToken.value = jsessionid
        SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, jsessionid)
    }

    /**
     * Persist an Amaze ID access token (and optional DPoP proof header) for guarded endpoints.
     * A non-null [confirmation] marks the token as DPoP-bound — the Authorization scheme must
     * then be `DPoP`, exactly like `amazeId.ts` checks `accessToken.confirmation !== null`.
     */
    fun saveAmazeIdSession(
        accessToken: String,
        dpopHeader: String? = null,
        confirmation: String? = null
    ) {
        _amazeAccessToken.value = accessToken.takeIf { it.isNotBlank() }
        _amazeDpopHeader.value = dpopHeader?.takeIf { it.isNotBlank() }
        _amazeTokenConfirmation.value = confirmation?.takeIf { it.isNotBlank() }
        if (_amazeAccessToken.value != null) SettingsManager.setString(KEY_AMAZE_ACCESS_TOKEN, accessToken)
        else SettingsManager.remove(KEY_AMAZE_ACCESS_TOKEN)
        if (_amazeDpopHeader.value != null) SettingsManager.setString(KEY_AMAZE_DPOP_HEADER, dpopHeader!!)
        else SettingsManager.remove(KEY_AMAZE_DPOP_HEADER)
        if (_amazeTokenConfirmation.value != null) SettingsManager.setString(KEY_AMAZE_CONFIRMATION, confirmation!!)
        else SettingsManager.remove(KEY_AMAZE_CONFIRMATION)
        _amazeAuthRequired.value = false
    }

    fun clearAmazeIdSession() {
        _amazeAccessToken.value = null
        _amazeDpopHeader.value = null
        _amazeTokenConfirmation.value = null
        _amazeAuthRequired.value = false
        SettingsManager.remove(KEY_AMAZE_ACCESS_TOKEN)
        SettingsManager.remove(KEY_AMAZE_DPOP_HEADER)
        SettingsManager.remove(KEY_AMAZE_CONFIRMATION)
    }

    /** Explicit "sign in with Amaze ID" signal, raised by guarded 401 responses. */
    fun markAmazeAuthRequired() { _amazeAuthRequired.value = true }
    fun clearAmazeAuthRequired() { _amazeAuthRequired.value = false }

    fun markSessionFresh() {
        _sessionCreatedAt.value = Clock.System.now().toEpochMilliseconds()
        SettingsManager.setLong(SettingsManager.SESSION_CREATED_AT, _sessionCreatedAt.value)
    }

    /** True when the session is older than [maxAgeMs] or its age is unknown (legacy install). */
    fun isSessionStale(maxAgeMs: Long): Boolean {
        // The persisted timestamp is authoritative so a stale session written by another
        // process (or restored on cold start) cannot be masked by an in-memory "fresh" value.
        val created = SettingsManager.getLong(SettingsManager.SESSION_CREATED_AT)
            .takeIf { it > 0L } ?: _sessionCreatedAt.value
        if (created <= 0L) return true
        return Clock.System.now().toEpochMilliseconds() - created > maxAgeMs
    }

    fun clearSession() {
        _cookies.value = null
        _csrf.value = null
        _authorizedID.value = null
        _clubToken.value = null
        _sessionCreatedAt.value = 0L
        SettingsManager.remove(SettingsManager.SESSION_COOKIES)
        SettingsManager.remove(SettingsManager.SESSION_CSRF)
        SettingsManager.remove(SettingsManager.SESSION_AUTHORIZED_ID)
        SettingsManager.remove(SettingsManager.SESSION_CLUB_TOKEN)
        SettingsManager.remove(SettingsManager.SESSION_CREATED_AT)
        // Amaze ID is a separate identity: a VTOP logout must not silently sign it out.
    }
}

