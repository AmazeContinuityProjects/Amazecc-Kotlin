package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Grade
import com.amazecc.app.shared.domain.Semester
import com.amazecc.app.shared.domain.overhaul.CourseCompletionStatus
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CurriculumBasket as ModelCurriculumBasket
import com.amazecc.app.shared.model.CurriculumBasketItem
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless checks for the curriculum host's real-data derivation. */
class CurriculumUiStateTest {

    private fun snapshot() = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                "CH20252601" to Semester(
                    id = "CH20252601",
                    courses = mapOf(
                        "CSE1001" to Course(
                            code = "CSE1001", title = "Programming", type = "Theory Only",
                            credits = "4", category = "Professional Core",
                            attendance = Attendance(attended = 10, total = 12),
                            grade = Grade(letter = "S", grandTotal = "92"),
                        ),
                        "CSE1002" to Course(
                            code = "CSE1002", title = "Data Structures", type = "Theory Only",
                            credits = "3", category = "Professional Elective",
                            attendance = Attendance(attended = 8, total = 12),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun curriculum() = CurriculumRes(
        success = true,
        title = "B.Tech CSE",
        totalCredits = 160,
        categories = listOf(
            CurriculumCategory("PCC", "Programme Core Courses", 40, 40),
            CurriculumCategory("UCC", "University Core Courses", 60, 60),
            CurriculumCategory("OEC", "Open Elective Courses", 40, 40),
            CurriculumCategory("CON", "Concentration", 20, 20),
            CurriculumCategory("NC", "Non Credit Course", 4, 0),
            CurriculumCategory("EXTRA", "Extracurricular Activities", 2, 2),
        ),
        details = listOf(
            CategoryDetail("PCC", "Programme Core Courses", listOf(
                ModelCurriculumBasket("Core", 40, listOf(CurriculumBasketItem("CSE1001", "Programming", 4))),
            )),
            CategoryDetail("OEC", "Open Elective Courses", listOf(
                ModelCurriculumBasket("Elective", 40, listOf(CurriculumBasketItem("CSE1002", "Data Structures", 3))),
            )),
            CategoryDetail("EXTRA", "Extracurricular Activities", emptyList()),
        ),
    )

    @Test
    fun realVtopCategoryCodesMapToTheCoarseFilterChips() {
        assertEquals(CurriculumBasket.PROGRAM_CORE, curriculumBasketFor("PCC", "Programme Core Courses"))
        assertEquals(CurriculumBasket.UNIVERSITY_CORE, curriculumBasketFor("UCC", "University Core Courses"))
        assertEquals(CurriculumBasket.UNIVERSITY_ELECTIVE, curriculumBasketFor("OEC", "Open Elective Courses"))
        assertEquals(CurriculumBasket.PROGRAM_ELECTIVE, curriculumBasketFor("CON", "Concentration"))
        assertEquals(CurriculumBasket.ALL, curriculumBasketFor("NC", "Non Credit Course"))
        // Legacy codes still resolve so older cached payloads keep working.
        assertEquals(CurriculumBasket.PROGRAM_CORE, curriculumBasketFor("PC", "Program Core"))
        assertEquals(CurriculumBasket.PROGRAM_ELECTIVE, curriculumBasketFor("PE", "Program Elective"))
        assertEquals(CurriculumBasket.UNIVERSITY_CORE, curriculumBasketFor("UC", "University Core"))
        assertEquals(CurriculumBasket.UNIVERSITY_ELECTIVE, curriculumBasketFor("UE", "University Elective"))
    }

    @Test
    fun stateUsesRealCreditsStatusesAndProgrammeName() {
        val state = buildCurriculumState(
            curriculumRes = curriculum(),
            snapshot = snapshot(),
            publishedCreditsEarned = "120",
            programmeName = "B.Tech Computer Science",
        )

        assertEquals("B.Tech Computer Science", state.programmeName)
        assertEquals(4, state.totalCreditsCompleted) // earned from the real grade, not a placeholder
        assertEquals(160, state.totalCreditsRequired) // published total, not a hard-coded one
        assertEquals(6, state.categoryRows.size)
        assertTrue(state.categoryRows.any { it.title == "Extracurricular Activities" && it.isSpecial })

        val done = state.courses.first { it.courseCode == "CSE1001" }
        assertEquals(CourseCompletionStatus.COMPLETED, done.status)
        assertEquals("S", done.grade)
        assertEquals(CurriculumBasket.PROGRAM_CORE, done.basket)

        val live = state.courses.first { it.courseCode == "CSE1002" }
        assertEquals(CourseCompletionStatus.IN_PROGRESS, live.status)
        assertEquals(null, live.grade)
        assertEquals(CurriculumBasket.UNIVERSITY_ELECTIVE, live.basket)
    }

    @Test
    fun basketFilterAndSearchAreAppliedToTheRealCourses() {
        val core = buildCurriculumState(
            curriculumRes = curriculum(), snapshot = snapshot(),
            selectedBasket = CurriculumBasket.PROGRAM_CORE,
        )
        assertEquals(listOf("CSE1001"), core.filteredCourses.map { it.courseCode })

        val searched = buildCurriculumState(
            curriculumRes = curriculum(), snapshot = snapshot(), searchQuery = "structures",
        )
        assertEquals(listOf("CSE1002"), searched.filteredCourses.map { it.courseCode })
    }

    @Test
    fun emptyPayloadNeverInventsACurriculum() {
        val state = buildCurriculumState(curriculumRes = null, snapshot = DomainSnapshot())
        assertEquals(0, state.totalCreditsCompleted)
        assertTrue(state.courses.isEmpty())
        assertTrue(state.baskets.isEmpty())
    }
}
