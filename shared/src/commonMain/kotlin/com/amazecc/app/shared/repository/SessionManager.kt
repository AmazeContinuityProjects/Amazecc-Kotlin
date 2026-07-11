package com.amazecc.app.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    private val _cookies = MutableStateFlow<String?>(null)
    val cookies: StateFlow<String?> = _cookies.asStateFlow()

    private val _csrf = MutableStateFlow<String?>(null)
    val csrf: StateFlow<String?> = _csrf.asStateFlow()

    private val _authorizedID = MutableStateFlow<String?>(null)
    val authorizedID: StateFlow<String?> = _authorizedID.asStateFlow()

    private val _clubToken = MutableStateFlow<String?>(null)
    val clubToken: StateFlow<String?> = _clubToken.asStateFlow()

    // Settings
    val currentTheme = MutableStateFlow("system") // system, light, dark
    val currentAccent = MutableStateFlow("ocean") // ocean, forest, lavender, sunset
    val onboardingCompleted = MutableStateFlow(false)
    val lmsAuthenticated = MutableStateFlow(false)
    
    // Academic preferences
    val decimalValues = MutableStateFlow(false)
    val isDayscholarWithBus = MutableStateFlow(false)
    val residentialStatus = MutableStateFlow("hosteller") // hosteller, dayscholar
    val friendlyName = MutableStateFlow("")
    val postLoginCompleted = MutableStateFlow(false)

    // Dashboard customization settings
    val dashboardWidgets = MutableStateFlow(listOf(
        DashboardWidget.GREETING,
        DashboardWidget.METRICS,
        DashboardWidget.ALERTS,
        DashboardWidget.TODAY_CLASSES,
        DashboardWidget.UPCOMING_EXAMS,
        DashboardWidget.QUICK_ACTIONS,
        DashboardWidget.ACADEMICS_HUB,
        DashboardWidget.CAMPUS_SERVICES,
        DashboardWidget.RECENT_ACTIVITY
    ))
    val hiddenWidgets = MutableStateFlow(emptySet<DashboardWidget>())
    val collapsedWidgets = MutableStateFlow(emptySet<DashboardWidget>())
    val compactMetricsView = MutableStateFlow(false)

    val isLoggedIn: Boolean
        get() = _cookies.value != null && _csrf.value != null && _authorizedID.value != null

    fun saveSession(cookies: String, csrf: String, authorizedID: String, clubToken: String?) {
        _cookies.value = cookies
        _csrf.value = csrf
        _authorizedID.value = authorizedID
        _clubToken.value = clubToken
    }

    fun completeOnboarding() {
        onboardingCompleted.value = true
    }

    fun clearSession() {
        _cookies.value = null
        _csrf.value = null
        _authorizedID.value = null
        _clubToken.value = null
        lmsAuthenticated.value = false
        decimalValues.value = false
        isDayscholarWithBus.value = false
        residentialStatus.value = "hosteller"
        friendlyName.value = ""
        postLoginCompleted.value = false
    }

    fun moveWidgetUp(widget: DashboardWidget) {
        val list = dashboardWidgets.value.toMutableList()
        val index = list.indexOf(widget)
        if (index > 0) {
            list.removeAt(index)
            list.add(index - 1, widget)
            dashboardWidgets.value = list
        }
    }

    fun moveWidgetDown(widget: DashboardWidget) {
        val list = dashboardWidgets.value.toMutableList()
        val index = list.indexOf(widget)
        if (index != -1 && index < list.lastIndex) {
            list.removeAt(index)
            list.add(index + 1, widget)
            dashboardWidgets.value = list
        }
    }

    fun toggleWidgetVisibility(widget: DashboardWidget) {
        val set = hiddenWidgets.value.toMutableSet()
        if (set.contains(widget)) set.remove(widget) else set.add(widget)
        hiddenWidgets.value = set
    }

    fun toggleWidgetCollapse(widget: DashboardWidget) {
        val set = collapsedWidgets.value.toMutableSet()
        if (set.contains(widget)) set.remove(widget) else set.add(widget)
        collapsedWidgets.value = set
    }
}

enum class DashboardWidget(val displayName: String) {
    GREETING("Greeting Header"),
    METRICS("Academic Summary Metrics"),
    ALERTS("Attendance Watchlist & Alerts"),
    TODAY_CLASSES("Today's Timetable Preview"),
    UPCOMING_EXAMS("Upcoming Exam Schedule"),
    QUICK_ACTIONS("Quick Navigation Grid"),
    ACADEMICS_HUB("Study Workspace Shortcuts"),
    CAMPUS_SERVICES("Campus Services Hub"),
    RECENT_ACTIVITY("Recent Sync & Notices")
}

