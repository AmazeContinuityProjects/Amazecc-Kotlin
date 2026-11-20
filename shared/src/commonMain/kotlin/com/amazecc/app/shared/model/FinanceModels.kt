package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Typed mirror of `POST /api/finance` (`section=all`). The Hono route spreads each section at the
 * TOP level — `{ success, dues, receipts, wallet, bank, library }` — so the old client's ad-hoc
 * `JsonObject` projection silently dropped refunds, invoices and the USD ledger (D-022).
 *
 * Every field is optional/defaulted: a partial upstream page must never throw.
 */
@Serializable
data class FinanceResponse(
    val success: Boolean = true,
    val error: String? = null,
    val dues: DuesSection? = null,
    val receipts: ReceiptsSection? = null,
    val wallet: WalletSection? = null,
    val bank: BankSection? = null,
    val library: LibraryDues? = null,
    val receiptDetail: ReceiptDetailSection? = null,
    val errors: Map<String, String>? = null
)

@Serializable
data class DuesSection(
    val hasDues: Boolean = false,
    val message: String? = null,
    val studentInfo: DuesStudentInfo? = null
)

@Serializable
data class DuesStudentInfo(
    val name: String? = null,
    val programme: String? = null,
    val campus: String? = null
)

@Serializable
data class ReceiptItem(
    val receiptNumber: String = "",
    val date: String? = null,
    val amount: String? = null,
    val campusCode: String? = null,
    val applNo: String? = null
)

@Serializable
data class ReceiptsSection(
    val receipts: List<ReceiptItem> = emptyList()
)

/** One wallet ledger row. All cells are strings from VTOP; absent cells stay null. */
@Serializable
data class LedgerEntry(
    val transactionDate: String? = null,
    val receiptNumber: String? = null,
    val transactionType: String? = null,
    val amount: String? = null,
    val refundAmount: String? = null,
    val bookBalanceAmount: String? = null,
    val refundDate: String? = null,
    val particulars: String? = null
)

@Serializable
data class WalletSection(
    val ledgerINR: List<LedgerEntry> = emptyList(),
    val ledgerUSD: List<LedgerEntry> = emptyList()
)

@Serializable
data class BankSection(
    val fields: Map<String, String>? = null,
    val tables: List<JsonElement>? = null
)

@Serializable
data class ReceiptDetailSection(
    val tables: List<JsonElement> = emptyList()
)

/** A book row from the nested `library` block of `POST /api/finance?section=library` (D-017). */
@Serializable
data class LibraryBook(
    val title: String = "",
    val author: String? = null,
    val isbn: String? = null,
    /** VTOP publishes this as a string, but the TS contract allows a number. */
    @Serializable(with = FlexibleStringSerializer::class)
    val dueAmount: String? = null,
    /** Parsed to a number by the API when possible; a raw string is also accepted. */
    @Serializable(with = FlexibleStringSerializer::class)
    val daysOverdue: String? = null
)

/** Nested `library` payload: `{ books, tables, keyValuePairs, messages }`. */
@Serializable
data class LibraryDues(
    val books: List<LibraryBook> = emptyList(),
    val tables: List<JsonElement> = emptyList(),
    val keyValuePairs: Map<String, String>? = null,
    val messages: Map<String, String>? = null
) {
    /** UI-facing book rows, derived from the nested wire shape without inventing data. */
    val booksIssued: List<BookItem> get() = books.map { b ->
        BookItem(
            bookId = b.isbn.orEmpty(),
            title = b.title,
            author = b.author,
            dueDate = b.daysOverdue,
            fineAmount = b.dueAmount,
            isbn = b.isbn,
            daysOverdue = b.daysOverdue
        )
    }
}
