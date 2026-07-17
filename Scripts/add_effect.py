import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/payments/PaymentsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add LaunchedEffect after val payments = ...
old_code = """    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    var subTab by remember { mutableStateOf("due") }"""

new_code = """    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    var subTab by remember { mutableStateOf("due") }

    LaunchedEffect(paymentsRes) {
        if (paymentsRes == null) {
            AppState.refreshPayments()
        }
    }"""

content = content.replace(old_code, new_code)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/payments/PaymentsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
