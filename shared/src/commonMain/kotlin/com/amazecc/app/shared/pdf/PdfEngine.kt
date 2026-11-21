package com.amazecc.app.shared.pdf

/**
 * D-148 — the single, dependency-free PDF engine for `commonMain`.
 *
 * Two responsibilities live here:
 *
 *  1. **Byte-compatible replacements** for the three hand-written writers that used to live in
 *     `M3EExamScheduleExports`, `M3ECampusLegacyBridge` and `DocumentScannerPdf`. [buildTextPdf]
 *     reproduces the text writer layout exactly (object ids `1/2/3` + `4+2i`/`5+2i`, Helvetica +
 *     WinAnsi, uncompressed literal strings, 10-digit xref offsets), and [buildImagePdf]
 *     reproduces the scanner's fixed `1..6` layout with a raw `/DCTDecode` XObject. Both route
 *     through the same [PdfDocumentBuilder] serializer, so the byte offsets in the xref table are
 *     always real offsets into the emitted byte array (not `StringBuilder` character counts that
 *     happen to be correct only while every character is ASCII).
 *
 *  2. **Rich primitives** for the web-style reports: [wrapText], [getTextWidth], [fitOneLine],
 *     WinAnsi folding, aspect-fit image placement with a real clipping rectangle, multi-page scan
 *     assembly with a progress callback, and an mm-based page geometry shared with [PdfCanvas].
 *
 * Nothing here uses `java.*`, `android.*`, platform image decoders or stream compression. The JPEG
 * is embedded verbatim; only its SOF header is inspected to choose `/DeviceGray` vs `/DeviceRGB`.
 */

/** Top-left-origin crop in normalised image coordinates (all values 0f..1f). */
data class PdfCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        /** The whole image: `(0, 0, 1, 1)`. */
        val FULL: PdfCrop = PdfCrop(0f, 0f, 1f, 1f)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Knobs that distinguish the legacy text writers (10pt/13pt exam vs 11pt/15pt report). */
data class PdfTextOptions(
    val fontSize: Int = 10,
    val leading: Int = 13,
    val startX: Int = 36,
    val startY: Int = 812,
    val linesPerPage: Int = 48
)

/** One scan page: a verbatim JPEG, its crop, and the title/note drawn above the image. */
data class PdfImagePage(
    val jpeg: ByteArray,
    val crop: PdfCrop = PdfCrop.FULL,
    val imageWidth: Int,
    val imageHeight: Int,
    val title: String,
    val note: String = ""
)

/** SOF header summary used to size the image XObject and pick its colour space. */
data class PdfJpegInfo(val width: Int, val height: Int, val components: Int)

object PdfEngine {

    // ---- Geometry ------------------------------------------------------------------------

    /** The legacy writers use integer A4: 210mm -> 595pt, 297mm -> 842pt. */
    internal const val PAGE_WIDTH_PT: Int = 595
    internal const val PAGE_HEIGHT_PT: Int = 842
    internal const val MM_TO_PT: Double = 595.0 / 210.0

    internal fun mmToPt(mm: Float): Float = (mm * MM_TO_PT).toFloat()
    internal fun ptToMm(pt: Double): Float = (pt / MM_TO_PT).toFloat()

    /**
     * Stable 3-decimal number formatting shared by the writers and [PdfCanvas]. Truncation (not
     * rounding) matches the original scanner writer, so image PDFs stay byte-identical.
     */
    internal fun pdfNum(value: Float): String {
        val rounded = (value * 1000).toLong() / 1000f
        return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
    }

    // ---- Text helpers ---------------------------------------------------------------------

    /**
     * Escapes a PDF literal string. Backslash, `(` and `)` get literal escapes; every character
     * outside printable ASCII is replaced with `?`. The replacement keeps **one output character
     * per input character**, which is what makes byte offsets and ASCII `/Length` values safe for
     * the legacy writers (character count == UTF-8 byte count).
     */
    fun escapePdfLiteral(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                else -> append(if (ch.code in 32..126) ch else '?')
            }
        }
    }

    /**
     * Folds typographic Unicode into the ASCII subset that survives `WinAnsiEncoding` plus
     * [escapePdfLiteral]. Smart punctuation is folded to its plain ASCII counterpart, bullets and
     * ellipses become `*` / `...`, non-breaking spaces become ordinary spaces, and common Latin-1
     * accents lose their diacritic. Opt-in: [buildTextPdf] deliberately does **not** call this, so
     * legacy output is unchanged; rich-report callers (e.g. [PdfCanvas]) do.
     */
    fun sanitizeWinAnsi(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                // dashes
                '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> append('-')
                // single quotes
                '\u0060', '\u02BC', '\u2018', '\u2019', '\u201A', '\u201B' -> append('\'')
                // double quotes
                '\u00AB', '\u00BB', '\u201C', '\u201D', '\u201E', '\u201F' -> append('"')
                // ellipsis / bullets
                '\u2026' -> append("...")
                '\u00B7', '\u2022', '\u25AA', '\u25CF' -> append('*')
                // spaces that would otherwise become '?'
                '\u2000', '\u2001', '\u2002', '\u2003', '\u2009', '\u00A0', '\u2007', '\u202F' -> append(' ')
                // invisible / soft
                '\u00AD' -> Unit
                // common symbols
                '\u2122' -> append("TM")
                '\u00AE' -> append("(R)")
                '\u00A9' -> append("(C)")
                '\u00B0' -> append("deg")
                '\u00D7' -> append('x')
                '\u00F7' -> append('/')
                // Latin-1 accents
                '\u00C0', '\u00C1', '\u00C2', '\u00C3', '\u00C4', '\u00C5',
                '\u00E0', '\u00E1', '\u00E2', '\u00E3', '\u00E4', '\u00E5' -> append('a')
                '\u00C7', '\u00E7' -> append('c')
                '\u00C8', '\u00C9', '\u00CA', '\u00CB', '\u00E8', '\u00E9', '\u00EA', '\u00EB' -> append('e')
                '\u00CC', '\u00CD', '\u00CE', '\u00CF', '\u00EC', '\u00ED', '\u00EE', '\u00EF' -> append('i')
                '\u00D1', '\u00F1' -> append('n')
                '\u00D2', '\u00D3', '\u00D4', '\u00D5', '\u00D6',
                '\u00F2', '\u00F3', '\u00F4', '\u00F5', '\u00F6' -> append('o')
                '\u00D9', '\u00DA', '\u00DB', '\u00DC', '\u00F9', '\u00FA', '\u00FB', '\u00FC' -> append('u')
                '\u00DD', '\u00FD', '\u00FF' -> append('y')
                '\u00C6' -> append("AE")
                '\u00E6' -> append("ae")
                '\u0152' -> append("OE")
                '\u0153' -> append("oe")
                '\u00DF' -> append("ss")
                else -> append(ch)
            }
        }
    }

    /** Standard Helvetica AFM widths (1/1000 em) for ASCII codes 32..126. */
    private val HELVETICA_WIDTHS = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, // 32..41
        389, 584, 278, 333, 278, 278, 556, 556, 556, 556, // 42..51
        556, 556, 556, 556, 556, 556, 278, 278, 584, 584, // 52..61
        584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, // 62..71
        722, 278, 500, 667, 556, 833, 722, 778, 667, 778, // 72..81
        722, 667, 611, 722, 667, 944, 667, 667, 611, 278, // 82..91
        278, 278, 469, 556, 333, 556, 556, 500, 556, 556, // 92..101
        278, 556, 556, 222, 222, 500, 222, 833, 556, 556, // 102..111
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, // 112..121
        500, 334, 260, 334, 584                             // 122..126
    )

    /** Approximate Helvetica advance width in points for [sizePt]. */
    fun getTextWidth(text: String, sizePt: Double): Double {
        if (text.isEmpty() || sizePt <= 0.0) return 0.0
        var units = 0
        for (ch in text) {
            val code = ch.code
            units += if (code in 32..126) HELVETICA_WIDTHS[code - 32] else 556
        }
        return units * sizePt / 1000.0
    }

    /**
     * Greedy word wrap for a Helvetica run, measured in millimetres. Explicit `\n` characters are
     * preserved as paragraph breaks. Words wider than one line are split at character boundaries
     * so no output line can overflow the box.
     */
    fun wrapText(text: String, widthMm: Double, sizePt: Double): List<String> {
        if (text.isEmpty()) return emptyList()
        val maxPt = widthMm * MM_TO_PT
        if (maxPt <= 0.0) return emptyList()
        val out = mutableListOf<String>()

        fun breakLongWord(word: String) {
            var piece = ""
            for (ch in word) {
                val next = piece + ch
                if (piece.isNotEmpty() && getTextWidth(next, sizePt) > maxPt) {
                    out += piece
                    piece = ch.toString()
                } else {
                    piece = next
                }
            }
            if (piece.isNotEmpty()) out += piece
        }

        for (paragraph in text.split('\n')) {
            if (paragraph.isBlank()) {
                out += ""
                continue
            }
            var line = ""
            for (word in paragraph.trim().split(Regex("\\s+"))) {
                if (line.isNotEmpty()) {
                    val candidate = "$line $word"
                    if (getTextWidth(candidate, sizePt) <= maxPt) {
                        line = candidate
                        continue
                    }
                    out += line
                    line = ""
                }
                if (getTextWidth(word, sizePt) <= maxPt) {
                    line = word
                } else {
                    breakLongWord(word)
                }
            }
            if (line.isNotEmpty()) out += line
        }
        return out
    }

    /**
     * Returns exactly one line. Tries every size from [startSize] down to [minSize] in 0.5pt steps
     * and returns the unmodified (whitespace-flattened) text at the first size that fits. If even
     * [minSize] is too small, the text is truncated with an ASCII `...` suffix that fits at
     * [minSize]. Never returns an empty list.
     */
    fun fitOneLine(text: String, widthMm: Double, startSize: Double, minSize: Double): List<String> {
        val oneLine = text.replace('\n', ' ').replace('\r', ' ').trim()
        val maxPt = widthMm * MM_TO_PT
        if (maxPt <= 0.0) return listOf("")
        val floor = if (minSize > 0.0) minSize else 0.5
        val ceiling = if (startSize >= floor) startSize else floor
        var size = ceiling
        while (size >= floor) {
            if (getTextWidth(oneLine, size) <= maxPt) return listOf(oneLine)
            size -= 0.5
        }
        val ellipsis = "..."
        val avail = maxPt - getTextWidth(ellipsis, floor)
        if (avail <= 0.0) return listOf("")
        val kept = StringBuilder()
        for (ch in oneLine) {
            if (getTextWidth(kept.toString() + ch, floor) > avail) break
            kept.append(ch)
        }
        return listOf(kept.toString().trimEnd() + ellipsis)
    }

    // ---- Byte-compatible text writer ------------------------------------------------------

    /**
     * Byte-compatible with the exam/report writers.
     *
     * [title] is document **metadata only**: the legacy `lines(title, entries)` helpers already
     * bake the heading in as the first element of `lines`, so [title] is deliberately never drawn
     * and never serialised (an `/Info` dictionary would add objects and change the xref/trailer,
     * breaking byte compatibility). Only [lines] become page content.
     *
     * Every emitted character is ASCII: [escapePdfLiteral] folds all chars outside 32..126 to `?`,
     * so character counts and UTF-8 byte counts agree and the xref offsets remain valid.
     */
    fun buildTextPdf(
        title: String,
        lines: List<String>,
        options: PdfTextOptions = PdfTextOptions()
    ): ByteArray {
        val pages = lines.chunked(options.linesPerPage.coerceAtLeast(1))
        val pageCount = pages.size.coerceAtLeast(1)
        val pageIds = (0 until pageCount).map { 4 + it * 2 }
        val contentIds = (0 until pageCount).map { 5 + it * 2 }

        val doc = PdfDocumentBuilder()
        doc.put(1, "<< /Type /Catalog /Pages 2 0 R >>")
        doc.put(
            2,
            "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count $pageCount >>"
        )
        doc.put(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

        pages.forEachIndexed { index, pageLines ->
            doc.put(
                pageIds[index],
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents ${contentIds[index]} 0 R >>"
            )
            val stream = buildString {
                append("BT\n/F1 ").append(options.fontSize).append(" Tf\n")
                append(options.startX).append(' ').append(options.startY).append(" Td\n")
                append(options.leading).append(" TL\n")
                pageLines.forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) append("T*\n")
                    append('(').append(escapePdfLiteral(line)).append(") Tj\n")
                }
                append("ET\n")
            }
            doc.put(
                contentIds[index],
                "<< /Length ${stream.encodeToByteArray().size} >>\nstream\n$stream\nendstream"
            )
        }
        return doc.serialize()
    }

    // ---- Byte-compatible scanner writer / multi-page scans --------------------------------

    /** Byte-compatible with the scanner writer: a single image page with fixed ids `1..6`. */
    fun buildImagePdf(
        jpeg: ByteArray,
        crop: PdfCrop,
        imageWidth: Int,
        imageHeight: Int,
        title: String,
        note: String
    ): ByteArray = buildImagePdfPages(
        listOf(PdfImagePage(jpeg, crop, imageWidth, imageHeight, title, note))
    )

    /**
     * Multi-page scan. Every page gets a real clipping rectangle (`q / cm / re W n / Do / Q`) that
     * exposes only its crop of the actual JPEG, plus an optional title (14pt, y=814) and note
     * (10pt, y=794). [onProgress] is invoked once per assembled page with `(1-based page, total)`,
     * so a 3-page scan reports `(1,3)`, `(2,3)`, `(3,3)`.
     *
     * Object ids are `1` catalog, `2` pages, `3` font, then `4+3i` page, `5+3i` content and
     * `6+3i` image. For a single page that is exactly the legacy `1..6` scanner layout.
     */
    fun buildImagePdfPages(
        pages: List<PdfImagePage>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ByteArray {
        val pageCount = pages.size
        val doc = PdfDocumentBuilder()
        doc.put(1, "<< /Type /Catalog /Pages 2 0 R >>")
        doc.put(
            2,
            "<< /Type /Pages /Kids [${(0 until pageCount).joinToString(" ") { "${4 + it * 3} 0 R" }}] " +
                "/Count $pageCount >>"
        )
        doc.put(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

        pages.forEachIndexed { index, page ->
            val pageId = 4 + index * 3
            val contentId = pageId + 1
            val imageId = pageId + 2
            doc.put(
                pageId,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R >> /XObject << /Im0 $imageId 0 R >> >> " +
                    "/Contents $contentId 0 R >>"
            )

            val info = readJpegInfo(page.jpeg, page.imageWidth, page.imageHeight)
            val pageW = PAGE_WIDTH_PT
            val pageH = PAGE_HEIGHT_PT
            val margin = 40f
            val availW = pageW - 2 * margin
            val availH = pageH - 2 * margin - 70f
            val scale = minOf(availW / info.width, availH / info.height)
            val dispW = info.width * scale
            val dispH = info.height * scale
            val clipW = page.crop.width * dispW
            val clipH = page.crop.height * dispH
            val dx = -page.crop.left * dispW
            val dy = -(1f - page.crop.bottom) * dispH
            val colorspace = if (info.components == 1) "/DeviceGray" else "/DeviceRGB"

            val content = buildString {
                append("q\n")
                append("1 0 0 1 ${pdfNum(dx)} ${pdfNum(dy)} cm\n")
                append("${pdfNum(margin)} ${pdfNum(margin)} ${pdfNum(clipW)} ${pdfNum(clipH)} re W n\n")
                append("${pdfNum(dispW)} 0 0 ${pdfNum(dispH)} ${pdfNum(margin)} ${pdfNum(margin)} cm\n")
                append("/Im0 Do\n")
                append("Q\n")
                append(
                    "BT\n/F1 14 Tf\n${pageH - 28} Td\n" +
                        "(${escapePdfLiteral(page.title.ifBlank { "AmazeCC Scan" })}) Tj\nET\n"
                )
                if (page.note.isNotBlank()) {
                    append("BT\n/F1 10 Tf\n${pageH - 48} Td\n(${escapePdfLiteral(page.note)}) Tj\nET\n")
                }
            }
            // Legacy byte compatibility: the stream body is `content + "\n"` but /Length is
            // measured from the same string and the original writers declared one byte more
            // than the literal body. Keep the +1 and the trailing newline exactly as-is; a
            // "correction" here changes emitted bytes and breaks the pinned exam-PDF contract.
            val contentBytes = (content + "\n").encodeToByteArray()
            doc.put(
                contentId,
                "<< /Length ${contentBytes.size} >>\nstream\n$content\nendstream"
            )
            doc.putBinary(
                imageId,
                "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
                    "/ColorSpace $colorspace /BitsPerComponent 8 /Filter /DCTDecode " +
                    "/Length ${page.jpeg.size + 1} >>\nstream\n", // +1 is intentional legacy byte-compat; see content stream comment above
                page.jpeg,
                "\nendstream"
            )
            onProgress?.invoke(index + 1, pageCount)
        }
        return doc.serialize()
    }

    // ---- JPEG header inspection -----------------------------------------------------------

    /** True when the bytes start with a JPEG SOI marker (`FF D8 FF`). */
    fun isJpeg(bytes: ByteArray): Boolean = bytes.size > 3 &&
        (bytes[0].toInt() and 0xFF) == 0xFF &&
        (bytes[1].toInt() and 0xFF) == 0xD8 &&
        (bytes[2].toInt() and 0xFF) == 0xFF

    /**
     * Reads SOF dimensions/components so the PDF colour space matches the file. Falls back to the
     * caller-supplied dimensions (at least 1x1) and 3 components when no SOF marker is present.
     */
    fun readJpegInfo(bytes: ByteArray, fallbackWidth: Int, fallbackHeight: Int): PdfJpegInfo {
        val sofMarkers = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)
        var i = 2
        while (i + 9 < bytes.size) {
            if ((bytes[i].toInt() and 0xFF) != 0xFF) { i++; continue }
            while (i < bytes.size && (bytes[i].toInt() and 0xFF) == 0xFF) i++
            if (i >= bytes.size) break
            val marker = bytes[i].toInt() and 0xFF
            i++
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue
            if (marker == 0xD9 || marker == 0xDA) break
            if (i + 2 > bytes.size) break
            val segmentLength = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            if (marker in sofMarkers && i + 8 <= bytes.size) {
                val height = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
                val width = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                val components = bytes[i + 7].toInt() and 0xFF
                if (width > 0 && height > 0 && components in 1..4) {
                    return PdfJpegInfo(width, height, components)
                }
            }
            if (segmentLength < 2) break
            i += segmentLength
        }
        return PdfJpegInfo(fallbackWidth.coerceAtLeast(1), fallbackHeight.coerceAtLeast(1), 3)
    }
}

// ---- Shared low-level serializer ---------------------------------------------------------

/** A body is either plain ASCII text or a text prefix, raw bytes, and a text suffix (the JPEG). */
internal sealed class PdfObjectBody {
    class Text(val value: String) : PdfObjectBody()
    class Binary(val prefix: String, val bytes: ByteArray, val suffix: String) : PdfObjectBody()
}

/**
 * Append-only byte buffer. Offsets are taken from [size], which is a true byte count even when a
 * binary XObject is embedded, so the xref table is correct for every writer.
 */
internal class PdfByteSink {
    private val bytes = ArrayList<Byte>(4096)
    val size: Int get() = bytes.size

    operator fun plusAssign(text: String) {
        val encoded = text.encodeToByteArray()
        for (b in encoded) bytes.add(b)
    }

    operator fun plusAssign(data: ByteArray) {
        for (b in data) bytes.add(b)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

/** Serialises objects `1..maxId` into a valid PDF 1.4 file with correct xref BYTE offsets. */
internal class PdfDocumentBuilder {
    private val objects = LinkedHashMap<Int, PdfObjectBody>()

    fun put(id: Int, body: String) {
        objects[id] = PdfObjectBody.Text(body)
    }

    fun putBinary(id: Int, prefix: String, bytes: ByteArray, suffix: String) {
        objects[id] = PdfObjectBody.Binary(prefix, bytes, suffix)
    }

    fun serialize(): ByteArray {
        val sink = PdfByteSink()
        sink += "%PDF-1.4\n"
        val offsets = HashMap<Int, Int>()
        val ids = objects.keys.sorted()
        for (id in ids) {
            offsets[id] = sink.size
            sink += "$id 0 obj\n"
            when (val body = objects.getValue(id)) {
                is PdfObjectBody.Text -> sink += body.value
                is PdfObjectBody.Binary -> {
                    sink += body.prefix
                    sink += body.bytes
                    sink += body.suffix
                }
            }
            sink += "\nendobj\n"
        }
        val xrefOffset = sink.size
        val maxId = ids.lastOrNull() ?: 0
        sink += "xref\n0 ${maxId + 1}\n"
        sink += "0000000000 65535 f \n"
        for (id in 1..maxId) {
            val offset = offsets[id] ?: error("Missing PDF object $id")
            sink += offset.toString().padStart(10, '0')
            sink += " 00000 n \n"
        }
        sink += "trailer\n<< /Size ${maxId + 1} /Root 1 0 R >>\n"
        sink += "startxref\n$xrefOffset\n%%EOF\n"
        return sink.toByteArray()
    }
}
