package com.amazecc.app.shared.ui.components

/**
 * Pick the value a CGPA stat card should show: the published degree-wide CGPA is authoritative;
 * a semester GPA is an SGPA and may only be used when no published value exists.
 */
fun publishedCgpaOrSemesterGpa(publishedCgpa: String?, semesterGpa: String?): String =
    publishedCgpa?.takeIf { it.isNotBlank() }
        ?: semesterGpa?.takeIf { it.isNotBlank() }
        ?: ""
