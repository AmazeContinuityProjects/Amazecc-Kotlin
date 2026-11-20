package com.amazecc.app.shared.model

import com.amazecc.app.shared.domain.asCurveStats
import com.amazecc.app.shared.domain.estimateGrade
import com.amazecc.app.shared.domain.estimateGradeWithCurve
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-012: the typed cohort-statistics / marks-token DTOs must decode both real response shapes
 * from `../amazecc-api-hono/src/routes/services/{curves,marks-tokens}.ts`, and the curve input
 * must reach `MarksPredictor.estimateGrade` only when the class sample is usable.
 */
class CurvesDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `single class curves response decodes`() {
        val payload = """
            {
              "success": true,
              "classId": "CSE1001",
              "semesterId": "CH20262701",
              "stats": { "count": 20, "mean": 60.2, "sd": 8.4 },
              "classmates": [ { "reg_no": "21BCE1234", "display_name": "Ada" } ]
            }
        """.trimIndent()

        val res = json.decodeFromString<CurvesEnvelopeDto>(payload)
        assertTrue(res.success)
        assertEquals("CSE1001", res.classId)
        assertEquals("21BCE1234", res.classmates.single().regNo)
        assertEquals("Ada", res.classmates.single().displayName)

        val stats = res.singleClassStats()!!
        assertEquals(20, stats.count)
        assertEquals(60.2, stats.mean, 0.0001)
        assertEquals(8.4, stats.sd, 0.0001)
        assertTrue(res.batchClassStats().isEmpty())
    }

    @Test
    fun `batch curves response decodes with assessment metadata`() {
        val payload = """
            {
              "success": true,
              "stats": {
                "CSE1001": {
                  "count": 20, "mean": 60.2, "sd": 8.4,
                  "assessments": {
                    "abc123": { "count": 18, "mean": 72.1, "sd": 6.2, "component": "theory", "title": "CAT - I" }
                  }
                },
                "MAT1002": { "count": 2, "mean": 0, "sd": 0, "assessments": {} }
              }
            }
        """.trimIndent()

        val res = json.decodeFromString<CurvesEnvelopeDto>(payload)
        assertNull(res.singleClassStats())

        val byClass = res.batchClassStats()
        assertEquals(setOf("CSE1001", "MAT1002"), byClass.keys)
        val cat = byClass["CSE1001"]!!.assessments["abc123"]!!
        assertEquals("theory", cat.component)
        assertEquals("CAT - I", cat.title)
        assertEquals(18, cat.count)

        // count=2 is below MIN_CURVE_SAMPLE: the API publishes {mean:0, sd:0} for "no data".
        assertNull(byClass["MAT1002"]!!.asCurveStats())
    }

    @Test
    fun `curve input reaches estimateGrade only for a relative-graded course`() {
        val stats = json.decodeFromString<CurvesEnvelopeDto>(
            """{"success":true,"stats":{"count":20,"mean":60.0,"sd":8.0}}"""
        ).singleClassStats()!!
        assertNotNull(stats.asCurveStats())

        // Absolute ladder: 67 -> C. Curved around mean 60 / sd 8: 67 -> A.
        assertEquals("C", estimateGrade(67.0).letter)
        assertEquals("A", estimateGradeWithCurve(67.0, stats, "ACE", "Theory").letter)

        // A lab course is absolute even with the same curve data.
        assertEquals("C", estimateGradeWithCurve(67.0, stats, "ACE", "Lab").letter)
        // A legacy caller with no grading system keeps the absolute ladder.
        assertEquals("C", estimateGradeWithCurve(67.0, stats, null, "Theory").letter)
        // No curve data -> absolute.
        assertEquals("C", estimateGradeWithCurve(67.0, null, "ACE", "Theory").letter)
    }

    @Test
    fun `marks tokens response decodes`() {
        val payload = """
            {
              "success": true,
              "tokens": [
                { "classId": "CSE1001", "scope": "overall", "assessmentKey": "overall",
                  "token": "hmac-abc", "updatedAt": 1730000000000 }
              ],
              "legacy": []
            }
        """.trimIndent()

        val res = json.decodeFromString<MarksTokensEnvelopeDto>(payload)
        assertTrue(res.success)
        assertEquals("hmac-abc", res.tokens.single().token)
        assertEquals("overall", res.tokens.single().scope)
        assertEquals(1730000000000L, res.tokens.single().updatedAt)
        assertTrue(res.legacy.isEmpty())
    }
}
