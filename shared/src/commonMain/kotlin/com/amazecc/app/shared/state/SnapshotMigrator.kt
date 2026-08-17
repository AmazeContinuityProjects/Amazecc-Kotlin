package com.amazecc.app.shared.state

/**
 * Pure v1 → v2 migration for the academic schema.
 *
 * See docs/features/schemas/03-migration.md for the full ruleset.
 *
 * Mirror-resolution rules (v1 single-semester fields that duplicate
 * `allSemester*` entries under an unrecoverable key):
 * 1. if any `allSemester*` entry is value-equal to the mirror → covered, skip;
 * 2. else if the mirror carries its own semesterId and it exists → use it;
 * 3. else if exactly one semester holds data for that domain → use it;
 * 4. else land in [FALLBACK_SEMESTER] (self-heals on next full sync).
 */
object SnapshotMigrator {

    const val FALLBACK_SEMESTER = "__v1_mirror"

    fun toV2(legacy: LegacyAppDataSnapshot): AppDataSnapshot {
        var academic = AcademicData()

        legacy.allSemesterAttendance.forEach { (sem, res) ->
            if (res != null) academic = AcademicMerge.upsertAttendance(academic, sem, res)
        }
        legacy.allSemesterMarks.forEach { (sem, res) ->
            academic = AcademicMerge.upsertMarks(academic, sem, res)
        }
        legacy.allGrades?.grades.orEmpty().forEach { (sem, res) ->
            if (res != null) academic = AcademicMerge.upsertGrades(academic, sem, res.gpa, res.grades)
        }
        legacy.allSemesterExams.forEach { (sem, res) ->
            if (res != null) academic = AcademicMerge.upsertExams(academic, sem, res)
        }

        legacy.attendance?.let { mirror ->
            val covered = legacy.allSemesterAttendance.values.any { it == mirror }
            if (!covered) {
                academic = AcademicMerge.upsertAttendance(
                    academic,
                    resolveSemester(academic, mirror.semesterId) { sem -> sem.courses.values.any { it.attendance != null } },
                    mirror
                )
            }
        }
        legacy.marks?.let { mirror ->
            val covered = legacy.allSemesterMarks.values.any { it == mirror }
            if (!covered) {
                academic = AcademicMerge.upsertMarks(
                    academic,
                    resolveSemester(academic, null) { sem -> sem.courses.values.any { it.marks != null } },
                    mirror
                )
            }
        }
        legacy.examSchedule?.let { mirror ->
            val covered = legacy.allSemesterExams.values.any { it == mirror }
            if (!covered) {
                academic = AcademicMerge.upsertExams(
                    academic,
                    resolveSemester(academic, null) { sem -> sem.exams.isNotEmpty() },
                    mirror
                )
            }
        }
        legacy.timetable?.let { mirror ->
            academic = AcademicMerge.upsertTimetable(
                academic,
                resolveSemester(academic, mirror.semesterId) { sem -> sem.courses.isNotEmpty() },
                mirror
            )
        }

        return AppDataSnapshot(
            schemaVersion = 2,
            academic = academic,
            hostelDetails = legacy.hostelDetails,
            messMenu = legacy.messMenu,
            laundrySchedule = legacy.laundrySchedule,
            hostelCounselling = legacy.hostelCounselling,
            calendar = legacy.calendar,
            calendarsList = legacy.calendarsList,
            qcmView = legacy.qcmView,
            curriculum = legacy.curriculum,
            payments = legacy.payments,
            library = legacy.library,
            transportData = legacy.transportData,
            buses = legacy.buses,
            lms = legacy.lms,
            events = legacy.events,
            registeredEvents = legacy.registeredEvents,
            clubs = legacy.clubs,
            circulars = legacy.circulars,
            moodleData = legacy.moodleData,
            cabShareUser = legacy.cabShareUser,
            cabHubs = legacy.cabHubs,
            ffcsRegistration = legacy.ffcsRegistration,
            tasks = legacy.tasks
        )
    }

    private fun resolveSemester(
        academic: AcademicData,
        preferredId: String?,
        hasData: (SemesterData) -> Boolean
    ): String {
        if (preferredId != null && preferredId in academic.semesters) return preferredId
        val withData = academic.semesters.filterValues(hasData).keys
        return if (withData.size == 1) withData.first() else FALLBACK_SEMESTER
    }
}
