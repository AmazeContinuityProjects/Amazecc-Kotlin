package com.amazecc.app.shared.ui.screens.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Payments",
            description = "View dues and transaction history",
            showBackButton = false,
            showSyncButton = true
        )

        AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), backgroundColor = colors.accent.copy(alpha = 0.1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("VTOP WALLET BALANCE", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                    Text(
                        paymentsRes?.walletBalance ?: "â‚¹ 0.00",
                        style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                }
            }
        }

        if (payments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No payment records found.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 30.dp)
            ) {
                items(payments) { payment ->
                    PaymentReceiptCard(payment)
                }
            }
        }
    }
}

@Composable
fun PaymentReceiptCard(payment: PaymentItem) {
    val colors = AmazeTheme.colors
    val isPaid = true // Assuming all fetched are paid history for now, or based on a status field if it exists
    
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Receipt, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ref: ${(payment.receiptNo ?: payment.billingId)}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                }
                AmazeBadge(if (isPaid) "PAID" else "PENDING", variant = if (isPaid) BadgeVariant.SUCCESS else BadgeVariant.WARNING)
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(payment.description, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(4.dp))
            Text((payment.paymentDate ?: payment.dueDate ?: ""), style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(payment.amount, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                AmazeButton(
                    text = "Receipt", 
                    icon = Icons.Rounded.Download,
                    onClick = { /* Open receipt PDF if supported */ }, 
                    variant = ButtonVariant.SECONDARY
                )
            }
        }
    }
}