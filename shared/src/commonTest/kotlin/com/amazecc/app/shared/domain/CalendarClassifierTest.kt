package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The calendar vocabulary itself - the port of the classifier half of
 * `AmazeCC/src/__tests__/analyzeCalendar.test.ts`.
 *
 * These are the rules every other calendar surface inherits - the grid, the day
 * sheet and the per-course predictors all classify through this module. The
 * regressions pinned here are not hypothetical: each of them shipped, and each
 * one made a real thing invisible rather than merely ugly.
 */
class CalendarClassifierTest {

    private fun ev(type: String? = null, text: String? = null, category: String? = null) =
        ClassifiableEvent(type = type, text = text, category = category)

    // ── normalize ────────────────────────────────────────────────────────────

    @Test
    fun `collapses the whitespace that punctuation leaves behind`() {
        // The bug: "CAT - I" became "cat   i", which no single-spaced keyword
        // could match, so CAT I and CAT II were never detected anywhere.
        assertEquals("cat i", CalendarClassifier.normalize("CAT - I"))
        assertEquals("cat ii", CalendarClassifier.normalize("CAT   II"))
        assertEquals("no instructional day", CalendarClassifier.normalize("No  Instructional  Day"))
        assertEquals("lid for theory classes", CalendarClassifier.normalize("LID FOR THEORY CLASSES"))
    }

    @Test
    fun `is empty for empty input`() {
        assertEquals("", CalendarClassifier.normalize(""))
        assertEquals("", CalendarClassifier.normalize(null))
    }

    // ── matchImportantEvent ──────────────────────────────────────────────────

    @Test
    fun `matches the written form of every milestone`() {
        assertEquals("cat i", CalendarClassifier.matchImportantEvent(ev(text = "CAT - I"))?.key)
        assertEquals("cat ii", CalendarClassifier.matchImportantEvent(ev(text = "CAT-II"))?.key)
        assertEquals(
            "lid for theory classes",
            CalendarClassifier.matchImportantEvent(ev(text = "LID FOR THEORY CLASSES"))?.key,
        )
        assertEquals(
            "lid for laboratory classes",
            CalendarClassifier.matchImportantEvent(ev(text = "LID FOR LABORATORY CLASSES"))?.key,
        )
        assertEquals(
            "lid for laboratory classes",
            CalendarClassifier.matchImportantEvent(ev(text = "LID for lab"))?.key,
        )
        assertEquals("mid term test", CalendarClassifier.matchImportantEvent(ev(text = "Mid Term Test"))?.key)
    }

    @Test
    fun `prefers the longest match so CAT II is not filed as CAT I`() {
        // "cat i" is a substring of "cat ii". A first-match scan returned CAT I
        // for every CAT II, which handed the predictor the wrong date.
        assertEquals("cat ii", CalendarClassifier.matchImportantEvent(ev(text = "CAT II"))?.key)
        assertEquals("CAT II", CalendarClassifier.matchImportantEvent(ev(text = "CAT - II"))?.display)
    }

    @Test
    fun `prefers the full laboratory name over its alias`() {
        assertEquals(
            "lid for laboratory classes",
            CalendarClassifier.matchImportantEvent(ev(text = "LID FOR LABORATORY CLASSES"))?.key,
        )
    }

    @Test
    fun `has a readable short name and a blurb for every entry`() {
        for (entry in CalendarClassifier.IMPORTANT_EVENTS) {
            assertTrue(entry.short.isNotEmpty(), "empty short for ${entry.key}")
            assertTrue(entry.blurb.isNotEmpty(), "empty blurb for ${entry.key}")
        }
    }

    @Test
    fun `keeps display in the exact form the predictor pages match on`() {
        // Callers look these up by exact lowercase string, so reworded here and
        // they silently return null.
        assertEquals(
            listOf(
                "CAT I",
                "CAT II",
                "LID FOR LABORATORY CLASSES",
                "LID FOR THEORY CLASSES",
                "MID TERM TEST",
            ),
            CalendarClassifier.IMPORTANT_EVENTS.map { it.display },
        )
    }

    @Test
    fun `returns null for an ordinary entry`() {
        assertNull(CalendarClassifier.matchImportantEvent(ev(text = "Vibrance 2026")))
        assertNull(CalendarClassifier.matchImportantEvent(ev(text = "")))
        assertNull(CalendarClassifier.matchImportantEvent(null))
    }

    // ── day-type classifiers ─────────────────────────────────────────────────

    @Test
    fun `separates a holiday from a non-instructional day`() {
        // Both used to be holidays, which is how a working day at an open
        // college came back as a day off.
        val holiday = ev(type = "Other", text = "Pongal", category = "Festival")
        val quiet = ev(type = "Other", text = "Non Instructional Day", category = "Working day")

        assertTrue(CalendarClassifier.isHolidayEvent(holiday))
        assertFalse(CalendarClassifier.isNonInstructionalEvent(holiday))

        assertFalse(CalendarClassifier.isHolidayEvent(quiet))
        assertTrue(CalendarClassifier.isNonInstructionalEvent(quiet))
    }

    @Test
    fun `does not call a non-instructional day instructional`() {
        // Its category is "Working day", which the instructional test also
        // matches. Whichever runs first wins, so the order is load-bearing.
        val quiet = ev(type = "Other", text = "Non Instructional Day", category = "Working day")
        assertFalse(CalendarClassifier.isInstructionalEvent(quiet))
    }

    @Test
    fun `still recognises a real instructional day`() {
        assertTrue(
            CalendarClassifier.isInstructionalEvent(
                ev(type = "Instructional Day", text = "Instructional Day", category = "Working day"),
            ),
        )
        // Category alone is enough when the type is not the marker.
        assertTrue(
            CalendarClassifier.isInstructionalEvent(
                ev(type = "Other", text = "Classes as usual", category = "Working day"),
            ),
        )
    }

    @Test
    fun `does not call a holiday instructional`() {
        assertFalse(
            CalendarClassifier.isInstructionalEvent(
                ev(type = "Holiday", text = "Christmas", category = "Festival"),
            ),
        )
    }
}
