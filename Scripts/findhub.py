import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/DashboardScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# find all HubItem
matches = re.findall(r'HubItem\([^)]+\)', content)
for m in matches:
    print(m)
