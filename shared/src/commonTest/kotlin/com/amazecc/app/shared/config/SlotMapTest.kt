package com.amazecc.app.shared.config

import com.amazecc.app.shared.utils.TimeMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotMapTest {

    /**
     * `SlotMap` is a Kotlin copy of `AmazeCC/config.json`'s `slotMap`, made because the app
     * cannot read JSON at runtime on every target. It is a copy, so it can drift — and it did.
     *
     * These tests pin the invariants that `config.json` establishes and that had already been
     * broken once: every day present, every range well-formed, and every day's sixth-period lab
     * occupying the same slot.
     */
    @Test
    fun allSevenDaysArePresent() {
        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), SlotMap.days)
        assertEquals(SlotMap.days, SlotMap.map.keys.toList())
        // Weekdays are a strict subset: FFCS builds a five-day grid from it.
        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI"), SlotMap.weekdays)
        assertTrue(SlotMap.weekdays.all { it in SlotMap.days })
        // Every day has both theory and lab periods.
        SlotMap.map.forEach { (day, slots) ->
            assertTrue(slots.isNotEmpty(), "$day has no slots")
            assertTrue(slots.keys.any { it.startsWith("L") }, "$day has no lab slots")
        }
    }

    @Test
    fun everyRangeIsWellFormed() {
        SlotMap.map.forEach { (day, slots) ->
            slots.forEach { (code, range) ->
                val (start, end) = TimeMath.toRange(range)
                assertTrue(
                    start in 0..1439 && end in 0..1439,
                    "$day/$code '$range' parsed out of bounds ($start, $end)",
                )
                assertTrue(
                    start < end,
                    "$day/$code '$range' does not end after it starts ($start, $end)",
                )
            }
        }
    }

    /**
     * The bug this suite exists for: `WED/L18` and `THU/L24` were hand-copied from the *theory*
     * sixth period (`12:35-1:25`) instead of the lab one, so those two labs showed five minutes
     * late while the other five days were correct. `config.json` has `12:30-1:20` for all seven.
     */
    @Test
    fun everySixthPeriodLabOccupiesTheSameSlot() {
        val periodSixLabs = mapOf(
            "MON" to "L6", "TUE" to "L12", "WED" to "L18", "THU" to "L24",
            "FRI" to "L30", "SAT" to "L76", "SUN" to "L88",
        )
        periodSixLabs.forEach { (day, code) ->
            assertEquals(
                "12:30-1:20",
                SlotMap.map[day]?.get(code),
                "$day/$code drifted from config.json",
            )
        }
        // And the theory sixth period is deliberately five minutes later on the days that have one.
        assertEquals("12:35-1:25", SlotMap.map["MON"]?.get("S11"))
        assertEquals("12:35-1:25", SlotMap.map["FRI"]?.get("S15"))
    }

    @Test
    fun dayLabelsCoverEveryDay() {
        assertEquals(SlotMap.days.toSet(), SlotMap.dayLabels.keys)
        SlotMap.map.keys.forEach { day ->
            assertEquals(day.lowercase().replaceFirstChar { it.uppercase() }, SlotMap.dayLabels[day])
        }
    }

    @Test
    fun totalSlotsMatchesVocabularyCount() {
        assertEquals(164, SlotMap.TOTAL_SLOTS)
        var total = 0
        SlotMap.days.forEach { day ->
            total += SlotMap.totalSlotsAcrossDay(day)
        }
        assertEquals(164, total)
    }

    @Test
    fun lawSchoolBuddyTranslationResolvesCorrectly() {
        // Direct matches
        assertEquals("8:00-8:50", SlotMap.getTime("MON", "A1"))
        assertEquals("12:35-1:25", SlotMap.getTime("MON", "S11"))
        assertEquals("12:35-1:25", SlotMap.getTime("FRI", "S15"))

        // Law School buddy spellings: A -> A1, TEE -> S11, TFF -> S15, TAA -> TAA1
        assertEquals("8:00-8:50", SlotMap.getTime("MON", "A"))
        assertEquals("12:35-1:25", SlotMap.getTime("MON", "TEE"))
        assertEquals("12:35-1:25", SlotMap.getTime("FRI", "TFF"))
        assertEquals("11:40-12:30", SlotMap.getTime("TUE", "TAA"))

        // Invalidation: non-law or invalid slot returns null
        assertEquals(null, SlotMap.getTime("MON", "NIL"))
        assertEquals(null, SlotMap.getTime("SUN", "UNKNOWN_SLOT"))
    }

    @Test
    fun slotRangeAndTimeMath() {
        // "8:00" -> 480 (8 AM)
        assertEquals(480, SlotMap.toMinutes("8:00"))
        // "1:20" -> 800 (1:20 PM by 1-7 PM rule)
        assertEquals(800, SlotMap.toMinutes("1:20"))
        assertEquals("1:20 PM", SlotMap.minutesToTimeStr(800))
        assertEquals("8:00 AM", SlotMap.minutesToTimeStr(480))

        val (start, end) = SlotMap.slotRange("8:00-8:50")
        assertEquals(480, start)
        assertEquals(530, end)
        assertEquals(50, SlotMap.slotSpanMinutes("8:00-8:50"))
    }

    @Test
    fun compoundSlotRangeResolution() {
        // A1 (8:00-8:50) + TB1 (10:45-11:35) on MON
        val range1 = SlotMap.resolveRange("MON", "A1+TB1")
        assertEquals(480 to 695, range1)

        // Compound slot using Law School spellings: A + TB on MON
        val rangeLaw = SlotMap.resolveRange("MON", "A+TB")
        assertEquals(480 to 695, rangeLaw)

        // Lab slots: L31+L32 on MON (2:00-2:50 and 2:50-3:40 -> 14:00 to 15:40 = 840 to 940)
        val rangeLab = SlotMap.resolveRange("MON", "L31+L32")
        assertEquals(840 to 940, rangeLab)
    }

    @Test
    fun chennaiCampusSchemaClashDetection() {
        // A1 (MON 8:00-8:50) vs L1 (MON 8:00-8:50) -> CLASH
        assertTrue(com.amazecc.app.shared.domain.slotsOverlap("A1", "L1"))
        // Law slot A (MON 8:00-8:50) vs L1 -> CLASH
        assertTrue(com.amazecc.app.shared.domain.slotsOverlap("A", "L1"))
        // Morning theory vs afternoon lab (A1 vs L31+L32) -> NO CLASH
        kotlin.test.assertFalse(com.amazecc.app.shared.domain.slotsOverlap("A1", "L31+L32"))
    }
}
