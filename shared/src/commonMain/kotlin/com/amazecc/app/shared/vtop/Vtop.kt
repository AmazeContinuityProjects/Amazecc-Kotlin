package com.amazecc.app.shared.vtop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The captcha VTOP is currently showing. */
data class CaptchaChallenge(
    val base64: String?,
    val isRecaptcha: Boolean
)

/** What the user (or the solver) decided about a [CaptchaChallenge]. */
sealed interface CaptchaAnswer {
    /** An image-captcha transcription, or a raw reCAPTCHA token. */
    data class Text(val text: String) : CaptchaAnswer

    data object Cancelled : CaptchaAnswer
}

fun interface CaptchaHandler {
    suspend fun onCaptcha(challenge: CaptchaChallenge): CaptchaAnswer
}

/** Progress of the local login flow, for the UI and the diagnostics screen. */
sealed interface VtopAuthStage {
    data object Idle : VtopAuthStage
    data object Connecting : VtopAuthStage
    data object Prelogin : VtopAuthStage
    data object LoadingLogin : VtopAuthStage
    data object AwaitingCaptcha : VtopAuthStage
    data object Submitting : VtopAuthStage
    data object LoadingDashboard : VtopAuthStage
    data object FetchingSemesters : VtopAuthStage
    data class Done(val message: String) : VtopAuthStage
    data class Failed(val message: String) : VtopAuthStage
}

/**
 * Drives the VTOP login flow on-device.
 *
 * This exists because the app's server cannot reach VTOP: VTOP only accepts connections from
 * Indian IP space and the server runs in Singapore. See
 * `docs/sep-29-2026/vtop-local-integration-plan.md`.
 */
object Vtop {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var engine: VtopEngine? = null

    private val _stage = MutableStateFlow<VtopAuthStage>(VtopAuthStage.Idle)
    val stage: StateFlow<VtopAuthStage> = _stage.asStateFlow()

    /**
     * The captcha currently awaiting an answer, or null.
     *
     * When no [CaptchaHandler] is supplied the flow parks here and the UI shows a dialog,
     * then calls [submitCaptcha] or [cancelCaptcha]. This keeps the login coroutine and the
     * Compose UI decoupled.
     */
    private val _captchaRequest = MutableStateFlow<CaptchaChallenge?>(null)
    val captchaRequest: StateFlow<CaptchaChallenge?> = _captchaRequest.asStateFlow()

    private var captchaSignal: CompletableDeferred<CaptchaAnswer>? = null

    val isSupported: Boolean get() = vtopEngineSupported

    private fun engine(): VtopEngine = engine ?: VtopEngine().also { engine = it }

    /** The shared engine, for [VtopDataSource]. Exposed rather than duplicated. */
    fun engineInstance(): VtopEngine = engine()

    /** Answers the parked captcha prompt. Blank text is treated as a cancel. */
    fun submitCaptcha(text: String) {
        val signal = captchaSignal ?: return
        captchaSignal = null
        _captchaRequest.value = null
        if (text.isBlank()) signal.complete(CaptchaAnswer.Cancelled)
        else signal.complete(CaptchaAnswer.Text(text.trim()))
    }

    /** Abandons the parked captcha prompt, failing the login. */
    fun cancelCaptcha() {
        val signal = captchaSignal ?: return
        captchaSignal = null
        _captchaRequest.value = null
        signal.complete(CaptchaAnswer.Cancelled)
    }

    /** Awaits an answer from the UI. Used by [UiCaptchaHandler]. */
    suspend fun awaitUiCaptcha(challenge: CaptchaChallenge): CaptchaAnswer = resolveCaptcha(challenge, null)

    private suspend fun resolveCaptcha(
        challenge: CaptchaChallenge,
        handler: CaptchaHandler?
    ): CaptchaAnswer {
        if (handler != null) return handler.onCaptcha(challenge)
        val signal = CompletableDeferred<CaptchaAnswer>()
        captchaSignal = signal
        _captchaRequest.value = challenge
        return try {
            signal.await()
        } finally {
            captchaSignal = null
            _captchaRequest.value = null
        }
    }

    /**
     * Full login: root -> prelogin -> login page -> captcha -> submit -> dashboard -> semesters.
     *
     * Retries up to [maxCaptchaAttempts] times on a bad captcha, matching the reference
     * implementation which reloads `/login` to pick up a fresh image.
     */
    suspend fun login(
        username: String,
        password: String,
        captchaHandler: CaptchaHandler,
        maxCaptchaAttempts: Int = 3
    ): LoginResult {
        if (!vtopEngineSupported) {
            return LoginResult.Failure("Local VTOP access is not available on this platform")
        }
        if (username.isBlank() || password.isBlank()) {
            return LoginResult.InvalidCredentials
        }

        val e = engine()
        return try {
            performLogin(e, username, password, captchaHandler, maxCaptchaAttempts)
        } catch (t: Throwable) {
            _stage.value = VtopAuthStage.Failed(t.message ?: "Login failed")
            LoginResult.Failure(t.message ?: "Login failed")
        }
    }

    private suspend fun performLogin(
        e: VtopEngine,
        username: String,
        password: String,
        captchaHandler: CaptchaHandler,
        maxCaptchaAttempts: Int
    ): LoginResult {
        e.initialize()
        e.setUserAgent(UserAgentPool.current())

        _stage.value = VtopAuthStage.Connecting
        e.load(VtopSession.PATH_ROOT)

        when (awaitPageState(e)) {
            VtopPageState.HOME -> return completeSession(e)
            VtopPageState.LOGIN -> return runLoginLoop(e, username, password, captchaHandler, maxCaptchaAttempts)
            VtopPageState.LANDING -> {
                _stage.value = VtopAuthStage.Prelogin
                val prelogin = parseObject(e.evaluate(VtopScripts.PRELOGIN))
                if (prelogin["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    val reason = prelogin["error"]?.jsonPrimitive?.content
                    val suffix = if (reason.isNullOrBlank()) "" else ": $reason"
                    return LoginResult.Failure("VTOP prelogin handshake failed$suffix")
                }
                _stage.value = VtopAuthStage.LoadingLogin
                e.load(VtopSession.PATH_LOGIN)
                if (awaitPageState(e) != VtopPageState.LOGIN) {
                    return LoginResult.Failure("Could not reach the VTOP login form")
                }
                return runLoginLoop(e, username, password, captchaHandler, maxCaptchaAttempts)
            }
            else -> return LoginResult.Failure("Unexpected VTOP landing page")
        }
    }

    private suspend fun runLoginLoop(
        e: VtopEngine,
        username: String,
        password: String,
        captchaHandler: CaptchaHandler,
        attempts: Int
    ): LoginResult {
        var result: LoginResult = LoginResult.Unknown
        repeat(attempts.coerceAtLeast(1)) {
            val captcha = readCaptcha(e) ?: return LoginResult.Failure("Could not read the VTOP captcha")

            _stage.value = VtopAuthStage.AwaitingCaptcha
            when (val answer = resolveCaptcha(captcha, captchaHandler)) {
                is CaptchaAnswer.Cancelled -> return LoginResult.Failure("Login cancelled")
                is CaptchaAnswer.Text -> {
                    if (answer.text.isBlank()) {
                        return LoginResult.Failure("Login cancelled")
                    }
                    _stage.value = VtopAuthStage.Submitting
                    result = submit(e, username, password, answer.text)
                }
            }

            when (result) {
                is LoginResult.Success -> return completeSession(e)
                is LoginResult.InvalidCaptcha -> {
                    // Reload the login page to pick up a fresh captcha image.
                    _stage.value = VtopAuthStage.LoadingLogin
                    e.load(VtopSession.PATH_LOGIN)
                    awaitPageState(e)
                }
                is LoginResult.InvalidCredentials,
                is LoginResult.AccountLocked,
                is LoginResult.MaxAttempts,
                is LoginResult.Failure -> return result
                is LoginResult.Unknown -> return result
            }
        }
        return result
    }

    private suspend fun completeSession(e: VtopEngine): LoginResult {
        _stage.value = VtopAuthStage.LoadingDashboard
        e.load(VtopSession.PATH_CONTENT)

        val content = parseObject(e.evaluate(VtopScripts.EXTRACT_CONTENT))
        if (content["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            return LoginResult.Failure("Logged in but could not read the VTOP dashboard")
        }

        val csrf = content["csrf"]?.jsonPrimitive?.content.orEmpty()
        val authorizedIDX = content["authorizedIDX"]?.jsonPrimitive?.content.orEmpty()
        val winImage = content["winImage"]?.jsonPrimitive?.content
        if (csrf.isBlank() || authorizedIDX.isBlank()) {
            return LoginResult.Failure("VTOP dashboard did not expose a CSRF token")
        }
        VtopSession.saveContentTokens(csrf, authorizedIDX, winImage)

        // authorizedID is the student's login id; the dashboard exposes it in the form's
        // action/values. Fall back to the uppercase username, matching the server's behaviour
        // (AmazeCC-API src/app/api/login/route.ts:156-158).
        val authorizedID = content["authorizedID"]?.jsonPrimitive?.content
            ?: VtopSession.authorizedID.value
            ?: authorizedIDX
        VtopSession.save(csrf, authorizedID, authorizedIDX)

        _stage.value = VtopAuthStage.FetchingSemesters
        runCatching { fetchSemesters(e) }

        _stage.value = VtopAuthStage.Done("Login successful")
        return LoginResult.Success(csrf = csrf, authorizedID = authorizedID, authorizedIDX = authorizedIDX)
    }

    private suspend fun readCaptcha(e: VtopEngine): CaptchaChallenge? {
        val obj = parseObject(e.evaluate(VtopScripts.READ_CAPTCHA))
        val isRecaptcha = obj["isRecaptcha"]?.jsonPrimitive?.booleanOrNull ?: false
        if (isRecaptcha) {
            return CaptchaChallenge(base64 = null, isRecaptcha = true)
        }
        val base64 = obj["base64"]?.jsonPrimitive?.content
        return if (base64.isNullOrBlank()) null else CaptchaChallenge(base64 = base64, isRecaptcha = false)
    }

    private suspend fun submit(
        e: VtopEngine,
        username: String,
        password: String,
        captcha: String
    ): LoginResult {
        val obj = parseObject(e.evaluate(VtopScripts.submitLogin(username, password, captcha)))
        if (obj["authorized"]?.jsonPrimitive?.booleanOrNull == true) {
            return LoginResult.Success(
                csrf = VtopSession.csrf.value.orEmpty(),
                authorizedID = VtopSession.authorizedID.value.orEmpty(),
                authorizedIDX = VtopSession.authorizedIDX.value.orEmpty()
            )
        }
        return when (obj["error_code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) {
            1 -> LoginResult.InvalidCaptcha
            2 -> LoginResult.InvalidCredentials
            3 -> LoginResult.AccountLocked
            4 -> LoginResult.MaxAttempts
            else -> LoginResult.Unknown
        }
    }

    /**
     * Loads the semester list.
     *
     * A `not authorized` body is VTOP rejecting the User-Agent; the UA is rotated and the caller
     * can retry.
     */
    suspend fun fetchSemesters(e: VtopEngine = engine()): Map<String, String> {
        val obj = parseObject(e.evaluate(VtopScripts.FETCH_SEMESTERS))
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            if (obj["error"]?.jsonPrimitive?.content?.contains("not authorized") == true) {
                rotateUserAgent(e)
            }
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        val raw = obj["semesters"]?.jsonObject as? Map<String, kotlinx.serialization.json.JsonElement> ?: emptyMap()
        val semesters = raw.entries.associate { (name, value) ->
            name to value.jsonPrimitive.content
        }
        if (semesters.isNotEmpty()) VtopSession.saveSemesters(semesters)
        return semesters
    }

    /**
     * Re-hydrates [VtopSession] from a `JSESSIONID` the WebView is still holding, without asking
     * for credentials or solving another captcha.
     *
     * This exists because [VtopSession] is deliberately memory-only — the real cookie never
     * leaves the WebView's `CookieManager`, which *does* persist across process death. Without
     * this, every cold start reverts every module to "No VTOP session" even though the browser
     * session is still valid, and the only remedy is a full interactive login with a captcha.
     *
     * Returns true when a session was restored. Never prompts, so it is safe to call on startup.
     */
    suspend fun restoreSessionIfPossible(): Boolean {
        if (VtopSession.hasSession) return true
        if (!isSupported) return false

        val e = engine()
        // A stale JSESSIONID sends us straight back to the login form, which is the negative case.
        if (awaitPageState(e) != VtopPageState.HOME) {
            runCatching { e.load(VtopSession.PATH_CONTENT) }
            if (awaitPageState(e) != VtopPageState.HOME) return false
        }
        return completeSession(e) is LoginResult.Success
    }

    /** Swaps to the next User-Agent and reloads the login page. */
    suspend fun rotateUserAgent(e: VtopEngine = engine()): String {
        val next = UserAgentPool.rotate()
        e.setUserAgent(next)
        e.load(VtopSession.PATH_LOGIN)
        return next
    }

    suspend fun logout() {
        VtopSession.clear()
        UserAgentPool.reset()
        if (vtopEngineSupported) {
            runCatching {
                val e = engine()
                e.clearCookies()
                e.load(VtopSession.PATH_ROOT)
            }
        }
        _stage.value = VtopAuthStage.Idle
    }

    private suspend fun awaitPageState(e: VtopEngine): VtopPageState {
        val obj = parseObject(e.evaluate(VtopScripts.PAGE_STATE))
        val name = obj["state"]?.jsonPrimitive?.content ?: "UNKNOWN"
        return runCatching { VtopPageState.valueOf(name) }.getOrDefault(VtopPageState.UNKNOWN)
    }

    private fun parseObject(raw: String): JsonObject = try {
        json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}
