import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add showTimetableDialog variable
grid_screen_def = '    var selectedDay by remember { mutableStateOf<String?>(null) }'
grid_screen_new = grid_screen_def + '\n    var showTimetableDialog by remember { mutableStateOf(false) }'
content = content.replace(grid_screen_def, grid_screen_new)

# 2. Update the Day selector row to include the Schedule button
day_selector = """        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {"""
day_selector_new = """        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {"""
content = content.replace(day_selector, day_selector_new)

end_day_selector = """                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { selectedDay = if (isSelected) null else day }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        day,
                        color = if (isSelected) Color.White else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }"""
end_day_selector_new = """                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { selectedDay = if (isSelected) null else day }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        day,
                        color = if (isSelected) Color.White else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = { showTimetableDialog = true }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.CalendarViewWeek, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Schedule", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }
        }"""
content = content.replace(end_day_selector, end_day_selector_new)

# 3. Add TimetableDialog at the very end of TimetableGridScreen
end_pattern = re.compile(r'                }\n            }\n        }\n    }\n}', re.MULTILINE)
match = end_pattern.search(content)

dialog_call = """    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }"""

if match:
    content = content[:match.end()] + '\n\n' + dialog_call + '\n' + content[match.end():]

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated AttendanceScreen.kt")
