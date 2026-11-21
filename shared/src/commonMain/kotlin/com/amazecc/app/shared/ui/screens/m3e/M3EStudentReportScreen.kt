package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.FormattingParity
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportAction
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportState
import com.amazecc.app.shared.ui.m3e.components.M3EPageSkeleton
import com.amazecc.app.shared.ui.m3e.components.M3EStatCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3EStatSubtitle
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SettingsGroupHeader
import com.amazecc.app.shared.ui.m3e.components.SettingsInfoRow
import com.amazecc.app.shared.ui.m3e.components.SettingsToggleRow
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-158: UI-only labels for the ten report section toggles. Kept in the screen (not the
 * model) because this is a presentation concern. Unknown keys fall back to the raw key so
 * a newly added section can never silently vanish from the toggle group.
 */
private val REPORT_SECTION_LABELS = mapOf(
    "overview" to "Academic Overview",
    "curriculum" to "Curriculum",
    "subjects" to "Subject Dossiers",
    "history" to "Grade History",
    "attendance" to "Attendance Register",
    "od" to "OD Usage",
    "exams" to "Exam Schedule",
    "events" to "Events",
    "finance" to "Finance",
    "services" to "Student Services"
)

/**
 * Material 3 Expressive Academic Report (D-153).
 *
 * 100% stateless: accepts [M3EStudentReportState] and emits [M3EStudentReportAction].
 * The host supplies the real app bar ([M3EStudentReportTopBar]); this composable owns only
 * the scrollable report body — a cover block and grouped Settings-style sections, never a
 * pile of equal cards. No row holds more than two cards or two labelled buttons; the two
 * secondary export actions are icon buttons instead. The "Report Sections" group (D-158)
 * renders one full-width [SettingsToggleRow] per [M3EStudentReportState.sections] key; these
 * are stateless controls that emit [M3EStudentReportAction.ToggleSection] only.
 *
 * Facts are never invented: blank identity fields are omitted, no generated-at line is shown
 * because the state exposes no timestamp, and export outcome copy is shown only when the host
 * supplies a real result. Loading renders an honest skeleton at [state.isLoading], not zeros.
 * (No generated-at line exists because [M3EStudentReportState] has no such field.)
 */
@Composable
fun M3EStudentReportScreen(
    state: M3EStudentReportState,
    onAction: (M3EStudentReportAction) -> Unit,
    modifier: Modifier = Modifier,
    exportErrorMessage: String? = null
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Cover / header block. There is no generatedAt in M3EStudentReportState, so no
        // timestamp is printed (an invented date would be a fabricated fact).
        item(key = "report_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("report_header"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ACADEMIC RECORD",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = "Student Academic Report",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "Comprehensive record of performance, attendance, and standing",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Not an official transcript. For informational use only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        if (state.isLoading) {
            // Honest loading state: no CGPA/attendance/credit numbers are shown until the
            // host has real cached data. M3EPageSkeleton shimmers (or rests under reduced
            // motion) and publishes its aria-busy equivalent semantics.
            item(key = "report_loading") {
                M3EPageSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    label = "Loading academic report",
                    tiles = 2,
                    rows = 4
                )
            }
        } else {
            // Student — flat detail rows, real values only.
            item(key = "section_student") {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("report_section_student")
                ) {
                    SettingsGroupHeader(title = "Student")
                    val detailRows = buildList {
                        add("Name" to state.studentName)
                        add("Registration No." to state.registrationNumber)
                        add("Programme" to state.programme)
                        add("School" to state.school)
                        add("Campus" to state.campus)
                        state.proctorName?.let { add("Proctor" to it) }
                        state.proctorEmail?.let { add("Proctor email" to it) }
                    }
                    detailRows.forEach { (label, value) ->
                        if (value.isNotBlank()) {
                            SettingsInfoRow(label = label, value = value)
                        }
                    }
                }
            }

            // Performance — two M3E stat tiles in one row (never 3+ on compact width).
            item(key = "section_performance") {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("report_section_performance")
                ) {
                    SettingsGroupHeader(title = "Performance")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        M3EStatCard(
                            stat = M3EStatItem(
                                id = "report_cgpa",
                                label = "Cumulative GPA",
                                value = FormattingParity.toFixed(state.cgpa, 2),
                                sub = M3EStatSubtitle.fromData("Published CGPA"),
                                tone = M3EStatTone.PRIMARY,
                                icon = Icons.Rounded.School
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        val attendanceSafe = state.overallAttendancePct >= 75.0
                        M3EStatCard(
                            stat = M3EStatItem(
                                id = "report_attendance",
                                label = "Overall Attendance",
                                value = "${FormattingParity.toFixed(state.overallAttendancePct, 2)}%",
                                sub = M3EStatSubtitle.fromData(
                                    if (attendanceSafe) "Meets 75% target" else "Below 75% target"
                                ),
                                tone = if (attendanceSafe) M3EStatTone.GOOD else M3EStatTone.CRITICAL,
                                icon = if (attendanceSafe) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Credits & Standing — flat value rows plus one progress surface.
            item(key = "section_credits") {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("report_section_credits")
                ) {
                    SettingsGroupHeader(title = "Credits & Standing")
                    SettingsInfoRow(
                        label = "Degree credits",
                        value = "${state.completedCredits} / ${state.totalRequiredCredits} Credits"
                    )
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        val progress = if (state.totalRequiredCredits > 0) {
                            (state.completedCredits.toDouble() / state.totalRequiredCredits.toDouble()).toFloat()
                        } else {
                            0f
                        }
                        M3EWavyLinearProgress(
                            progress = progress,
                            label = "Credit completion",
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                    SettingsInfoRow(
                        label = "Standing",
                        value = if (state.hasArrears) {
                            "${state.arrearsCount} Standing Arrears"
                        } else {
                            "Clean Record (No Arrears)"
                        }
                    )
                }
            }

            // Report Sections (D-158) — one real toggle per canonical section key, in the
            // stable order from M3EReportSections.keys(). Full-width rows (not cards), so the
            // "max two cards / two labelled buttons per row" rule is untouched. The screen is
            // stateless: each row only emits ToggleSection; the host owns the persisted value.
            item(key = "section_report_sections") {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("report_section_toggles")
                ) {
                    SettingsGroupHeader(title = "Report Sections")
                    state.sections.keys().forEach { (key, checked) ->
                        SettingsToggleRow(
                            title = REPORT_SECTION_LABELS[key] ?: key,
                            checked = checked,
                            onCheckedChange = { enabled ->
                                onAction(M3EStudentReportAction.ToggleSection(key, enabled))
                            }
                        )
                    }
                }
            }

            // Export — one primary labelled button, one secondary labelled button, and the
            // remaining two actions as icon buttons with explicit contentDescriptions.
            item(key = "section_export") {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("report_section_export")
                ) {
                    SettingsGroupHeader(title = "Export")
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ElevatedButton(
                            onClick = { onAction(M3EStudentReportAction.ExportPdfReport) },
                            enabled = !state.isExportingPdf,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.isExportingPdf) {
                                    "Generating PDF..."
                                } else {
                                    "Download Comprehensive Report (PDF)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { onAction(M3EStudentReportAction.ExportJsonData) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DataObject,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("JSON Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "MORE FORMATS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = colors.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { onAction(M3EStudentReportAction.ExportHtmlReport) },
                                modifier = Modifier.testTag("report_export_html")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = "Export HTML report",
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAction(M3EStudentReportAction.ShareReport) },
                                modifier = Modifier.testTag("report_export_share")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share report",
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Export result: bound to real host outcomes only. No success is claimed from a
            // button press or from the absence of an error.
            if (!state.isExportingPdf && !exportErrorMessage.isNullOrBlank()) {
                item(key = "export_failure") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.error.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PDF export failed",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.error
                                )
                            }
                            Text(text = exportErrorMessage, color = colors.onSurfaceVariant)
                            TextButton(onClick = { onAction(M3EStudentReportAction.ExportPdfReport) }) {
                                Text("Retry export", color = colors.primary)
                            }
                        }
                    }
                }
            } else if (!state.isExportingPdf && !state.exportSuccessMessage.isNullOrBlank()) {
                item(key = "export_success") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.success.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "PDF report saved",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.success
                                )
                                // Name/path comes from the real export result, not a guess.
                                Text(
                                    text = state.exportSuccessMessage!!,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom spacer clears the now-visible floating nav bar (twice its height).
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }
}

/**
 * D-153: stateless page composition used by the stateful host. It pairs the real M3E top
 * app bar with the scrolling body and routes Refresh through the same [onAction] sink as
 * every other report action, so the control cannot be dead. Back is a plain callback the
 * host binds to AppState navigation; no state or navigation is owned here.
 */
@Composable
fun M3EStudentReportContent(
    state: M3EStudentReportState,
    onAction: (M3EStudentReportAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    exportErrorMessage: String? = null,
    notice: String? = null
) {
    val colors = M3ETheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        M3EStudentReportTopBar(onBack = onBack, onRefresh = { onAction(M3EStudentReportAction.Refresh) })
        if (!notice.isNullOrBlank()) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        M3EStudentReportScreen(
            state = state,
            onAction = onAction,
            exportErrorMessage = exportErrorMessage,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * D-153: stateless M3E top app bar for the report host — back navigation icon, title and
 * the real Refresh control. Kept here (rather than inside the scrolling body) so the host
 * owns state/navigation and the bar is independently testable.
 */
@Composable
fun M3EStudentReportTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("report_back")) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.onSurface
            )
        }
        Text(
            text = "Academic Report",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        IconButton(onClick = onRefresh, modifier = Modifier.testTag("report_refresh")) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = colors.onSurface
            )
        }
    }
}

private fun createMockStudentReportState() = M3EStudentReportState(
    studentName = "Aravind Kumar",
    registrationNumber = "21BCE1042",
    programme = "B.Tech Computer Science & Engineering",
    school = "SCOPE",
    campus = "Vellore",
    proctorName = "Dr. Anita R",
    proctorEmail = "anita.r@vit.ac.in",
    cgpa = 9.24,
    overallAttendancePct = 91.5,
    completedCredits = 124,
    totalRequiredCredits = 160,
    hasArrears = false,
    arrearsCount = 0
)

@Preview
@Composable
private fun M3EStudentReportScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EStudentReportScreen(state = createMockStudentReportState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EStudentReportScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EStudentReportScreen(state = createMockStudentReportState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EStudentReportExportSuccessPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EStudentReportScreen(
            state = createMockStudentReportState().copy(
                exportSuccessMessage = "student_report_21BCE1042.pdf saved to Downloads"
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun M3EStudentReportExportFailurePreview() {
    AmazeM3ETheme(isDark = false) {
        M3EStudentReportScreen(
            state = createMockStudentReportState(),
            onAction = {},
            exportErrorMessage = "Could not generate the PDF. Storage permission was denied."
        )
    }
}

@Preview
@Composable
private fun M3EStudentReportLoadingPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EStudentReportScreen(
            state = createMockStudentReportState().copy(isLoading = true),
            onAction = {}
        )
    }
}
