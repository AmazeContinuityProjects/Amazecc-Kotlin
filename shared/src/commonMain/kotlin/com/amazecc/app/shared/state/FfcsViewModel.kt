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
    val allCourses: StateFlow<List<ParsedCourse>> = _allCourses.asStateFlow()

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
    val uniqueFaculty: StateFlow<Boolean> = _uniqueFaculty.asStateFlow()

    private val _morningPreference = MutableStateFlow(false)
    val morningPreference: StateFlow<Boolean> = _morningPreference.asStateFlow()

    private val _maxResults = MutableStateFlow(50)
    val maxResults: StateFlow<Int> = _maxResults.asStateFlow()

    fun initFromCsv() {
        scope.launch {
            _isLoading.value = true
            try {
                val csvText = FfcsReportData.CSV_DATA
                val parsed = FfcsCourseProcessor.parseFFCSCSV(csvText)
                val processed = FfcsCourseProcessor.processCourses(parsed)
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
                        allowedSlots = emptyList(),
                        allowedFaculty = emptyList(),
                        offerings = offs.map { it.toKey() }
                    )
                }

                _errorMsg.value = null
            } catch (e: Exception) {
                _errorMsg.value = "Failed to load courses: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun initFromParsedCourses(parsed: List<ParsedCourse>) {
        val processed = FfcsCourseProcessor.processCourses(parsed)
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
                allowedSlots = emptyList(),
                allowedFaculty = emptyList(),
                offerings = offs.map { it.toKey() }
            )
        }
    }

    fun toggleCourse(code: String) {
        val current = _selectedCodes.value.toMutableSet()
        if (current.contains(code)) current.remove(code) else current.add(code)
        _selectedCodes.value = current
    }

    fun setLock(code: String, allowedSlots: List<String>, allowedFaculty: List<String>) {
        val current = _locks.value.toMutableList()
        val index = current.indexOfFirst { it.code.equals(code, ignoreCase = true) }
        if (index != -1) {
            current[index] = current[index].copy(
                allowedSlots = allowedSlots,
                allowedFaculty = allowedFaculty
            )
            _locks.value = current
        }
    }

    fun selectOffering(code: String, offering: CourseOffering) {
        val current = _locks.value.toMutableList()
        val index = current.indexOfFirst { it.code.equals(code, ignoreCase = true) }
        if (index != -1) {
            val lock = current[index]
            val currentSlots = lock.allowedSlots.toMutableList()
            val currentFaculty = lock.allowedFaculty.toMutableList()

            if (currentSlots.contains(offering.slot) && currentFaculty.contains(offering.faculty)) {
                currentSlots.remove(offering.slot)
                currentFaculty.remove(offering.faculty)
            } else {
                currentSlots.add(offering.slot)
                if (!currentFaculty.contains(offering.faculty)) {
                    currentFaculty.add(offering.faculty)
                }
            }

            current[index] = lock.copy(
                allowedSlots = currentSlots,
                allowedFaculty = currentFaculty
            )
            _locks.value = current
        }
    }

    fun toggleBlockSlot(slotKey: String) {
        val current = _blockedSlots.value.toMutableSet()
        if (current.contains(slotKey)) current.remove(slotKey) else current.add(slotKey)
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

    fun generate() {
        val selected = _selectedCodes.value
        if (selected.isEmpty()) {
            _errorMsg.value = "Select at least one course to generate."
            return
        }

        _isGenerating.value = true
        _errorMsg.value = null
        _generatedTimetables.value = emptyList()

        scope.launch {
            try {
                val lockMap = _locks.value.associateBy { it.code.uppercase() }
                val optionsPerCourse = selected.mapNotNull { code ->
                    val lock = lockMap[code.uppercase()]
                    val allOpts = _allCourses.value.filter { it.code.uppercase() == code.uppercase() }

                    if (lock != null && lock.allowedFaculty.isNotEmpty() && lock.allowedSlots.isNotEmpty()) {
                        allOpts.filter { it.faculty in lock.allowedFaculty && it.slot in lock.allowedSlots }
                    } else if (lock != null && lock.allowedFaculty.isNotEmpty()) {
                        allOpts.filter { it.faculty in lock.allowedFaculty }
                    } else if (lock != null && lock.allowedSlots.isNotEmpty()) {
                        allOpts.filter { it.slot in lock.allowedSlots }
                    } else {
                        allOpts
                    }.ifEmpty { null }
                }

                if (optionsPerCourse.isEmpty()) {
                    throw Exception("No valid courses selected. Check your locks.")
                }

                val results = FfcsEngine.generateTimetables(
                    optionsPerCourse = optionsPerCourse,
                    locks = _locks.value,
                    blockedSlots = _blockedSlots.value,
                    maxResults = _maxResults.value,
                    uniqueFaculty = _uniqueFaculty.value,
                    morningPreference = _morningPreference.value
                )

                _generatedTimetables.value = results
            } catch (e: Exception) {
                _errorMsg.value = e.message ?: "Generation failed."
                _generatedTimetables.value = emptyList()
            }
            _isGenerating.value = false
        }
    }

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
