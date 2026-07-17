with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'showTimetableDialog' in line or 'TimetableGridScreen' in line:
        print(f"{i+1}: {line.strip()}")
