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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs of the Arrear & Remedial screen. */
enum class M3EArrearTab(val label: String) {
    SCHEDULE("Schedule"), DETAILS("Details"), GRADES("Grades")
}

/** Immutable, stateless state for [M3EArrearScreen]. */
@Immutable
data class M3EArrearState(
    val activeTab: M3EArrearTab = M3EArrearTab.SCHEDULE,
    val arrearCount: Int = 0,
    val isLoading: Boolean = false
)

/** User intents emitted by [M3EArrearScreen]. */
sealed interface M3EArrearAction {
    data class SelectTab(val tab: M3EArrearTab) : M3EArrearAction
    data object Refresh : M3EArrearAction
    data object Back : M3EArrearAction
}

/**
 * Material 3 Expressive Arrear & Remedial screen.
 * 100% stateless: accepts [M3EArrearState], emits [M3EArrearAction].
 */
@Composable
fun M3EArrearScreen(
    state: M3EArrearState,
    onAction: (M3EArrearAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val emptyMessage = when (state.activeTab) {
        M3EArrearTab.SCHEDULE -> "No arrear exams scheduled"
        M3EArrearTab.DETAILS -> "No current arrear details"
        M3EArrearTab.GRADES -> "No past arrear grades found"
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Arrears & Remedial", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Registrations, schedule and past grades", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3EArrearAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3EArrearTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3EArrearAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            item(key = "empty") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.School, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(emptyMessage, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

@Preview
@Composable
private fun M3EArrearScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EArrearScreen(state = M3EArrearState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EArrearScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EArrearScreen(state = M3EArrearState(), onAction = {}) }
}
