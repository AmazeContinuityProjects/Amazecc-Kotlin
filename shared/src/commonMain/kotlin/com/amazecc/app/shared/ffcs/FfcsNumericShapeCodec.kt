package com.amazecc.app.shared.ffcs

/**
 * D-179 — the `?r=` numeric-shape codec: `"<semester-code>.<base64url LEB128 deltas>"`.
 *
 * Ported verbatim from `ffcs-planner`:
 *  - `reference/06-import-export-share.md` §1.3
 *  - `vendor/amazecc-ffcs/src/lib/ffcsShare.ts:345-444`
 *  - `scripts/ts/src/codec.ts:138-198` / `scripts/python/ffcs_core/codec.py:436-516`
 *
 * The deltas are unsigned LEB128 over sorted four-digit class-ID suffixes; the
 * first delta is relative to 0. The golden vector `2.bOYI` decodes to
 * `CH2026270500108, CH2026270501234` and must re-encode to the same bytes.
 */
object FfcsNumericShapeCodec {

    /** Decoded name-free numeric share. */
    data class Data(val semesterId: FfcsSemesterId, val classIds: List<String>)

    private const val B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val B64URL_REVERSE: Map<Char, Int> = B64URL.withIndex().associate { it.value to it.index }
    private val SAFE_DATA = Regex("[A-Za-z0-9_-]+")
    private val FOUR_DIGITS = Regex("[0-9]{4}")

    /** `{ "2": winter prefix, "1": winter mock prefix, "0": fall prefix }`. */
    private val CODE_TO_PREFIX: Map<String, String> = FfcsReports.SEMESTER_REPORTS.mapNotNull { report ->
        val code = report.classIdPrefixCode
        val prefix = report.classIdPrefix
        if (code != null && prefix != null) code to prefix else null
    }.toMap()

    private val REPORT_BY_CODE: Map<String, FfcsSemesterReport> = FfcsReports.SEMESTER_REPORTS.mapNotNull { report ->
        report.classIdPrefixCode?.let { it to report }
    }.toMap()

    /**
     * Encode with the report's own prefix/code. Returns null when the semester
     * has no class-ID namespace or an id is missing/duplicated/not four digits.
     */
    fun encode(classIds: Collection<String>, semester: FfcsSemesterId): String? {
        val report = FfcsReports.getFfcsSemester(semester)
        val prefix = report.classIdPrefix ?: return null
        val code = report.classIdPrefixCode ?: return null
        return encode(classIds, code, prefix)
    }

    /**
     * Lower-level encoder matching the TS port's `(ids, semCode, classIdPrefix)`
     * signature: the caller supplies the code/prefix pair, exactly as the port
     * does. Use [encode] with an [FfcsSemesterId] for registry-backed encoding.
     */
    fun encode(classIds: Collection<String>, semCode: String, classIdPrefix: String): String? {
        if (semCode.isEmpty()) return null
        val unique = classIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (unique.isEmpty() || unique.size > 100 || unique.toSet().size != unique.size) return null

        val suffixes = mutableListOf<Int>()
        for (id in unique) {
            if (!id.startsWith(classIdPrefix)) return null
            val suffix = id.substring(classIdPrefix.length)
            if (!FOUR_DIGITS.matches(suffix)) return null
            suffixes.add(suffix.toInt())
        }
        suffixes.sort()

        val bytes = ArrayList<Byte>()
        var previous = 0
        for (suffix in suffixes) {
            var delta = suffix - previous
            do {
                val data = delta and 0x7F
                delta = delta shr 7
                bytes.add((data or (if (delta > 0) 0x80 else 0)).toByte())
            } while (delta > 0)
            previous = suffix
        }
        return "$semCode.${base64UrlEncode(bytes)}"
    }

    /** Decode and strictly validate a numeric-shape payload. */
    fun decode(encoded: String): Data? {
        if (encoded.isEmpty()) return null
        val dot = encoded.indexOf('.')
        if (dot < 1 || dot == encoded.length - 1) return null

        val code = encoded.substring(0, dot)
        val data = encoded.substring(dot + 1)
        val report = REPORT_BY_CODE[code] ?: return null
        val prefix = report.classIdPrefix ?: return null
        if (!SAFE_DATA.matches(data)) return null

        val bytes = base64UrlDecode(data) ?: return null
        if (bytes.isEmpty() || bytes.size > 200) return null

        val classIds = mutableListOf<String>()
        var currentSuffix = 0
        var value = 0
        var multiplier = 1
        var varintBytes = 0
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            varintBytes++
            if (varintBytes > 2) return null
            value += (b and 0x7F) * multiplier
            if (value > 9999) return null

            if ((b and 0x80) != 0) {
                multiplier *= 128
                continue
            }
            if (varintBytes > 1 && value < 128) return null // non-canonical varint
            if (classIds.isNotEmpty() && value == 0) return null // deltas must be strictly increasing
            currentSuffix += value
            if (currentSuffix > 9999) return null
            classIds.add(prefix + currentSuffix.toString().padStart(4, '0'))
            if (classIds.size > 100) return null

            value = 0
            multiplier = 1
            varintBytes = 0
        }
        if (varintBytes != 0 || classIds.isEmpty()) return null
        return Data(report.id, classIds)
    }

    /** Stable code → prefix lookup, useful to callers rendering `r=` links. */
    fun prefixForCode(code: String): String? = CODE_TO_PREFIX[code]

    private fun base64UrlEncode(bytes: List<Byte>): String {
        val out = StringBuilder()
        var i = 0
        while (i + 3 <= bytes.size) {
            val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            out.append(B64URL[(chunk shr 18) and 0x3F])
            out.append(B64URL[(chunk shr 12) and 0x3F])
            out.append(B64URL[(chunk shr 6) and 0x3F])
            out.append(B64URL[chunk and 0x3F])
            i += 3
        }
        val remaining = bytes.size - i
        if (remaining == 1) {
            val chunk = (bytes[i].toInt() and 0xFF) shl 16
            out.append(B64URL[(chunk shr 18) and 0x3F])
            out.append(B64URL[(chunk shr 12) and 0x3F])
        } else if (remaining == 2) {
            val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            out.append(B64URL[(chunk shr 18) and 0x3F])
            out.append(B64URL[(chunk shr 12) and 0x3F])
            out.append(B64URL[(chunk shr 6) and 0x3F])
        }
        return out.toString()
    }

    /**
     * Base64URL decode with `atob`'s leniency for missing padding: a length of
     * `4n+1` is invalid (matches JS `atob` throwing), `4n+2`/`4n+3` drop the
     * leftover low bits. All input chars are validated by [SAFE_DATA] first.
     */
    private fun base64UrlDecode(data: String): ByteArray? {
        val rem = data.length % 4
        if (rem == 1) return null
        val out = ArrayList<Byte>((data.length * 3) / 4)
        var i = 0
        while (i + 4 <= data.length) {
            val chunk = (B64URL_REVERSE.getValue(data[i]) shl 18) or
                (B64URL_REVERSE.getValue(data[i + 1]) shl 12) or
                (B64URL_REVERSE.getValue(data[i + 2]) shl 6) or
                B64URL_REVERSE.getValue(data[i + 3])
            out.add(((chunk shr 16) and 0xFF).toByte())
            out.add(((chunk shr 8) and 0xFF).toByte())
            out.add((chunk and 0xFF).toByte())
            i += 4
        }
        if (rem == 2) {
            val chunk = (B64URL_REVERSE.getValue(data[i]) shl 18) or
                (B64URL_REVERSE.getValue(data[i + 1]) shl 12)
            out.add(((chunk shr 16) and 0xFF).toByte())
        } else if (rem == 3) {
            val chunk = (B64URL_REVERSE.getValue(data[i]) shl 18) or
                (B64URL_REVERSE.getValue(data[i + 1]) shl 12) or
                (B64URL_REVERSE.getValue(data[i + 2]) shl 6)
            out.add(((chunk shr 16) and 0xFF).toByte())
            out.add(((chunk shr 8) and 0xFF).toByte())
        }
        return out.toByteArray()
    }
}
