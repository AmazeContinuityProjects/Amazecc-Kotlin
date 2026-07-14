package com.amazecc.app.shared.ui.screens.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.model.PaymentItem

@Composable
fun PaymentsScreen() {
    val colors = AmazeTheme.colors
    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    var subTab by remember { mutableStateOf("due") }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Payments",
            description = "View dues and transaction history",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshPayments
        )

        // Wallet balance card
        AmazeCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            backgroundColor = colors.accent.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Wallet Balance", color = colors.textSecondary, fontSize = 11.sp)
                    Text(
                        paymentsRes?.walletBalance?.replace("â‚¹", "\u20B9") ?: "\u20B9 0.00",
                        fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.accent
                    )
                }
            }
        }

        // Sub-tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("due" to "Dues", "receipts" to "Receipts", "wallet" to "Wallet").forEach { (key, label) ->
                val sel = subTab == key
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) colors.accent else colors.surface)
                        .clickable { subTab = key }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (sel) colors.background else colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        when (subTab) {
            "receipts" -> {
                if (payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ReceiptLong, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No receipts found", color = colors.textMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 30.dp)
                    ) {
                        items(payments) { payment ->
                            PaymentReceiptCard(payment, colors)
                        }
                    }
                }
            }
            "wallet" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AccountBalance, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Wallet ledger coming soon", color = colors.textMuted)
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("All clear — no pending dues", color = colors.textMuted)
                        paymentsRes?.let { res ->
                            Spacer(Modifier.height(4.dp))
                            Text("${payments.size} past receipts", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentReceiptCard(payment: PaymentItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val isPaid = payment.status == "PAID"

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Receipt, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ref: ${payment.receiptNo ?: payment.billingId}", color = colors.textSecondary, fontSize = 11.sp)
                }
                AmazeBadge(if (isPaid) "PAID" else "PENDING", variant = if (isPaid) BadgeVariant.SUCCESS else BadgeVariant.WARNING)
            }
            Spacer(Modifier.height(12.dp))
            Text(payment.description, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(payment.paymentDate ?: payment.dueDate ?: "", color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(payment.amount.replace("â‚¹", "\u20B9"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.accent)
                AmazeButton("Receipt", icon = Icons.Rounded.Download, onClick = { /* TODO: download PDF */ }, variant = ButtonVariant.SECONDARY, modifier = Modifier.height(34.dp))
            }
        }
    }
}
