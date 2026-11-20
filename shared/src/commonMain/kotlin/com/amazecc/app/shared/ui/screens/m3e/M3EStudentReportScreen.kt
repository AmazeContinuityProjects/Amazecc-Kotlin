package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportAction
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Student Report & Academic Export Hub.
 * 100% Stateless: accepts [M3EStudentReportState] and emits [M3EStudentReportAction].
 * Features Complete Student Profile, Academic Metrics, and PDF / JSON export actions.
 *
 * Export outcomes are shown only when the host supplies them: [state.exportSuccessMessage]
 * on real success and [exportErrorMessage] on real failure. A blank/in-flight export never
 * renders a success claim.
 */
@Composable
fun M3EStudentReportScreen(
    state: M3EStudentReportState,
    onAction: (M3EStudentReportAction) -> Unit,
    modifier: Modifier = Modifier,
    exportErrorMessage: String? = null
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item(key = "report_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Student Academic Report",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Comprehensive record of performance, attendance, and standing",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Student Identity Hero Card
            item(key = "student_identity_card") {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = state.studentName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "${state.registrationNumber} • ${state.programme}",
                                    fontSize = 13.sp,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = "${state.school} • ${state.campus} Campus",
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Proctor info
                        state.proctorName?.let { proctor ->
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Proctor: $proctor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurface
                                    )
                                }

                                state.proctorEmail?.let { email ->
                                    Text(
                                        text = email,
                                        fontSize = 11.sp,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Key Metrics Summary Grid
            item(key = "metrics_grid") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 20.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Cumulative GPA",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.cgpa.toFixed(2),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.primary
                            )
                        }
                    }

                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 20.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Overall Attendance",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.overallAttendancePct.toFixed(2, "%"),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = if (state.overallAttendancePct >= 75.0) colors.success else colors.error
                            )
                        }
                    }
                }
            }

            // Credits & Arrears Card
            item(key = "credits_arrears_card") {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Degree Credits",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "${state.completedCredits} / ${state.totalRequiredCredits} Credits",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }

                        val progress = if (state.totalRequiredCredits > 0) {
                            (state.completedCredits.toDouble() / state.totalRequiredCredits.toDouble()).toFloat()
                        } else 0f

                        M3EWavyLinearProgress(
                            progress = progress,
                            activeColor = colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                            label = "Credit completion"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Standing & Arrears",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                            if (state.hasArrears) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = colors.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${state.arrearsCount} Standing Arrears",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.error
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.success,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Clean Record (No Arrears)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.success
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export Actions Section
            item(key = "export_actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Export Options",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )

                    // Export PDF Button
                    ElevatedButton(
                        onClick = { onAction(M3EStudentReportAction.ExportPdfReport) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isExportingPdf) "Generating PDF..." else "Download Comprehensive Report (PDF)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Export JSON Bundle & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAction(M3EStudentReportAction.ExportJsonData) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DataObject,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("JSON Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { onAction(M3EStudentReportAction.ShareReport) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Export result: bound to real host outcomes only. No success is
            // claimed from a button press or from the absence of an error.
            if (!state.isExportingPdf && !exportErrorMessage.isNullOrBlank()) {
                item(key = "export_failure") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.error.copy(alpha = 0.5f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.error
                                )
                            }
                            Text(
                                text = exportErrorMessage,
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                            TextButton(onClick = { onAction(M3EStudentReportAction.ExportPdfReport) }) {
                                Text("Retry export", color = colors.primary)
                            }
                        }
                    }
                }
            } else if (!state.isExportingPdf && !state.exportSuccessMessage.isNullOrBlank()) {
                item(key = "export_success") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.success.copy(alpha = 0.5f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.success
                                )
                                // Name/path comes from the real export result, not a guess.
                                Text(
                                    text = state.exportSuccessMessage!!,
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
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
