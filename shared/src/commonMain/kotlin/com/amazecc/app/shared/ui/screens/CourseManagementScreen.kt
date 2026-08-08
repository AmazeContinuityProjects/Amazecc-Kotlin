package com.amazecc.app.shared.ui.screens

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.ArrearResponse

@Composable
fun CourseManagementScreen() {
    TabbedKeyValueScreen(
        title = "Course Management",
        description = "Option changes, EXC, minors",
        loadingText = "Loading course data...",
        tabLabels = listOf("Course Option", "EXC Registration", "Minor/Honour", "Completion"),
        endpointKeys = listOf("course-option-change", "exc-registration", "minor-honour", "course-completion")
    ) { ep ->
        when (ep) {
            "course-option-change" -> AmazeClient.getCourseOptionChange()
            "exc-registration" -> AmazeClient.getExcRegistration()
            "minor-honour" -> AmazeClient.getMinorHonour()
            "course-completion" -> AmazeClient.getCourseCompletion()
            else -> ArrearResponse(success = false, message = "Unknown")
        }
    }
}
