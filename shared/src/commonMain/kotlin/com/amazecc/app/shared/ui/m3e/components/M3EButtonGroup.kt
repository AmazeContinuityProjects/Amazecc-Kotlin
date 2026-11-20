package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Connected Button Group (replaces legacy segmented button).
 * Sourced directly from M3E specification §9.4.
 *
 * Supports horizontal scrolling/swiping when items exceed the available viewport width,
 * preventing truncated or squished labels on smaller mobile screens. A trailing
 * fade is shown while more content is off-screen so the scroll affordance is visible.
 *
 * D-118: segments are exposed as tabs with an explicit selected state, are at
 * least 48dp tall, and long labels stay reachable through the scroll affordance.
 */
@Composable
fun <T> M3EConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    labelProvider: (T) -> String = { it.toString() }
) {
    val colors = M3ETheme.colors
    val scrollState = rememberScrollState()
    val canScrollForward by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
    }

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item == selectedItem
                val targetBg = if (isSelected) colors.primary else colors.surfaceContainerLow
                val targetContent = if (isSelected) colors.onPrimary else colors.onSurfaceVariant

                val animatedBg by animateColorAsState(
                    targetValue = targetBg,
                    animationSpec = M3ETheme.motion.effectsDefault(),
                    label = "ButtonGroupBg"
                )
                val animatedContent by animateColorAsState(
                    targetValue = targetContent,
                    animationSpec = M3ETheme.motion.effectsDefault(),
                    label = "ButtonGroupContent"
                )

                // Expressive asymmetric pill corner radius:
                // First item: rounded outer start
                // Last item: rounded outer end
                // Middle items: subtle inner curve (8dp) or full pill if selected
                val itemShape = when {
                    items.size == 1 -> RoundedCornerShape(20.dp)
                    index == 0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                    index == items.size - 1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                val label = labelProvider(item)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .clip(if (isSelected) RoundedCornerShape(18.dp) else itemShape)
                        .background(animatedBg)
                        .clickable(onClickLabel = label) { onItemSelected(item) }
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = animatedContent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            color = animatedContent,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // D-118: a visible fade at the trailing edge tells the user there are
        // more segments to scroll to instead of silently clipping long labels.
        if (scrollable && canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(32.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to colors.surfaceContainerLow
                        )
                    )
                    .semantics {
                        contentDescription = "More options to the right — scroll to view"
                    }
            )
        }
    }
}

@Preview
@Composable
private fun M3EConnectedButtonGroupPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EConnectedButtonGroup(
            items = listOf("All Courses", "Critical Attendance (<75%)", "Safe Attendance (>80%)", "Pending Approval"),
            selectedItem = "All Courses",
            onItemSelected = {}
        )
    }
}
