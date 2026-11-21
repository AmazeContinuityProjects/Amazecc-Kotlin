package com.amazecc.app.shared.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-146: standalone client-side HTML exports are well formed and cannot inject markup. */
class HtmlExportTest {

    @Test
    fun simple_html_has_standalone_document_structure() {
        val html = buildSimpleHtml("Student Report", listOf("Student: Aravind", "CGPA: 9.24"))

        assertTrue(html.startsWith("<!DOCTYPE html>"), html)
        assertTrue(html.contains("<html lang=\"en\">"), html)
        assertTrue(html.contains("<meta charset=\"UTF-8\">"), html)
        assertTrue(html.contains("<title>Student Report</title>"), html)
        assertTrue(html.contains("<h1>Student Report</h1>"), html)
        assertTrue(html.contains("<pre>"), html)
        assertTrue(html.contains("Student: Aravind"), html)
        assertTrue(html.contains("CGPA: 9.24"), html)
        assertTrue(html.trimEnd().endsWith("</html>"), html)
    }

    @Test
    fun escape_html_covers_all_five_special_characters() {
        assertEquals("&amp;", escapeHtml("&"))
        assertEquals("&lt;", escapeHtml("<"))
        assertEquals("&gt;", escapeHtml(">"))
        assertEquals("&quot;", escapeHtml("\""))
        assertEquals("&#39;", escapeHtml("'"))
        assertEquals("&lt;&amp;&gt;&quot;&#39;", escapeHtml("<&>\"'"))
        assertEquals("plain text 123", escapeHtml("plain text 123"))
    }

    @Test
    fun simple_html_escapes_every_interpolated_value() {
        val injected = "<script>alert('x')</script> & \"quoted\""
        val html = buildSimpleHtml(injected, listOf(injected, "Safe <b>bold</b>"))

        assertFalse(html.contains(injected), "raw injected string leaked into the document")
        assertFalse(html.contains("<script>"), "a raw script tag leaked into the document")
        assertFalse(html.contains("<b>bold</b>"), "a raw markup snippet leaked into the document")
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;quoted&quot;"), html)
        assertTrue(html.contains("Safe &lt;b&gt;bold&lt;/b&gt;"), html)
    }

    @Test
    fun table_html_contains_headers_and_cells() {
        val html = buildTableHtml(
            title = "FFCS Dossier",
            headers = listOf("Course", "Slots"),
            rows = listOf(
                listOf("CSE3002", "A1+B2"),
                listOf("MAT2001", "C1")
            )
        )

        assertTrue(html.startsWith("<!DOCTYPE html>"), html)
        assertTrue(html.contains("<title>FFCS Dossier</title>"), html)
        assertTrue(html.contains("<table>"), html)
        assertTrue(html.contains("<th>Course</th>"), html)
        assertTrue(html.contains("<th>Slots</th>"), html)
        assertTrue(html.contains("<td>CSE3002</td>"), html)
        assertTrue(html.contains("<td>A1+B2</td>"), html)
        assertTrue(html.contains("<td>MAT2001</td>"), html)
        assertTrue(html.contains("<td>C1</td>"), html)
    }

    @Test
    fun table_html_escapes_headers_and_cells() {
        val header = "<img src=x>"
        val cell = "Danger & 'quote' \"double\""
        val html = buildTableHtml("Dossier <title>", listOf(header), listOf(listOf(cell)))

        assertFalse(html.contains(header), "raw header markup leaked into the table")
        assertFalse(html.contains(cell), "raw cell text with markup characters leaked into the table")
        assertFalse(html.contains("Dossier <title>"), "raw title leaked into the document")
        assertTrue(html.contains("&lt;img src=x&gt;"), html)
        assertTrue(html.contains("Danger &amp; &#39;quote&#39; &quot;double&quot;"), html)
        assertTrue(html.contains("&lt;title&gt;"), html)
    }
}
