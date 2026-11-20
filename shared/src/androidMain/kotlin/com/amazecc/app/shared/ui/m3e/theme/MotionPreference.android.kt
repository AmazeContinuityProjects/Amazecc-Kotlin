package com.amazecc.app.shared.ui.m3e.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's accessibility "Remove animations" setting maps to the two global
 * animation scale values. Either being 0 means the user asked for no motion.
 */
@Composable
actual fun platformPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val resolver = context.contentResolver
            val animatorScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val transitionScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            animatorScale == 0f || transitionScale == 0f
        }.getOrDefault(false)
    }
}
