import re

# 1. Read both files
with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'r', encoding='utf-8') as f:
    acad_content = f.read()

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    attn_content = f.read()

# 2. Extract TimetableDialog and TimetableCard
card_pattern = re.compile(r'@Composable\s+private fun TimetableCard.*?^}', re.MULTILINE | re.DOTALL)
dialog_pattern = re.compile(r'@Composable\s+fun TimetableDialog.*?^}', re.MULTILINE | re.DOTALL)

card_match = card_pattern.search(acad_content)
dialog_match = dialog_pattern.search(acad_content)

card_code = card_match.group(0) if card_match else ""
dialog_code = dialog_match.group(0) if dialog_match else ""

# Also extract the HubCard definition and SectionHeader/EmptyState if needed
# Wait, SectionHeader and EmptyState might be used by other parts in AcademicsScreen. 
# We'll leave them in AcademicsScreen.
# Wait, TimetableCard and TimetableDialog don't rely on AcademicsScreen-specific stuff except AmazeTheme.

# 3. Remove Timetable Section and Calendar Section from AcademicsScreen
# Find the start of Timetable Section
start_timetable = acad_content.find('            // ✨ Timetable Section ✨')
if start_timetable == -1:
    start_timetable = acad_content.find('            // ✨ Timetable Section ✨') # Maybe encoding issue? Let's just use regex.

# Let's use a simpler replacement for AcademicsScreen:
# Just remove everything between Hub grid and Spacer before TimetableDialog call.

new_acad_content = acad_content

# Remove Timetable Section
timetable_sec_regex = re.compile(r'\s*// ✨ Timetable Section ✨.*?// ✨ Academic Calendar Section ✨', re.DOTALL)
new_acad_content = timetable_sec_regex.sub('\n\n            // ✨ Academic Calendar Section ✨', new_acad_content)

# Remove Academic Calendar Section
calendar_sec_regex = re.compile(r'\s*// ✨ Academic Calendar Section ✨.*?(?=\s*item \{ Spacer\(modifier = Modifier\.height\(16\.dp\)\) \})', re.DOTALL)
new_acad_content = calendar_sec_regex.sub('', new_acad_content)

# Remove TimetableDialog call
dialog_call_regex = re.compile(r'\s*if \(showTimetableDialog\) \{.*?\n\s*\}', re.DOTALL)
new_acad_content = dialog_call_regex.sub('', new_acad_content)

# Remove showTimetableDialog variable
var_regex = re.compile(r'\s*var showTimetableDialog by remember \{ mutableStateOf\(false\) \}')
new_acad_content = var_regex.sub('', new_acad_content)

# Remove TimetableCard and TimetableDialog definitions
new_acad_content = new_acad_content.replace(card_code, '')
new_acad_content = new_acad_content.replace(dialog_code, '')

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_acad_content)

# 4. Inject TimetableCard, TimetableDialog, and the display logic into AttendanceScreen.kt
# We'll append TimetableCard and TimetableDialog at the very end of AttendanceScreen.kt
if card_code not in attn_content:
    # Make TimetableCard public so it can be used if needed, or just remove 'private'
    card_code = card_code.replace('private fun TimetableCard', 'fun TimetableCard')
    attn_content += '\n\n' + card_code + '\n\n' + dialog_code

# Inject the display logic into TimetableGridScreen
# TimetableGridScreen has: ar selectedDay by remember { mutableStateOf<String?>(null) }
# We'll add ar showTimetableDialog by remember { mutableStateOf(false) } inside TimetableGridScreen.
# And inside the Column, maybe add the "Full Week" button and the list of TimetableCards when selectedDay == null.

grid_screen_def = '    var selectedDay by remember { mutableStateOf<String?>(null) }'
grid_screen_new = grid_screen_def + '\n    var showTimetableDialog by remember { mutableStateOf(false) }'
attn_content = attn_content.replace(grid_screen_def, grid_screen_new)

# Add the dialog call at the end of TimetableGridScreen
end_grid_screen = '                }\n            }\n        }\n    }\n}\n'
end_grid_screen_new = """                }
            }
        }
    }
    
    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }
}
"""
# This might be brittle. Let's just replace the whole TimetableGridScreen with a python script that inserts the schedule list where "All" is selected.

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'w', encoding='utf-8') as f:
    f.write(attn_content)

print("Script executed successfully.")
