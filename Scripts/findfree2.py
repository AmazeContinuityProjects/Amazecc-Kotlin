with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/DashboardScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

with open('free_lines.txt', 'w', encoding='utf-8') as fw:
    for i, line in enumerate(lines):
        if 'free' in line.lower():
            fw.write(f"Line {i+1}: {line.strip()}\n")
