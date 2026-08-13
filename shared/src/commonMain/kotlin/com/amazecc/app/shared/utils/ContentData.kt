package com.amazecc.app.shared.utils

import amazecc_app.shared.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
data class Contributor(val name: String, val role: String = "")

@Serializable
data class ChangelogEntry(val title: String? = null, val description: String = "")

object ContentData {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var contributorsCache: List<Contributor>? = null
    private var changelogCache: List<ChangelogEntry>? = null

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readFile(path: String): String =
        Res.readBytes(path).decodeToString()

    suspend fun contributors(): List<Contributor> {
        contributorsCache?.let { return it }
        return try {
            contributorsCache = json.decodeFromString(readFile("files/hallOfFame.json"))
            contributorsCache ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun changelog(): List<ChangelogEntry> {
        changelogCache?.let { return it }
        return try {
            changelogCache = json.decodeFromString(readFile("files/changelog.json"))
            changelogCache ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}