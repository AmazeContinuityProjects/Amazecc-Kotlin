package com.amazecc.app.shared.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Display formatting whose exact output the web app pins.
 *
 * Two rules are worth keeping in one place: JS `toFixed` rounds the *exact binary value* (so
 * `1.005.toFixed(2)` is `"1.00"`), and money is rendered as `₹` immediately followed by `en-IN`
 * grouping with no trailing `.00`. Both are easy to reimplement approximately and then disagree
 * with the web by one digit, so callers should use these functions instead of writing their own.
 */
object FormattingParity {

    /**
     * JavaScript `Number.prototype.toFixed(digits)`, including its binary-float quirks.
     *
     * `BigDecimal(Double)` (unlike `BigDecimal.valueOf`, which uses the shortest round-trip
     * decimal) keeps the same exact value V8 rounds, so `2.675 -> "2.67"` and `1.005 -> "1.00"`.
     * Trailing zeros are kept, matching `toFixed`.
     */
    fun toFixed(value: Double, digits: Int): String {
        require(digits in 0..100) { "toFixed digits out of range: $digits" }
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0.0) "Infinity" else "-Infinity"
        return BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    }

    /** Mirrors `payments.ts fmtAmt` for a raw API amount such as `"₹ 1,240.00"`. */
    fun fmtAmt(raw: String?, symbol: String = "₹"): String {
        if (raw.isNullOrBlank()) return "${symbol}0"
        val cleaned = raw.replace(Regex("[^0-9.\\-]"), "")
        return formatAmount(cleaned.toDoubleOrNull() ?: 0.0, symbol)
    }

    /** Mirrors `payments.ts fmtAmt` for an already numeric amount. */
    fun fmtAmt(value: Number?, symbol: String = "₹"): String {
        if (value == null) return "${symbol}0"
        return formatAmount(value.toDouble(), symbol)
    }

    private fun formatAmount(value: Double, symbol: String): String {
        val finite = if (value.isFinite()) value else 0.0
        // `toLocaleString` renders the shortest round-trip decimal and keeps at most three
        // fraction digits. Trailing zeros are dropped, so a stored "1,240.00" prints as "1,240".
        var amount = BigDecimal.valueOf(finite)
        if (amount.scale() > 3) amount = amount.setScale(3, RoundingMode.HALF_UP)
        val plain = amount.stripTrailingZeros().toPlainString()
        val negative = plain.startsWith("-")
        val unsigned = if (negative) plain.substring(1) else plain
        val parts = unsigned.split(".", limit = 2)
        val integer = groupIndian(parts[0].ifEmpty { "0" })
        val fraction = parts.getOrNull(1)
        val body = if (fraction.isNullOrEmpty()) integer else "$integer.$fraction"
        return "$symbol${if (negative) "-" else ""}$body"
    }

    /** `1234567` -> `12,34,567`, the `en-IN` 3-then-2s grouping. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val tail = digits.takeLast(3)
        var head = digits.dropLast(3)
        val chunks = mutableListOf<String>()
        while (head.length > 2) {
            chunks.add(0, head.takeLast(2))
            head = head.dropLast(2)
        }
        if (head.isNotEmpty()) chunks.add(0, head)
        return (chunks + tail).joinToString(",")
    }
}
