package com.amazecc.app.shared.state

/**
 * Op registry for the sync engine (web parity: `src/lib/sync-engine/operation-registry.ts`).
 *
 * Each registered op carries the metadata the web registry publishes — auth domain,
 * criticality, relative cost, category and a human-readable label/description —
 * so the progress sheet, mode editor and scheduling can present and gate sync work
 * without knowing which module it belongs to.
 */

enum class SyncOpAuth { VTOP, EVENTHUB, NONE }
enum class SyncOpCost { LIGHT, MEDIUM, HEAVY }
enum class SyncOpCategory { ACADEMICS, STAY, CAMPUS, PROFILE, SOCIAL }

data class SyncOpMeta(
    /** Human label for logs, pickers and the editor. */
    val label: String,
    /** One-line explanation shown in the mode editor. */
    val description: String,
    val category: SyncOpCategory,
    /** Relative network cost, used for low-data warnings. */
    val cost: SyncOpCost,
    val needsSemester: Boolean = false,
)

data class SyncOp(
    val name: String,
    val auth: SyncOpAuth,
    val critical: Boolean = false,
    val dependsOn: List<String> = emptyList(),
    val meta: SyncOpMeta? = null,
)

object SyncOpRegistry {

    /** Web parity ops (`operations.ts`): name → op. Web op names are kept verbatim. */
    private val byName: Map<String, SyncOp> = mapOf(
        "attendanceMarks" to SyncOp(
            "attendanceMarks", SyncOpAuth.VTOP, critical = true,
            meta = SyncOpMeta("Attendance & Marks", "Current-semester attendance and marks — the daily check.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "marksStats" to SyncOp(
            "marksStats", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Cohort statistics", "FFCS-enrollment-gated cohort curves for the caller's classes.", SyncOpCategory.ACADEMICS, SyncOpCost.MEDIUM),
        ),
        "grades" to SyncOp(
            "grades", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Grades", "Unified grades summary and semester history.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "schedule" to SyncOp(
            "schedule", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Exam schedule", "Exam timetable for the active semester.", SyncOpCategory.ACADEMICS, SyncOpCost.MEDIUM, needsSemester = true),
        ),
        "hostel" to SyncOp(
            "hostel", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Hostel details", "Room, mess, laundry and leave data.", SyncOpCategory.STAY, SyncOpCost.MEDIUM),
        ),
        "calendar" to SyncOp(
            "calendar", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Academic calendar", "Milestone dates and academic calendar.", SyncOpCategory.ACADEMICS, SyncOpCost.MEDIUM, needsSemester = true),
        ),
        "all-grades" to SyncOp(
            "all-grades", SyncOpAuth.VTOP,
            meta = SyncOpMeta("All grades history", "Every semester's grades — adapted from unified grades endpoint.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY),
        ),
        "coursePage" to SyncOp(
            "coursePage", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Course details", "Lecture plan and reference materials for every current-semester course.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "core" to SyncOp(
            "core", SyncOpAuth.VTOP, critical = true,
            meta = SyncOpMeta("Core data bundle", "Grades, schedule, hostel, calendar, history and photos in one bundle.", SyncOpCategory.PROFILE, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "officialOd" to SyncOp(
            "officialOd", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Official OD records", "On-duty records for the active semester.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT, needsSemester = true),
        ),
        "whoami" to SyncOp(
            "whoami", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Identity & profile", "Unified identity/profile/contact/credentials/acknowledgement/dayboarder/EPT data.", SyncOpCategory.PROFILE, SyncOpCost.MEDIUM),
        ),
        "studentProfile" to SyncOp(
            "studentProfile", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Profile details", "Student profile/identity via /api/whoami; publishes the active reg number.", SyncOpCategory.PROFILE, SyncOpCost.MEDIUM),
        ),
        "pastAttendance" to SyncOp(
            "pastAttendance", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Past semester attendance", "Freezes attendance for every older semester.", SyncOpCategory.PROFILE, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "fresher" to SyncOp(
            "fresher", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Fresher / EPT data", "EPT schedule and acknowledgement via /api/whoami.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "buses" to SyncOp(
            "buses", SyncOpAuth.NONE,
            meta = SyncOpMeta("Bus routes", "Public bus routes — no login needed.", SyncOpCategory.STAY, SyncOpCost.LIGHT),
        ),
        "transport" to SyncOp(
            "transport", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Transport data", "Transport registration for the student.", SyncOpCategory.STAY, SyncOpCost.MEDIUM),
        ),
        "events" to SyncOp(
            "events", SyncOpAuth.EVENTHUB,
            meta = SyncOpMeta("Registered events", "Event Hub registrations plus the public catalogue.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "bulk" to SyncOp(
            "bulk", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Additional records cache", "Registrations, credentials, library dues and payments cache.", SyncOpCategory.PROFILE, SyncOpCost.HEAVY),
        ),
        "lms" to SyncOp(
            "lms", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Moodle data", "LMS courses and deadlines. Needs Moodle credentials when passed.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY),
        ),
        "circulars" to SyncOp(
            "circulars", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Circulars", "Official circulars and announcements.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
        "curriculum" to SyncOp(
            "curriculum", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Curriculum data", "Degree credit baskets, catalog and planner source.", SyncOpCategory.ACADEMICS, SyncOpCost.MEDIUM),
        ),
        "qcm" to SyncOp(
            "qcm", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Quality Circle Meeting (QCM)", "Course committee feedback, suggestions and faculty remarks.", SyncOpCategory.ACADEMICS, SyncOpCost.LIGHT, needsSemester = true),
        ),
        "ffcs" to SyncOp(
            "ffcs", SyncOpAuth.VTOP,
            meta = SyncOpMeta("FFCS", "Registration, feedback, wishlist, exam schedule and QCM in one call.", SyncOpCategory.ACADEMICS, SyncOpCost.HEAVY, needsSemester = true),
        ),
        "social" to SyncOp(
            "social", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Friends & groups", "Pushes your timetable, pulls peers and grant secrets.", SyncOpCategory.SOCIAL, SyncOpCost.MEDIUM, needsSemester = true),
        ),
        "finance" to SyncOp(
            "finance", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Finance", "Payments, dues, receipts, wallet and receipt details.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "notifications-subscribe" to SyncOp(
            "notifications-subscribe", SyncOpAuth.NONE,
            meta = SyncOpMeta("Push subscribe", "Register push notification subscription.", SyncOpCategory.PROFILE, SyncOpCost.LIGHT),
        ),
        // Kotlin-side ops without a current web counterpart (finance bundle splits).
        "payments" to SyncOp(
            "payments", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Payments", "Payment dues and receipts.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "libraryDue" to SyncOp(
            "libraryDue", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Library due", "Library books and dues.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "clubs" to SyncOp(
            "clubs", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Clubs", "Club listings.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
        "bankInfo" to SyncOp(
            "bankInfo", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Bank information", "Student account details for fee payment.", SyncOpCategory.PROFILE, SyncOpCost.MEDIUM),
        ),
        "dayboarder" to SyncOp(
            "dayboarder", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Dayboarder info", "Whether the student is a day Scholar or hosteller.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
        "registrationSchedule" to SyncOp(
            "registrationSchedule", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Registration schedule", "Upcoming registration and academic schedules.", SyncOpCategory.PROFILE, SyncOpCost.MEDIUM),
        ),
        "universityDay" to SyncOp(
            "universityDay", SyncOpAuth.VTOP,
            meta = SyncOpMeta("University day", "Exam university day/date.", SyncOpCategory.PROFILE, SyncOpCost.LIGHT),
        ),
        "apaaRID" to SyncOp(
            "apaaRID", SyncOpAuth.VTOP,
            meta = SyncOpMeta("APAAR ID", "Unique learner identity number (Aadhaar-linked).", SyncOpCategory.PROFILE, SyncOpCost.LIGHT),
        ),
        "freeClassrooms" to SyncOp(
            "freeClassrooms", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Free classrooms", "Occupancy-based free rooms for booking.", SyncOpCategory.CAMPUS, SyncOpCost.MEDIUM),
        ),
        "documents" to SyncOp(
            "documents", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Documents", "Hall tickets, ID cards and certificates.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
        "feedbackStatus" to SyncOp(
            "feedbackStatus", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Feedback status", "Course feedback completion status.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
        "calendarsList" to SyncOp(
            "calendarsList", SyncOpAuth.VTOP,
            meta = SyncOpMeta("Calendars list", "All calendars the student has access to.", SyncOpCategory.CAMPUS, SyncOpCost.LIGHT),
        ),
    )

    /** Module → registered op name (used by the mode editor, per-op gates and progress sheet). */
    val MODULE_OP_NAMES: Map<SyncModule, String> = mapOf(
        SyncModule.ATTENDANCE to "attendanceMarks",
        SyncModule.ALL_SEMESTER_ATTENDANCE to "pastAttendance",
        SyncModule.TIMETABLE to "schedule",
        SyncModule.MARKS to "marksStats",
        SyncModule.GRADES to "grades",
        SyncModule.CURRICULUM to "curriculum",
        SyncModule.HOSTEL_DETAILS to "hostel",
        SyncModule.EXAM_SCHEDULE to "schedule",
        SyncModule.CALENDAR to "calendar",
        SyncModule.CALENDARS_LIST to "calendarsList",
        SyncModule.PAYMENTS to "payments",
        SyncModule.LIBRARY to "libraryDue",
        SyncModule.TRANSPORT to "transport",
        SyncModule.BUSES to "buses",
        SyncModule.LMS to "lms",
        SyncModule.EVENTS to "events",
        SyncModule.CLUBS to "clubs",
        SyncModule.QCM_VIEW to "qcm",
        SyncModule.STUDENT_PROFILE to "studentProfile",
        SyncModule.CIRCULARS to "circulars",
        SyncModule.PROFILE_IMAGES to "whoami",
        SyncModule.CREDENTIALS to "whoami",
        SyncModule.BANK_INFO to "bankInfo",
        SyncModule.DAYBOARDER to "dayboarder",
        SyncModule.EPT_SCHEDULE to "fresher",
        SyncModule.REGISTRATION_SCHEDULE to "registrationSchedule",
        SyncModule.UNIVERSITY_DAY to "universityDay",
        SyncModule.APAAR_ID to "apaaRID",
        SyncModule.MOODLE to "lms",
        SyncModule.FREE_CLASSROOMS to "freeClassrooms",
        SyncModule.DOCUMENTS to "documents",
        SyncModule.OD_DETAILS to "officialOd",
        SyncModule.FEEDBACK_STATUS to "feedbackStatus",
    )

    /** Ops registered at runtime (module-bound variants). */
    private val runtime: MutableMap<String, SyncOp> = mutableMapOf()

    fun getOp(name: String): SyncOp? = runtime[name] ?: byName[name]

    fun allOps(): List<SyncOp> = byName.values.toList() + runtime.values.toList()

    /** Register an op at runtime (e.g. a module-bound variant). */
    fun registerOp(op: SyncOp) {
        runtime[op.name] = op
    }

    /** Metadata for one op, if the op registered any. */
    fun getOpMeta(name: String): SyncOpMeta? = getOp(name)?.meta

    /** Every registered op with its metadata (label falls back to the op name). */
    fun listOpMeta(): List<SyncOp> =
        allOps().map { op -> op.copy(meta = op.meta ?: SyncOpMeta(op.name, "", SyncOpCategory.PROFILE, SyncOpCost.MEDIUM)) }

    /** Module-bound op metadata for the mode editor and the progress sheet. */
    fun moduleOpMeta(module: SyncModule): SyncOpMeta? =
        MODULE_OP_NAMES[module]?.let { name -> byName[name]?.meta }
}
