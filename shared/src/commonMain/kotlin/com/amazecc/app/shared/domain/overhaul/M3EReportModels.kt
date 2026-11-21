package com.amazecc.app.shared.domain.overhaul

/**
 * The single immutable snapshot of the full web-parity student academic report.
 *
 * The report assembler fills every field from the app's real caches (whoami/profile,
 * attendance, grades, curriculum, exams, events, finance and services); all three
 * serializers — HTML, JSON and PDF — consume this one value. Nothing is fabricated:
 * a field is nullable (or empty) only where the web report genuinely tolerates absence.
 */
data class M3EReportData(
    val generatedAt: Long,
    val identity: M3EReportIdentity,
    val sections: M3EReportSections,
    val summary: M3EReportSummary,
    val semesters: List<M3EReportSemester>,
    val curriculum: List<M3EReportCurriculumRow>,
    val calendar: List<M3EReportCalendarMonth>,
    val attendance: M3EReportAttendance,
    val odUsage: M3EReportOdUsage,
    val exams: List<M3EReportExam>,
    val subjects: List<M3EReportSubject>,
    val events: List<M3EReportEvent>,
    val finance: M3EReportFinance,
    val services: M3EReportServices
)

data class M3EReportSections(
    val overview: Boolean = true, val curriculum: Boolean = true,
    val subjects: Boolean = true, val history: Boolean = true,
    val attendance: Boolean = true, val od: Boolean = true,
    val exams: Boolean = true, val events: Boolean = true,
    val finance: Boolean = true, val services: Boolean = true
) {
    /** Section keys in the stable order the web report renders them. */
    fun keys(): List<Pair<String, Boolean>> = listOf(
        "overview" to overview,
        "curriculum" to curriculum,
        "subjects" to subjects,
        "history" to history,
        "attendance" to attendance,
        "od" to od,
        "exams" to exams,
        "events" to events,
        "finance" to finance,
        "services" to services
    )

    /** Returns a copy with one canonical section key set; unknown keys are a no-op. */
    fun with(key: String, enabled: Boolean): M3EReportSections = when (key) {
        "overview" -> copy(overview = enabled)
        "curriculum" -> copy(curriculum = enabled)
        "subjects" -> copy(subjects = enabled)
        "history" -> copy(history = enabled)
        "attendance" -> copy(attendance = enabled)
        "od" -> copy(od = enabled)
        "exams" -> copy(exams = enabled)
        "events" -> copy(events = enabled)
        "finance" -> copy(finance = enabled)
        "services" -> copy(services = enabled)
        else -> this
    }
}

data class M3EReportIdentity(
    val name: String, val registrationNumber: String, val programme: String,
    val school: String, val campus: String, val proctorName: String? = null,
    val proctorEmail: String? = null, val photoBase64: String? = null,
    val semesterName: String = ""
)

data class M3EReportSummary(
    val cgpa: Double, val currentGpa: Double?, val attendancePct: Double,
    val classesAttended: Int, val classesTotal: Int,
    val daysWithoutAbsence: Int, val daysRecorded: Int,
    val classesMissed: Int, val creditsEarned: Int, val creditsRegistered: Int,
    val arrearsCount: Int
)

data class M3EReportSemester(
    val semesterId: String, val name: String, val gpa: Double?,
    val courses: List<M3EReportGradeCourse>
)
data class M3EReportGradeCourse(
    val code: String, val title: String, val type: String, val credits: Int,
    val grandTotal: Double?, val grade: String,
    val components: List<M3EReportAssessment>
)
data class M3EReportAssessment(
    val title: String, val status: String, val scored: Double, val maximum: Double,
    val weightagePercent: Double = 0.0, val weightedScore: Double = 0.0
)

data class M3EReportCurriculumRow(
    val category: String, val basket: String, val code: String,
    val title: String, val credits: Int, val type: String,
    val required: Int = 0, val earned: Int = 0, val progressPct: Double = 0.0
)

data class M3EReportCalendarMonth(
    val key: String, val label: String, val days: List<M3EReportCalendarDay>
)
data class M3EReportCalendarDay(
    val date: Int, val weekday: String, val events: List<String>
)

data class M3EReportAttendance(
    val courses: List<M3EReportAttendanceCourse>,
    val months: List<M3EReportAttendanceMonth>
)
data class M3EReportAttendanceCourse(
    val code: String, val title: String, val type: String, val faculty: String,
    val slotVenue: String, val attended: Int, val total: Int, val percentage: Double
)
data class M3EReportAttendanceMonth(
    val key: String, val label: String,
    val daysRecorded: Int, val daysWithoutAbsence: Int,
    val presentSessions: Int, val absentSessions: Int, val odSessions: Int,
    val calendar: List<M3EReportCalendarDay>,
    val entries: List<M3EReportAttendanceEntry>
)
data class M3EReportAttendanceEntry(
    val date: String, val courseCode: String, val classLabel: String, val status: String
)

/**
 * OD hour totals. [validHours] and [wastedHours] are null when the app has no wasted-OD
 * tracker / recovered-hours source — the value is UNKNOWN, never zero.
 */
data class M3EReportOdUsage(
    val claimedHours: Int, val validHours: Int?, val wastedHours: Int?,
    val perSubject: List<M3EReportOdSubject>, val official: List<M3EReportOfficialOd>
)
/**
 * Per-subject OD hours. [validHours] and [wastedHours] are null when the app has no
 * wasted-OD tracker / recovered-hours source — the value is UNKNOWN, never zero.
 */
data class M3EReportOdSubject(
    val code: String, val claimedHours: Int, val validHours: Int?,
    val wastedHours: Int?, val officialMatchPct: Double
)
data class M3EReportOfficialOd(
    val date: String, val type: String, val reason: String,
    val basis: String, val time: String, val remarks: String
)

data class M3EReportExam(
    val series: String, val courseCode: String, val courseTitle: String,
    val date: String, val session: String, val time: String,
    val venue: String, val seat: String
)

data class M3EReportSubject(
    val code: String, val title: String, val type: String, val credits: Int,
    val classId: String, val faculty: String, val department: String,
    val slot: String, val venue: String,
    val attendance: M3EReportAttendanceCourse,
    val publishedGrade: String?,
    val assessmentAverage: Double?,
    val assessments: List<M3EReportAssessment>,
    val od: M3EReportOdSubject?,
    val prediction: M3EReportPrediction?
)
data class M3EReportPrediction(
    val currentPace: String, val bestGrade: String, val currentStandingPct: Double?,
    val maxPossiblePct: Double?, val classAveragePct: Double?, val classSize: Int
)

data class M3EReportEvent(
    val name: String, val date: String, val time: String, val venue: String,
    val paymentStatus: String
)

data class M3EReportFinance(
    val hasDues: Boolean, val duesMessage: String,
    val receipts: List<M3EReportReceipt>, val wallet: List<M3EReportWalletEntry>
)
data class M3EReportReceipt(val number: String, val date: String, val amount: String)
data class M3EReportWalletEntry(
    val date: String, val receiptNumber: String, val type: String,
    val amount: String, val refundAmount: String, val balance: String
)
data class M3EReportServices(
    val transport: List<Pair<String, String>>,
    val library: List<Pair<String, String>>,
    val moodle: List<M3EReportMoodleItem>,
    val hostel: List<Pair<String, String>>
)
data class M3EReportMoodleItem(
    val course: String, val title: String, val dueDate: String, val status: String
)
