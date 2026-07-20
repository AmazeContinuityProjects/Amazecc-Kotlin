import re

# Fix GPAPredictorScreen
with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/GPAPredictorScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'val today = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())',
    'val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date'
)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/GPAPredictorScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# Fix CurriculumScreen
with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CurriculumScreen.kt', 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'EmptyState("No curriculum data found. Tap refresh to sync.", modifier = Modifier.padding(top = 40.dp))',
    'Text("No curriculum data found. Tap refresh to sync.", color = colors.textSecondary, modifier = Modifier.padding(top = 40.dp))'
)
content2 = content2.replace(
    'Icons.Rounded.Download',
    'androidx.compose.material.icons.rounded.FileDownload'
)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CurriculumScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content2)

print("Fixed compile errors")
