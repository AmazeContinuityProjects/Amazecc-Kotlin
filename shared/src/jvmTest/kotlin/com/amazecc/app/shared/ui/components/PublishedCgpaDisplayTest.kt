package com.amazecc.app.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The stat card must show the published (degree-wide) CGPA whenever it exists. A semester GPA is
 * an SGPA and is only a fallback for grade responses that carried no `overall.cgpa` block.
 */
class PublishedCgpaDisplayTest {

    @Test
    fun publishedCgpaWinsOverSemesterGpa() {
        assertEquals(
            "8.74",
            publishedCgpaOrSemesterGpa(publishedCgpa = "8.74", semesterGpa = "9.10")
        )
    }

    @Test
    fun fallsBackToSemesterGpaWhenPublishedCgpaIsMissing() {
        assertEquals(
            "9.10",
            publishedCgpaOrSemesterGpa(publishedCgpa = null, semesterGpa = "9.10")
        )
        assertEquals(
            "9.10",
            publishedCgpaOrSemesterGpa(publishedCgpa = "", semesterGpa = "9.10"),
            "a blank published value must not mask a real SGPA"
        )
    }

    @Test
    fun bothMissingYieldsEmptyValue() {
        assertEquals("", publishedCgpaOrSemesterGpa(null, null))
        assertEquals("", publishedCgpaOrSemesterGpa("", ""))
    }

    @Test
    fun blankSemesterGpaDoesNotMaskPublishedCgpa() {
        assertEquals("8.74", publishedCgpaOrSemesterGpa("8.74", null))
    }
}
