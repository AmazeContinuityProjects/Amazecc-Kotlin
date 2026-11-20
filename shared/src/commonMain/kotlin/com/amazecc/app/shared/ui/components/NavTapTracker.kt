package com.amazecc.app.shared.ui.components

import kotlinx.datetime.Clock

/**
 * D-055: pure-Kotlin port of the web `createTapTracker()` (`lib/sectionHome.ts`).
 *
 * A tap is never delayed: the first tap navigates immediately. A second tap on
 * the same tab inside [DOUBLE_TAP_WINDOW_MS] means "take me to this section's
 * root". The pair is consumed, so a triple tap is a pair followed by a single.
 */
const val DOUBLE_TAP_WINDOW_MS = 350L

sealed interface NavTapOutcome {
    /** Plain tap: do the tab's normal thing. */
    data object Single : NavTapOutcome

    /**
     * Double tap: go to the section root.
     *
     * [collapseFirstTap] is true when the first tap had to navigate into this
     * section (it was not already active). That first navigation was written to
     * history as its own screen, so the root move must fold into it rather than
     * push a second entry, or Back walks through a screen the user never chose.
     */
    data class GoRoot(val collapseFirstTap: Boolean) : NavTapOutcome
}

class NavTapTracker(
    private val windowMs: Long = DOUBLE_TAP_WINDOW_MS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private data class LastTap(val at: Long, val wasActive: Boolean)

    private val last = mutableMapOf<String, LastTap>()

    fun tap(id: String, wasActive: Boolean, nowMs: Long = clock()): NavTapOutcome {
        val prev = last[id]
        if (prev != null && nowMs - prev.at <= windowMs) {
            // Consume the pair so the next tap starts a fresh window.
            last.remove(id)
            return NavTapOutcome.GoRoot(collapseFirstTap = !prev.wasActive)
        }
        last[id] = LastTap(at = nowMs, wasActive = wasActive)
        return NavTapOutcome.Single
    }

    fun reset(id: String) {
        last.remove(id)
    }

    fun clear() = last.clear()
}
