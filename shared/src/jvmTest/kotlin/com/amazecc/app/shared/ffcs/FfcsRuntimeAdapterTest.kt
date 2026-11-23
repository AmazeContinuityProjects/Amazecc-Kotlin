package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.AddedCourse as PlannerAddedCourse
import com.amazecc.app.shared.ffcs.planner.DashDetail as PlannerDashDetail
import com.amazecc.app.shared.ffcs.planner.DayId
import com.amazecc.app.shared.ffcs.planner.GapDetail as PlannerGapDetail
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as PlannerParsedCourse
import com.amazecc.app.shared.ffcs.planner.Preference
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableMetrics as PlannerMetrics
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-183 — pins the legacy↔canonical runtime bridge:
 * exact UPPERCASE CSV cells, CREDITS as String, '+'-joined SLOT, honest
 * absences for canonical-only identifiers and ranking scores, derived lock
 * filters that never invent data, and the minutes→hours metrics boundary.
 */
class FfcsRuntimeAdapterTest {

    private val legacyCourse = ParsedCourse(
        code = "BACSE211",
        title = "Robotics: Kinematics, Dynamics and Motion Control",
        type = "ETH",
        credits = "3",
        room = "AB3-308",
        slot = "B1+TB1",
        faculty = "MUTHUKUMAR AN K",
    )

    private fun params(
        courses: List<ParsedCourse> = listOf(legacyCourse),
        locks: List<CourseLock> = emptyList(),
        blockedSlots: Set<String> = emptySet(),
        options: FfcsEngine.GenerateOptions = FfcsEngine.GenerateOptions(),
        rankingFactors: List<RankingFactor>? = null,
        facultyPriority: Map<String, List<String>> = emptyMap(),
    ) = FfcsRuntimeAdapter.buildPlannerGenerateParams(
        courses = courses,
        locks = locks,
        blockedSlots = blockedSlots,
        options = options,
        rankingFactors = rankingFactors,
        facultyPriority = facultyPriority,
    )

    // ── ParsedCourse round trip ─────────────────────────────────────────────

    @Test
    fun `round trip preserves every required field and exact CSV spellings`() {
        val canonical = FfcsRuntimeAdapter.toPlannerParsed(legacyCourse)

        assertEquals("BACSE211", canonical.CODE)
        assertEquals("Robotics: Kinematics, Dynamics and Motion Control", canonical.TITLE)
        assertEquals("ETH", canonical.TYPE)
        assertEquals("3", canonical.CREDITS, "CREDITS stays a String")
        assertEquals("AB3-308", canonical.ROOM)
        assertEquals("B1+TB1", canonical.SLOT, "SLOT stays one '+'-joined string")
        assertEquals("MUTHUKUMAR AN K", canonical.FACULTY)
        assertEquals(listOf("B1", "TB1"), com.amazecc.app.shared.ffcs.planner.splitSlots(canonical.SLOT))

        // The legacy model has no canonical-only cells, so none is invented.
        assertNull(canonical.ORIGINAL_CODE)
        assertNull(canonical.LINK_ID)
        assertNull(canonical.BATCH)
        assertNull(canonical.CLASS_ID)
        assertNull(canonical.CLASS_IDS)
        assertNull(canonical.ERP_ID)
        assertNull(canonical.EMPLOYEE_SCHOOL)

        assertEquals(legacyCourse, FfcsRuntimeAdapter.fromPlannerParsed(canonical))
    }

    @Test
    fun `canonical identifiers survive on the canonical side and are not fabricated on the legacy side`() {
        val canonical = PlannerParsedCourse(
            CODE = "BACSE211",
            TITLE = "Robotics",
            TYPE = "ETH",
            CREDITS = "3",
            ROOM = "AB3-308",
            SLOT = "B1+TB1",
            FACULTY = "MUTHUKUMAR AN K",
            ORIGINAL_CODE = "BACSE211",
            LINK_ID = "shared:BACSE211:t:l",
            BATCH = "B26",
            CLASS_ID = "CH2026270501792",
            CLASS_IDS = listOf("CH2026270501792", "CH2026270501793"),
            ERP_ID = "50191",
            EMPLOYEE_SCHOOL = "SCOPE",
        )

        val legacy = FfcsRuntimeAdapter.fromPlannerParsed(canonical)
        assertEquals("BACSE211", legacy.code)
        assertEquals("Robotics", legacy.title)
        assertEquals("ETH", legacy.type)
        assertEquals("3", legacy.credits)
        assertEquals("AB3-308", legacy.room)
        assertEquals("B1+TB1", legacy.slot)
        assertEquals("MUTHUKUMAR AN K", legacy.faculty)

        // The legacy shape has no ERP_ID / CLASS_ID field: the bridge never
        // folds them into an unrelated cell or makes one up on the way back.
        val back = FfcsRuntimeAdapter.toPlannerParsed(legacy)
        assertNull(back.ERP_ID)
        assertNull(back.CLASS_ID)
        assertNull(back.CLASS_IDS)
        assertNull(back.BATCH)
        assertNull(back.LINK_ID)

        // Nothing is mutated, so a caller that must keep these ids can stay
        // on the canonical side of the seam.
        assertEquals("50191", canonical.ERP_ID)
        assertEquals("CH2026270501792", canonical.CLASS_ID)
        assertEquals("MUTHUKUMAR AN K|B1+TB1|AB3-308|50191", canonical.offeringId())
    }

    @Test
    fun `empty optional cells stay empty in both directions`() {
        val blank = ParsedCourse(code = "", title = "", type = "", credits = "", room = "", slot = "", faculty = "")
        val canonical = FfcsRuntimeAdapter.toPlannerParsed(blank)
        assertEquals(blank, FfcsRuntimeAdapter.fromPlannerParsed(canonical))
        assertEquals("", canonical.CREDITS)
        assertEquals("", canonical.SLOT)
        assertNull(canonical.ERP_ID)
        assertNull(canonical.CLASS_ID)
    }

    // ── GenerateParams ───────────────────────────────────────────────────────

    @Test
    fun `params carry supplied ranking factors and faculty priority`() {
        val factors = listOf(RankingFactor.GAPS, RankingFactor.EARLY_FINISH)
        val priority = mapOf("BACSE211" to listOf("MUTHUKUMAR AN K", "Dr. Rao"))
        val built = params(rankingFactors = factors, facultyPriority = priority)

        assertEquals(factors, built.rankingFactors?.order)
        assertTrue(built.rankingFactors!!.disabled.isEmpty())
        assertEquals(priority, built.facultyPriority)
        assertNull(built.rankingMode, "one ranking path per call, never stacked")
        assertEquals(listOf(FfcsRuntimeAdapter.toPlannerParsed(legacyCourse)), built.masterCourses)

        // Real data and honest absences everywhere else.
        assertTrue(built.schema.theory.isNotEmpty(), "the real Chennai schema is projected, not an empty shell")
        assertTrue(built.schema.lab.isNotEmpty())
        assertTrue(built.friends.isEmpty())
        assertEquals(Preference.NONE, built.preference)
        assertFalse(built.syncFriendClasses)
        assertEquals(0, built.minHalfDays)
    }

    @Test
    fun `legacy toggles derive ranking factors only when none was supplied`() {
        val allOn = params(options = FfcsEngine.GenerateOptions(closeGaps = true, lateStart = true, earlyFinish = true))
        assertEquals(
            listOf(RankingFactor.GAPS, RankingFactor.LATE_START, RankingFactor.EARLY_FINISH),
            allOn.rankingFactors?.order,
        )
        assertEquals(SortBy.COMPACTNESS, allOn.sortBy)

        val plain = params(options = FfcsEngine.GenerateOptions())
        assertNull(plain.rankingFactors)
        assertEquals(SortBy.BALANCED, plain.sortBy)

        val explicitlyOff = params(
            options = FfcsEngine.GenerateOptions(closeGaps = true),
            rankingFactors = emptyList(),
        )
        assertNull(explicitlyOff.rankingFactors, "an explicitly empty list is honoured, not overwritten")
    }

    @Test
    fun `blocked slots and empty faculty priority are passed through without invention`() {
        val blocked = setOf("A1", "a2")
        val built = params(blockedSlots = blocked)
        assertEquals(blocked, built.blockedSlots.toSet())
        assertTrue(built.courseLocks.isEmpty())
        assertNull(built.facultyPriority)
        assertNull(built.rankingFactors)
    }

    // ── CourseLock heuristics ───────────────────────────────────────────────

    @Test
    fun `locks split offering keys into slots and faculty while keeping exact ids`() {
        val key = "MUTHUKUMAR AN K|B1+TB1|AB3-308"
        val mapped = params(locks = listOf(CourseLock("BACSE211", "Robotics", listOf(key))))
            .courseLocks.single()

        assertEquals(listOf(key), mapped.offerings)
        assertEquals(listOf("MUTHUKUMAR AN K"), mapped.allowedFaculty)
        assertEquals(listOf("B1", "TB1"), mapped.allowedSlots)
        assertEquals("BACSE211", mapped.code)
        assertEquals("Robotics", mapped.title)
    }

    @Test
    fun `code shaped leading segments are not mistaken for faculty names`() {
        val embeddedKey = "BACSE211(L)|Dr. Lab|L1|R2"
        val mapped = params(locks = listOf(CourseLock("BACSE211", "Robotics", listOf(embeddedKey))))
            .courseLocks.single()

        assertEquals(
            listOf("Dr. Lab"),
            mapped.allowedFaculty,
            "the leading code is skipped, but a real name in that key is still a faculty filter",
        )
        assertEquals(listOf("L1"), mapped.allowedSlots, "the slot comes from the slot segment, not the faculty cell")
        assertEquals(listOf(embeddedKey), mapped.offerings)

        val codeInFacultyPosition = params(
            locks = listOf(CourseLock("BACSE211", "Robotics", listOf("BACSE211|L1|A1|R2"))),
        ).courseLocks.single()
        assertTrue(codeInFacultyPosition.allowedFaculty.isEmpty(), "a code-shaped segment is never a faculty filter")
        assertEquals(listOf("A1"), codeInFacultyPosition.allowedSlots)
    }

    @Test
    fun `locks with no offerings and unrelated keys stay unrestricted`() {
        val empty = params(locks = listOf(CourseLock("BACSE211", "Robotics")))
            .courseLocks.single()
        assertTrue(empty.allowedSlots.isEmpty())
        assertTrue(empty.allowedFaculty.isEmpty())
        assertNull(empty.offerings)

        val unrelated = params(locks = listOf(CourseLock("BACSE211", "Robotics", listOf("SOMEBODY|A1|R1"))))
            .courseLocks.single()
        assertEquals(listOf("SOMEBODY|A1|R1"), unrelated.offerings)
        assertEquals(listOf("SOMEBODY"), unrelated.allowedFaculty)
        assertEquals(listOf("A1"), unrelated.allowedSlots)
    }

    // ── Settled timetables and metrics ───────────────────────────────────────

    @Test
    fun `to legacy timetable preserves course count and adapted metrics`() {
        val course = PlannerAddedCourse(
            id = "c1",
            code = "BACSE211",
            title = "Robotics",
            slots = listOf("B1", "TB1"),
            faculty = "MUTHUKUMAR AN K",
            venue = "AB3-308",
            credits = "3",
            type = "ETH",
            color = "#2563EB",
            employeeId = "50191",
            classId = "CH2026270501792",
            classIds = listOf("CH2026270501792", "CH2026270501793"),
            batch = "B26",
        )
        val metrics = PlannerMetrics(
            halfDays = 8,
            gaps = 120,
            gapsPerDay = mapOf("mon" to 60, "wed" to 60),
            gapDetails = listOf(
                PlannerGapDetail(day = DayId.MON, startMin = 590, endMin = 650, durationMins = 60),
            ),
            buildingDashes = 1,
            dashDetails = listOf(
                PlannerDashDetail(
                    fromClass = "A (Theory)", toClass = "B (Lab)",
                    fromTime = "08:50", toTime = "08:50", day = "mon",
                    fromBlock = "AB1", toBlock = "SJT",
                ),
            ),
            socialScore = 40,
            bestFriendMatches = listOf("Friend"),
        )
        val planner = PlannerTimetable(
            id = "t1",
            name = "Option 1",
            courses = listOf(course, course.copy(id = "c2")),
            metrics = metrics,
            rankingScore = 87.5,
        )

        val legacy = FfcsRuntimeAdapter.toLegacyTimetable(planner)

        assertEquals("t1", legacy.id)
        assertEquals("Option 1", legacy.name)
        assertEquals(2, legacy.courses.size)
        assertEquals("c1", legacy.courses.first().id)
        assertEquals("BACSE211", legacy.courses.first().code)
        assertEquals(listOf("B1", "TB1"), legacy.courses.first().slots)
        assertEquals("50191", course.employeeId, "canonical-only id is still on the untouched input")

        assertEquals(8, legacy.metrics.halfDays)
        assertEquals(2, legacy.metrics.gaps, "120 canonical minutes = 2 legacy whole hours")
        assertEquals(mapOf("mon" to 1, "wed" to 1), legacy.metrics.gapsPerDay)
        assertEquals("mon", legacy.metrics.gapDetails.single().day)
        assertEquals(60, legacy.metrics.gapDetails.single().durationMins)
        assertEquals(1, legacy.metrics.buildingDashes)
        assertEquals("AB1", legacy.metrics.dashDetails.single().fromBlock)
        assertEquals(40, legacy.metrics.socialScore)
        assertFalse(legacy.metrics.isLongWeekend)
        assertEquals(87.5, planner.rankingScore!!, "the adapter never rewrites the canonical score")
    }

    @Test
    fun `absent canonical metrics and ranking score are never turned into zeros`() {
        val planner = PlannerTimetable(id = "t2", name = "Option 2", courses = emptyList())
        assertNull(planner.rankingScore)

        val legacy = FfcsRuntimeAdapter.toLegacyTimetable(planner)
        assertTrue(legacy.courses.isEmpty())
        assertEquals(0, legacy.metrics.halfDays, "the legacy model's own default, not a derived score")
        assertEquals(0, legacy.metrics.gaps)
        assertNull(planner.rankingScore)

        val zeroGaps = FfcsRuntimeAdapter.toLegacyMetrics(PlannerMetrics(halfDays = 4, gaps = 0))
        assertEquals(0, zeroGaps.gaps)
        assertTrue(zeroGaps.gapsPerDay.isEmpty())
    }
}
