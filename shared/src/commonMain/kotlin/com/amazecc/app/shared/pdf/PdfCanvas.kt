package com.amazecc.app.shared.pdf

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** An opaque RGB fill/stroke colour; components are 0f..1f. */
data class PdfColor(val r: Float, val g: Float, val b: Float) {
    companion object {
        val BLACK: PdfColor = PdfColor(0f, 0f, 0f)
        val WHITE: PdfColor = PdfColor(1f, 1f, 1f)
        val GRAY: PdfColor = PdfColor(0.42f, 0.42f, 0.42f)
        val LIGHT_GRAY: PdfColor = PdfColor(0.93f, 0.93f, 0.93f)
        val ZEBRA_GRAY: PdfColor = PdfColor(0.97f, 0.97f, 0.97f)
        val ACCENT: PdfColor = PdfColor(0.12f, 0.35f, 0.60f)
    }
}

/** Horizontal text placement relative to the supplied x coordinate. */
enum class PdfAlign { LEFT, CENTER, RIGHT }

/** A table column: width in mm and the alignment of its cell text. */
data class PdfColumn(val widthMm: Float, val align: PdfAlign = PdfAlign.LEFT)

/**
 * Additive, mm-based A4 canvas for the web-style reports.
 *
 * Coordinates are measured from the **top-left** of the page in millimetres; [PdfCanvas] converts
 * them to PDF's bottom-left point space when serialising. Geometry is A4 (210x297mm -> 595x842pt)
 * with a Helvetica normal (`/F1`) and Helvetica-Bold (`/F2`) resource pair on every page.
 *
 * Every primitive appends operators to the current page and shares [PdfEngine]'s
 * [PdfDocumentBuilder], so [build] emits a valid PDF 1.4 with correct xref **byte** offsets even
 * though content streams are uncompressed ASCII.
 *
 * Shape limits: rounded corners and circles are approximated with four cubic Beziers using the
 * standard `k = 0.5522847498` quarter-circle constant (visually exact to well under a pixel at
 * report sizes); triangles are straight `m/l/h` paths. All use real `f`/`S`/`B` painting operators —
 * no placeholder rectangles stand in for a shape.
 *
 * @param title metadata only; it never fabricates page content (draw a heading with [text]).
 */
class PdfCanvas(
    val title: String = "",
    val pageWidthMm: Float = 210f,
    val pageHeightMm: Float = 297f,
    val marginMm: Float = 16f
) {

    private data class FooterSpec(val text: String, val sizePt: Float, val color: PdfColor)

    private val pageOps = mutableListOf<StringBuilder>()
    private val footerSpecs = mutableListOf<FooterSpec>()
    private var footersApplied = false
    private var cursorYMm: Float = marginMm

    init {
        pageOps += StringBuilder()
    }

    private fun current(): StringBuilder = pageOps.last()
    private fun ptX(mm: Float): Float = PdfEngine.mmToPt(mm)
    private fun ptY(yMm: Float): Float = PdfEngine.mmToPt(pageHeightMm - yMm)
    private fun append(value: String) { current().append(value) }

    private fun rgb(color: PdfColor): String =
        "${PdfEngine.pdfNum(color.r)} ${PdfEngine.pdfNum(color.g)} ${PdfEngine.pdfNum(color.b)}"

    /** Emits the fill/stroke operators for a path that has already been appended. */
    private fun paint(fill: PdfColor?, stroke: PdfColor?, strokeWidthPt: Float) {
        when {
            fill != null && stroke != null -> {
                append("${rgb(fill)} rg\n${rgb(stroke)} RG\n${PdfEngine.pdfNum(strokeWidthPt)} w\nB\n")
            }
            fill != null -> append("${rgb(fill)} rg\nf\n")
            stroke != null -> append("${rgb(stroke)} RG\n${PdfEngine.pdfNum(strokeWidthPt)} w\nS\n")
            else -> append("0 0 0 rg\nf\n")
        }
    }

    // ---- Vector shapes --------------------------------------------------------------------

    /** Axis-aligned rectangle; [yMm] is the top edge. */
    fun rect(
        xMm: Float,
        yMm: Float,
        widthMm: Float,
        heightMm: Float,
        fill: PdfColor? = null,
        stroke: PdfColor? = null,
        strokeWidthPt: Float = 0.6f
    ) {
        if (widthMm <= 0f || heightMm <= 0f) return
        append(
            "${PdfEngine.pdfNum(ptX(xMm))} ${PdfEngine.pdfNum(ptY(yMm + heightMm))} " +
                "${PdfEngine.pdfNum(ptX(widthMm))} ${PdfEngine.pdfNum(ptX(heightMm))} re\n"
        )
        paint(fill, stroke, strokeWidthPt)
    }

    /** Line between two top-left-origin points, optionally dashed. */
    fun line(
        x1Mm: Float,
        y1Mm: Float,
        x2Mm: Float,
        y2Mm: Float,
        color: PdfColor = PdfColor.BLACK,
        widthPt: Float = 0.6f,
        dashed: Boolean = false
    ) {
        append(if (dashed) "[2 2] 0 d\n" else "[] 0 d\n")
        append(
            "${rgb(color)} RG\n${PdfEngine.pdfNum(widthPt)} w\n" +
                "${PdfEngine.pdfNum(ptX(x1Mm))} ${PdfEngine.pdfNum(ptY(y1Mm))} m\n" +
                "${PdfEngine.pdfNum(ptX(x2Mm))} ${PdfEngine.pdfNum(ptY(y2Mm))} l\nS\n"
        )
    }

    /**
     * Rounded rectangle using four cubic Bezier arcs. Radius is clamped to half the smaller side;
     * a non-positive radius degrades to a plain [rect].
     */
    fun roundedRect(
        xMm: Float,
        yMm: Float,
        widthMm: Float,
        heightMm: Float,
        radiusMm: Float,
        fill: PdfColor? = null,
        stroke: PdfColor? = null,
        strokeWidthPt: Float = 0.6f
    ) {
        if (widthMm <= 0f || heightMm <= 0f) return
        val x = ptX(xMm)
        val y = ptY(yMm + heightMm)
        val w = ptX(widthMm)
        val h = ptX(heightMm)
        val r = minOf(ptX(radiusMm), w / 2f, h / 2f).coerceAtLeast(0f)
        if (r == 0f) {
            rect(xMm, yMm, widthMm, heightMm, fill, stroke, strokeWidthPt)
            return
        }
        append("${PdfEngine.pdfNum(x + r)} ${PdfEngine.pdfNum(y)} m\n")
        append("${PdfEngine.pdfNum(x + w - r)} ${PdfEngine.pdfNum(y)} l\n")
        appendArc(x + w - r, y + r, r, 270.0, 360.0)
        append("${PdfEngine.pdfNum(x + w)} ${PdfEngine.pdfNum(y + h - r)} l\n")
        appendArc(x + w - r, y + h - r, r, 0.0, 90.0)
        append("${PdfEngine.pdfNum(x + r)} ${PdfEngine.pdfNum(y + h)} l\n")
        appendArc(x + r, y + h - r, r, 90.0, 180.0)
        append("${PdfEngine.pdfNum(x)} ${PdfEngine.pdfNum(y + r)} l\n")
        appendArc(x + r, y + r, r, 180.0, 270.0)
        append("h\n")
        paint(fill, stroke, strokeWidthPt)
    }

    /** Circle centred at ([cxMm], [cyMm]) with the given radius, drawn as four Bezier arcs. */
    fun circle(
        cxMm: Float,
        cyMm: Float,
        radiusMm: Float,
        fill: PdfColor? = null,
        stroke: PdfColor? = null,
        strokeWidthPt: Float = 0.6f
    ) {
        val cx = ptX(cxMm)
        val cy = ptY(cyMm)
        val r = ptX(radiusMm)
        if (r <= 0f) return
        append("${PdfEngine.pdfNum(cx + r)} ${PdfEngine.pdfNum(cy)} m\n")
        appendArc(cx, cy, r, 0.0, 90.0)
        appendArc(cx, cy, r, 90.0, 180.0)
        appendArc(cx, cy, r, 180.0, 270.0)
        appendArc(cx, cy, r, 270.0, 360.0)
        append("h\n")
        paint(fill, stroke, strokeWidthPt)
    }

    /** Filled/stroked triangle with three top-left-origin vertices. */
    fun triangle(
        x1Mm: Float,
        y1Mm: Float,
        x2Mm: Float,
        y2Mm: Float,
        x3Mm: Float,
        y3Mm: Float,
        fill: PdfColor? = null,
        stroke: PdfColor? = null,
        strokeWidthPt: Float = 0.6f
    ) {
        append(
            "${PdfEngine.pdfNum(ptX(x1Mm))} ${PdfEngine.pdfNum(ptY(y1Mm))} m\n" +
                "${PdfEngine.pdfNum(ptX(x2Mm))} ${PdfEngine.pdfNum(ptY(y2Mm))} l\n" +
                "${PdfEngine.pdfNum(ptX(x3Mm))} ${PdfEngine.pdfNum(ptY(y3Mm))} l\nh\n"
        )
        paint(fill, stroke, strokeWidthPt)
    }

    private fun appendArc(cx: Float, cy: Float, r: Float, fromDeg: Double, toDeg: Double) {
        val k = 0.5522847498 * r
        val a = fromDeg * PI / 180.0
        val b = toDeg * PI / 180.0
        val sa = sin(a)
        val ca = cos(a)
        val sb = sin(b)
        val cb = cos(b)
        val endX = cx + r * cb.toFloat()
        val endY = cy + r * sb.toFloat()
        append(
            "${PdfEngine.pdfNum(cx + r * ca.toFloat() - (k * sa).toFloat())} " +
                "${PdfEngine.pdfNum(cy + r * sa.toFloat() + (k * ca).toFloat())} " +
                "${PdfEngine.pdfNum(endX + (k * sb).toFloat())} " +
                "${PdfEngine.pdfNum(endY - (k * cb).toFloat())} " +
                "${PdfEngine.pdfNum(endX)} ${PdfEngine.pdfNum(endY)} c\n"
        )
    }

    // ---- Text -----------------------------------------------------------------------------

    /**
     * Draws one line of text at ([xMm], [yMm]) where [yMm] is the baseline measured from the top.
     * The string is WinAnsi-folded and literal-escaped before emission, so smart quotes and em
     * dashes never turn into `?`. [align] shifts around x, [rotationDeg] rotates about the
     * baseline point, and [maxWidthMm] shrinks/truncates the run to fit via [PdfEngine.fitOneLine].
     */
    fun text(
        value: String,
        xMm: Float,
        yMm: Float,
        sizePt: Float = 10f,
        bold: Boolean = false,
        color: PdfColor = PdfColor.BLACK,
        align: PdfAlign = PdfAlign.LEFT,
        rotationDeg: Float = 0f,
        maxWidthMm: Float? = null
    ) = writeText(current(), value, xMm, yMm, sizePt, bold, color, align, rotationDeg, maxWidthMm)

    private fun writeText(
        target: StringBuilder,
        value: String,
        xMm: Float,
        yMm: Float,
        sizePt: Float,
        bold: Boolean,
        color: PdfColor,
        align: PdfAlign,
        rotationDeg: Float,
        maxWidthMm: Float? = null
    ) {
        var safe = PdfEngine.sanitizeWinAnsi(value).replace('\n', ' ').replace('\r', ' ')
        if (maxWidthMm != null) {
            safe = PdfEngine.fitOneLine(
                safe,
                maxWidthMm.toDouble(),
                sizePt.toDouble(),
                (sizePt * 0.6).toDouble()
            ).firstOrNull().orEmpty()
        }
        if (safe.isEmpty()) return
        val escaped = PdfEngine.escapePdfLiteral(safe)
        val widthPt = PdfEngine.getTextWidth(safe, sizePt.toDouble())
        val offsetMm = PdfEngine.ptToMm(widthPt).toFloat()
        val xt = when (align) {
            PdfAlign.LEFT -> ptX(xMm)
            PdfAlign.CENTER -> ptX(xMm) - ptX(offsetMm / 2f)
            PdfAlign.RIGHT -> ptX(xMm) - ptX(offsetMm)
        }
        val yt = ptY(yMm)
        val font = if (bold) "/F2" else "/F1"
        val style = "${rgb(color)} rg\n$font ${PdfEngine.pdfNum(sizePt)} Tf\n"
        if (rotationDeg == 0f) {
            target.append("BT\n$style${PdfEngine.pdfNum(xt)} ${PdfEngine.pdfNum(yt)} Td\n($escaped) Tj\nET\n")
        } else {
            val a = rotationDeg * PI / 180.0
            target.append(
                "q\n${cos(a)} ${sin(a)} ${-sin(a)} ${cos(a)} " +
                    "${PdfEngine.pdfNum(xt)} ${PdfEngine.pdfNum(yt)} cm\n" +
                    "BT\n$style" + "0 0 Td\n($escaped) Tj\nET\nQ\n"
            )
        }
    }

    // ---- Flow / pagination -----------------------------------------------------------------

    /** True when [heightMm] still fits between the cursor and the bottom margin. */
    fun room(heightMm: Float): Boolean = cursorYMm + heightMm <= pageHeightMm - marginMm

    /** Starts a new page if needed. Returns true when a page break actually happened. */
    fun ensureRoom(heightMm: Float): Boolean {
        if (room(heightMm)) return false
        newPage()
        return true
    }

    /** Ends the current page and starts a fresh one (no-op if the current page is empty). */
    fun newPage() {
        if (current().isNotEmpty()) pageOps += StringBuilder()
        cursorYMm = marginMm
    }

    /** Queues a footer for the final page, drawn below the content area when [build] runs. */
    fun footerAtEnd(text: String, sizePt: Float = 8f, color: PdfColor = PdfColor.GRAY) {
        footerSpecs += FooterSpec(text, sizePt, color)
    }

    // ---- Tables ---------------------------------------------------------------------------

    private fun sanitized(cell: String?): String = PdfEngine.sanitizeWinAnsi(cell.orEmpty())

    private fun rowHeightMm(cells: List<String>, fontPt: Float, padMm: Float, minMm: Float): Float {
        val lineHeightMm = (fontPt * 1.35 / PdfEngine.MM_TO_PT).toFloat()
        var lines = 1
        columns@ for (i in cells.indices) {
            val width = cellColumnWidth(i)
            val avail = (width - 2 * padMm).toDouble()
            if (avail <= 0.0) continue@columns
            lines = maxOf(lines, PdfEngine.wrapText(cells[i], avail, fontPt.toDouble()).size)
        }
        return maxOf(minMm, lines * lineHeightMm + 2 * padMm)
    }

    private fun cellColumnWidth(index: Int): Float =
        if (index < lastColumns.size) lastColumns[index].widthMm else 0f

    private var lastColumns: List<PdfColumn> = emptyList()

    /**
     * Draws a header + rows grid with per-cell word wrapping and automatic page breaks, repeating
     * the header on each new page. The cursor is left just below the last row. [startYMm] overrides
     * the current vertical position (useful for placing two tables side by side in a flow).
     */
    fun chunkedTable(
        columns: List<PdfColumn>,
        headers: List<String>,
        rows: List<List<String>>,
        startYMm: Float? = null,
        rowPaddingMm: Float = 3f,
        minRowHeightMm: Float = 7f,
        fontSizePt: Float = 9f,
        headerFontSizePt: Float = 9f,
        headerFill: PdfColor = PdfColor.LIGHT_GRAY,
        rowFill: PdfColor? = null,
        alternatingFill: PdfColor? = PdfColor.ZEBRA_GRAY,
        gridColor: PdfColor = PdfColor(0.75f, 0.75f, 0.75f),
        repeatHeaderOnNewPage: Boolean = true
    ) {
        if (columns.isEmpty()) return
        lastColumns = columns
        if (startYMm != null) cursorYMm = startYMm
        val totalWidthMm = columns.sumOf { it.widthMm.toDouble() }.toFloat()
        val headerCells = columns.indices.map { sanitized(headers.getOrNull(it)) }

        fun headerHeight(): Float =
            rowHeightMm(headerCells, headerFontSizePt, rowPaddingMm, minRowHeightMm)

        fun drawHeader() {
            val h = headerHeight()
            val y = cursorYMm
            rect(marginMm, y, totalWidthMm, h, fill = headerFill)
            var x = marginMm
            columns.forEachIndexed { i, col ->
                writeText(
                    current(), headerCells[i], x + rowPaddingMm,
                    (y + rowPaddingMm + headerFontSizePt * 0.9f / PdfEngine.MM_TO_PT).toFloat(),
                    headerFontSizePt, true, PdfColor.BLACK, col.align, 0f
                )
                x += col.widthMm
            }
            line(marginMm, y + h, marginMm + totalWidthMm, y + h, gridColor, 0.4f)
            cursorYMm = y + h
        }

        ensureRoom(headerHeight() + minRowHeightMm)
        drawHeader()

        rows.forEachIndexed { rowIndex, row ->
            val cells = columns.indices.map { sanitized(row.getOrNull(it)) }
            val h = rowHeightMm(cells, fontSizePt, rowPaddingMm, minRowHeightMm)
            if (!room(h)) {
                newPage()
                if (repeatHeaderOnNewPage) drawHeader()
            }
            val y = cursorYMm
            val background = rowFill ?: if (alternatingFill != null && rowIndex % 2 == 1) alternatingFill else null
            if (background != null) rect(marginMm, y, totalWidthMm, h, fill = background)

            val lineHeightMm = (fontSizePt * 1.35 / PdfEngine.MM_TO_PT).toFloat()
            var x = marginMm
            columns.forEachIndexed { i, col ->
                val avail = (col.widthMm - 2 * rowPaddingMm).toDouble()
                val lines = if (avail > 0.0) {
                    PdfEngine.wrapText(cells[i], avail, fontSizePt.toDouble()).ifEmpty { listOf("") }
                } else {
                    listOf(cells[i])
                }
                lines.forEachIndexed { lineIndex, lineText ->
                    writeText(
                        current(), lineText, x + rowPaddingMm,
                        (y + rowPaddingMm + lineIndex * lineHeightMm + fontSizePt * 0.9f / PdfEngine.MM_TO_PT).toFloat(),
                        fontSizePt, false, PdfColor.BLACK, col.align, 0f
                    )
                }
                x += col.widthMm
            }
            x = marginMm
            repeat(columns.size + 1) {
                line(x, y, x, y + h, gridColor, 0.4f)
                x += if (it < columns.size) columns[it].widthMm else 0f
            }
            line(marginMm, y + h, marginMm + totalWidthMm, y + h, gridColor, 0.4f)
            cursorYMm = y + h
        }
        lastColumns = emptyList()
    }

    // ---- Serialisation --------------------------------------------------------------------

    /** Finalises footers and serialises every page into a valid, uncompressed PDF 1.4. */
    fun build(): ByteArray {
        if (footerSpecs.isNotEmpty() && pageOps.isNotEmpty() && !footersApplied) {
            footersApplied = true
            val footerPage = pageOps.last()
            var y = pageHeightMm - marginMm / 2f
            footerSpecs.forEach { footer ->
                writeText(
                    footerPage, footer.text, marginMm, y, footer.sizePt,
                    false, footer.color, PdfAlign.LEFT, 0f
                )
                y -= (footer.sizePt * 0.7 / PdfEngine.MM_TO_PT).toFloat()
            }
        }

        val doc = PdfDocumentBuilder()
        doc.put(1, "<< /Type /Catalog /Pages 2 0 R >>")
        doc.put(
            2,
            "<< /Type /Pages /Kids [${(pageOps.indices).joinToString(" ") { "${5 + it * 2} 0 R" }}] " +
                "/Count ${pageOps.size} >>"
        )
        doc.put(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        doc.put(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        pageOps.forEachIndexed { index, ops ->
            val pageId = 5 + index * 2
            val contentId = pageId + 1
            doc.put(
                pageId,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${PdfEngine.PAGE_WIDTH_PT} ${PdfEngine.PAGE_HEIGHT_PT}] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentId 0 R >>"
            )
            val stream = ops.toString()
            val terminated = if (stream.endsWith('\n')) stream else "$stream\n"
            doc.put(
                contentId,
                "<< /Length ${terminated.encodeToByteArray().size} >>\nstream\n$terminated\nendstream"
            )
        }
        return doc.serialize()
    }
}
