package com.amazecc.app.shared.state

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.TimetableRes

/**
 * Pure derivation helpers over the unified academic schema.
 *
 * Replaces the timetable slot-derivation that used to live inside
 * [AppSanitizers] (which only cleans transport payloads now) and the
 * OD-hour counter that used to live in WidgetDataUtils.
 */
object AcademicDerivers {

    private val slotIndex: Map<String, Pair<String, String>> by lazy {
        buildMap {
            SlotMap.map.forEach { (day, slots) ->
                slots.forEach { (slot, time) -> put(slot, day to time) }
            }
        }
    }

    fun courseTypeOf(rawCode: String): String? = when {
        rawCode.endsWith("(L)", ignoreCase = true) -> "Lab Only"
        rawCode.endsWith("(T)", ignoreCase = true) -> "Theory Only"
        else -> null
    }

    fun cleanCourseCode(raw: String): String {
        val code = raw.trim().removeSuffix("(L)").removeSuffix("(T)").trim()
        return code.takeIf { it.isNotBlank() } ?: raw.trim()
    }

    /** Splits "C2+TC2 | AB3-305" or "C2+TC2 - AB3-305" into slot codes and venue (slot side must resolve in [slotIndex]). */
    fun splitSlotVenue(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val clean = raw.replace(Regex("\\s+"), " ").trim()
        val parts = clean.split("|").map { it.trim() }
        val left = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val right = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        if (left != null) {
            val tokens = left.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.all { it in slotIndex }) return left to right
        }
        val dashIdx = clean.lastIndexOf(" - ")
        val dLeft = if (dashIdx >= 0) clean.substring(0, dashIdx).trim() else clean
        val dRight = if (dashIdx >= 0) clean.substring(dashIdx + 3).trim().takeIf { it.isNotBlank() } else null
        val dTokens = dLeft.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        if (dTokens.isNotEmpty() && dTokens.all { it in slotIndex }) return dLeft to dRight
        return null to clean
    }

    /**
     * Cleans the raw timetable courseInfo rows (identity fields only — no slot
     * derivation). Used by the store's timetable upsert.
     */
    fun cleanTimetableInfo(res: TimetableRes?): TimetableRes? {
        if (res == null) return null
        val info = res.courseInfo.orEmpty().mapNotNull { ci ->
            val code = ci.courseCode?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!AppSanitizers.isValidCourseCode(code)) return@mapNotNull null
            ci.copy(
                courseCode = code,
                course = cleanCourseTitle(ci.course),
                LTPJC = ci.LTPJC?.cleanText(),
                category = ci.category?.cleanText(),
                classId = ci.classId?.cleanText(),
                slotVenue = ci.slotVenue?.cleanText()?.takeIf { it.isNotBlank() },
                facultyDetails = cleanFacultyDetails(ci.facultyDetails)
            )
        }
        return res.copy(courseInfo = info)
    }

    /** The weekly timetable (day × slot × course with resolved time) for a semester. */
    fun buildWeeklyTimetable(sem: SemesterData): List<TimetableSlot> {
        val slots = mutableListOf<TimetableSlot>()
        sem.courses.values.forEach { course ->
            val type = courseTypeOf(course.courseCode)
                ?: course.category?.takeIf { it.contains("Lab", true) }?.let { "Lab Only" }
                ?: course.courseType
            course.slots.forEach { slotCode ->
                val (day, time) = slotIndex[slotCode] ?: return@forEach
                slots += TimetableSlot(
                    day = day,
                    slotName = slotCode,
                    time = time,
                    courseCode = course.courseCode,
                    courseTitle = course.courseTitle,
                    courseType = type,
                    venue = course.venue,
                    faculty = course.faculty,
                    classId = course.classId,
                    category = course.category,
                    attendancePercentage = percentOf(course)
                )
            }
        }
        return slots.sortedWith(compareBy({ it.day ?: "" }, { slotStartMinutes(it.time) }, { it.slotName ?: "" }))
    }

    fun percentOf(course: StoredCourse): Double? =
        course.attendance?.attendancePercentage?.toDoubleOrNull()

    /**
     * Resolves the "current semester" deterministically (used by widget and
     * notification processes, which have no AppState): the most recent
     * semester that has any attendance-bearing courses (the semester you are
     * actively attending classes in), ties broken by semesterId.
     */
    fun resolveCurrentSemester(academic: AcademicData): SemesterData? {
        if (academic.semesters.isEmpty()) return null
        return academic.semesters.values
            .filter { it.courses.values.any { c -> c.attendance != null } }
            .maxWithOrNull(
                compareBy<SemesterData> { it.semesterId }
                    .thenBy { it.courses.values.count { c -> c.attendance != null } }
            )
    }

    /** Adapts a stored course into the transport [AttendanceItem] shape for UI pipelines that still consume it. */
    fun StoredCourse.toAttendanceItem(): com.amazecc.app.shared.model.AttendanceItem =
        com.amazecc.app.shared.model.AttendanceItem(
            courseCode = courseCode,
            courseTitle = courseTitle,
            courseType = courseType,
            slotName = slots.joinToString("+"),
            faculty = faculty ?: "",
            slotVenue = venue,
            totalClasses = attendance?.totalClasses ?: 0,
            attendedClasses = attendance?.attendedClasses ?: 0,
            attendancePercentage = attendance?.attendancePercentage ?: "",
            credits = credits,
            category = category,
            logs = attendance?.logs.orEmpty()
        )

    /** "Embedded Theory" / "Embedded Lab" for suffixed embedded course codes, else null. */
    fun embeddedComponentLabel(rawCode: String): String? = when {
        rawCode.endsWith("(T)", ignoreCase = true) -> "Embedded Theory"
        rawCode.endsWith("(L)", ignoreCase = true) -> "Embedded Lab"
        else -> null
    }

    /** True when the stored course is the lab component of an embedded pair. */
    fun StoredCourse.isLabCourse(): Boolean =
        courseType.contains("Lab", ignoreCase = true) ||
            slots.any { it.uppercase().startsWith("L") } ||
            courseCode.endsWith("(L)", ignoreCase = true)

    /** Adapts a stored course into the transport [MarksCourseItem] shape for UI pipelines that still consume it. */
    fun StoredCourse.toMarksCourseItem(): com.amazecc.app.shared.model.MarksCourseItem =
        com.amazecc.app.shared.model.MarksCourseItem(
            classNbr = classId ?: "",
            courseCode = courseCode,
            courseTitle = courseTitle,
            courseType = courseType,
            courseSystem = courseSystem ?: "",
            faculty = faculty ?: "",
            slot = slots.joinToString("+"),
            assessments = marks?.assessments.orEmpty()
        )

    /** Adapts a stored grade into the transport [GradeItem] shape for UI pipelines that still consume it. */
    fun StoredCourse.toGradeItem(): com.amazecc.app.shared.model.GradeItem =
        com.amazecc.app.shared.model.GradeItem(
            courseCode = courseCode,
            courseTitle = courseTitle,
            courseType = courseType,
            grandTotal = grade?.grandTotal ?: "",
            grade = grade?.grade ?: "",
            details = grade?.details,
            range = grade?.range
        )

    /**
     * Total on-duty hours across a semester's courses (lab = 2h, theory = 1h).
     * Mirrors the OD Tracker screen counter: statuses "on duty"/"od"/"onduty" count as OD.
     */
    fun computeODHours(sem: SemesterData): Int {
        var hours = 0
        for (course in sem.courses.values) {
            val statuses = course.attendance?.logs.orEmpty().map { log -> log.status.trim().lowercase() }
            val odCount = statuses.count { it == "on duty" || it == "od" || it == "onduty" }
            if (odCount > 0) {
                val isLab = course.slots.firstOrNull()?.startsWith("L") == true
                    || course.courseType.startsWith("Lab", ignoreCase = true)
                    || course.courseCode.endsWith("(L)", ignoreCase = true)
                hours += odCount * (if (isLab) 2 else 1)
            }
        }
        return hours
    }

    private fun slotStartMinutes(time: String?): Int {
        val start = time?.split("-")?.firstOrNull()?.trim() ?: return 0
        val parts = start.split(":")
        var h = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        if (h < 8) h += 12
        return h * 60 + m
    }

    /** Collapses runs of whitespace (incl. `\t`/`\n`) into single spaces. */
    private fun String?.cleanText(): String? {
        if (this == null) return null
        val collapsed = replace(Regex("\\s+"), " ").trim()
        return collapsed.takeIf { it.isNotBlank() }
    }

    /** Strips the "CODE - " prefix and " ( Lab Only )"-style suffix from a course title. */
    private fun cleanCourseTitle(raw: String?): String? {
        val s = raw.cleanText() ?: return null
        val noType = s.replace(
            Regex("\\(\\s*(lab|theory|embedded lab|embedded theory)\\s*\\)", RegexOption.IGNORE_CASE), " "
        ).trim()
        val noCode = noType.replace(Regex("^[A-Za-z0-9]{3,10}\\s*[-–]\\s*"), "").trim()
        return noCode.takeIf { it.isNotBlank() } ?: noType
    }

    /** "NAME - DEPT" pairs → "NAME (DEPT)", preserving names like "52282 SHEENA CHRISTABEL PRAVIN". */
    private fun cleanFacultyDetails(raw: String?): String? {
        val s = raw.cleanText() ?: return null
        val parts = s.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) "${parts[0]} (${parts.drop(1).joinToString(" ")})" else s
    }
}
