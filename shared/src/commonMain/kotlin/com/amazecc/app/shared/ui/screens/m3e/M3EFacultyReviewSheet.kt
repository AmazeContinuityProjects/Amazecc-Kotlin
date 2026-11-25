package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.utils.toImageBitmap
import kotlin.math.roundToInt

internal const val FACULTY_REVIEW_OVERLAY_ID = "vitfac-review-sheet"

@OptIn(ExperimentalLayoutApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
@Composable
fun M3EFacultyReviewSheet(
    faculty: M3EFacultyMember,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val uriHandler = LocalUriHandler.current
    val empId = faculty.employeeId.orEmpty()

    var detail by remember(empId) { mutableStateOf<AmazeClient.VitfacProfileDetail?>(null) }
    var loading by remember(empId) { mutableStateOf(empId.isNotBlank()) }
    var activeCourseFilter by remember(empId) { mutableStateOf("global") }
    var avatarBitmap by remember(faculty.imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(faculty.imageUrl) {
        val url = faculty.imageUrl
        if (!url.isNullOrBlank()) {
            avatarBitmap = AmazeClient.getImageBytes(url)?.toImageBitmap()
        }
    }

    LaunchedEffect(empId) {
        if (empId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        detail = AmazeClient.getVitfacProfile(empId)
        loading = false
    }

    val b64Id = detail?.b64Id?.takeIf { it.isNotBlank() }
        ?: if (empId.isNotBlank()) kotlin.io.encoding.Base64.encode(empId.encodeToByteArray()) else ""
    val profileUrl = if (b64Id.isNotBlank()) "https://vitfac.pages.dev/faculty/$b64Id" else "https://vitfac.pages.dev"

    val selectedCourseStat = remember(detail, activeCourseFilter) {
        if (activeCourseFilter == "global") null
        else detail?.courseStats?.firstOrNull { it.code.equals(activeCourseFilter, ignoreCase = true) }
    }

    val wPct = selectedCourseStat?.wPct ?: detail?.globalWPct ?: detail?.wPct ?: faculty.wPct ?: 0
    val reviewsCount = selectedCourseStat?.reviews ?: detail?.globalReviews ?: detail?.reviews ?: faculty.reviewCount
    val wCount = if (selectedCourseStat == null && detail != null && detail!!.wCount + detail!!.lCount > 0) {
        detail!!.wCount
    } else {
        ((wPct / 100.0) * reviewsCount).roundToInt()
    }
    val lCount = (reviewsCount - wCount).coerceAtLeast(0)

    val lecture = selectedCourseStat?.lecture ?: detail?.lecture
    val da = selectedCourseStat?.da ?: detail?.da
    val vibe = selectedCourseStat?.vibe ?: detail?.vibe

    val greenLore = selectedCourseStat?.greenLore ?: detail?.greenLore.orEmpty()
    val redLore = selectedCourseStat?.redLore ?: detail?.redLore.orEmpty()

    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = onDismiss,
        modifier = modifier,
        title = "vitfac Reviews",
        overlayId = FACULTY_REVIEW_OVERLAY_ID
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Avatar + Name + Designation/Dept + vitfac link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val initials = faculty.name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("")
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val bm = avatarBitmap
                        if (bm != null) {
                            Image(
                                bitmap = bm,
                                contentDescription = faculty.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = initials.ifBlank { "F" },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = detail?.name?.takeIf { it.isNotBlank() } ?: faculty.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        val sub = listOfNotNull(
                            (detail?.designation?.takeIf { it.isNotBlank() } ?: faculty.designation).takeIf { it.isNotBlank() },
                            (detail?.dept?.takeIf { it.isNotBlank() } ?: faculty.school.takeIf { it != "All" })?.takeIf { it.isNotBlank() },
                            empId.takeIf { it.isNotBlank() }?.let { "ERP #$it" }
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(text = sub, fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(profileUrl) } },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("vitfac", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))

            if (loading && detail == null && reviewsCount == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.primary)
                    Text("Loading faculty reviews…", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            } else if (reviewsCount == 0) {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("No reviews yet on vitfac", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(
                            text = "This faculty has not received any reviews yet on vitfac.pages.dev.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Course Filter Bar
                val courseList = detail?.courseStats.orEmpty()
                if (courseList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "FILTER BY COURSE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            letterSpacing = 0.7.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = activeCourseFilter == "global",
                                onClick = { activeCourseFilter = "global" },
                                label = { Text("All (${detail?.globalReviews ?: reviewsCount})", fontSize = 11.sp) }
                            )
                            courseList.forEach { c ->
                                FilterChip(
                                    selected = activeCourseFilter.equals(c.code, ignoreCase = true),
                                    onClick = { activeCourseFilter = c.code },
                                    label = { Text("${c.code} (${c.reviews})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Community Rating Hero
                val ratingColor = when {
                    wPct >= 70 -> colors.success
                    wPct >= 40 -> colors.warning
                    else -> colors.error
                }
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMMUNITY RATING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurfaceVariant,
                                letterSpacing = 0.7.sp
                            )
                            Text(
                                text = "$reviewsCount ${if (reviewsCount == 1) "student review" else "student reviews"}",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "$wPct%",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ratingColor
                                )
                                Text(
                                    text = "Win Rate",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                                    Text("$wCount W", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.success)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.ThumbDown, contentDescription = null, tint = colors.error, modifier = Modifier.size(14.dp))
                                    Text("$lCount L", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.error)
                                }
                            }
                        }

                        // Split bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(colors.error.copy(alpha = 0.25f))
                        ) {
                            if (wPct > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (wPct / 100f).coerceIn(0f, 1f))
                                        .height(8.dp)
                                        .background(colors.success)
                                )
                            }
                        }
                    }
                }

                // 3 Metric Gauges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("LECTURE", lecture, Icons.Rounded.Book),
                        Triple("DA MARKS", da, Icons.Rounded.Description),
                        Triple("VIBE", vibe, Icons.Rounded.Mood)
                    ).forEach { (label, score, icon) ->
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 12.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = score?.let { "${it.toFixed(1)}/5" } ?: "—",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Green Flags
                if (greenLore.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                            Text("GREEN FLAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.success, letterSpacing = 0.7.sp)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            greenLore.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.success.copy(alpha = 0.12f))
                                        .border(1.dp, colors.success.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${item.text} (+${item.count})", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.success)
                                }
                            }
                        }
                    }
                }

                // Red Flags
                if (redLore.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(14.dp))
                            Text("RED FLAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.error, letterSpacing = 0.7.sp)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            redLore.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.error.copy(alpha = 0.12f))
                                        .border(1.dp, colors.error.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${item.text} (+${item.count})", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.error)
                                }
                            }
                        }
                    }
                }

                // Known Courses
                val knownCourses = detail?.knownCourses.orEmpty().ifEmpty { faculty.coursesTaught }
                if (knownCourses.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("KNOWN COURSES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, letterSpacing = 0.7.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            knownCourses.take(12).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surfaceContainerHighest)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))

            // Footer CTA & disclaimer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.success.copy(alpha = 0.12f))
                    .border(1.dp, colors.success.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { runCatching { uriHandler.openUri(profileUrl) } }
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                    Text("Leave a Review on vitfac.pages.dev", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.success)
                }
            }

            Text(
                text = "Disclaimer: Ratings and reviews are sourced from vitfac.pages.dev. AmazeCC does not create, endorse, or verify these opinions.",
                fontSize = 10.sp,
                color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }
    }
}
