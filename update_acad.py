import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove the Timetable Section and Calendar Section
# Find start of Timetable Section
start_str = "            // ✨ Timetable Section ✨"
end_str = "            item { Spacer(modifier = Modifier.height(16.dp)) }"

start_idx = content.find(start_str)
end_idx = content.find(end_str)

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + content[end_idx:]

# 2. Remove the showTimetableDialog variable
var_str = "    var showTimetableDialog by remember { mutableStateOf(false) }"
content = content.replace(var_str + "\n", "")

# 3. Remove the TimetableDialog call block
call_str = """    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = attendanceCourses,
            timetableCourses = timetableCourses,
            onDismiss = { showTimetableDialog = false }
        )
    }"""
content = content.replace(call_str, "")

# 4. Remove TimetableCard definition
card_pattern = re.compile(r'@Composable\s+private fun TimetableCard.*?^}', re.MULTILINE | re.DOTALL)
content = card_pattern.sub('', content)

# 5. Remove TimetableDialog definition
dialog_pattern = re.compile(r'@Composable\s+fun TimetableDialog.*?^}', re.MULTILINE | re.DOTALL)
content = dialog_pattern.sub('', content)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated AcademicsScreen.kt")
