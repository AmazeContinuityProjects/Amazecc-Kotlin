package com.amazecc.app.shared.ui.m3e.theme

/**
 * Web-parity timings for short feedback surfaces (D-087 / D-089).
 * Centralised so the sync sheet and the App tab transition cannot drift again.
 */
object M3EFeedbackDurations {
    /** Web `SyncNotification.tsx:389` — "Copied" toast lifetime. */
    const val COPY_TOAST_MS: Long = 2_000L

    /** Web `SyncNotification.tsx:322` — clean sync closes the sheet after 1.5 s. */
    const val SYNC_SUCCESS_CLOSE_MS: Long = 1_500L

    /** framer-motion `AnimatePresence` crossfade/scale for tab switches. */
    const val TAB_TRANSITION_MS: Int = 180
}
