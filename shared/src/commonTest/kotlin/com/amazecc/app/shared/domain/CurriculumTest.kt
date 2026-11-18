package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumBasketItem
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of `src/__tests__/curriculum.test.ts` (8 cases, verbatim fixtures) plus coverage for the
 * derivations that only existed inside the composable.
 */
class CurriculumTest {

    // ── Node's fixtures ─────────────────────────────────────────────────────

    private val details = listOf(
        CategoryDetail(
            code = "FC",
            name = "Foundation Core",
            baskets = listOf(
                CurriculumBasket(
                    title = "Mathematics",
                    credits = 18,
                    items = listOf(CurriculumBasketItem(code = "BCSE101M", name = "Maths", credits = 3)),
                ),
            ),
        ),
        CategoryDetail(
            code = "PC",
            name = "Professional Core",
            baskets = listOf(
                CurriculumBasket(
                    title = "Systems",
                    credits = 24,
                    items = listOf(CurriculumBasketItem(code = "BCSE203E", name = "Embedded", credits = 3)),
                ),
            ),
        ),
    )

    private val curriculum = listOf(
        CurriculumRow("Foundation Core", "20", "18"),
        CurriculumRow("Professional Core", "60", "30"),
        CurriculumRow("Foreign Language", "4", "2"),
        CurriculumRow("Total Credits", "160", "100"),
    )

    // ── splitCurriculumRows ─────────────────────────────────────────────────

    @Test
    fun dropsTheTotalCreditsRowAndSplitsOutElectiveBaskets() {
        val (main, sub) = splitCurriculumRows(curriculum)
        assertEquals(listOf("Foundation Core", "Professional Core"), main.map { it.basketTitle })
        assertEquals(listOf("Foreign Language"), sub.map { it.basketTitle })
    }

    @Test
    fun theTotalRowIsMatchedCaseInsensitivelyAndAsASubstring() {
        assertTrue(isTotalCreditsRow("total credits"))
        assertTrue(isTotalCreditsRow("TOTAL CREDITS"))
        assertTrue(isTotalCreditsRow("Programme Total Credits Required"))
        assertTrue(!isTotalCreditsRow("Total Credit"))
    }

    @Test
    fun everySpecialBasketIsMatchedCaseInsensitively() {
        assertTrue(isSpecialBasket("Foreign Language"))
        assertTrue(isSpecialBasket("HSM ELECTIVE"))
        assertTrue(isSpecialBasket("extra curricular activities"))
        assertTrue(!isSpecialBasket("Foundation Core"))
    }

    // ── buildCategoryRows: primary source ───────────────────────────────────

    @Test
    fun buildsRowsFromTheFlatCurriculumListAsThePrimarySource() {
        val rows = buildCategoryRows(
            curriculum = curriculum,
            categories = emptyList(),
            details = details,
            ongoingByCategory = mapOf("Professional Core" to 9f),
        )
        assertEquals(2, rows.size)
        assertEquals("cur:Foundation Core", rows[0].key)
        assertEquals("Foundation Core", rows[0].title)
        assertEquals(18f, rows[0].earned)
        assertEquals(20f, rows[0].required)
        assertEquals(0f, rows[0].ongoing)
        assertEquals("Mathematics", rows[0].baskets[0].title)
        assertEquals(9f, rows[1].ongoing)
        // No code chip on this source.
        assertNull(rows[0].code)
    }

    // ── buildCategoryRows: fallback source ─────────────────────────────────

    @Test
    fun fallsBackToTheCategoriesArrayWhenTheFlatListIsEmpty() {
        // This is Node's regression: accounts without a flat `curriculum` list still have
        // `categories`, and the baskets must still render.
        val categories = listOf(
            CurriculumCategory("FC", "Foundation Core", credits = 18, maxCredits = 20),
            CurriculumCategory("PC", "Professional Core", credits = 30, maxCredits = 60),
        )
        val rows = buildCategoryRows(
            curriculum = emptyList(),
            categories = categories,
            details = details,
            ongoingByCategory = emptyMap(),
        )
        assertEquals(2, rows.size)
        assertEquals("cat:FC", rows[0].key)
        assertEquals("FC", rows[0].code)
        assertEquals("Foundation Core", rows[0].title)
        assertEquals(18f, rows[0].earned)
        assertEquals(20f, rows[0].required)
        // Baskets resolved through the details code join.
        assertEquals("Mathematics", rows[0].baskets[0].title)
        assertEquals("Systems", rows[1].baskets[0].title)
    }

    @Test
    fun returnsNothingWhenBothSourcesAreEmpty() {
        assertEquals(
            emptyList(),
            buildCategoryRows(curriculum = emptyList(), categories = emptyList(), details = emptyList()),
        )
    }

    @Test
    fun neverDividesByAZeroRequirement() {
        val primary = buildCategoryRows(
            curriculum = listOf(CurriculumRow("Odd", "0", "0")),
            details = emptyList(),
        )
        assertEquals(1f, primary[0].required)

        val fallback = buildCategoryRows(
            curriculum = emptyList(),
            categories = listOf(CurriculumCategory("X", "Odd", credits = 0, maxCredits = 0)),
            details = emptyList(),
        )
        assertEquals(1f, fallback[0].required)
    }

    @Test
    fun toleratesACategoryWithNoMatchingDetailEntry() {
        val rows = buildCategoryRows(
            curriculum = emptyList(),
            categories = listOf(CurriculumCategory("ZZ", "Unknown", credits = 3, maxCredits = 4)),
            details = details,
        )
        assertEquals(emptyList(), rows[0].baskets)
    }

    // ── buildBasketMap ──────────────────────────────────────────────────────

    @Test
    fun mapsDetailsToBasketsByCategoryName() {
        val map = buildBasketMap(details)
        assertEquals(1, map["Foundation Core"]?.size)
        assertNull(map["Nope"])
    }

    @Test
    fun buildBasketMapConcatenatesBasketsWhenTwoDetailsShareAName() {
        val merged = buildBasketMap(
            listOf(
                CategoryDetail("A", "Same", listOf(CurriculumBasket("One", 3))),
                CategoryDetail("B", "Same", listOf(CurriculumBasket("Two", 3))),
            ),
        )
        assertEquals(listOf("One", "Two"), merged["Same"]?.map { it.title })
    }

    // ── curriculumNum ───────────────────────────────────────────────────────

    @Test
    fun numCoercesJunkToAFallback() {
        assertEquals(18.5f, curriculumNum("18.5"))
        assertEquals(0f, curriculumNum(null))
        assertEquals(7f, curriculumNum("abc", 7f))
        assertEquals(3f, curriculumNum("  3  "))
    }

    // ── ongoing credits by category ─────────────────────────────────────────

    private fun course(
        code: String,
        category: String?,
        credits: String? = "3",
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = code,
        courseType = "Theory Only",
        slotName = "A1",
        slotVenue = null,
        faculty = "",
        credits = credits,
        category = category,
        attendedClasses = 0,
        totalClasses = 0,
        attendancePercentage = "",
        logs = emptyList(),
    )

    @Test
    fun ongoingCreditsAreKeyedByTheCourseCategory() {
        val map = ongoingCreditsByCategory(
            listOf(
                course("BCSE101M", "Foundation Core"),
                course("BCSE102M", "Foundation Core"),
                course("BCSE203E", "Professional Core"),
            ),
        )
        assertEquals(6f, map["Foundation Core"])
        assertEquals(3f, map["Professional Core"])
    }

    @Test
    fun theHssmSubBasketsAreRenamedOntoTheRowsThePageActuallyHas() {
        assertEquals("Foreign Language", normaliseOngoingCategory("$HSSM_BASKET (LANGUAGE Basket)"))
        assertEquals("HSM Elective", normaliseOngoingCategory("$HSSM_BASKET (GENERAL Basket)"))
        assertEquals("Extra extracurricular activities", normaliseOngoingCategory("$HSSM_BASKET (EXTRA CURRICULAR Basket)"))
    }

    @Test
    fun anHssmSubBasketIsCountedUnderBothItsOwnNameAndItsParent() {
        val map = ongoingCreditsByCategory(listOf(course("BCSE104L", "$HSSM_BASKET (LANGUAGE Basket)")))
        assertEquals(3f, map["Foreign Language"])
        assertEquals(3f, map[HSSM_BASKET])
    }

    @Test
    fun extraCurricularCreditsAlsoLandInTheNonGradedCoreRequirement() {
        val map = ongoingCreditsByCategory(listOf(course("BCSE105L", "$HSSM_BASKET (EXTRA CURRICULAR Basket)")))
        assertEquals(3f, map["Extra extracurricular activities"])
        assertEquals(3f, map[NGCR_REQUIREMENT])
    }

    @Test
    fun aCourseWithNoCategoryIsUncategorizedRatherThanDropped() {
        val map = ongoingCreditsByCategory(listOf(course("BCSE106L", null)))
        assertEquals(3f, map[UNCATEGORIZED])
    }

    @Test
    fun totalOngoingCreditsDoesNotCountAnHssmSubBasketTwice() {
        // The map double-entries these, so summing it would report 6 for 3 credits of French.
        val courses = listOf(course("BCSE104L", "$HSSM_BASKET (LANGUAGE Basket)"))
        assertEquals(3f, totalOngoingCredits(courses))
        assertEquals(6f, ongoingCreditsByCategory(courses).values.sum())
    }

    @Test
    fun unparseableCreditsCountAsZeroRatherThanFailing() {
        val map = ongoingCreditsByCategory(listOf(course("BCSE107L", "Foundation Core", credits = "n/a")))
        assertTrue(map.isEmpty())
        assertEquals(0f, totalOngoingCredits(listOf(course("BCSE107L", "Foundation Core", credits = null))))
    }

    // ── course status ───────────────────────────────────────────────────────

    @Test
    fun statusIsMatchedCaseInsensitivelyOnBothSides() {
        val completed = setOf("BCSE101M")
        val ongoing = setOf("BCSE203E")
        // The basket item is lower-cased where the graded course is upper-cased.
        assertEquals(CourseStatus.COMPLETED, courseStatus("bcse101m", completed, ongoing))
        assertEquals(CourseStatus.IN_PROGRESS, courseStatus("BCSE203E", completed, ongoing))
        assertEquals(CourseStatus.REMAINING, courseStatus("BCSE999Z", completed, ongoing))
    }

    @Test
    fun aBlankCodeIsRemainingRatherThanAFalseMatch() {
        assertEquals(CourseStatus.REMAINING, courseStatus(null, setOf(""), setOf("")))
        assertEquals(CourseStatus.REMAINING, courseStatus("   ", setOf(""), setOf("")))
    }

    @Test
    fun completedWinsOverOngoing() {
        assertEquals(
            CourseStatus.COMPLETED,
            courseStatus("BCSE101M", completed = setOf("BCSE101M"), ongoing = setOf("BCSE101M")),
        )
    }

    // ── grades ──────────────────────────────────────────────────────────────

    private fun snapshot(vararg courses: Pair<String, String?>) = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                "CH20262701" to Semester(
                    id = "CH20262701",
                    courses = courses.toList().associate { (code, grade) ->
                        code to Course(
                            code = code,
                            title = code,
                            credits = "3",
                            grade = grade?.let { Grade(letter = it) },
                        )
                    },
                ),
            ),
        ),
    )

    @Test
    fun aFailedOrNotTakenCourseIsNeitherCompletedNorEarned() {
        val s = snapshot("BCSE101M" to "A", "BCSE102M" to "F", "BCSE103M" to "N", "BCSE104M" to null)
        assertEquals(setOf("BCSE101M"), completedCourseCodes(s))
        assertEquals(3f, earnedCreditsFromGrades(s))
    }

    @Test
    fun aBlankGradeIsNotProgress() {
        assertEquals(emptySet(), completedCourseCodes(snapshot("BCSE101M" to "")))
    }

    /**
     * An embedded pair is two stored rows with one grade between them.
     *
     * VTOP reports a single grade for an ETH/ELA pair, so it lands on the theory half and the lab
     * half has credits but no grade. Crediting the theory row alone would report 3 credits earned
     * for a 5-credit course, which is the under-count this guards.
     */
    @Test
    fun anEmbeddedPairIsCountedOnceAtItsFullCredit() {
        val s = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "CH20252605" to Semester(
                        id = "CH20252605",
                        courses = mapOf(
                            "BACSE106" to Course(
                                code = "BACSE106",
                                title = "Operating Systems",
                                credits = "3",
                                grade = Grade(letter = "B+"),
                            ),
                            "BACSE106(L)" to Course(
                                code = "BACSE106(L)",
                                title = "Operating Systems",
                                credits = "2",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(setOf("BACSE106"), completedCourseCodes(s))
        assertEquals(5f, earnedCreditsFromGrades(s), "3 theory + 2 lab, counted once")
    }

    @Test
    fun anEmbeddedPairIsNotMatchedAgainstTheBasketTwice() {
        val s = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "CH20252605" to Semester(
                        id = "CH20252605",
                        courses = mapOf(
                            "BACSE106" to Course(code = "BACSE106", credits = "3", grade = Grade(letter = "B+")),
                            "BACSE106(L)" to Course(code = "BACSE106(L)", credits = "2"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            setOf("BACSE106"),
            ongoingCourseCodes(
                listOf(
                    CourseAttendance("BACSE106", "Operating Systems", "Embedded Theory", "", null, "", "3", null, 26, 28, "93", emptyList()),
                    CourseAttendance("BACSE106(L)", "Operating Systems", "Embedded Lab", "", null, "", "2", null, 16, 18, "89", emptyList()),
                )
            ),
        )
    }

    // ── totals ──────────────────────────────────────────────────────────────

    @Test
    fun thePublishedTotalIsUsedBeforeTheOneSixtyFallback() {
        // The screen used to hard-code 160 even when the curriculum response published a total.
        val totals = curriculumTotals(CurriculumRes(totalCredits = 165), earnedCredits = 20f, ongoingCredits = 0f)
        assertEquals(165f, totals.required)
    }

    @Test
    fun anAbsentOrZeroTotalFallsBackToOneSixty() {
        assertEquals(160f, curriculumTotals(null, 10f, 0f).required)
        assertEquals(160f, curriculumTotals(CurriculumRes(totalCredits = 0), 10f, 0f).required)
    }

    @Test
    fun thePublishedTotalIsTheRequirementAndIsNeverUsedAsEarned() {
        // The screen used to fall back to `totalCredits` for earned when no course had a grade,
        // which reported a first-year student as 160 of 160 credits earned.
        val totals = curriculumTotals(CurriculumRes(totalCredits = 160), earnedCredits = 0f, ongoingCredits = 0f)
        assertEquals(160f, totals.required)
        assertEquals(0f, totals.earned)
        assertEquals("7 sem", totals.expectedLabel) // ceil(160 / 24)
    }

    @Test
    fun earnedComesFromTheGradesAlone() {
        assertEquals(20f, curriculumTotals(CurriculumRes(totalCredits = 160), 20f, 0f).earned)
    }

    @Test
    fun theOngoingArcNeverOverlapsTheEarnedArc() {
        val totals = curriculumTotals(CurriculumRes(totalCredits = 100), earnedCredits = 90f, ongoingCredits = 30f)
        assertEquals(0.9f, totals.earnedPct, 1e-6f)
        assertEquals(0.1f, totals.ongoingPct, 1e-6f, "capped at what is left, not at 1")
    }

    @Test
    fun remainingIsNeverNegativeAndDrivesTheSemesterEstimate() {
        val plenty = curriculumTotals(null, earnedCredits = 10f, ongoingCredits = 5f)
        assertEquals(145f, plenty.remaining)
        assertEquals("7 sem", plenty.expectedLabel) // ceil(145 / 24) = 7

        val done = curriculumTotals(null, earnedCredits = 160f, ongoingCredits = 0f)
        assertEquals(0f, done.remaining)
        assertEquals("Ready", done.expectedLabel)
    }
}