package com.amazecc.app.shared.ffcs.planner

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-171 — pins the exact canonical FFCS-planner type contract:
 *  - UPPERCASE ParsedCourse field names and '+'-joined SLOT on the wire,
 *  - absent rankingScore stays null (never 0),
 *  - offeringId with/without ERP + legacy matching,
 *  - enum order and wire values,
 *  - no fabricated GenerateParams defaults.
 */
class FfcsPlannerModelsTest {

    private val plainJson = Json
    private val omitNulls = Json { explicitNulls = false }
    // Json's default (encodeDefaults = false) drops every field equal to its
    // default, so only halfDays/gaps would appear. To assert the *shape* of the
    // nine-field web contract, all default-valued fields must be emitted.
    private val fullShapeJson = Json { encodeDefaults = true }

    private val fullCourse = ParsedCourse(
        CODE = "BACSE211",
        TITLE = "Robotics: Kinematics, Dynamics and Motion Control",
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

    // ── ParsedCourse wire shape ────────────────────────────────────────────

    @Test
    fun `ParsedCourse serializes with exact UPPERCASE field names`() {
        val json = plainJson.encodeToString(fullCourse)
        val expectedKeys = listOf(
            "CODE", "TITLE", "TYPE", "CREDITS", "ROOM", "SLOT", "FACULTY",
            "ORIGINAL_CODE", "LINK_ID", "BATCH", "CLASS_ID", "CLASS_IDS",
            "ERP_ID", "EMPLOYEE_SCHOOL",
        )
        expectedKeys.forEach { key ->
            assertTrue("\"$key\":" in json, "Missing UPPERCASE key $key in: $json")
        }
        assertFalse("\"code\":" in json, "Lowercase key leaked into: $json")
        assertFalse("\"credits\":" in json, "Lowercase key leaked into: $json")
        assertFalse("\"slots\":" in json, "SLOT must not be serialised as slots[]: $json")
        assertEquals(fullCourse, plainJson.decodeFromString<ParsedCourse>(json))
    }

    @Test
    fun `CREDITS is a String and SLOT is one plus joined string`() {
        val json = plainJson.encodeToString(fullCourse)
        assertTrue("\"CREDITS\":\"3\"" in json, json)
        assertTrue("\"SLOT\":\"B1+TB1\"" in json, json)
        assertEquals("3", fullCourse.CREDITS)
        assertEquals("B1+TB1", fullCourse.SLOT)
        assertEquals(listOf("B1", "TB1"), splitSlots(fullCourse.SLOT))
        assertEquals(listOf("B1", "TB1"), splitSlots(" b1 + tb1 "))
        assertTrue(splitSlots("NIL").contains("NIL"))
    }

    // ── rankingScore absence ───────────────────────────────────────────────

    @Test
    fun `absent rankingScore stays null never zero`() {
        val decoded = plainJson.decodeFromString<TimetableState>(
            """{"id":"t1","name":"Option 1","courses":[]}""",
        )
        assertNull(decoded.rankingScore)
        assertNull(decoded.rankingBreakdown)
        assertNull(decoded.rankingComponents)
        assertNull(decoded.metrics)
        assertNull(decoded.variants)
        assertNull(decoded.facultySummary)

        // With nulls omitted, the key must disappear entirely — not become 0.
        val encoded = omitNulls.encodeToString(decoded)
        assertFalse("rankingScore" in encoded, encoded)
        assertFalse("rankingScore\":0" in encoded, encoded)

        // Constructor default is also null, not 0.0.
        val constructed = TimetableState(id = "t2", name = "Option 2", courses = emptyList())
        assertNull(constructed.rankingScore)

        // Explicit JSON null still reads back as null.
        val explicitNull = plainJson.decodeFromString<TimetableState>(
            """{"id":"t3","name":"Option 3","courses":[],"rankingScore":null}""",
        )
        assertNull(explicitNull.rankingScore)
    }

    @Test
    fun `present rankingScore and nullable components decode faithfully`() {
        val decoded = plainJson.decodeFromString<TimetableState>(
            """{"id":"t4","name":"Option 4","courses":[],"rankingScore":87.5,""" +
                """"rankingComponents":{"profile":90.0,"faculty":null,"sort":80.0,"time":null}}""",
        )
        assertEquals(87.5, decoded.rankingScore)
        assertEquals(90.0, decoded.rankingComponents?.profile)
        assertNull(decoded.rankingComponents?.faculty)
        assertEquals(80.0, decoded.rankingComponents?.sort)
        assertNull(decoded.rankingComponents?.time)
    }

    // ── Offering identity ──────────────────────────────────────────────────

    @Test
    fun `offeringId appends ERP_ID only when present`() {
        assertEquals(
            "MUTHUKUMAR AN K|B1+TB1|AB3-308|50191",
            fullCourse.offeringId(),
        )
        assertEquals("MUTHUKUMAR AN K|B1+TB1|AB3-308", fullCourse.legacyOfferingId())

        val noErp = fullCourse.copy(ERP_ID = null)
        assertEquals("MUTHUKUMAR AN K|B1+TB1|AB3-308", noErp.offeringId())
        assertEquals(noErp.offeringId(), noErp.legacyOfferingId())

        // An empty ERP cell is treated as absent, never as a trailing '|'.
        val emptyErp = fullCourse.copy(ERP_ID = "")
        assertEquals("MUTHUKUMAR AN K|B1+TB1|AB3-308", emptyErp.offeringId())
    }

    @Test
    fun `offeringMatches accepts modern and legacy ids`() {
        assertTrue(
            offeringMatches(fullCourse, listOf("MUTHUKUMAR AN K|B1+TB1|AB3-308|50191")),
        )
        assertTrue(
            offeringMatches(fullCourse, listOf("MUTHUKUMAR AN K|B1+TB1|AB3-308")),
        )
        assertFalse(offeringMatches(fullCourse, listOf("SOMEBODY ELSE|A1|R101")))

        val noErp = fullCourse.copy(ERP_ID = null)
        assertTrue(offeringMatches(noErp, listOf(noErp.offeringId())))
        assertTrue(offeringMatches(noErp, listOf(noErp.legacyOfferingId())))
    }

    // ── Enum order / wire values ───────────────────────────────────────────

    @Test
    fun `enum orders match the canonical engine contract`() {
        assertEquals(
            listOf(DayId.MON, DayId.TUE, DayId.WED, DayId.THU, DayId.FRI),
            DayId.entries.toList(),
        )
        assertEquals(
            listOf("mon", "tue", "wed", "thu", "fri"),
            DayId.DAYS_OF_WEEK.map { it.value },
        )
        assertEquals(
            listOf("gaps", "halfdays", "social", "faculty", "earlyFinish", "lateStart", "balancedLoad", "time"),
            RankingFactor.CANONICAL_ORDER.map { it.value },
        )
        assertEquals(
            listOf("profile", "faculty", "sort", "time"),
            RankingPriority.DEFAULT_ORDER.map { it.value },
        )
        assertEquals(4, RankingPriority.entries.size)
        assertEquals(
            listOf("Best Faculty", "Balanced", "Half Days", "Minimize Gaps", "Early Finish", "Late Start"),
            RankingMode.entries.map { it.value },
        )
        assertEquals(6, RankingMode.entries.size)
        assertEquals(
            listOf("none", "morning", "evening", "compact", "spread"),
            Preference.entries.map { it.value },
        )
        assertEquals(
            listOf("social", "halfdays", "compactness", "balanced", "faculty"),
            SortBy.entries.map { it.value },
        )
    }

    @Test
    fun `enums serialize to their exact web wire values`() {
        assertEquals("\"mon\"", plainJson.encodeToString(DayId.MON))
        assertEquals("\"earlyFinish\"", plainJson.encodeToString(RankingFactor.EARLY_FINISH))
        assertEquals("\"profile\"", plainJson.encodeToString(RankingPriority.PROFILE))
        assertEquals("\"Best Faculty\"", plainJson.encodeToString(RankingMode.BEST_FACULTY))
        assertEquals(RankingFactor.TIME, plainJson.decodeFromString<RankingFactor>("\"time\""))
        assertEquals(RankingMode.BALANCED, RankingMode.DEFAULT)
    }

    @Test
    fun `DayId alias helpers accept wire values and full day names`() {
        assertEquals(DayId.MON, DayId.fromString("mon"))
        assertEquals(DayId.MON, DayId.fromString("Monday"))
        assertEquals(DayId.MON, DayId.fromString(" MON "))
        assertEquals(DayId.FRI, DayId.fromString("friday"))
        assertTrue(DayId.isDayId("tue"))
        assertTrue(DayId.isDayId("wednesday"))
        assertFalse(DayId.isDayId("sat"))
        assertNull(DayId.fromString(null))
    }

    // ── No fabricated defaults ─────────────────────────────────────────────

    @Test
    fun `GenerateParams optional fields are unset not fabricated`() {
        val params = GenerateParams(
            schema = CampusSchema(),
            masterCourses = emptyList(),
            courseLocks = emptyList(),
            blockedSlots = emptyList(),
            friends = emptyList(),
            preference = Preference.NONE,
            syncFriendClasses = false,
            maximizeFreeTimeFriends = emptyList(),
            uniqueFaculties = false,
            noLimit = false,
            minHalfDays = 0,
            sortBy = SortBy.BALANCED,
        )
        assertNull(params.strictTheoryPreference)
        assertNull(params.minStartTime)
        assertNull(params.maxEndTime)
        assertNull(params.maxEndTimeByDay)
        assertNull(params.resultCap)
        assertNull(params.removeHardResultLimit)
        assertNull(params.rankingMode)
        assertNull(params.facultyRatings)
        assertNull(params.facultyRatingsAvailable)
        assertNull(params.facultyMetricWeights)
        assertNull(params.facultyScoreFilters)
        assertNull(params.rankingPriorityOrder)
        assertNull(params.rankingFactors)
        assertNull(params.separateLabDays)
        assertNull(params.avoidConsecutiveLabs)
        assertNull(params.lunchBreakSeparatesLabs)
        assertNull(params.facultyPriority)

        // The only empty list that is a real default is `disabled`.
        val factors = RankingFactorsConfig(order = RankingFactor.CANONICAL_ORDER)
        assertTrue(factors.disabled.isEmpty())
        assertEquals(RankingFactor.CANONICAL_ORDER, factors.order)
    }

    @Test
    fun `CourseLock empty lists mean unrestricted`() {
        val lock = CourseLock(
            code = "BACSE211",
            title = "Robotics",
            allowedSlots = emptyList(),
            allowedFaculty = emptyList(),
        )
        assertTrue(lock.allowedSlots.isEmpty())
        assertTrue(lock.allowedFaculty.isEmpty())
        assertNull(lock.offerings)
    }

    @Test
    fun `AddedCourse optional identity fields stay null when absent`() {
        val added = AddedCourse(
            id = "1",
            code = "BACSE211",
            title = "Robotics",
            slots = listOf("B1", "TB1"),
            faculty = "MUTHUKUMAR AN K",
            venue = "AB3-308",
            credits = "3",
            type = "ETH",
            color = "#2563EB",
        )
        assertNull(added.employeeId)
        assertNull(added.classId)
        assertNull(added.classIds)
        assertNull(added.batch)
        val json = omitNulls.encodeToString(added)
        assertFalse("employeeId" in json, json)
        assertFalse("classId" in json, json)
        assertTrue("\"slots\":[\"B1\",\"TB1\"]" in json, json)
    }

    @Test
    fun `TimetableMetrics keeps all nine web fields`() {
        val metrics = TimetableMetrics(halfDays = 6, gaps = 90)
        assertEquals(
            listOf(
                "halfDays", "gaps", "gapsPerDay", "gapDetails", "buildingDashes",
                "dashDetails", "isLongWeekend", "socialScore", "bestFriendMatches",
            ),
            TimetableMetrics.KEYS,
        )
        val json = fullShapeJson.encodeToString(metrics)
        TimetableMetrics.KEYS.forEach { key ->
            assertTrue("\"$key\":" in json, "Missing metric field $key in: $json")
        }
        assertEquals(emptyList<String>(), metrics.bestFriendMatches)
        // The wire shape round-trips without inventing values.
        assertEquals(metrics, plainJson.decodeFromString<TimetableMetrics>(json))
    }
}
