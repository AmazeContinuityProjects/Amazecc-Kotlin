package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import kotlinx.datetime.Clock

/**
 * Kotlin port of the web `src/lib/signInReturn.ts` "sign in, then come back here" contract (D-073).
 *
 * A signed-out student cannot use the server-backed social surfaces. Instead of a dead-end
 * error, the caller parks the surface it came from here, opens the login form, and the
 * login-success hook calls [completeSignIn] to finish the trip. The note is process-scoped
 * (a native app has no browser tab), expires after 30 minutes, and is allowlisted to the
 * two screens that actually issue sign-in redirects — never an arbitrary destination.
 */
object SignInReturn {
    private const val MAX_AGE_MS = 30L * 60 * 1000

    private data class Note(val screen: Screen, val at: Long)

    /** Only these surfaces may ask for a sign-in-and-return round trip. */
    private val allowed = setOf(Screen.SOCIAL)

    private var note: Note? = null

    /**
     * Remember [returnTo] and open the login form.
     *
     * Unlike a cold-start `/?signin=1` (which the splash handles via
     * [AppState.EntryIntent]), this is an in-app redirect: go straight to the login form and
     * dismiss the guest landing so the student actually sees it. The parked note is what
     * makes the trip a round trip.
     */
    fun prepare(returnTo: Screen, openLogin: Boolean = true) {
        note = if (returnTo in allowed) {
            Note(returnTo, Clock.System.now().toEpochMilliseconds())
        } else {
            null
        }
        if (openLogin) {
            AppState.closeGuestLanding()
            AppState.navigateTo(Screen.LOGIN)
        }
    }

    /** The pending return screen without clearing it, or null when absent/expired/unsafe. */
    fun peek(): Screen? {
        val current = note ?: return null
        val age = Clock.System.now().toEpochMilliseconds() - current.at
        if (age !in 0..MAX_AGE_MS) {
            note = null
            return null
        }
        return current.screen.takeIf { it in allowed }
    }

    /** Read and clear the return screen. */
    fun take(): Screen? = peek().also { note = null }

    /**
     * Login-success hook. Returns the student to the parked surface when there is a valid
     * return trip, otherwise follows the normal [default] destination. Call this from
     * `M3ELoginScreen`'s three success branches instead of `AppState.navigateTo(target)`.
     */
    fun completeSignIn(default: Screen) {
        val returnTo = take()
        if (returnTo != null) {
            AppState.closeGuestLanding()
            AppState.clearPendingEntryIntent()
            AppState.navigateTo(returnTo)
        } else {
            AppState.navigateTo(default)
        }
    }

    fun clear() {
        note = null
    }
}
