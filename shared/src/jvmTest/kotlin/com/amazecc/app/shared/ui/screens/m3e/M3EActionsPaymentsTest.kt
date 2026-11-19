package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.model.ApiTable
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.KeyValuePair
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM tests for the newly wired PayNow / DownloadReceipt actions. */
@OptIn(ExperimentalTestApi::class)
class M3EActionsPaymentsTest {

    private fun due() = M3EPaymentItem(
        id = "bill-1",
        reference = "2026-8812",
        date = "Due 30 Nov 2026",
        description = "Evening Semester Tuition Fee",
        amount = "₹ 1,240.00",
        isPaid = false
    )

    private fun paid() = M3EPaymentItem(
        id = "bill-2",
        reference = "2026-7701",
        date = "Paid 12 Aug 2026",
        description = "Hostel Fee",
        amount = "₹ 8,500.00",
        isPaid = true,
        receiptNo = "APP-7701"
    )

    @Test
    fun payNowEmitsActionAndShowsHonestExplanation() = runComposeUiTest {
        val actions = mutableListOf<M3EPaymentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(
                    state = M3EPaymentsState(dues = listOf(due())),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Opens VTOP payments in your browser").assertExists()
        onNodeWithText("Pay Now").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3EPaymentsAction.PayNow)
        assertEquals("bill-1", (actions.first() as M3EPaymentsAction.PayNow).paymentId)
    }

    @Test
    fun downloadReceiptEmitsActionWithPaymentId() = runComposeUiTest {
        val actions = mutableListOf<M3EPaymentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(
                    state = M3EPaymentsState(
                        selectedTab = M3EPaymentsTab.RECEIPTS,
                        receipts = listOf(paid())
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Receipt").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3EPaymentsAction.DownloadReceipt)
        assertEquals("bill-2", (actions.first() as M3EPaymentsAction.DownloadReceipt).paymentId)
    }

    @Test
    fun receiptDialogRendersTablesAndKeyValues() = runComposeUiTest {
        val detail = ArrearResponse(
            success = true,
            keyValuePairs = listOf(KeyValuePair("Receipt No.", "RCPT-123")),
            tables = listOf(
                ApiTable(
                    title = "Fee Breakdown",
                    headers = listOf("Head", "Amount"),
                    rows = listOf(listOf("Tuition", "₹ 1,240"))
                )
            )
        )
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(
                    state = M3EPaymentsState(receiptDetail = detail),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Payment Receipt").assertExists()
        onNodeWithText("Receipt No.").assertExists()
        onNodeWithText("RCPT-123").assertExists()
        onNodeWithText("Fee Breakdown").assertExists()
        onNodeWithText("Head").assertExists()
        onNodeWithText("Tuition  •  ₹ 1,240").assertExists()
    }

    @Test
    fun dismissReceiptEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EPaymentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(
                    state = M3EPaymentsState(receiptDetail = ArrearResponse(success = true)),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Close").performClick()
        assertEquals(listOf<M3EPaymentsAction>(M3EPaymentsAction.DismissReceipt), actions)
    }

    @Test
    fun neutralNoticeIsRenderedInsteadOfSilentNoOp() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(
                    state = M3EPaymentsState(
                        receiptNotice = "No receipt number is attached to this payment."
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("No receipt number is attached to this payment.").assertExists()
    }

    @Test
    fun noReceiptDialogWhenDetailIsAbsent() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EPaymentsScreen(state = M3EPaymentsState(), onAction = {})
            }
        }

        onNodeWithText("Payment Receipt").assertDoesNotExist()
    }
}
