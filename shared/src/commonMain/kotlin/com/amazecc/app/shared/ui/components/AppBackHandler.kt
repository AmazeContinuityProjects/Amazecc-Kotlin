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
