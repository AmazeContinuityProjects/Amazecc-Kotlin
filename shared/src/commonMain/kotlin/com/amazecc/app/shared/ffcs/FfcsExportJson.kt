package com.amazecc.app.shared.ffcs

/**
 * D-182 — pure FFCS timetable JSON serializer.
 *
 * Mirrors the `onExportTimetablesJson` surface (`reference/06-import-export-share.md:250-269`) and
 * the schema/escape discipline of `ReportSerializers.json`: a stable schema marker plus the real
 * timetables, with correct JSON escaping and verbatim UTF-8 (never '?'-folded). No clock and no
 * I/O — the host supplies bytes to the save/share seam.
 */
object FfcsExportJson {

    /** Stable marker so consumers can validate the payload shape. */
    const val SCHEMA = "amazecc.ffcs.timetables.v1"

    /** Serialize [timetables] to a deterministic, UTF-8-ready JSON document. */
    fun build(timetables: List<TimetableState>): String = buildString {
        append("{\n")
        append("  \"schema\": ").append(quote(SCHEMA)).append(",\n")
        append("  \"count\": ").append(timetables.size).append(",\n")
        append("  \"timetables\": [")
        if (timetables.isEmpty()) {
            append("]\n")
        } else {
            append('\n')
            timetables.forEachIndexed { index, timetable ->
                append("    {\n")
                append("      \"id\": ").append(quote(timetable.id)).append(",\n")
                append("      \"name\": ").append(quote(timetable.name)).append(",\n")
                append("      \"metrics\": {\n")
                append("        \"halfDays\": ").append(timetable.metrics.halfDays).append(",\n")
                append("        \"gaps\": ").append(timetable.metrics.gaps).append('\n')
                append("      },\n")
                append("      \"firstStartMin\": ").append(timetable.firstStartMin).append(",\n")
                append("      \"lastEndMin\": ").append(timetable.lastEndMin).append(",\n")
                append("      \"courses\": [")
                if (timetable.courses.isEmpty()) {
                    append("]\n")
                } else {
                    append('\n')
                    timetable.courses.forEachIndexed { cIndex, course ->
                        append("        {\n")
                        append("          \"id\": ").append(quote(course.id)).append(",\n")
                        append("          \"code\": ").append(quote(course.code)).append(",\n")
                        append("          \"title\": ").append(quote(course.title)).append(",\n")
                        append("          \"type\": ").append(quote(course.type)).append(",\n")
                        append("          \"credits\": ").append(quote(course.credits)).append(",\n")
                        append("          \"slots\": [")
                        append(course.slots.joinToString(", ") { quote(it) })
                        append("],\n")
                        append("          \"venue\": ").append(quote(course.venue)).append(",\n")
                        append("          \"faculty\": ").append(quote(course.faculty)).append('\n')
                        append("        }")
                        if (cIndex < timetable.courses.lastIndex) append(',')
                        append('\n')
                    }
                    append("      ]\n")
                }
                append("    }")
                if (index < timetables.lastIndex) append(',')
                append('\n')
            }
            append("  ]\n")
        }
        append("}\n")
    }

    /**
     * Correct JSON string escaper: quotes, backslash, backspace/form-feed/newline/return/tab and
     * all other C0 controls; non-ASCII is emitted verbatim so UTF-8 survives the round-trip.
     */
    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}

/** Convenience top-level alias matching the export-ladder API name. */
fun buildFfcsJson(timetables: List<TimetableState>): String = FfcsExportJson.build(timetables)
