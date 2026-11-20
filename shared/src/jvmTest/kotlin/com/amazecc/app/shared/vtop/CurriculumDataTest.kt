package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure seams for the curriculum data fixes. These assert the generated WebView scripts, so they
 * run on the JVM with no session and no HTML parser.
 */
class CurriculumDataTest {

    private fun occurrences(haystack: String, needle: String): Int =
        haystack.windowed(needle.length).count { it == needle }

    @Test
    fun stageTwoCurriculumBodyReplaysAuthorizedId() {
        val script = VtopScripts.fetchCurriculumCategory("/vtop/academics/common/curriculumCategoryView", "page-csrf", "UCC", "25BLC1081")

        assertTrue(script.contains("authorizedID="), "stage 2 must echo the session authorizedID")
        assertTrue(script.contains("25BLC1081"), "the real id must reach the generated JS literal")
        assertTrue(script.contains("page-csrf"), "the stage-1 page csrf is still sent")
        assertTrue(script.contains("categoryId="), "the category code is still sent")
    }

    @Test
    fun legacyThreeArgOverloadDoesNotInventAnAuthorizedId() {
        val script = VtopScripts.fetchCurriculumCategory("/vtop/academics/common/curriculumCategoryView", "page-csrf", "NC")

        assertFalse(script.contains("authorizedID="), "an absent id must not become an empty field")
        assertTrue(script.contains("categoryId="))
    }

    @Test
    fun curriculumDownloadScriptMirrorsVtopForm() {
        val script = VtopScripts.downloadCurriculum("/vtop/academics/curriculDown", "csrf-1", "25BLC1081")

        // VTOP sends _csrf twice (routes/remote/curriculum-download.ts:21-24): the second is
        // named `&_csrf=` in the same form body, so count the field name, not the quote prefix.
        assertTrue(occurrences(script, "_csrf=") == 2, "expected exactly two _csrf fields")
        assertTrue(script.contains("regNo=NONE"), "regNo=NONE is part of the real form")
        assertTrue(script.contains("25BLC1081"))
        assertTrue(script.contains("Referer"), "the download needs the Curriculum page as Referer")
        assertTrue(script.contains("x-user-defined"), "binary bytes must be rebuilt, not decoded as UTF-8")
    }

    @Test
    fun basketTablesAreSelectedByHeaderNotByPosition() {
        val script = VtopScripts.fetchGradeHistorySummary("/vtop/examinations/examGradeView/StudentGradeHistory", "body")

        assertTrue(script.contains("Curriculum Distribution Type"))
        assertTrue(script.contains("Basket Title"))
        assertFalse(script.contains("tables[5]"), "positional table selection must be gone")
        assertFalse(script.contains("tables[6]"), "positional table selection must be gone")
    }
}
