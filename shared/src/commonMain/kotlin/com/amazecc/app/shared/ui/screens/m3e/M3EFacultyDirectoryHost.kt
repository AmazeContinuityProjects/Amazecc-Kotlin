package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.FacultyInfoScreen

/**
 * D-038 host for the *live* faculty directory and per-faculty free-slot timetable.
 *
 * `FacultyInfoScreen` already holds the real implementation: it fetches the school list and
 * scrape from `AmazeClient`, lazily loads each profile, and derives the weekly grid from
 * `FacultyFreeSlotsUtil`. It was simply never mounted by the router. This wrapper is the
 * thin themed entry point; App.kt only needs to route `Screen.FACULTY_INFO` here instead of
 * the lightweight `M3EFacultyScreen()` bridge (one-line change reported to the owner).
 *
 * All side effects remain inside the legacy screen's own `LaunchedEffect`s — the M3E
 * stateless `M3EFacultyScreen` is deliberately untouched.
 */
@Composable
fun M3EFacultyDirectoryHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    AmazeM3ETheme(isDark = isDark) {
        Box(modifier = modifier) {
            FacultyInfoScreen()
        }
    }
}
