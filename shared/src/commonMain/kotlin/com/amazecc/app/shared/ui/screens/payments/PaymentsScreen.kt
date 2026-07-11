package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.layout.*
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.amazecc.app.shared.ui.components.MetricCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun PaymentsScreen() {
    val colors = AmazeTheme.colors
    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Payments",
            description = "View dues and transaction history",
            showBackButton = false,
            showSyncButton = true
        )

        MetricCard(
            title = "VTOP WALLET BALANCE",
            value = paymentsRes?.walletBalance ?: "—",
            caption = "Available when wallet data is returned by the API",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Transactions & Dues", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (payments.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No billing receipts found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(payments) { bill ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(bill.description, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                                AmazeBadge(text = bill.status, variant = if (bill.status == "PAID") BadgeVariant.SUCCESS else BadgeVariant.DANGER)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Billing ID: ${bill.billingId}", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                Text(bill.amount, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
                            }
                            if (bill.paymentDate != null) {
                                Text("Paid on: ${bill.paymentDate} (Receipt: ${bill.receiptNo})", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}
