package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 */
@Composable
fun <T> M3EConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() }
) {
    val colors = M3ETheme.colors

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
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

            // Asymmetric corner radius based on index
            val itemShape = RoundedCornerShape(16.dp)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(itemShape)
                    .background(animatedBg)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = animatedContent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = labelProvider(item),
                        color = animatedContent,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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
            items = listOf("All", "Critical (<75%)", "Safe (>80%)"),
            selectedItem = "All",
            onItemSelected = {}
        )
    }
}
