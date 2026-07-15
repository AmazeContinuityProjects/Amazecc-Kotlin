with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/DashboardScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()
    for i, line in enumerate(lines):
        if 'free' in line.lower():
            print(f"Line {i+1}: {line.strip()}")
