package com.amazecc.app.shared.state

import com.amazecc.app.shared.ffcs.CourseLock
import com.amazecc.app.shared.ffcs.FfcsEngine
import com.amazecc.app.shared.ffcs.ParsedCourse
import com.amazecc.app.shared.ffcs.FfcsCourseProcessor
import com.amazecc.app.shared.ffcs.TimetableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object FfcsViewModel {
    
    // UI State
    private val _targetCourses = MutableStateFlow<List<List<ParsedCourse>>>(emptyList())
    val targetCourses: StateFlow<List<List<ParsedCourse>>> = _targetCourses

    private val _locks = MutableStateFlow<List<CourseLock>>(emptyList())
    val locks: StateFlow<List<CourseLock>> = _locks

    private val _generatedTimetables = MutableStateFlow<List<TimetableState>>(emptyList())
    val generatedTimetables: StateFlow<List<TimetableState>> = _generatedTimetables

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    
    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val scope = CoroutineScope(Dispatchers.Main)

    fun addMockTargetCourses() {
        val rawMockData = listOf(
            ParsedCourse("CSE1001", "Problem Solving", "ETH", "3", "SJT 101", "L1+L2", "Prof A"),
            ParsedCourse("CSE1001", "Problem Solving", "ETH", "3", "SJT 102", "L31+L32", "Prof B"),
            ParsedCourse("MAT1011", "Calculus", "ETH", "4", "TT 201", "A1", "Prof X"),
            ParsedCourse("MAT1011", "Calculus", "ELA", "0", "TT Lab", "L3", "Prof X"), // Will be grouped!
            ParsedCourse("PHY1001", "Physics", "ETH", "3", "SMV 101", "D1", "Prof P")
        )
        
        val processed = FfcsCourseProcessor.processCourses(rawMockData)
        val grouped = processed.groupBy { it.code }.values.toList()
        
        _targetCourses.value = grouped
        
        // Initialize locks for each course
        _locks.value = grouped.map { options ->
            CourseLock(
                code = options.first().code,
                title = options.first().title,
                allowedSlots = emptyList(),
                allowedFaculty = emptyList(),
                offerings = options.map { "${it.faculty}|${it.slot}|${it.room}" }
            )
        }
    }
    
    fun setLock(code: String, allowedSlots: List<String>, allowedFaculty: List<String>) {
        val current = _locks.value.toMutableList()
        val index = current.indexOfFirst { it.code == code }
        if (index != -1) {
            current[index] = current[index].copy(allowedSlots = allowedSlots, allowedFaculty = allowedFaculty)
            _locks.value = current
        }
    }

    fun generate() {
        if (_targetCourses.value.isEmpty()) return
        _isGenerating.value = true
        _errorMsg.value = null
        
        scope.launch {
            try {
                val results = FfcsEngine.generateTimetables(_targetCourses.value, _locks.value, FriendsViewModel.friends.value)
                _generatedTimetables.value = results
            } catch (e: Exception) {
                _errorMsg.value = e.message ?: "Failed to generate timetables."
                _generatedTimetables.value = emptyList()
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clear() {
        _generatedTimetables.value = emptyList()
        _errorMsg.value = null
    }
}
