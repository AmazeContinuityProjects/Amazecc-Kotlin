package com.amazecc.app.shared.utils

import amazecc_app.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

object UpdateConfig {
    const val GITHUB_OWNER = "AmazeContinuityProjects"
    const val GITHUB_REPO = "Amazecc-Kotlin"

    private var _cachedVersion: String? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun getCurrentVersion(): String {
        _cachedVersion?.let { return it }
        return try {
            val bytes = Res.readBytes("files/version.properties")
            val text = bytes.decodeToString()
            val version = text.lines()
                .firstOrNull { it.startsWith("VERSION_NAME=") }
                ?.substringAfter("=")
                ?.trim()
                ?.removePrefix("v")
                ?.removePrefix("V") ?: "1.9.2"
            _cachedVersion = version
            version
        } catch (e: Exception) {
            "1.9.2"
        }
    }
}
