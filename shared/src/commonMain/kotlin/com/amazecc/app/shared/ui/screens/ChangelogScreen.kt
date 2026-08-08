package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.ScreenHeader

val changelogEntries = listOf(
    "Phase 3: 15 new features including QBank, Faculty Info, Course Management, Projects, Wishlist, Feedback, Documents, Activity Tree, Spotlight Search, and more",
    "Phase 2: Hostel (Mess/Laundry/Counselling), Transport, CabShare, Events, Social modules",
    "Phase 1: Attendance Predictor, Arrear Management, Circulars, Curriculum, OD Tracker",
    "Phase 0: Foundation with Settings, Profile, Grades, GPA Predictor",
    "Initial release with Attendance, Timetable, Academic Calendar, Libraries, Payments"
)

@Composable
fun ChangelogScreen() {
    val colors = AmazeTheme.colors
    val changes = changelogEntries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Changelog",
            description = "What's new in AmazeCC",
            showBackButton = true,
            showSyncButton = false
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSpacer() }
            items(changes.size, key = { it }) { index ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
                        Column {
                            Text(
                                text = "Update ${changes.size - index}",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                text = changes[index],
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, lineHeight = 18.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}
