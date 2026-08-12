package com.amazecc.app.shared.utils

object UpdateConfig {
    const val GITHUB_OWNER = "AmazeContinuityProjects"
    const val GITHUB_REPO = "Amazecc-Kotlin"

    fun getCurrentVersion(): String = VersionInfo.VERSION_NAME

    fun getVersionCode(): Int = VersionInfo.VERSION_CODE
}

expect fun isAndroid(): Boolean