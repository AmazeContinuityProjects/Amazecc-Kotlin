import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/payments/PaymentsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the subTabs logic
old_when = """        when (subTab) {
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
                            Text(" past receipts", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }"""

new_when = """        val dues = payments.filter { it.status != "PAID" }
        val receipts = payments.filter { it.status == "PAID" }

        when (subTab) {
            "receipts" -> {
                if (receipts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
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
                        items(receipts) { payment ->
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
                if (dues.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("All clear — no pending dues", color = colors.textMuted)
                            if (receipts.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(" past receipts", color = colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 30.dp)
                    ) {
                        items(dues) { payment ->
                            PaymentReceiptCard(payment, colors)
                        }
                    }
                }
            }
        }"""

content = content.replace(old_when, new_when)

# Fix the mojibake replacement in the amount
content = re.sub(r'payment\.amount\.replace\("[^"]+", "\\u20B9"\)', 'payment.amount.replace("Rs.", "\\u20B9").replace("INR", "\\u20B9")', content)
content = re.sub(r'paymentsRes\?\.walletBalance\?\.replace\("[^"]+", "\\u20B9"\)', 'paymentsRes?.walletBalance?.replace("Rs.", "\\u20B9")?.replace("INR", "\\u20B9")', content)

# Fix Icon(Icons.Rounded.ReceiptLong to Icon(Icons.AutoMirrored.Rounded.ReceiptLong
content = content.replace('Icons.Rounded.ReceiptLong', 'Icons.AutoMirrored.Rounded.ReceiptLong')

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/payments/PaymentsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
