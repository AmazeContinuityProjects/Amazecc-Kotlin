package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Unified Page Shell.
 * Directly corresponds to the primitives-based PageShell from AmazeCC with M3E tokens.
 * 
 * Features:
 * - Eyebrow kicker with uppercase tracking
 * - Heavy headline title + subtitle description
 * - Action cluster and optional back button
 * - Built-in [M3EStatCardsCarousel] for hero insights
 * - Mobile-first vertical rhythm
 */
@Composable
fun M3EPageShell(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    stats: List<M3EStatItem> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Block
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: Back button (if present) + Actions cluster
                if (onBack != null || actions != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceContainerLow)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(38.dp))
                        }

                        if (actions != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Eyebrow kicker
                if (!eyebrow.isNullOrBlank()) {
                    Text(
                        text = eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        letterSpacing = 1.2.sp
                    )
                }

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    letterSpacing = (-0.5).sp
                )

                // Subtitle
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Stat Cards Carousel (if provided)
            if (stats.isNotEmpty()) {
                M3EStatCardsCarousel(
                    stats = stats,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Page Content Body
            content()
        }
    }
}

@Preview
@Composable
private fun M3EPageShellPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EPageShell(
            eyebrow = "Academics",
            title = "Course Details",
            subtitle = "Winter Semester 2026-27 • 24 Registered Credits",
            onBack = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            },
            stats = listOf(
                M3EStatItem(
                    id = "stat_1",
                    label = "Current CGPA",
                    value = "9.18",
                    sub = "Rank #12 in SCOPE",
                    tone = M3EStatTone.PRIMARY
                ),
                M3EStatItem(
                    id = "stat_2",
                    label = "Total Attendance",
                    value = "94.2%",
                    sub = "Safe across all 6 courses",
                    tone = M3EStatTone.GOOD
                )
            )
        ) {
            Text("Page content goes here", color = M3ETheme.colors.onSurface)
        }
    }
}
