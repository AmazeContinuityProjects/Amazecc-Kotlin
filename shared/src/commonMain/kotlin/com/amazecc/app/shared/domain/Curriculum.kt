package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes

/**
 * Port of Node's `src/lib/curriculum.ts`, plus the four derivations `CurriculumPage` wraps around
 * it (ongoing credits per category, the totals cascade, and the completed / in-progress course
 * sets). These lived inline in `CurriculumScreen` until now, where none of them could be tested.
 *
 * Two shapes in Node's source have no counterpart here, and the divergence is deliberate:
 *
 * 1. **The flat `curriculum` list.** Node's *primary* source for a category row is that list,
 *    which `CurriculumPage.findCurriculum` digs out of eleven candidate spots across the CGPA and
 *    marks payloads. Kotlin parses no flat curriculum array anywhere - `CGPAResult` carries only
 *    `creditsEarned` and `cgpa` - so Kotlin is permanently on Node's *fallback* path: the
 *    `categories` + `details` array this app already fetches in two stages
 *    (`VtopDataSource.fetchCurriculum`). The primary branch is kept so the shape survives in case a
 *    flat list is ever parsed.
 * 2. **The CGPA steps of the totals cascade**, which need `cgpa.creditsRequired` and
 *    `cgpa.nonGradedRequirement`. See [curriculumTotals].
 */

// ── Node's interfaces ─────────────────────────────────────────────────────────

/** A row of the flat all-grades curriculum list. See the file note: Kotlin parses none of these. */
data class CurriculumRow(
    val basketTitle: String = "",
    val creditsRequired: String = "",
    val creditsEarned: String = "",
)

/** One row of the curriculum page: a credit category and how much of it is done. */
data class CategoryRow(
    val key: String,
    val code: String? = null,
    val title: String,
    val earned: Float,
    /** Never 0 - a zero requirement would make the percentage meaningless. */
    val required: Float,
    val ongoing: Float,
    val baskets: List<CurriculumBasket> = emptyList(),
)

/** Where a course sits in the programme. Node's `CourseStatus`, as a tri-state. */
enum class CourseStatus { COMPLETED, IN_PROGRESS, REMAINING }

// ── Constants and coercion ────────────────────────────────────────────────────

/**
 * Baskets Node lifts out of the main list and lists separately.
 *
 * Matched case-insensitively as substrings of the row title, exactly as `curriculum.ts.isSpecial`.
 */
val SPECIAL_BASKETS: List<String> = listOf(
    // The VTOP snapshot spells this "Extracurricular Activities" (curriculum.html:436-453).
    // The two older spellings are kept because normaliseOngoingCategory emits one and older
    // cached snapshots carry the other; matching is a case-insensitive substring on all three.
    "Extracurricular Activities",
    "Extra extracurricular activities",
    "Extra curricular activities",
    "HSM Elective",
    "Foreign Language",
)

/** Node's fallback when the programme total is unknown. BTech is 160 credits. */
const val DEFAULT_TOTAL_CREDITS: Float = 160f

/** Credits per semester, used by the "N sem" estimate. */
const val CREDITS_PER_SEMESTER: Int = 24

/**
 * Node's `num`: read a credit field that may be null, blank or junk.
 *
 * One deliberate difference: JavaScript's `parseFloat("18abc")` is `18`, while [String.toFloatOrNull]
 * rejects the whole string and returns the fallback. Node's own tests only pin `"18.5"`,
 * `undefined` and `"abc"`, which agree.
 */
fun curriculumNum(raw: String?, fallback: Float = 0f): Float =
    raw?.trim()?.toFloatOrNull() ?: fallback

/** True for the synthetic summary row, which is a total and not a category. */
fun isTotalCreditsRow(title: String): Boolean = title.lowercase().contains("total credits")

fun isSpecialBasket(title: String): Boolean {
    val lower = title.lowercase()
    return SPECIAL_BASKETS.any { lower.contains(it.lowercase()) }
}

/** Drops the synthetic "Total Credits" row and splits out the elective baskets. */
fun splitCurriculumRows(
    rows: List<CurriculumRow>,
): Pair<List<CurriculumRow>, List<CurriculumRow>> {
    val withoutTotal = rows.filterNot { isTotalCreditsRow(it.basketTitle) }
    return withoutTotal.filterNot { isSpecialBasket(it.basketTitle) } to
        withoutTotal.filter { isSpecialBasket(it.basketTitle) }
}

// ── Baskets ───────────────────────────────────────────────────────────────────

/**
 * Category name to its baskets.
 *
 * Keyed by `CategoryDetail.name` and not by code, because a name is the only key a flat curriculum
 * row carries - `curriculum.test.ts` asserts the join is by name.
 */
fun buildBasketMap(details: List<CategoryDetail>): Map<String, List<CurriculumBasket>> {
    if (details.isEmpty()) return emptyMap()
    val map = LinkedHashMap<String, MutableList<CurriculumBasket>>()
    for (cat in details) {
        map.getOrPut(cat.name) { mutableListOf() }.addAll(cat.baskets)
    }
    return map
}

// ── Category rows ─────────────────────────────────────────────────────────────

/**
 * One row per credit category, from whichever source has data.
 *
 * The flat list wins when it is non-empty (Node's primary source); otherwise the `categories`
 * array is used and its baskets are resolved against [details] by code. `required` is floored at 1
 * on both paths so the page can never divide by zero.
 */
fun buildCategoryRows(
    curriculum: List<CurriculumRow> = emptyList(),
    categories: List<CurriculumCategory> = emptyList(),
    details: List<CategoryDetail> = emptyList(),
    ongoingByCategory: Map<String, Float> = emptyMap(),
): List<CategoryRow> {
    val basketMap = buildBasketMap(details)
    val (main, _) = splitCurriculumRows(curriculum)

    if (main.isNotEmpty()) {
        return main.map { row ->
            val required = curriculumNum(row.creditsRequired)
            CategoryRow(
                key = "cur:${row.basketTitle}",
                title = row.basketTitle,
                earned = curriculumNum(row.creditsEarned),
                required = if (required > 0f) required else 1f,
                ongoing = ongoingByCategory[row.basketTitle] ?: 0f,
                baskets = basketMap[row.basketTitle].orEmpty(),
            )
        }
    }

    return categories.map { cat ->
        val detail = details.firstOrNull { it.code == cat.code }
        val maxCredits = curriculumNum(cat.maxCredits.toString())
        CategoryRow(
            key = "cat:${cat.code}",
            code = cat.code,
            title = cat.name,
            earned = curriculumNum(cat.credits.toString()),
            required = if (maxCredits > 0f) maxCredits else 1f,
            ongoing = ongoingByCategory[cat.name] ?: 0f,
            baskets = detail?.baskets.orEmpty(),
        )
    }
}

// ── Ongoing credits, keyed by category ────────────────────────────────────────

/**
 * The parent basket that VIT's three HSSM sub-baskets roll up into.
 *
 * VIT splits Humanities/Social Sciences/Management into `(LANGUAGE Basket)`, `(GENERAL Basket)`
 * and `(EXTRA CURRICULAR Basket)` categories, while the curriculum page lists them as `Foreign
 * Language`, `HSM Elective` and `Extra extracurricular activities`. Without this mapping those
 * credits land in a category the page has no row for and vanish from the progress ring.
 */
const val HSSM_BASKET = "Foundation Core - Humanities, Social Sciences and Management"

/** Where `Extra extracurricular activities` credits are also counted. */
const val NGCR_REQUIREMENT = "Non-graded Core Requirement"

/** Used when a course carries no category at all. */
const val UNCATEGORIZED = "Uncategorized"

private const val HSSM_LANGUAGE = "$HSSM_BASKET (LANGUAGE Basket)"
private const val HSSM_GENERAL = "$HSSM_BASKET (GENERAL Basket)"
private const val HSSM_EXTRA = "$HSSM_BASKET (EXTRA CURRICULAR Basket)"

/**
 * Rewrites VTOP's HSSM sub-basket names onto the curriculum page's names.
 *
 * Public because it is the part worth testing: the three spellings are long and easy to get wrong
 * in a way that silently loses credits.
 */
fun normaliseOngoingCategory(category: String?): String = when (category) {
    HSSM_LANGUAGE -> "Foreign Language"
    HSSM_GENERAL -> "HSM Elective"
    HSSM_EXTRA -> "Extra extracurricular activities"
    null, "" -> UNCATEGORIZED
    else -> category
}

/**
 * Ongoing credits per category name, folding the HSSM sub-baskets onto their parent.
 *
 * A course's credits are counted under **both** its own category and, when it is one of the three
 * HSSM sub-baskets, its parent. That is Node's behaviour and it is what lets a row keyed
 * `Foundation Core - Humanities, Social Sciences and Management` show the same credits as the
 * `Foreign Language` row beside it.
 *
 * Because of that double entry, the sum of this map's values is **not** the number of ongoing
 * credits - use [totalOngoingCredits] for that. Node sums the map's values
 * (`CurriculumPage.tsx:563-566`), which counts every HSSM sub-basket twice; recorded as a
 * divergence in `docs/sep-30-2026/screen-transplant-plan.md`.
 */
fun ongoingCreditsByCategory(courses: List<CourseAttendance>): Map<String, Float> {
    if (courses.isEmpty()) return emptyMap()
    val acc = LinkedHashMap<String, Float>()
    fun add(key: String, credits: Float) {
        if (credits == 0f) return
        acc[key] = (acc[key] ?: 0f) + credits
    }

    for (course in courses) {
        val credits = curriculumNum(course.credits)
        val category = normaliseOngoingCategory(course.category)
        add(category, credits)
        if (category == "Foreign Language" || category == "HSM Elective") add(HSSM_BASKET, credits)
        if (category == "Extra extracurricular activities") add(NGCR_REQUIREMENT, credits)
    }
    return acc
}

/**
 * Ongoing credits as one number: the plain sum, with no double entry.
 *
 * See [ongoingCreditsByCategory] for why that is not the sum of the map's values.
 */
fun totalOngoingCredits(courses: List<CourseAttendance>): Float =
    courses.sumOf { curriculumNum(it.credits).toDouble() }.toFloat()

// ── Passed / in-progress courses ──────────────────────────────────────────────

/** Grades that do not count as progress: a fail, and the `N` VTOP shows for a not-taken course. */
private val NON_PASSING_GRADES = listOf("F", "N", "")

/**
 * One entry per course that has a passing grade, across every semester.
 *
 * An embedded course is two rows - `BACSE106` and `BACSE106(L)` - and VTOP reports **one** grade
 * for the pair, so only one half carries it. Counting halves would double-count the course in
 * [earnedCreditsFromGrades] and in [completedCourseCodes]; crediting the theory half alone would
 * drop the lab's credits, so the entry keeps both halves' credits and the one grade that exists.
 *
 * The halves are keyed by [EmbeddedLinkCode.baseCode], the one canonical normalizer (it also folds
 * `BCSE101L` onto `BCSE101`, which the old `substringBefore("(")` split could not).
 */
private data class PassedCourse(val baseCode: String, val credits: Double)

private fun passingCourses(snapshot: DomainSnapshot): List<PassedCourse> =
    snapshot.academics.semesters.values.flatMap { semester ->
        semester.courses.values
            .groupBy { EmbeddedLinkCode.baseCode(it.code) }
            .mapNotNull { (baseCode, halves) ->
                val passed = halves.any { half ->
                    val grade = half.grade?.letter
                    !grade.isNullOrBlank() && grade !in NON_PASSING_GRADES
                }
                if (!passed) return@mapNotNull null
                PassedCourse(baseCode, halves.sumOf { curriculumNum(it.credits).toDouble() })
            }
    }

/** Credits of every passed course, with an embedded pair counted once at its full weight. */
fun earnedCreditsFromGrades(snapshot: DomainSnapshot): Float =
    passingCourses(snapshot).sumOf { it.credits }.toFloat()

/**
 * Codes of passed courses, upper-cased.
 *
 * Node additionally scrapes a code out of the basket title with `/([A-Z]{3,4}\d{3,4})/`, because
 * some effective-grade rows carry only a title. Kotlin has one code and one grade per course, so
 * there is nothing to scrape.
 */
fun completedCourseCodes(snapshot: DomainSnapshot): Set<String> =
    passingCourses(snapshot).mapTo(LinkedHashSet()) { it.baseCode }

/**
 * Codes of courses the student has attendance records for, upper-cased.
 *
 * Node keys this off the whole attendance list rather than one semester, so a course from an
 * earlier term still reads as in progress. An embedded pair contributes one code, not one per half,
 * so it matches against the basket exactly once. [EmbeddedLinkCode.baseCode] is the same rule the
 * graded side uses, so `[BACSE106, BACSE106(L)]` becomes exactly one `BACSE106`.
 */
fun ongoingCourseCodes(courses: List<CourseAttendance>): Set<String> =
    courses.mapTo(LinkedHashSet()) { EmbeddedLinkCode.baseCode(it.courseCode) }

/**
 * Where a curriculum basket item sits, matched case-insensitively on both sides.
 *
 * Case matters here: VTOP is not consistent about it, and this screen used to compare the raw
 * code, which silently dropped any basket item whose code differed from the graded course only in
 * case.
 */
fun courseStatus(code: String?, completed: Set<String>, ongoing: Set<String>): CourseStatus {
    val c = code?.trim()?.uppercase().orEmpty()
    if (c.isEmpty()) return CourseStatus.REMAINING
    if (c in completed) return CourseStatus.COMPLETED
    if (c in ongoing) return CourseStatus.IN_PROGRESS
    return CourseStatus.REMAINING
}

// ── Totals ────────────────────────────────────────────────────────────────────

/** The headline numbers the curriculum hero card shows. */
data class CurriculumTotals(
    val earned: Float,
    val required: Float,
    val ongoing: Float,
) {
    val remaining: Float get() = (required - earned - ongoing).coerceAtLeast(0f)

    val earnedPct: Float get() = if (required > 0f) (earned / required).coerceAtMost(1f) else 0f

    /**
     * The ongoing arc is drawn inside what is left, so it is capped at `1 - earnedPct` rather than
     * at 1 - otherwise the two arcs overlap on a nearly-finished programme.
     */
    val ongoingPct: Float get() = if (required > 0f) (ongoing / required).coerceAtMost(1f - earnedPct) else 0f

    val expectedLabel: String
        get() = if (remaining <= 0f) {
            "Ready"
        } else {
            val semesters = ((remaining.toInt() + CREDITS_PER_SEMESTER - 1) / CREDITS_PER_SEMESTER)
            "${semesters.coerceAtLeast(1)} sem"
        }
}

/**
 * The totals cascade.
 *
 * Node's order for the requirement is: the flat list's own "Total Credits" row, else the
 * `/curriculum` response's total, else 160. Only the last two are reachable here - see the file
 * note - so the published total is honoured before the 160 fallback, which is what the screen used
 * to skip and hard-code.
 *
 * For earned, Node takes the same row, else CGPA, else the effective-grade rows. The CGPA step is
 * unreachable because this app does not parse the CGPA payload's credit counts, so the caller
 * supplies [earnedCredits] summed from real grades instead.
 *
 * Note that the published total is the programme's **requirement**, so it must never be used as
 * earned. The screen used to do exactly that when no course had a grade yet, which reported a
 * first-year student as 160 of 160 credits earned.
 */
fun curriculumTotals(
    curriculum: CurriculumRes?,
    earnedCredits: Float,
    ongoingCredits: Float,
): CurriculumTotals {
    val published = curriculumNum(curriculum?.totalCredits?.toString())
    return CurriculumTotals(
        earned = earnedCredits.coerceAtLeast(0f),
        required = if (published > 0f) published else DEFAULT_TOTAL_CREDITS,
        ongoing = ongoingCredits,
    )
}