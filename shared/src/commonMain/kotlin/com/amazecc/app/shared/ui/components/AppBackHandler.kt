package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable

/**
 * Platform-safe back handler.
 *
 * Android: delegates to [androidx.compose.ui.backhandler.BackHandler] so the system back
 * button and predictive back gestures are handled with the correct animation.
 *
 * iOS/Desktop: no-op — there is no system back mechanism, and the CMP implementation
 * would crash without a NavigationEventDispatcherOwner being provided.
 */
@Composable
expect fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * D-070 contract (web parity): overlays dismiss via back on Android. This flag says
 * whether the root `App` surface's Escape-key handler (in `App.kt`) may dismiss the
 * top overlay on the platform. Desktop/iOS have no system back mechanism, so Escape
 * deliberately does not dismiss modal sheets there — the honest, documented contract.
 *
 * Android: `true`. iOS/Desktop: `false` (see the "honest contract" note in the drift register).
 */
expect val AppBackHandlerDismissesOverlaysOnEscape: Boolean
