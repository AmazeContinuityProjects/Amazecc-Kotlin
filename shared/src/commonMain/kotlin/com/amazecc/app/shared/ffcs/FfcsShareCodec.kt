package com.amazecc.app.shared.ffcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * D-179 — the four FFCS share payloads, ported from `ffcs-planner`:
 *  - `reference/06-import-export-share.md` §1 (all wire formats and validations)
 *  - `vendor/amazecc-ffcs/src/lib/ffcsShare.ts:38-93, 253-344, 353-573`
 *  - `scripts/ts/src/codec.ts:9-204` / `scripts/python/ffcs_core/codec.py`
 *
 * | param | format  | payload                                              |
 * |-------|---------|------------------------------------------------------|
 * | `d`   | full    | lz-string JSON `{v,c,n,k}` — name + full courses      |
 * | `x`   | compact | lz-string JSON `{v,s,p,i,n}` — prefix + suffixes      |
 * | `q`   | shape   | lz-string JSON `{p,i}` — name-free                    |
 * | `r`   | numeric | `"<code>.<base64url LEB128 deltas>"` — name-free      |
 *
 * `FFCS_SHARE_VERSION` is checked exactly; any mismatch, truncation or bad
 * field shape decodes to null rather than a half-understood timetable. A full
 * decode always assigns a FRESH id, so importing one link twice yields two
 * plans instead of overwriting the first.
 */
object FfcsShareCodec {

    const val FFCS_SHARE_VERSION: Int = 1
    const val FFCS_SHARE_WARN_LENGTH: Int = 8000
    const val DEFAULT_CAMPUS: String = "chennai"

    /** The wire shape of one shared course. Field order is preserved when encoding. */
    data class FfcsShareCourse(
        val id: String,
        val code: String,
        val title: String,
        val slots: List<String>,
        val faculty: String,
        val venue: String,
        val credits: String,
        val type: String,
        val color: String,
        val classId: String? = null,
        val classIds: List<String>? = null,
        val batch: String? = null,
        /** Present on the input model; the shipped `asCourse` drops it on decode. */
        val employeeId: String? = null,
    )

    /** The part of a timetable that actually travels in a share link. */
    data class FfcsShareTimetable(
        val id: String = "",
        val name: String,
        val courses: List<FfcsShareCourse>,
    )

    /** Result of a `?d=` decode — the timetable has a brand new id. */
    data class FfcsDecodedFullShare(val timetable: FfcsShareTimetable, val campus: String)

    /** Validated `?x=` payload. `previous` is normalised to `fall-2026-2027`. */
    data class FfcsCompactShareWire(
        val v: Int,
        val s: String,
        val p: String,
        val i: List<String>,
        val n: String,
    )

    /** Validated `?q=` payload: semester inferred from the class-ID namespace. */
    data class FfcsShapeShareData(val semesterId: FfcsSemesterId, val classIds: List<String>, val prefix: String)

    /** Which of the four payloads a pasted value turned out to be. */
    enum class FfcsShareInputFormat { FULL, COMPACT, SHAPE, NUMERIC_SHAPE }

    data class FfcsParsedShareInput(val format: FfcsShareInputFormat, val encoded: String)

    // ── Full codec ──────────────────────────────────────────────────────────

    /** Compress one timetable into the `?d=` payload. */
    fun encodeTimetableShare(name: String, courses: List<FfcsShareCourse>, campus: String = DEFAULT_CAMPUS): String {
        val wire = buildJsonObject {
            append("v", FFCS_SHARE_VERSION.toString())
            append("c", jsonString(campus))
            append("n", jsonString(name))
            append("k", courses.joinToString(",", "[", "]") { courseJson(it) })
        }
        return FfcsLzString.compressToEncodedUriComponent(wire)
    }

    fun encodeTimetableShare(timetable: FfcsShareTimetable, campus: String = DEFAULT_CAMPUS): String =
        encodeTimetableShare(timetable.name, timetable.courses, campus)

    /** Decode a `?d=` payload; null for tampered, truncated or version-mismatched input. */
    fun decodeTimetableShare(encoded: String): FfcsDecodedFullShare? {
        val wire = parseWire(encoded) ?: return null
        if (wire.intField("v") != FFCS_SHARE_VERSION) return null
        val name = wire.stringField("n") ?: return null
        val rawCourses = wire.arrayField("k") ?: return null
        val courses = rawCourses.mapNotNull { asCourse(it) }
        if (courses.isEmpty()) return null
        val campus = wire.stringField("c")?.takeIf { it.isNotEmpty() } ?: DEFAULT_CAMPUS
        return FfcsDecodedFullShare(FfcsShareTimetable(freshId(), name, courses), campus)
    }

    // ── Compact codec ───────────────────────────────────────────────────────

    /** Encode the shared class-ID prefix plus each suffix; null when unavailable. */
    fun encodeCompactTimetableShare(name: String, courses: List<FfcsShareCourse>, semester: String): String? {
        val perCourse = courses.map { courseIds(it) }
        if (perCourse.any { it.isEmpty() }) return null
        val ids = perCourse.flatten()
        if (ids.toSet().size != ids.size) return null
        val prefix = commonPrefix(ids)
        if (prefix.length < 4) return null
        val suffixes = ids.map { it.substring(prefix.length) }
        // A class id equal to the shared prefix yields an empty suffix — there
        // is no distinguishing tail to send, so the compact form is unusable.
        if (suffixes.any { it.isEmpty() }) return null
        if (suffixes.toSet().size != suffixes.size) return null
        val wire = buildJsonObject {
            append("v", FFCS_SHARE_VERSION.toString())
            append("s", jsonString(semester))
            append("p", jsonString(prefix))
            append("i", jsonArray(suffixes))
            append("n", jsonString(name))
        }
        return FfcsLzString.compressToEncodedUriComponent(wire)
    }

    fun encodeCompactTimetableShare(
        timetable: FfcsShareTimetable,
        semester: FfcsSemesterId,
    ): String? = encodeCompactTimetableShare(timetable.name, timetable.courses, semester.value)

    /** Strictly validate a `?x=` payload before the receiver loads its report. */
    fun decodeCompactTimetableShare(encoded: String): FfcsCompactShareWire? {
        val wire = parseWire(encoded) ?: return null
        if (wire.intField("v") != FFCS_SHARE_VERSION) return null
        val semester = wire.stringField("s") ?: return null
        if (semester !in FULL_SEMESTERS) return null
        val prefix = wire.stringField("p") ?: return null
        if (prefix.length !in 4..100 || !PREFIX_CHARS.matches(prefix)) return null
        val name = wire.stringField("n") ?: return null
        if (name.length > 120) return null
        val suffixes = stringArray(wire["i"]) ?: return null
        if (suffixes.isEmpty() || suffixes.size > 100) return null
        if (suffixes.any { it.length > 100 || !SUFFIX_CHARS.matches(it) }) return null
        if (suffixes.toSet().size != suffixes.size) return null
        return FfcsCompactShareWire(
            v = FFCS_SHARE_VERSION,
            s = if (semester == "previous") FfcsSemesterId.FALL.value else semester,
            p = prefix,
            i = suffixes,
            n = name,
        )
    }

    // ── Shape codec ─────────────────────────────────────────────────────────

    /** Encode the name-free `{p,i}` payload; semester supplies the report prefix. */
    fun encodeTimetableShapeShare(courses: List<FfcsShareCourse>, semester: FfcsSemesterId): String? {
        val reportPrefix = FfcsReports.getFfcsSemester(semester).classIdPrefix ?: return null
        val perCourse = courses.map { courseIds(it) }
        if (perCourse.any { it.isEmpty() }) return null
        val ids = perCourse.flatten()
        if (ids.isEmpty() || ids.size > 100 || ids.toSet().size != ids.size) return null
        if (ids.any { !ID_CHARS.matches(it) || !it.startsWith(reportPrefix) }) return null
        val prefix = commonPrefix(ids)
        if (!prefix.startsWith(reportPrefix)) return null
        val wire = buildJsonObject {
            append("p", jsonString(prefix))
            append("i", jsonArray(ids.map { it.substring(prefix.length) }))
        }
        return FfcsLzString.compressToEncodedUriComponent(wire)
    }

    fun encodeTimetableShapeShare(timetable: FfcsShareTimetable, semester: FfcsSemesterId): String? =
        encodeTimetableShapeShare(timetable.courses, semester)

    /** Decode a `?q=` payload, inferring the semester from the shared prefix. */
    fun decodeTimetableShapeShare(encoded: String): FfcsShapeShareData? {
        val wire = parseWire(encoded) ?: return null
        val prefix = wire.stringField("p") ?: return null
        if (prefix.length !in 4..100 || !PREFIX_CHARS.matches(prefix)) return null
        val suffixes = stringArray(wire["i"]) ?: return null
        if (suffixes.isEmpty() || suffixes.size > 100) return null
        if (suffixes.any { it.length > 100 || !SUFFIX_CHARS.matches(it) }) return null

        val report = FfcsReports.getFfcsSemesterByClassIdPrefix(prefix) ?: return null
        val reportPrefix = report.classIdPrefix ?: return null
        val classIds = suffixes.map { prefix + it }
        if (classIds.any { !it.startsWith(reportPrefix) } || classIds.toSet().size != classIds.size) return null
        return FfcsShapeShareData(report.id, classIds, prefix)
    }

    // ── Numeric-shape codec ─────────────────────────────────────────────────

    /** Encode the smallest name-free payload using the semester's report code. */
    fun encodeNumericShapeShare(courses: List<FfcsShareCourse>, semester: FfcsSemesterId): String? =
        FfcsNumericShapeCodec.encode(collectClassIds(courses), semester)

    /** Lower-level form matching the TS port's `(ids, semCode, classIdPrefix)`. */
    fun encodeNumericShapeShare(ids: Collection<String>, semCode: String, classIdPrefix: String): String? =
        FfcsNumericShapeCodec.encode(ids, semCode, classIdPrefix)

    fun encodeNumericShapeShare(timetable: FfcsShareTimetable, semester: FfcsSemesterId): String? =
        encodeNumericShapeShare(timetable.courses, semester)

    fun decodeNumericShapeShare(encoded: String): FfcsNumericShapeCodec.Data? =
        FfcsNumericShapeCodec.decode(encoded)

    // ── Receiver parser ─────────────────────────────────────────────────────

    /**
     * Parse a pasted URL, bare `?x=...` fragment or bare code.
     *
     * When a URL carries more than one share parameter, the receiver priority
     * is `q` > `r` > `x` > `d` (`FFCSShareView.tsx:40-64`) regardless of their
     * order in the string. A bare code is tried full → compact → numeric →
     * shape (`ffcsShareInput.ts:118-134`): compact precedes shape because a
     * compact payload also satisfies the shape decoder.
     */
    fun parseShareInput(raw: String): FfcsParsedShareInput? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val matches = QUERY_PARAM.findAll(trimmed).toList()
        if (matches.isNotEmpty()) {
            for (key in RECEIVER_PRIORITY) {
                val match = matches.firstOrNull { it.groupValues[1] == key } ?: continue
                val encoded = percentDecode(match.groupValues[2]) ?: return null
                return FfcsParsedShareInput(PARAM_FORMATS.getValue(key), encoded)
            }
            return null
        }
        return detectBareCode(trimmed)
    }

    private fun detectBareCode(value: String): FfcsParsedShareInput? {
        val candidate = if ('%' in value) (percentDecode(value) ?: value) else value
        if (decodeTimetableShare(candidate) != null) return FfcsParsedShareInput(FfcsShareInputFormat.FULL, candidate)
        if (decodeCompactTimetableShare(candidate) != null) {
            return FfcsParsedShareInput(FfcsShareInputFormat.COMPACT, candidate)
        }
        if (decodeNumericShapeShare(candidate) != null) {
            return FfcsParsedShareInput(FfcsShareInputFormat.NUMERIC_SHAPE, candidate)
        }
        if (decodeTimetableShapeShare(candidate) != null) return FfcsParsedShareInput(FfcsShareInputFormat.SHAPE, candidate)
        return null
    }

    // ── URL builders ────────────────────────────────────────────────────────

    /**
     * Build the full share URL, honouring a base path such as `/AmazeCC` so a
     * static GitHub Pages build does not 404 (`ffcsShare.ts:536-573`).
     */
    fun buildShareUrl(encoded: String, origin: String, param: String = "d", basePath: String = ""): String =
        origin.trimEnd('/') + basePath.trimEnd('/') + "/share/ffcs/?$param=${urlEncode(encoded)}"

    fun buildCompactShareUrl(encoded: String, origin: String, basePath: String = ""): String =
        buildShareUrl(encoded, origin, "x", basePath)

    fun buildTimetableShapeShareUrl(encoded: String, origin: String, basePath: String = ""): String =
        buildShareUrl(encoded, origin, "q", basePath)

    fun buildNumericTimetableShapeShareUrl(encoded: String, origin: String, basePath: String = ""): String =
        buildShareUrl(encoded, origin, "r", basePath)

    /** `true` when the link is past WhatsApp/QR-safe length (strictly > 8000). */
    fun isOversizedShareLink(url: String): Boolean = url.length > FFCS_SHARE_WARN_LENGTH

    // ── internals ───────────────────────────────────────────────────────────

    private val FULL_SEMESTERS = setOf(
        FfcsSemesterId.WINTER_FINAL.value,
        FfcsSemesterId.FALL.value,
        FfcsSemesterId.PREVIOUS.value,
    )
    private val PREFIX_CHARS = Regex("[A-Za-z0-9_-]+")
    private val SUFFIX_CHARS = Regex("[A-Za-z0-9_-]*")
    private val ID_CHARS = Regex("[A-Za-z0-9_-]+")
    private val QUERY_PARAM = Regex("(?:^|[?\\s&])([dxqr])=([^\\s&#]+)")
    private val RECEIVER_PRIORITY = listOf("q", "r", "x", "d")
    private val PARAM_FORMATS = mapOf(
        "d" to FfcsShareInputFormat.FULL,
        "x" to FfcsShareInputFormat.COMPACT,
        "q" to FfcsShareInputFormat.SHAPE,
        "r" to FfcsShareInputFormat.NUMERIC_SHAPE,
    )

    private fun freshId(): String =
        "share-${Random.nextLong(0, Long.MAX_VALUE).toString(36)}-${Random.nextLong(0, Long.MAX_VALUE).toString(36)}"

    /** `course.classIds` when non-empty, else `classId`, trimmed and filtered. */
    private fun courseIds(course: FfcsShareCourse): List<String> {
        val raw = if (!course.classIds.isNullOrEmpty()) course.classIds else course.classId?.let { listOf(it) } ?: emptyList()
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun collectClassIds(courses: List<FfcsShareCourse>): List<String> = courses.flatMap { courseIds(it) }

    private fun commonPrefix(values: List<String>): String {
        if (values.isEmpty()) return ""
        var prefix = values[0]
        for (value in values.drop(1)) {
            var length = 0
            while (length < prefix.length && length < value.length && prefix[length] == value[length]) length++
            prefix = prefix.substring(0, length)
            if (prefix.isEmpty()) break
        }
        return prefix
    }

    /** Tiny ordered JSON object builder; kotlinx.serialization is used only for parsing. */
    private class JsonObjectBuilder {
        private val parts = ArrayList<String>()

        fun append(key: String, value: String) {
            parts += "${FfcsShareCodec.jsonString(key)}:$value"
        }

        fun build(): String = parts.joinToString(",", "{", "}")
    }

    /** `JSON.stringify`-shaped object: keys in insertion order, no whitespace. */
    private fun buildJsonObject(body: JsonObjectBuilder.() -> Unit): String =
        JsonObjectBuilder().apply(body).build()

    private fun jsonArray(values: List<String>): String = values.joinToString(",", "[", "]") { jsonString(it) }

    /** JS `JSON.stringify` string escaping: compact, non-ASCII left literal. */
    private fun jsonString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    private fun courseJson(course: FfcsShareCourse): String = buildJsonObject {
        append("id", jsonString(course.id))
        append("code", jsonString(course.code))
        append("title", jsonString(course.title))
        append("slots", jsonArray(course.slots))
        append("faculty", jsonString(course.faculty))
        append("venue", jsonString(course.venue))
        append("credits", jsonString(course.credits))
        append("type", jsonString(course.type))
        append("color", jsonString(course.color))
        if (course.classId != null) append("classId", jsonString(course.classId))
        if (course.classIds != null) append("classIds", jsonArray(course.classIds))
        if (course.batch != null) append("batch", jsonString(course.batch))
        if (course.employeeId != null) append("employeeId", jsonString(course.employeeId))
    }

    private fun parseWire(encoded: String): JsonObject? {
        if (encoded.isEmpty()) return null
        return try {
            val json = FfcsLzString.decompressFromEncodedUriComponent(encoded) ?: return null
            if (json.isEmpty()) return null
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.stringField(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        return if (value.isString) value.content else null
    }

    /** Strict integer check: JS `v !== 1` also rejects the string `"1"`. */
    private fun JsonObject.intField(key: String): Int? {
        val value = this[key] as? JsonPrimitive ?: return null
        // JsonPrimitive has no intOrNull extension in this kotlinx.serialization
        // version. Parse the raw content instead; `isString` still rejects a
        // quoted `"1"` (JS `v !== 1`), and non-numeric content returns null.
        if (value.isString) return null
        return value.content.toIntOrNull()
    }

    private fun JsonObject.arrayField(key: String): JsonArray? = this[key] as? JsonArray

    private fun stringArray(element: JsonElement?): List<String>? {
        val array = element as? JsonArray ?: return null
        val result = ArrayList<String>(array.size)
        for (item in array) {
            val primitive = item as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            result.add(primitive.content)
        }
        return result
    }

    /** Known fields only, exactly like `asCourse` in `ffcsShare.ts:71-93`. */
    private fun asCourse(element: JsonElement): FfcsShareCourse? {
        val obj = element as? JsonObject ?: return null
        val code = obj.stringField("code")?.takeIf { it.isNotEmpty() } ?: return null
        val slots = stringArray(obj["slots"]) ?: return null
        val id = obj.stringField("id")?.takeIf { it.isNotEmpty() } ?: "share-$code"
        return FfcsShareCourse(
            id = id,
            code = code,
            title = obj.stringField("title") ?: "",
            slots = slots,
            faculty = obj.stringField("faculty") ?: "",
            venue = obj.stringField("venue") ?: "",
            credits = obj.stringField("credits") ?: "",
            type = obj.stringField("type") ?: "",
            color = obj.stringField("color") ?: "",
            classId = obj.stringField("classId")?.takeIf { it.isNotEmpty() },
            classIds = stringArray(obj["classIds"]),
            batch = obj.stringField("batch"),
            // The shipped asCourse drops employeeId — keep decode faithful.
            employeeId = null,
        )
    }

    // ── URI helpers (no platform dependency) ────────────────────────────────

    /** JS `encodeURIComponent`: unreserved ASCII stays, everything else is %XX. */
    private fun urlEncode(value: String): String {
        val out = StringBuilder()
        for (byte in utf8Bytes(value)) {
            val b = byte.toInt() and 0xFF
            val isUnreserved = (b in 0x41..0x5A) || (b in 0x61..0x7A) || (b in 0x30..0x39) ||
                b == 0x2D || b == 0x5F || b == 0x2E || b == 0x21 || b == 0x7E ||
                b == 0x2A || b == 0x27 || b == 0x28 || b == 0x29
            if (isUnreserved) {
                out.append(b.toChar())
            } else {
                out.append('%')
                out.append(HEX[(b shr 4) and 0xF])
                out.append(HEX[b and 0xF])
            }
        }
        return out.toString()
    }

    /** JS `decodeURIComponent`, only applied when the raw value contains `%`. */
    private fun percentDecode(value: String): String? {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%') {
                if (i + 2 >= value.length) return null
                val hi = hexValue(value[i + 1]) ?: return null
                val lo = hexValue(value[i + 2]) ?: return null
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
            } else {
                for (byte in utf8Bytes(ch.toString())) bytes.add(byte)
                i++
            }
        }
        return utf8Decode(bytes)
    }

    private fun hexValue(ch: Char): Int? = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> null
    }

    private fun utf8Bytes(value: String): ByteArray {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < value.length) {
            var codePoint = value[i].code
            if (codePoint in 0xD800..0xDBFF && i + 1 < value.length) {
                val low = value[i + 1].code
                if (low in 0xDC00..0xDFFF) {
                    codePoint = 0x10000 + ((codePoint - 0xD800) shl 10) + (low - 0xDC00)
                    i++
                }
            }
            when {
                codePoint < 0x80 -> out.add(codePoint.toByte())
                codePoint < 0x800 -> {
                    out.add((0xC0 or (codePoint shr 6)).toByte())
                    out.add((0x80 or (codePoint and 0x3F)).toByte())
                }
                codePoint < 0x10000 -> {
                    out.add((0xE0 or (codePoint shr 12)).toByte())
                    out.add((0x80 or ((codePoint shr 6) and 0x3F)).toByte())
                    out.add((0x80 or (codePoint and 0x3F)).toByte())
                }
                else -> {
                    out.add((0xF0 or (codePoint shr 18)).toByte())
                    out.add((0x80 or ((codePoint shr 12) and 0x3F)).toByte())
                    out.add((0x80 or ((codePoint shr 6) and 0x3F)).toByte())
                    out.add((0x80 or (codePoint and 0x3F)).toByte())
                }
            }
            i++
        }
        return out.toByteArray()
    }

    private fun utf8Decode(bytes: List<Byte>): String? {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> { out.append(b.toChar()); i++ }
                b and 0xE0 == 0xC0 -> {
                    if (i + 1 >= bytes.size) return null
                    out.append((((b and 0x1F) shl 6) or (bytes[i + 1].toInt() and 0x3F)).toChar())
                    i += 2
                }
                b and 0xF0 == 0xE0 -> {
                    if (i + 2 >= bytes.size) return null
                    out.append(
                        (((b and 0x0F) shl 12) or
                            ((bytes[i + 1].toInt() and 0x3F) shl 6) or
                            (bytes[i + 2].toInt() and 0x3F)).toChar(),
                    )
                    i += 3
                }
                b and 0xF8 == 0xF0 -> {
                    if (i + 3 >= bytes.size) return null
                    val cp = ((b and 0x07) shl 18) or
                        ((bytes[i + 1].toInt() and 0x3F) shl 12) or
                        ((bytes[i + 2].toInt() and 0x3F) shl 6) or
                        (bytes[i + 3].toInt() and 0x3F)
                    val v = cp - 0x10000
                    out.append((0xD800 + (v shr 10)).toChar())
                    out.append((0xDC00 + (v and 0x3FF)).toChar())
                    i += 4
                }
                else -> return null
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
