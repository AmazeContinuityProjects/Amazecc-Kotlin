package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-172 — canonical slot vocabulary + clash algebra acceptance tests.
 * Grounded in ffcs-planner reference/03 §3.1-3.4 and reference/05 §3.1.
 */
class FfcsSlotSchemaTest {

    private fun period(day: String, start: Int, end: Int, type: String = "theory") =
        SlotPeriod(day, "H:MM AM", "H:MM AM", start, end, type, 0)

    // ---- strict parsing ----------------------------------------------------

    @Test
    fun strictParse_rejects_clock_without_meridian() {
        assertNull(parseTimeToMinutes("8:00"))
        assertNull(parseTimeToMinutes("08:00"))
        assertNull(parseTimeToMinutes("9:45 "))
    }

    @Test
    fun strictParse_accepts_meridian_and_rejects_out_of_range() {
        assertEquals(480, parseTimeToMinutes("8:00 AM"))
        assertEquals(535, parseTimeToMinutes("8:55am"))
        assertEquals(800, parseTimeToMinutes("1:20 PM"))
        assertEquals(755, parseTimeToMinutes("12:35 PM"))
        assertEquals(0, parseTimeToMinutes("12:00 AM"))
        assertEquals(720, parseTimeToMinutes("12:00 PM"))
        assertNull(parseTimeToMinutes("25:00 PM"))
        assertNull(parseTimeToMinutes("9:60 AM"))
        assertNull(parseTimeToMinutes("not a time"))
    }

    @Test
    fun timemath_strict_entry_point_does_not_change_web_parity() {
        assertNull(TimeMath.parseSchemaTimeToMinutes("8:00"))
        assertEquals(480, TimeMath.parseSchemaTimeToMinutes("8:00 AM"))
        // Legacy web-parity parser is untouched: bare 24h still parses.
        assertEquals(480, TimeMath.parseTimeToMinutes("08:00"))
    }

    // ---- generated schema / provenance -------------------------------------

    @Test
    fun generated_schema_contains_only_real_source_tokens() {
        val theory = FfcsSlotSchema.theoryTokens()
        val lab = FfcsSlotSchema.labTokens()
        assertTrue(theory.containsAll(setOf("A1", "A2", "TAA1", "S11", "S15", "C1", "E1")))
        assertTrue(lab.contains("L1"))
        assertTrue(lab.contains("L60"))
        assertTrue(lab.contains("L94")) // weekend tokens are real SlotMap data
        assertTrue(lab.all { it in SlotMap.labTokens() })
        assertTrue(theory.all { it in SlotMap.theoryTokens() })
        assertFalse(theory.any { it.startsWith("L") && it.drop(1).all { c -> c.isDigit() } })
    }

    @Test
    fun a1_maps_to_both_of_its_periods() {
        val periods = FfcsSlotSchema.periodsForSlot("A1")
        assertEquals(2, periods.size)
        assertTrue(periods.any {
            it.day == "mon" && it.startMin == 480 && it.endMin == 530 && it.type == "theory"
        })
        assertTrue(periods.any {
            it.day == "wed" && it.startMin == 535 && it.endMin == 585 && it.type == "theory"
        })
    }

    @Test
    fun aliases_expand_to_the_same_periods() {
        assertEquals(FfcsSlotSchema.periodsForSlot("A1"), FfcsSlotSchema.periodsForSlot("A"))
        assertEquals(FfcsSlotSchema.periodsForSlot("C1"), FfcsSlotSchema.periodsForSlot("C"))
        assertEquals(FfcsSlotSchema.periodsForSlot("E1"), FfcsSlotSchema.periodsForSlot("E"))
        assertEquals(FfcsSlotSchema.periodsForSlot("TAA1"), FfcsSlotSchema.periodsForSlot("TAA"))
        assertEquals(FfcsSlotSchema.periodsForSlot("S11"), FfcsSlotSchema.periodsForSlot("TEE"))
        assertEquals(FfcsSlotSchema.periodsForSlot("S15"), FfcsSlotSchema.periodsForSlot("TFF"))
        // Law aliases resolve to the real S11/S15 clock row.
        assertEquals(755, FfcsSlotSchema.periodsForSlot("TEE").single().startMin)
        assertEquals(805, FfcsSlotSchema.periodsForSlot("TFF").single().endMin)
    }

    @Test
    fun nil_and_non_slots_have_no_periods() {
        assertTrue(FfcsSlotSchema.periodsForSlot("NIL").isEmpty())
        assertTrue(FfcsSlotSchema.periodsForSlot("S").isEmpty())
        assertTrue(FfcsSlotSchema.periodsForSlot("L").isEmpty())
    }

    @Test
    fun lab_tokens_are_scanned_from_the_lab_schema() {
        val periods = FfcsSlotSchema.periodsForSlot("L1")
        assertEquals(1, periods.size)
        assertEquals("lab", periods.single().type)
        assertEquals("mon", periods.single().day)
        assertEquals(480, periods.single().startMin)
    }

    @Test
    fun day_cells_split_on_slash_and_plus() {
        val schema = CampusSchema(
            theory = listOf(
                SchemaPeriod(
                    start = "8:00 AM",
                    end = "8:50 AM",
                    days = mapOf("mon" to "ZZ1/A1", "tue" to "A1+F1"),
                )
            )
        )
        val periods = FfcsSlotSchema.periodsForSlot("A1", schema)
        assertEquals(2, periods.size)
        assertEquals(setOf("mon", "tue"), periods.map { it.day }.toSet())
    }

    // ---- clash algebra -----------------------------------------------------

    @Test
    fun boundary_touching_is_legal() {
        assertFalse(
            FfcsSlotSchema.periodsOverlap(
                period("mon", 480, 530),
                period("mon", 530, 580),
            )
        )
        // Same boundary through the full schema: A1 ends 8:50, L2 starts 8:50 on Mon.
        assertFalse(FfcsSlotSchema.slotsOverlap("A1", "L2"))
    }

    @Test
    fun real_overlap_is_illegal_and_requires_same_day() {
        assertTrue(
            FfcsSlotSchema.periodsOverlap(
                period("mon", 535, 585),
                period("mon", 530, 580),
            )
        )
        assertFalse(
            FfcsSlotSchema.periodsOverlap(
                period("tue", 535, 585),
                period("mon", 530, 580),
            )
        )
    }

    @Test
    fun lab_and_theory_use_the_same_clock_test() {
        // A1 and L1 both occupy Mon 8:00-8:50.
        assertTrue(FfcsSlotSchema.slotsOverlap("A1", "L1"))
        // Same-day/same-half is penalised (-30) in pairing, not forbidden here.
        assertEquals(-30, FfcsSlotSchema.SAME_DAY_SAME_HALF_PENALTY)
        assertEquals(840, FfcsSlotSchema.MORNING_BEFORE_MIN)
    }

    @Test
    fun compound_slots_are_exploded_on_plus() {
        val periods = FfcsSlotSchema.splitSlots(" a1+ta1 + L31 ")
        assertEquals(listOf("A1", "TA1", "L31"), periods)
    }

    // ---- lab windows derived, not hard-coded --------------------------------

    @Test
    fun lab_period_ids_resolve_from_the_source_data() {
        assertEquals(setOf("L1", "L7", "L13", "L19", "L25"), SlotMap.labTokensOf("L01"))
        assertEquals(setOf("L6", "L12", "L18", "L24", "L30"), SlotMap.labTokensOf("L06"))
        assertEquals(setOf("L31", "L37", "L43", "L49", "L55"), SlotMap.labTokensOf("L08"))
        assertEquals(setOf("L36", "L42", "L48", "L54", "L60"), SlotMap.labTokensOf("L13"))
        // L07 is the lunch gap; no fabricated tokens.
        assertTrue(SlotMap.labTokensOf("L07").isEmpty())
    }

    @Test
    fun lab_window_sets_cover_the_canonical_tokens() {
        assertEquals(20, SlotMap.coreMorningLabSlots().size)
        assertEquals(20, SlotMap.coreEveningLabSlots().size)
        assertEquals(10, SlotMap.lateMorningLabSlots().size)
        assertEquals(10, SlotMap.lateEveningLabSlots().size)
        assertTrue(SlotMap.coreMorningLabSlots().contains("L1"))
        assertTrue(SlotMap.coreMorningLabSlots().contains("L28"))
        assertTrue(SlotMap.coreEveningLabSlots().contains("L31"))
        assertTrue(SlotMap.coreEveningLabSlots().contains("L58"))
        assertTrue(SlotMap.lateMorningLabSlots().containsAll(setOf("L5", "L6", "L30")))
        assertTrue(SlotMap.lateEveningLabSlots().contains("L60"))
        assertTrue(
            (SlotMap.coreMorningLabSlots() + SlotMap.coreEveningLabSlots() +
                SlotMap.lateMorningLabSlots() + SlotMap.lateEveningLabSlots())
                .all { it in SlotMap.labTokens() }
        )
    }
}
