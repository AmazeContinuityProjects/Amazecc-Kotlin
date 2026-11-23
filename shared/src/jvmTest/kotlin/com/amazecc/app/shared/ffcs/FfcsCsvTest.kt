package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.ffcs.planner.ParsedCourse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-176 — CSV import/export + canonical theory/lab pairing acceptance tests.
 *
 * Grounded in `ffcs-planner` reference/05 (§1 writer rules, §2 row rules,
 * §3.1 pair-score constants, §6 validation) and reference/03:124-333
 * (shipped headers, `parseFastCSV`, `parseReportRows` aliasing). Golden counts
 * come from `assets/golden-vectors.json` → `csv.embeddedCombos`.
 */
class FfcsCsvTest {

    companion object {
        /** Golden vectors: `csv.embeddedCombos` (full web report catalogues). */
        const val GOLDEN_WINTER_MOCK_COMBOS = 1128
        const val GOLDEN_WINTER_MOCK_BA_COMBOS = 726
        const val GOLDEN_FALL_COMBOS = 365
        const val GOLDEN_BAMEE203_COMBOS = 10

        /** Rows in the bundled 7-column fall subset (`FfcsReportData.CSV_DATA`). */
        const val BUNDLED_ROWS = 2357
    }

    private fun course(
        code: String = "BAMAT101",
        title: String = "Multivariable Calculus and Differential Equations",
        type: String = "ETH",
        credits: String = "3",
        classId: String? = "CH2026270103142",
        room: String = "AB3-509",
        slot: String = "G1+TG1",
        erpId: String? = "50791",
        faculty: String = "PARTHIBAN V",
        school: String? = "SAS",
    ) = ParsedCourse(
        CODE = code,
        TITLE = title,
        TYPE = type,
        CREDITS = credits,
        ROOM = room,
        SLOT = slot,
        FACULTY = faculty,
        CLASS_ID = classId,
        ERP_ID = erpId,
        EMPLOYEE_SCHOOL = school,
    )

    // ---- writer ------------------------------------------------------------

    @Test
    fun canonicalHeader_isByteEqualAndLfTerminated() {
        val csv = FfcsCsv.writeRows(
            FfcsCsv.CANONICAL_HEADER,
            listOf(
                listOf(
                    "BAMAT101", "Multivariable Calculus and Differential Equations", "ETH", "3",
                    "CH2026270103142", "AB3-509", "G1+TG1", "50791", "PARTHIBAN V", "SAS",
                ),
            ),
        )

        assertEquals(FfcsCsv.CANONICAL_HEADER, csv.substringBefore('\n'))
        assertTrue(csv.endsWith("\n"), "a written report always has a trailing LF")
        assertFalse('\r' in csv, "CRLF is tolerated on read but never emitted")
        assertEquals(2, csv.split("\n").size - 1, "one header + one row + trailing newline")
    }

    @Test
    fun rfc4180_quoteEscapingRoundTrips() {
        val tricky = "Robotics: Kinematics, Dynamics and \"Motion\"\nControl"
        val csv = FfcsCsv.writeRows("CODE,TITLE", listOf(listOf("BACSE211", tricky)))

        assertTrue(
            csv.contains("\"Robotics: Kinematics, Dynamics and \"\"Motion\"\"\nControl\""),
            "commas, doubled quotes and embedded newlines must be quoted",
        )
        val rows = FfcsCsv.parseRows(csv)
        assertEquals(listOf("CODE", "TITLE"), rows[0])
        assertEquals(2, rows.size, "the embedded newline must not split a row")
        assertEquals(tricky, rows[1][1])
    }

    // ---- reader ------------------------------------------------------------

    @Test
    fun reader_toleratesBomAndBothHeaderDialects() {
        val canonicalCsv = "\uFEFF" + FfcsCsv.CANONICAL_HEADER + "\n" +
            "BAMAT101,Multivariable Calculus and Differential Equations,ETH,3," +
            "CH2026270103142,AB3-509,G1+TG1,50791,PARTHIBAN V,SAS\n" +
            "BACSE211,\"Robotics: Kinematics, Dynamics and Motion Control\",ETH,3," +
            "CH2026270501792,AB3-308,B1+TB1,51651,MUTHUKUMAR AN K,SCOPE\n"
        val parsed = FfcsCsv.parseReportRows(canonicalCsv)

        assertEquals(2, parsed.size)
        assertEquals("BAMAT101", parsed[0].CODE)
        assertEquals("CH2026270103142", parsed[0].CLASS_ID)
        assertEquals("50791", parsed[0].ERP_ID)
        assertEquals("SAS", parsed[0].EMPLOYEE_SCHOOL)
        assertEquals("G1+TG1", parsed[0].SLOT)
        assertEquals("Robotics: Kinematics, Dynamics and Motion Control", parsed[1].TITLE)

        val legacyCsv = "\uFEFF" + FfcsCsv.LEGACY_HEADER + "\n" +
            "BAARB101,Arabic Level I,TH,3,TAA1,MOHD SAQIB,PRP134\n"
        assertEquals(FfcsCsv.LEGACY_HEADER, FfcsCsv.detectHeader(legacyCsv))
        assertTrue(FfcsCsv.hasAcceptedHeader(legacyCsv))

        val legacy = FfcsCsv.parseReportRows(legacyCsv).single()
        assertEquals("BAARB101", legacy.CODE)
        assertEquals("TH", legacy.TYPE)
        assertEquals("TAA1", legacy.SLOT)
        assertEquals("MOHD SAQIB", legacy.FACULTY)
        assertEquals("PRP134", legacy.ROOM)
        assertNull(legacy.CLASS_ID)
        assertNull(legacy.ERP_ID)
        assertNull(legacy.EMPLOYEE_SCHOOL)
    }

    // ---- golden pins / bundled data ---------------------------------------

    @Test
    fun bundledReport_matchesGoldenPinsAndProducesClashFreePairs() {
        val rows = FfcsCsv.parseReportRows(FfcsReportData.CSV_DATA)

        assertEquals(BUNDLED_ROWS, rows.size)
        assertTrue(rows.all { FfcsCsv.isValidCode(it.CODE) }, "every CODE matches /^[A-Z0-9]{7,8}$/")
        assertEquals(
            GOLDEN_BAMEE203_COMBOS,
            rows.count { it.CODE == "BAMEE203" },
            "BAMEE203's 10 pre-combined ETH+ELA rows are readable in the bundled CSV",
        )

        // The 1128 / 726 / 365 pins belong to the full web report assets; the
        // bundled Kotlin CSV is their 7-column fall subset, so only the
        // BAMEE203 pin is directly observable here. The constants above keep
        // the full pins recorded for the next catalogue bundling pass.
        // A real-data slice keeps the assertion fast while still exercising the
        // bundled fall rows (BAMAT101's ETH + ELA halves share PARTHIBAN V).
        val grouped = FfcsCourseProcessor.processCanonicalCourses(rows.filter { it.CODE == "BAMAT101" })
        val combos = grouped.filter { it.TYPE == "ETH+ELA" }
        assertEquals(1, combos.size, "the bundled ETH + ELA halves merge into one offering")
        assertTrue(combos.all { it.CREDITS == "4" })
        assertTrue(combos.all { it.FACULTY == "PARTHIBAN V" })
    }

    @Test
    fun pairScore_appliesTheFourTiers() {
        val evening = course(type = "ETH", slot = "A2+TA2")
        val morning = course(type = "ETH", slot = "G1+TG1")
        val coreMorningLab = course(type = "ELA", slot = "L1+L2")
        val coreEveningLab = course(type = "ELA", slot = "L31+L32")
        val lateMorningLab = course(type = "ELA", slot = "L5+L6")
        val lateEveningLab = course(type = "ELA", slot = "L35+L36")

        // 840 (2:00 PM) is the morning/evening boundary (FfcsSlotSchema).
        assertEquals(840, FfcsSlotSchema.MORNING_BEFORE_MIN)
        assertTrue(FfcsCourseProcessor.computePairScore(evening, coreMorningLab) >= 2000)
        assertTrue(FfcsCourseProcessor.computePairScore(morning, coreEveningLab) >= 2000)
        // The late windows share Monday with TG1 / A2, so -30 per same-half
        // period pair applies on top of the +1000 tier.
        assertTrue(FfcsCourseProcessor.computePairScore(morning, lateMorningLab) in 900..1000)
        assertTrue(FfcsCourseProcessor.computePairScore(evening, lateEveningLab) in 900..1000)
        assertTrue(FfcsCourseProcessor.computePairScore(evening, lateMorningLab) >= 500)
        assertTrue(FfcsCourseProcessor.computePairScore(morning, coreMorningLab) <= -1000)
    }

    @Test
    fun classIdProximity_scores20Then10() {
        val theory = course(classId = "CH2026270103142")
        assertEquals(20, FfcsCsv.classIdProximityBonus(theory, course(classId = "CH2026270103143")))
        assertEquals(10, FfcsCsv.classIdProximityBonus(theory, course(classId = "CH2026270103150")))
        assertEquals(0, FfcsCsv.classIdProximityBonus(theory, course(classId = "CH2026270103999")))
        assertEquals(0, FfcsCsv.classIdProximityBonus(theory, course(classId = null)))
    }

    @Test
    fun embeddedPairing_avoidsOverlapAndKeepsOneErpPerPair() {
        val csv = listOf(
            FfcsCsv.CANONICAL_HEADER,
            "BACSE201,Data Structures and Algorithms,ETH,3,CH2026270103142,AB2-201,A2+TA2,60123,TEST FACULTY,SCOPE",
            "BACSE201,Data Structures and Algorithms,ETH,3,CH2026270103144,AB2-201,A2+TA2,60123,TEST FACULTY,SCOPE",
            "BACSE201,Data Structures and Algorithms,ELA,1,CH2026270103143,AB2-202L,L1+L2,60123,TEST FACULTY,SCOPE",
            "BACSE201,Data Structures and Algorithms,ELA,1,CH2026270103145,AB2-202L,L7+L8,60123,TEST FACULTY,SCOPE",
            // Overlaps A2 on Monday — the branch-and-bound must never pick it.
            "BACSE201,Data Structures and Algorithms,ELA,1,CH2026270103146,AB2-202L,A2+L3,60123,TEST FACULTY,SCOPE",
        ).joinToString("\n", postfix = "\n")

        val grouped = FfcsCourseProcessor.pairCanonicalCsv(csv)
        val combos = grouped.filter { it.TYPE == "ETH+ELA" }

        assertEquals(2, combos.size, "two clash-free pairs out of three lab candidates")
        assertTrue(combos.all { it.ERP_ID == "60123" }, "both halves of every pair share one ERP id")
        assertTrue(combos.none { "A2+L3" in it.SLOT }, "the overlapping lab is left unpaired")
        assertTrue(combos.all { it.CREDITS == "4" }, "3 + 1 credits are merged")
        assertTrue(combos.all { "Embedded Theory and Lab" in it.TITLE })
        assertTrue(
            combos.all { it.CLASS_IDS?.size == 2 },
            "both class ids survive the merge (and earn the +20 proximity bonus)",
        )
    }

    // ---- validation / registry --------------------------------------------

    @Test
    fun validate_reportsBadCodeSlotPrefixDuplicateIdAndMixedErp() {
        val bad = listOf(
            course(code = "bad", slot = "A1"),
            course(code = "BACSE201", slot = ""),
            course(code = "BACSE201", slot = "A1", classId = "XYZ123"),
            course(code = "BACSE201", slot = "L1+L2", classId = "XYZ123", erpId = "2"),
            course(code = "BACSE201", slot = "A2+TA2", classId = "CH2026270103142", erpId = "2"),
            course(code = "BACSE201", slot = "L31+L32", classId = "CH2026270103143", erpId = "3"),
        )
        val errors = FfcsCsv.validate(bad)

        assertTrue(errors.any { "bad CODE" in it })
        assertTrue(errors.any { "empty SLOT" in it })
        assertTrue(errors.any { "unexpected CLASS_ID prefix" in it })
        assertTrue(errors.any { "duplicate CLASS_ID" in it })
        assertTrue(errors.any { "mixed ERP_ID" in it })
    }

    @Test
    fun validate_acceptsAWellFormedPair() {
        val valid = listOf(
            course(code = "BAMAT101", slot = "G1+TG1", erpId = "50791"),
            course(
                code = "BAMAT101", type = "ELA", slot = "L31+L32",
                classId = "CH2026270103143", erpId = "50791",
            ),
        )
        assertTrue(FfcsCsv.validate(valid).isEmpty())
    }

    @Test
    fun semesterRegistry_resolvesPreviousAliasAndPrefixes() {
        assertEquals(FfcsSemesterId.WINTER_FINAL, FfcsReports.DEFAULT_SEMESTER)
        assertEquals(FfcsReports.PATH_FALL_FINAL, FfcsReports.getFfcsSemester("previous").path)
        assertEquals(FfcsReports.PREFIX_WINTER, FfcsReports.getFfcsSemester("winter-2026-2027").classIdPrefix)
        assertEquals("2", FfcsReports.getFfcsSemester("winter-2026-2027").classIdPrefixCode)
        assertEquals(FfcsReports.PATH_CODECHEF, FfcsReports.getFfcsSemester("codechef-vit").path)
        assertTrue(FfcsReports.isFfcsSemesterId("winter-2026-2027-mock"))
        assertFalse(FfcsReports.isFfcsSemesterId("not-a-semester"))
        assertEquals(
            FfcsReports.PATH_WINTER_FINAL,
            FfcsReports.getFfcsSemesterByClassIdPrefix("CH2026270503142")?.path,
            "winter final and mock share a namespace; the tie resolves to the default",
        )
    }
}
