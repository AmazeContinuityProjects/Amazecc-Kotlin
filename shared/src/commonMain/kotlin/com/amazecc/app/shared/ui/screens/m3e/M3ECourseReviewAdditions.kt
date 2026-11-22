package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.StoredQcmRow
import com.amazecc.app.shared.state.TimetableSlot
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.rememberFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun M3ECourseReviewAdditions(
    semesterId: String,
    courseCode: String,
    onDownloadSyllabus: () -> Unit,
    syllabusStatus: String?,
) {
    val academic by AppDataStore.academic.collectAsState()
    val cachedQcm by AppDataStore.qcmView.collectAsState()
    val slots = remember(academic, semesterId, courseCode) { courseWeeklySchedule(academic, semesterId, courseCode) }
    val meetings = remember(cachedQcm, semesterId, courseCode) { courseQcmMeetings(cachedQcm, semesterId, courseCode) }
    val saveFile = rememberFileSaver()
    val scope = rememberCoroutineScope()
    var downloadingCurriculum by remember { mutableStateOf(false) }
    var curriculumStatus by remember { mutableStateOf<String?>(null) }

    OfficialDocuments(
        onDownloadSyllabus = onDownloadSyllabus,
        syllabusStatus = syllabusStatus,
        downloadingCurriculum = downloadingCurriculum,
        curriculumStatus = curriculumStatus,
        onDownloadCurriculum = {
            if (!downloadingCurriculum) {
                downloadingCurriculum = true
                curriculumStatus = null
                scope.launch {
                    try {
                        val bytes = AmazeClient.downloadCurriculum()
                        curriculumStatus = when {
                            bytes == null || bytes.isEmpty() -> "Could not download the curriculum."
                            saveFile("curriculum.pdf", bytes) -> "Curriculum saved."
                            else -> "Could not save the curriculum file."
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        curriculumStatus = "Could not download or save the curriculum."
                    } finally {
                        downloadingCurriculum = false
                    }
                }
            }
        },
    )
    WeeklySchedule(slots)
    CourseQcm(meetings)
}

internal fun courseWeeklySchedule(academic: AcademicData, semesterId: String, courseCode: String): List<TimetableSlot> {
    if (courseCode.isBlank() || semesterId.isBlank()) return emptyList()
    val semester = academic.semesters[semesterId] ?: return emptyList()
    val code = Projections.canonicalCourseKey(courseCode)
    val courses = semester.courses.filterValues { Projections.canonicalCourseKey(it.courseCode).equals(code, ignoreCase = true) }
    return AcademicDerivers.buildWeeklyTimetable(semester.copy(courses = courses)).distinct()
}

internal fun courseQcmMeetings(cache: QcmViewRes?, semesterId: String, courseCode: String): List<StoredQcmRow> {
    if (courseCode.isBlank() || semesterId.isBlank()) return emptyList()
    val code = Projections.canonicalCourseKey(courseCode)
    return cache?.tables.orEmpty().flatMap { it.rows }.filter { row ->
        row.semesterCode?.trim().equals(semesterId, ignoreCase = true) &&
            Projections.canonicalCourseKey(row.courseCode.orEmpty()).equals(code, ignoreCase = true)
    }.distinct()
}

@Composable
private fun OfficialDocuments(
    onDownloadSyllabus: () -> Unit,
    syllabusStatus: String?,
    downloadingCurriculum: Boolean,
    curriculumStatus: String?,
    onDownloadCurriculum: () -> Unit,
) {
    CourseReviewCard("Official Documents") {
        OutlinedButton(onClick = onDownloadSyllabus, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Text("Download Official Syllabus (PDF)")
        }
        syllabusStatus?.let { ReviewCaption(it) }
        OutlinedButton(
            onClick = onDownloadCurriculum,
            enabled = !downloadingCurriculum,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Text(if (downloadingCurriculum) "Downloading curriculum" else "Download Curriculum (PDF)")
        }
        curriculumStatus?.let { ReviewCaption(it) }
    }
}

@Composable
private fun WeeklySchedule(slots: List<TimetableSlot>) {
    CourseReviewCard("Weekly Schedule") {
        val byDay = slots.groupBy { it.day }
        if (byDay.isEmpty()) ReviewCaption("No weekly schedule available for this course and semester.")
        SlotMap.days.forEach { day ->
            val daySlots = byDay[day].orEmpty()
            if (daySlots.isNotEmpty()) {
                Text(day.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                daySlots.forEach { slot ->
                    Text(
                        listOfNotNull(slot.time, slot.slotName, slot.courseType, slot.venue)
                            .filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    slot.faculty?.takeIf { it.isNotBlank() }?.let { ReviewCaption(it) }
                }
            }
        }
    }
}

@Composable
private fun CourseQcm(meetings: List<StoredQcmRow>) {
    CourseReviewCard("Quality Circle Meeting (QCM)") {
        if (meetings.isEmpty()) ReviewCaption("No QCM feedback in the cache for this course and semester.")
        meetings.forEach { meeting ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    meeting.qcmNo?.takeIf { it.isNotBlank() }?.let { "Meeting $it" } ?: "Meeting feedback",
                    style = MaterialTheme.typography.titleSmall,
                )
                listOfNotNull(meeting.courseType, meeting.classNbr, meeting.faculty)
                    .filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { ReviewCaption(it.joinToString(" • ")) }
                QcmField("Suggestions", meeting.suggestions)
                QcmField("Action", meeting.action)
                QcmField("Faculty Reply", meeting.facultyReply)
                QcmField("HOD Comments", meeting.hodComments)
            }
        }
    }
}

@Composable
private fun QcmField(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = M3ETheme.colors.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CourseReviewCard(title: String, content: @Composable () -> Unit) {
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ReviewCaption(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = M3ETheme.colors.onSurfaceVariant)
}
