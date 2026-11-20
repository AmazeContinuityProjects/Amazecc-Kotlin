package com.amazecc.app.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class NavTapTrackerTest {

    @Test
    fun firstTapIsSingleAndSecondInsideWindowGoesHome() {
        val tracker = NavTapTracker(clock = { 1_000L })
        assertEquals(NavTapOutcome.Single, tracker.tap("ACADEMICS", wasActive = true, nowMs = 1_000L))
        assertEquals(
            NavTapOutcome.GoRoot(collapseFirstTap = false),
            tracker.tap("ACADEMICS", wasActive = true, nowMs = 1_300L)
        )
    }

    @Test
    fun firstTapThatNavigatedSetsCollapse() {
        val tracker = NavTapTracker(clock = { 0L })
        tracker.tap("ATTENDANCE", wasActive = false, nowMs = 100L)
        assertEquals(
            NavTapOutcome.GoRoot(collapseFirstTap = true),
            tracker.tap("ATTENDANCE", wasActive = true, nowMs = 200L)
        )
    }

    @Test
    fun pairIsConsumedAndSlowTapStartsNewWindow() {
        val tracker = NavTapTracker(clock = { 0L })
        tracker.tap("HOME", wasActive = true, nowMs = 0L)
        tracker.tap("HOME", wasActive = true, nowMs = 100L) // consumes the pair
        assertEquals(NavTapOutcome.Single, tracker.tap("HOME", wasActive = true, nowMs = 200L))

        tracker.reset("HOME")
        tracker.tap("HOME", wasActive = true, nowMs = 1_000L)
        assertEquals(
            NavTapOutcome.Single,
            tracker.tap("HOME", wasActive = true, nowMs = 1_400L) // > 350 ms window
        )
    }

    @Test
    fun tapsOnDifferentTabsDoNotPair() {
        val tracker = NavTapTracker(clock = { 0L })
        assertEquals(NavTapOutcome.Single, tracker.tap("HOME", wasActive = true, nowMs = 0L))
        assertEquals(NavTapOutcome.Single, tracker.tap("ACADEMICS", wasActive = false, nowMs = 50L))
    }
}
