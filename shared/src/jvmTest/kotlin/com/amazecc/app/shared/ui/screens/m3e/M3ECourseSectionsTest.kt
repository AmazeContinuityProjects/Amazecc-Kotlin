package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.CachedCourseMaterialRef
import com.amazecc.app.shared.domain.CachedCourseMaterialRow
import com.amazecc.app.shared.domain.CachedCoursePage
import com.amazecc.app.shared.domain.CoursePageCache
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.coursePageCacheAge
import com.amazecc.app.shared.domain.parseCoursePageRows
import com.amazecc.app.shared.model.CalendarDay as PublishedDay
import com.amazecc.app.shared.model.CalendarEvent as PublishedEvent
import com.amazecc.app.shared.model.CalendarMonth as PublishedMonth
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic acceptance checks for the two D-170 course sections.
 *
 * No network, no global settings and no wall clock: the forecast is fed an explicit `now`
 * and a real September 2026 calendar, and every materials assertion goes through the
 * stateless content composable or the pure parse/cache helpers.
 */
@OptIn(ExperimentalTestApi::class)
class M3ECourseSectionsTest {

    private val now = LocalDateTime(2026, 9, 1, 0, 0)

    private fun workingEvents(): List<PublishedEvent> = listOf(
        PublishedEvent(type = "Instructional Day", text = "Working Day", category = "Working day"),
    )

    private fun publishedMonths(cat1OnDay: Int? = null): List<PublishedMonth> = listOf(
        PublishedMonth(
            month = "September 2026",
            monthNumber = 9,
            year = 2026,
            days = (1..30).map { day ->
                val events = workingEvents().toMutableList()
                if (day == cat1OnDay) {
                    events += PublishedEvent(
                        type = "Instructional Day",
                        text = "CAT I",
                        category = "CAT I",
                    )
                }
                PublishedDay(date = day, events = events)
            },
        ),
    )

    private fun theoryHalf() = EmbeddedCourseHalf(
        code = "CSE3002(T)",
        title = "Internet and Web Programming",
        type = "Embedded Theory",
        half = "T",
        slots = listOf("B1"),
        attendance = Attendance(attended = 28, total = 30, percentage = "93.33"),
    )

    private fun snapshot(cat1OnDay: Int? = null) = buildCourseForecastSnapshot(
        component = theoryHalf(),
        months = publishedMonths(cat1OnDay),
        yearByMonth = buildYearByMonth(publishedMonths(cat1OnDay)),
        now = now,
        targetPercent = 75.0,
    )

    @Test
    fun missingAttendanceRendersHonestEmptyState() = runComposeUiTest {
        val empty = buildCourseForecastSnapshot(
            component = EmbeddedCourseHalf(code = "CSE3002(T)", slots = listOf("B1")),
            months = publishedMonths(),
            yearByMonth = buildYearByMonth(publishedMonths()),
            now = now,
            targetPercent = 75.0,
        )
        assertNull(empty)

        setContent {
            AmazeM3ETheme { M3EForecastContent(snapshot = null) }
        }
        onNodeWithText("No attendance data available for this course.").assertExists()
    }

    @Test
    fun realAttendanceFixtureProjectsRealHeroAndMilestones() = runComposeUiTest {
        val result = snapshot()
        assertNotNull(result)

        // 28/30 attended, nine Tue/Thu meetings left in September 2026.
        assertEquals(28, result.hero.attended)
        assertEquals(30, result.hero.total)
        assertEquals(93.3333, result.hero.currentPct, 0.01)
        assertEquals(0, result.hero.classesNeeded)
        assertEquals(10, result.hero.safeBunks)
        assertFalse(result.hero.isLab)
        assertEquals(4, result.milestones.size)
        assertEquals(listOf(9, 9, 9, 9), result.milestones.map { it.count })

        setContent {
            AmazeM3ETheme { M3EForecastContent(snapshot = result) }
        }
        onNodeWithText("93.3%").assertExists()
        onNodeWithText("10 bunkable").assertExists()
        onNodeWithText("28/30 attended").assertExists()
        onNodeWithText("Classes before CAT I").assertExists()
        onNodeWithText("Classes before Mid Term Test").assertExists()
        onNodeWithText("Classes before FAT").assertExists()
    }

    @Test
    fun lockedMilestonePrintsRealZero() = runComposeUiTest {
        // CAT I on 2 Sep locks the cutoff to 30 Aug, before any September meeting.
        val result = snapshot(cat1OnDay = 2)
        assertNotNull(result)
        assertEquals(0, result.milestones.first { it.label == "Classes before CAT I" }.count)
        assertEquals(9, result.milestones.first { it.label == "Classes before FAT" }.count)

        setContent {
            AmazeM3ETheme { M3EForecastContent(snapshot = result) }
        }
        onNodeWithText("Classes before CAT I").assertExists()
        onNodeWithText("0").assertExists()
    }

    @Test
    fun marginHeadlineUsesRealProjection() {
        val hero = M3EAttendanceHero(
            attended = 28,
            total = 30,
            currentPct = 93.33,
            predictedPct = 94.87,
            safeBunks = 10,
            classesNeeded = 0,
            isLab = false,
        )
        assertEquals("10 bunkable", forecastMarginHeadline(hero))
        assertEquals("Need 3 classes", forecastMarginHeadline(hero.copy(safeBunks = 0, classesNeeded = 3)))
        assertEquals("Need 1 class", forecastMarginHeadline(hero.copy(safeBunks = 0, classesNeeded = 1)))
        assertEquals("On edge", forecastMarginHeadline(hero.copy(safeBunks = 0)))
    }

    @Test
    fun materialsNotFetchedYetState() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseMaterialsContent(
                    entry = null,
                    errorMessage = null,
                    isLoading = false,
                    nowMillis = 1_800_000_000_000L,
                    onReload = {},
                )
            }
        }
        onNodeWithText("Not fetched yet").assertExists()
        onNodeWithText("Tap Reload to pull the course page from VTOP.").assertExists()
        onNodeWithText("Reload").assertExists()
    }

    @Test
    fun reloadErrorKeepsCachedRowsVisible() = runComposeUiTest {
        val cached = CachedCoursePage(
            semesterId = "CH20262701",
            courseCode = "CSE3002",
            fetchedAt = 1_700_000_000_000L,
            rows = listOf(
                CachedCourseMaterialRow(
                    slNo = "1",
                    date = "12/09/2026",
                    slot = "B1",
                    topic = "Introduction to Web Programming",
                    materials = listOf(
                        CachedCourseMaterialRef(materialId = "m-1", label = "Lecture Notes.pdf"),
                    ),
                ),
            ),
        )
        setContent {
            AmazeM3ETheme {
                M3ECourseMaterialsContent(
                    entry = cached,
                    errorMessage = "Reload failed — showing cached data",
                    isLoading = false,
                    nowMillis = 1_700_000_000_000L + 12 * 60_000L,
                    onReload = {},
                )
            }
        }
        onNodeWithText("Reload failed — showing cached data").assertExists()
        onNodeWithText("Cached · 12 min ago").assertExists()
        onNodeWithText("Introduction to Web Programming").assertExists()
        onNodeWithText("Lecture Notes.pdf").assertExists()
        onNodeWithText("Not fetched yet").assertDoesNotExist()
    }

    @Test
    fun coursePageCacheRoundTripAndScopeMismatch() {
        val entry = CachedCoursePage(
            semesterId = "CH20262701",
            courseCode = "CSE3002",
            fetchedAt = 1_700_000_000_000L,
            rows = listOf(
                CachedCourseMaterialRow(
                    slNo = "1",
                    date = "12/09/2026",
                    slot = "B1",
                    topic = "Introduction",
                    materials = listOf(CachedCourseMaterialRef(materialId = "m-1", label = "Notes.pdf")),
                ),
            ),
        )
        val decoded = CoursePageCache.decode(CoursePageCache.encode(entry))
        assertNotNull(decoded)
        assertEquals(entry, decoded)
        assertEquals("CH20262701", decoded.semesterId)
        assertEquals(1, decoded.rows.size)
        assertEquals("Notes.pdf", decoded.rows.first().materials.first().label)

        assertTrue(decoded.matches("CH20262701", "CSE3002"))
        assertTrue(decoded.matches("CH20262701", "cse3002"))
        assertFalse(decoded.matches("CH20262702", "CSE3002"))
        assertFalse(decoded.matches("CH20262701", "CSE3003"))
        assertFalse(decoded.matches(null, "CSE3002"))
        assertNull(CoursePageCache.decode(null))
        assertNull(CoursePageCache.decode("not-json"))
    }

    @Test
    fun coursePageRowsParseDefensively() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": {
                "materials": {
                  "rows": [
                    {
                      "slNo": "1",
                      "date": "12/09/2026",
                      "slot": "B1",
                      "topic": "Intro",
                      "materials": [
                        { "materialId": "m-1", "label": "Notes.pdf", "materialDate": "13/09/2026" },
                        { "label": "" }
                      ]
                    },
                    {}
                  ]
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val rows = parseCoursePageRows(payload)
        assertEquals(1, rows.size)
        assertEquals("Intro", rows.first().topic)
        assertEquals("B1", rows.first().slot)
        assertEquals(1, rows.first().materials.size)
        assertEquals("m-1", rows.first().materials.first().materialId)
        assertEquals("13/09/2026", rows.first().materials.first().materialDate)

        assertTrue(parseCoursePageRows(Json.parseToJsonElement("{}").jsonObject).isEmpty())
        assertTrue(parseCoursePageRows(null).isEmpty())
    }

    @Test
    fun cacheAgeIsComputedFromRealTimestamps() {
        assertEquals("just now", coursePageCacheAge(1_000L, 20_000L))
        assertEquals("12 min ago", coursePageCacheAge(1_000L, 1_000L + 12 * 60_000L))
        assertEquals("2 h ago", coursePageCacheAge(1_000L, 1_000L + 2 * 60 * 60_000L))
        assertEquals("3 d ago", coursePageCacheAge(1_000L, 1_000L + 3 * 24 * 60 * 60_000L))
    }
}
