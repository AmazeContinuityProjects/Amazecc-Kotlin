import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/TimetableComponents.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('CourseInfoItem', 'CourseItem')

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/TimetableComponents.kt', 'w', encoding='utf-8') as f:
    f.write(content)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AttendanceScreen.kt', 'a', encoding='utf-8') as f:
    f.write('\n}\n')

print("Fixed CourseItem and AttendanceScreen missing bracket")
