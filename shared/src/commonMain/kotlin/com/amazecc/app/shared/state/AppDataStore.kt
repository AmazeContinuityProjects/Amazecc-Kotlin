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
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.security.Encryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * The single source of truth for the student's academic/campus data.
 *
 * All reads, writes and edits go through this store — nothing else touches the
 * data caches. Every setter sanitizes its transport response at the store
 * boundary (see [AppSanitizers]), so consumers only ever see clean, typed data.
 *
 * Persistence: the whole snapshot is stored encrypted under
 * [SettingsManager.CACHE_APP_DATA] (visible to the widget/notification
 * processes, which re-read it via [loadPersistedSnapshot]).
 */
object AppDataStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _data = MutableStateFlow(AppDataSnapshot())
    val data: StateFlow<AppDataSnapshot> = _data.asStateFlow()

    // ── Per-module derived flows (names mirror the legacy AppState flow names) ──

    val academic: StateFlow<AcademicData> = derived { it.academic }
    val hostelDetails: StateFlow<HostelDetails?> = derived { it.hostelDetails }
    val messMenu: StateFlow<MessMenuRes?> = derived { it.messMenu }
    val laundrySchedule: StateFlow<LaundryRes?> = derived { it.laundrySchedule }
    val hostelCounselling: StateFlow<ArrearResponse?> = derived { it.hostelCounselling }
    val calendar: StateFlow<CalendarRes?> = derived { it.calendar }
    val calendarsList: StateFlow<CalendarsListRes?> = derived { it.calendarsList }
    val qcmView: StateFlow<QcmViewRes?> = derived { it.qcmView }
    val curriculum: StateFlow<CurriculumRes?> = derived { it.curriculum }
    val payments: StateFlow<PaymentsRes?> = derived { it.payments }
    val library: StateFlow<LibraryRes?> = derived { it.library }
    val transportData: StateFlow<TransportDataRes?> = derived { it.transportData }
    val buses: StateFlow<BusesRes?> = derived { it.buses }
    val lms: StateFlow<LMSRes?> = derived { it.lms }
    val events: StateFlow<EventHubRes?> = derived { it.events }
    val registeredEvents: StateFlow<EventHubRegisteredEventsRes?> = derived { it.registeredEvents }
    val clubs: StateFlow<ClubsRes?> = derived { it.clubs }
    val circulars: StateFlow<CircularsRes?> = derived { it.circulars }
    val moodleData: StateFlow<MoodleRes?> = derived { it.moodleData }
    val cabShareUser: StateFlow<CabShareUser?> = derived { it.cabShareUser }
    val cabHubs: StateFlow<List<CabShareHub>> = derived { it.cabHubs }
    val ffcsRegistration: StateFlow<FfcsRegistrationInfo?> = derived { it.ffcsRegistration }
    val tasks: StateFlow<List<HomeworkTask>> = derived { it.tasks }

    private fun <T> derived(select: (AppDataSnapshot) -> T): StateFlow<T> =
        _data.map(select)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, select(_data.value))

    // ── Core mutation ──

    private fun update(transform: (AppDataSnapshot) -> AppDataSnapshot) {
        val next = transform(_data.value)
        if (next == _data.value) return
        _data.value = next
        persist()
    }

    /**
     * Restores the persisted snapshot. When no snapshot exists yet, migrates
     * the legacy per-module caches into it (and deletes them).
     *
     * v1 snapshots (no `academic` key) are migrated to v2 in the same pass via
     * [SnapshotMigrator] and re-persisted (see docs/features/schemas/03-migration.md).
     */
    fun restore() {
        val raw = SettingsManager.getNullableString(SettingsManager.CACHE_APP_DATA)
        if (raw != null) {
            val decoded = runCatching { Encryption.decryptOrPlain(raw) }.getOrNull()
            val restored = decoded?.let {
                runCatching { decodeSnapshot(it) }.getOrNull()
            }
            if (restored != null) {
                val normalized = AcademicMerge.normalizeEmbeddedKeys(restored)
                _data.value = normalized
                if (normalized != restored) persist()
                return
            }
        }
        migrateLegacyCaches()
    }

    /** Restores a snapshot WITHOUT side effects — for widget/notification processes. */
    fun loadPersistedSnapshot(): AppDataSnapshot {
        val raw = SettingsManager.getNullableString(SettingsManager.CACHE_APP_DATA) ?: return AppDataSnapshot()
        val decoded = runCatching { Encryption.decryptOrPlain(raw) }.getOrNull() ?: return AppDataSnapshot()
        return AcademicMerge.normalizeEmbeddedKeys(
            runCatching { decodeSnapshot(decoded) }.getOrNull() ?: AppDataSnapshot()
        )
    }

    /** Decodes a persisted snapshot, migrating v1 → v2 if needed (pure, no writes). */
    private fun decodeSnapshot(encoded: String): AppDataSnapshot =
        if ("\"academic\"" in encoded) {
            json.decodeFromString<AppDataSnapshot>(encoded)
        } else {
            SnapshotMigrator.toV2(json.decodeFromString<LegacyAppDataSnapshot>(encoded))
        }

    private fun persist() {
        val encoded = runCatching { json.encodeToString(AppDataSnapshot.serializer(), _data.value) }.getOrNull()
            ?: return
        SettingsManager.setString(SettingsManager.CACHE_APP_DATA, Encryption.encryptOrPlain(encoded))
    }

    /** Forces a persist of the current snapshot (idempotent no-op if nothing changed). */
    fun persistNow() = persist()

    /** Serialises the current snapshot as plain JSON for backup export (never the encrypted blob). */
    fun exportSnapshot(): String =
        runCatching { json.encodeToString(AppDataSnapshot.serializer(), _data.value) }.getOrDefault("{}")

    /** Clears the in-memory snapshot (logout). The persisted key is wiped by the caller's global cache clear. */
    fun clear() {
        _data.value = AppDataSnapshot()
    }

    /** Directly replaces the whole snapshot (backup import). */
    fun importSnapshot(snapshot: AppDataSnapshot) = update { snapshot }

    // ── Module setters (transport payload in, sanitised data out) ──

    // ── Academic (unified schema — see docs/features/schemas) ──

    fun upsertAttendance(semesterId: String, res: AttendanceRes?) = update { s ->
        s.copy(academic = AcademicMerge.upsertAttendance(s.academic, semesterId, res))
    }

    fun upsertMarks(semesterId: String, res: MarksRes?) = update { s ->
        s.copy(academic = AcademicMerge.upsertMarks(s.academic, semesterId, res))
    }

    fun upsertGrades(res: com.amazecc.app.shared.model.AllGradesRes?) = update { s ->
        s.copy(academic = AcademicMerge.upsertGrades(s.academic, res))
    }

    fun upsertExams(semesterId: String, res: ExamScheduleRes?) = update { s ->
        s.copy(academic = AcademicMerge.upsertExams(s.academic, semesterId, res))
    }

    fun upsertTimetable(semesterId: String, res: TimetableRes?) = update { s ->
        s.copy(academic = AcademicMerge.upsertTimetable(s.academic, semesterId, res))
    }

    fun updateSemester(semesterId: String, transform: (SemesterData) -> SemesterData) = update { s ->
        s.copy(academic = AcademicMerge.updateSemester(s.academic, semesterId, transform))
    }

    fun setHostelDetails(res: HostelDetails?) = update { it.copy(hostelDetails = AppSanitizers.sanitizeHostelDetails(res)) }

    fun setMessMenu(res: MessMenuRes?) = update { it.copy(messMenu = AppSanitizers.sanitizeMessMenu(res)) }

    fun setLaundrySchedule(res: LaundryRes?) = update { it.copy(laundrySchedule = AppSanitizers.sanitizeLaundry(res)) }

    fun setHostelCounselling(res: ArrearResponse?) = update { it.copy(hostelCounselling = AppSanitizers.sanitizeCounselling(res)) }

    fun setCalendar(res: CalendarRes?) = update { it.copy(calendar = AppSanitizers.sanitizeCalendar(res)) }

    fun setCalendarsList(res: CalendarsListRes?) = update { it.copy(calendarsList = AppSanitizers.sanitizeCalendarsList(res)) }

    fun setQcmView(res: QcmViewRes?) = update { it.copy(qcmView = AppSanitizers.sanitizeQcmView(res)) }

    fun setCurriculum(res: CurriculumRes?) = update { it.copy(curriculum = AppSanitizers.sanitizeCurriculum(res)) }

    fun setPayments(res: PaymentsRes?) = update { it.copy(payments = AppSanitizers.sanitizePayments(res)) }

    fun setLibrary(res: LibraryRes?) = update { it.copy(library = AppSanitizers.sanitizeLibrary(res)) }

    fun setTransportData(res: TransportDataRes?) = update { it.copy(transportData = AppSanitizers.sanitizeTransportData(res)) }

    fun setBuses(res: BusesRes?) = update { it.copy(buses = AppSanitizers.sanitizeBuses(res)) }

    fun setLms(res: LMSRes?) = update { it.copy(lms = AppSanitizers.sanitizeLms(res)) }

    fun setEvents(res: EventHubRes?) = update { it.copy(events = AppSanitizers.sanitizeEvents(res)) }

    fun setRegisteredEvents(res: EventHubRegisteredEventsRes?) = update { it.copy(registeredEvents = AppSanitizers.sanitizeRegisteredEvents(res)) }

    fun setClubs(res: ClubsRes?) = update { it.copy(clubs = AppSanitizers.sanitizeClubs(res)) }

    fun setCirculars(res: CircularsRes?) = update { it.copy(circulars = AppSanitizers.sanitizeCirculars(res)) }

    fun setMoodle(res: MoodleRes?) = update { it.copy(moodleData = AppSanitizers.sanitizeMoodle(res)) }

    fun setCabShareUser(res: CabShareUser?) = update { it.copy(cabShareUser = AppSanitizers.sanitizeCabShareUser(res)) }

    fun setCabHubs(hubs: List<CabShareHub>) = update { it.copy(cabHubs = hubs) }

    fun setFfcsRegistration(info: FfcsRegistrationInfo?) = update { it.copy(ffcsRegistration = AppSanitizers.sanitizeFfcs(info)) }

    // ── Tasks ──

    fun setTasks(tasks: List<HomeworkTask>) = update { it.copy(tasks = AppSanitizers.sanitizeTasks(tasks)) }

    fun addTask(task: HomeworkTask) = update { it.copy(tasks = it.tasks + task) }

    fun updateTask(id: String, transform: (HomeworkTask) -> HomeworkTask) = update { s ->
        s.copy(tasks = s.tasks.map { if (it.id == id) transform(it) else it })
    }

    fun removeTask(id: String) = update { s ->
        s.copy(tasks = s.tasks.filter { it.id != id })
    }

    fun toggleTask(id: String) = updateTask(id) { it.copy(completed = !it.completed) }

    /** Merges imported tasks into the current list, keeping existing ids. */
    fun mergeImportedTasks(imported: List<HomeworkTask>) = update { s ->
        val importedIds = imported.map { it.id }
        s.copy(tasks = imported + s.tasks.filter { it.id !in importedIds })
    }

    // ── Legacy cache migration ──

    private inline fun <reified T> readLegacy(key: String): T? {
        val raw = SettingsManager.getNullableString(key) ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private val legacyKeys = listOf(
        SettingsManager.CACHE_ATTENDANCE,
        SettingsManager.CACHE_TIMETABLE,
        SettingsManager.CACHE_MARKS,
        SettingsManager.CACHE_GRADES,
        SettingsManager.CACHE_HOSTEL_DETAILS,
        SettingsManager.CACHE_MESS_MENU,
        SettingsManager.CACHE_LAUNDRY,
        SettingsManager.CACHE_HOSTEL_COUNSELLING,
        SettingsManager.CACHE_EXAM_SCHEDULE,
        SettingsManager.CACHE_CALENDAR,
        SettingsManager.CACHE_CALENDARS_LIST,
        SettingsManager.CACHE_QCM_VIEW,
        SettingsManager.CACHE_CURRICULUM,
        SettingsManager.CACHE_PAYMENTS,
        SettingsManager.CACHE_LIBRARY,
        SettingsManager.CACHE_TRANSPORT_DATA,
        SettingsManager.CACHE_BUSES,
        SettingsManager.CACHE_LMS,
        SettingsManager.CACHE_EVENTS,
        SettingsManager.CACHE_CLUBS,
        SettingsManager.CACHE_CIRCULARS,
        SettingsManager.CACHE_MOODLE,
        SettingsManager.CACHE_CAB_USER,
        SettingsManager.CACHE_FFCS_REG_INFO,
        SettingsManager.CACHE_TASKS,
        SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE,
        SettingsManager.CACHE_ALL_SEMESTER_MARKS,
        SettingsManager.CACHE_ALL_SEMESTER_EXAMS
    )

    private fun migrateLegacyCaches() {
        var restored = AppDataSnapshot()
        var found = false
        var academic = AcademicData()

        readLegacy<AttendanceRes>(SettingsManager.CACHE_ATTENDANCE)?.let {
            val sem = it.semesterId ?: ""
            academic = AcademicMerge.upsertAttendance(academic, sem, it); found = true
        }
        readLegacy<Map<String, AttendanceRes?>>(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE)?.let { map ->
            map.forEach { (sem, v) ->
                if (v != null) { academic = AcademicMerge.upsertAttendance(academic, sem, v); found = true }
            }
        }
        readLegacy<MarksRes>(SettingsManager.CACHE_MARKS)?.let {
            academic = AcademicMerge.upsertMarks(academic, "", it); found = true
        }
        readLegacy<Map<String, MarksRes>>(SettingsManager.CACHE_ALL_SEMESTER_MARKS)?.let { map ->
            map.forEach { (sem, v) ->
                academic = AcademicMerge.upsertMarks(academic, sem, v); found = true
            }
        }
        readLegacy<AllGradesRes>(SettingsManager.CACHE_GRADES)?.let {
            academic = AcademicMerge.upsertGrades(academic, it); found = true
        }
        readLegacy<Map<String, ExamScheduleRes?>>(SettingsManager.CACHE_ALL_SEMESTER_EXAMS)?.let { map ->
            map.forEach { (sem, v) ->
                if (v != null) { academic = AcademicMerge.upsertExams(academic, sem, v); found = true }
            }
        }
        readLegacy<ExamScheduleRes>(SettingsManager.CACHE_EXAM_SCHEDULE)?.let {
            val sem = if (academic.semesters.size == 1) academic.semesters.keys.first() else ""
            academic = AcademicMerge.upsertExams(academic, sem, it); found = true
        }
        readLegacy<TimetableRes>(SettingsManager.CACHE_TIMETABLE)?.let {
            val sem = it.semesterId ?: if (academic.semesters.size == 1) academic.semesters.keys.first() else ""
            academic = AcademicMerge.upsertTimetable(academic, sem, it); found = true
        }
        restored = restored.copy(academic = academic)

        readLegacy<HostelDetails>(SettingsManager.CACHE_HOSTEL_DETAILS)?.let {
            restored = restored.copy(hostelDetails = AppSanitizers.sanitizeHostelDetails(it)); found = true
        }
        readLegacy<MessMenuRes>(SettingsManager.CACHE_MESS_MENU)?.let {
            restored = restored.copy(messMenu = AppSanitizers.sanitizeMessMenu(it)); found = true
        }
        readLegacy<LaundryRes>(SettingsManager.CACHE_LAUNDRY)?.let {
            restored = restored.copy(laundrySchedule = AppSanitizers.sanitizeLaundry(it)); found = true
        }
        readLegacy<ArrearResponse>(SettingsManager.CACHE_HOSTEL_COUNSELLING)?.let {
            restored = restored.copy(hostelCounselling = AppSanitizers.sanitizeCounselling(it)); found = true
        }
        readLegacy<CalendarRes>(SettingsManager.CACHE_CALENDAR)?.let {
            restored = restored.copy(calendar = AppSanitizers.sanitizeCalendar(it)); found = true
        }
        readLegacy<CalendarsListRes>(SettingsManager.CACHE_CALENDARS_LIST)?.let {
            restored = restored.copy(calendarsList = AppSanitizers.sanitizeCalendarsList(it)); found = true
        }
        readLegacy<QcmViewRes>(SettingsManager.CACHE_QCM_VIEW)?.let {
            restored = restored.copy(qcmView = AppSanitizers.sanitizeQcmView(it)); found = true
        }
        readLegacy<CurriculumRes>(SettingsManager.CACHE_CURRICULUM)?.let {
            restored = restored.copy(curriculum = AppSanitizers.sanitizeCurriculum(it)); found = true
        }
        readLegacy<PaymentsRes>(SettingsManager.CACHE_PAYMENTS)?.let {
            restored = restored.copy(payments = AppSanitizers.sanitizePayments(it)); found = true
        }
        readLegacy<LibraryRes>(SettingsManager.CACHE_LIBRARY)?.let {
            restored = restored.copy(library = AppSanitizers.sanitizeLibrary(it)); found = true
        }
        readLegacy<TransportDataRes>(SettingsManager.CACHE_TRANSPORT_DATA)?.let {
            restored = restored.copy(transportData = AppSanitizers.sanitizeTransportData(it)); found = true
        }
        readLegacy<BusesRes>(SettingsManager.CACHE_BUSES)?.let {
            restored = restored.copy(buses = AppSanitizers.sanitizeBuses(it)); found = true
        }
        readLegacy<LMSRes>(SettingsManager.CACHE_LMS)?.let {
            restored = restored.copy(lms = AppSanitizers.sanitizeLms(it)); found = true
        }
        readLegacy<EventHubRes>(SettingsManager.CACHE_EVENTS)?.let {
            restored = restored.copy(events = AppSanitizers.sanitizeEvents(it)); found = true
        }
        readLegacy<ClubsRes>(SettingsManager.CACHE_CLUBS)?.let {
            restored = restored.copy(clubs = AppSanitizers.sanitizeClubs(it)); found = true
        }
        readLegacy<CircularsRes>(SettingsManager.CACHE_CIRCULARS)?.let {
            restored = restored.copy(circulars = AppSanitizers.sanitizeCirculars(it)); found = true
        }
        readLegacy<MoodleRes>(SettingsManager.CACHE_MOODLE)?.let {
            restored = restored.copy(moodleData = AppSanitizers.sanitizeMoodle(it)); found = true
        }
        readLegacy<CabShareUser>(SettingsManager.CACHE_CAB_USER)?.let {
            restored = restored.copy(cabShareUser = AppSanitizers.sanitizeCabShareUser(it)); found = true
        }
        readLegacy<FfcsRegistrationInfo>(SettingsManager.CACHE_FFCS_REG_INFO)?.let {
            restored = restored.copy(ffcsRegistration = AppSanitizers.sanitizeFfcs(it)); found = true
        }
        readLegacy<List<HomeworkTask>>(SettingsManager.CACHE_TASKS)?.let {
            restored = restored.copy(tasks = AppSanitizers.sanitizeTasks(it)); found = true
        }

        if (found) {
            _data.value = restored
            persist()
            legacyKeys.forEach { SettingsManager.remove(it) }
        }
    }
}
