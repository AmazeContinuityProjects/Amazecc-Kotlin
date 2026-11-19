package com.amazecc.app.shared.domain

import com.amazecc.app.shared.vtop.VtopComponent
import com.amazecc.app.shared.vtop.VtopCourseCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one normalizer rule, exercised against the two spellings the real endpoints emit:
 *
 * - API rows carry a `(T)`/`(L)` suffix on the code,
 * - scraper rows carry no suffix but a `type` such as `Embedded Theory` / `Embedded Lab`.
 *
 * Both must resolve to one base code and one half, or the same logical course is shown twice.
 */
class EmbeddedLinkCodeTest {

    @Test
    fun `base code strips paren and dash markers case insensitively`() {
        val forms = listOf(
            "BCSE203E", "bcse203e", " BCSE203E ", "BCSE203E(T)", "bcse203e(t)",
            "BCSE203E (L)", "BCSE203E-L", "BCSE203E/L", "BCSE203E - Embedded Lab",
            "BCSE203E(L) - Database Systems",
        )
        assertEquals(1, forms.map { EmbeddedLinkCode.baseCode(it) }.toSet().size)
        forms.forEach { assertEquals("BCSE203E", EmbeddedLinkCode.baseCode(it), "from $it") }
    }

    @Test
    fun `base code is idempotent`() {
        val once = EmbeddedLinkCode.baseCode(" BCSE203E(t) ")
        assertEquals(once, EmbeddedLinkCode.baseCode(once))
        assertEquals("", EmbeddedLinkCode.baseCode(null))
        assertEquals("", EmbeddedLinkCode.baseCode("   "))
    }

    @Test
    fun `suffixed API row and type only scraper row are the same course`() {
        assertTrue(EmbeddedLinkCode.sameCourse("BCSE203E(T)", "BCSE203E"))
        assertTrue(EmbeddedLinkCode.sameCourse("BCSE203E(L)", "BCSE203E"))
        assertTrue(EmbeddedLinkCode.sameCourse("BCSE203E-L", "bcse203e"))
        assertFalse(EmbeddedLinkCode.sameCourse("BCSE203E", "BCSE204E"))
    }

    @Test
    fun `half comes from the suffix when the code carries one`() {
        assertEquals(EmbeddedLinkCode.Half.THEORY, EmbeddedLinkCode.halfOf("BCSE203E(T)"))
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("BCSE203E(L)"))
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("bcse203e-l"))
        // An explicit marker wins even when the type text disagrees.
        assertEquals(EmbeddedLinkCode.Half.THEORY, EmbeddedLinkCode.halfOf("BCSE203E(T)", "Lab Only"))
    }

    @Test
    fun `half comes from the type when the code has no suffix`() {
        assertEquals(EmbeddedLinkCode.Half.THEORY, EmbeddedLinkCode.halfOf("BCSE203E", "Embedded Theory"))
        assertEquals(EmbeddedLinkCode.Half.THEORY, EmbeddedLinkCode.halfOf("BCSE203E", "Theory Only"))
        assertEquals(EmbeddedLinkCode.Half.THEORY, EmbeddedLinkCode.halfOf("BCSE203E", "Theory"))
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("BCSE203E", "Embedded Lab"))
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("BCSE203E", "Lab Only"))
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("BCSE203E", "Lab"))
        assertNull(EmbeddedLinkCode.halfOf("BCSE203E", "Soft Skill"))
    }

    @Test
    fun `embedded wording is classified before generic lab wording`() {
        // "Embedded Lab" also contains "Lab"; a generic lab pattern would misclassify every
        // embedded course as a standalone Lab Only and stop the halves pairing.
        assertEquals(EmbeddedLinkCode.Component.ELA, EmbeddedLinkCode.componentOf("BCSE203E", "Embedded Lab"))
        assertEquals(EmbeddedLinkCode.Component.ETH, EmbeddedLinkCode.componentOf("BCSE203E", "Embedded Theory"))
        assertEquals(VtopComponent.ELA, VtopCourseCode.componentOf("BCSE203E", "Embedded Lab"))
        assertEquals(VtopComponent.ETH, VtopCourseCode.fromTypeLabel("Embedded Theory"))
        assertEquals(VtopComponent.LO, VtopCourseCode.fromTypeLabel("Lab Only"))
        assertEquals(VtopComponent.UNKNOWN, VtopCourseCode.fromTypeLabel("Soft Skill"))
    }

    @Test
    fun `slot is the last resort and loses to an explicit label`() {
        assertEquals(EmbeddedLinkCode.Half.LAB, EmbeddedLinkCode.halfOf("BCSE203E", null, "L31+L32"))
        assertEquals(
            EmbeddedLinkCode.Half.THEORY,
            EmbeddedLinkCode.halfOf("BCSE203E", "Embedded Theory", "L31+L32"),
        )
    }

    @Test
    fun `VtopCourseCode delegates to the canonical normalizer`() {
        assertEquals("BCSE203E", VtopCourseCode.base("BCSE203E(T)"))
        assertEquals("BCSE203E", VtopCourseCode.base("BCSE203E-L"))
        assertEquals("L", VtopCourseCode.suffixOf("BCSE203E(L)"))
        assertEquals("T", VtopCourseCode.suffixOf("BCSE203E-t"))
        assertEquals("BCSE203E", VtopCourseCode.canonical("BCSE203E(T)"))
    }

    @Test
    fun `display title names the course from the theory half`() {
        assertEquals(
            "Database Systems",
            EmbeddedLinkCode.displayTitle("Database Systems", "Database Systems Lab", "BCSE203E"),
        )
        assertEquals("Database Systems Lab", EmbeddedLinkCode.displayTitle("", "Database Systems Lab", "BCSE203E"))
        assertEquals("BCSE203E", EmbeddedLinkCode.displayTitle("", "", "BCSE203E"))
    }
}
