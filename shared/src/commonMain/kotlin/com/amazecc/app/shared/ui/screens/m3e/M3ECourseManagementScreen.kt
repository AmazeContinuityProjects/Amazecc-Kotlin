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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tab labels and their scraper endpoint keys, shared with [M3ECourseManagementScreenHost]. */
val M3E_COURSE_MANAGEMENT_TABS = listOf("Course Option", "EXC Registration", "Minor/Honour", "Completion")
val M3E_COURSE_MANAGEMENT_ENDPOINTS = listOf(
    "course-option-change", "exc-registration", "minor-honour", "course-completion"
)

@Immutable
data class M3ECourseManagementState(
    val selectedTab: Int = 0,
    val responses: Map<String, ArrearResponse> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

sealed interface M3ECourseManagementAction {
    data class SelectTab(val index: Int) : M3ECourseManagementAction
    data object Refresh : M3ECourseManagementAction
    data object Back : M3ECourseManagementAction
}

/**
 * Material 3 Expressive Course Management page — 100% stateless.
 * Consumes the ONLINE scraper responses assembled by the host; no direct data access here.
 */
@Composable
fun M3ECourseManagementScreen(
    state: M3ECourseManagementState,
    onAction: (M3ECourseManagementAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val endpoint = M3E_COURSE_MANAGEMENT_ENDPOINTS.getOrNull(state.selectedTab)

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ACADEMICS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = "Course Management",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Option changes, EXC, minors and completion",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item(key = "tabs") {
                M3EConnectedButtonGroup(
                    items = M3E_COURSE_MANAGEMENT_TABS.indices.toList(),
                    selectedItem = state.selectedTab,
                    onItemSelected = { onAction(M3ECourseManagementAction.SelectTab(it)) },
                    labelProvider = { M3E_COURSE_MANAGEMENT_TABS[it] },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        M3EWavyLinearProgress(progress = 0.55f, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Loading course data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            } else {
                val response = endpoint?.let { state.responses[it] }
                if (response == null || !response.success) {
                    item(key = "empty") {
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Info, null, tint = colors.onSurfaceVariant)
                                Text(
                                    text = response?.message ?: state.errorMessage ?: "No data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    if (response.keyValuePairs.isNotEmpty()) {
                        item(key = "fields") {
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOW,
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    response.keyValuePairs.forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = pair.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = pair.value,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    response.tables.forEachIndexed { tableIndex, table ->
                        item(key = "table_$tableIndex") {
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOW,
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = table.title ?: table.caption ?: "Details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )
                                    table.headers.forEach { header ->
                                        Text(
                                            text = header,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                    }
                                    table.rows.forEach { row ->
                                        Text(
                                            text = row.joinToString("  •  "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (response.keyValuePairs.isEmpty() && response.tables.isEmpty()) {
                        item(key = "no_rows") {
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOW,
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = response.message ?: "No records available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Preview
@Composable
private fun M3ECourseManagementScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECourseManagementScreen(
            state = M3ECourseManagementState(
                selectedTab = 1,
                isLoading = true
            ),
            onAction = {}
        )
    }
}
