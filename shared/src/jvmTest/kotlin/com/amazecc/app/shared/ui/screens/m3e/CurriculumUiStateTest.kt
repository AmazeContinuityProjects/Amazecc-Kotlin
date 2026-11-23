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
import kotlin.test.assertNull
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

    // ── D-188: labelled embedded halves ─────────────────────────────────────

    private fun embeddedSnapshot(theoryGrade: String?, labGrade: String?) = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                "CH20252605" to Semester(
                    id = "CH20252605",
                    courses = mapOf(
                        "BACSE106" to Course(
                            code = "BACSE106", title = "Operating Systems", type = "Embedded Theory",
                            credits = "3", grade = theoryGrade?.let { Grade(letter = it) },
                        ),
                        "BACSE106(L)" to Course(
                            code = "BACSE106(L)", title = "Operating Systems Lab", type = "Embedded Lab",
                            credits = "2", grade = labGrade?.let { Grade(letter = it) },
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun embeddedCurriculum() = CurriculumRes(
        success = true,
        title = "B.Tech CSE",
        totalCredits = 160,
        categories = listOf(CurriculumCategory("PCC", "Programme Core Courses", 5, 5)),
        details = listOf(
            CategoryDetail("PCC", "Programme Core Courses", listOf(
                ModelCurriculumBasket("Core", 5, listOf(
                    CurriculumBasketItem("BACSE106", "Operating Systems", 3, "Embedded Theory"),
                    CurriculumBasketItem("BACSE106(L)", "Operating Systems Lab", 2, "Embedded Lab"),
                )),
            )),
        ),
    )

    @Test
    fun embeddedHalvesGetWebExactLabelsAndOneSummedHeader() {
        val state = buildCurriculumState(
            curriculumRes = embeddedCurriculum(),
            // VTOP publishes one grade for the pair, on the theory half.
            snapshot = embeddedSnapshot(theoryGrade = "S", labGrade = null),
        )

        val theory = state.courses.first { it.courseCode == "BACSE106" }
        val lab = state.courses.first { it.courseCode == "BACSE106(L)" }
        assertEquals("Embedded Theory", theory.embeddedLabel)
        assertEquals("Embedded Lab", lab.embeddedLabel)
        assertEquals("BACSE106", theory.baseCode)
        assertEquals("BACSE106", lab.baseCode)

        val sections = buildCurriculumCourseSections(state.filteredCourses)
        assertEquals(1, sections.size, "the two halves share ONE section, not two rows")
        val section = sections.single()
        assertEquals("Embedded Theory & Lab", section.label)
        assertEquals("BACSE106", section.baseCode)
        assertEquals(5, section.totalCredits, "3 + 2 shown once, never counted twice")
        assertEquals(listOf("BACSE106", "BACSE106(L)"), section.courses.map { it.courseCode })

        // Two grade entries, one per half. The lab row published no letter, so it stays blank and
        // no combined grade is fabricated from the theory half's letter or from any blend.
        assertEquals("S", theory.grade)
        assertNull(lab.grade)
        assertNull(section.combinedGrade)
    }

    @Test
    fun differentPublishedHalfLettersNeverFabricateACombinedGrade() {
        val state = buildCurriculumState(
            curriculumRes = embeddedCurriculum(),
            snapshot = embeddedSnapshot(theoryGrade = "S", labGrade = "A"),
        )
        val section = buildCurriculumCourseSections(state.filteredCourses).single()

        assertEquals("S", state.courses.first { it.courseCode == "BACSE106" }.grade)
        assertEquals("A", state.courses.first { it.courseCode == "BACSE106(L)" }.grade)
        assertNull(section.combinedGrade, "no shared letter -> no combined grade")
    }

    @Test
    fun anUnclassifiedTypeNeverClaimsAnEmbeddedLabel() {
        val unknown = embeddedCurriculum().copy(
            details = listOf(
                CategoryDetail("PCC", "Programme Core Courses", listOf(
                    ModelCurriculumBasket("Core", 5, listOf(
                        CurriculumBasketItem("BCSE201", "Soft Skills", 2, "Soft Skill"),
                    )),
                )),
            ),
        )
        val state = buildCurriculumState(curriculumRes = unknown, snapshot = DomainSnapshot())
        val course = state.courses.single()

        assertNull(course.embeddedLabel, "labelFor returned empty; no label may be invented")
        val section = buildCurriculumCourseSections(state.filteredCourses).single()
        assertNull(section.label)
        assertNull(section.combinedGrade)
    }

    /**
     * Cross-D regression: "Theory Only"/"Lab Only" are standalone course types, not embedded
     * halves. Even when their codes share a base (the shape that used to produce a false embedded
     * group), they must stay two plain rows with no label, no summed header and no combined grade.
     */
    @Test
    fun standaloneTheoryAndLabTypesNeverBecomeEmbeddedSections() {
        val standalone = embeddedCurriculum().copy(
            details = listOf(
                CategoryDetail("PCC", "Programme Core Courses", listOf(
                    ModelCurriculumBasket("Core", 5, listOf(
                        CurriculumBasketItem("BCSE201", "Communicative English", 2, "Theory Only"),
                        CurriculumBasketItem("BCSE201(L)", "English Lab", 3, "Lab Only"),
                    )),
                )),
            ),
        )
        val state = buildCurriculumState(curriculumRes = standalone, snapshot = DomainSnapshot())
        val sections = buildCurriculumCourseSections(state.filteredCourses)

        assertEquals(2, sections.size, "two standalone courses, not one embedded group")
        assertTrue(state.courses.all { it.embeddedLabel == null })
        assertTrue(sections.all { it.label == null }, "no embedded header")
        assertTrue(sections.all { it.combinedGrade == null })
        assertEquals(listOf(2, 3), sections.map { it.totalCredits }, "plain per-course credits")
    }

    /** Both web-exact combined spellings still pass the embedded-only guard and group once. */
    @Test
    fun combinedEmbeddedSpellingStillGroupsAndSumsCreditsOnce() {
        val combined = embeddedCurriculum().copy(
            details = listOf(
                CategoryDetail("PCC", "Programme Core Courses", listOf(
                    ModelCurriculumBasket("Core", 5, listOf(
                        CurriculumBasketItem("BACSE106", "Operating Systems", 3, "ETH+ELA"),
                        CurriculumBasketItem(
                            "BACSE106(L)", "Operating Systems Lab", 2,
                            "Embedded Theory and Lab",
                        ),
                    )),
                )),
            ),
        )
        val state = buildCurriculumState(
            curriculumRes = combined,
            snapshot = embeddedSnapshot(theoryGrade = "S", labGrade = null),
        )
        val sections = buildCurriculumCourseSections(state.filteredCourses)

        assertEquals(1, sections.size, "the pair shares one embedded section")
        val section = sections.single()
        assertEquals("Embedded Theory & Lab", section.label)
        assertEquals("BACSE106", section.baseCode)
        assertEquals(5, section.totalCredits, "3 + 2 shown once")
        assertEquals(listOf("BACSE106", "BACSE106(L)"), section.courses.map { it.courseCode })
    }

    /** A letter borrowed from a base-code fallback must never be published as a shared grade. */
    @Test
    fun aBaseFallbackLetterNeverBecomesACombinedGrade() {
        // The theory row is stored under a suffixed code, so the bare basket item "BACSE106" has
        // no exact row. Both letters read "S", which used to fabricate a combined grade.
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "CH20252605" to Semester(
                        id = "CH20252605",
                        courses = mapOf(
                            "BACSE106(T)" to Course(
                                code = "BACSE106(T)", title = "Operating Systems", type = "Embedded Theory",
                                credits = "3", grade = Grade(letter = "S"),
                            ),
                            "BACSE106(L)" to Course(
                                code = "BACSE106(L)", title = "Operating Systems Lab", type = "Embedded Lab",
                                credits = "2", grade = Grade(letter = "S"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = buildCurriculumState(curriculumRes = embeddedCurriculum(), snapshot = snapshot)
        val theory = state.courses.first { it.courseCode == "BACSE106" }
        val lab = state.courses.first { it.courseCode == "BACSE106(L)" }
        val section = buildCurriculumCourseSections(state.filteredCourses).single()

        assertEquals("S", theory.grade, "display still uses the published pair letter")
        assertEquals(false, theory.gradeFromExactRow, "no exact snapshot row for the bare code")
        assertEquals(true, lab.gradeFromExactRow)
        assertNull(section.combinedGrade, "a borrowed letter is not a shared published letter")
    }

    @Test
    fun standaloneTrailingLetterCodeKeepsItsCompletedStatus() {
        val standalone = CurriculumRes(
            success = true,
            title = "B.Tech CSE",
            totalCredits = 160,
            categories = listOf(CurriculumCategory("PCC", "Programme Core Courses", 3, 3)),
            details = listOf(
                CategoryDetail("PCC", "Programme Core Courses", listOf(
                    ModelCurriculumBasket("Core", 3, listOf(
                        CurriculumBasketItem("CSE101L", "Programming Lab", 3, "Lab Only"),
                    )),
                )),
            ),
        )
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "CH20252601" to Semester(
                        id = "CH20252601",
                        courses = mapOf(
                            "CSE101L" to Course(
                                code = "CSE101L", title = "Programming Lab", type = "Lab Only",
                                credits = "3", grade = Grade(letter = "A"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = buildCurriculumState(curriculumRes = standalone, snapshot = snapshot)
        val course = state.courses.single()

        assertEquals(CourseCompletionStatus.COMPLETED, course.status)
        assertNull(course.embeddedLabel, "Lab Only is standalone, never embedded")
        assertEquals("CSE101", course.baseCode)
    }
}
