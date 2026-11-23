package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.ParsedCourse

/**
 * D-176 — RFC-4180 CSV reader/writer for FFCS registration reports.
 *
 * Ported from the `ffcs-planner` skill:
 *  - `reference/05-csv-generation.md:7-51` (accepted headers, writer rules,
 *    `csvField` verbatim), `:55-69` (per-row rules), `:275-315` (validation),
 *  - `reference/03-data-model-and-schema.md:124-248` (shipped headers,
 *    `parseFastCSV` tokenizer, `parseReportRows` header aliasing),
 *  - `ffcsReports.ts:45-86` (report registry lives in [FfcsReports]).
 *
 * The canonical read type is the D-171 [ParsedCourse] (UPPERCASE fields, SLOT
 * one '+'-joined string). The legacy lowercase `ffcs.ParsedCourse` screen model
 * is intentionally untouched — [FfcsCourseProcessor] bridges the two.
 */
object FfcsCsv {

    /** The exact primary header — byte-for-byte, LF terminated on write. */
    const val CANONICAL_HEADER: String =
        "COURSE CODE,COURSE TITLE,TYPE,CREDITS,CLASS ID,ROOM,SLOT,ERP ID,EMPLOYEE NAME,EMPLOYEE SCHOOL"

    /** Legacy 7-column dialect emitted by `mockffcsReport.csv`. */
    const val LEGACY_HEADER: String = "CODE,TITLE,Type,CREDITS,SLOT,FACULTY,VENUE"

    /** Winter mock slot-timetable: no TYPE, no ROOM, but keeps ids/ERP/school. */
    const val SLOT_TIMETABLE_HEADER: String =
        "COURSE CODE,COURSE TITLE,CREDITS,CLASS ID,SLOT,ERP ID,EMPLOYEE NAME,EMPLOYEE SCHOOL"

    /** CodeChef catalogue: 8 columns, no registration class id. */
    const val CODECHEF_HEADER: String = "CODE,TITLE,TYPE,CREDITS,SLOT,FACULTY,VENUE,EMPLOYEE SCHOOL"

    /** All byte-exact header spellings the shipped planner accepts. */
    val ACCEPTED_HEADERS: List<String> = listOf(
        CANONICAL_HEADER,
        SLOT_TIMETABLE_HEADER,
        CODECHEF_HEADER,
        "CODE,TITLE,TYPE,CREDITS,SLOT,FACULTY,VENUE,EMPLOYEE SCHOOL",
        LEGACY_HEADER,
    )

    /** Course-code rule from reference/05 §2 and the golden vectors. */
    val CODE_REGEX: Regex = Regex("^[A-Z0-9]{7,8}$")

    /** Registration class-id namespaces used by the shipped reports. */
    val CLASS_ID_PREFIXES: Set<String> = setOf("CH202627050", "CH202627010")

    private val HEADER_CLEANUP: Regex = Regex("^[\\uFEFF\"\\s]+|[\"\\s]+\\z")
    private val WHITESPACE: Regex = Regex("""\s+""")
    private val NON_DIGITS: Regex = Regex("""[^0-9]""")

    /** Spreadsheet cell as the planner wants it: collapsed whitespace, trimmed. */
    fun cleanCell(value: String?): String = WHITESPACE.replace(value ?: "", " ").trim()

    fun isValidCode(code: String): Boolean = CODE_REGEX.matches(code)

    fun isCanonicalHeader(header: String): Boolean = header == CANONICAL_HEADER

    /**
     * RFC-4180 tokenizer — a direct port of `parseFastCSV` (`fastCsv.ts:6-81`).
     * Skips an optional UTF-8 BOM, doubles as quote escape, tolerates CRLF,
     * keeps embedded newlines inside quotes, trims every field and drops blank
     * rows. Unlike the JS version this returns the raw rows (header included)
     * so callers can inspect or re-emit them; [parseRecords] applies the
     * "fewer than two rows means empty" rule.
     */
    fun parseRows(text: String): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = if (text[0] == '\uFEFF') 1 else 0

        fun finishRow() {
            row.add(field.toString().trim())
            if (row.size > 1 || (row.size == 1 && row[0].isNotEmpty())) {
                rows.add(row.toList())
            }
            row.clear()
            field.clear()
        }

        while (i < text.length) {
            val char = text[i]
            if (inQuotes) {
                when {
                    char == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i += 2
                        continue
                    }
                    char == '"' -> inQuotes = false
                    else -> field.append(char)
                }
            } else {
                when (char) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString().trim())
                        field.clear()
                    }
                    '\r' -> Unit // ignore CR in CRLF
                    '\n' -> finishRow()
                    else -> field.append(char)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    /** Normalise a header cell the same way `parseFastCSV` does. */
    fun normaliseHeader(header: String): String =
        HEADER_CLEANUP.replace(header, "").trim()

    /**
     * Rows as header→value maps. Header cells are BOM/quote/space cleaned; an
     * empty or single-row document yields an empty list (web `parseFastCSV`).
     */
    fun parseRecords(text: String): List<Map<String, String>> {
        val rows = parseRows(text)
        if (rows.size < 2) return emptyList()
        val headers = rows[0].map(::normaliseHeader)
        return rows.drop(1).map { current ->
            headers.mapIndexed { c, key -> key to (current.getOrNull(c) ?: "") }.toMap()
        }
    }

    /** Web-parity alias (`parseFastCSV`) returning header→value records. */
    fun parseFastCsv(text: String): List<Map<String, String>> = parseRecords(text)

    /** The header line of [text] with the same normalization as [parseRecords]. */
    fun detectHeader(text: String): String? =
        parseRows(text).firstOrNull()?.joinToString(",") { normaliseHeader(it) }

    /** True when the document's header is one of [ACCEPTED_HEADERS]. */
    fun hasAcceptedHeader(text: String): Boolean = detectHeader(text) in ACCEPTED_HEADERS

    /** First non-empty value among the aliases, whitespace-collapsed. */
    private fun readText(row: Map<String, String>, vararg keys: String): String {
        for (key in keys) {
            val value = cleanCell(row[key])
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    /**
     * Header aliasing from `parseReportRows` (`ffcsReports.ts:311-344`).
     * Accepts both the canonical 10-column report and every legacy dialect;
     * rows without a CODE are dropped. TYPE is left blank when the source
     * report has no TYPE column — a blank TYPE is meaningful (winter's
     * unlabeled halves are paired by slot shape in the processor).
     */
    fun parseReportRows(text: String): List<ParsedCourse> {
        return parseRecords(text).mapNotNull { raw ->
            val clean = raw.mapKeys { (key, _) -> key.replace("\uFEFF", "").trim().uppercase() }
            val code = readText(clean, "CODE", "COURSE CODE", "COURSE_CODE").uppercase()
            if (code.isEmpty()) return@mapNotNull null
            val course = ParsedCourse(
                CODE = code,
                TITLE = readText(clean, "TITLE", "COURSE TITLE", "COURSE_TITLE"),
                TYPE = readText(clean, "TYPE"),
                CREDITS = readText(clean, "CREDITS").ifEmpty { "0" },
                ROOM = readText(clean, "VENUE", "ROOM"),
                SLOT = readText(clean, "SLOT").uppercase(),
                FACULTY = readText(clean, "FACULTY", "EMPLOYEE NAME", "EMPLOYEE_NAME"),
                BATCH = readText(clean, "BATCH").ifEmpty { null },
            )
            val classId = readText(clean, "CLASS ID", "CLASS_ID")
            val erpId = readText(clean, "ERP ID", "ERP_ID")
            val school = readText(clean, "EMPLOYEE SCHOOL", "EMPLOYEE_SCHOOL")
            course.copy(
                CLASS_ID = classId.ifEmpty { null },
                ERP_ID = erpId.ifEmpty { null },
                EMPLOYEE_SCHOOL = school.ifEmpty { null },
            )
        }
    }

    /** Alias for [parseReportRows] — the natural user-import seam. */
    fun parseReport(csvText: String): List<ParsedCourse> = parseReportRows(csvText)

    /** Short alias for [parseReportRows]. */
    fun parse(csvText: String): List<ParsedCourse> = parseReportRows(csvText)

    /** Explicitly named alias for [parseReportRows]. */
    fun parseCsv(csvText: String): List<ParsedCourse> = parseReportRows(csvText)

    /** RFC-4180 field escape: quote on comma, quote, CR or LF; double quotes. */
    fun escapeField(value: String?): String {
        val text = value ?: ""
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    /** Web-parity alias (`csvField`) for [escapeField]. */
    fun csvField(value: String?): String = escapeField(value)

    /**
     * Serialize a header plus rows. Always LF, always a trailing newline,
     * optional UTF-8 BOM. This is `convert-codechef-catalogue.mjs:322-334`
     * expressed in Kotlin.
     */
    fun writeRows(
        header: String,
        rows: Iterable<Iterable<String?>>,
        bom: Boolean = false,
    ): String = buildString {
        if (bom) append('\uFEFF')
        append(header)
        append('\n')
        for (row in rows) {
            append(row.joinToString(",") { escapeField(it) })
            append('\n')
        }
    }

    /** Emit the canonical 10-column dialect from D-171 rows. */
    fun writeReport(rows: Iterable<ParsedCourse>, bom: Boolean = false): String =
        writeRows(
            CANONICAL_HEADER,
            rows.map { row ->
                listOf(
                    row.CODE, row.TITLE, row.TYPE, row.CREDITS, row.CLASS_ID,
                    row.ROOM, row.SLOT, row.ERP_ID, row.FACULTY, row.EMPLOYEE_SCHOOL,
                )
            },
            bom = bom,
        )

    /** Short alias for [writeReport]. */
    fun write(rows: Iterable<ParsedCourse>, bom: Boolean = false): String = writeReport(rows, bom)

    /** Numeric part of a class id, or null when it has none. */
    private fun classNumber(classId: String?): Long? {
        if (classId == null) return null
        val digits = NON_DIGITS.replace(classId, "")
        return digits.toLongOrNull()
    }

    /** `+20` when theory and lab class ids differ by at most 2, `+10` up to 10. */
    fun classIdProximityBonus(theory: ParsedCourse, lab: ParsedCourse): Int {
        val a = classNumber(theory.CLASS_ID) ?: return 0
        val b = classNumber(lab.CLASS_ID) ?: return 0
        val diff = kotlin.math.abs(a - b)
        return when {
            diff <= 2 -> 20
            diff <= 10 -> 10
            else -> 0
        }
    }

    /**
     * Validation checklist from reference/05 §6. Returns error strings; an
     * empty list means the report is safe to feed back into the processor.
     *
     * Checks: CODE regex, non-empty SLOT (NIL is allowed), class-id namespace,
     * unique class ids, slot tokens known to the schema (or NIL), a pair's two
     * halves share CODE + ERP_ID, and no theory/lab clock overlap. Consecutive
     * class ids are scored by [classIdProximityBonus] rather than treated as a
     * hard error — the reference calls them a preference (`+20`), not a rule.
     */
    fun validate(
        rows: List<ParsedCourse>,
        schema: CampusSchema = FfcsSlotSchema.CHENNAI,
    ): List<String> {
        val errors = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        for (row in rows) {
            if (!isValidCode(row.CODE)) errors.add("bad CODE: ${row.CODE}")
            if (row.SLOT.isEmpty()) errors.add("empty SLOT: ${row.CODE}")
            val classId = row.CLASS_ID
            if (!classId.isNullOrEmpty()) {
                if (classId in ids) errors.add("duplicate CLASS_ID: $classId")
                ids.add(classId)
                if (CLASS_ID_PREFIXES.none { classId.startsWith(it) }) {
                    errors.add("unexpected CLASS_ID prefix: $classId")
                }
            }
            for (token in FfcsSlotSchema.splitSlots(row.SLOT)) {
                if (token == "NIL") continue
                if (FfcsSlotSchema.periodsForSlot(token, schema).isEmpty()) {
                    errors.add("unknown slot $token in ${row.CODE}")
                }
            }
        }

        // Pair checks: same CODE + ERP_ID, no overlap. A faculty's theory and
        // lab halves must carry the same ERP id wherever the report has one.
        val byFacultyAndCode = LinkedHashMap<String, MutableList<ParsedCourse>>()
        for (row in rows) {
            val key = "${row.CODE}|${row.ERP_ID ?: row.FACULTY}"
            byFacultyAndCode.getOrPut(key) { mutableListOf() }.add(row)
        }
        for ((key, group) in byFacultyAndCode) {
            val theories = group.filter { it.SLOT.isNotEmpty() && !it.SLOT.startsWith("L") && it.SLOT != "NIL" }
            val labs = group.filter { it.SLOT.startsWith("L") }
            for (theory in theories) {
                for (lab in labs) {
                    if (FfcsSlotSchema.slotsOverlap(theory.SLOT, lab.SLOT, schema)) {
                        errors.add("overlapping theory/lab in $key")
                    }
                }
            }
        }
        for ((code, group) in rows.groupBy { it.CODE }) {
            if (group.none { it.SLOT.startsWith("L") } || group.none {
                    it.SLOT.isNotEmpty() && !it.SLOT.startsWith("L") && it.SLOT != "NIL"
                }
            ) continue
            val erpIds = group.mapNotNull { it.ERP_ID }.distinct()
            if (erpIds.size > 1) errors.add("mixed ERP_ID on theory/lab of $code: ${erpIds.joinToString("/")}")
        }
        return errors
    }
}
