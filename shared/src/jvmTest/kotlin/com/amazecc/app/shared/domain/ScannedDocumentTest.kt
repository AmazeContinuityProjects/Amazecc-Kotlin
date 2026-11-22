package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-174 — pure, host-independent guards for the auto-scan result model.
 *
 * No Android device, ML Kit Task or OpenCV native library is involved. These are the rules the
 * scanner host relies on when deciding whether a result is a real processed page or a cancelled
 * scan; platform scanning behaviour is intentionally not faked in a JVM test.
 */
class ScannedDocumentTest {

    @Test
    fun defaultsDescribeAnEmptyUnprocessedResult() {
        val doc = ScannedDocument()

        assertTrue(doc.imageBytes.isEmpty())
        assertNull(doc.pdfBytes)
        assertEquals(ScanSource.ML_KIT, doc.source)
        assertNull(doc.notice)
    }

    @Test
    fun explicitValuesArePreserved() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF
        val doc = ScannedDocument(
            imageBytes = jpeg,
            pdfBytes = pdf,
            source = ScanSource.OPENCV,
            notice = "Detected edges and straightened with OpenCV fallback",
        )

        assertTrue(doc.imageBytes.contentEquals(jpeg))
        assertTrue(doc.pdfBytes!!.contentEquals(pdf))
        assertEquals(ScanSource.OPENCV, doc.source)
        assertEquals("Detected edges and straightened with OpenCV fallback", doc.notice)
    }

    @Test
    fun theSourceEnumHasExactlyTheTwoRealEngines() {
        assertEquals(listOf(ScanSource.ML_KIT, ScanSource.OPENCV), ScanSource.entries)
        assertEquals(2, ScanSource.entries.size)
    }

    @Test
    fun equalityIsByContentAndCopyKeepsValues() {
        val doc = ScannedDocument(
            imageBytes = byteArrayOf(1, 2, 3),
            pdfBytes = byteArrayOf(4, 5),
            source = ScanSource.ML_KIT,
            notice = "Straightened and cleaned by ML Kit Document Scanner",
        )

        // Kotlin ByteArray uses reference equality in data classes; compare bytes explicitly.
        assertEquals(doc, doc.copy(), "copy preserves the same byte instances")
        val sameValues = ScannedDocument(
            imageBytes = byteArrayOf(1, 2, 3),
            pdfBytes = byteArrayOf(4, 5),
            source = ScanSource.ML_KIT,
            notice = "Straightened and cleaned by ML Kit Document Scanner",
        )
        assertTrue(doc.imageBytes.contentEquals(sameValues.imageBytes))
        assertTrue(doc.pdfBytes!!.contentEquals(sameValues.pdfBytes!!))
        assertEquals(doc.source, sameValues.source)
        assertEquals(doc.notice, sameValues.notice)
        assertTrue(!doc.imageBytes.contentEquals(byteArrayOf(9, 9, 9)))
        assertNotEquals(doc, doc.copy(source = ScanSource.OPENCV))
        assertNotEquals(doc, doc.copy(notice = null))
    }

    @Test
    fun pdfIsIndependentOfTheJpegPage() {
        val page = byteArrayOf(10, 20)
        val withoutPdf = ScannedDocument(imageBytes = page)
        val withPdf = withoutPdf.copy(pdfBytes = byteArrayOf(30, 40))

        assertNull(withoutPdf.pdfBytes)
        assertTrue(withPdf.pdfBytes!!.isNotEmpty())
        assertTrue(withPdf.imageBytes.contentEquals(withoutPdf.imageBytes))
        assertTrue(withPdf.copy(pdfBytes = null).pdfBytes == null)
    }
}
