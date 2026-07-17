import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/AcademicsScreen.kt', 'r', encoding='utf-8') as f:
    acad_content = f.read()

# Extract components
card_pattern = re.compile(r'@Composable\s+private fun TimetableCard.*?^}', re.MULTILINE | re.DOTALL)
dialog_pattern = re.compile(r'@Composable\s+fun TimetableDialog.*?^}', re.MULTILINE | re.DOTALL)

card_code = card_pattern.search(acad_content).group(0)
dialog_code = dialog_pattern.search(acad_content).group(0)

# Make TimetableCard public
card_code = card_code.replace("private fun TimetableCard", "fun TimetableCard")

new_file_content = """package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.CourseInfoItem
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard

""" + card_code + "\n\n" + dialog_code

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/TimetableComponents.kt', 'w', encoding='utf-8') as f:
    f.write(new_file_content)

print("Created TimetableComponents.kt")
