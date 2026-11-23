package com.amazecc.app.shared.ffcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-185 — the clash deriver may only report overlaps that really exist in the selected courses.
 * Every slot token below is a real CHENNAI token; expected days/clocks come from `SlotMap`, not
 * from the deriver itself, so an invented meeting cannot slip through a self-fulfilling assertion.
 */
class FfcsClashDeriverTest {

    private fun course(code: String, slots: List<String>) = AddedCourse(
        id = "c-$code",
        code = code,
        title = "Course $code",
        slots = slots,
        faculty = "Dr. Ada",
        venue = "AB3-101",
        credits = "4",
        type = "TH",
        color = "#2563EB",
    )

    /** A1 really meets on Mon 08:00-08:50 and Wed 08:55-09:45 in the CHENNAI slot map. */
    private val a1Meetings = listOf(
        FfcsClashMeeting("mon", "8:00 AM"),
        FfcsClashMeeting("wed", "8:55 AM"),
    )

    @Test
    fun two_courses_sharing_a_real_slot_emit_the_real_day_and_time() {
        val a = course("CSE1001", listOf("A1"))
        val b = course("MAT2005", listOf("A1"))

        val clashes = FfcsClashDeriver.derive(listOf(a, b))

        assertEquals(2, clashes.size)
        assertEquals(listOf(a, b), clashes.map { it.course }, "rows must keep input order")
        assertEquals("MAT2005", clashes[0].clashesWith.single().code)
        assertEquals(a1Meetings, clashes[0].clashesWith.single().meetings)
        assertEquals("CSE1001", clashes[1].clashesWith.single().code)
        assertEquals(a1Meetings, clashes[1].clashesWith.single().meetings)
    }

    @Test
    fun back_to_back_boundary_slots_are_not_a_clash() {
        // L1 = Mon 08:00-08:50, L2 = Mon 08:50-09:40: end == start, so no open interval overlaps.
        val clashes = FfcsClashDeriver.derive(
            listOf(course("CSE1001", listOf("L1")), course("MAT2005", listOf("L2"))),
        )
        assertEquals(emptyList(), clashes)
    }

    @Test
    fun same_clock_on_different_days_is_not_a_clash() {
        // L1 = Mon 08:00-08:50, L7 = Tue 08:00-08:50: same clock, different day.
        val clashes = FfcsClashDeriver.derive(
            listOf(course("CSE1001", listOf("L1")), course("MAT2005", listOf("L7"))),
        )
        assertEquals(emptyList(), clashes)
    }

    @Test
    fun three_courses_with_one_overlapping_pair_emit_only_the_two_real_rows() {
        val a = course("CSE1001", listOf("L1")) // Mon 08:00-08:50
        val b = course("MAT2005", listOf("L1")) // Mon 08:00-08:50 — real clash with a
        val c = course("PHY1002", listOf("L7")) // Tue 08:00-08:50 — no clash with either

        val clashes = FfcsClashDeriver.derive(listOf(a, b, c))

        assertEquals(listOf(a, b), clashes.map { it.course }, "the non-clashing course gets no row")
        assertEquals("MAT2005", clashes[0].clashesWith.single().code)
        assertEquals("CSE1001", clashes[1].clashesWith.single().code)
        assertEquals(
            listOf(FfcsClashMeeting("mon", "8:00 AM")),
            clashes[0].clashesWith.single().meetings,
        )
        assertTrue(clashes.none { "PHY1002" in it.clashesWith.map { entry -> entry.code } })
        assertFalse(clashes.any { it.course.code == "PHY1002" })
    }

    @Test
    fun empty_selection_returns_empty_list() {
        assertEquals(emptyList(), FfcsClashDeriver.derive(emptyList()))
        // A single course cannot clash with itself.
        assertEquals(emptyList(), FfcsClashDeriver.derive(listOf(course("CSE1001", listOf("A1")))))
    }

    @Test
    fun unparseable_token_contributes_nothing() {
        val a = course("CSE1001", listOf("BOGUS99", "   ", ""))
        val b = course("MAT2005", listOf("L7"))
        assertEquals(emptyList(), FfcsClashDeriver.derive(listOf(a, b)))

        val real = course("PHY1002", listOf("L1"))
        val clashes = FfcsClashDeriver.derive(
            listOf(course("CSE1001", listOf("BOGUS99", "L1")), real),
        )
        assertEquals(2, clashes.size, "both clashing courses get a row")
        val expected = listOf(FfcsClashMeeting("mon", "8:00 AM"))
        assertEquals(
            expected,
            clashes[0].clashesWith.single().meetings,
            "the bogus token must not add a fabricated meeting",
        )
        assertEquals(expected, clashes[1].clashesWith.single().meetings)
    }

    @Test
    fun identical_meetings_from_alias_slots_are_deduplicated() {
        // "A" is a real alias of "A1", so both tokens expand to the same two (day, clock) pairs.
        val clashes = FfcsClashDeriver.derive(
            listOf(course("CSE1001", listOf("A1", "A")), course("MAT2005", listOf("A1"))),
        )
        assertEquals(a1Meetings, clashes.first().clashesWith.single().meetings)
        assertEquals(a1Meetings, clashes.last().clashesWith.single().meetings)
    }

    @Test
    fun derive_is_a_pure_function_of_its_input() {
        val input = listOf(
            course("CSE1001", listOf("A1")),
            course("MAT2005", listOf("L1")),
            course("PHY1002", listOf("L7")),
        )
        assertEquals(FfcsClashDeriver.derive(input), FfcsClashDeriver.derive(input))
    }
}
