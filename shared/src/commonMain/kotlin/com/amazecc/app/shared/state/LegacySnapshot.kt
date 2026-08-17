package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.CabShareHub
import com.amazecc.app.shared.model.CabShareUser
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.ClubsRes
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.EventHubRes
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.FfcsRegistrationInfo
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.LaundryRes
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.MessMenuRes
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.TimetableRes
import com.amazecc.app.shared.model.TransportDataRes
import kotlinx.serialization.Serializable

/**
 * The v1 snapshot shape (schemaVersion absent, 8 academic fields, mirrors).
 *
 * NEVER written and never exposed as flows — exists only so persisted v1
 * snapshots and old backup files can be decoded and migrated to v2 by
 * [SnapshotMigrator]. See docs/features/schemas/03-migration.md.
 */
@Serializable
data class LegacyAppDataSnapshot(
    val attendance: AttendanceRes? = null,
    val timetable: TimetableRes? = null,
    val marks: MarksRes? = null,
    val allGrades: AllGradesRes? = null,
    val allSemesterAttendance: Map<String, AttendanceRes?> = emptyMap(),
    val allSemesterMarks: Map<String, MarksRes> = emptyMap(),
    val allSemesterExams: Map<String, ExamScheduleRes?> = emptyMap(),
    val hostelDetails: HostelDetails? = null,
    val messMenu: MessMenuRes? = null,
    val laundrySchedule: LaundryRes? = null,
    val hostelCounselling: ArrearResponse? = null,
    val examSchedule: ExamScheduleRes? = null,
    val calendar: CalendarRes? = null,
    val calendarsList: CalendarsListRes? = null,
    val qcmView: QcmViewRes? = null,
    val curriculum: CurriculumRes? = null,
    val payments: PaymentsRes? = null,
    val library: LibraryRes? = null,
    val transportData: TransportDataRes? = null,
    val buses: BusesRes? = null,
    val lms: LMSRes? = null,
    val events: EventHubRes? = null,
    val registeredEvents: EventHubRegisteredEventsRes? = null,
    val clubs: ClubsRes? = null,
    val circulars: CircularsRes? = null,
    val moodleData: MoodleRes? = null,
    val cabShareUser: CabShareUser? = null,
    val cabHubs: List<CabShareHub> = emptyList(),
    val ffcsRegistration: FfcsRegistrationInfo? = null,
    val tasks: List<HomeworkTask> = emptyList()
)
