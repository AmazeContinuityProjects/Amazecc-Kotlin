import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the dangling block by finding the hanging block and moving it inside the function
hanging_block = """}

    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }


@Composable"""

fixed_block = """    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }
}

@Composable"""

if hanging_block in content:
    content = content.replace(hanging_block, fixed_block)
    
with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed AttendanceScreen.kt syntax error")
