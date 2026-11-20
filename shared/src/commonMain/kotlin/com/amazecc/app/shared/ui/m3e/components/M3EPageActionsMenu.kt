package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * D-116 / D-124: one M3E page-actions menu, used wherever a header used to expose
 * two or more separate controls. The toggle is a real Material FAB at M3E
 * elevation level 3 (6 dp rest / 8 dp pressed, per m3-expressive-unified Part I
 * §5); the items are tonal surfaces with the project's hairline `outlineVariant`
 * border (DESIGN.md "tonal first" rule).
 *
 * D-124: the FAB no longer lives in a scrolling header. It is rendered by
 * [M3EPageActionsHost] at the page root, *after* the body content in a `Box`
 * overlay (so z-order puts it above every sibling), pinned bottom-end with an
 * inset that clears the floating nav bar. The menu expands upward from the FAB.
 * Use [M3EPageActionsHost] for pages; this primitive stays public/testable on
 * its own and paints a full-screen backdrop (tap to dismiss) behind the items.
 *
 * Platform note: Compose Multiplatform's commonMain (currently 1.12.2) does
 * **not** ship the Android-only experimental `FloatingActionButtonMenu` /
 * `ToggleFloatingActionButton` / `FloatingActionButtonMenuItem` shown in the
 * Android-only example. The primitive therefore composes the same pattern out
 * of the M3E APIs that *are*
 * available here — [FloatingActionButton], M3E motion tokens and Material
 * rounded icons — with an identical contract (one toggle, N labelled items,
 * collapse-and-invoke on selection). No API behaviour is faked: focus, roles,
 * keyboard activation and reduced motion come from the real Material/A11y
 * primitives.
 *
 * Strictly stateless with respect to app data: it receives action lambdas and
 * never reads AppState. The only local state is the menu's own open/closed flag.
 *
 * Renders nothing for 0 or 1 actions so single-action pages keep their existing
 * inline button unchanged.
 *
 * @param modifier applied to the full-screen overlay root (so the backdrop can
 *   cover the whole page). The host normally leaves this at the default.
 * @param fabModifier positions the FAB within the overlay; the host supplies the
 *   bottom-end padding that keeps it clear of the floating navigation bar.
 */
@Composable
fun M3EPageActionsMenu(
    actions: List<M3EPageAction>,
    modifier: Modifier = Modifier,
    fabModifier: Modifier = Modifier,
    collapsedLabel: String = "More actions",
    expandedLabel: String = "Close actions"
) {
    if (actions.size < 2) return

    // Self-provide the active palette exactly like M3EDismissibleSheet: several
    // call sites mount headers outside an AmazeM3ETheme wrapper.
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        var expanded by remember { mutableStateOf(false) }
        // M3E elevation level 3: 6 dp at rest, 8 dp when lifted (Part I §5).
        val fabElevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
            focusedElevation = 8.dp,
            hoveredElevation = 8.dp
        )

        // Read motion specs in a composable scope (the AnimatedContent spec
        // builder itself is not composable). Reduced motion resolves to snap().
        val effectsSpec: FiniteAnimationSpec<Float> = M3ETheme.motion.effectsDefault()
        val spatialSpec: FiniteAnimationSpec<Float> = M3ETheme.motion.spatialDefault()
        val itemEnter = fadeIn(animationSpec = effectsSpec) +
            scaleIn(initialScale = 0.92f, animationSpec = spatialSpec)
        val itemExit = fadeOut(animationSpec = effectsSpec) +
            scaleOut(targetScale = 0.92f, animationSpec = spatialSpec)

        Box(
            modifier = modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && expanded) {
                        expanded = false
                        true
                    } else {
                        false
                    }
                }
        ) {
            // D-124: tap-away backdrop. Drawn before the FAB/menu so the controls
            // stay above it and remain clickable; only the page body is covered.
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(M3EPageActionsMenuTestTags.BACKDROP)
                        .clickableA11y(onClickLabel = "Dismiss page actions") {
                            expanded = false
                        }
                )
            }

            // Bottom-end anchor owned by the host. Items are the first child so
            // the FAB (second child) paints on top; the whole column is bottom
            // aligned, so adding items pushes the menu *upward* from the FAB.
            Column(
                modifier = fabModifier
                    .align(Alignment.BottomEnd)
                    .testTag(M3EPageActionsMenuTestTags.TOGGLE_AREA),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                AnimatedVisibility(visible = expanded, enter = itemEnter, exit = itemExit) {
                    Box(modifier = Modifier.padding(bottom = MENU_GAP)) {
                        Column(
                            modifier = Modifier
                                .testTag(M3EPageActionsMenuTestTags.ITEMS)
                                .background(colors.surfaceContainerHigh, MENU_SHAPE)
                                .border(
                                    width = 1.dp,
                                    color = colors.outlineVariant.copy(alpha = 0.5f),
                                    shape = MENU_SHAPE
                                )
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            actions.forEachIndexed { index, action ->
                                M3EPageActionMenuItem(
                                    action = action,
                                    index = index,
                                    onSelect = {
                                        expanded = false
                                        action.onClick()
                                    }
                                )
                            }
                        }
                    }
                }

                val toggleDescription = if (expanded) expandedLabel else collapsedLabel
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .testTag(M3EPageActionsMenuTestTags.TOGGLE)
                        .semantics { contentDescription = toggleDescription },
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    elevation = fabElevation
                ) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            fadeIn(animationSpec = effectsSpec) togetherWith
                                fadeOut(animationSpec = effectsSpec)
                        },
                        label = "Page actions toggle icon"
                    ) { isExpanded ->
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * D-124: reusable page host that guarantees the page-actions FAB floats above
 * ALL page content. [content] (the screen body — header, lists, cards, sheets)
 * is composed first; the [M3EPageActionsMenu] overlay is composed last in the
 * same root `Box`, so it is drawn on top and can never be clipped or scrolled
 * away by a sibling. It is pinned bottom-end with [FAB_BOTTOM_INSET] clearing
 * the floating navigation pill (DESIGN.md: centred pill, 8 dp above the bottom).
 *
 * The decision to host here (per screen) rather than in `M3EAppScaffold` /
 * `MainTabPager` is deliberate: the action lambdas capture screen-local state
 * (e.g. Profile's logout-confirmation flag, ExamSchedule's clipboard/export
 * notices), and those hosts only receive opaque `content`/zero-arg screens.
 * Hoisting them would require edits to App.kt / AppState.kt / M3EStateBridge.kt,
 * which are owned by other workers. This host keeps every lambda verbatim.
 *
 * Pages with fewer than two actions render no FAB and keep their inline button.
 */
@Composable
fun M3EPageActionsHost(
    actions: List<M3EPageAction>,
    modifier: Modifier = Modifier,
    collapsedLabel: String = "More actions",
    expandedLabel: String = "Close actions",
    content: @Composable () -> Unit
) {
    AmazeM3ETheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                // D-124: test seam proving the FAB is mounted in the page-root
                // overlay, not inline in a scrolling header.
                .testTag(M3EPageActionsMenuTestTags.HOST_ROOT)
        ) {
            content()
            if (actions.size >= 2) {
                M3EPageActionsMenu(
                    actions = actions,
                    fabModifier = Modifier.padding(
                        end = FAB_END_INSET,
                        bottom = FAB_BOTTOM_INSET
                    ),
                    collapsedLabel = collapsedLabel,
                    expandedLabel = expandedLabel
                )
            }
        }
    }
}

/** One action exposed by [M3EPageActionsMenu]. */
data class M3EPageAction(
    /** Visible menu label; also the accessibility click label for the item. */
    val label: String,
    /** A real Material rounded icon vector, drawn at the M3E 24 dp standard. */
    val icon: ImageVector,
    val onClick: () -> Unit,
    /** Disabled items stay visible but cannot be activated (e.g. no results to export). */
    val enabled: Boolean = true,
    /** Optional stable tag for screens whose existing tests own a trigger tag. */
    val testTag: String? = null
)

object M3EPageActionsMenuTestTags {
    const val HOST_ROOT = "m3e_page_actions_host_root"
    const val TOGGLE_AREA = "m3e_page_actions_area"
    const val TOGGLE = "m3e_page_actions_toggle"
    const val ITEMS = "m3e_page_actions_items"
    const val BACKDROP = "m3e_page_actions_backdrop"
    fun item(index: Int): String = "m3e_page_actions_item_$index"
}

@Composable
private fun M3EPageActionMenuItem(
    action: M3EPageAction,
    index: Int,
    onSelect: () -> Unit
) {
    val colors = M3ETheme.colors
    val foreground = if (action.enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f)
    val iconTint = if (action.enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .testTag(M3EPageActionsMenuTestTags.item(index))
            .then(if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier)
            .defaultMinSize(minWidth = 220.dp, minHeight = 56.dp)
            .clickableA11y(onClickLabel = action.label, enabled = action.enabled, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val MENU_SHAPE = RoundedCornerShape(20.dp)
private val MENU_GAP = 4.dp
/** End inset matching DESIGN.md's 16 px outer gutter. */
private val FAB_END_INSET = 16.dp
/** Bottom inset clearing the centred nav pill (≈80 dp pill + 12 dp lift). */
private val FAB_BOTTOM_INSET = 96.dp
