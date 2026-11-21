package com.amazecc.app.shared.utils

/**
 * Minimal, dependency-free HTML export for the Kotlin/Android app (web H2/H3 parity).
 *
 * Everything is generated client-side: no serializer, no template engine, no network call.
 * Every value interpolated into the document passes through [escapeHtml], so caller-supplied
 * text (which may originate from a scraped VTOP page) can never inject markup or script.
 */

/** Escapes the five characters that have a special meaning in HTML text/attribute contexts. */
fun escapeHtml(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

private val DOCUMENT_CSS = """
    body { font-family: system-ui, -apple-system, sans-serif; margin: 24px; line-height: 1.5; color: #1a1a1a; }
    h1 { font-size: 1.6rem; margin: 0 0 16px; }
    pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; }
    table { border-collapse: collapse; width: 100%; margin-top: 12px; }
    th, td { border: 1px solid #cccccc; padding: 8px; text-align: left; vertical-align: top; }
    th { background: #f2f2f2; }
""".trimIndent()

/** Shared standalone UTF-8 HTML5 shell. [body] is already-escaped markup from a builder below. */
private fun buildHtmlDocument(title: String, body: String): String {
    val safeTitle = escapeHtml(title)
    return buildString {
        append("<!DOCTYPE html>\n")
        append("<html lang=\"en\">\n")
        append("<head>\n")
        append("<meta charset=\"UTF-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        append("<title>").append(safeTitle).append("</title>\n")
        append("<style>\n").append(DOCUMENT_CSS).append("\n</style>\n")
        append("</head>\n")
        append("<body>\n")
        append("<h1>").append(safeTitle).append("</h1>\n")
        append(body)
        if (!body.endsWith("\n")) append("\n")
        append("</body>\n")
        append("</html>\n")
    }
}

/** A complete HTML document rendering caller-provided [lines] as preformatted text. */
fun buildSimpleHtml(title: String, lines: List<String>): String {
    val body = buildString {
        append("<pre>\n")
        lines.forEach { append(escapeHtml(it)).append('\n') }
        append("</pre>\n")
    }
    return buildHtmlDocument(title, body)
}

/** A complete HTML document rendering caller-provided [headers] and [rows] as a real table. */
fun buildTableHtml(
    title: String,
    headers: List<String>,
    rows: List<List<String>>
): String {
    val body = buildString {
        append("<table>\n")
        append("<thead>\n<tr>\n")
        headers.forEach { append("<th>").append(escapeHtml(it)).append("</th>\n") }
        append("</tr>\n</thead>\n")
        append("<tbody>\n")
        rows.forEach { row ->
            append("<tr>\n")
            row.forEach { cell -> append("<td>").append(escapeHtml(cell)).append("</td>\n") }
            append("</tr>\n")
        }
        append("</tbody>\n</table>\n")
    }
    return buildHtmlDocument(title, body)
}
