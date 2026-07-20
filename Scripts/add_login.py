import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/FreeClassroomsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Check if we already imported SessionManager and AppState
if 'import com.amazecc.app.shared.repository.SessionManager' not in content:
    content = content.replace(
        'import com.amazecc.app.shared.theme.AmazeTheme',
        'import com.amazecc.app.shared.repository.SessionManager\nimport com.amazecc.app.shared.state.AppState\nimport com.amazecc.app.shared.state.Screen\nimport com.amazecc.app.shared.theme.AmazeTheme'
    )

# Add login check at the top of the Composable
old_code = "val colors = AmazeTheme.colors"
new_code = """val colors = AmazeTheme.colors
    val authId by SessionManager.authorizedID.collectAsState()
    
    if (authId == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Free Classrooms", description = "Find an empty spot to sit", showBackButton = true)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Login Required", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(8.dp))
                    Text("Please login to VTOP to view free classrooms and your timetable slots.", color = colors.textSecondary, modifier = Modifier.padding(bottom = 24.dp))
                    Button(onClick = { AppState.navigateTo(Screen.LOGIN) }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                        Text("Go to Login", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }"""

content = content.replace(old_code, new_code)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/FreeClassroomsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
