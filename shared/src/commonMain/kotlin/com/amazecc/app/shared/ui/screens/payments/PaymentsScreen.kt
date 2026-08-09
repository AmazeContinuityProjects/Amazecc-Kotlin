package com.amazecc.app.shared.ui.screens.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.model.PaymentItem

@Composable
fun PaymentsScreen() {
    val colors = AmazeTheme.colors
    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    var subTab by remember { mutableStateOf("due") }

    LaunchedEffect(paymentsRes) {
        if (paymentsRes == null) {
            AppState.refreshPayments()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            val walletCardGradient = remember(colors) {
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(colors.accent, colors.accent.copy(alpha = 0.6f))
                )
            }

            // Premium Wallet Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.large))
                    .background(walletCardGradient)
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text("VIT Wallet Balance", color = Color.White.copy(alpha = 0.9f), style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium))
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                    Text(
                        paymentsRes?.walletBalance?.replace("Rs.", "\u20B9")?.replace("INR", "\u20B9") ?: "\u20B9 0.00",
                        fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.display, color = Color.White
                    )
                    Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { /* TODO */ },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(AmazeTheme.radius.small)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                            Text("Top Up", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                        }
                        Button(
                            onClick = { /* TODO */ },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                            shape = RoundedCornerShape(AmazeTheme.radius.small)
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                            Text("History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                        }
                    }
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))

            // Sub-tabs (Bouncy Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("due" to "Dues", "receipts" to "Receipts").forEach { (key, label) ->
                    val sel = subTab == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(if (sel) colors.accent else colors.surface)
                            .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                            .clickable { subTab = key }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            color = if (sel) Color.White else colors.textSecondary,
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            val dues = payments.filter { it.status != "PAID" }
            val receipts = payments.filter { it.status == "PAID" }

            when (subTab) {
                "receipts" -> {
                    if (receipts.isEmpty()) {
                        EmptyStateView(Icons.AutoMirrored.Rounded.ReceiptLong, "No receipts found", colors)
                    } else {
                        PaymentList(receipts, colors)
                    }
                }
                "wallet" -> {
                    EmptyStateView(Icons.Rounded.AccountBalance, "Wallet ledger coming soon", colors)
                }
                else -> {
                    if (dues.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.successText, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                Text("All clear — no pending dues", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                if (receipts.isNotEmpty()) {
                                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                    Text("${receipts.size} past receipts", color = colors.textSecondary, style = AmazeTheme.typography.caption)
                                }
                            }
                        }
                    } else {
                        PaymentList(dues, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(AmazeTheme.spacing.md))
            Text(text, style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun PaymentList(items: List<PaymentItem>, colors: com.amazecc.app.shared.theme.AmazeColors) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        items(items, key = { it.billingId }) { payment ->
            PaymentReceiptCard(payment, colors)
        }
    }
}

@Composable
private fun PaymentReceiptCard(payment: PaymentItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val isPaid = payment.status == "PAID"

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Receipt, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column {
                        Text("Ref: ${payment.receiptNo ?: payment.billingId}", color = colors.textSecondary, style = AmazeTheme.typography.smallLabel)
                        Text(payment.paymentDate ?: payment.dueDate ?: "", color = colors.textMuted, style = AmazeTheme.typography.caption)
                    }
                }
                AmazeBadge(if (isPaid) "PAID" else "PENDING", variant = if (isPaid) BadgeVariant.SUCCESS else BadgeVariant.WARNING)
            }
            Spacer(Modifier.height(AmazeTheme.spacing.md))
            Text(payment.description, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(Modifier.height(AmazeTheme.spacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(payment.amount.replace("Rs.", "\u20B9").replace("INR", "\u20B9"), style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
                if (isPaid) {
                    AmazeButton("Receipt", icon = Icons.Rounded.Download, onClick = { /* TODO: download PDF */ }, variant = ButtonVariant.SECONDARY, modifier = Modifier.height(36.dp))
                } else {
                    AmazeButton("Pay Now", icon = Icons.Rounded.Payment, onClick = { /* TODO: Pay */ }, variant = ButtonVariant.PRIMARY, modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}
