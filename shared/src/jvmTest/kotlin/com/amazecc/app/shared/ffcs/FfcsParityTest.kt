package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.state.FfcsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-065: the FFCS upgrade must be backed by real behaviour, not just new UI controls.
 * Covers the generator preferences, RFC 5545 export and the ViewModel's real-data path.
 */
class FfcsParityTest {

    private fun offering(code: String, slot: String, faculty: String = "Dr. A") = ParsedCourse(
        code = code,
        title = "Course $code",
        type = "Theory",
        credits = "3",
        room = "SJT-101",
        slot = slot,
        faculty = faculty
    )

    @Test
    fun closeGaps_ranksGapFreeTimetableFirst() = runTest {
        // "L1+L3" is MON-only (08:00 and 09:50) -> one 60-minute gap.
        // "A1+F1" is contiguous on MON and WED -> zero gaps.
        val ranked = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(
                listOf(offering("CSE1001", "L1+L3"), offering("CSE1001", "A1+F1"))
            ),
            options = FfcsEngine.GenerateOptions(closeGaps = true)
        )

        assertEquals(2, ranked.size, "Both clash-free options must be kept.")
        assertEquals(0, ranked.first().metrics.gaps, "Gap-free option must rank first.")
        assertTrue(ranked.any { it.metrics.gaps > 0 }, "The gappy option must still be present.")
    }

    @Test
    fun lateStart_ranksLaterFirstClassFirst() = runTest {
        // A1 starts 08:00 on MON; A2 starts 14:00 (TimeMath VIT afternoon heuristic).
        val ranked = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(
                listOf(offering("CSE1001", "A1"), offering("CSE1001", "A2"))
            ),
            options = FfcsEngine.GenerateOptions(lateStart = true)
        )

        assertEquals(2, ranked.size)
        assertTrue(
            ranked.first().firstStartMin > ranked.last().firstStartMin,
            "Late-start preference must rank the later first class first."
        )
    }

    @Test
    fun earlyFinish_ranksEarlierLastClassFirst() = runTest {
        // Input order is deliberately late-first: only the filter should reorder these.
        val ranked = FfcsEngine.generateTimetables(
            optionsPerCourse = listOf(
                listOf(offering("CSE1001", "A2"), offering("CSE1001", "A1"))
            ),
            options = FfcsEngine.GenerateOptions(earlyFinish = true)
        )

        assertEquals(2, ranked.size)
        assertTrue(
            ranked.first().lastEndMin < ranked.last().lastEndMin,
            "Early-finish preference must rank the earlier last class first."
        )
    }

    @Test
    fun buildFfcsIcs_emitsRfc5545UtcStampAndOneEventPerClassPeriod() {
        val course = AddedCourse(
            id = "tt0_CSE1001_0",
            code = "CSE1001",
            title = "Programming",
            slots = listOf("A1"), // MON 08:00, WED 08:55
            faculty = "Dr. A",
            venue = "SJT-101",
            credits = "3",
            type = "Theory",
            color = "#2563EB"
        )

        val ics = requireNotNull(FfcsEngine.buildFfcsIcs(listOf(course)))
        assertTrue(
            ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"),
            "ICS must start with VCALENDAR and VERSION:2.0."
        )
        assertTrue(ics.endsWith("END:VCALENDAR"), "ICS must end with END:VCALENDAR.")
        val lines = ics.split("\r\n")
        assertEquals(2, lines.count { it == "BEGIN:VEVENT" }, "A1 yields MON and WED class periods.")
        val stamps = lines.filter { it.startsWith("DTSTAMP:") }
        assertEquals(2, stamps.size, "Every VEVENT must carry a DTSTAMP.")
        assertTrue(
            stamps.all { Regex("^DTSTAMP:\\d{8}T\\d{6}Z$").matches(it) },
            "DTSTAMP must be a UTC basic-format timestamp with a trailing Z."
        )
        assertTrue(
            lines.any { Regex("^DTSTART:\\d{8}T\\d{6}$").matches(it) },
            "DTSTART must be a basic-format local date-time."
        )
        assertTrue(
            lines.any { it.startsWith("SUMMARY:CSE1001 - Programming") },
            "SUMMARY must use the web parity format \${code} - \${title}."
        )
        assertTrue(lines.any { it == "LOCATION:SJT-101" }, "LOCATION must be the real venue.")
        assertTrue(lines.any { it == "DESCRIPTION:Dr. A" }, "DESCRIPTION must be the real faculty.")
        assertNull(FfcsEngine.buildFfcsIcs(emptyList()), "Empty plans must not produce an ICS file.")
    }

    @Test
    fun viewModel_seedsFromRealCourses_andGenerates() = runBlocking {
        FfcsViewModel.resetSelection()
        FfcsViewModel.initFromCourses(
            listOf(
                offering("CSE1001", "A1"),
                offering("CSE1001", "A2"),
                offering("MAT2001", "B1")
            )
        )

        val offerings = withTimeout(5_000) {
            FfcsViewModel.courseOfferings.first { it.isNotEmpty() }
        }
        assertEquals(setOf("CSE1001", "MAT2001"), offerings.map { it.first }.toSet())
        assertEquals(2, offerings.first { it.first == "CSE1001" }.second.size, "Two real offerings kept.")

        val locks = withTimeout(5_000) { FfcsViewModel.locks.first { it.isNotEmpty() } }
        assertEquals(2, locks.size, "One target locker per seeded course.")

        FfcsViewModel.selectCourse("CSE1001", true)
        val selected = withTimeout(5_000) { FfcsViewModel.selectedCodes.first { "CSE1001" in it } }
        assertTrue("CSE1001" in selected)

        FfcsViewModel.generate()
        val generated = withTimeout(5_000) {
            FfcsViewModel.generatedTimetables.first { it.isNotEmpty() }
        }
        assertEquals(2, generated.size, "Both clash-free options for the selected course generated.")
        assertTrue(generated.all { it.courses.single().code == "CSE1001" })
        assertNotNull(FfcsViewModel.exportIcs(), "Generated timetable can be exported as ICS.")
        FfcsViewModel.resetSelection()
    }
}
