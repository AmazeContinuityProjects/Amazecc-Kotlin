package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.VtopIngestor
import com.amazecc.app.shared.domain.buildCourseGroups
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An embedded course is two halves that one card pairs, not one row that two cards share.
 *
 * The store used to fold an ETH and an ELA row onto a single key and sum the class counts, which
 * made `CourseGroup.lab` permanently null and therefore `isEmbedded` permanently false — so every
 * academic surface showed the theory half and dropped the lab. Node never merges them
 * (`CourseDetailSubpage.tsx:174-186`): it groups by base code and keeps `theory` and `lab` as two
 * independent objects, then decides embedded-ness with the identical expression
 * `isEmbedded = theory && lab` (`:668`).
 *
 * The twelve rows below are CH20262701 verbatim, read off
 * `vtop-snapshots/attendance__CH20262701.html` via `resources/fixtures/attendance.json`:
 * four embedded pairs, one Lab Only, two Theory Only, one Soft Skill.
 */
class EmbeddedPairSplitTest {

    private fun att(
        code: String,
        title: String,
        type: String,
        slot: String,
        attended: Int,
        total: Int
    ) = AttendanceItem(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        slotName = slot,
        attendedClasses = attended,
        totalClasses = total,
        attendancePercentage = "0",
        viewLinkRaw = null
    )

    /** The 12 rows VTOP returns for CH20262701, in the order it returns them. */
    private val ch20262701 = AttendanceRes(
        attendance = listOf(
            att("BACSE102", "Problem Solving Using Java", "Lab Only", "L31+L32+L37+L38", 32, 36),
            att("BACSE105", "Data Structures and Algorithms", "Embedded Theory", "F1+TF1", 24, 27),
            att("BACSE105", "Data Structures and Algorithms", "Embedded Lab", "L11+L12", 18, 20),
            att("BACSE106", "Operating Systems", "Embedded Theory", "C1+TC1", 26, 28),
            att("BACSE106", "Operating Systems", "Embedded Lab", "L55+L56", 16, 18),
            att("BAECE203", "Analog Electronics", "Embedded Theory", "A1+TA1", 24, 27),
            att("BAECE203", "Analog Electronics", "Embedded Lab", "L23+L24", 18, 20),
            att("BAGER101", "German Level I", "Embedded Theory", "TE1", 10, 10),
            att("BAGER101", "German Level I", "Embedded Lab", "L43+L44", 16, 18),
            att("BAHUM251", "Indian Constitution", "Theory Only", "G1+TG1", 27, 29),
            att("BAMAT209", "Mathematical Foundations for Computation", "Theory Only", "D1+TD1+TDD1", 34, 37),
            att("BASTS101", "Qualitative and Quantitative Skills Practice I", "Soft Skill", "B1+TB1", 28, 29)
        )
    )

    private fun upsert(semId: String = "CH20262701") =
        AcademicMerge.upsertAttendance(AcademicData(), semId, ch20262701)
            .semesters.getValue(semId).courses

    /** [AcademicData] as the grouping layer sees it. */
    private fun AcademicData.snapshot() =
        VtopIngestor.fromLegacy(AppDataSnapshot(academic = this))

    /** The whole current-semester ingest: attendance, then marks over it. */
    private fun withAttendance(semId: String = "CH20262701") =
        AcademicMerge.upsertAttendance(AcademicData(), semId, ch20262701)

    // ── The halves survive ───────────────────────────────────────────────────

    @Test
    fun everyRowLandsSomewhereInsteadOfOverwritingItsPair() {
        val courses = upsert()
        assertEquals(12, courses.size, "one row per half, got ${courses.keys}")
    }

    @Test
    fun eachHalfKeepsItsOwnSlotRatherThanASummedList() {
        val courses = upsert()
        val theory = assertNotNull(courses["BACSE105"])
        val lab = assertNotNull(courses["BACSE105(L)"])
        assertEquals(listOf("F1", "TF1"), theory.slots)
        assertEquals(listOf("L11", "L12"), lab.slots)
    }

    @Test
    fun eachHalfKeepsItsOwnAttendance() {
        val courses = upsert()
        assertEquals(24, assertNotNull(courses["BACSE105"]).attendance!!.attendedClasses)
        assertEquals(27, assertNotNull(courses["BACSE105"]).attendance!!.totalClasses)
        assertEquals(18, assertNotNull(courses["BACSE105(L)"]).attendance!!.attendedClasses)
        assertEquals(20, assertNotNull(courses["BACSE105(L)"]).attendance!!.totalClasses)
    }

    @Test
    fun aLabOnlyCourseLandsOnTheLabKeyAndATheoryOnlyCourseOnTheBareKey() {
        val courses = upsert()
        assertNotNull(courses["BACSE102(L)"], "Lab Only is a lab half")
        assertNotNull(courses["BAHUM251"], "Theory Only is a theory half")
        assertNull(courses["BAHUM251(L)"])
    }

    @Test
    fun aCourseWithNoLabWordingAtAllStillGetsTheBareKey() {
        val courses = upsert()
        val soft = assertNotNull(courses["BASTS101"])
        assertEquals("Soft Skill", soft.courseType, "an unclassified type must survive verbatim")
    }

    // ── Which the pairing layer then joins back up ────────────────────────────

    @Test
    fun theGroupIsEmbeddedSoTheUiShowsBothHalves() {
        val groups = buildCourseGroups(withAttendance().snapshot())
        val embedded = groups.filter { it.isEmbedded }
        assertEquals(
            setOf("BACSE105", "BACSE106", "BAECE203", "BAGER101"),
            embedded.map { it.courseCode }.toSet()
        )
    }

    @Test
    fun theCardCarriesBothHalvesAttendance() {
        val group = buildCourseGroups(withAttendance().snapshot())
            .first { it.courseCode == "BACSE105" }
        assertEquals(24, group.theoryAtt!!.attendedClasses)
        assertEquals(18, group.labAtt!!.attendedClasses)
    }

    @Test
    fun aStandaloneCourseIsNotEmbedded() {
        val groups = buildCourseGroups(withAttendance().snapshot())
        val byCode = groups.associateBy { it.courseCode }
        assertTrue(!byCode.getValue("BACSE102").isEmbedded, "Lab Only is a single lab half")
        assertTrue(!byCode.getValue("BAHUM251").isEmbedded, "Theory Only is a single theory half")
        // The lab-only card must still find its half, on whichever side it landed.
        assertNotNull(byCode.getValue("BACSE102").labAtt)
    }

    @Test
    fun eightCardsNotTwelve() {
        // 12 rows collapse to 8 cards, because BACSE102 is a standalone Lab Only: it has no
        // theory half to pair with, so it renders alone rather than as a pair.
        val groups = buildCourseGroups(withAttendance().snapshot())
        assertEquals(8, groups.size, "4 pairs + Lab Only + 2 Theory Only + Soft Skill")
        assertEquals(
            listOf(
                "BACSE102", "BACSE105", "BACSE106", "BAECE203",
                "BAGER101", "BAHUM251", "BAMAT209", "BASTS101"
            ),
            groups.map { it.courseCode }
        )
    }

    // ── Marks: the reason the lab used to vanish ─────────────────────────────

    /**
     * Marks publishes an `Embedded Theory` row and no `Embedded Lab` row for BACSE105, BAECE203
     * and BAGER101 this term — read off `marks__CH20262701.html`, which carries one `Embedded
     * Lab` against four `Embedded Theory`. Node therefore ends up with a lab half that has
     * attendance but no marks, which is the honest rendering; merging hid the lab entirely.
     */
    private fun marksRows() = MarksRes(
        courses = listOf(
            MarksCourseItem(
                classNbr = "CH2026270102407",
                courseCode = "BACSE105",
                courseTitle = "Data Structures and Algorithms",
                courseType = "Embedded Theory",
                slot = "F1+TF1",
                credits = "3",
                assessments = listOf(
                    AssessmentItem(title = "CIA-1", maxMark = "50", weightageMark = "45", scoredMark = "45")
                )
            ),
            MarksCourseItem(
                classNbr = "CH2026270102460",
                courseCode = "BACSE106",
                courseTitle = "Operating Systems",
                courseType = "Embedded Theory",
                slot = "C1+TC1",
                credits = "3",
                assessments = listOf(
                    AssessmentItem(title = "CIA-1", maxMark = "50", weightageMark = "44", scoredMark = "44")
                )
            ),
            MarksCourseItem(
                classNbr = "CH2026270102167",
                courseCode = "BACSE106",
                courseTitle = "Operating Systems",
                courseType = "Embedded Lab",
                slot = "L55+L56",
                credits = "2",
                assessments = listOf(
                    AssessmentItem(title = "Lab CIA", maxMark = "100", weightageMark = "88", scoredMark = "88")
                )
            )
        )
    )

    @Test
    fun aCourseWithNoLabMarksRowStillShowsItsLabHalf() {
        val academic = AcademicMerge.upsertMarks(withAttendance(), "CH20262701", marksRows())
        val group = buildCourseGroups(academic.snapshot())
            .first { it.courseCode == "BACSE105" }
        assertTrue(group.isEmbedded, "attendance proves the pair exists")
        assertNotNull(group.lab, "attendance put a lab half there and marks must not delete it")
        assertNull(group.lab!!.totalMark, "there is genuinely no lab marks row this term")
        assertEquals(45.0, group.theory!!.totalMark!!, 0.001)
    }

    @Test
    fun aCompletePairKeepsEachHalfsOwnMarks() {
        val academic = AcademicMerge.upsertMarks(withAttendance(), "CH20262701", marksRows())
        val group = buildCourseGroups(academic.snapshot())
            .first { it.courseCode == "BACSE106" }
        assertTrue(group.isEmbedded)
        assertEquals(44.0, group.theory!!.totalMark!!, 0.001)
        assertEquals(88.0, group.lab!!.totalMark!!, 0.001)
    }

    @Test
    fun gradesStillLandOnTheTheoryHalf() {
        val academic = AcademicMerge.upsertGrades(withAttendance(), "CH20262701",
            "8.4",
            listOf(
                GradeItem(
                    courseCode = "BACSE105",
                    courseTitle = "Data Structures and Algorithms",
                    courseType = "Embedded Theory",
                    grandTotal = "88",
                    grade = "B+"
                )
            )
        )
        val courses = academic.semesters.getValue("CH20262701").courses
        assertEquals("B+", assertNotNull(courses["BACSE105"]).grade!!.grade)
    }
}