import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove Timetable Section and Calendar Section by finding "Timetable Section"
start_idx = content.find('Timetable Section')
if start_idx != -1:
    # Find the beginning of this line
    start_idx = content.rfind('\n', 0, start_idx)

end_idx = content.find('item { Spacer(modifier = Modifier.height(16.dp)) }')
if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + '\n            ' + content[end_idx:]
else:
    print("Could not find start or end index for section deletion.")

# 2. Remove the showTimetableDialog variable
content = re.sub(r'\s*var showTimetableDialog by remember \{ mutableStateOf\(false\) \}', '', content)

# 3. Remove the TimetableDialog call block
content = re.sub(r'\s*if \(showTimetableDialog\) \{.*?\n\s*\}', '', content, flags=re.DOTALL)

# 4. Remove TimetableCard definition
content = re.sub(r'@Composable\s+private fun TimetableCard.*?^}', '', content, flags=re.MULTILINE | re.DOTALL)
content = re.sub(r'@Composable\s+fun TimetableCard.*?^}', '', content, flags=re.MULTILINE | re.DOTALL)

# 5. Remove TimetableDialog definition
content = re.sub(r'@Composable\s+fun TimetableDialog.*?^}', '', content, flags=re.MULTILINE | re.DOTALL)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated AcademicsScreen.kt")
