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
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
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
    val isPaid: Boolean
)

/** Immutable, screen-owned state for [M3EPaymentsScreen]. */
@Immutable
data class M3EPaymentsState(
    val walletBalance: String = "",
    val dues: List<M3EPaymentItem> = emptyList(),
    val receipts: List<M3EPaymentItem> = emptyList(),
    val selectedTab: M3EPaymentsTab = M3EPaymentsTab.DUES,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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
                receiptsCount = state.receipts.size
            )
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(progress = 0.55f)
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
}

@Composable
private fun WalletHeroCard(
    balance: String,
    duesCount: Int,
    receiptsCount: Int,
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
                text = balance.ifBlank { "₹ 0.00" },
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
                    text = payment.amount,
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
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewPaymentsState() = M3EPaymentsState(
    walletBalance = "₹ 1,240.00",
    dues = listOf(
        M3EPaymentItem(
            id = "bill-1",
            reference = "2026-8812",
            date = "Due 30 Nov 2026",
            description = "Evening Semester Tuition Fee",
            amount = "₹ 1,240.00",
            isPaid = false
        )
    ),
    receipts = listOf(
        M3EPaymentItem(
            id = "bill-2",
            reference = "2026-7701",
            date = "Paid 12 Aug 2026",
            description = "Hostel Fee — Q Block",
            amount = "₹ 8,500.00",
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
