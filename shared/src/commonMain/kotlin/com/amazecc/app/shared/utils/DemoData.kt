package com.amazecc.app.shared.utils

import amazecc_app.shared.generated.resources.Res
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.compose.resources.ExperimentalResourceApi

object DemoData {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var root: JsonObject? = null
    private var loadFailed = false

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): Boolean {
        if (root != null) return true
        if (loadFailed) return false
        return try {
            val bytes = Res.readBytes("files/demoData.json")
            root = json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
            root != null
        } catch (e: Exception) {
            loadFailed = true
            false
        }
    }

    suspend fun <T> get(name: String, serializer: KSerializer<T>): T? {
        if (!load()) return null
        val element = root?.get(name) ?: return null
        return try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: Exception) {
            null
        }
    }
}
