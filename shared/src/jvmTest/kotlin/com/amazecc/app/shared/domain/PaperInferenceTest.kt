package com.amazecc.app.shared.domain

import com.amazecc.app.shared.utils.inferPaperDetails
import com.amazecc.app.shared.utils.recognizeDocumentText
import com.amazecc.app.shared.utils.suggestedDocumentName
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-166 — pure naming contract plus the honest "unsupported platform" behaviour. */
class PaperInferenceTest {

    @Test
    fun `null inference uses the ASCII-safe fallback`() {
        assertEquals("Scanned-Document", suggestedDocumentName(null))
    }

    @Test
    fun `empty inference never returns a blank name`() {
        assertEquals("Scanned-Document", suggestedDocumentName(PaperInference(suggestedName = "")))
        assertEquals(
            "Scanned-Document",
            suggestedDocumentName(PaperInference(suggestedName = "   ", subjectCode = " "))
        )
    }

    @Test
    fun `full fields prefer code then assessment then date`() {
        val name = suggestedDocumentName(
            PaperInference(
                subjectCode = "BCSE203E",
                subject = "Database Systems",
                assessmentType = "CIA-1",
                documentDate = "2026-04-12",
                suggestedName = "this should not win"
            )
        )
        assertEquals("BCSE203E-CIA-1-2026-04-12", name)
    }

    @Test
    fun `subject name is used when no subject code exists`() {
        val name = suggestedDocumentName(
            PaperInference(subject = "Database Systems", assessmentType = " CIA / 1 ", suggestedName = "")
        )
        assertEquals("Database-Systems-CIA-1", name)
    }

    @Test
    fun `illegal filename characters are sanitised`() {
        val name = suggestedDocumentName(
            PaperInference(
                subjectCode = "CS/SE:203?*",
                assessmentType = "CIA|1",
                documentDate = "12/04/2026",
                suggestedName = "x"
            )
        )
        assertEquals("CS-SE-203-CIA-1-12-04-2026", name)
        assertTrue(name.all { it.isAscii() })
    }

    @Test
    fun `model suggested name is a last resort and is sanitised`() {
        assertEquals(
            "Midterm-Paper-Final",
            suggestedDocumentName(PaperInference(suggestedName = "Midterm Paper (Final)"))
        )
    }

    @Test
    fun `absent tokens are treated as missing data`() {
        assertEquals(
            "Scanned-Document",
            suggestedDocumentName(
                PaperInference(subjectCode = "null", assessmentType = "N/A", documentDate = "", suggestedName = "none")
            )
        )
    }

    @Test
    fun `overlong names are bounded`() {
        val name = suggestedDocumentName(PaperInference(subjectCode = "A".repeat(120), suggestedName = ""))
        assertEquals(80, name.length)
        assertEquals("A".repeat(80), name)
    }

    @Test
    fun `unsupported platforms return empty or null instead of fake inference`() = runBlocking {
        assertTrue(recognizeDocumentText(byteArrayOf(1, 2, 3)).isEmpty())
        assertNull(inferPaperDetails("CIA-1 Database Systems"))
    }

    private fun Char.isAscii(): Boolean = code in 0..127
}
