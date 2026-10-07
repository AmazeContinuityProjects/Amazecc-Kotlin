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
}
