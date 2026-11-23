package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.domain.CHENNAI_CAMPUS_SCHEMA
import com.amazecc.app.shared.domain.EmbeddedLinkCode
import com.amazecc.app.shared.ffcs.planner.CampusSchema as PlannerCampusSchema
import com.amazecc.app.shared.ffcs.planner.CourseLock as PlannerCourseLock
import com.amazecc.app.shared.ffcs.planner.GenerateParams as PlannerParams
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as PlannerParsedCourse
import com.amazecc.app.shared.ffcs.planner.Preference
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableMetrics as PlannerMetrics
import com.amazecc.app.shared.ffcs.planner.TimetablePeriod
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetable
import com.amazecc.app.shared.ffcs.planner.splitSlots as plannerSplitSlots

/**
 * D-183 — the pure bridge between the two staged FFCS type families:
 * the running app's lowercase `com.amazecc.app.shared.ffcs` models and the
 * canonical `com.amazecc.app.shared.ffcs.planner` wire contract (D-171).
 *
 * Neither model file is touched. Every function is a total, side-effect-free
 * field mapping: no optional value is invented, no ranking score is defaulted
 * to zero, and no field is renamed or re-cased beyond the two contracts'
 * declared spellings (UPPERCASE CSV names, CREDITS as String, SLOT '+'-joined).
 *
 * Fields that exist on only one side are *not* smuggled into another field:
 *  - canonical `ERP_ID` / `CLASS_ID` / `CLASS_IDS` / `BATCH` / `LINK_ID` /
 *    `ORIGINAL_CODE` / `EMPLOYEE_SCHOOL` have no legacy `ParsedCourse` slot, so
 *    a legacy→canonical conversion leaves them null;
 *  - canonical `rankingScore` / `variants` / `rankingBreakdown` /
 *    `facultySummary` / `rankingComponents` and `AddedCourse.employeeId` /
 *    `classId` / `classIds` / `batch` have no legacy `TimetableState` slot;
 *  - legacy `firstStartMin` / `lastEndMin` have no canonical source, so they
 *    keep the legacy model's own defaults instead of a guessed clock value.
 *
 * Wave 3 can therefore feed real data across the seam without editing either
 * model file; anything needing the lost fields should stay on the canonical
 * side of the bridge.
 */

/**
 * The legacy generate-option type is nested in `FfcsEngine`; this alias gives
 * wave 3 the flat `com.amazecc.app.shared.ffcs.GenerateOptions` spelling from
 * the D-183 contract without moving the declaration out of `FfcsEngine`
 * (which would edit that file). Both spellings are the very same type.
 */
typealias GenerateOptions = FfcsEngine.GenerateOptions

object FfcsRuntimeAdapter {

    // ── ParsedCourse ────────────────────────────────────────────────────────

    /**
     * Lowercase legacy course → canonical UPPERCASE report row.
     * The legacy model carries only the seven required cells, so every
     * canonical-only cell stays null — in particular no ERP id is guessed
     * from the faculty name.
     */
    fun toPlannerParsed(legacy: ParsedCourse): PlannerParsedCourse = PlannerParsedCourse(
        CODE = legacy.code,
        TITLE = legacy.title,
        TYPE = legacy.type,
        CREDITS = legacy.credits,
        ROOM = legacy.room,
        SLOT = legacy.slot,
        FACULTY = legacy.faculty,
    )

    /** Canonical report row → lowercase legacy course, exact strings preserved. */
    fun fromPlannerParsed(planner: PlannerParsedCourse): ParsedCourse = ParsedCourse(
        code = planner.CODE,
        title = planner.TITLE,
        type = planner.TYPE,
        credits = planner.CREDITS,
        room = planner.ROOM,
        slot = planner.SLOT,
        faculty = planner.FACULTY,
    )

    // ── Locks (legacy exact-offering keys → canonical three-list lock) ──────

    /**
     * Legacy `CourseLock.allowedOfferings` entries are `FACULTY|SLOT|ROOM`
     * (see `ParsedCourse.offeringKey()` / `CourseOffering.toKey()`); the canonical
     * lock carries the same exact ids plus optional slot/faculty filters.
     *
     * The exact id is always kept verbatim so `offeringMatches` can still use
     * either the modern or the legacy identity. Slot and faculty filters are
     * *derived* from the key's segments because that is real data already
     * present in the key, never a guess. `EmbeddedLinkCode.baseCode` decides
     * whether the leading segment is this course's code: a code-prefixed key
     * is `CODE|FACULTY|SLOT|ROOM`, while a plain legacy key is
     * `FACULTY|SLOT|ROOM`. The offsets matter — without them a code-prefixed
     * key reads the faculty cell as the slot, and a code-shaped segment (e.g.
     * `L1`) must never become a faculty filter.
     */
    private fun isCodeShaped(value: String): Boolean {
        val base = EmbeddedLinkCode.baseCode(value)
        return base.isNotEmpty() && base.none { it.isWhitespace() } && base.any { it.isDigit() }
    }

    private fun toPlannerLock(lock: CourseLock): PlannerCourseLock {
        val allowedSlots = LinkedHashSet<String>()
        val allowedFaculty = LinkedHashSet<String>()
        val offerings = ArrayList<String>()
        val lockBase = EmbeddedLinkCode.baseCode(lock.code)

        for (raw in lock.allowedOfferings) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split('|')
            if (parts.size < 2) {
                // A code-level key names the course, not one of its offerings.
                // With no slot/faculty segment there is nothing to derive; an
                // unrelated id is still preserved verbatim for offeringMatches.
                val base = EmbeddedLinkCode.baseCode(trimmed)
                if (base.isEmpty() || lockBase.isEmpty() || base != lockBase) offerings.add(raw)
                continue
            }
            val codePrefix = lockBase.isNotEmpty() &&
                EmbeddedLinkCode.baseCode(parts[0]) == lockBase
            val facultyPart = parts.getOrNull(if (codePrefix) 1 else 0)
            if (facultyPart != null && !isCodeShaped(facultyPart)) {
                allowedFaculty.add(facultyPart)
            }
            val slotPart = parts.getOrNull(if (codePrefix) 2 else 1)
            if (slotPart != null) allowedSlots.addAll(plannerSplitSlots(slotPart))
            offerings.add(raw)
        }

        return PlannerCourseLock(
            code = lock.code,
            title = lock.title,
            allowedSlots = allowedSlots.toList(),
            allowedFaculty = allowedFaculty.toList(),
            offerings = offerings.takeIf { it.isNotEmpty() },
        )
    }

    // ── Schema ──────────────────────────────────────────────────────────────

    /**
     * The canonical generator params need the planner's serializable schema.
     * It is projected from the existing, hand-ported web schema in `domain`
     * (`CHENNAI_CAMPUS_SCHEMA`): the clock strings, lunch flags and day→token
     * maps are copied exactly, with no hand-invented slot ids.
     */
    private fun plannerSchema(): PlannerCampusSchema {
        fun periods(source: List<com.amazecc.app.shared.domain.SchemaPeriod>?) =
            source.orEmpty().map { period ->
                TimetablePeriod(
                    start = period.start,
                    end = period.end,
                    lunch = period.lunch,
                    days = period.days,
                )
            }
        return PlannerCampusSchema(
            theory = periods(CHENNAI_CAMPUS_SCHEMA.theory),
            lab = periods(CHENNAI_CAMPUS_SCHEMA.lab),
        )
    }

    // ── GenerateParams ──────────────────────────────────────────────────────

    /**
     * Build the canonical, structured-clone-safe generator request.
     *
     * Only data handed in is used. `friends`, time bounds, result caps and all
     * the other optional web fields stay at their honest absences (empty or
     * null) because the legacy options object has no value for them. The three
     * legacy toggles are the canonical ranking factors `GAPS`, `LATE_START`
     * and `EARLY_FINISH`: when the caller supplies [rankingFactors] it wins
     * verbatim (web rule: one ranking path per call), otherwise the toggles
     * supply the order; with neither, `rankingFactors` stays null.
     */
    fun buildPlannerGenerateParams(
        courses: List<ParsedCourse>,
        locks: List<CourseLock>,
        blockedSlots: Set<String>,
        options: GenerateOptions,
        rankingFactors: List<RankingFactor>? = null,
        facultyPriority: Map<String, List<String>> = emptyMap(),
    ): PlannerParams {
        val derivedFactors = buildList<RankingFactor> {
            if (options.closeGaps) add(RankingFactor.GAPS)
            if (options.lateStart) add(RankingFactor.LATE_START)
            if (options.earlyFinish) add(RankingFactor.EARLY_FINISH)
        }
        // `null` means "not supplied" → derive from the toggles. An explicitly
        // empty list is supplied too, and means "no factors" → stay unset.
        // These two cases must never collapse into the same branch.
        val factorsConfig = if (rankingFactors != null) {
            rankingFactors.takeIf { it.isNotEmpty() }?.let { RankingFactorsConfig(order = it) }
        } else if (derivedFactors.isEmpty()) {
            null
        } else {
            RankingFactorsConfig(order = derivedFactors)
        }

        return PlannerParams(
            schema = plannerSchema(),
            masterCourses = courses.map(::toPlannerParsed),
            courseLocks = locks.map(::toPlannerLock),
            blockedSlots = blockedSlots.toList(),
            friends = emptyList(),
            preference = Preference.NONE,
            syncFriendClasses = false,
            maximizeFreeTimeFriends = emptyList(),
            uniqueFaculties = false,
            noLimit = false,
            minHalfDays = 0,
            sortBy = if (options.closeGaps) SortBy.COMPACTNESS else SortBy.BALANCED,
            rankingFactors = factorsConfig,
            facultyPriority = facultyPriority.takeIf { it.isNotEmpty() },
        )
    }

    // ── Settled results ─────────────────────────────────────────────────────

    /** Canonical added course → legacy added course (the nine shared cells). */
    private fun toLegacyAddedCourse(course: com.amazecc.app.shared.ffcs.planner.AddedCourse) = AddedCourse(
        id = course.id,
        code = course.code,
        title = course.title,
        slots = course.slots,
        faculty = course.faculty,
        venue = course.venue,
        credits = course.credits,
        type = course.type,
        color = course.color,
    )

    /**
     * Canonical timetable → legacy timetable. Course count and every shared
     * course cell survive; metrics are adapted at the boundary exactly as
     * `FfcsMetrics.calculateTimetableMetrics` does (canonical gaps are minutes,
     * the legacy field is whole hours). A missing canonical score stays absent
     * — the legacy model has no such field, and no zero is written anywhere.
     */
    fun toLegacyTimetable(planner: PlannerTimetable): TimetableState = TimetableState(
        id = planner.id,
        name = planner.name,
        courses = planner.courses.map(::toLegacyAddedCourse),
        metrics = planner.metrics?.let(::toLegacyMetrics)
            ?: TimetableMetrics(halfDays = 0, gaps = 0),
    )

    /**
     * Canonical nine-field metrics → legacy metrics, using the same minutes→
     * hours adaptation as the existing legacy boundary. `bestFriendMatches`
     * has no legacy field and is deliberately dropped, not folded into the
     * social score.
     */
    fun toLegacyMetrics(planner: PlannerMetrics): TimetableMetrics = TimetableMetrics(
        halfDays = planner.halfDays,
        gaps = planner.gaps / 60,
        gapsPerDay = planner.gapsPerDay.mapValues { (_, minutes) -> minutes / 60 },
        gapDetails = planner.gapDetails.map { detail ->
            GapDetail(
                day = detail.day.value,
                startMin = detail.startMin,
                endMin = detail.endMin,
                durationMins = detail.durationMins,
                fromClass = detail.fromClass,
                toClass = detail.toClass,
                fromTime = detail.fromTime,
                toTime = detail.toTime,
            )
        },
        buildingDashes = planner.buildingDashes,
        dashDetails = planner.dashDetails.map { detail ->
            DashDetail(
                fromClass = detail.fromClass,
                toClass = detail.toClass,
                fromTime = detail.fromTime,
                toTime = detail.toTime,
                day = detail.day,
                fromBlock = detail.fromBlock,
                toBlock = detail.toBlock,
            )
        },
        socialScore = planner.socialScore,
        isLongWeekend = planner.isLongWeekend,
    )
}
