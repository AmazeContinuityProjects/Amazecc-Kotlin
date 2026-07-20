import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CurriculumScreen.kt', 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'androidx.compose.material.icons.Icons.Rounded.ArrowDownward',
    'androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown'
)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CurriculumScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content2)

print("Fixed compile errors v3")
