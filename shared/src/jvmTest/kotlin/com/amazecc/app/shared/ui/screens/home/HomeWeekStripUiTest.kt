package com.amazecc.app.shared.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.AttendanceDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * The week strip as a user meets it: seven discs, one accessible name each, and a drag that
 * pages the week.
 *
 * Ported from `../AmazeCC/src/__tests__/simplifiedMobileHome.weekStrip.test.tsx`, which renders
 * the real page and drives the real gesture against the DOM. What that file asserts about CSS
 * (`bg-amber-500/10`, `ring-emerald-500`) has no Compose equivalent and is deliberately not
 * ported - a Compose disc carries its tint in a `Color`, not in a class name, and pinning pixel
 * values would test the theme rather than the strip. What is asserted here is what the semantics
 * tree can honestly answer: which discs exist, what each one is called, and whether the gesture
 * reaches the callbacks.
 *
 * The week's own dates, kinds and paging are the model's job and are pinned in `HomeModelsTest`;
 * this file exists for the two things a pure test cannot see - that the discs are actually drawn
 * with the descriptions the model produced, and that a swipe is a swipe.
 */
@OptIn(ExperimentalTestApi::class)
class HomeWeekStripUiTest {

    /** Tue 29 Sept 2026, which puts today on the second disc of the visible week. */
    private val tuesday = LocalDate(2026, 9, 29)

    /**
     * Node's fixture, in this file's shape: an exam on the Tuesday, and two October
     * no-instructional days. They need their own October block because a week is matched to a
     * calendar by month *and* year - a "Sept 2026" entry says nothing about 1 Oct.
     */
    private fun months() = listOf(
        CalendarMonth("Sept 2026", emptyList()),
        CalendarMonth(
            "Oct 2026",
            listOf(
                CalendarDay(1, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
                CalendarDay(3, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
            ),
        ),
    )

    private fun week(exams: List<ExamItem> = emptyList()) = buildHomeWeekDays(
        weekOffset = 0,
        today = tuesday,
        exams = exams,
        calendarMonths = months(),
        deadlineDates = emptySet(),
    )

    @Composable
    private fun strip(
        days: List<HomeWeekDay>,
        onSelectDay: (AttendanceDay) -> Unit = {},
        onSwipeNext: () -> Unit = {},
        onSwipePrev: () -> Unit = {},
    ) = AmazeTheme {
        HomeWeekStrip(
            days = days,
            selectedDay = AttendanceDay.TUE,
            classCounts = emptyMap(),
            onSelectDay = onSelectDay,
            onSwipeNext = onSwipeNext,
            onSwipePrev = onSwipePrev,
        )
    }

    @Test
    fun `draws seven discs monday first and describes each one`() = runComposeUiTest {
        setContent { strip(week()) }

        onNodeWithContentDescription("Mon 28 Sept · No classes").assertExists()
        onNodeWithContentDescription("Tue 29 Sept · No classes").assertExists()
        onNodeWithContentDescription("Wed 30 Sept · No classes").assertExists()
        onNodeWithContentDescription("Thu 1 Oct · Academic holiday").assertExists()
        onNodeWithContentDescription("Fri 2 Oct · No classes").assertExists()
        onNodeWithContentDescription("Sat 3 Oct · Academic holiday").assertExists()
        onNodeWithContentDescription("Sun 4 Oct · No classes").assertExists()
    }

    @Test
    fun `names an exam disc as an exam day with its paper count`() = runComposeUiTest {
        val paper = ExamItem(courseCode = "BAGER101", courseTitle = "German Level I", examDate = "29-09-2026")

        setContent { strip(week(exams = listOf(paper))) }

        onNodeWithContentDescription("Tue 29 Sept · Exam day, 1 exam").assertExists()
        // The exam is the day's whole story, so the ordinary description must not also be there.
        onNodeWithContentDescription("Tue 29 Sept · No classes").assertDoesNotExist()
    }

    @Test
    fun `a left swipe pages forward and a right swipe comes back`() = runComposeUiTest {
        var forward = 0
        var back = 0
        setContent {
            strip(
                week(),
                onSwipeNext = { forward++ },
                onSwipePrev = { back++ },
            )
        }

        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(1, forward)
        assertEquals(0, back)

        onRoot().performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(1, back)
    }

    @Test
    fun `a swipe is not a tap - it must not select the day it started on`() = runComposeUiTest {
        val tapped = mutableListOf<AttendanceDay>()

        setContent { strip(week(), onSelectDay = { tapped.add(it) }) }

        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()

        assertTrue(tapped.isEmpty(), "a swipe selected ${tapped.joinToString()}")
    }
}
