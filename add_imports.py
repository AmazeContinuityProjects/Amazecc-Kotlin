import re
with open(r'shared\src\commonMain\kotlin\com\amazecc\app\shared\ui\screens\academics\AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

imports = [
    'import androidx.compose.foundation.rememberScrollState',
    'import androidx.compose.foundation.verticalScroll'
]

for imp in imports:
    if imp not in content:
        content = re.sub(r'(import androidx\.compose\..*?\n)', rf'\1{imp}\n', content, count=1)

with open(r'shared\src\commonMain\kotlin\com\amazecc\app\shared\ui\screens\academics\AttendanceScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
