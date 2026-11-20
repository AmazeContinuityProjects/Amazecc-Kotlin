package com.amazecc.app.shared.utils

/** iOS has no activity-alias mechanism — the control reports unsupported. */
actual fun setAppIconTheme(theme: AppIconTheme): Boolean = false

actual fun currentAppIconTheme(): AppIconTheme = AppIconTheme.DEFAULT
