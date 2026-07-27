package com.amazecc.app.shared.ffcs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun CourseSearchPanel(
    allCourses: List<Pair<String, List<CourseOffering>>>, // (courseCode, offerings)
    selectedCodes: Set<String>,
    onToggleCourse: (String) -> Unit,
    onSelectOffering: (String, CourseOffering) -> Unit,
    blockedSlots: Set<String>,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    var search by remember { mutableStateOf("") }
    var expandedCourse by remember { mutableStateOf<String?>(null) }

    val filtered = remember(allCourses, search) {
        if (search.isBlank()) allCourses
        else allCourses.filter { (code, offerings) ->
            code.contains(search, ignoreCase = true) ||
            offerings.any { it.title.contains(search, ignoreCase = true) }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Search by code or title...",
                    style = AmazeTheme.typography.body.copy(fontSize = 13.sp, color = colors.textMuted))
            },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent.copy(alpha = 0.5f),
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accent
            )
        )

        Spacer(Modifier.height(8.dp))

        if (selectedCodes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.08f)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${selectedCodes.size} course(s) selected",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered, key = { it.first }) { (code, offerings) ->
                val isSelected = selectedCodes.contains(code)
                val isExpanded = expandedCourse == code
                val title = offerings.firstOrNull()?.title ?: ""

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.accent.copy(alpha = 0.06f) else colors.surface)
                        .border(1.dp, if (isSelected) colors.accent.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedCourse = if (isExpanded) null else code }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleCourse(code) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accent,
                                uncheckedColor = colors.border
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                code,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                title,
                                style = AmazeTheme.typography.caption.copy(
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            "${offerings.size} offerings",
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontSize = 9.sp,
                                color = colors.textMuted
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isExpanded) {
                        offerings.forEach { offering ->
                            val offeringKey = offering.toKey()
                            val isSlotBlocked = blockedSlots.any { bs ->
                                bs.split("|").getOrNull(1) == offering.slot
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectOffering(code, offering) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (isSlotBlocked) colors.danger else colors.accent.copy(alpha = 0.6f)
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    offering.faculty,
                                    style = AmazeTheme.typography.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary
                                    ),
                                    modifier = Modifier.width(120.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    offering.slot,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        fontSize = 10.sp,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    offering.room,
                                    style = AmazeTheme.typography.smallLabel.copy(
                                        fontSize = 9.sp,
                                        color = colors.textMuted
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
