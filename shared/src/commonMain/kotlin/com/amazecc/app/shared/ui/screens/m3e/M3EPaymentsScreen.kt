package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.FormattingParity
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A single fee or receipt row, mapped from the scraped [PaymentItem]. */
@Immutable
data class M3EPaymentItem(
    val id: String,
    val reference: String,
    val date: String,
    val description: String,
    val amount: String,
    val isPaid: Boolean,
    /** VTOP application number used to fetch this payment's receipt detail. */
    val receiptNo: String = ""
)

/** Immutable, screen-owned state for [M3EPaymentsScreen]. */
@Immutable
data class M3EPaymentsState(
    val walletBalance: String = "",
    val dues: List<M3EPaymentItem> = emptyList(),
    val receipts: List<M3EPaymentItem> = emptyList(),
    val selectedTab: M3EPaymentsTab = M3EPaymentsTab.DUES,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Non-null when a receipt detail has been fetched; rendered in a detail dialog. */
    val receiptDetail: ArrearResponse? = null,
    /** Neutral (non-error) feedback, e.g. when a payment has no receipt number. */
    val receiptNotice: String? = null,
    /** Privacy mode (web parity) — amounts are masked instead of rendered. */
    val blurAmounts: Boolean = false
)

/** The two payment ledger views. */
enum class M3EPaymentsTab(val label: String) {
    DUES("Dues"),
    RECEIPTS("Receipts")
}

/** User intents emitted by [M3EPaymentsScreen]. */
sealed interface M3EPaymentsAction {
    data class SelectTab(val tab: M3EPaymentsTab) : M3EPaymentsAction
    data object Refresh : M3EPaymentsAction
    data class PayNow(val paymentId: String) : M3EPaymentsAction
    data class DownloadReceipt(val paymentId: String) : M3EPaymentsAction
    data object DismissReceipt : M3EPaymentsAction
}

/**
 * Material 3 Expressive Fee Payments & Receipts screen.
 * 100% stateless: receives [M3EPaymentsState] and emits [M3EPaymentsAction].
 * Data comes from AppState.payments (online scraper / offline cache) via the host bridge.
 */
@Composable
fun M3EPaymentsScreen(
    state: M3EPaymentsState,
    onAction: (M3EPaymentsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fee Payments",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Wallet, pending dues & past receipts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3EPaymentsAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh payments",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // One hero: the wallet card. Spring-driven press feedback lives inside M3ESurfaceCard.
        item(key = "wallet") {
            WalletHeroCard(
                balance = state.walletBalance,
                duesCount = state.dues.size,
                receiptsCount = state.receipts.size,
                blurAmounts = state.blurAmounts
            )
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(
                    progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                    label = "Payment loading"
                )
            }
        }

        item(key = "tabs") {
            M3EConnectedButtonGroup(
                items = M3EPaymentsTab.entries,
                selectedItem = state.selectedTab,
                onItemSelected = { onAction(M3EPaymentsAction.SelectTab(it)) },
                labelProvider = { tab ->
                    when (tab) {
                        M3EPaymentsTab.DUES -> "Dues (${state.dues.size})"
                        M3EPaymentsTab.RECEIPTS -> "Receipts (${state.receipts.size})"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Neutral feedback (not an error) — e.g. a receipt row with no application number.
        if (!state.receiptNotice.isNullOrBlank()) {
            item(key = "notice") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.receiptNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }

        val visibleItems = when (state.selectedTab) {
            M3EPaymentsTab.DUES -> state.dues
            M3EPaymentsTab.RECEIPTS -> state.receipts
        }

        if (visibleItems.isEmpty()) {
            item(key = "empty") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (state.selectedTab == M3EPaymentsTab.DUES) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.AutoMirrored.Rounded.ReceiptLong
                            },
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.selectedTab == M3EPaymentsTab.DUES) {
                                "All clear — no pending dues"
                            } else {
                                "No receipts found"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }
                }
            }
        } else {
            items(visibleItems, key = { it.id }) { payment ->
                PaymentCard(
                    payment = payment,
                    blurAmounts = state.blurAmounts,
                    onPrimaryAction = {
                        if (payment.isPaid) {
                            onAction(M3EPaymentsAction.DownloadReceipt(payment.id))
                        } else {
                            onAction(M3EPaymentsAction.PayNow(payment.id))
                        }
                    }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }

    val detail = state.receiptDetail
    if (detail != null) {
        ReceiptDetailDialog(
            response = detail,
            onDismiss = { onAction(M3EPaymentsAction.DismissReceipt) }
        )
    }
}

/** M3E-styled dialog that renders the VTOP receipt's tables and key/value rows. */
@Composable
private fun ReceiptDetailDialog(
    response: ArrearResponse,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        modifier = modifier,
        title = "Payment Receipt"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (response.keyValuePairs.isNotEmpty()) {
                    item(key = "fields") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                response.keyValuePairs.forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pair.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = pair.value,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                response.tables.forEachIndexed { index, table ->
                    item(key = "table_$index") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val title = table.title ?: table.caption ?: "Details"
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                                table.headers.forEach { header ->
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primary
                                    )
                                }
                                table.rows.forEach { row ->
                                    Text(
                                        text = row.joinToString("  •  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                response.messages.forEach { message ->
                    item(key = "message_${message.message}") {
                        Text(
                            text = message.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                if (response.keyValuePairs.isEmpty() && response.tables.isEmpty() && response.messages.isEmpty()) {
                    item(key = "empty") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Text(
                                text = response.message ?: "No receipt details available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Close", color = colors.primary)
                }
            }
        }
    }
}

/**
 * Fee amounts in display form (web parity — `FormattingParity.fmtAmt`).
 * With [blurAmounts] the digits are masked, matching the privacy-mode blur.
 */
private fun displayAmount(raw: String, blurAmounts: Boolean): String {
    val formatted = FormattingParity.fmtAmt(raw)
    return if (blurAmounts) maskDigits(formatted) else formatted
}

private fun maskDigits(text: String): String = text.map { if (it.isDigit()) '•' else it }.joinToString("")

@Composable
private fun WalletHeroCard(
    balance: String,
    duesCount: Int,
    receiptsCount: Int,
    blurAmounts: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // Subtle spring scale on the hero balance when the amount changes.
    val balanceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "WalletBalanceScale"
    )

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "VIT Wallet Balance",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayAmount(balance.ifBlank { "0" }, blurAmounts),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                modifier = Modifier.graphicsLayer(scaleX = balanceScale, scaleY = balanceScale)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$duesCount dues",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$receiptsCount receipts",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: M3EPaymentItem,
    blurAmounts: Boolean = false,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Payment,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ref: ${payment.reference}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = payment.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (payment.isPaid) colors.primaryContainer else colors.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (payment.isPaid) "PAID" else "PENDING",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (payment.isPaid) colors.onPrimaryContainer else colors.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = payment.description,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayAmount(payment.amount, blurAmounts),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                ElevatedButton(
                    onClick = onPrimaryAction,
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (payment.isPaid) colors.surfaceContainerHigh else colors.primary,
                        contentColor = if (payment.isPaid) colors.onSurface else colors.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (payment.isPaid) Icons.Rounded.Download else Icons.Rounded.Pending,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (payment.isPaid) "Receipt" else "Pay Now",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (!payment.isPaid) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Opens VTOP payments in your browser",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewPaymentsState() = M3EPaymentsState(
    walletBalance = FormattingParity.fmtAmt(1240),
    dues = listOf(
        M3EPaymentItem(
            id = "bill-1",
            reference = "2026-8812",
            date = "Due 30 Nov 2026",
            description = "Evening Semester Tuition Fee",
            amount = FormattingParity.fmtAmt(1240),
            isPaid = false
        )
    ),
    receipts = listOf(
        M3EPaymentItem(
            id = "bill-2",
            reference = "2026-7701",
            date = "Paid 12 Aug 2026",
            description = "Hostel Fee — Q Block",
            amount = FormattingParity.fmtAmt(8500),
            isPaid = true
        )
    )
)

@Preview
@Composable
private fun M3EPaymentsScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EPaymentsScreen(state = previewPaymentsState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EPaymentsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EPaymentsScreen(state = previewPaymentsState(), onAction = {})
    }
}
