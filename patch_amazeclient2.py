import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/api/AmazeClient.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add missing imports if they don't exist
imports_to_add = [
    "import kotlinx.serialization.json.booleanOrNull",
    "import kotlinx.serialization.json.jsonArray",
    "import kotlinx.serialization.json.boolean"
]

for imp in imports_to_add:
    if imp not in content:
        content = content.replace("import kotlinx.serialization.json.jsonPrimitive", f"import kotlinx.serialization.json.jsonPrimitive\n{imp}")

# Fix the method
old_code = """    suspend fun getPayments(): PaymentsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return PaymentsRes(
                success = true,
                payments = listOf(
                    PaymentItem("BILL-4412", "Academic Tuition Fees 2026-27", "Rs. 1,98,000", "2026-06-15", "PAID", "2026-06-10", "REC-99120"),
                    PaymentItem("BILL-4501", "Hostel & Mess Booking Q-Block", "Rs. 1,12,000", "2026-06-30", "PAID", "2026-06-25", "REC-99881")
                ),
                walletBalance = "Rs. 2,450.00"
            )
        }
        return try {
            val duesResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payments")
            val receiptsResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payment-receipts")
            val walletResp = postAuthorized<kotlinx.serialization.json.JsonObject>("wallet")

            val paymentsList = mutableListOf<PaymentItem>()

            if (duesResp?.get("hasDues")?.jsonPrimitive?.boolean == true) {
                paymentsList.add(PaymentItem(
                    id = "due-pending",
                    title = duesResp["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Pending Dues",
                    amount = "Check VTOP",
                    dueDate = "-",
                    status = "UNPAID"
                ))
            }

            val receiptsArray = receiptsResp?.get("receipts")?.jsonArray
            receiptsArray?.forEach { r ->
                val obj = r.jsonObject
                paymentsList.add(PaymentItem(
                    id = obj["receiptNumber"]?.jsonPrimitive?.content ?: "rec",
                    title = "Fee Payment",
                    amount = obj["amount"]?.jsonPrimitive?.content ?: "-",
                    dueDate = "-",
                    status = "PAID",
                    paymentDate = obj["date"]?.jsonPrimitive?.content,
                    receiptNo = obj["receiptNumber"]?.jsonPrimitive?.content
                ))
            }

            val walletLedger = walletResp?.get("ledgerINR")?.jsonArray
            val balance = if (walletLedger != null && walletLedger.isNotEmpty()) {
                walletLedger[0].jsonObject["bookBalanceAmount"]?.jsonPrimitive?.content
            } else null

            PaymentsRes(
                success = true,
                payments = paymentsList,
                walletBalance = balance,
                message = if (duesResp?.get("hasDues")?.jsonPrimitive?.boolean == false) duesResp["message"]?.jsonPrimitive?.content else null
            )
        } catch (e: Exception) {
            PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }"""

new_code = """    suspend fun getPayments(): PaymentsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return PaymentsRes(
                success = true,
                payments = listOf(
                    PaymentItem("BILL-4412", "Academic Tuition Fees 2026-27", "Rs. 1,98,000", "2026-06-15", "PAID", "2026-06-10", "REC-99120"),
                    PaymentItem("BILL-4501", "Hostel & Mess Booking Q-Block", "Rs. 1,12,000", "2026-06-30", "PAID", "2026-06-25", "REC-99881")
                ),
                walletBalance = "Rs. 2,450.00"
            )
        }
        return try {
            val duesResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payments")
            val receiptsResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payment-receipts")
            val walletResp = postAuthorized<kotlinx.serialization.json.JsonObject>("wallet")

            val paymentsList = mutableListOf<PaymentItem>()

            if (duesResp?.get("hasDues")?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true || duesResp?.get("hasDues")?.jsonPrimitive?.booleanOrNull == true) {
                paymentsList.add(PaymentItem(
                    billingId = "due-pending",
                    description = duesResp["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Pending Dues",
                    amount = "Check VTOP",
                    dueDate = "-",
                    status = "UNPAID"
                ))
            }

            val receiptsArray = receiptsResp?.get("receipts")?.jsonArray
            receiptsArray?.forEach { r ->
                val obj = r.jsonObject
                paymentsList.add(PaymentItem(
                    billingId = obj["receiptNumber"]?.jsonPrimitive?.content ?: "rec",
                    description = "Fee Payment",
                    amount = obj["amount"]?.jsonPrimitive?.content ?: "-",
                    dueDate = "-",
                    status = "PAID",
                    paymentDate = obj["date"]?.jsonPrimitive?.content,
                    receiptNo = obj["receiptNumber"]?.jsonPrimitive?.content
                ))
            }

            val walletLedger = walletResp?.get("ledgerINR")?.jsonArray
            val balance = if (walletLedger != null && walletLedger.size > 0) {
                walletLedger[0].jsonObject["bookBalanceAmount"]?.jsonPrimitive?.content
            } else null

            val hasDuesVal = duesResp?.get("hasDues")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: duesResp?.get("hasDues")?.jsonPrimitive?.booleanOrNull
            PaymentsRes(
                success = true,
                payments = paymentsList,
                walletBalance = balance,
                message = if (hasDuesVal == false) duesResp["message"]?.jsonPrimitive?.content else null
            )
        } catch (e: Exception) {
            PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/api/AmazeClient.kt', 'w', encoding='utf-8') as f:
        f.write(content)
    print("SUCCESS")
else:
    print("FAILED TO FIND OLD CODE")
