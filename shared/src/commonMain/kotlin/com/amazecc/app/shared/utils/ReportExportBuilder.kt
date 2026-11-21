package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.overhaul.M3EReportData
import com.amazecc.app.shared.pdf.PdfEngine
import com.amazecc.app.shared.pdf.PdfTextOptions

/** The three live report export formats, all derived from one [M3EReportData] snapshot. */
enum class ReportExportFormat { PDF, JSON, HTML }

/** A real, dated filename plus the complete bytes to hand to the platform save seam. */
data class ReportExportFile(val filename: String, val bytes: ByteArray)

/**
 * D-157b — the single pure seam between the assembled web-parity report snapshot and every
 * live export. No I/O, no clock, no Compose: the caller supplies the fully assembled [data],
 * including its `generatedAt`, so each format is a deterministic function of that one value.
 *
 * Every format takes its filename from [ReportSerializers.filename], i.e. the report's real UTC
 * date — never a hard-coded name. PDF keeps the pinned 11pt / 15pt-leading / 48-line layout so its
 * bytes match the legacy contract; JSON and HTML are the UTF-8 serializer outputs verbatim.
 */
object ReportExportBuilder {
    fun build(data: M3EReportData, format: ReportExportFormat): ReportExportFile = when (format) {
        ReportExportFormat.PDF -> {
            val bytes = PdfEngine.buildTextPdf(
                "AMAZECC STUDENT ACADEMIC REPORT",
                ReportSerializers.pdfContentLines(data),
                PdfTextOptions(fontSize = 11, leading = 15, startX = 36, startY = 812, linesPerPage = 48),
            )
            ReportExportFile(ReportSerializers.filename("pdf", data.generatedAt), bytes)
        }
        ReportExportFormat.JSON -> utf8File("json", ReportSerializers.json(data), data.generatedAt)
        ReportExportFormat.HTML -> utf8File("html", ReportSerializers.html(data), data.generatedAt)
    }

    /** UTF-8 share-sheet text from the same full snapshot, preserving non-ASCII characters. */
    fun shareText(data: M3EReportData): String = ReportSerializers.shareText(data)

    private fun utf8File(extension: String, text: String, generatedAt: Long): ReportExportFile =
        ReportExportFile(ReportSerializers.filename(extension, generatedAt), text.encodeToByteArray())
}
