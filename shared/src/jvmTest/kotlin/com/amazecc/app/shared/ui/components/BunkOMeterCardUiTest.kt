package com.amazecc.app.shared.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.theme.AmazeTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BunkOMeterCardUiTest {

    private fun sampleCourse(
        code: String,
        attended: Int,
        total: Int,
        pct: String
    ) = AttendanceItem(
        courseCode = code,
        courseTitle = "Sample Course $code",
        courseType = "Theory Only",
        slotName = "A1",
        faculty = "Prof. Test",
        attendedClasses = attended,
        totalClasses = total,
        attendancePercentage = pct
    )

    @Test
    fun shows_empty_state_when_no_courses_provided() = runComposeUiTest {
        setContent {
            AmazeTheme {
                BunkOMeterCard(courses = emptyList())
            }
        }

        onNodeWithText("Bunk-O-Meter Ready").assertExists()
        onNodeWithText("Sync attendance to calculate safe bunk limits.").assertExists()
    }

    @Test
    fun shows_critical_warning_when_attendance_is_below_target() = runComposeUiTest {
        val criticalList = listOf(
            sampleCourse("CSE101", attended = 10, total = 20, pct = "50.0")
        )

        setContent {
            AmazeTheme {
                BunkOMeterCard(courses = criticalList, targetPct = 75f)
            }
        }

        onNodeWithText("⚠️ 1 Critical Course").assertExists()
        onNodeWithText("Attendance is below 75%! Do NOT skip any more classes.").assertExists()
    }

    @Test
    fun shows_safe_state_when_all_courses_are_well_above_target() = runComposeUiTest {
        val safeList = listOf(
            sampleCourse("MAT101", attended = 19, total = 20, pct = "95.0")
        )

        setContent {
            AmazeTheme {
                BunkOMeterCard(courses = safeList, targetPct = 75f)
            }
        }

        onNodeWithText("Safe to Bunk ~5 Classes", substring = true).assertExists()
    }
}
