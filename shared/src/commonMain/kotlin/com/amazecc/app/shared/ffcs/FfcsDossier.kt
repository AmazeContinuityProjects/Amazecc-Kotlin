package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.utils.buildTableHtml
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * D-182 — standalone HTML dossier for one FFCS timetable.
 *
 * Delegates the document shell to [buildTableHtml], which runs every header and cell through
 * `HtmlExport.escapeHtml` (`reference/06-import-export-share.md:392-394`). Every cell is real data
 * from [TimetableState.courses]; there are no placeholder rows. No script, no `@page`, no network —
 * the host saves/shares the returned UTF-8 string.
 */
fun buildFfcsDossierHtml(timetable: TimetableState): String {
    val headers = listOf("CODE", "COURSE", "TYPE", "CR", "SLOTS", "VENUE", "FACULTY")
    val rows = timetable.courses.map { course ->
        listOf(
            course.code,
            course.title,
            course.type,
            course.credits,
            course.slots.joinToString("+").ifEmpty { "NIL" },
            course.venue,
            course.faculty,
        )
    }
    val heading = timetable.name.trim().ifEmpty { "Untitled timetable" }
    return buildTableHtml("FFCS Dossier - $heading", headers, rows)
}

/**
 * A real, dated, ASCII-safe filename for an FFCS export: `AmazeCC-FFCS-YYYY-MM-DD.<ext>`.
 *
 * The date comes from the caller's real `epochMillis` (UTC, like `ReportSerializers.filename`),
 * and the extension is sanitised to `[a-z0-9]` with `txt` as the fallback, so no exotic format
 * string can leak path separators or non-ASCII characters into a filename.
 */
fun ffcsExportFileName(format: String, epochMillis: Long): String {
    val ext = buildString(format.length) {
        format.lowercase().forEach { ch ->
            if (ch in 'a'..'z' || ch in '0'..'9') append(ch)
        }
    }.ifEmpty { "txt" }
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
    return "AmazeCC-FFCS-$date.$ext"
}
