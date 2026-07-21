package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun VitolScreen() {
    val colors = AmazeTheme.colors
    val vitolData by AppState.vitolData.collectAsState()

    val balance = vitolData?.data?.balance ?: "—"
    val limit = vitolData?.data?.limit ?: "—"
    val consumed = vitolData?.data?.consumed ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "VITOL Wallet",
            description = "Digital wallet balance and usage",
            showBackButton = true,
            showSyncButton = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Balance card
            item {
                AmazeCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colors.accent.copy(alpha = 0.06f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AccountBalanceWallet,
                                null,
                                tint = colors.accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Available Balance",
                            style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                        )
                        Text(
                            "Rs. $balance",
                            style = AmazeTheme.typography.display.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Black,
                                fontSize = 36.sp
                            )
                        )
                    }
                }
            }

            // Limit & Consumed
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AmazeCard(modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.ArrowUpward, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Rs. $limit", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text("Limit", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                    AmazeCard(modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.ArrowDownward, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Rs. $consumed", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text("Consumed", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
            }

            // Usage progress
            item {
                val limitNum = limit.toDoubleOrNull() ?: 0.0
                val consumedNum = consumed.toDoubleOrNull() ?: 0.0
                val usagePct = if (limitNum > 0) (consumedNum / limitNum).coerceIn(0.0, 1.0) else 0.0

                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Usage", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { usagePct.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (usagePct < 0.7) Color(0xFF10B981) else if (usagePct < 0.9) Color(0xFFF59E0B) else Color(0xFFEF4444),
                        trackColor = colors.border
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(usagePct * 100).toInt()}% of your limit used",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            // Transactions placeholder
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Receipt, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Transaction history coming soon",
                        style = AmazeTheme.typography.body.copy(color = colors.textMuted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
