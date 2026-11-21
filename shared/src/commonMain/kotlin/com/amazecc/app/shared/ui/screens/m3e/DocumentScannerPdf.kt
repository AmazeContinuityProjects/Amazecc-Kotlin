package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.pdf.PdfCrop
import com.amazecc.app.shared.pdf.PdfEngine
import com.amazecc.app.shared.pdf.PdfImagePage

/**
 * D-009 / D-149 — dependency-free PDF export for the document scanner.
 *
 * This object is now a thin adapter over the shared [PdfEngine]. Local value types ([Crop],
 * [JpegInfo]) are kept so existing call sites and tests do not have to know the engine package;
 * the actual JPEG embedding, clipping and xref serialization all live in one engine instead of
 * three hand-written copies.
 */
internal object DocumentScannerPdf {

    /** Normalized (0..1) crop rectangle. Maps 1:1 onto [PdfCrop]. */
    data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        companion object {
            val FULL = Crop(0f, 0f, 1f, 1f)
        }

        internal fun toPdfCrop(): PdfCrop = PdfCrop(left, top, right, bottom)
    }

    data class JpegInfo(val width: Int, val height: Int, val components: Int)

    fun isJpeg(bytes: ByteArray): Boolean = PdfEngine.isJpeg(bytes)

    /** Reads SOF dimensions/components so the PDF colorspace matches the file. */
    fun readJpegInfo(bytes: ByteArray, fallbackWidth: Int, fallbackHeight: Int): JpegInfo {
        val info = PdfEngine.readJpegInfo(bytes, fallbackWidth, fallbackHeight)
        return JpegInfo(info.width, info.height, info.components)
    }

    /** Builds a one-page PDF: cropped JPEG image + optional title/note text. */
    fun buildPdf(
        jpeg: ByteArray,
        crop: Crop,
        imageWidth: Int,
        imageHeight: Int,
        title: String,
        note: String,
    ): ByteArray = PdfEngine.buildImagePdf(
        jpeg,
        crop.toPdfCrop(),
        imageWidth,
        imageHeight,
        title,
        note,
    )

    /** One imported page for [buildPdfPages]; each page carries its own original JPEG bytes. */
    data class Page(
        val jpeg: ByteArray,
        val crop: Crop = Crop.FULL,
        val imageWidth: Int,
        val imageHeight: Int,
        val title: String,
        val note: String = "",
    )

    /**
     * Builds one PDF page per imported JPEG, in list order. Every page embeds its own JPEG;
     * this function never fabricates extra pages from a single image. Callers that only have one
     * JPEG should keep using [buildPdf].
     *
     * [onProgress], when supplied, is invoked by the engine as `(completedPages, totalPages)`
     * while pages are serialized — it reports real work done, not a timer-driven estimate.
     */
    fun buildPdfPages(
        pages: List<Page>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): ByteArray = PdfEngine.buildImagePdfPages(
        pages.map { page ->
            PdfImagePage(
                jpeg = page.jpeg,
                crop = page.crop.toPdfCrop(),
                imageWidth = page.imageWidth,
                imageHeight = page.imageHeight,
                title = page.title,
                note = page.note,
            )
        },
        onProgress,
    )
}
