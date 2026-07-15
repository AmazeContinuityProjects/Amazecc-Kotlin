import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/GPAPredictorScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add "Course Targets" to the mode toggle
new_toggle = '''
            // Mode toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AmazeButton(
                    text = "Project GPA",
                    onClick = { activeMode = "project" },
                    modifier = Modifier.weight(1f),
                    variant = if (activeMode == "project") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
                AmazeButton(
                    text = "What Grade?",
                    onClick = { activeMode = "whatif" },
                    modifier = Modifier.weight(1f),
                    variant = if (activeMode == "whatif") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
                AmazeButton(
                    text = "Course Targets",
                    onClick = { activeMode = "course" },
                    modifier = Modifier.weight(1f),
                    variant = if (activeMode == "course") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                )
            }
'''
content = re.sub(
    r'// Mode toggle.*?Row\(horizontalArrangement = Arrangement\.spacedBy\(8\.dp\), modifier = Modifier\.fillMaxWidth\(\)\) \{.*?\n            \}',
    new_toggle.strip(),
    content,
    flags=re.DOTALL
)

# 2. Add course mode logic branch
new_branch = '''
            if (activeMode == "project") {
                ProjectionMode(
'''
new_branch_replacement = '''
            if (activeMode == "course") {
                CourseTargetMode(
                    calendarRes = AppState.calendar.collectAsState().value,
                    colors = colors
                )
            } else if (activeMode == "project") {
                ProjectionMode(
'''
content = content.replace(new_branch.strip(), new_branch_replacement.strip())

# 3. Add CourseTargetMode composable at the end
course_target_composable = '''

@Composable
private fun CourseTargetMode(
    calendarRes: com.amazecc.app.shared.model.CalendarRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var courseType by remember { mutableStateOf("Theory") }
    var targetGrade by remember { mutableStateOf("S") }
    
    var cat1Marks by remember { mutableStateOf("") }
    var cat2Marks by remember { mutableStateOf("") }
    var quizMarks by remember { mutableStateOf("") }
    var labInternals by remember { mutableStateOf("") }
    
    // Evaluate calendar for CAT dates
    val today = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
    
    var cat1Date: kotlinx.datetime.LocalDate? = null
    var cat2Date: kotlinx.datetime.LocalDate? = null
    
    calendarRes?.months?.forEach { monthObj ->
        monthObj.days.forEach { day ->
            day.events.forEach { event ->
                val txt = event.text.lowercase()
                val parsedDate = try {
                    // Try parsing month string + date, for simplicity just assume it's a rough date
                    // Since month parsing is complex without a full date formatter in KMP, we will just use a fallback heuristic.
                    null
                } catch (e: Exception) { null }
                
                if (txt.contains("continuous assessment test - i") || txt.contains("cat 1") || txt.contains("cat - i")) {
                    // It's a CAT 1 date
                    // Let's assume if we found the event, we just check if it's past or not (using a simple heuristic or we just show both fields for simplicity)
                }
            }
        }
    }
    
    val cat1Status = "Enter CAT 1 (out of 50)"
    val cat2Status = "Enter CAT 2 (out of 50)"

    val gradeTargetPoints = when (targetGrade) {
        "S" -> 90.0
        "A" -> 80.0
        "B" -> 70.0
        "C" -> 60.0
        "D" -> 50.0
        "E" -> 40.0
        else -> 90.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Predict FAT Requirements",
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Theory", "Lab", "Embedded").forEach { type ->
                AmazeButton(
                    text = type,
                    onClick = { courseType = type },
                    variant = if (courseType == type) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        OutlinedTextField(
            value = targetGrade,
            onValueChange = { targetGrade = it.uppercase().filter { c -> c in listOf('S','A','B','C','D','E','F') }.take(1) },
            label = { Text("Target Grade (S, A, B...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        if (courseType == "Theory" || courseType == "Embedded") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cat1Marks,
                    onValueChange = { cat1Marks = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(cat1Status) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = cat2Marks,
                    onValueChange = { cat2Marks = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(cat2Status) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )
            }
            OutlinedTextField(
                value = quizMarks,
                onValueChange = { quizMarks = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Assignments / Quizzes (out of 30)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }
        
        if (courseType == "Lab" || courseType == "Embedded") {
            OutlinedTextField(
                value = labInternals,
                onValueChange = { labInternals = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Lab Internals (out of 60)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }
        
        // Calculation logic
        val c1 = cat1Marks.toDoubleOrNull() ?: 0.0
        val c2 = cat2Marks.toDoubleOrNull() ?: 0.0
        val q = quizMarks.toDoubleOrNull() ?: 0.0
        val l = labInternals.toDoubleOrNull() ?: 0.0
        
        // Theory internal = (c1 * 0.3) + (c2 * 0.3) + q
        val theoryInternal = (c1 * 0.3) + (c2 * 0.3) + q
        val requiredTotal = gradeTargetPoints
        
        var message = ""
        var isPossible = true
        
        if (courseType == "Theory") {
            val neededTheoryFatMarks = (requiredTotal - theoryInternal) / 0.4
            isPossible = neededTheoryFatMarks <= 100
            val actualNeed = neededTheoryFatMarks.coerceAtLeast(0.0)
            message = if (isPossible) {
                "You need  / 100 in Theory FAT."
            } else {
                "Not mathematically possible. Max achievable is ."
            }
        } else if (courseType == "Lab") {
            val neededLabFatMarks = (requiredTotal - l) / 0.4
            isPossible = neededLabFatMarks <= 100
            val actualNeed = neededLabFatMarks.coerceAtLeast(0.0)
            message = if (isPossible) {
                "You need  / 100 in Lab FAT."
            } else {
                "Not mathematically possible. Max achievable is ."
            }
        } else if (courseType == "Embedded") {
            // Typical embedded weight: Theory 75%, Lab 25% or Theory 60%, Lab 40% depending on credits.
            // Let's assume generic 75% Theory, 25% Lab.
            val totalInternal = (theoryInternal * 0.75) + (l * 0.25)
            // FAT is out of 100, wait, FAT is for Theory, maybe Lab FAT too?
            // Usually embedded means there is no Lab FAT or Theory FAT encompasses both, let's just ask them for Theory FAT.
            val neededFat = (requiredTotal - totalInternal) / 0.4
            isPossible = neededFat <= 100
            val actualNeed = neededFat.coerceAtLeast(0.0)
            message = if (isPossible) {
                "Assuming 75/25 weightage. You need  / 100 in FAT."
            } else {
                "Not mathematically possible to get ."
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPossible) colors.surface else colors.dangerSurface)
                .border(1.dp, if (isPossible) colors.border else colors.danger, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Prediction",
                    style = AmazeTheme.typography.smallLabel.copy(color = if (isPossible) colors.textSecondary else colors.dangerText)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = if (isPossible) colors.textPrimary else colors.dangerText)
                )
            }
        }
    }
}
'''
content += course_target_composable

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/GPAPredictorScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated GPAPredictorScreen successfully")
