package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/** Web `SWIPE_DISMISS_PX` parity: a downward grabber drag past 120 dp dismisses. */
private val SHEET_SWIPE_DISMISS = 120.dp

/**
 * D-058: one Compose sheet contract matching `shared/BottomSheet.tsx`.
 *
 * Every dismissal route funnels through the same guard:
 *  - X button (labelled "Dismiss sheet")
 *  - swipe the grabber down past 120 dp
 *  - backdrop tap
 *  - Escape (routes through [onSwipeDown], so a sheet that parks on outward
 *    gestures still parks on Escape — only X means "cancel")
 *  - system back via `DialogProperties.dismissOnBackPress`
 *
 * [dismissable] = false disables all five; the caller is expected to supply its
 * own explicit confirm/action control in [content]. When [overlayId] names an
 * entry in AppState's LIFO overlay stack, outward gestures only act while this
 * sheet is the top overlay, so nested sheets never dismiss together.
 *
 * LIFO registration itself stays with the caller (App.kt already pushes the
 * sync and command-palette ids): this primitive does not mutate the stack.
 */
@Composable
fun M3EDismissibleSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissable: Boolean = true,
    overlayId: String? = null,
    showClose: Boolean = true,
    onBackdropClick: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSystemBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isOpen) return

    val colors = M3ETheme.colors
    var dragY by remember { mutableFloatStateOf(0f) }

    fun isTopOverlay(): Boolean =
        overlayId == null || AppState.overlayStack.value.lastOrNull() == overlayId

    fun requestDismissal(handler: (() -> Unit)?) {
        if (!dismissable || !isTopOverlay()) return
        (handler ?: onDismiss)()
    }

    // Web BottomSheet: Escape uses the swipe handler (park), not the X (cancel).
    val escapeHandler = onSwipeDown ?: onDismiss

    Dialog(
        onDismissRequest = { requestDismissal(onSystemBack) },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = false, // backdrop is owned below so the guard applies
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickableA11y(
                    onClickLabel = "Dismiss sheet",
                    mergeDescendants = false // keep the sheet's own a11y tree readable
                ) { requestDismissal(onBackdropClick) }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        requestDismissal(escapeHandler)
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.surfaceContainerLow)
                    .graphicsLayer { translationY = dragY }
            ) {
                // Grabber owns the drag so vertical scrolling in content stays native.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                        .pointerInput(dismissable, onSwipeDown) {
                            if (!dismissable) return@pointerInput
                            var traveledY = 0f
                            val threshold = SHEET_SWIPE_DISMISS.toPx()
                            detectVerticalDragGestures(
                                onDragStart = { traveledY = 0f },
                                onDragEnd = {
                                    val dy = traveledY
                                    traveledY = 0f
                                    dragY = 0f
                                    if (dy >= threshold) requestDismissal(onSwipeDown)
                                },
                                onDragCancel = {
                                    traveledY = 0f
                                    dragY = 0f
                                }
                            ) { _, dragAmount ->
                                // detectVerticalDragGestures supplies a Float (vertical delta).
                                traveledY = (traveledY + dragAmount).coerceAtLeast(0f)
                                dragY = traveledY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.outlineVariant)
                    )
                }

                if (title != null || (showClose && dismissable)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title.orEmpty(),
                            color = colors.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (showClose && dismissable) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerHighest)
                                    .clickableA11y(onClickLabel = "Dismiss sheet") {
                                        requestDismissal(null)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss sheet",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                content()
            }
        }
    }
}
