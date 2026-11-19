package com.amazecc.app.shared

import com.amazecc.app.shared.api.circularDownloadFileName
import com.amazecc.app.shared.api.percentDecodeUtf8
import com.amazecc.app.shared.model.CircularDownloadResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** JVM-side checks for the document download data-layer actions. */
class DataActionsDocumentTest {

    @Test
    fun parsesQuotedFilename() {
        assertEquals(
            "circular.pdf",
            circularDownloadFileName("attachment; filename=\"circular.pdf\"")
        )
    }

    @Test
    fun parsesRfc5987FilenameAndDecodesUtf8() {
        assertEquals(
            "Notice 2026.pdf",
            circularDownloadFileName("attachment; filename*=UTF-8''Notice%202026.pdf")
        )
        assertEquals(
            "नोटिस.pdf",
            circularDownloadFileName("attachment; filename*=UTF-8''%E0%A4%A8%E0%A5%8B%E0%A4%9F%E0%A4%BF%E0%A4%B8.pdf")
        )
    }

    @Test
    fun returnsNullForUnusableHeader() {
        assertNull(circularDownloadFileName(null))
        assertNull(circularDownloadFileName(""))
        assertNull(circularDownloadFileName("attachment"))
    }

    @Test
    fun percentDecodeKeepsMalformedEscapesLiteral() {
        assertEquals("100 done", percentDecodeUtf8("100%20done"))
        assertEquals("abc%ZZdef", percentDecodeUtf8("abc%ZZdef"))
        assertEquals("a+b", percentDecodeUtf8("a+b"))
    }

    @Test
    fun downloadResultCarriesBytesAndHeaders() {
        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF"
        val result = CircularDownloadResult(bytes = bytes, fileName = "notice.pdf", mimeType = "application/pdf")
        assertContentEquals(bytes, result.bytes)
        assertEquals("notice.pdf", result.fileName)
        assertEquals("application/pdf", result.mimeType)
    }
}
