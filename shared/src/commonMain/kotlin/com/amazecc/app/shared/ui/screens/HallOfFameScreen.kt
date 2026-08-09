package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.HeaderSpacer

@Composable
fun HallOfFameScreen() {
    val colors = AmazeTheme.colors
    
    val contributors = listOf(
        Pair("Jane Doe", "Lead Developer & UI Designer"),
        Pair("John Smith", "Backend & API Integrations"),
        Pair("Alice Johnson", "Quality Assurance & Testing"),
        Pair("Bob Williams", "Documentation & Support")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSpacer() }
            items(contributors.size, key = { it }) { index ->
                val contributor = contributors[index]
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contributor.first.take(1),
                                style = AmazeTheme.typography.heading.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
                        Column {
                            Text(
                                text = contributor.first,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                text = contributor.second,
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}
