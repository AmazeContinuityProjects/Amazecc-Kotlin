import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Remove lines 470-476
del lines[469:476]

# Find where TimetableGridScreen ends
# It starts at 646 (now 639). We need to insert the block at the end of the function.
# Let's just find the last line of the file (which should be the closing bracket of TimetableGridScreen)
last_brace = len(lines) - 1
while last_brace > 0 and lines[last_brace].strip() != '}':
    last_brace -= 1

if last_brace > 0:
    block = """
    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }
"""
    lines.insert(last_brace, block)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'w', encoding='utf-8') as f:
    f.writelines(lines)
