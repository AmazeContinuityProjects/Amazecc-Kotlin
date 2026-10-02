package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AcademicMerge
import com.amazecc.app.shared.state.AppDataSnapshot
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.state.StoredGrade
import com.amazecc.app.shared.state.StoredMarks
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Folds transport DTOs into one [DomainSnapshot].
 *
 * The only place that knows a DTO and a domain model can both exist. Everything downstream
 * (projections, screens) sees domain only, which is what stops mapping logic spreading into the
 * UI.
 *
 * Three properties this is built to hold:
 *
 *  - **Idempotent.** Ingesting the same input twice yields the same snapshot, so a retried module
 *    cannot half-apply. The merge step is pure, and the mutex keeps two concurrent syncs from
 *    interleaving a read-modify-write.
 *  - **Additive.** Migrating from [AppDataSnapshot] is read-only, so this can land before any
 *    screen moves and be reverted by deleting the call site.
 *  - **Non-destructive.** A module that failed to sync leaves its previous value in place rather
 *    than blanking it, matching the "never persist nulls over good data" rule the old sync engine
 *    already used.
 */
object VtopIngestor {

    private val mutex = Mutex()

    /**
     * Domain snapshot derived from the legacy persisted snapshot.
     *
     * **Must be lossless.** This is the conversion every existing install passes through on
     * upgrade, so any field without a mapping is deleted the moment the domain snapshot becomes
     * the stored format. `SnapshotRoundTripTest` populates every [AppDataSnapshot] field with a
     * sentinel and asserts the whole set survives, so this cannot silently lose one again.
     *
     * Read-only and pure, so it can land before any screen moves and be reverted by deleting the
     * call site.
     *
     * @param selectedSemesterId the user's own choice, when known. It wins over [resolveCurrentSemesterId].
     */
    fun fromLegacy(
        snapshot: AppDataSnapshot,
        selectedSemesterId: String? = null,
    ): DomainSnapshot = DomainSnapshot(
        academics = academicsFrom(snapshot, selectedSemesterId),
        schedule = Schedule(
            exams = snapshot.academic.semesters
                .flatMap { (id, sem) -> sem.exams.map { examFrom(id, it) } }
                .sortedBy { it.date },
            calendar = snapshot.calendar?.months.orEmpty().map { m ->
                CalendarMonth(
                    label = m.month,
                    days = m.days.map { d ->
                        CalendarDay(
                            date = d.date,
                            events = d.events.map { e ->
                                CalendarEventItem(
                                    type = e.type, text = e.text,
                                    color = e.color, category = e.category
                                )
                            }
                        )
                    }
                )
            },
            calendarsList = snapshot.calendarsList,
        ),
        campus = Campus(
            messMenu = snapshot.messMenu,
            hostel = snapshot.hostelDetails,
            laundry = snapshot.laundrySchedule,
            counselling = snapshot.hostelCounselling,
            library = snapshot.library,
            payments = snapshot.payments,
            transport = snapshot.transportData,
            buses = snapshot.buses,
            cabShareUser = snapshot.cabShareUser,
            cabHubs = snapshot.cabHubs,
            ffcsRegistration = snapshot.ffcsRegistration,
        ),
        engagement = Engagement(
            events = snapshot.events,
            registeredEvents = snapshot.registeredEvents,
            clubs = snapshot.clubs,
            circulars = snapshot.circulars,
        ),
        services = Services(
            lms = snapshot.lms,
            moodle = snapshot.moodleData,
            qcm = snapshot.qcmView,
            tasks = snapshot.tasks,
        ),
    )

    /**
     * The inverse of [fromLegacy].
     *
     * Present so the persisted format can become [DomainSnapshot] while ~43 screens still read
     * `AppDataSnapshot` flows. Once every screen reads a projection, this is deleted along with
     * [fromLegacy] and the sync engine writes the domain snapshot directly.
     */
    fun toLegacy(domain: DomainSnapshot): AppDataSnapshot {
        val creditsByCode = curriculumCredits(domain.academics.curriculum)
        val semesters = domain.academics.semesters.mapValues { (_, sem) ->
            SemesterData(
                semesterId = sem.id,
                semesterName = sem.name,
                gpa = sem.gpa,
                courses = sem.courses.mapValues { (_, c) -> courseToStored(c, creditsByCode) },
                // Exams are flattened across semesters in the domain shape, so they are regrouped
                // by the key they were bucketed on.
                exams = domain.schedule.exams
                    .filter { it.semesterId == sem.id }
                    .map { e ->
                        ExamItem(
                            courseCode = e.courseCode,
                            courseTitle = e.courseTitle,
                            classId = e.classId,
                            slot = e.slot,
                            examDate = e.date,
                            examSession = e.session,
                            reportingTime = e.reportingTime,
                            examTime = e.time,
                            venue = e.venue,
                            seatLocation = e.seatLocation,
                            seatNo = e.seatNo,
                        )
                    },
            )
        }

        return AppDataSnapshot(
            schemaVersion = AppDataSnapshot.SCHEMA_VERSION,
            academic = AcademicData(semesters = semesters),
            hostelDetails = domain.campus.hostel,
            messMenu = domain.campus.messMenu,
            laundrySchedule = domain.campus.laundry,
            hostelCounselling = domain.campus.counselling,
            calendar = domain.schedule.calendar.takeIf { it.isNotEmpty() }?.let { months ->
                CalendarRes(
                    success = true,
                    months = months.map { m ->
                        CalendarMonth(
                            month = m.label,
                            days = m.days.map { d ->
                                CalendarDay(
                                    date = d.date,
                                    events = d.events.map { e ->
                                        CalendarEvent(type = e.type, text = e.text, color = e.color, category = e.category)
                                    }
                                )
                            }
                        )
                    }
                )
            },
            calendarsList = domain.schedule.calendarsList,
            qcmView = domain.services.qcm,
            curriculum = domain.academics.curriculum,
            payments = domain.campus.payments,
            library = domain.campus.library,
            transportData = domain.campus.transport,
            buses = domain.campus.buses,
            lms = domain.services.lms,
            events = domain.engagement.events,
            registeredEvents = domain.engagement.registeredEvents,
            clubs = domain.engagement.clubs,
            circulars = domain.engagement.circulars,
            moodleData = domain.services.moodle,
            cabShareUser = domain.campus.cabShareUser,
            cabHubs = domain.campus.cabHubs,
            ffcsRegistration = domain.campus.ffcsRegistration,
            tasks = domain.services.tasks,
        )
    }

    private fun academicsFrom(snapshot: AppDataSnapshot, selectedSemesterId: String?): Academics {
        val creditsByCode = curriculumCredits(snapshot.curriculum)
        val semesters = snapshot.academic.semesters.mapValues { (id, sem) ->
            Semester(
                id = id,
                name = sem.semesterName,
                gpa = sem.gpa,
                courses = sem.courses.mapValues { (_, course) ->
                    courseFrom(course, creditsByCode, snapshot.curriculum)
                },
            )
        }
        return Academics(
            semesters = semesters,
            selectedSemesterId = selectedSemesterId?.takeIf { it in semesters }
                ?: resolveCurrentSemesterId(semesters.keys),
            curriculum = snapshot.curriculum,
        )
    }

    /**
     * Newest semester by VTOP's `CH<year><term><campus>` id.
     *
     * Not `maxByOrNull { it }`: those ids are fixed-width and zero-ish-padded inconsistently, so
     * comparing them as strings picks the lexicographically greatest, which is the *oldest*
     * semester. See `SnapshotRoundTripTest.selectedSemesterIsTheNewestNotTheLexicographicallyGreatest`.
     */
    internal fun resolveCurrentSemesterId(ids: Set<String>): String? =
        ids.maxWithOrNull(
            compareBy(
                // CH + 4-digit academic year + 2-digit term + campus code.
                { it.take(6).toIntOrNull() ?: 0 },
                { it.drop(6).take(2).toIntOrNull() ?: 0 },
                { it },
            )
        )

    /**
     * Curriculum is authoritative for credits: it is the official programme structure, whereas
     * the timetable's LTPJC column is a formatting convenience that parses unreliably.
     */
    private fun curriculumCredits(curriculum: CurriculumRes?): Map<String, String> {
        if (curriculum == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (detail in curriculum.details) {
            for (basket in detail.baskets) {
                for (item in basket.items) {
                    if (item.credits > 0) out[item.code] = item.credits.toString()
                }
            }
        }
        return out
    }

    /** Curriculum baskets a course belongs to, so the curriculum screen needs no second lookup. */
    private fun basketsFor(curriculum: CurriculumRes?, courseCode: String): List<BasketRef> {
        if (curriculum == null) return emptyList()
        val out = mutableListOf<BasketRef>()
        for (detail in curriculum.details) {
            for (basket in detail.baskets) {
                if (basket.items.any { it.code == courseCode }) {
                    out += BasketRef(
                        categoryCode = detail.code,
                        categoryName = detail.name,
                        basketTitle = basket.title,
                    )
                }
            }
        }
        return out
    }

    private fun courseFrom(
        stored: StoredCourse,
        creditsByCode: Map<String, String>,
        curriculum: CurriculumRes?,
    ): Course = Course(
        code = stored.courseCode,
        title = stored.courseTitle,
        type = stored.courseType,
        category = stored.category,
        credits = creditsByCode[stored.courseCode] ?: stored.credits,
        classId = stored.classId,
        slots = stored.slots,
        venue = stored.venue,
        faculty = stored.faculty,
        courseSystem = stored.courseSystem,
        attendance = stored.attendance?.let { a ->
            Attendance(
                attended = a.attendedClasses,
                total = a.totalClasses,
                percentage = a.attendancePercentage,
                logs = a.logs.map { AttendanceDay(it.date, it.status) },
            )
        },
        marks = stored.marks?.let { m ->
            Marks(
                classNbr = m.classNbr,
                assessments = m.assessments.map { a ->
                    Assessment(
                        name = a.title,
                        score = a.scoredMark,
                        maxMark = a.maxMark,
                        weightage = a.weightageMark,
                        weightagePercent = a.weightagePercent,
                        status = a.status,
                        component = a.component,
                    )
                },
                total = m.totalMark,
                maxTotal = m.maxMark,
                mergedFrom = m.mergedFrom,
            )
        },
        grade = stored.grade?.let { g ->
            Grade(
                letter = g.grade,
                grandTotal = g.grandTotal,
                details = g.details.orEmpty().map { d ->
                    GradeComponent(
                        name = d.component,
                        score = d.scoredMark,
                        maxMark = d.maxMark,
                        weightagePercent = d.weightagePercent,
                        status = d.status,
                        weightage = d.weightageMark,
                    )
                },
                range = g.range?.let {
                    GradeBands(
                        s = it.S, a = it.A, b = it.B, c = it.C,
                        d = it.D, e = it.E, f = it.F,
                    )
                },
            )
        },
        baskets = basketsFor(curriculum, stored.courseCode),
    )

    private fun courseToStored(course: Course, creditsByCode: Map<String, String>): StoredCourse = StoredCourse(
        courseCode = course.code,
        courseTitle = course.title,
        courseType = course.type,
        category = course.category,
        credits = creditsByCode[course.code] ?: course.credits,
        classId = course.classId,
        slots = course.slots,
        venue = course.venue,
        faculty = course.faculty,
        courseSystem = course.courseSystem,
        attendance = course.attendance?.let { a ->
            StoredAttendance(
                attendedClasses = a.attended,
                totalClasses = a.total,
                attendancePercentage = a.percentage,
                logs = a.logs.map { AttendanceLog(it.date, it.status) },
            )
        },
        marks = course.marks?.let { m ->
            StoredMarks(
                classNbr = m.classNbr,
                assessments = m.assessments.map { a ->
                    AssessmentItem(
                        title = a.name,
                        maxMark = a.maxMark,
                        weightagePercent = a.weightagePercent,
                        status = a.status,
                        scoredMark = a.score,
                        weightageMark = a.weightage,
                        component = a.component,
                    )
                },
                totalMark = m.total,
                maxMark = m.maxTotal,
                mergedFrom = m.mergedFrom,
            )
        },
        grade = course.grade?.let { g ->
            StoredGrade(
                grandTotal = g.grandTotal,
                grade = g.letter,
                details = g.details.map { d ->
                    GradeBreakdown(
                        component = d.name,
                        maxMark = d.maxMark,
                        weightagePercent = d.weightagePercent,
                        status = d.status,
                        scoredMark = d.score,
                        weightageMark = d.weightage,
                    )
                }.takeIf { it.isNotEmpty() },
                range = g.range?.let { GradeRange(it.s, it.a, it.b, it.c, it.d, it.e, it.f) },
            )
        },
    )

    private fun examFrom(
        semesterId: String,
        item: ExamItem,
    ): Exam = Exam(
        courseCode = item.courseCode,
        courseTitle = item.courseTitle,
        classId = item.classId,
        slot = item.slot,
        date = item.examDate,
        session = item.examSession,
        reportingTime = item.reportingTime,
        time = item.examTime,
        venue = item.venue,
        seatLocation = item.seatLocation,
        seatNo = item.seatNo,
        semesterId = semesterId,
    )

    /**
     * Placeholder for step 4: merges a freshly synced snapshot into the current domain snapshot
     * under a lock, so overlapping syncs cannot interleave.
     */
    suspend fun merge(current: DomainSnapshot, incoming: DomainSnapshot): DomainSnapshot =
        mutex.withLock {
            DomainSnapshot(
                academics = Academics(
                    // Incoming semesters win; anything it lacks is carried forward.
                    semesters = current.academics.semesters + incoming.academics.semesters,
                    selectedSemesterId = incoming.academics.selectedSemesterId
                        ?: current.academics.selectedSemesterId,
                ),
                schedule = incoming.schedule.takeIf { it.exams.isNotEmpty() || it.calendar.isNotEmpty() }
                    ?: current.schedule,
                campus = current.campus,
                engagement = current.engagement,
                services = current.services,
            )
        }

    /** Exposed so the merge policy is testable without a snapshot. */
    fun canonicalKeys(academics: Academics): List<String> =
        academics.semesters.values.flatMap { s -> s.courses.keys }
}
