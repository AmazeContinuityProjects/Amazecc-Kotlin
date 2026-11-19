package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless port tests for [VtopExamScraper].
 *
 * The scraper keeps all interpretation in Kotlin (the WebView only emits raw positional cells),
 * so exam-type grouping, cell mapping and the seat fallback are fully verifiable without a browser.
 */
class ParserPortExamTest {

    @Test
    fun `groups by exam type and maps positional cells`() {
        val payload = """
        {
          "ok": true,
          "semester": "CH20262701",
          "groups": [
            {"type": "CAT 1", "rows": [
              ["0","CS101","Data Structures","x","CLS1","A1","19-Nov-2026","FN","09:00 AM","09:30 AM","Room 1","R5C3","41"],
              ["1","MA101","Mathematics","x","CLS2","B1","20-Nov-2026","AN","02:00 PM","02:30 PM","Room 2","R2C1","9"]
            ]},
            {"type": "FAT", "rows": [
              ["0","PH101","Physics","x","CLS3","C1","10-Dec-2026","FN","09:00 AM","09:30 AM","Main Hall","R1C1","1"]
            ]}
          ]
        }
        """.trimIndent()

        val res = VtopExamScraper.parsePayload(payload)
        assertNotNull(res)
        assertTrue(res.success)
        assertEquals(setOf("CAT 1", "FAT"), res.schedule.keys, "exam types are data-driven keys")

        val cat1 = res.schedule.getValue("CAT 1")
        assertEquals(2, cat1.size)
        val first = cat1[0]
        assertEquals("CS101", first.courseCode)
        assertEquals("Data Structures", first.courseTitle)
        assertEquals("CLS1", first.classId)
        assertEquals("A1", first.slot)
        assertEquals("19-Nov-2026", first.examDate)
        assertEquals("FN", first.examSession)
        assertEquals("09:00 AM", first.reportingTime)
        assertEquals("09:30 AM", first.examTime)
        assertEquals("Room 1", first.venue)
        assertEquals("R5C3", first.seatLocation, "explicit seat location is preserved")
        assertEquals("41", first.seatNo)

        val fat = res.schedule.getValue("FAT")
        assertEquals(1, fat.size)
        assertEquals("PH101", fat[0].courseCode)
        assertEquals("R1C1", fat[0].seatLocation)
    }

    @Test
    fun `dash seat location with numeric seat number derives row-column`() {
        val payload = """
        {
          "ok": true,
          "groups": [{
            "type": "CAT 2",
            "rows": [
              ["0","CS101","Data Structures","x","C","S","D","E","F","G","H","-","41"],
              ["1","DH101","French Language","x","C","S","D","E","F","G","H","-","2"],
              ["2","EC101","Electronics","x","C","S","D","E","F","G","H","R7C2","30"]
            ]
          }]
        }
        """.trimIndent()

        val items = VtopExamScraper.parsePayload(payload)?.schedule?.getValue("CAT 2")
        assertNotNull(items)
        assertEquals(3, items.size)
        // n=41: group=2 -> C1=5, pos=4 -> row=3, even pos -> C1 => R3C5
        assertEquals("R3C5", items[0].seatLocation)
        assertEquals("41", items[0].seatNo)
        assertEquals("-", items[1].seatLocation, "exempt language course keeps '-'")
        assertEquals("R7C2", items[2].seatLocation, "a real location is never overwritten")
    }

    @Test
    fun `seat derivation helper handles boundaries and exempt courses`() {
        assertEquals("R1C1", VtopExamScraper.normalizeSeatLocation("-", "1", "Data Structures"))
        assertEquals("R1C2", VtopExamScraper.normalizeSeatLocation("- ", "2", "Data Structures"))
        assertEquals("R1C3", VtopExamScraper.normalizeSeatLocation("-", "19", "Data Structures"))
        assertEquals("-", VtopExamScraper.normalizeSeatLocation("-", "41", "Quantitative Aptitude"))
        assertEquals("-", VtopExamScraper.normalizeSeatLocation("-", "abc", "Data Structures"))
        assertEquals("-", VtopExamScraper.normalizeSeatLocation("-", "0", "Data Structures"))
        assertEquals("R4C6", VtopExamScraper.normalizeSeatLocation("R4C6", "12", "Data Structures"))
    }

    @Test
    fun `failed or empty payloads never fabricate rows`() {
        assertNull(VtopExamScraper.parsePayload(""))
        assertNull(VtopExamScraper.parsePayload("not json"))
        assertNull(VtopExamScraper.parsePayload("""{"ok":false,"error":"not authorized"}"""))

        val noRows = VtopExamScraper.parsePayload("""{"ok":true,"groups":[{"type":"CAT 1","rows":[]}]}""")
        assertNotNull(noRows)
        assertTrue(noRows.schedule.getValue("CAT 1").isEmpty())

        // A row without a course code is noise, not an exam.
        val noise = VtopExamScraper.parsePayload(
            """{"ok":true,"groups":[{"type":"CAT 1","rows":[["0","","","x"]]}]}"""
        )
        assertNotNull(noise)
        assertTrue(noise.schedule.getValue("CAT 1").isEmpty())
    }
}
