package com.amazecc.app.shared.utils

import amazecc_app.shared.generated.resources.Res
import com.amazecc.app.shared.model.DocumentItem
import com.amazecc.app.shared.model.FreeClassroomRoom
import com.amazecc.app.shared.model.FreeClassroomsRes
import com.amazecc.app.shared.model.OdDetailsRes
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

    /** Built-in demo payloads, used only when `useMockData` is on and the JSON key is absent. */
    fun freeClassroomsDemo(): FreeClassroomsRes = FreeClassroomsRes(
        success = true,
        rooms = listOf(
            FreeClassroomRoom("SJT-301", "SJT", 3, 60, true, "free", true, "9:50 AM"),
            FreeClassroomRoom("TT-204", "TT", 2, 45, false, "occupied", false, "2:00 PM"),
            FreeClassroomRoom("AB1-101", "AB1", 1, 70, false, "free", true, null)
        ),
        day = "MON",
        period = "8:00 AM - 8:50 AM"
    )

    fun documentsDemo(): List<DocumentItem> = listOf(
        DocumentItem("bonafide", "Bonafide Certificate", "Demo document"),
        DocumentItem("e-transcript", "E-Transcript", "Demo document"),
        DocumentItem("additional-learning", "Additional Learning", "Demo document")
    )

    fun odDetailsDemo(semesterId: String): OdDetailsRes = OdDetailsRes(
        success = true,
        semesterId = semesterId,
        note = "Demo data"
    )
}
