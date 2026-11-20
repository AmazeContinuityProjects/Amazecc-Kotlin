package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.M3EExamEntry

/**
 * D-062: real, dependency-free exam schedule exports.
 *
 * The web app downloads an HTML-rendered PNG and a print PDF. commonMain has no image/PDF codec,
 * so these writers emit standards-compliant files directly from the same immutable exam rows the
 * screen renders — no HTML snapshot, no fake placeholder content. The calendar export stays in
 * [buildExamIcs] (D-040); the per-series buttons simply pass that builder a filtered row list.
 */
internal object M3EExamScheduleExports {

    /** One human-readable block per exam; shared by both binary formats so they cannot drift. */
    fun lines(title: String, entries: List<M3EExamEntry>): List<String> {
        val out = mutableListOf(title, "AmazeCC - Exam Schedule", "")
        if (entries.isEmpty()) {
            out += "No exams available for this selection."
            return out
        }
        entries.forEach { exam ->
            out += "${exam.courseCode} - ${exam.courseTitle}"
            out += "  ${exam.examSeries.label} | ${exam.date.ifBlank { "Date TBA" }} | " +
                "${exam.session.ifBlank { "Session TBA" }} | Slot ${exam.slot.ifBlank { "TBA" }}"
            out += "  Reporting: ${exam.reportingTime.ifBlank { "TBA" }} | " +
                "Exam: ${exam.examTime.ifBlank { "TBA" }}"
            out += "  Venue: ${exam.venueDisplay.ifBlank { exam.venue }.ifBlank { "TBA" }} | " +
                "Seat: ${exam.seatNumber.ifBlank { "TBA" }}"
            out += ""
        }
        return out
    }

    fun fileName(extension: String, seriesLabel: String?): String {
        val suffix = seriesLabel
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("[^A-Z0-9]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
            ?.let { "-$it" }
            .orEmpty()
        return "amazecc-exam-schedule$suffix.$extension"
    }

    /** Minimal valid PDF 1.4 with Helvetica, one text page per 48 lines. */
    fun buildPdf(title: String, entries: List<M3EExamEntry>): ByteArray {
        val allLines = lines(title, entries)
        val pages = allLines.chunked(48)
        val pageCount = pages.size.coerceAtLeast(1)
        // Object ids: 1 catalog, 2 pages tree, 3 font, then (page, content) pairs.
        val pageIds = (0 until pageCount).map { 4 + it * 2 }
        val contentIds = (0 until pageCount).map { 5 + it * 2 }

        fun escapePdf(value: String): String = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '(' -> append("\\(")
                    ')' -> append("\\)")
                    else -> append(if (ch.code in 32..126) ch else '?')
                }
            }
        }

        val objects = linkedMapOf<Int, String>()
        objects[1] = "<< /Type /Catalog /Pages 2 0 R >>"
        objects[2] = "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count $pageCount >>"
        objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        pages.forEachIndexed { index, linesOnPage ->
            objects[pageIds[index]] =
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Resources << /Font << /F1 3 0 R >> >> /Contents ${contentIds[index]} 0 R >>"
            val stream = buildString {
                append("BT\n/F1 10 Tf\n36 812 Td\n13 TL\n")
                linesOnPage.forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) append("T*\n")
                    append("(").append(escapePdf(line)).append(") Tj\n")
                }
                append("ET\n")
            }
            objects[contentIds[index]] = "<< /Length ${stream.toByteArray().size} >>\nstream\n$stream\nendstream"
        }

        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = mutableMapOf<Int, Int>()
        objects.toSortedMap().forEach { (id, body) ->
            offsets[id] = pdf.length
            pdf.append(id).append(" 0 obj\n").append(body).append("\nendobj\n")
        }
        val xrefOffset = pdf.length
        pdf.append("xref\n0 ${objects.size + 1}\n")
        pdf.append("0000000000 65535 f \n")
        objects.keys.sorted().forEach { id ->
            pdf.append(offsets.getValue(id).toString().padStart(10, '0')).append(" 00000 n \n")
        }
        pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        pdf.append("startxref\n$xrefOffset\n%%EOF\n")
        return pdf.toString().toByteArray()
    }

    /** Minimal valid PNG (RGB, 8-bit, stored DEFLATE) with a 5x7 bitmap font for real text. */
    fun buildPng(title: String, entries: List<M3EExamEntry>): ByteArray {
        val rawLines = lines(title, entries)
        val maxChars = rawLines.maxOfOrNull { it.length.coerceAtMost(96) }?.coerceAtLeast(16) ?: 16
        val lines = rawLines.map { line ->
            val ascii = line.map { if (it.code in 32..126) it else '?' }.joinToString("")
            if (ascii.length <= maxChars) ascii else ascii.take(maxChars - 2) + ".."
        }

        val scale = 2
        val margin = 12
        val charWidth = 6 * scale
        val lineHeight = 10 * scale
        val width = margin * 2 + maxChars * charWidth
        val height = margin * 2 + lines.size * lineHeight
        val pixels = ByteArray(width * height * 3) { 0xFF.toByte() } // white background

        fun setPixel(x: Int, y: Int, r: Int, g: Int, b: Int) {
            if (x !in 0 until width || y !in 0 until height) return
            val offset = (y * width + x) * 3
            pixels[offset] = r.toByte()
            pixels[offset + 1] = g.toByte()
            pixels[offset + 2] = b.toByte()
        }

        lines.forEachIndexed { lineIndex, line ->
            line.forEachIndexed { charIndex, ch ->
                val glyph = glyph(ch)
                glyph.forEachIndexed { row, bits ->
                    bits.forEachIndexed { col, bit ->
                        if (bit != '#') return@forEachIndexed
                        val x0 = margin + (charIndex * 6 + col) * scale
                        val y0 = margin + (lineIndex * 10 + row) * scale
                        for (dx in 0 until scale) {
                            for (dy in 0 until scale) {
                                setPixel(x0 + dx, y0 + dy, 23, 33, 53)
                            }
                        }
                    }
                }
            }
        }
        return encodePng(width, height, pixels)
    }

    // ---- PNG plumbing -------------------------------------------------------------------

    private fun encodePng(width: Int, height: Int, rgb: ByteArray): ByteArray {
        val raw = ByteArray(height * (1 + width * 3))
        var cursor = 0
        for (row in 0 until height) {
            raw[cursor++] = 0 // filter type: None
            val rowStart = row * width * 3
            rgb.copyInto(raw, destinationOffset = cursor, startIndex = rowStart, endIndex = rowStart + width * 3)
            cursor += width * 3
        }
        val zlib = byteArrayOf(0x78, 0x01) + deflateStored(raw) + adler32(raw)
        val out = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdr = ByteArray(13)
        writeInt(ihdr, 0, width)
        writeInt(ihdr, 4, height)
        ihdr[8] = 8 // bit depth
        ihdr[9] = 2 // truecolour RGB
        ihdr[10] = 0 // compression
        ihdr[11] = 0 // filter
        ihdr[12] = 0 // interlace
        return out + chunk("IHDR", ihdr) + chunk("IDAT", zlib) + chunk("IEND", ByteArray(0))
    }

    /** Stored (uncompressed) DEFLATE blocks — valid zlib payload with no platform dependency. */
    private fun deflateStored(raw: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        var offset = 0
        while (offset < raw.size) {
            val length = minOf(65_535, raw.size - offset)
            val final = offset + length == raw.size
            out.add(if (final) 1 else 0) // BFINAL=1/0, BTYPE=00
            out.add((length and 0xFF).toByte())
            out.add(((length shr 8) and 0xFF).toByte())
            val inverse = length.inv() and 0xFFFF
            out.add((inverse and 0xFF).toByte())
            out.add(((inverse shr 8) and 0xFF).toByte())
            for (i in offset until offset + length) out.add(raw[i])
            offset += length
        }
        if (raw.isEmpty()) out.add(1)
        return out.toByteArray()
    }

    private fun adler32(data: ByteArray): ByteArray {
        var a = 1
        var b = 0
        data.forEach { byte ->
            a = (a + (byte.toInt() and 0xFF)) % 65_521
            b = (b + a) % 65_521
        }
        val value = (b shl 16) or a
        return byteArrayOf(
            (value ushr 24).toByte(), (value ushr 16).toByte(),
            (value ushr 8).toByte(), value.toByte()
        )
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        val typeBytes = type.toByteArray()
        typeBytes.copyInto(out, 4)
        data.copyInto(out, 8)
        val crcInput = ByteArray(4 + data.size)
        typeBytes.copyInto(crcInput, 0)
        data.copyInto(crcInput, 4)
        writeInt(out, 8 + data.size, crc32(crcInput))
        return out
    }

    private val crcTable = IntArray(256).also { table ->
        for (n in 0 until 256) {
            var c = n
            repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
            table[n] = c
        }
    }

    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        data.forEach { byte -> crc = crcTable[(crc xor (byte.toInt() and 0xFF)) and 0xFF] xor (crc ushr 8) }
        return crc xor 0xFFFFFFFF.toInt()
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    // ---- 5x7 glyphs ----------------------------------------------------------------------

    private val glyphs: Map<Char, List<String>> = buildMap {
        fun add(ch: Char, vararg rows: String) { put(ch, rows.toList()) }
        add('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        add('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.")
        add('C', ".####", "#....", "#....", "#....", "#....", "#....", ".####")
        add('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.")
        add('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####")
        add('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....")
        add('G', ".####", "#....", "#....", "#.###", "#...#", "#...#", ".###.")
        add('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        add('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####")
        add('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..")
        add('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#")
        add('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####")
        add('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#")
        add('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#")
        add('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        add('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....")
        add('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#")
        add('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#")
        add('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.")
        add('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..")
        add('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        add('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..")
        add('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#")
        add('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#")
        add('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..")
        add('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####")
        add('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.")
        add('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", "#####")
        add('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####")
        add('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.")
        add('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.")
        add('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.")
        add('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.")
        add('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...")
        add('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.")
        add('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..")
        add(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....")
        add('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....")
        add('.', ".....", ".....", ".....", ".....", ".....", "..#..", ".....")
        add(':', ".....", "..#..", ".....", ".....", "..#..", ".....", ".....")
        add('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....")
        add(',', ".....", ".....", ".....", ".....", "..#..", ".#...", ".....")
        add('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.")
        add(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...")
        add('|', "..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..")
        add('>', "#....", ".#...", "..#..", "...#.", "..#..", ".#...", "#....")
        add('=', ".....", "#####", ".....", "#####", ".....", ".....", ".....")
        add('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..")
    }

    private fun glyph(ch: Char): List<String> =
        glyphs[ch.uppercaseChar()] ?: glyphs.getValue('?')
}
