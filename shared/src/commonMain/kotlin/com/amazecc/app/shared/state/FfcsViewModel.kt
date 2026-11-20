package com.amazecc.app.shared.state

import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.ffcs.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FfcsViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _allCourses = MutableStateFlow<List<ParsedCourse>>(emptyList())

    private val _courseOfferings = MutableStateFlow<List<Pair<String, List<CourseOffering>>>>(emptyList())
    val courseOfferings: StateFlow<List<Pair<String, List<CourseOffering>>>> = _courseOfferings.asStateFlow()

    private val _selectedCodes = MutableStateFlow<Set<String>>(emptySet())
    val selectedCodes: StateFlow<Set<String>> = _selectedCodes.asStateFlow()

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

    private val _morningPreference = MutableStateFlow(false)

    private val _maxResults = MutableStateFlow(50)

    private val _generateOptions = MutableStateFlow(FfcsEngine.GenerateOptions())
    val generateOptions: StateFlow<FfcsEngine.GenerateOptions> = _generateOptions.asStateFlow()

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
                val first = courseList.first()
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
        if (current.contains(code)) current.remove(code) else current.add(code)
        _selectedCodes.value = current
    }

    /**
     * Explicit programmatic selection for the modern bridge: [selected] selects the code,
     * deselecting it otherwise. Distinct from the web's default-on behavior — callers that
     * want a toggle use [toggleCourse].
     */
    fun selectCourse(code: String, selected: Boolean) {
        val current = _selectedCodes.value
        val next = if (selected) current + code else current - code
        if (next != current) _selectedCodes.value = next
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

    fun generate() {
        val selected = _selectedCodes.value
        if (selected.isEmpty()) {
            _errorMsg.value = "Select at least one course to generate."
            return
        }

        val locksSnapshot = _locks.value
        val blockedSnapshot = _blockedSlots.value
        val maxResultsSnapshot = _maxResults.value
        val uniqueFacultySnapshot = _uniqueFaculty.value
        val morningPrefSnapshot = _morningPreference.value
        val allCoursesSnapshot = _allCourses.value

        _isGenerating.value = true
        _errorMsg.value = null
        _generatedTimetables.value = emptyList()

        scope.launch {
            try {
                val lockMap = locksSnapshot.associateBy { it.code.uppercase() }

                val droppedCodes = mutableListOf<String>()
                val optionsPerCourse = selected.mapNotNull { code ->
                    val lock = lockMap[code.uppercase()]
                    val allOpts = allCoursesSnapshot.filter { it.code.uppercase() == code.uppercase() }

                    val opts = if (lock != null && lock.allowedOfferings.isNotEmpty()) {
                        allOpts.filter { it.offeringKey() in lock.allowedOfferings }
                    } else {
                        allOpts
                    }

                    if (opts.isEmpty()) {
                        droppedCodes.add(code)
                        null
                    } else {
                        opts
                    }
                }

                if (droppedCodes.isNotEmpty()) {
                    throw Exception(
                        "No available offerings for: ${droppedCodes.joinToString(", ")}. " +
                            "Deselect them or clear their locks/blocks."
                    )
                }
                if (optionsPerCourse.isEmpty()) {
                    throw Exception("No valid courses selected. Check your locks.")
                }

                val results = FfcsEngine.generateTimetables(
                    optionsPerCourse = optionsPerCourse,
                    locks = locksSnapshot,
                    blockedSlots = blockedSnapshot,
                    maxResults = maxResultsSnapshot,
                    uniqueFaculty = uniqueFacultySnapshot,
                    morningPreference = morningPrefSnapshot,
                    options = _generateOptions.value
                )

                _generatedTimetables.value = results
            } catch (e: Exception) {
                _errorMsg.value = e.message ?: "Generation failed."
                _generatedTimetables.value = emptyList()
            }
            _isGenerating.value = false
        }
    }

    /** RFC 5545 ICS text of the first generated timetable, for export parity with the web app. */
    fun exportIcs(): String? =
        FfcsEngine.buildFfcsIcs(_generatedTimetables.value.firstOrNull()?.courses ?: emptyList())

    fun clearResults() {
        _generatedTimetables.value = emptyList()
        _errorMsg.value = null
    }

    fun resetSelection() {
        _selectedCodes.value = emptySet()
        _generatedTimetables.value = emptyList()
        _errorMsg.value = null
        _blockedSlots.value = emptySet()
    }
}
