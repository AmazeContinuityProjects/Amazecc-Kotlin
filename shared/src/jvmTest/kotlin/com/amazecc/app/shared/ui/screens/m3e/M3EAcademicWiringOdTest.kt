package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless checks that OD entries come from the real OD-details payload, not an hour heuristic. */
class M3EAcademicWiringOdTest {

    private fun odRes() = OdDetailsRes(
        success = true,
        semesterId = "CH20262701",
        totalCount = 3,
        records = listOf(
            OdRecordItem(
                slNo = "1",
                type = "Lab",
                reason = "CSE3002",
                basis = "Practical",
                date = "12-Sep-2026",
                time = "09:00 AM - 11:00 AM",
                remarks = "On duty"
            ),
            OdRecordItem(
                slNo = "2",
                type = "OD",
                reason = "MAT2001",
                basis = "Theory",
                date = "08-Sep-2026",
                time = "2 hours"
            ),
            OdRecordItem(
                slNo = "3",
                type = "OD",
                reason = "PHY1001",
                basis = "Theory",
                date = "not a date",
                time = "unparseable"
            )
        )
    )

    @Test
    fun odHoursAreParsedFromRealTimeNotGuessed() {
        assertEquals(2, odHoursFrom("09:00 AM - 11:00 AM"))
        assertEquals(2, odHoursFrom("2 hours"))
        assertEquals(1, odHoursFrom("09:00-10:00"))
        assertEquals(2, odHoursFrom("09:00-10:30"))
        assertEquals(0, odHoursFrom("unparseable"))
        assertEquals(0, odHoursFrom(""))
    }

    @Test
    fun odTypeComesFromRealTypeAndBasis() {
        assertEquals("LAB", odTypeFor("Lab", "Practical"))
        assertEquals("LAB", odTypeFor("OD", "Lab session"))
        assertEquals("TH", odTypeFor("OD", "Theory"))
    }

    @Test
    fun odDaysUseRealRecordsAndPreserveAnnotations() {
        val tracker = mapOf("12-Sep-2026|CSE3002" to M3EODStatus.WASTED)
        val days = mapOdRecordsToDays(odRes(), tracker)

        assertEquals(3, days.size)
        // Real date parsing puts the two Sep dates ahead of the unparseable one.
        assertEquals("12-Sep-2026", days.first().date)
        assertEquals("not a date", days.last().date)

        val labDay = days.first { it.date == "12-Sep-2026" }
        val entry = labDay.entries.single()
        assertEquals("CSE3002", entry.courseCode)
        assertEquals("LAB", entry.type)
        assertEquals(2, entry.hours)
        assertEquals(M3EODStatus.WASTED, entry.status)

        val theoryDay = days.first { it.date == "08-Sep-2026" }
        assertEquals(2, theoryDay.entries.single().hours)
        assertEquals("TH", theoryDay.entries.single().type)
        assertEquals(M3EODStatus.NONE, theoryDay.entries.single().status)

        val broken = days.last()
        assertEquals(0, broken.entries.single().hours)
    }

    @Test
    fun odFailureOrEmptyPayloadYieldsNoFabricatedRows() {
        assertTrue(mapOdRecordsToDays(null, emptyMap()).isEmpty())
        assertTrue(
            mapOdRecordsToDays(
                OdDetailsRes(success = false, error = "offline", records = emptyList()),
                emptyMap()
            ).isEmpty()
        )
        assertTrue(mapOdRecordsToDays(OdDetailsRes(success = true), emptyMap()).isEmpty())
    }
}
