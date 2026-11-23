package com.amazecc.app.shared.state

import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.ffcs.*
import com.amazecc.app.shared.ffcs.planner.FacultyPriorityMap
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * D-183 API CONSUMED HERE (com.amazecc.app.shared.ffcs.FfcsRuntimeAdapter):
 *   buildPlannerGenerateParams(courses, locks, blockedSlots, options,
 *                              rankingFactors?, facultyPriority): planner.GenerateParams
 *   fromPlannerParsed(plannerParsed): legacy ParsedCourse
 *   toLegacyTimetable(plannerTimetable): legacy TimetableState
 * The typed D-180 engine and D-181 runner already settle into the legacy
 * TimetableState shape, so no invented "mapping" step is inserted between them
 * and the UI. The adapter carries the real canonical request across the seam:
 * planner params, course identity and faculty priority all come from it.
 */

/**
 * Immutable snapshot of every user-driven input that affects generation.
 *
 * D-184: this is the seam between the legacy running state and the typed D-180/D-181
 * libraries. It is deliberately a plain value object so `buildEngineParams` is a pure,
 * testable function — no default ranking mode, factor list or drop behaviour is
 * fabricated here. An unset ranking field stays null and the engine runs its
 * un-ranked path.
 */
data class FfcsGenerationSnapshot(
    val allCourses: List<ParsedCourse>,
    val selectedCodes: List<String>,
    val locks: List<CourseLock>,
    val blockedSlots: Set<String>,
    val maxResults: Int,
    val uniqueFaculty: Boolean,
    val morningPreference: Boolean,
    val options: FfcsEngine.GenerateOptions,
    val rankingMode: RankingMode?,
    val rankingFactors: RankingFactorsConfig?,
    val facultyPriority: FacultyPriorityMap,
    val rankingPriorityOrder: List<RankingPriority>? = null
)

/** Honest terminal shape of one D-184 generation run, for the host and the tests. */
sealed class FfcsRuntimeOutcome {
    data class Generated(val timetables: List<TimetableState>, val droppedCodes: List<String>) : FfcsRuntimeOutcome()
    data class Failed(val reason: String, val code: FfcsErrorCode?, val droppedCodes: List<String>) : FfcsRuntimeOutcome()
}

object FfcsViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _allCourses = MutableStateFlow<List<ParsedCourse>>(emptyList())

    private val _courseOfferings = MutableStateFlow<List<Pair<String, List<CourseOffering>>>>(emptyList())
    val courseOfferings: StateFlow<List<Pair<String, List<CourseOffering>>>> = _courseOfferings.asStateFlow()

    private val _selectedCodes = MutableStateFlow<Set<String>>(emptySet())
    val selectedCodes: StateFlow<Set<String>> = _selectedCodes.asStateFlow()

    /** Insertion order of selected codes — the course/drop ladder's priority order. */
    private val _selectionOrder = MutableStateFlow<List<String>>(emptyList())
    val selectionOrder: StateFlow<List<String>> = _selectionOrder.asStateFlow()

    private val _locks = MutableStateFlow<List<CourseLock>>(emptyList())
    val locks: StateFlow<List<CourseLock>> = _locks.asStateFlow()

    private val _generatedTimetables = MutableStateFlow<List<TimetableState>>(emptyList())
    val generatedTimetables: StateFlow<List<TimetableState>> = _generatedTimetables.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _blockedSlots = MutableStateFlow<Set<String>>(emptySet())
    val blockedSlots: StateFlow<Set<String>> = _blockedSlots.asStateFlow()

    private val _uniqueFaculty = MutableStateFlow(false)
    val uniqueFaculty: StateFlow<Boolean> = _uniqueFaculty.asStateFlow()

    private val _morningPreference = MutableStateFlow(false)
    val morningPreference: StateFlow<Boolean> = _morningPreference.asStateFlow()

    private val _maxResults = MutableStateFlow(50)
    val maxResults: StateFlow<Int> = _maxResults.asStateFlow()

    private val _generateOptions = MutableStateFlow(FfcsEngine.GenerateOptions())
    val generateOptions: StateFlow<FfcsEngine.GenerateOptions> = _generateOptions.asStateFlow()

    // ── D-184 typed capability state. All values are real user choices; none are fabricated. ──
    private val _rankingMode = MutableStateFlow<RankingMode?>(null)
    val rankingMode: StateFlow<RankingMode?> = _rankingMode.asStateFlow()

    private val _rankingFactors = MutableStateFlow<RankingFactorsConfig?>(null)
    val rankingFactors: StateFlow<RankingFactorsConfig?> = _rankingFactors.asStateFlow()

    private val _facultyPriority = MutableStateFlow<FacultyPriorityMap>(emptyMap())
    val facultyPriority: StateFlow<FacultyPriorityMap> = _facultyPriority.asStateFlow()

    private val _dropRetryEnabled = MutableStateFlow(false)
    val dropRetryEnabled: StateFlow<Boolean> = _dropRetryEnabled.asStateFlow()

    /** Outcome copy from the last real attempt (never a fabricated success). */
    private val _lastRunOutcome = MutableStateFlow<FfcsRuntimeOutcome?>(null)
    val lastRunOutcome: StateFlow<FfcsRuntimeOutcome?> = _lastRunOutcome.asStateFlow()

    fun initFromCsv() {
        scope.launch {
            _isLoading.value = true
            try {
                val csvText = FfcsReportData.CSV_DATA
                val parsed = FfcsCourseProcessor.parseFFCSCSV(csvText)
                applyProcessedCourses(FfcsCourseProcessor.processCourses(parsed))
            } catch (e: Exception) {
                _errorMsg.value = "Failed to load courses: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    /**
     * Real-data entry point for the M3E bridge: processes real [courses] (e.g. the domain
     * academic snapshot, via the host's mapping) and publishes them as the catalog.
     */
    fun initFromCourses(courses: List<ParsedCourse>) {
        scope.launch {
            _isLoading.value = true
            try {
                applyProcessedCourses(FfcsCourseProcessor.processCourses(courses))
            } catch (e: Exception) {
                _errorMsg.value = "Failed to load courses: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    private fun applyProcessedCourses(processed: List<ParsedCourse>) {
        _allCourses.value = processed

        val offerings = processed
            .groupBy { it.code.uppercase() }
            .map { (code, courseList) ->
                val offs = courseList.map { c ->
                    CourseOffering(
                        faculty = c.faculty,
                        slot = c.slot,
                        room = c.room,
                        code = code,
                        title = c.title,
                        type = c.type,
                        credits = c.credits
                    )
                }
                code to offs
            }
            .sortedBy { it.first }

        _courseOfferings.value = offerings

        _locks.value = offerings.map { (code, offs) ->
            CourseLock(
                code = code,
                title = offs.firstOrNull()?.title ?: "",
                allowedOfferings = emptyList()
            )
        }

        _errorMsg.value = null
    }

    fun toggleCourse(code: String) {
        val current = _selectedCodes.value.toMutableSet()
        val selecting = !current.contains(code)
        if (selecting) current.add(code) else current.remove(code)
        _selectedCodes.value = current
        _selectionOrder.value = if (selecting) {
            _selectionOrder.value.filterNot { it.equals(code, ignoreCase = true) } + code
        } else {
            _selectionOrder.value.filterNot { it.equals(code, ignoreCase = true) }
        }
    }

    /**
     * Explicit programmatic selection for the modern bridge: [selected] selects the code,
     * deselecting it otherwise. Distinct from the web's default-on behavior — callers that
     * want a toggle use [toggleCourse].
     */
    fun selectCourse(code: String, selected: Boolean) {
        val current = _selectedCodes.value
        val next = if (selected) current + code else current - code
        if (next != current) {
            _selectedCodes.value = next
            _selectionOrder.value = if (selected) {
                _selectionOrder.value.filterNot { it.equals(code, ignoreCase = true) } + code
            } else {
                _selectionOrder.value.filterNot { it.equals(code, ignoreCase = true) }
            }
        }
    }

    fun selectOffering(code: String, offering: CourseOffering) {
        val current = _locks.value.toMutableList()
        val index = current.indexOfFirst { it.code.equals(code, ignoreCase = true) }
        if (index != -1) {
            val lock = current[index]
            val key = offering.toKey()
            val currentOfferings = lock.allowedOfferings.toMutableList()

            if (currentOfferings.contains(key)) currentOfferings.remove(key)
            else currentOfferings.add(key)

            current[index] = lock.copy(allowedOfferings = currentOfferings)
            _locks.value = current
        }
    }

    fun toggleBlockSlots(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val current = _blockedSlots.value.toMutableSet()
        val anyBlocked = keys.any { it in current }
        if (anyBlocked) current.removeAll(keys.toSet()) else current.addAll(keys)
        _blockedSlots.value = current
    }

    /** Add or remove one real offering key (`FACULTY|SLOT|ROOM`) from the block set. */
    fun setBlockedSlot(key: String, blocked: Boolean) {
        if (key.isBlank()) return
        val current = _blockedSlots.value
        val next = if (blocked) current + key else current - key
        if (next != current) _blockedSlots.value = next
    }

    fun clearBlockedSlots() {
        _blockedSlots.value = emptySet()
    }

    fun setUniqueFaculty(value: Boolean) {
        _uniqueFaculty.value = value
    }

    fun setMorningPreference(value: Boolean) {
        _morningPreference.value = value
    }

    fun setMaxResults(value: Int) {
        _maxResults.value = value.coerceIn(1, 500)
    }

    fun setCloseGaps(value: Boolean) {
        _generateOptions.value = _generateOptions.value.copy(closeGaps = value)
    }

    fun setLateStart(value: Boolean) {
        _generateOptions.value = _generateOptions.value.copy(lateStart = value)
    }

    fun setEarlyFinish(value: Boolean) {
        _generateOptions.value = _generateOptions.value.copy(earlyFinish = value)
    }

    // ── D-184 ranking / priority / drop configuration setters ────────────────

    /** Null means "no profile requested" — the engine keeps its un-ranked path. */
    fun setRankingMode(mode: RankingMode?) {
        _rankingMode.value = mode
    }

    /** Ordered, user-selected factor config; null means "no factor ranking requested". */
    fun setRankingFactors(config: RankingFactorsConfig?) {
        _rankingFactors.value = config
    }

    /** Enable/disable one factor while preserving the canonical engine order. */
    fun setRankingFactorEnabled(factor: RankingFactor, enabled: Boolean) {
        val current = _rankingFactors.value
        val enabledSet = if (current == null) {
            if (!enabled) return else emptySet()
        } else {
            current.order.filterNot { it in current.disabled }.toSet()
        }
        val next = if (enabled) enabledSet + factor else enabledSet - factor
        _rankingFactors.value = if (next.isEmpty()) {
            null
        } else {
            RankingFactorsConfig(
                order = RankingFactor.CANONICAL_ORDER,
                disabled = RankingFactor.CANONICAL_ORDER.filterNot { it in next }
            )
        }
    }

    /** Per-course ordered faculty preference (best first). Empty map = no preference. */
    fun setFacultyPriorityForCourse(code: String, orderedFaculty: List<String>) {
        val current = _facultyPriority.value.toMutableMap()
        val clean = orderedFaculty.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) current.remove(code.uppercase()) else current[code.uppercase()] = clean
        _facultyPriority.value = current
    }

    /** Move one faculty member up one rank inside a course's preference list. */
    fun promoteFaculty(code: String, faculty: String) {
        val key = code.uppercase()
        val list = _facultyPriority.value[key].orEmpty().toMutableList()
        val index = list.indexOf(faculty)
        if (index > 0) {
            list[index - 1] = list[index].also { list[index] = list[index - 1] }
            _facultyPriority.value = _facultyPriority.value + (key to list.toList())
        }
    }

    fun clearFacultyPriority(code: String? = null) {
        _facultyPriority.value = if (code == null) {
            emptyMap()
        } else {
            _facultyPriority.value.filterNot { it.key == code.uppercase() }
        }
    }

    /** Explicit opt-in for the drop ladder. Off by default: never fabricate a retry. */
    fun setDropRetryEnabled(enabled: Boolean) {
        _dropRetryEnabled.value = enabled
    }

    // ── Pure params builder (testable, state-driven, no fabricated defaults) ──

    /** Build the D-180 typed engine request from a real state snapshot. */
    fun buildEngineParams(snapshot: FfcsGenerationSnapshot): FfcsGenerationParams {
        val selectedUpper = snapshot.selectedCodes.map { it.uppercase() }.toSet()
        val lockMap = snapshot.locks.associateBy { it.code.uppercase() }

        // D-183: the canonical planner request is the source of truth for courses,
        // locks, blocked keys and faculty priority. Ranking factors are passed in
        // from the user's state (the adapter also derives them from the legacy
        // toggles when no explicit factor set exists).
        val legacyLocks = snapshot.selectedCodes.map { code ->
            val legacy = lockMap[code.uppercase()]
            CourseLock(
                code = code,
                title = legacy?.title ?: code,
                allowedOfferings = legacy?.allowedOfferings.orEmpty()
            )
        }
        val planner = FfcsRuntimeAdapter.buildPlannerGenerateParams(
            courses = snapshot.allCourses.filter { it.code.uppercase() in selectedUpper },
            locks = legacyLocks,
            blockedSlots = snapshot.blockedSlots,
            options = snapshot.options,
            rankingFactors = snapshot.rankingFactors?.let { config ->
                config.order.filterNot { it in config.disabled }
            },
            facultyPriority = snapshot.facultyPriority
        )

        // A blocked key is an offering key "FACULTY|SLOT|ROOM" (optionally with a
        // trailing id). The typed engine compares blockedSlots against slot spellings
        // only, so the composite "SLOT|ROOM" tail must be split — passing "A1|SJT-101"
        // would block nothing. A bare slot key is already in the right shape.
        val blocked = snapshot.blockedSlots.mapNotNull { key ->
            key.substringAfter('|', key)      // drop FACULTY (or keep a bare slot)
                .substringBefore('|')         // drop ROOM / trailing id
                .trim().uppercase().takeIf { it.isNotEmpty() }
        }.flatMap { it.split('+').map { slot -> slot.trim().uppercase() } }
            .filter { it.isNotEmpty() }
            .distinct()

        return FfcsGenerationParams(
            // Canonical → legacy identity mapping comes from the D-183 adapter.
            masterCourses = planner.masterCourses.map { FfcsRuntimeAdapter.fromPlannerParsed(it) },
            courseLocks = legacyLocks.map { lock ->
                FfcsLock(code = lock.code, title = lock.title, offerings = lock.allowedOfferings)
            },
            blockedSlots = blocked,
            uniqueFaculties = snapshot.uniqueFaculty,
            resultCap = snapshot.maxResults.coerceIn(1, 500),
            preference = if (snapshot.morningPreference) FfcsPreference.MORNING else FfcsPreference.NONE,
            strictTheoryPreference = true,
            sortBy = FfcsSortBy.BALANCED,
            // Only values actually chosen in state; null keeps the engine's plain path.
            rankingMode = snapshot.rankingMode,
            rankingPriorityOrder = snapshot.rankingPriorityOrder,
            rankingFactors = planner.rankingFactors,
            facultyPriority = planner.facultyPriority
        )
    }

    /** Current state → immutable snapshot used by both generation paths. */
    private fun currentSnapshot(): FfcsGenerationSnapshot {
        val order = _selectionOrder.value
        val selected = _selectedCodes.value
        val ordered = order.filter { it in selected } + (selected - order.toSet()).toList()
        return FfcsGenerationSnapshot(
            allCourses = _allCourses.value,
            selectedCodes = ordered,
            locks = _locks.value,
            blockedSlots = _blockedSlots.value,
            maxResults = _maxResults.value,
            uniqueFaculty = _uniqueFaculty.value,
            morningPreference = _morningPreference.value,
            options = _generateOptions.value,
            rankingMode = _rankingMode.value,
            rankingFactors = _rankingFactors.value,
            facultyPriority = _facultyPriority.value
        )
    }

    /** Legacy fallback: the original public generation path, kept working verbatim. */
    private suspend fun legacyGenerate(snapshot: FfcsGenerationSnapshot): List<TimetableState> {
        val lockMap = snapshot.locks.associateBy { it.code.uppercase() }
        val optionsPerCourse = snapshot.selectedCodes.mapNotNull { code ->
            val lock = lockMap[code.uppercase()]
            val allOpts = snapshot.allCourses.filter { it.code.uppercase() == code.uppercase() }
            val opts = if (lock != null && lock.allowedOfferings.isNotEmpty()) {
                allOpts.filter { it.offeringKey() in lock.allowedOfferings }
            } else {
                allOpts
            }
            opts.takeIf { it.isNotEmpty() }
        }
        if (optionsPerCourse.isEmpty()) throw IllegalStateException("No valid courses selected.")
        return FfcsEngine.generateTimetables(
            optionsPerCourse = optionsPerCourse,
            locks = snapshot.locks,
            blockedSlots = snapshot.blockedSlots,
            maxResults = snapshot.maxResults,
            uniqueFaculty = snapshot.uniqueFaculty,
            morningPreference = snapshot.morningPreference,
            options = snapshot.options
        )
    }

    /**
     * D-184 orchestration. Injectable seams keep the real typed path and the retry
     * behaviour testable without a device or a real catalogue.
     *  - [engine] is the D-180 typed engine;
     *  - [runner] is the D-181 worker bridge, used for the drop-retry second attempt;
     *  - [legacy] is the original public generation path, used only on typed failure.
     * All three run off Main (the engine/runner already dispatch to Default; the ViewModel
     * scope is Default too).
     */
    internal suspend fun runGeneration(
        snapshot: FfcsGenerationSnapshot,
        allowDropRetry: Boolean,
        engine: suspend (FfcsGenerationParams) -> FfcsEngineResult = { FfcsEngine.generate(it) },
        runner: suspend (GenerationParams) -> FfcsGenerateResult = { params ->
            FfcsGeneratorRunner.generateTimetablesAsync(
                params, GenerationOptions(maxResults = snapshot.maxResults)
            )
        },
        legacy: suspend (FfcsGenerationSnapshot) -> List<TimetableState> = { legacyGenerate(it) }
    ): FfcsRuntimeOutcome {
        var dropped = emptyList<String>()
        var typedReason: FfcsErrorCode? = null
        var typedSubject: String? = null

        val first = try {
            engine(buildEngineParams(snapshot))
        } catch (t: Throwable) {
            null // typed path unavailable → legacy fallback below
        }

        when (first) {
            is FfcsEngineResult.Ok -> {
                // D-180 settles into the same legacy TimetableState the UI and the
                // ICS/HTML exports already consume; no invented mapping is needed.
                return FfcsRuntimeOutcome.Generated(first.timetables.map { it.timetable }, dropped)
                    .also { finalize(it) }
            }
            is FfcsEngineResult.Error -> {
                typedReason = first.code
                typedSubject = first.subjectCode
            }
            is FfcsEngineResult.Cancelled -> Unit
            null -> Unit
        }

        // Drop ladder: only on a retryable typed failure, only when the user opted in,
        // and never on a crash. The second attempt goes through the D-181 runner.
        if (allowDropRetry && first is FfcsEngineResult.Error) {
            val failure = DropFailure("error", first.code.wire, first.subjectCode)
            if (isRetryableFailure(failure)) {
                val policy = DropPolicy(
                    courseOrder = snapshot.selectedCodes,
                    protectedCodes = snapshot.locks.filter { it.hasLock }.map { it.code.uppercase() }
                )
                val next = nextDrop(droppableTail(policy), emptyList(), failure)
                if (next != null) {
                    dropped = listOf(next)
                    val retrySnapshot = snapshot.copy(
                        selectedCodes = snapshot.selectedCodes.filterNot { it.equals(next, ignoreCase = true) }
                    )
                    val retrySelected = retrySnapshot.selectedCodes.map { it.uppercase() }.toSet()
                    val retryParams = GenerationParams(
                        masterCourses = retrySnapshot.allCourses.filter { it.code.uppercase() in retrySelected },
                        courseLocks = retrySnapshot.locks.filter { it.code.uppercase() in retrySelected }
                    )
                    val retry = try {
                        runner(retryParams)
                    } catch (t: Throwable) {
                        null
                    }
                    when (retry) {
                        is FfcsGenerateResult.Success -> {
                            // D-181's Success already carries the legacy timetables the
                            // UI consumes; no adapter output is fabricated here.
                            return FfcsRuntimeOutcome.Generated(retry.timetables, dropped)
                                .also { finalize(it) }
                        }
                        is FfcsGenerateResult.Error -> {
                            typedReason = retry.code
                            typedSubject = retry.subjectCode
                        }
                        is FfcsGenerateResult.Cancelled, null -> Unit
                    }
                }
            }
        }

        // Legacy fallback keeps the original public generation path alive.
        val fallback = try {
            legacy(snapshot)
        } catch (t: Throwable) {
            emptyList()
        }
        if (fallback.isNotEmpty()) {
            return FfcsRuntimeOutcome.Generated(fallback, dropped).also { finalize(it) }
        }

        val reason = when {
            typedReason != null -> describeTypedError(typedReason, typedSubject)
            first == null -> "Generation was unavailable; the legacy path returned no options."
            first is FfcsEngineResult.Cancelled -> "Generation was cancelled; no options were produced."
            else -> "No clash-free timetable matched these courses and filters."
        }
        return FfcsRuntimeOutcome.Failed(reason, typedReason, dropped).also { finalize(it) }
    }

    /** Honest failure copy derived only from the real typed code and subject course. */
    private fun describeTypedError(code: FfcsErrorCode, subject: String?): String {
        val suffix = subject?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when (code) {
            FfcsErrorCode.NO_COURSES_SELECTED -> "Select at least one course to generate."
            FfcsErrorCode.NO_VALID_SLOTS ->
                "No valid offering remains for a selected course$suffix. Clear its lock or unblock a slot."
            FfcsErrorCode.NO_CONFLICT_FREE ->
                "No clash-free timetable fits every selected course."
            FfcsErrorCode.BELOW_MIN_HALF_DAYS ->
                "No timetable meets the minimum half-day filter."
            FfcsErrorCode.BELOW_MIN_FACULTY_SCORES ->
                "No timetable meets the faculty-score filter."
            FfcsErrorCode.NO_SEPARATE_LABS ->
                "No timetable keeps labs on separate days."
            FfcsErrorCode.NO_CONSECUTIVE_LABS ->
                "No timetable avoids consecutive labs."
            FfcsErrorCode.ERROR -> "Generation failed$suffix."
        }
    }

    private fun finalize(outcome: FfcsRuntimeOutcome) {
        when (outcome) {
            is FfcsRuntimeOutcome.Generated -> {
                _generatedTimetables.value = outcome.timetables
                _errorMsg.value = null
            }
            is FfcsRuntimeOutcome.Failed -> {
                _generatedTimetables.value = emptyList()
                _errorMsg.value = outcome.reason
            }
        }
        _lastRunOutcome.value = outcome
        _isGenerating.value = false
    }

    fun generate() {
        val selected = _selectedCodes.value
        if (selected.isEmpty()) {
            _errorMsg.value = "Select at least one course to generate."
            _lastRunOutcome.value = null
            return
        }

        val snapshot = currentSnapshot()
        val retryAllowed = _dropRetryEnabled.value

        _isGenerating.value = true
        _errorMsg.value = null
        _generatedTimetables.value = emptyList()
        _lastRunOutcome.value = null

        scope.launch {
            runGeneration(snapshot, retryAllowed)
        }
    }

    /** Explicit second attempt from the UI, after a retryable failure. */
    fun retryWithDrop() {
        val selected = _selectedCodes.value
        if (selected.isEmpty()) {
            _errorMsg.value = "Select at least one course to generate."
            return
        }
        val snapshot = currentSnapshot()
        _isGenerating.value = true
        _errorMsg.value = null
        scope.launch {
            runGeneration(snapshot, allowDropRetry = true)
        }
    }

    /** RFC 5545 ICS text of the first generated timetable, for export parity with the web app. */
    fun exportIcs(): String? =
        FfcsEngine.buildFfcsIcs(_generatedTimetables.value.firstOrNull()?.courses ?: emptyList())

    fun clearResults() {
        _generatedTimetables.value = emptyList()
        _errorMsg.value = null
        _lastRunOutcome.value = null
    }

    fun resetSelection() {
        _selectedCodes.value = emptySet()
        _selectionOrder.value = emptyList()
        _generatedTimetables.value = emptyList()
        _errorMsg.value = null
        _blockedSlots.value = emptySet()
        _rankingMode.value = null
        _rankingFactors.value = null
        _facultyPriority.value = emptyMap()
        _dropRetryEnabled.value = false
        _lastRunOutcome.value = null
    }
}
