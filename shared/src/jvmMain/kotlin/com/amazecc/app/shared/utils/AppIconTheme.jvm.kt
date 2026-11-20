package com.amazecc.app.shared.utils

/** Desktop has no launcher icon — the control reports unsupported. */
actual fun setAppIconTheme(theme: AppIconTheme): Boolean = false

actual fun currentAppIconTheme(): AppIconTheme = AppIconTheme.DEFAULT
