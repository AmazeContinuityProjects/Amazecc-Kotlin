package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.AddedCourse as PlannerAddedCourse
import com.amazecc.app.shared.ffcs.planner.CourseLock as PlannerCourseLock
import com.amazecc.app.shared.ffcs.planner.Friend as PlannerFriend
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as PlannerParsedCourse
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableMetrics as PlannerMetrics
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-177 — parity checks for the metrics/sorting/filter contract:
 * minutes not hours, free-half semantics, stable settled order, honest absence,
 * and the exact filter order from reference/04:85-195.
 */
class FfcsMetricsParityTest {

    private fun legacy(
        id: String,
        vararg slots: String,
        code: String = "CSE1001",
        venue: String = "AB1-101",
    ) = AddedCourse(
        id = id,
        code = code,
        title = code,
        slots = slots.toList(),
        faculty = "Dr. A",
        venue = venue,
        credits = "3",
        type = "Theory",
        color = "#2563EB",
    )

    private fun plannerCourse(
        id: String,
        code: String,
        slots: List<String>,
        faculty: String = "Dr. A",
        venue: String = "AB1-101",
    ) = PlannerAddedCourse(
        id = id,
        code = code,
        title = code,
        slots = slots,
        faculty = faculty,
        venue = venue,
        credits = "3",
        type = "Theory",
        color = "#2563EB",
    )

    private fun plannerTimetable(
        name: String,
        courses: List<PlannerAddedCourse> = emptyList(),
        metrics: PlannerMetrics? = null,
        rankingScore: Double? = null,
    ) = PlannerTimetable(
        id = name,
        name = name,
        courses = courses,
        metrics = metrics,
        rankingScore = rankingScore,
    )

    private fun offering(slot: String, faculty: String, room: String = "R1") = PlannerParsedCourse(
        CODE = "CSE1001",
        TITLE = "Programming",
        TYPE = "TH",
        CREDITS = "3",
        ROOM = room,
        SLOT = slot,
        FACULTY = faculty,
    )

    // ── metrics: minutes, not hours ─────────────────────────────────────────

    @Test
    fun canonical_gaps_are_minutes_and_legacy_field_is_adapted_to_hours() {
        // MON A1 08:00-08:50, D1 09:50-10:40 -> one 60-minute gap.
        val week = listOf(legacy("a", "A1", "D1"))
        val canonical = canonicalMetricsFor(week)
        assertEquals(60, canonical.gapsMinutes)
        assertEquals(60, canonical.gapsPerDay["mon"])
        assertEquals(1, FfcsMetrics.calculateTimetableMetrics(week).gaps, "legacy field stays whole hours")

        // A 35-minute gap (MON S11 ends 13:25, A2 starts 14:00) must not
        // disappear the way integer hours did. Period clocks are the web's
        // index-paired theory clocks, even for lab halves.
        val shortWeek = listOf(legacy("b", "S11", "A2"))
        assertEquals(35, canonicalMetricsFor(shortWeek).gapsMinutes)
        assertEquals(0, FfcsMetrics.calculateTimetableMetrics(shortWeek).gaps)
        // Adjacent theory periods are 5 minutes apart: not a reported gap.
        assertEquals(0, canonicalMetricsFor(listOf(legacy("b2", "A1", "F1"))).gapsMinutes)

        // The old 40-minute lunch subtraction is gone: web counts the full 90.
        val acrossLunch = listOf(legacy("c", "TG1", "A2"))
        assertEquals(90, canonicalMetricsFor(acrossLunch).gapsMinutes)
    }

    @Test
    fun empty_day_and_half_day_semantics_follow_the_web_walk() {
        val empty = canonicalMetricsFor(emptyList())
        assertEquals(10, empty.halfDays)
        assertEquals(10, empty.freeHalfDays.size)
        assertTrue(empty.isLongWeekend)
        assertEquals(0, empty.gapsMinutes)
        assertTrue(empty.gapDetails.isEmpty())

        // L2 is MON morning only: MON evening + the other four days are free.
        val oneMorning = canonicalMetricsFor(listOf(legacy("m", "L2")))
        assertEquals(9, oneMorning.halfDays)
        assertTrue(oneMorning.isLongWeekend, "Friday still has no classes")

        // A token occurring on two mornings (MON + WED) removes two halves.
        assertEquals(8, canonicalMetricsFor(listOf(legacy("m", "A1"))).halfDays)

        // One morning class on each weekday -> five free halves, no long weekend.
        val fiveDays = canonicalMetricsFor(
            listOf("L1", "L7", "L13", "L19", "L25").mapIndexed { i, slot -> legacy("d$i", slot) }
        )
        assertEquals(5, fiveDays.halfDays)
        assertFalse(fiveDays.isLongWeekend)

        // Legacy adapter keeps the same half-day number.
        assertEquals(9, FfcsMetrics.calculateTimetableMetrics(listOf(legacy("m", "L2"))).halfDays)
    }

    @Test
    fun building_dashes_use_the_web_block_and_window_rules() {
        // L1 (AB1) ends 08:50, L2 (SJT) starts 08:50: a dash, not a gap.
        val dash = canonicalMetricsFor(
            listOf(legacy("a", "L1", venue = "AB1-101"), legacy("b", "L2", venue = "SJT-101"))
        )
        assertEquals(1, dash.buildingDashes)
        assertEquals(1, dash.dashDetails.size)
        assertEquals("AB1", dash.dashDetails.single().fromBlock)
        assertEquals("SJT", dash.dashDetails.single().toBlock)
        assertEquals(0, dash.gapsMinutes)

        // Same building, and NIL venues, never count.
        val sameBlock = canonicalMetricsFor(
            listOf(legacy("a", "L1", venue = "AB1-101"), legacy("b", "L2", venue = "AB1-102L"))
        )
        assertEquals(0, sameBlock.buildingDashes)
        val nilVenue = canonicalMetricsFor(
            listOf(legacy("a", "L1", venue = "NIL"), legacy("b", "L2", venue = "SJT-101"))
        )
        assertEquals(0, nilVenue.buildingDashes)
    }

    // ── social score: null when absent, real when friends exist ─────────────

    @Test
    fun social_score_is_null_without_friends_and_real_with_them() {
        val mine = listOf(legacy("a", "L1", code = "CSE1001"))
        val canonical = canonicalMetricsFor(mine)
        assertNull(canonical.socialScore)
        assertTrue(canonical.bestFriendMatches.isEmpty())
        assertEquals(0, FfcsMetrics.calculateTimetableMetrics(mine).socialScore, "legacy field cannot be null")

        val friend = PlannerFriend(
            id = "f1",
            name = "Friend",
            timetables = listOf(
                plannerTimetable("their-tt", listOf(plannerCourse("fc", "CSE1001", listOf("L1"))))
            ),
        )
        val withFriend = canonicalMetricsFor(mine, listOf(friend))
        assertNotNull(withFriend.socialScore)
        assertEquals(100, withFriend.socialScore!!, "identical week: 1 shared class, 59 free slots, 9 free halves")
        assertEquals(listOf("Friend"), withFriend.bestFriendMatches)
    }

    // ── sorting: golden score, metric-less last, stable ties ────────────────

    @Test
    fun balanced_score_golden_and_metric_less_timetables_sort_last() {
        val strong = PlannerMetrics(halfDays = 6, gaps = 0, socialScore = 40)
        val weaker = PlannerMetrics(halfDays = 6, gaps = 120, socialScore = 40)
        // 6*10 + ((1200-120)/60)*5 + 40 = 60 + 90 + 40 = 190.
        assertEquals(190.0, balancedScore(weaker), 1e-9)
        assertEquals(200.0, balancedScore(strong), 1e-9)

        val good = plannerTimetable("good", metrics = strong)
        val bad = plannerTimetable("bad", metrics = weaker)
        val unscored = plannerTimetable("unscored")
        assertEquals(
            listOf("good", "bad", "unscored"),
            sortTimetables(listOf(bad, unscored, good), SortBy.BALANCED).map { it.name },
        )
        assertEquals(
            listOf("good", "bad", "unscored"),
            sortTimetables(listOf(bad, unscored, good)).map { it.name },
            "balanced is the default sort",
        )
    }

    @Test
    fun sorts_are_stable_and_a_missing_ranking_score_is_minus_one() {
        val shared = PlannerMetrics(halfDays = 4, gaps = 60, socialScore = 10)
        val x = plannerTimetable("x", metrics = shared)
        val y = plannerTimetable("y", metrics = shared)
        val ranked = plannerTimetable("ranked", metrics = shared, rankingScore = 0.2)
        val missing = plannerTimetable("missing", metrics = shared)

        assertEquals(listOf("x", "y"), sortTimetables(listOf(x, y)).map { it.name })
        assertEquals(
            listOf("ranked", "x", "y", "missing"),
            sortByRankingScore(listOf(x, ranked, y, missing)).map { it.name },
        )
        assertNull(missing.rankingScore, "sorting must not write a zero into an absent score")
    }

    @Test
    fun settle_composes_base_sort_dedupe_and_faculty_preference() {
        val metrics = PlannerMetrics(halfDays = 8, gaps = 0, socialScore = 0)
        val first = plannerTimetable("first", listOf(plannerCourse("1", "C1", listOf("L1"), "Dr. Rao")), metrics)
        val duplicate = plannerTimetable("duplicate", listOf(plannerCourse("2", "C1", listOf("L1"), "Dr. Rao")), metrics)
        val preferred = plannerTimetable("preferred", listOf(plannerCourse("3", "C1", listOf("L1"), "Dr. Rao")), metrics)

        val deduped = settleTimetables(
            listOf(first, duplicate), uniqueFaculties = true,
            facultyPriority = mapOf("C1" to listOf("Dr. Rao")),
        )
        assertEquals(listOf("first"), deduped.map { it.name })

        val byPreference = sortByFacultyPreference(listOf(first, preferred), mapOf("C1" to listOf("Dr. Rao")))
        assertEquals(listOf("first", "preferred"), byPreference.map { it.name }, "equal scores keep input order")
    }

    // ── filters: fixed order and honest gates ───────────────────────────────

    @Test
    fun offering_filters_compose_in_fixed_order_and_block_both_spellings() {
        val lock = PlannerCourseLock(
            code = "CSE1001",
            title = "Programming",
            allowedSlots = listOf("A1"),
            allowedFaculty = listOf("Dr. A"),
        )
        val friend = PlannerFriend(
            id = "f1",
            name = "Friend",
            timetables = listOf(
                plannerTimetable(
                    "their-tt",
                    listOf(plannerCourse("fc", "CSE1001", listOf("A1"), "Dr. A")),
                )
            ),
        )
        val params = defaultGenerateParams().copy(syncFriendClasses = true, friends = listOf(friend))
        val candidates = listOf(
            offering("A1", "Dr. A"), // passes every gate
            offering("A1", "Dr. B"), // rejected by allowedFaculty
            offering("F1", "Dr. A"), // rejected by allowedSlots
            offering("B1", "Dr. A"), // rejected by allowedSlots
        )
        assertEquals(listOf("Dr. A"), filterOfferings(candidates, lock, params).map { it.FACULTY })
        assertEquals(listOf("A1", "A1", "F1", "B1"), candidates.map { it.SLOT }, "filters are pure")

        // Blocking the unnumbered spelling also kills the report's A1.
        val blocked = params.copy(blockedSlots = listOf("a"))
        assertTrue(filterOfferings(listOf(offering("A1", "Dr. A")), lock, blocked).isEmpty())

        // A friend without this course has "no opinion", not "reject everything".
        val otherCourseFriend = friend.copy(
            timetables = listOf(
                plannerTimetable("other", listOf(plannerCourse("x", "MATH1001", listOf("A1"), "Dr. A")))
            ),
        )
        assertEquals(
            listOf("Dr. A"),
            filterOfferings(listOf(offering("A1", "Dr. A")), lock, params.copy(friends = listOf(otherCourseFriend)))
                .map { it.FACULTY },
        )
    }

    @Test
    fun faculty_priority_sort_is_the_last_offering_filter_and_is_pure() {
        val options = listOf("D", "B", "A", "C").map { offering("A1", "Prof $it") }
        val lock = PlannerCourseLock("CSE1001", "Programming", emptyList(), emptyList())
        val params = defaultGenerateParams().copy(facultyPriority = mapOf("CSE1001" to listOf("Prof A", "Prof B")))

        val sorted = filterOfferings(options, lock, params)
        // Every offering shares one CODE, so the D-174 golden letters live in
        // the faculty cell ("Prof A"). Strip the prefix to compare that vector.
        fun letter(offering: PlannerParsedCourse): String = offering.FACULTY.removePrefix("Prof ")
        assertEquals(listOf("A", "B", "D", "C"), sorted.map(::letter))
        assertEquals(options.map(::letter), listOf("D", "B", "A", "C"), "inputs are not mutated")
    }

    @Test
    fun result_filters_apply_lab_half_day_and_faculty_gates_in_order() {
        val metrics = PlannerMetrics(halfDays = 8, gaps = 0, socialScore = 0)
        val mondayLab = plannerCourse("1", "C1", listOf("L1"))
        val tuesdayLab = plannerCourse("2", "C2", listOf("L7"))
        val clean = plannerTimetable("clean", listOf(mondayLab, tuesdayLab), metrics)
        val params = defaultGenerateParams().copy(separateLabDays = true, avoidConsecutiveLabs = true)
        assertTrue(passesResultFilters(clean, params))

        val sameDay = plannerTimetable("same-day", listOf(mondayLab, plannerCourse("3", "C3", listOf("L2"))), metrics)
        assertFalse(passesResultFilters(sameDay, params), "two labs on MON are rejected")

        val missingMetrics = plannerTimetable("no-metrics", listOf(mondayLab))
        assertFalse(
            passesResultFilters(missingMetrics, defaultGenerateParams().copy(minHalfDays = 3)),
            "absent metrics give zero half-days, never a fabricated pass",
        )
        assertEquals(listOf(clean), filterResults(listOf(clean, sameDay), params))
    }
}
