package com.amazecc.app.shared.vtop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Mutable VTOP session tokens.
 *
 * The `JSESSIONID` cookie is deliberately absent: when running [VtopSource.LOCAL] it lives in
 * the WebView's own `CookieManager` and is never exposed to Kotlin. We only hold the values
 * that VTOP embeds in the rendered DOM and expects back on subsequent form posts.
 *
 * VTOP sessions expire after 10 minutes on the server (600,000 ms). We track the creation/refresh
 * timestamp and proactively refresh at 9.5 minutes (570,000 ms) before operations fail.
 */
object VtopSession {
    const val BASE_URL = "https://vtopcc.vit.ac.in/vtop"

    const val PATH_ROOT = ""
    const val PATH_LOGIN = "/login"
    const val PATH_CONTENT = "/content"
    const val PATH_NEW_CAPTCHA = "/vtop/get/new/captcha"

    /** VTOP session lifetime is exactly 10 minutes (600,000 ms) */
    const val SESSION_MAX_AGE_MS: Long = 10 * 60 * 1000L
    /** Refresh threshold: refresh when session is older than 9.5 minutes */
    const val SESSION_REFRESH_THRESHOLD_MS: Long = 9 * 60 * 1000L + 30 * 1000L

    private val _csrf = MutableStateFlow<String?>(null)
    val csrf: StateFlow<String?> = _csrf.asStateFlow()

    private val _authorizedID = MutableStateFlow<String?>(null)
    val authorizedID: StateFlow<String?> = _authorizedID.asStateFlow()

    /** `#authorizedIDX` from `/vtop/content`. The `verifyMenu` family needs it verbatim. */
    private val _authorizedIDX = MutableStateFlow<String?>(null)
    val authorizedIDX: StateFlow<String?> = _authorizedIDX.asStateFlow()

    /** `#winImage` — required by the `verifyMenu` endpoint family. */
    private val _winImage = MutableStateFlow<String?>(null)
    val winImage: StateFlow<String?> = _winImage.asStateFlow()

    private val _semesterSubId = MutableStateFlow<String?>(null)
    val semesterSubId: StateFlow<String?> = _semesterSubId.asStateFlow()

    private val _semesterName = MutableStateFlow<String?>(null)
    val semesterName: StateFlow<String?> = _semesterName.asStateFlow()

    private val _availableSemesters = MutableStateFlow<Map<String, String>>(emptyMap())
    val availableSemesters: StateFlow<Map<String, String>> = _availableSemesters.asStateFlow()

    private val _sessionTimestamp = MutableStateFlow<Long>(0L)
    val sessionTimestamp: StateFlow<Long> = _sessionTimestamp.asStateFlow()

    val hasSession: Boolean
        get() = !_csrf.value.isNullOrBlank() && !_authorizedID.value.isNullOrBlank()

    val sessionAgeMs: Long
        get() {
            val ts = _sessionTimestamp.value
            return if (ts == 0L) Long.MAX_VALUE else (Clock.System.now().toEpochMilliseconds() - ts).coerceAtLeast(0L)
        }

    val isExpired: Boolean
        get() = !hasSession || (sessionAgeMs >= SESSION_REFRESH_THRESHOLD_MS)

    val isValid: Boolean
        get() = hasSession && !isExpired

    fun markActive(nowMs: Long = Clock.System.now().toEpochMilliseconds()) {
        _sessionTimestamp.value = nowMs
    }

    fun save(csrf: String, authorizedID: String, authorizedIDX: String, winImage: String? = null) {
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _authorizedIDX.value = authorizedIDX
        _winImage.value = winImage
        _sessionTimestamp.value = Clock.System.now().toEpochMilliseconds()
    }

    fun saveContentTokens(csrf: String, authorizedIDX: String, winImage: String?) {
        _csrf.value = csrf
        _authorizedIDX.value = authorizedIDX
        _winImage.value = winImage
        _sessionTimestamp.value = Clock.System.now().toEpochMilliseconds()
    }

    fun saveSemesters(semesters: Map<String, String>) {
        _availableSemesters.value = semesters
        val first = semesters.entries.firstOrNull()
        if (first != null) {
            _semesterName.value = first.key
            _semesterSubId.value = first.value
        }
    }

    fun selectSemester(name: String) {
        val id = _availableSemesters.value[name] ?: return
        _semesterName.value = name
        _semesterSubId.value = id
    }

    /**
     * The canonical parameter string shared by every `verifyMenu` endpoint.
     *
     * `nocache` is a millisecond epoch so VTOP does not serve a cached fragment.
     */
    fun verifyMenuParams(nowMs: Long): String? {
        val id = _authorizedID.value?.takeIf { it.isNotBlank() } ?: return null
        val token = _csrf.value?.takeIf { it.isNotBlank() } ?: return null
        return "verifyMenu=true&authorizedID=$id&_csrf=$token&nocache=$nowMs"
    }

    fun clear() {
        _csrf.value = null
        _authorizedID.value = null
        _authorizedIDX.value = null
        _winImage.value = null
        _semesterSubId.value = null
        _semesterName.value = null
        _availableSemesters.value = emptyMap()
        _sessionTimestamp.value = 0L
    }
}
