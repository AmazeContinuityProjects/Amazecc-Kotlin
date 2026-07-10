package com.amazecc.app.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    private val _cookies = MutableStateFlow<String?>(null)
    val cookies: StateFlow<String?> = _cookies.asStateFlow()

    private val _csrf = MutableStateFlow<String?>(null)
    val csrf: StateFlow<String?> = _csrf.asStateFlow()

    private val _authorizedID = MutableStateFlow<String?>(null)
    val authorizedID: StateFlow<String?> = _authorizedID.asStateFlow()

    private val _clubToken = MutableStateFlow<String?>(null)
    val clubToken: StateFlow<String?> = _clubToken.asStateFlow()

    // Settings
    val currentTheme = MutableStateFlow("midnight") // light, dark, midnight
    val currentAccent = MutableStateFlow("ocean") // ocean, forest, lavender, sunset

    val isLoggedIn: Boolean
        get() = _cookies.value != null && _csrf.value != null && _authorizedID.value != null

    fun saveSession(cookies: String, csrf: String, authorizedID: String, clubToken: String?) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
    }

    fun clearSession() {
        _cookies.value = null
        _csrf.value = null
        _authorizedID.value = null
        _clubToken.value = null
    }
}
