package com.amazecc.app.shared.domain

import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EComputations
import com.amazecc.app.shared.domain.overhaul.M3ECourseAttendanceCardState
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AcademicMerge
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.state.StoredMarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end linking of an embedded course reported in both spellings:
 * the API's `(T)`/`(L)` codes and the scraper's bare code + `type` rows.
 */
class EmbeddedLinkMergeTest {

    private fun theoryRow(code: String, type: String) = Course(
        code = code,
        title = "Database Systems",
        type = type,
        credits = "3",
        slots = listOf("C1", "TC1"),
        classId = "TH-1",
        attendance = Attendance(24, 27, "88.89"),
        marks = Marks(
            classNbr = "TH-1",
            assessments = listOf(Assessment(name = "CIA-1", weightage = "40", maxMark = "50")),
            total = 40.0,
            maxTotal = 100.0,
        ),
    )

    private fun labRow(code: String, type: String) = Course(
        code = code,
        title = "Database Systems Lab",
        type = type,
        credits = "1",
        slots = listOf("L31", "L32"),
        classId = "LAB-1",
        attendance = Attendance(18, 20, "90"),
        marks = Marks(
            classNbr = "LAB-1",
            assessments = listOf(Assessment(name = "Lab CIA", weightage = "90", maxMark = "100")),
            total = 90.0,
            maxTotal = 100.0,
        ),
    )

    /** A half with caller-chosen credits/score/printed percentage, for the blend/attendance rules. */
    private fun marksHalf(code: String, type: String, credits: String?, total: Double?, percentage: String) = Course(
        code = code,
        title = "Database Systems",
        type = type,
        credits = credits,
        attendance = Attendance(24, 27, percentage),
        marks = Marks(classNbr = code, total = total, maxTotal = 100.0),
    )

    /** Two endpoints, four rows, one logical course. */
    private fun fourRows() = listOf(
        theoryRow("BCSE203E(T)", "Embedded Theory"),   // API style
        theoryRow("BCSE203E", "Embedded Theory"),       // scraper style (same half)
        labRow("BCSE203E(L)", "Embedded Lab"),          // API style
        labRow("BCSE203E", "Embedded Lab"),             // scraper style (same half)
    )

    /**
     * A semester map cannot hold two rows under the same bare code, so the persisted form of the
     * scenario keeps one scraper row (the theory duplicate) and the two suffixed API rows.
     */
    private fun threeRows() = listOf(
        theoryRow("BCSE203E(T)", "Embedded Theory"),
        theoryRow("BCSE203E", "Embedded Theory"),
        labRow("BCSE203E(L)", "Embedded Lab"),
    )

    private fun snapshot(rows: List<Course>) = DomainSnapshot(
        academics = Academics(
            semesters = mapOf("S1" to Semester(id = "S1", courses = rows.associateBy { it.code })),
        ),
    )

    @Test
    fun `mixed suffix and type rows collapse to one logical course`() {
        val merged = EmbeddedLinkMerge.mergeCourses(fourRows())
        assertEquals(1, merged.size)
        val course = merged.single()
        assertEquals("BCSE203E", course.code)
        assertEquals("BCSE203E", course.linkedBaseCode)
        assertTrue(course.isEmbeddedMerged)
        assertEquals("Database Systems", course.title, "the theory half names the course")
        assertEquals("Embedded Theory and Lab", course.type)
        assertEquals(4, course.halves.size, "raw halves stay available to a detail screen")
    }

    @Test
    fun `credits slots attendance and marks combine across the halves`() {
        val course = EmbeddedLinkMerge.mergeCourses(fourRows()).single()
        assertEquals("4", course.credits, "3 theory + 1 lab credits")
        assertEquals(listOf("C1", "TC1", "L31", "L32"), course.slots)
        assertEquals(42, course.attendance!!.attended, "24 + 18")
        assertEquals(47, course.attendance!!.total, "27 + 20")
        assertEquals(2, course.marks!!.assessments.size)
        assertEquals(listOf("ETH"), course.marks!!.assessments.filter { it.component == "ETH" }
            .map { it.component })
        // (40*3 + 90*1) / 4 = 52.5
        assertEquals(52.5, course.marks!!.total!!, 0.001)
        assertEquals(100.0, course.marks!!.maxTotal!!, 0.001)
    }

    @Test
    fun `summaries do not double count the two spellings of a half`() {
        val snapshot = snapshot(threeRows())
        val summary = Projections.attendanceSummary(snapshot, "S1", 75f)
        // Without linking all three rows count: 66/74. One logical course gives 42/47.
        assertEquals(42, summary.attended)
        assertEquals(47, summary.total)
        assertEquals(1, Projections.marks(snapshot, "S1").size)
        assertEquals(1, Projections.courses(snapshot, "S1").size)
    }

    @Test
    fun `merge is idempotent on rows and on a snapshot`() {
        val once = EmbeddedLinkMerge.mergeCourses(fourRows())
        val twice = EmbeddedLinkMerge.mergeCourses(once)
        assertEquals(once, twice)

        val snapshot = snapshot(threeRows())
        val mergedSnapshot = EmbeddedLinkMerge.mergeSnapshot(snapshot)
        assertEquals(mergedSnapshot, EmbeddedLinkMerge.mergeSnapshot(mergedSnapshot))
    }

    @Test
    fun `a lone half is left alone but keyed canonically`() {
        val merged = EmbeddedLinkMerge.mergeCourses(listOf(theoryRow("BCSE203E(T)", "Embedded Theory")))
        assertEquals(1, merged.size)
        assertEquals("BCSE203E(T)", merged.single().code, "single rows keep their identity")
        assertEquals("BCSE203E", merged.single().linkedBaseCode)
        assertFalse(merged.single().isEmbeddedMerged)
        assertFalse(merged.single().halves.isNotEmpty())
    }

    @Test
    fun `legacy ingestion path links the same pair`() {
        fun stored(code: String, type: String, theory: Boolean) = StoredCourse(
            courseCode = code,
            courseTitle = if (theory) "Database Systems" else "Database Systems Lab",
            courseType = type,
            credits = if (theory) "3" else "1",
            attendance = if (theory) StoredAttendance(24, 27, "88.89") else StoredAttendance(18, 20, "90"),
            marks = StoredMarks(
                assessments = listOf(
                    AssessmentItem(
                        title = if (theory) "CIA-1" else "Lab CIA",
                        weightageMark = if (theory) "40" else "90",
                        maxMark = "100",
                    )
                ),
                totalMark = if (theory) 40.0 else 90.0,
                maxMark = 100.0,
            ),
        )
        val academic = AcademicData(
            semesters = mapOf(
                "S1" to SemesterData(
                    semesterId = "S1",
                    courses = mapOf(
                        "BCSE203E(T)" to stored("BCSE203E(T)", "Embedded Theory", true),
                        "BCSE203E(L)" to stored("BCSE203E(L)", "Embedded Lab", false),
                    ),
                )
            )
        )
        val linked = AcademicMerge.linkEmbeddedPairs(academic).semesters.getValue("S1").courses
        assertEquals(setOf("BCSE203E"), linked.keys)
        val course = linked.getValue("BCSE203E")
        assertEquals("4", course.credits)
        assertEquals(42, course.attendance!!.attendedClasses)
        assertEquals(47, course.attendance!!.totalClasses)
        assertEquals(52.5, course.marks!!.totalMark!!, 0.001)
    }

    @Test
    fun `M3E summary guard counts a suffixed and bare card only once`() {
        fun card(code: String) = M3ECourseAttendanceCardState(
            courseCode = code,
            courseTitle = "Database Systems",
            badge = "Embedded",
            attendedClasses = 24,
            totalClasses = 27,
            percentage = 88.9,
            tone = M3ECardTone.GOOD,
            faculty = "",
            slot = "C1",
            bunkSummary = "",
            isEmbedded = true,
        )
        assertEquals("BCSE203E", M3EComputations.canonicalCourseKey("BCSE203E(T)"))
        val deduped = M3EComputations.deduplicateByBaseCode(listOf(card("BCSE203E(T)"), card("BCSE203E")))
        assertEquals(1, deduped.size)
    }

    // ── D-189: no invented credit ratio, no invented combined attendance ──────────

    @Test
    fun `two halves with missing or zero credits leave marks total null`() {
        fun merged(t: String?, l: String?) = EmbeddedLinkMerge.mergeCourses(
            listOf(
                marksHalf("BCSE203E(T)", "Embedded Theory", t, 40.0, "88.89"),
                marksHalf("BCSE203E(L)", "Embedded Lab", l, 90.0, "90"),
            )
        ).single()

        assertNull(merged(null, "1").marks?.total, "missing theory credit")
        assertNull(merged("3", null).marks?.total, "missing lab credit")
        assertNull(merged("", " ").marks?.total, "blank credits")
        assertNull(merged("0", "1").marks?.total, "zero is not a weight")
        // The old code returned 65 (= (40+90)/2) for all four cases above; now they are null.
        assertEquals("Embedded Theory + Embedded Lab", merged("3", "1").marks?.mergedFrom)
    }

    @Test
    fun `3 plus 1 and 2 plus 2 blends use each half's own credits`() {
        fun total(tCredits: String, lCredits: String, tScore: Double, lScore: Double): Double? =
            EmbeddedLinkMerge.mergeCourses(
                listOf(
                    marksHalf("BCSE203E(T)", "Embedded Theory", tCredits, tScore, "88.89"),
                    marksHalf("BCSE203E(L)", "Embedded Lab", lCredits, lScore, "90"),
                )
            ).single().marks!!.total

        // (40*3 + 90*1) / 4 = 52.5; the old 50/50 would have been 65.
        assertEquals(52.5, total("3", "1", 40.0, 90.0)!!, 0.000001)
        // (90*2 + 50*2) / 4 = 70 — equal credits, still each half's own credit.
        assertEquals(70.0, total("2", "2", 90.0, 50.0)!!, 0.000001)
        // Same scores at 3+1 give 80, proving the split actually drives the blend.
        assertEquals(80.0, total("3", "1", 90.0, 50.0)!!, 0.000001)
    }

    @Test
    fun `two halves keep their own attendance figures while the merged row keeps only counts`() {
        val merged = EmbeddedLinkMerge.mergeCourses(
            listOf(
                theoryRow("BCSE203E(T)", "Embedded Theory"),
                labRow("BCSE203E(L)", "Embedded Lab"),
            )
        ).single()

        assertTrue(merged.isEmbeddedMerged)
        assertEquals(42, merged.attendance!!.attended, "24 + 18 counts are still available")
        assertEquals(47, merged.attendance!!.total, "27 + 20 counts are still available")
        assertEquals("", merged.attendance!!.percentage, "two classes are not one percentage")
        assertEquals(2, merged.halves.size)
        assertEquals(
            setOf("88.89", "90"),
            merged.halves.map { it.attendance!!.percentage }.toSet(),
            "each half retains its own published figure",
        )
    }

    @Test
    fun `a single published half keeps its marks and attendance verbatim`() {
        val lone = EmbeddedLinkMerge.mergeCourses(
            listOf(theoryRow("BCSE203E(T)", "Embedded Theory"))
        ).single()

        assertFalse(lone.isEmbeddedMerged)
        assertEquals(40.0, lone.marks!!.total!!, 0.000001)
        assertEquals("88.89", lone.attendance!!.percentage)
    }
}
