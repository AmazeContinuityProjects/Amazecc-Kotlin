package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One contributor recognized in the hall of fame. */
@Immutable
data class M3EHallOfFameContributor(val name: String, val role: String)

/** Immutable, stateless state for [M3EHallOfFameScreen]. */
@Immutable
data class M3EHallOfFameState(
    val contributors: List<M3EHallOfFameContributor> = emptyList(),
    val isLoading: Boolean = false
)

/** User intents emitted by [M3EHallOfFameScreen]. */
sealed interface M3EHallOfFameAction {
    data object Refresh : M3EHallOfFameAction
    data object Back : M3EHallOfFameAction
}

/**
 * Material 3 Expressive Hall of Fame screen.
 * 100% stateless: accepts [M3EHallOfFameState], emits [M3EHallOfFameAction].
 */
@Composable
fun M3EHallOfFameScreen(
    state: M3EHallOfFameState,
    onAction: (M3EHallOfFameAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                    Column {
                        Text("Hall of Fame", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("The people who built AmazeCC", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                }
            }

            if (state.contributors.isEmpty()) {
                item(key = "empty") {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                        Text("No contributors to show yet.", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                items(state.contributors, key = { it.name }) { contributor ->
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contributor.name.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                            }
                            Column {
                                Text(contributor.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(contributor.role, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

private fun mockHallOfFameState() = M3EHallOfFameState(
    contributors = listOf(
        M3EHallOfFameContributor("Aravind Kumar", "Lead Developer"),
        M3EHallOfFameContributor("Sugee", "Android & Compose")
    )
)

@Preview
@Composable
private fun M3EHallOfFameScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EHallOfFameScreen(state = mockHallOfFameState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EHallOfFameScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EHallOfFameScreen(state = mockHallOfFameState(), onAction = {}) }
}
