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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * preventing truncated or squished labels on smaller mobile screens.
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

    val containerModifier = if (scrollable) {
        modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .horizontalScroll(scrollState)
            .padding(4.dp)
    } else {
        modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(4.dp)
    }

    Row(
        modifier = containerModifier,
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
            // Middle items: subtle inner curve (12dp) or full pill if selected
            val itemShape = when {
                items.size == 1 -> RoundedCornerShape(20.dp)
                index == 0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                index == items.size - 1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp)
                else -> RoundedCornerShape(8.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(if (isSelected) RoundedCornerShape(18.dp) else itemShape)
                    .background(animatedBg)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
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
                        text = labelProvider(item),
                        color = animatedContent,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
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
