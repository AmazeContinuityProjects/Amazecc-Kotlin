package com.amazecc.app.shared.ui.screens.m3e

/**
 * D-009 — dependency-free, standards-compliant PDF writer for the document
 * scanner. The imported JPEG is embedded verbatim with a DCTDecode image
 * XObject; the crop selection is applied as a real PDF clipping rectangle, so
 * the exported file contains the chosen region of the actual image — not a
 * text placeholder. No platform image encoder or PDF library is required.
 */
internal object DocumentScannerPdf {

    data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        companion object {
            val FULL = Crop(0f, 0f, 1f, 1f)
        }
    }

    data class JpegInfo(val width: Int, val height: Int, val components: Int)

    fun isJpeg(bytes: ByteArray): Boolean = bytes.size > 3 &&
        (bytes[0].toInt() and 0xFF) == 0xFF &&
        (bytes[1].toInt() and 0xFF) == 0xD8 &&
        (bytes[2].toInt() and 0xFF) == 0xFF

    /** Reads SOF dimensions/components so the PDF colorspace matches the file. */
    fun readJpegInfo(bytes: ByteArray, fallbackWidth: Int, fallbackHeight: Int): JpegInfo {
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
                if (width > 0 && height > 0 && components in 1..4) return JpegInfo(width, height, components)
            }
            if (segmentLength < 2) break
            i += segmentLength
        }
        return JpegInfo(fallbackWidth.coerceAtLeast(1), fallbackHeight.coerceAtLeast(1), 3)
    }

    /** Builds a one-page PDF: cropped JPEG image + optional title/note text. */
    fun buildPdf(jpeg: ByteArray, crop: Crop, imageWidth: Int, imageHeight: Int, title: String, note: String): ByteArray {
        val info = readJpegInfo(jpeg, imageWidth, imageHeight)
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val availW = pageW - 2 * margin
        val availH = pageH - 2 * margin - 70f
        val scale = minOf(availW / info.width, availH / info.height)
        val dispW = info.width * scale
        val dispH = info.height * scale
        val mx = margin
        val my = margin
        val clipW = crop.width * dispW
        val clipH = crop.height * dispH
        // Translate the crop's bottom-left corner to (mx, my), then draw the
        // full image in that translated space. The clip keeps only the crop.
        val dx = -crop.left * dispW
        val dy = -(1f - crop.bottom) * dispH
        val colorspace = if (info.components == 1) "/DeviceGray" else "/DeviceRGB"

        val content = buildString {
            append("q\n")
            append("1 0 0 1 ${num(dx)} ${num(dy)} cm\n")
            append("${num(mx)} ${num(my)} ${num(clipW)} ${num(clipH)} re W n\n")
            append("${num(dispW)} 0 0 ${num(dispH)} ${num(mx)} ${num(my)} cm\n")
            append("/Im0 Do\n")
            append("Q\n")
            append("BT\n/F1 14 Tf\n${pageH - 28} Td\n(${escape(title.ifBlank { "AmazeCC Scan" })}) Tj\nET\n")
            if (note.isNotBlank()) {
                append("BT\n/F1 10 Tf\n${pageH - 48} Td\n(${escape(note)}) Tj\nET\n")
            }
        }

        val sink = Sink()
        sink ascii "%PDF-1.4\n"
        val offsets = mutableMapOf<Int, Int>()
        fun obj(id: Int, body: String) {
            offsets[id] = sink.size
            sink ascii "$id 0 obj\n"
            sink ascii body
            sink ascii "\nendobj\n"
        }
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(
            3,
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] " +
                "/Resources << /Font << /F1 5 0 R >> /XObject << /Im0 6 0 R >> >> /Contents 4 0 R >>",
        )
        // The stream data is `content` plus its terminating newline.
        val contentBytes = (content + "\n").encodeToByteArray()
        obj(4, "<< /Length ${contentBytes.size} >>\nstream\n$content\nendstream")
        obj(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        offsets[6] = sink.size
        sink ascii "6 0 obj\n<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
            "/ColorSpace $colorspace /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size + 1} >>\nstream\n"
        sink raw jpeg
        sink ascii "\nendstream\nendobj\n"

        val xrefOffset = sink.size
        sink ascii "xref\n0 7\n0000000000 65535 f \n"
        for (id in 1..6) {
            sink ascii offsets.getValue(id).toString().padStart(10, '0')
            sink ascii " 00000 n \n"
        }
        sink ascii "trailer\n<< /Size 7 /Root 1 0 R >>\n"
        sink ascii "startxref\n$xrefOffset\n%%EOF\n"
        return sink.toByteArray()
    }

    private fun num(value: Float): String {
        val rounded = (value * 1000).toLong() / 1000f
        return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
    }

    private fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                else -> append(if (ch.code in 32..126) ch else '?')
            }
        }
    }

    /** Minimal append-only byte buffer (pure commonMain, no platform streams). */
    private class Sink {
        private val bytes = ArrayList<Byte>(4096)
        val size: Int get() = bytes.size

        infix fun ascii(text: String) {
            bytes.addAll(text.encodeToByteArray().asList())
        }

        infix fun raw(data: ByteArray) {
            bytes.addAll(data.asList())
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
