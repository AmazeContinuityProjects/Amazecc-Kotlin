package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * D-085: the Compose counterpart of the web `clickable()` helper in
 * `src/lib/a11y.ts`.
 *
 * The web helper sets `role="button"`, `tabIndex=0` and activates on Enter or
 * Space (ignoring keys aimed at a focused child). Compose's `Modifier.clickable`
 * already supplies keyboard activation (Enter / numpad Enter / Space), focus
 * and the `OnClick` semantics action; what was missing at our call sites was
 * the *explicit* role and a readable label. This single modifier applies the
 * whole contract so clickable cards, rows and chips cannot drift again.
 *
 * @param onClickLabel announced name of the action, e.g. "Open Academics".
 */
@Composable
fun Modifier.clickableA11y(
    onClickLabel: String? = null,
    enabled: Boolean = true,
    mergeDescendants: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier {
    val rememberedSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: rememberedSource
    return this
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
        .focusable(enabled = enabled)
        .semantics(mergeDescendants = mergeDescendants) {
            role = Role.Button
            if (onClickLabel != null) contentDescription = onClickLabel
        }
}
