package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.CardVariant
import com.amazecc.app.shared.ui.components.BunkOMeterCard
import com.amazecc.app.shared.ui.components.CommandPalette
import com.amazecc.app.shared.ui.components.UpdateDialog
import com.amazecc.app.shared.ui.components.UpdateResultDialog
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.screens.academics.AddTaskDialog
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.parseViewLink
import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.ktor.util.decodeBase64Bytes
import com.amazecc.app.shared.utils.toImageBitmap

@Composable
fun DashboardScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val spacing = AmazeTheme.spacing
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val profile by AppState.studentProfile.collectAsState()
    val profileImages by AppState.profileImages.collectAsState()

    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()

    val updateStatus by AppState.updateStatus.collectAsState()
    var showManualUpdateResult by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LaunchedEffect(Unit) {
        AppState.checkForUpdate()
    }

    when (val status = updateStatus) {
        is AppState.UpdateStatus.Available -> {
            UpdateDialog(
                release = status.release,
                currentVersion = status.currentVersion,
                onDismiss = { AppState.dismissUpdateDialog() },
                onDownload = {
                    AppState.dismissUpdateDialog()
                    uriHandler.openUri(status.release.htmlUrl)
                }
            )
        }
        is AppState.UpdateStatus.UpToDate -> {
            if (showManualUpdateResult) {
                UpdateResultDialog(
                    status = status,
                    onDismiss = { showManualUpdateResult = false; AppState.checkForUpdate() }
                ) { }
            }
        }
        is AppState.UpdateStatus.Error -> {
            if (showManualUpdateResult) {
                UpdateResultDialog(
                    status = status,
                    onDismiss = { showManualUpdateResult = false; AppState.checkForUpdate() }
                ) { }
            }
        }
        else -> {}
    }

    val courses = attendanceRes?.attendance ?: emptyList()
    val allCourses = remember(allSemesterAttendance, courses) {
        val semesterCourses = allSemesterAttendance.values
            .filterNotNull()
            .flatMap { it.attendance.orEmpty() }
        courses + semesterCourses
    }

    val slotMapTyped = remember {
        SlotMap.map.mapValues { (_, inner) ->
            inner.mapValues { (_, time) -> SlotInfo(time) }
        }
    }
    val calendarRes by AppState.calendar.collectAsState()
    val todayClasses = remember(courses, calendarRes) {
        AttendanceTimetable.getTodayAttendanceClasses(
            attendance = courses.map { item ->
                mapOf(
                    "courseCode" to item.courseCode,
                    "courseTitle" to item.courseTitle,
                    "courseType" to item.courseType,
                    "faculty" to item.faculty,
                    "slotName" to (item.slotName ?: ""),
                    "attendancePercentage" to item.attendancePercentage,
                    "venue" to (item.slotVenue ?: "")
                )
            },
            slotMap = slotMapTyped,
            calendar = calendarRes
        )
    }

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.minutes)
            tick++
        }
    }

    val currentClass = remember(todayClasses, tick) {
        AttendanceTimetable.findCurrentClass(todayClasses)
    }
    val nextClass = remember(todayClasses, tick) {
        AttendanceTimetable.findNextClass(todayClasses)
    }

    val todayDate = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val dayOrderOverride = remember(todayDate, calendarRes) {
        AttendanceTimetable.getDayOrderOverrideForDate(todayDate, calendarRes)
    }

    val overallAttendance = remember(courses) {
        if (courses.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in courses) {
                totalAtt += item.attendedClasses
                totalCls += item.totalClasses
            }
            if (totalCls == 0) 0f else (totalAtt.toFloat() / totalCls.toFloat()) * 100f
        }
    }

    val isCgpaHidden by AppState.cgpaHidden.collectAsState()
    val rawCgpa = marksRes?.cgpa?.cgpa ?: "—"
    val cgpa = if (isCgpaHidden) "•••" else rawCgpa
    val credits = marksRes?.cgpa?.creditsEarned ?: "—"

    val avatarText = (profile?.name ?: authorizedID ?: "U").take(2).uppercase()
    val nameToDisplay = remember(profile?.name, authorizedID) {
        val n = profile?.name
        if (n.isNullOrBlank() || n.equals(authorizedID, ignoreCase = true)) ""
        else n.split(" ").firstOrNull { it.isNotBlank() }?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
    }

    var showCommandPalette by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.pageHorizontal)
                .padding(bottom = BOTTOM_NAV_PADDING)
        ) {
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(spacing.lg))

            // ── Profile Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .border(1.5.dp, colors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val photoBase64 = profile?.photoBase64 
                        ?: profileImages?.student?.photoBase64 
                        ?: profileImages?.profile?.photoBase64 
                        ?: profileImages?.studentPhoto
                    val decodedBitmap = remember(photoBase64) {
                        if (photoBase64 != null) {
                            try {
                                val cleanBase64 = photoBase64.substringAfter("base64,")
                                    .replace("\n", "")
                                    .replace("\r", "")
                                    .replace(" ", "")
                                cleanBase64.decodeBase64Bytes().toImageBitmap()
                            } catch (e: Exception) { null }
                        } else null
                    }

                    if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = decodedBitmap,
                            contentDescription = "Profile Image",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = avatarText,
                            style = AmazeTheme.typography.subheading.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Good ${getGreeting()}",
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.textMuted,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nameToDisplay.ifEmpty { "Student" },
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        SyncEngine.setShowSyncDialog(true)
                        AppState.loadAllData()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, CircleShape)
                ) {
                    val isSyncing by AppState.isLoading.collectAsState()
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Sync All Data",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                IconButton(
                    onClick = { showCommandPalette = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = Strings.search,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // ── Metric Cards Row ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    GlassMetricCard(
                        "CGPA", cgpa, if (isCgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Star, colors,
                        iconTint = if (isCgpaHidden) colors.textMuted else colors.warning,
                        surfaceBg = colors.warningSurface,
                        onClick = { AppState.setCgpaHidden(!isCgpaHidden) }
                    )
                }
                item {
                    GlassMetricCard(
                        "Credits", credits, Icons.Rounded.Info, colors,
                        iconTint = colors.info, surfaceBg = colors.infoSurface,
                        onClick = { AppState.navigateTo(Screen.PAYMENTS) }
                    )
                }
                item {
                    val odCount = remember(courses) {
                        val odDates = mutableSetOf<String>()
                        for (course in courses) {
                            try {
                                val arr = parseViewLink(course.viewLinkRaw)?.jsonArray
                                arr?.forEach { elem ->
                                    val obj = elem.jsonObject
                                    val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                                    if (status.equals("On Duty", ignoreCase = true)) {
                                        odDates.add(date)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        odDates.size
                    }
                    GlassMetricCard(
                        "ODs", if (courses.isNotEmpty()) "$odCount" else "—", Icons.Rounded.CheckCircle, colors,
                        iconTint = colors.success, surfaceBg = colors.successSurface,
                        onClick = { AppState.navigateTo(Screen.OD_TRACKER) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))

            val animatedAttendance by animateFloatAsState(
                targetValue = overallAttendance / 100f,
                animationSpec = tween(1500)
            )

            // ── Combined Attendance & Bunk-O-Meter Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.large))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(radius.large))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(88.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { animatedAttendance },
                                modifier = Modifier.fillMaxSize(),
                                color = if (overallAttendance >= 75f) colors.success
                                else if (overallAttendance >= 50f) colors.warning
                                else colors.danger,
                                trackColor = colors.border,
                                strokeWidth = 8.dp
                            )
                            Text(
                                text = "${overallAttendance.toInt()}%",
                                style = AmazeTheme.typography.subheading.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontSize = 16.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overall Attendance",
                                style = AmazeTheme.typography.body.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                text = if (overallAttendance >= 75f) "You're on track!"
                                else if (overallAttendance >= 50f) "Needs improvement!"
                                else "Critical!",
                                style = AmazeTheme.typography.caption.copy(
                                    color = if (overallAttendance >= 75f) colors.success
                                    else if (overallAttendance >= 50f) colors.warning
                                    else colors.danger,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            AmazeButton(
                                text = "Predict Attendance",
                                onClick = { AppState.openAttendanceView("Predictor") },
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                    BunkOMeterCard(
                        attendance = attendanceRes,
                        modifier = Modifier.fillMaxWidth(),
                        isInnerCard = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // ── Today's Classes ──

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(radius.xs))
                            .background(colors.accentSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.School, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column {
                        Text(
                            text = "Today's Classes",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        if (dayOrderOverride != null) {
                            Text(
                                text = "⚡ ${dayOrderOverride.name} Day Order",
                                style = AmazeTheme.typography.caption.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
                if (todayClasses.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.xs))
                            .background(colors.accentSurface)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${todayClasses.size} classes",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

            if (todayClasses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.large))
                        .background(colors.infoSurface)
                        .border(1.dp, colors.info.copy(alpha = 0.2f), RoundedCornerShape(radius.large))
                        .padding(spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FreeBreakfast, null, tint = colors.info, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Text("No classes today!", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                        Text("Enjoy your day off", style = AmazeTheme.typography.caption.copy(color = colors.info, fontWeight = FontWeight.Bold))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    todayClasses.forEach { cls ->
                        val isCurrent = cls == currentClass
                        val isNext = cls == nextClass
                        val clsIndex = todayClasses.indexOf(cls)
                        val currIndex = todayClasses.indexOf(currentClass)
                        val isPast = currIndex != -1 && clsIndex < currIndex
                        
                        val pct = cls.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                        val cardBg = when {
                            isCurrent -> colors.accentSurface
                            isNext -> colors.infoSurface
                            else -> colors.surface
                        }
                        val cardBorder = when {
                            isCurrent -> colors.accent.copy(alpha = 0.5f)
                            isNext -> colors.info.copy(alpha = 0.3f)
                            else -> colors.border
                        }
                        val stripColor = when {
                            isCurrent -> colors.accent
                            isNext -> colors.info
                            else -> Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (isPast) {
                                        alpha = 0.55f // fade past classes
                                    }
                                }
                                .clip(RoundedCornerShape(radius.medium))
                                .background(cardBg)
                                .then(
                                    if (stripColor != Color.Transparent) {
                                        Modifier.drawBehind {
                                            val stripWidth = 4.dp.toPx()
                                            drawRoundRect(
                                                color = stripColor,
                                                topLeft = Offset(0f, 0f),
                                                size = androidx.compose.ui.geometry.Size(stripWidth, size.height)
                                            )
                                        }
                                    } else Modifier
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = cardBorder,
                                    shape = RoundedCornerShape(radius.medium)
                                )
                                .clickable { cls.courseCode?.let { AppState.openCourseDetail(it) } }
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        val livePulse = rememberInfiniteTransition(label = "livePulse")
                                        val liveBgAlpha by livePulse.animateFloat(
                                            initialValue = 0.10f,
                                            targetValue = 0.30f,
                                            animationSpec = InfiniteRepeatableSpec(
                                                animation = tween(700),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "liveBgAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(colors.danger.copy(alpha = liveBgAlpha))
                                                .border(1.dp, colors.danger.copy(alpha = 0.4f), CircleShape)
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(colors.danger)
                                                )
                                                Text(
                                                    "LIVE",
                                                    style = AmazeTheme.typography.smallLabel.copy(
                                                        color = colors.danger,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(spacing.xs))
                                    }
                                    if (isNext) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(radius.xs))
                                                .background(colors.warning.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "UP NEXT",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.warning,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(spacing.xs))
                                    }
                                    Text(
                                        cls.courseTitle ?: "",
                                        style = AmazeTheme.typography.body.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                     Box(
                                        modifier = Modifier
                                            .background(
                                                if (pct >= 75) colors.success.copy(alpha = 0.12f)
                                                else if (pct >= 50) colors.warning.copy(alpha = 0.12f)
                                                else colors.danger.copy(alpha = 0.12f),
                                                RoundedCornerShape(radius.xs)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "${cls.attendancePercentage ?: "?"}",
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                color = if (pct >= 75) colors.success
                                                else if (pct >= 50) colors.warning
                                                else colors.danger,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(if (isCurrent) 36.dp else 32.dp)
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                            .background(colors.accent)
                                    )
                                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                    Column {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                cls.time,
                                                style = AmazeTheme.typography.caption.copy(
                                                    color = if (isCurrent) colors.accent else colors.textPrimary,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                            val detailsStr = listOfNotNull(
                                                cls.slotName?.takeIf { it.isNotBlank() },
                                                cls.venue?.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ")
                                            if (detailsStr.isNotEmpty()) {
                                                Text(
                                                    detailsStr,
                                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                                )
                                            }
                                        }
                                        if (isCurrent) {
                                            val remaining = remember(tick) {
                                                AttendanceTimetable.remainingMinutes(cls.time)
                                            }
                                            val minsStr = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m" else "${remaining} min"
                                            Text(
                                                "$minsStr left",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.accent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        if (isNext) {
                                            val until = remember(tick) {
                                                AttendanceTimetable.minutesUntil(cls.time)
                                            }
                                            val minsStr = if (until >= 60) "${until / 60}h ${until % 60}m" else "${until} min"
                                            Text(
                                                "Starts in $minsStr",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.warning,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            val safeCount by remember(allCourses) {
                derivedStateOf { allCourses.count { it.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0 >= 85.0 } }
            }
            val warnCount by remember(allCourses) {
                derivedStateOf { allCourses.count { it.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0 in 75.0..84.0 } }
            }
            val critCount by remember(allCourses) {
                derivedStateOf { allCourses.count { it.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0 < 75.0 } }
            }
            val avgCourseAtt by remember(allCourses) {
                derivedStateOf {
                    if (allCourses.isEmpty()) "—"
                    else {
                        val sum = allCourses.sumOf { (it.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0).toInt() }
                        "${sum / allCourses.size}%"
                    }
                }
            }

            // ── Course Attendance ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Course Attendance",
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.xs))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.xs))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${allCourses.size} courses",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

            // ── Course Stats Row ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                val statCards = listOf(
                    Triple("$safeCount", "Safe", colors.chart1),
                    Triple("$warnCount", Strings.warning, colors.chart3),
                    Triple("$critCount", "Critical", colors.chart5),
                    Triple("$avgCourseAtt", "Avg %", colors.chart2)
                )
                statCards.forEach { (value, label, color) ->
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(radius.small)).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(radius.small)).padding(10.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(value, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = color))
                            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = color.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

            if (allCourses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.large))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.large))
                        .padding(spacing.lg)
                ) {
                    Text("No course data available.", color = colors.textSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allCourses.take(4).forEach { course ->
                        ModernCourseCard(course, colors)
                    }
                    if (allCourses.size > 4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(radius.medium))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                                .clickable { AppState.navigateTo(Screen.ATTENDANCE) }
                                .padding(vertical = spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "View All ${allCourses.size} Courses",
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }



            // ── Quick Actions ──
            Text(
                text = "Quick Actions",
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(spacing.sm))

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    GlassActionCard(Modifier.weight(1f), "Predict Att.", Icons.Rounded.CheckCircle, colors, onClick = { AppState.navigateTo(Screen.COURSE_ATTENDANCE) })
                    GlassActionCard(Modifier.weight(1f), "GPA Calc", Icons.Rounded.Star, colors, onClick = { AppState.navigateTo(Screen.GRADES) })
                    GlassActionCard(Modifier.weight(1f), "Quick Task", Icons.Rounded.AddTask, colors, onClick = { showAddTaskDialog = true })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassActionCard(Modifier.weight(1f), "Bus Routes", Icons.Rounded.DirectionsBus, colors, onClick = { AppState.navigateTo(Screen.TRANSPORT) })
                    GlassActionCard(Modifier.weight(1f), "Wishlist", Icons.Rounded.Favorite, colors, onClick = { AppState.navigateTo(Screen.WISHLIST) })
                    GlassActionCard(Modifier.weight(1f), "Curriculum", Icons.AutoMirrored.Rounded.MenuBook, colors, onClick = { AppState.navigateTo(Screen.CURRICULUM) })
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // ── Free Classrooms Widget ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.large))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(radius.large))
                    .clickable { AppState.navigateTo(Screen.FREE_CLASSROOMS) }
                    .padding(spacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(radius.small))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MeetingRoom, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Find Free Classrooms",
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            "Locate an empty spot to sit and study.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xl))
        }
    }

    if (showCommandPalette) {
        AppState.openCommandPalette()
        showCommandPalette = false
    }
    if (showAddTaskDialog) {
        AddTaskDialog(onDismiss = { showAddTaskDialog = false })
    }
}

private fun getGreeting(): String {
    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return when {
        now.hour < 12 -> "Morning"
        now.hour < 17 -> "Afternoon"
        else -> "Evening"
    }
}

@Composable
private fun GlassMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    iconTint: Color = colors.accent,
    surfaceBg: Color = colors.accentSurface,
    onClick: (() -> Unit)? = null
) {
    val radius = AmazeTheme.radius
    val spacing = AmazeTheme.spacing
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.large))
            .background(surfaceBg)
            .border(1.dp, colors.accent.copy(alpha = 0.2f), RoundedCornerShape(radius.large))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    title,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = iconTint,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                value,
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
        }
    }
}

@Composable
private fun ModernCourseCard(
    course: AttendanceItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val percentage = course.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0
    val gradeColor = when {
        percentage >= 85.0 -> colors.chart1
        percentage >= 75.0 -> colors.chart3
        else -> colors.chart5
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.openCourseDetail(course.courseCode) },
        accentStrip = true,
        variant = CardVariant.DEFAULT
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "${course.courseCode} · ${course.courseTitle}",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (course.courseType.isNotBlank()) {
                        AmazeBadge(text = course.courseType, variant = BadgeVariant.INFO)
                    }
                    if (course.credits?.isNotBlank() == true) {
                        AmazeBadge(text = "${course.credits} cr", variant = BadgeVariant.SUCCESS)
                    }
                    Text(
                        text = "${course.attendedClasses}/${course.totalClasses} classes",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(gradeColor.copy(alpha = 0.15f))
                    .border(1.dp, gradeColor.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${percentage.toInt()}%",
                    style = AmazeTheme.typography.body.copy(color = gradeColor, fontWeight = FontWeight.Black),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GlassActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            Text(
                text = title,
                style = AmazeTheme.typography.caption.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


