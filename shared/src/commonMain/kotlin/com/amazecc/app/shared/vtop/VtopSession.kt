package com.amazecc.app.shared.vtop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mutable VTOP session tokens.
 *
 * The `JSESSIONID` cookie is deliberately absent: when running [VtopSource.LOCAL] it lives in
 * the WebView's own `CookieManager` and is never exposed to Kotlin. We only hold the values
 * that VTOP embeds in the rendered DOM and expects back on subsequent form posts.
 */
object VtopSession {
    const val BASE_URL = "https://vtopcc.vit.ac.in/vtop"

    const val PATH_ROOT = ""
    const val PATH_LOGIN = "/login"
    const val PATH_CONTENT = "/content"
    const val PATH_NEW_CAPTCHA = "/vtop/get/new/captcha"

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

    val hasSession: Boolean
        get() = !_csrf.value.isNullOrBlank() && !_authorizedID.value.isNullOrBlank()

    fun save(csrf: String, authorizedID: String, authorizedIDX: String) {
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _authorizedIDX.value = authorizedIDX
        _winImage.value = null
    }

    fun saveContentTokens(csrf: String, authorizedIDX: String, winImage: String?) {
        _csrf.value = csrf
        _authorizedIDX.value = authorizedIDX
        _winImage.value = winImage
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
        val id = _authorizedID.value ?: return null
        val token = _csrf.value ?: return null
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
    }
}
