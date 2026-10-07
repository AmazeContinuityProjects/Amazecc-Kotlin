package com.amazecc.app.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The port of the `buildAttendanceByDate` / `buildAttendanceLog` / `filterLog` / `summariseOd` /
 * `synthesiseDay` suites of `AmazeCC/src/__tests__/calendarDay.test.ts`.
 *
 * This logic used to live inside a page component, where it had no test at all - the only way to
 * find out whether the half-day rules were right was to be enrolled in the semester they describe.
 */
class CalendarAttendanceTest {

    private fun course(
        code: String,
        title: String = code,
        slotName: String = "A1",
        entries: List<Pair<String, String>>,
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = "Embedded Theory",
        slotName = slotName,
        slotVenue = null,
        faculty = "",
        credits = null,
        category = null,
        attendedClasses = 0,
        totalClasses = 0,
        attendancePercentage = "",
        logs = entries.map { AttendanceDay(date = it.first, status = it.second) },
    )

    /** 8am and 2pm, i.e. one class in each half of the day. */
    private val startMinutes: (String) -> Int? = { code ->
        if (code.startsWith("AM")) 8 * 60 else 14 * 60
    }

    private fun iso(day: Int): String = "2026-08-" + (if (day < 10) "0$day" else "$day")

    // ── buildAttendanceByDate ────────────────────────────────────────────────

    @Test
    fun `buckets one day across every course`() {
        val byDate = buildAttendanceByDate(
            listOf(
                course("25BLC1081", "Biology", entries = listOf(iso(12) to "Present", iso(13) to "Absent")),
                course("25BLC1081(L)", "Biology Lab", entries = listOf(iso(12) to "On Duty")),
            ),
        )

        val twelfth = byDate.getValue(iso(12))
        assertEquals(2, twelfth.held)
        assertEquals(1, twelfth.present)
        assertEquals(1, twelfth.onDuty)
        assertEquals(0, twelfth.absent)

        assertEquals(1, byDate.getValue(iso(13)).absent)
    }

    @Test
    fun `keeps the raw VTOP date so the notes tracker stays compatible`() {
        // The Theory and Lab log pages key the notes tracker on this string.
        val byDate = buildAttendanceByDate(listOf(course("25BLC1081", "Biology", entries = listOf("Aug 12, 2026" to "Absent"))))
        assertEquals("Aug 12, 2026", byDate.getValue("2026-08-12").courses.first().rawDate)
    }

    @Test
    fun `ignores a course with no history`() {
        assertEquals(0, buildAttendanceByDate(listOf(course("X", entries = emptyList()))).size)
        assertEquals(0, buildAttendanceByDate(emptyList()).size)
    }

    @Test
    fun `finds one course's status on a day`() {
        val byDate = buildAttendanceByDate(listOf(course("25BLC1081", entries = listOf(iso(12) to "Absent"))))
        assertEquals("Absent", statusForClass(byDate, iso(12), "25BLC1081"))
        assertNull(statusForClass(byDate, iso(12), "OTHER"))
        assertNull(statusForClass(byDate, iso(20), "25BLC1081"))
    }

    // ── buildAttendanceLog ───────────────────────────────────────────────────

    private fun log(vararg courses: CourseAttendance, now: LocalDate = LocalDate(2026, 8, 15)) =
        buildAttendanceLog(buildAttendanceByDate(courses.toList()), startMinutes, now)

    @Test
    fun `calls an untouched day a full day`() {
        val rows = log(
            course("AM-1", entries = listOf(iso(10) to "Present")),
            course("PM-1", entries = listOf(iso(10) to "Present")),
        )
        assertEquals(1, rows.size)
        assertEquals(LogStatus.PRESENT, rows[0].status)
        assertFalse(rows[0].isMissed)
        assertEquals("emerald", rows[0].tone)
    }

    @Test
    fun `distinguishes a morning miss from an evening miss`() {
        // This is the whole reason the log exists: "you missed a class" does not tell you whether
        // you lost the morning.
        val morningMiss = log(
            course("AM-1", entries = listOf(iso(10) to "Absent")),
            course("PM-1", entries = listOf(iso(10) to "Present")),
        )
        assertEquals(LogStatus.MORNING_HALF_DAY, morningMiss[0].status)
        assertEquals("amber", morningMiss[0].tone)

        val eveningMiss = log(
            course("AM-1", entries = listOf(iso(10) to "Present")),
            course("PM-1", entries = listOf(iso(10) to "Absent")),
        )
        assertEquals(LogStatus.EVENING_HALF_DAY, eveningMiss[0].status)
    }

    @Test
    fun `calls a total miss an absent day`() {
        val rows = log(
            course("AM-1", entries = listOf(iso(10) to "Absent")),
            course("PM-1", entries = listOf(iso(10) to "Absent")),
        )
        assertEquals(LogStatus.ABSENT, rows[0].status)
        assertEquals("red", rows[0].tone)
        assertEquals(2, rows[0].missedClasses.size)
    }

    @Test
    fun `does not let an on-duty read as a missed class in the verdict`() {
        // An approved absence is not a missed one, so the evening half is clean and the day reads
        // as a morning miss - not as wholly absent. It is still "missed" for notes, because there
        // are no notes for an OD either.
        val rows = log(
            course("AM-1", entries = listOf(iso(10) to "Absent")),
            course("PM-1", entries = listOf(iso(10) to "On Duty")),
        )
        assertEquals(LogStatus.MORNING_HALF_DAY, rows[0].status)
        assertTrue(rows[0].isMissed)
        assertEquals(2, rows[0].missedClasses.size)
    }

    @Test
    fun `calls a day with only on-duty a partial OD`() {
        val rows = log(
            course("AM-1", entries = listOf(iso(10) to "Present")),
            course("PM-1", entries = listOf(iso(10) to "On Duty")),
        )
        assertEquals(LogStatus.PARTIAL_OD, rows[0].status)
        assertEquals("amber", rows[0].tone)
    }

    @Test
    fun `orders newest first and flags the future`() {
        val rows = buildAttendanceLog(
            buildAttendanceByDate(
                listOf(
                    course(
                        "AM-1",
                        entries = listOf(iso(10) to "Present", iso(12) to "Present", iso(11) to "Present"),
                    ),
                ),
            ),
            startMinutes,
            LocalDate(2026, 8, 11),
        )
        assertEquals(listOf(iso(12), iso(11), iso(10)), rows.map { it.dateKey })
        assertTrue(rows[0].isFuture)
        assertFalse(rows[2].isFuture)
    }

    @Test
    fun `survives a course whose slot time is unknown`() {
        // No slot match -> no start time -> treated as morning, never crashing on a null.
        val rows = buildAttendanceLog(
            buildAttendanceByDate(listOf(course("AM-1", entries = listOf(iso(10) to "Absent")))),
            { null },
        )
        assertEquals(LogStatus.MORNING_HALF_DAY, rows[0].status)
    }

    // ── filterLog ────────────────────────────────────────────────────────────

    private val filterRows = buildAttendanceLog(
        buildAttendanceByDate(
            listOf(course("A", entries = listOf(iso(10) to "Present", iso(12) to "Absent"))),
        ),
        { 8 * 60 },
        LocalDate(2026, 8, 11),
    )

    @Test
    fun `keeps everything under all`() {
        assertEquals(2, filterLog(filterRows, LogFilter.ALL).size)
    }

    @Test
    fun `splits by whether a class was missed`() {
        assertEquals(listOf(iso(12)), filterLog(filterRows, LogFilter.MISSED).map { it.dateKey })
        assertEquals(listOf(iso(10)), filterLog(filterRows, LogFilter.PRESENT).map { it.dateKey })
    }

    @Test
    fun `separates the future`() {
        assertEquals(listOf(iso(12)), filterLog(filterRows, LogFilter.UPCOMING).map { it.dateKey })
    }

    // ── summariseOd ──────────────────────────────────────────────────────────

    private val od = listOf(
        OdRecord(
            date = "2026-08-12",
            total = 3,
            courses = listOf(OdCourse("Biology", "TH"), OdCourse("Biology Lab", "LAB")),
        ),
    )

    private fun tracker(vararg entries: Pair<String, String>) =
        mapOf("2026-08-12" to entries.map { TrackedOd(it.first, it.second) })

    @Test
    fun `counts a lab as two hours`() {
        val s = summariseOd(od, emptyMap())
        assertEquals(3, s.totalHours)
        assertEquals(3, s.validHours)
        assertEquals(0, s.wastedHours)
    }

    @Test
    fun `moves a tracked-wasted OD out of the valid total`() {
        val s = summariseOd(od, tracker("Biology" to "wasted"))
        assertEquals(1, s.wastedHours)
        assertEquals(1, s.wastedCount)
        assertEquals(2, s.validHours)
    }

    @Test
    fun `still counts a recovered OD as valid because it was earned`() {
        val s = summariseOd(od, tracker("Biology" to "recovered"))
        assertEquals(1, s.recoveredHours)
        assertEquals(3, s.validHours)
    }

    @Test
    fun `matches a tracked course on containment either way round`() {
        val s = summariseOd(od, tracker("biology lab" to "wasted"))
        assertEquals(2, s.wastedHours)
    }

    @Test
    fun `does not let a theory course's status leak onto its own lab`() {
        // "Biology" is a substring of "Biology Lab". Matching on containment alone marked both
        // wasted and double-counted the hours - a bug the old summarise carried verbatim.
        val s = summariseOd(od, tracker("Biology" to "wasted"))
        assertEquals(1, s.wastedHours)
        assertEquals(2, s.validHours)
    }

    @Test
    fun `prefers an exact match when one exists`() {
        val s = summariseOd(
            od,
            tracker("Biology" to "wasted", "Biology Lab" to "recovered"),
        )
        // The theory hour is wasted and drops out of the valid total; the lab is recovered, which
        // means it was earned, so it stays in.
        assertEquals(1, s.wastedHours)
        assertEquals(2, s.recoveredHours)
        assertEquals(2, s.validHours)
    }

    @Test
    fun `does not let one tracked entry claim two records`() {
        // Two tracked entries, neither an exact match for either record. The first claims the
        // closer substring; the second takes what is left. Each record is claimed once, so hours
        // are never counted twice.
        val s = summariseOd(od, tracker("Biolo" to "wasted", "logy Lab" to "wasted"))
        assertEquals(3, s.wastedHours)
        assertEquals(2, s.wastedCount)
    }

    @Test
    fun `claims nothing for an unrelated course title`() {
        val s = summariseOd(od, tracker("Chemistry" to "wasted"))
        assertEquals(0, s.wastedHours)
        assertEquals(3, s.validHours)
    }

    @Test
    fun `is all zeroes for no data`() {
        assertEquals(0, summariseOd(emptyList()).totalHours)
        assertEquals(0, summariseOd(emptyList()).validHours)
        assertEquals(0, summariseOd(emptyList()).wastedHours)
    }

    // ── odRecordsFrom ────────────────────────────────────────────────────────

    @Test
    fun `derives the OD list from attendance the way the OD screen does`() {
        val records = odRecordsFrom(
            listOf(
                course("25BLC1081", "Biology", slotName = "TA1", entries = listOf("2026-08-12" to "On Duty")),
                course("25BLC1081(L)", "Biology Lab", slotName = "L1+L2", entries = listOf("2026-08-12" to "On Duty")),
                course("25BLC1081(L)", "Biology Lab", slotName = "L1+L2", entries = listOf("2026-08-13" to "Absent")),
                course("25CH1001", "Chemistry", slotName = "TA2", entries = listOf("2026-08-13" to "OD")),
            ),
        )

        assertEquals(2, records.size)
        val twelfth = records.first { it.date == "2026-08-12" }
        assertEquals(listOf("TH", "LAB"), twelfth.courses.map { it.type })
        assertEquals(3, twelfth.total)

        val thirteenth = records.first { it.date == "2026-08-13" }
        assertEquals(listOf("Chemistry"), thirteenth.courses.map { it.title })
        assertEquals(1, thirteenth.total)
    }

    // ── synthesiseDay ────────────────────────────────────────────────────────

    @Test
    fun `rebuilds a day the published calendar does not cover`() {
        val byDate = buildAttendanceByDate(listOf(course("A", "Biology", entries = listOf("2026-06-30" to "Absent"))))
        val day = synthesiseDay(byDate.getValue("2026-06-30"))
        assertEquals("2026-06-30", day.dateKey)
        assertEquals(30, day.date)
        assertEquals(1, day.attendance.absent)
        assertTrue(day.events.any { it.kind == EventKind.CLASS })
    }

    @Test
    fun `does not produce an invalid date from an empty record`() {
        val day = synthesiseDay(DayAttendance())
        assertTrue(isValidDay(day.fullDate))
    }
}
