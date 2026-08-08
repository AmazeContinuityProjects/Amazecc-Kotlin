package com.amazecc.app.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    private val _cookies = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_COOKIES))
    val cookies: StateFlow<String?> = _cookies.asStateFlow()

    private val _csrf = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_CSRF))
    val csrf: StateFlow<String?> = _csrf.asStateFlow()

    private val _authorizedID = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_AUTHORIZED_ID))
    val authorizedID: StateFlow<String?> = _authorizedID.asStateFlow()

    private val _clubToken = MutableStateFlow<String?>(SettingsManager.getNullableString(SettingsManager.SESSION_CLUB_TOKEN))
    val clubToken: StateFlow<String?> = _clubToken.asStateFlow()

    // Settings
    val currentTheme = MutableStateFlow("system") // system, light, dark
    val currentAccent = MutableStateFlow("ocean") // ocean, forest, lavender, sunset

    val isLoggedIn: Boolean
        get() = _cookies.value != null && _csrf.value != null && _authorizedID.value != null

    fun saveSession(cookies: String, csrf: String, authorizedID: String, clubToken: String?) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
        SettingsManager.setString(SettingsManager.SESSION_COOKIES, cookies)
        SettingsManager.setString(SettingsManager.SESSION_CSRF, csrf)
        SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, authorizedID)
        if (clubToken != null) SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, clubToken)
    }

    fun saveInMemorySession(cookies: String, csrf: String, authorizedID: String, clubToken: String? = null) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
    }

    fun saveEventHubSession(jsessionid: String) {
        _clubToken.value = jsessionid
        SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, jsessionid)
    }

    fun clearSession() {
        _cookies.value = null
        _csrf.value = null
        _authorizedID.value = null
        _clubToken.value = null
        SettingsManager.remove(SettingsManager.SESSION_COOKIES)
        SettingsManager.remove(SettingsManager.SESSION_CSRF)
        SettingsManager.remove(SettingsManager.SESSION_AUTHORIZED_ID)
        SettingsManager.remove(SettingsManager.SESSION_CLUB_TOKEN)
    }
}

