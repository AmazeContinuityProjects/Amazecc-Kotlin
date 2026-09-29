package com.amazecc.app.shared.vtop

/**
 * Outcome of a VTOP login attempt.
 *
 * Codes mirror the taxonomy used by the Flutter reference implementation
 * (`fkvit/lib/features/authentication/core/auth_service.dart:877-958`), where
 * `InvalidCaptcha` triggers an automatic retry with a fresh captcha and
 * `InvalidCredentials` / [AccountLocked] / [MaxAttempts] are hard stops.
 */
sealed interface LoginResult {
    data class Success(
        val csrf: String,
        val authorizedID: String,
        val authorizedIDX: String
    ) : LoginResult

    data object InvalidCaptcha : LoginResult

    data object InvalidCredentials : LoginResult

    data object AccountLocked : LoginResult

    data object MaxAttempts : LoginResult

    data object Unknown : LoginResult

    /** Transport / WebView level failure. [message] is safe to surface to the user. */
    data class Failure(val message: String) : LoginResult

    /** True when the failure is worth retrying with a new captcha image. */
    val retryable: Boolean
        get() = this is InvalidCaptcha || this is Unknown
}

/** Which page the WebView is currently displaying. */
enum class VtopPageState {
    UNKNOWN,
    BODY_NOT_READY,

    /** The `stdForm` landing page; needs the prelogin handshake before `/login` works. */
    LANDING,

    /** The login form is present. */
    LOGIN,

    /** Logged in: `#authorizedIDX` is present. */
    HOME
}
