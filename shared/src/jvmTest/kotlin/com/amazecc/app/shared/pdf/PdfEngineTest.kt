package com.amazecc.app.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-148: structural validation of the shared PDF engine.
 *
 * The parser works on the raw byte array decoded 1:1 to chars (`byte & 0xFF`), never on
 * `ByteArray.decodeToString()`: JPEG XObjects are binary, and a decoded string would substitute
 * replacement characters and destroy the byte positions the xref table points at.
 */
class PdfEngineTest {

    private fun ByteArray.ascii(): String {
        val sb = StringBuilder(size)
        for (i in indices) sb.append((this[i].toInt() and 0xFF).toChar())
        return sb.toString()
    }

    /** SOI + SOF0 + EOI — enough for the header parser; no pixel decoding is attempted. */
    private fun jpegFixture(width: Int, height: Int, components: Int): ByteArray {
        val compBytes = ByteArray(components * 3) { i ->
            when (i % 3) {
                0 -> (i / 3 + 1).toByte() // component id
                1 -> 0x11.toByte()        // sampling
                else -> 0.toByte()        // quant table
            }
        }
        val sofLength = 2 + 1 + 2 + 2 + 1 + compBytes.size
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),                                  // SOI
            0xFF.toByte(), 0xC0.toByte(),                                  // SOF0
            (sofLength ushr 8).toByte(), (sofLength and 0xFF).toByte(),
            0x08.toByte(),                                                 // precision
            (height ushr 8).toByte(), (height and 0xFF).toByte(),
            (width ushr 8).toByte(), (width and 0xFF).toByte(),
            components.toByte()
        ) + compBytes + byteArrayOf(0xFF.toByte(), 0xD9.toByte())          // EOI
    }

    private fun assertValidPdf(pdf: ByteArray): String {
        val s = pdf.ascii()
        assertTrue(s.startsWith("%PDF-1.4\n"), "missing PDF 1.4 header")
        assertTrue(s.endsWith("%%EOF\n"), "missing %%EOF terminator")

        val sx = s.lastIndexOf("startxref\n")
        assertTrue(sx >= 0, "missing startxref")
        val afterSx = sx + "startxref\n".length
        val lineEnd = s.indexOf('\n', afterSx)
        assertTrue(lineEnd > afterSx, "malformed startxref value")
        val xrefOffset = s.substring(afterSx, lineEnd).trim().toInt()

        assertTrue(s.startsWith("xref\n", xrefOffset), "startxref does not point at the xref table")
        // Search AFTER the newline in "xref\n" itself, otherwise we find that one and
        // produce a substring range [xrefOffset+5, xrefOffset+4).
        val headerEnd = s.indexOf('\n', xrefOffset + "xref\n".length)
        val subsection = s.substring(xrefOffset + "xref\n".length, headerEnd).trim()
        val parts = subsection.split(' ')
        assertEquals(2, parts.size, "malformed xref subsection header: $subsection")
        assertEquals(0, parts[0].toInt())
        val count = parts[1].toInt()

        for (i in 0 until count) {
            val lineStart = headerEnd + 1 + i * 20
            val entry = s.substring(lineStart, lineStart + 20)
            val offset = entry.substring(0, 10).toInt()
            val generation = entry.substring(11, 16)
            val type = entry[17]
            val id = i // subsection starts at 0
            if (id == 0) {
                assertEquals('f', type, "free entry 0")
                assertEquals("65535", generation)
                assertEquals(0, offset)
            } else {
                assertEquals('n', type, "entry $id must be in-use")
                assertEquals("00000", generation, "entry $id generation")
                assertTrue(
                    s.startsWith("$id 0 obj\n", offset),
                    "xref offset for object $id points at '${s.substring(offset, minOf(offset + 16, s.length))}'"
                )
            }
        }
        assertTrue(
            s.contains("trailer\n<< /Size $count /Root 1 0 R >>\n"),
            "trailer /Size must equal xref subsection count"
        )
        return s
    }

    private fun pageCount(s: String): Int {
        val match = Regex("/Type /Pages /Kids \\[([^\\]]*)\\] /Count (\\d+)").find(s)
            ?: error("no /Pages tree in document")
        // Kids are object references like "4 0 R"; count those, not the space-separated
        // tokens ("4", "0", "R") which would triple the perceived page count.
        val kids = Regex("\\d+ 0 R").findAll(match.groupValues[1]).count()
        assertEquals(kids, match.groupValues[2].toInt(), "/Count disagrees with /Kids")
        return kids
    }

    // ---- Text writer ----------------------------------------------------------------------

    @Test
    fun text_pdf_has_valid_structure_and_uncompressed_real_data() {
        val lines = listOf(
            "Exam Schedule",
            "AmazeCC - Exam Schedule",
            "",
            "CSE3002 - Internet and Web Programming"
        )
        val pdf = PdfEngine.buildTextPdf("Exam Schedule", lines)
        val s = assertValidPdf(pdf)

        assertEquals(1, pageCount(s))
        assertTrue(s.contains("BT\n/F1 10 Tf\n36 812 Td\n13 TL\n"), "stream prefix")
        assertTrue(s.contains("/MediaBox [0 0 595 842]"))
        assertTrue(s.contains("T*\n"), "line advance operator")
        assertTrue(s.contains("(CSE3002 - Internet and Web Programming) Tj\n"), "raw exam data")
        assertTrue(s.contains("(AmazeCC - Exam Schedule) Tj\n"))
        assertTrue(s.contains("ET\n"))
        assertFalse(s.contains("/FlateDecode"), "content streams must not be compressed")
        assertFalse(s.contains("/Info"), "legacy byte-compatible layout has no Info dictionary")
    }

    @Test
    fun title_is_metadata_only_and_is_never_drawn() {
        val pdf = PdfEngine.buildTextPdf("Document Title", listOf("Body line"))
        val s = assertValidPdf(pdf)
        assertTrue(s.contains("(Body line) Tj\n"))
        assertFalse(s.contains("(Document Title) Tj"), "title must not be prepended as a line")
    }

    @Test
    fun text_pdf_honours_options_and_paginates() {
        val lines = (1..100).map { "Line $it" }
        val pdf = PdfEngine.buildTextPdf(
            "Report",
            lines,
            PdfTextOptions(fontSize = 11, leading = 15, startX = 40, startY = 800, linesPerPage = 48)
        )
        val s = assertValidPdf(pdf)
        assertEquals(3, pageCount(s)) // 100 lines / 48 -> 3 pages
        assertTrue(s.contains("BT\n/F1 11 Tf\n40 800 Td\n15 TL\n"))
        assertTrue(s.contains("/Kids [4 0 R 6 0 R 8 0 R] /Count 3"))
        assertTrue(s.contains("(Line 100) Tj"))
    }

    @Test
    fun escape_pdf_literal_escapes_and_keeps_ascii_invariant() {
        assertEquals("a\\\\b\\(c\\)d", PdfEngine.escapePdfLiteral("a\\b(c)d"))
        assertEquals("plain 123", PdfEngine.escapePdfLiteral("plain 123"))
        // One output char per input char — the invariant that keeps char counts == byte counts.
        val folded = PdfEngine.escapePdfLiteral("héllo—\n\t•")
        assertEquals("h?llo????", folded)
        assertEquals("héllo—\n\t•".length, folded.length)
        assertTrue(folded.all { it.code in 32..126 || it == '\\' })
    }

    @Test
    fun sanitize_win_ansi_folds_smart_punctuation() {
        assertEquals("-", PdfEngine.sanitizeWinAnsi("\u2014"))
        assertEquals("-", PdfEngine.sanitizeWinAnsi("\u2013"))
        assertEquals("'", PdfEngine.sanitizeWinAnsi("\u2019"))
        assertEquals("\"", PdfEngine.sanitizeWinAnsi("\u201C"))
        assertEquals("...", PdfEngine.sanitizeWinAnsi("\u2026"))
        assertEquals("*", PdfEngine.sanitizeWinAnsi("\u2022"))
        assertEquals(" ", PdfEngine.sanitizeWinAnsi("\u00A0"))
        assertEquals("a-b \"q\" ... x y", PdfEngine.sanitizeWinAnsi("a\u2014b \u201Cq\u201D \u2026 x\u00A0y"))
        assertEquals("cafe", PdfEngine.sanitizeWinAnsi("caf\u00E9"))
    }

    @Test
    fun wrap_text_preserves_paragraphs_and_measured_widths() {
        assertEquals(listOf("first", "", "second"), PdfEngine.wrapText("first\n\nsecond", 80.0, 10.0))
        assertTrue(PdfEngine.wrapText("", 50.0, 10.0).isEmpty())

        val wrapped = PdfEngine.wrapText("one two three four five six seven eight", 28.0, 10.0)
        assertTrue(wrapped.size > 1, "expected wrapping at 28mm")
        assertEquals(
            "one two three four five six seven eight",
            wrapped.joinToString(" "),
            "wrapping must not lose or duplicate words"
        )
        val limitPt = 28.0 * (595.0 / 210.0)
        wrapped.forEach { line ->
            assertTrue(PdfEngine.getTextWidth(line, 10.0) <= limitPt + 0.001, "line overruns box: $line")
        }
    }

    @Test
    fun fit_one_line_shrinks_or_truncates_to_fit() {
        val fits = PdfEngine.fitOneLine("Short", 40.0, 12.0, 6.0)
        assertEquals(listOf("Short"), fits)

        val shrunk = PdfEngine.fitOneLine("A considerably longer report heading", 18.0, 12.0, 6.0)
        assertEquals(1, shrunk.size)
        assertTrue(PdfEngine.getTextWidth(shrunk[0], 6.0) <= 18.0 * (595.0 / 210.0) + 0.001)
    }

    // ---- JPEG inspection -------------------------------------------------------------------

    @Test
    fun jpeg_header_parser_reads_dimensions_and_components() {
        val gray = jpegFixture(16, 16, 1)
        assertTrue(PdfEngine.isJpeg(gray))
        assertEquals(PdfJpegInfo(16, 16, 1), PdfEngine.readJpegInfo(gray, 8, 8))

        val rgb = jpegFixture(32, 24, 3)
        assertEquals(PdfJpegInfo(32, 24, 3), PdfEngine.readJpegInfo(rgb, 8, 8))

        assertFalse(PdfEngine.isJpeg(byteArrayOf(1, 2, 3, 4)))
        assertEquals(PdfJpegInfo(9, 7, 3), PdfEngine.readJpegInfo(byteArrayOf(1, 2, 3, 4), 9, 7))
    }

    // ---- Image writer / multi-page scans ---------------------------------------------------

    @Test
    fun image_pdf_has_fixed_layout_and_real_clip() {
        val jpeg = jpegFixture(16, 16, 1)
        val pdf = PdfEngine.buildImagePdf(jpeg, PdfCrop.FULL, 16, 16, "Scan", "camera roll")
        val s = assertValidPdf(pdf)

        assertEquals(1, pageCount(s))
        assertTrue(s.contains("/DCTDecode"))
        assertTrue(s.contains("/Im0 Do"))
        assertTrue(s.contains("re W n"), "crop must be a real clipping rectangle")
        assertTrue(s.contains("/DeviceGray"), "1-component JPEG -> DeviceGray")
        assertTrue(s.contains("/Length ${jpeg.size + 1}"))
        assertTrue(s.contains("(Scan) Tj"))
        assertTrue(s.contains("(camera roll) Tj"))
        assertTrue(s.contains("1 0 0 1 0 0 cm"), "FULL crop has no translation")
        assertTrue(s.contains("trailer\n<< /Size 7 /Root 1 0 R >>\n"), "fixed 1..6 object layout")
    }

    @Test
    fun image_pdf_applies_partial_crop_and_rgb_colour_space() {
        val jpeg = jpegFixture(32, 32, 3)
        val crop = PdfCrop(0.1f, 0.1f, 0.9f, 0.9f)
        val pdf = PdfEngine.buildImagePdf(jpeg, crop, 32, 32, "Cropped", "")
        val s = assertValidPdf(pdf)
        assertTrue(s.contains("/DeviceRGB"))
        assertTrue(s.contains("re W n"))
        assertFalse(s.contains("1 0 0 1 0 0 cm"), "partial crop must translate the image")
        assertFalse(s.contains("(note) Tj"), "blank note draws nothing")
    }

    @Test
    fun multi_page_scan_reports_progress_and_builds_all_pages() {
        val jpeg = jpegFixture(32, 32, 3)
        val pages = (1..3).map { PdfImagePage(jpeg, PdfCrop.FULL, 32, 32, "Page $it", "") }
        val progress = mutableListOf<Pair<Int, Int>>()
        val pdf = PdfEngine.buildImagePdfPages(pages) { current, total -> progress += current to total }
        val s = assertValidPdf(pdf)

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(3, pageCount(s))
        assertEquals(3, Regex("/Im0 Do").findAll(s).count())
        assertEquals(3, Regex("/DCTDecode").findAll(s).count())
        assertTrue(s.contains("(Page 1) Tj"))
        assertTrue(s.contains("(Page 3) Tj"))
        assertTrue(s.contains("trailer\n<< /Size 13 /Root 1 0 R >>\n"), "3 + 3*3 objects + 1")
    }

    @Test
    fun empty_scan_list_still_produces_a_valid_document() {
        val progress = mutableListOf<Pair<Int, Int>>()
        val pdf = PdfEngine.buildImagePdfPages(emptyList()) { c, t -> progress += c to t }
        val s = assertValidPdf(pdf)
        assertEquals(0, pageCount(s))
        assertTrue(progress.isEmpty())
    }

    // ---- Canvas ---------------------------------------------------------------------------

    @Test
    fun canvas_emits_valid_multipage_pdf_with_real_shapes_and_table() {
        val canvas = PdfCanvas(title = "Metadata only")
        canvas.text("Quarterly Report", 16f, 20f, sizePt = 18f, bold = true, color = PdfColor.ACCENT)
        canvas.rect(16f, 26f, 178f, 0.6f, fill = PdfColor.ACCENT)
        canvas.roundedRect(16f, 32f, 60f, 20f, 4f, fill = PdfColor.LIGHT_GRAY, stroke = PdfColor.GRAY)
        canvas.circle(150f, 42f, 8f, fill = PdfColor.ACCENT)
        canvas.triangle(20f, 60f, 32f, 60f, 26f, 70f, fill = PdfColor.ACCENT)
        canvas.line(16f, 75f, 194f, 75f, dashed = true)
        canvas.chunkedTable(
            columns = listOf(PdfColumn(60f), PdfColumn(60f), PdfColumn(58f)),
            headers = listOf("Code", "Title", "Marks"),
            rows = (1..60).map { listOf("CSE$it", "Course $it", "$it") }
        )
        canvas.footerAtEnd("AmazeCC - internal")

        val pdf = canvas.build()
        val s = assertValidPdf(pdf)
        assertTrue(pageCount(s) >= 2, "60 table rows must flow onto a second page")
        assertTrue(s.contains("/F2 18 Tf"), "bold font resource")
        assertTrue(s.contains(" c\n"), "Bezier arcs for rounded rect / circle")
        assertTrue(s.contains(" re\n"), "real rectangle operator")
        assertTrue(s.contains(" l\n"), "real line/triangle operators")
        assertTrue(s.contains("[2 2] 0 d\n"), "dashed line")
        assertTrue(s.contains("(Code) Tj"), "table headers")
        assertTrue(s.contains("(CSE60) Tj"), "last table row")
        assertTrue(s.contains("(AmazeCC - internal) Tj"), "footer on final page")
        assertFalse(s.contains("(Metadata only) Tj"), "canvas title is metadata only")
    }

    @Test
    fun canvas_new_page_and_room_control_pagination() {
        val canvas = PdfCanvas()
        assertTrue(canvas.room(100f))
        canvas.text("Page one", 16f, 20f)
        canvas.newPage()
        assertFalse(canvas.room(270f), "only 281mm of content area is available")
        canvas.text("Page two", 16f, 20f)
        val s = assertValidPdf(canvas.build())
        assertEquals(2, pageCount(s))
        assertTrue(s.contains("(Page one) Tj"))
        assertTrue(s.contains("(Page two) Tj"))
    }
}
