package com.amazecc.app.shared.state

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.TimetableRes
import com.amazecc.app.shared.vtop.VtopComponent
import com.amazecc.app.shared.vtop.VtopCourseCode

/**
 * Pure derivation helpers over the unified academic schema.
 *
 * Replaces the timetable slot-derivation that used to live inside
 * [AppSanitizers] (which only cleans transport payloads now) and the
 * OD-hour counter that used to live in WidgetDataUtils.
 */
object AcademicDerivers {

    /**
     * Every day on which a slot code is taught, with that day's time for it.
     *
     * A slot id does **not** name one day. `A1` is Monday period 1 *and* Wednesday period 2 (see
     * `AmazeCC/src/data/campus/chennai.json`, which maps period -> day -> slot id), so
     * `A1`/`F1`/`A2`/`F2` appear on MON and WED, and `D1`/`D2` on MON and THU. Flattening the
     * day-keyed map into `Map<slot, day>` silently kept only the last writer, which put every
     * A/F slot on WED and every D slot on THU and discarded MON entirely.
     *
     * So the lookup keeps every match, and a course is emitted once per day it could meet.
     */
    private val slotDays: Map<String, List<Pair<String, String>>> by lazy {
        val acc = mutableMapOf<String, MutableList<Pair<String, String>>>()
        SlotMap.map.forEach { (day, slots) ->
            slots.forEach { (slot, time) -> acc.getOrPut(slot) { mutableListOf() }.add(day to time) }
        }
        acc
    }

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
            val type = componentLabel(course.courseType)
                ?: course.category?.takeIf { it.contains("Lab", true) }?.let { VtopComponent.LO.label }
                ?: course.courseType
            course.slots.forEach { slotCode ->
                // A slot can be scheduled on more than one day, so emit one entry per day rather
                // than guessing one. See slotDays.
                val days = slotDays[slotCode] ?: return@forEach
                days.forEach { (day, time) ->
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
                // Attendance-bearing course count is the primary signal, semesterId only breaks
                // ties. The comparator had these the other way round, so it picked the highest
                // semester id and ignored the count entirely.
                compareBy<SemesterData> { it.courses.values.count { c -> c.attendance != null } }
                    .thenBy { it.semesterId }
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

    /**
     * "ETH" / "ELA" / "Theory Only" / "Lab Only" for a course's component, else null.
     *
     * Read from `courseType` rather than the code: keys are bare course codes, so the code no
     * longer encodes which half this is.
     */
    fun componentLabel(courseType: String?): String? =
        VtopComponent.entries.firstOrNull { it.label.isNotEmpty() && courseType?.contains(it.label, true) == true }?.label

    /** "ETH" / "ELA" for an embedded component, else null. */
    fun embeddedComponentLabel(rawCode: String, courseType: String? = null): String? =
        componentLabel(courseType)?.takeIf { VtopCourseCode.fromTypeLabel(it).isEmbedded }

    /**
     * True when this course is a lab component, by course identity.
     *
     * This is the canonical lab test — there were three, and the OD counter disagreed with the
     * attendance screen about a course whose code ended in `(L)` while its type cell was blank.
     * Prefers the resolved `courseType`; falls back to slot codes only when the type is
     * unlabelled, since a lab sharing a slot with its theory half would otherwise be misread.
     *
     * Real VTOP codes are bare (`BACSE102`), so [VtopCourseCode.componentOf] returns UNKNOWN for
     * them and the slot check stands.
     */
    fun isLabCourse(courseCode: String, courseType: String?, slots: List<String>): Boolean {
        val label = componentLabel(courseType)
        if (label != null) return VtopCourseCode.fromTypeLabel(label).isLab
        // A suffixed code ("18CSC301L", "18CSC301(L)") marks the lab half even when the type
        // cell is empty. Reading only `courseType` and `slots` misread a suffixed code with a
        // non-lab slot as theory; componentOf() also understands the real VTOP wording
        // ("Embedded Lab" / "Lab Only") through the type hint.
        val fromCode = VtopCourseCode.componentOf(courseCode, courseType)
        if (fromCode != VtopComponent.UNKNOWN) return fromCode.isLab
        return slots.any { it.uppercase().startsWith("L") }
    }

    fun StoredCourse.isLabCourse(): Boolean = isLabCourse(courseCode, courseType, slots)

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
            credits = credits,
            component = componentLabel(courseType),
            totalMark = marks?.totalMark,
            maxMark = marks?.maxMark,
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
     *
     * The OD Tracker screen counter, which is what every other OD-hours surface has to call.
     * The status vocabulary and the multiplier live in [Projections]; the lab test is
     * [isLabCourse] — both of which this used to re-derive with its own wording.
     */
    fun computeODHours(sem: SemesterData): Int =
        Projections.odHours(
            sem.courses.values.map { course ->
                val odCount = course.attendance?.logs.orEmpty().count { Projections.isOdStatus(it.status) }
                odCount to course.isLabCourse()
            }
        )

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
