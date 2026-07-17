import re

with open('old_attn.kt', 'r', encoding='utf-16') as f:
    old_content = f.read()

# Extract lines from fun OverallPredictorScreen() down to the line before fun TimetableGridScreen()
match = re.search(r'(fun OverallPredictorScreen\(\) \{.*?)(?=\n@Composable\nfun TimetableGridScreen\(\)|\nfun TimetableGridScreen\(\))', old_content, re.DOTALL)
if match:
    old_predictor_code = match.group(1)
else:
    print("Could not find old predictor code")
    exit(1)

# Apply fixes to old_predictor_code
# 1. Add verticalScroll to the main Column
old_predictor_code = old_predictor_code.replace(
    'Column(modifier = Modifier.fillMaxSize()) {',
    'val scrollState = rememberScrollState()\n    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 16.dp)) {'
)

# 2. Replace LazyColumn with Column
lazy_col_pattern = r'LazyColumn\([\s\S]*?modifier = Modifier\.weight\(1f\)[\s\S]*?verticalArrangement = Arrangement\.spacedBy\(8\.dp\)\n\s*\) \{\n\s*items\(predictions\) \{ pred ->'
new_col_replacement = '''Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            predictions.forEach { pred ->'''
old_predictor_code = re.sub(lazy_col_pattern, new_col_replacement, old_predictor_code)

# 3. Add venue to mapOf
venue_pattern = r'("attendancePercentage" to item\.attendancePercentage)\n\s*\)'
venue_repl = r'\1,\n                    "venue" to (item.slotVenue ?: "")\n                )'
old_predictor_code = re.sub(venue_pattern, venue_repl, old_predictor_code)

# Now read current AttendanceScreen.kt
with open(r'shared\src\commonMain\kotlin\com\amazecc\app\shared\ui\screens\academics\AttendanceScreen.kt', 'r', encoding='utf-8') as f:
    current_content = f.read()

# Replace current OverallPredictorScreen with old_predictor_code
current_match = re.search(r'(fun OverallPredictorScreen\(\) \{.*?)(?=\n@Composable\nfun TimetableGridScreen\(\)|\nfun TimetableGridScreen\(\))', current_content, re.DOTALL)
if current_match:
    new_content = current_content[:current_match.start()] + old_predictor_code + current_content[current_match.end():]
    with open(r'shared\src\commonMain\kotlin\com\amazecc\app\shared\ui\screens\academics\AttendanceScreen.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Successfully patched AttendanceScreen.kt")
else:
    print("Could not find current predictor code")
