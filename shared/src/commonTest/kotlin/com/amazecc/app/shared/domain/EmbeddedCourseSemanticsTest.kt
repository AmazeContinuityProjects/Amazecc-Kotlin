package com.amazecc.app.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-187 — pure embedded-course semantics, with the user's correction applied.
 *
 * The load-bearing assertions are the ones the old "blend everything" reading got wrong:
 *  - attendance stays TWO entries with their own percentages and there is no aggregate field;
 *  - marks use each half's OWN credits, for arbitrary pairs (not a hardcoded 3+1);
 *  - grades stay TWO entries, and `combinedGrade` is null unless a real combined letter exists;
 *  - a lone half is an honest half, not a fabricated pair.
 */
class EmbeddedCourseSemanticsTest {

    private fun half(
        code: String = "BACSE106",
        half: EmbeddedHalf,
        type: String = "",
        credits: Double = 0.0,
        attendancePercent: Double? = null,
        earnedMarks: Double? = null,
        releasedMarks: Double? = null,
        grade: String? = null,
        title: String = "",
    ) = EmbeddedEntry(
        code = code,
        half = half,
        type = type,
        credits = credits,
        attendancePercent = attendancePercent,
        earnedMarks = earnedMarks,
        releasedMarks = releasedMarks,
        grade = grade,
        title = title,
    )

    private fun pair(
        theoryCredits: Double,
        labCredits: Double,
        theoryEarned: Double = 87.5,
        labEarned: Double = 93.0,
        theoryReleased: Double = 87.5,
        labReleased: Double = 93.0,
    ) = listOf(
        half(
            code = "BACSE106",
            half = EmbeddedHalf.THEORY,
            credits = theoryCredits,
            earnedMarks = theoryEarned,
            releasedMarks = theoryReleased,
        ),
        half(
            code = "BACSE106(L)",
            half = EmbeddedHalf.LAB,
            credits = labCredits,
            earnedMarks = labEarned,
            releasedMarks = labReleased,
        ),
    )

    // ── marks: each half's own credits, no assumed ratio ──────────────────────

    @Test
    fun `earned and released blends use 3 plus 1 credits exactly`() {
        val group = EmbeddedCourseSemantics.group(pair(3.0, 1.0)).single()

        // (87.5 * 3 + 93.0 * 1) / 4 = 355.5 / 4 = 88.875 — NOT rounded to 88.88.
        assertEquals(88.875, checkNotNull(group.earnedBlend))
        assertEquals(88.875, checkNotNull(group.releasedBlend))
        assertEquals(4.0, group.totalCredits)
    }

    @Test
    fun `a 2 plus 2 pair changes the blend instead of reusing the 3 plus 1 ratio`() {
        val group = EmbeddedCourseSemantics.group(pair(2.0, 2.0)).single()

        assertEquals(90.25, checkNotNull(group.earnedBlend))
        assertEquals(90.25, checkNotNull(group.releasedBlend))
        assertEquals(4.0, group.totalCredits)
    }

    @Test
    fun `a 2 plus 1 pair and a 1 plus 1 pair prove credits are read from each half`() {
        val twoPlusOne = EmbeddedCourseSemantics.group(pair(2.0, 1.0)).single()
        // (87.5 * 2 + 93.0) / 3 = 268 / 3
        assertTrue(abs((twoPlusOne.earnedBlend ?: -1.0) - 268.0 / 3.0) < 1e-9)
        assertEquals(3.0, twoPlusOne.totalCredits)

        val onePlusOne = EmbeddedCourseSemantics.group(pair(1.0, 1.0)).single()
        assertEquals(90.25, checkNotNull(onePlusOne.earnedBlend))
        assertEquals(2.0, onePlusOne.totalCredits)
    }

    @Test
    fun `missing credits return null blends and never assume a 1 to 1 ratio`() {
        val noLabCredits = EmbeddedCourseSemantics.group(pair(3.0, 0.0)).single()
        assertNull(noLabCredits.earnedBlend)
        assertNull(noLabCredits.releasedBlend)
        // The total still reports the credits the halves actually published.
        assertEquals(3.0, noLabCredits.totalCredits)

        val neither = EmbeddedCourseSemantics.group(pair(0.0, 0.0)).single()
        assertNull(neither.earnedBlend)
        assertNull(neither.releasedBlend)
        assertEquals(0.0, neither.totalCredits)
    }

    @Test
    fun `creditWeighted rejects missing credits zero totals and empty selectors`() {
        val entries = listOf(
            half(code = "A", half = EmbeddedHalf.THEORY, credits = 3.0, earnedMarks = 80.0),
            half(code = "B", half = EmbeddedHalf.LAB, credits = 0.0, earnedMarks = 90.0),
        )
        assertNull(EmbeddedCourseSemantics.creditWeighted(entries) { it.earnedMarks })
        assertNull(EmbeddedCourseSemantics.creditWeighted(entries) { it.releasedMarks })
        assertNull(
            EmbeddedCourseSemantics.creditWeighted(
                listOf(
                    half(code = "A", half = EmbeddedHalf.THEORY, credits = 0.0, earnedMarks = 80.0),
                    half(code = "B", half = EmbeddedHalf.LAB, credits = 0.0, earnedMarks = 90.0),
                ),
            ) { it.earnedMarks },
        )
    }

    @Test
    fun `non-finite scores and credits are rejected instead of blended`() {
        val nonFiniteScores = listOf(
            half(code = "A", half = EmbeddedHalf.THEORY, credits = 3.0, earnedMarks = Double.NaN),
            half(
                code = "B",
                half = EmbeddedHalf.LAB,
                credits = 1.0,
                earnedMarks = Double.POSITIVE_INFINITY,
            ),
        )
        assertNull(EmbeddedCourseSemantics.creditWeighted(nonFiniteScores) { it.earnedMarks })

        val nonFiniteCredits = listOf(
            half(
                code = "A",
                half = EmbeddedHalf.THEORY,
                credits = Double.NaN,
                earnedMarks = 80.0,
            ),
            half(
                code = "B",
                half = EmbeddedHalf.LAB,
                credits = Double.NEGATIVE_INFINITY,
                earnedMarks = 90.0,
            ),
        )
        assertNull(EmbeddedCourseSemantics.creditWeighted(nonFiniteCredits) { it.earnedMarks })
    }

    @Test
    fun `a non-finite half score never drops the other half from a group blend`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(
                    code = "A",
                    half = EmbeddedHalf.THEORY,
                    credits = 3.0,
                    earnedMarks = Double.NaN,
                ),
                half(
                    code = "A",
                    half = EmbeddedHalf.LAB,
                    credits = 1.0,
                    earnedMarks = 90.0,
                ),
            ),
        ).single()

        // Filtering NaN in creditWeighted alone would leave one usable row and report 90 as a
        // "blend". The pair guard must reject that instead.
        assertNull(group.earnedBlend)
        assertEquals(4.0, group.totalCredits)
    }

    @Test
    fun `non-finite credits contribute nothing to totalCredits`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(code = "A", half = EmbeddedHalf.THEORY, credits = Double.NaN),
                half(
                    code = "A",
                    half = EmbeddedHalf.LAB,
                    credits = Double.POSITIVE_INFINITY,
                ),
            ),
        ).single()

        assertEquals(0.0, group.totalCredits)
        assertNull(group.earnedBlend)
    }

    // ── attendance: two entries, no aggregate ─────────────────────────────────

    @Test
    fun `attendance keeps two distinct percentages and no aggregate field`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(
                    half = EmbeddedHalf.THEORY,
                    credits = 3.0,
                    attendancePercent = 92.5,
                    earnedMarks = 87.5,
                ),
                half(
                    half = EmbeddedHalf.LAB,
                    credits = 1.0,
                    attendancePercent = 74.0,
                    earnedMarks = 93.0,
                ),
            ),
        ).single()

        assertEquals(2, group.attendanceEntries.size)
        assertEquals(
            listOf(92.5, 74.0),
            group.attendanceEntries.map { it.attendancePercent },
        )
        // EmbeddedGroup has no attendance percentage of its own: the two entries are the only
        // attendance surface, so no mean/blend can creep in through an aggregate property.
        assertEquals(92.5, group.theory?.attendancePercent)
        assertEquals(74.0, group.lab?.attendancePercent)
    }

    // ── grades: two entries, only a real combined letter ──────────────────────

    @Test
    fun `grades keep two entries and combinedGrade is null without a real combined grade`() {
        val noGradeData = EmbeddedCourseSemantics.group(
            listOf(
                half(half = EmbeddedHalf.THEORY, credits = 3.0),
                half(half = EmbeddedHalf.LAB, credits = 1.0),
            ),
        ).single()
        assertEquals(2, noGradeData.gradeEntries.size)
        assertNull(noGradeData.combinedGrade)

        val differentGradesNoBlend = EmbeddedCourseSemantics.group(
            listOf(
                half(half = EmbeddedHalf.THEORY, grade = "A"),
                half(half = EmbeddedHalf.LAB, grade = "B"),
            ),
        ).single()
        assertEquals(listOf("A", "B"), differentGradesNoBlend.gradeEntries.map { it.grade })
        assertNull(differentGradesNoBlend.combinedGrade)

        val ungraded = EmbeddedCourseSemantics.group(
            listOf(
                half(half = EmbeddedHalf.THEORY, grade = "N"),
                half(half = EmbeddedHalf.LAB, grade = "N"),
            ),
        ).single()
        assertNull(ungraded.combinedGrade)
    }

    @Test
    fun `a shared published grade is kept as combinedGrade alongside both half grades`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(half = EmbeddedHalf.THEORY, credits = 3.0, grade = "A"),
                half(half = EmbeddedHalf.LAB, credits = 1.0, grade = "A"),
            ),
        ).single()

        assertEquals(listOf("A", "A"), group.gradeEntries.map { it.grade })
        assertEquals("A", group.combinedGrade)
    }

    @Test
    fun `a released blend with no shared published grade stays a number and yields no letter`() {
        val group = EmbeddedCourseSemantics.group(
            pair(
                theoryCredits = 3.0,
                labCredits = 1.0,
                theoryReleased = 90.0,
                labReleased = 70.0,
            ),
        ).single()

        // (90 * 3 + 70 * 1) / 4 = 85. An embedded course receives ONE curved letter from the
        // college, not one read off an absolute ladder, so no letter may be inferred here.
        assertEquals(85.0, checkNotNull(group.releasedBlend))
        assertNull(group.combinedGrade)
    }

    @Test
    fun `the same real letter published by both halves is the only combined grade`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(
                    half = EmbeddedHalf.THEORY,
                    credits = 3.0,
                    releasedMarks = 90.0,
                    grade = "B+", // not a graded letter on the ladder -> not a combined grade
                ),
                half(
                    half = EmbeddedHalf.LAB,
                    credits = 1.0,
                    releasedMarks = 70.0,
                    grade = "B+",
                ),
            ),
        ).single()
        assertEquals(85.0, checkNotNull(group.releasedBlend))
        assertNull(group.combinedGrade)

        val real = EmbeddedCourseSemantics.group(
            listOf(
                half(half = EmbeddedHalf.THEORY, credits = 3.0, grade = "A"),
                half(half = EmbeddedHalf.LAB, credits = 1.0, grade = "a"),
            ),
        ).single()
        assertEquals("A", real.combinedGrade)
    }

    // ── grouping, labels and the single isEmbedded rule ───────────────────────

    @Test
    fun `base-code grouping links BACSE106 and BACSE106 L as one group`() {
        val group = EmbeddedCourseSemantics.group(pair(3.0, 1.0)).single()

        assertEquals("BACSE106", group.baseCode)
        assertNotNull(group.theory)
        assertNotNull(group.lab)
        assertEquals("Embedded Theory & Lab", group.label)
    }

    @Test
    fun `input order is preserved and out-of-order halves do not crash`() {
        val groups = EmbeddedCourseSemantics.group(
            listOf(
                half(code = "AAA101", half = EmbeddedHalf.LAB, credits = 1.0),
                half(code = "BBB202", half = EmbeddedHalf.THEORY, credits = 3.0),
                half(code = "AAA101(L)", half = EmbeddedHalf.THEORY, credits = 3.0),
                half(code = "AAA101", half = EmbeddedHalf.LAB, credits = 1.0),
            ),
        )

        assertEquals(listOf("AAA101", "BBB202"), groups.map { it.baseCode })
        val first = groups.first()
        assertNotNull(first.theory)
        assertNotNull(first.lab)
        assertEquals("Embedded Theory & Lab", first.label)
    }

    @Test
    fun `a lone lab is an Embedded Lab group with honest nulls`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(
                half(
                    code = "BACSE106(L)",
                    half = EmbeddedHalf.LAB,
                    type = "Embedded Lab",
                    credits = 1.0,
                    attendancePercent = 80.0,
                    earnedMarks = 72.0,
                    grade = "B",
                ),
            ),
        ).single()

        assertEquals("BACSE106", group.baseCode)
        assertEquals("Embedded Lab", group.label)
        assertNull(group.theory)
        assertNotNull(group.lab)
        assertEquals(1, group.attendanceEntries.size)
        assertEquals(1, group.gradeEntries.size)
        assertNull(group.earnedBlend)
        assertNull(group.releasedBlend)
        assertNull(group.combinedGrade)
        assertEquals(1.0, group.totalCredits)
    }

    @Test
    fun `a lone theory is an Embedded Theory group with honest nulls`() {
        val group = EmbeddedCourseSemantics.group(
            listOf(half(half = EmbeddedHalf.THEORY, type = "Embedded Theory", credits = 3.0)),
        ).single()

        assertEquals("Embedded Theory", group.label)
        assertNotNull(group.theory)
        assertNull(group.lab)
        assertNull(group.earnedBlend)
        assertNull(group.combinedGrade)
    }

    @Test
    fun `empty input produces no groups`() {
        assertTrue(EmbeddedCourseSemantics.group(emptyList()).isEmpty())
    }

    @Test
    fun `labelFor returns web-exact strings including ETH plus ELA and ETL`() {
        assertEquals("Embedded Theory & Lab", EmbeddedCourseSemantics.labelFor("ETH+ELA"))
        assertEquals("Embedded Theory & Lab", EmbeddedCourseSemantics.labelFor("ETL"))
        assertEquals("Embedded Theory & Lab", EmbeddedCourseSemantics.labelFor("Embedded Theory and Lab"))
        assertEquals("Embedded Theory & Lab", EmbeddedCourseSemantics.labelFor("Embedded Theory & Lab"))
        assertEquals("Embedded Lab", EmbeddedCourseSemantics.labelFor("Embedded Lab"))
        assertEquals("Embedded Theory", EmbeddedCourseSemantics.labelFor("Embedded Theory"))
    }

    @Test
    fun `labelFor does not claim an embedded label when the type carries no signal`() {
        assertEquals("", EmbeddedCourseSemantics.labelFor(null))
        assertEquals("", EmbeddedCourseSemantics.labelFor(""))
        assertEquals("", EmbeddedCourseSemantics.labelFor("   "))
        assertEquals("", EmbeddedCourseSemantics.labelFor("Unclassified"))

        // The neutral label agrees with isEmbedded, which is false for the same input.
        assertFalse(EmbeddedCourseSemantics.isEmbedded(null, false, false))
        assertFalse(EmbeddedCourseSemantics.isEmbedded("", false, false))
    }

    @Test
    fun `isEmbedded follows the single label-or-payload rule`() {
        // The label alone is enough.
        assertTrue(EmbeddedCourseSemantics.isEmbedded("Embedded Theory", false, false))
        assertTrue(EmbeddedCourseSemantics.isEmbedded("Embedded Lab", false, false))
        assertTrue(EmbeddedCourseSemantics.isEmbedded("EMBEDDED THEORY AND LAB", false, false))
        // A plain lab-only / theory-only label is not embedded on its own.
        assertFalse(EmbeddedCourseSemantics.isEmbedded("Lab Only", false, false))
        assertFalse(EmbeddedCourseSemantics.isEmbedded("Theory Only", false, false))
        // Both payloads are enough, even behind a non-embedded label.
        assertTrue(EmbeddedCourseSemantics.isEmbedded("", true, true))
        assertTrue(EmbeddedCourseSemantics.isEmbedded("Lab Only", true, true))
        // One payload alone is not a pair.
        assertFalse(EmbeddedCourseSemantics.isEmbedded("", true, false))
        assertFalse(EmbeddedCourseSemantics.isEmbedded("", false, true))
        assertFalse(EmbeddedCourseSemantics.isEmbedded(null, false, false))
    }
}
