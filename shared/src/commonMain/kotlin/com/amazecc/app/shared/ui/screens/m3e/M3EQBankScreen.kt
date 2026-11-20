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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One course in the question-bank catalog. */
@Immutable
data class M3EQBankCourse(val courseCode: String, val courseTitle: String)

/** Local practice performance summary. */
@Immutable
data class M3EQBankStats(val attempted: Int, val correct: Int, val accuracyPct: Double)

/** Immutable, stateless state for [M3EQBankScreen]. */
@Immutable
data class M3EQBankState(
    val courses: List<M3EQBankCourse> = emptyList(),
    val filteredCourses: List<M3EQBankCourse> = emptyList(),
    val searchQuery: String = "",
    val stats: M3EQBankStats? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false
)

/** User intents emitted by [M3EQBankScreen]. */
sealed interface M3EQBankAction {
    data class SearchQueryChanged(val query: String) : M3EQBankAction
    data class OpenCourse(val courseCode: String) : M3EQBankAction
    data object Refresh : M3EQBankAction
    data object Back : M3EQBankAction
}

/**
 * Material 3 Expressive Exam Prep Hub & QBank catalog.
 * 100% stateless: accepts [M3EQBankState], emits [M3EQBankAction].
 */
@Composable
fun M3EQBankScreen(
    state: M3EQBankState,
    onAction: (M3EQBankAction) -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exam Prep & QBank", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Past papers, practice & model answers", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onAction(M3EQBankAction.Refresh) },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.onSurface)
                    }
                }
            }

            if (state.stats != null) {
                item(key = "practice_stats") {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 24.dp) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your Practice", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                            Text(state.stats.accuracyPct.toFixed(2, "%"), fontSize = 34.sp, fontWeight = FontWeight.Black, color = colors.primary)
                            M3EWavyLinearProgress(
                                progress = (state.stats.accuracyPct / 100.0).toFloat(),
                                activeColor = colors.primary,
                                trackColor = colors.surfaceContainerHighest
                            )
                            Text("${state.stats.correct} correct of ${state.stats.attempted} attempted", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EQBankAction.SearchQueryChanged(it)) },
                    placeholder = { Text("Search by course code or title") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isOffline) {
                item(key = "offline") {
                    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW, borderColor = colors.warning) {
                        Text("Offline — the course catalog requires a connection. Your local practice stats above are still available.", fontSize = 13.sp, color = colors.warning)
                    }
                }
            }

            when {
                state.isLoading -> {
                    item(key = "loading") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                            Text("Loading question bank...", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
                state.errorMessage != null -> {
                    item(key = "error") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW, borderColor = colors.error) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = colors.error, modifier = Modifier.size(36.dp))
                                Text(state.errorMessage ?: "Something went wrong", fontSize = 13.sp, color = colors.error)
                            }
                        }
                    }
                }
                state.filteredCourses.isEmpty() -> {
                    item(key = "empty") {
                        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.LOW) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (state.searchQuery.isNotBlank()) {
                                        "No courses match “${state.searchQuery}”."
                                    } else {
                                        "No courses are available in the catalog yet."
                                    },
                                    fontSize = 14.sp,
                                    color = colors.onSurfaceVariant
                                )
                                // One-tap recovery: clear an active filter, or retry an empty catalog.
                                if (state.searchQuery.isNotBlank()) {
                                    TextButton(onClick = { onAction(M3EQBankAction.SearchQueryChanged("")) }) {
                                        Text("Clear search", color = colors.primary)
                                    }
                                } else {
                                    TextButton(onClick = { onAction(M3EQBankAction.Refresh) }) {
                                        Text("Refresh catalog", color = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    items(state.filteredCourses, key = { it.courseCode }) { course ->
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.STANDARD,
                            onClick = { onAction(M3EQBankAction.OpenCourse(course.courseCode)) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseCode, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                    Text(course.courseTitle, fontSize = 13.sp, color = colors.onSurface, maxLines = 2)
                                }
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

private fun mockQBankState() = M3EQBankState(
    courses = listOf(
        M3EQBankCourse("CSE3002", "Internet and Web Programming"),
        M3EQBankCourse("MAT2001", "Complex Variables & Linear Algebra")
    ),
    filteredCourses = listOf(
        M3EQBankCourse("CSE3002", "Internet and Web Programming"),
        M3EQBankCourse("MAT2001", "Complex Variables & Linear Algebra")
    ),
    stats = M3EQBankStats(attempted = 40, correct = 28, accuracyPct = 70.0)
)

@Preview
@Composable
private fun M3EQBankScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EQBankScreen(state = mockQBankState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EQBankScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EQBankScreen(state = mockQBankState(), onAction = {}) }
}
