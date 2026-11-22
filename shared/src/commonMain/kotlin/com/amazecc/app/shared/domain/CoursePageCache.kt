package com.amazecc.app.shared.domain

import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The one cached VTOP course page. The web `vtop` tab keeps its course-page payload in
 * localStorage and reads it on mount; Kotlin keeps this single entry under one settings key
 * and reads it on mount too. There is no fetch here and no material download client — the
 * entry holds only rows the server actually published.
 *
 * [semesterId] and [courseCode] are stored *inside* the entry so a later composition can
 * reject a page fetched for a different course or term instead of silently showing it.
 * [fetchedAt] is a real epoch-millis stamp, used only to print an honest "Cached · <age>".
 */
@Serializable
internal data class CachedCoursePage(
    val semesterId: String? = null,
    val courseCode: String = "",
    val fetchedAt: Long = 0L,
    val rows: List<CachedCourseMaterialRow> = emptyList(),
) {
    /** Exact scope check: a null semester never borrows a named semester's rows. */
    internal fun matches(semesterId: String?, courseCode: String?): Boolean =
        this.courseCode.equals(courseCode, ignoreCase = true) &&
            (this.semesterId == semesterId || (this.semesterId.isNullOrBlank() && semesterId.isNullOrBlank()))
}

@Serializable
internal data class CachedCourseMaterialRow(
    val slNo: String = "",
    val date: String = "",
    val slot: String = "",
    val topic: String = "",
    val materials: List<CachedCourseMaterialRef> = emptyList(),
)

@Serializable
internal data class CachedCourseMaterialRef(
    val materialId: String = "",
    val label: String = "",
    val materialDate: String = "",
)

/** Deterministic extraction of the server's `data.materials.rows` — never invents a field. */
internal fun parseCoursePageRows(root: JsonObject?): List<CachedCourseMaterialRow> {
    if (root == null) return emptyList()
    val data = root["data"] as? JsonObject ?: root
    val rows = when (val materials = data["materials"]) {
        is JsonObject -> materials["rows"] as? JsonArray
        is JsonArray -> materials
        else -> data["rows"] as? JsonArray
    } ?: return emptyList()

    return rows.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val refsArray = obj["materials"] as? JsonArray
        val refs = if (refsArray == null) {
            emptyList()
        } else {
            refsArray.mapNotNull { refElement ->
                val ref = refElement as? JsonObject ?: return@mapNotNull null
                val materialId = ref.text("materialId", "id")
                val label = ref.text("label", "name", "fileName", "title")
                if (materialId.isBlank() && label.isBlank()) return@mapNotNull null
                CachedCourseMaterialRef(
                    materialId = materialId,
                    label = label,
                    materialDate = ref.text("materialDate", "date"),
                )
            }
        }
        val row = CachedCourseMaterialRow(
            slNo = obj.text("slNo", "slno", "sNo"),
            date = obj.text("date", "classDate", "materialDate"),
            slot = obj.text("slot", "classSlot"),
            topic = obj.text("topic", "title", "description"),
            materials = refs,
        )
        // An empty object is not a session: dropping it stops the UI inventing "Session N".
        if (row.slNo.isBlank() && row.date.isBlank() && row.slot.isBlank() &&
            row.topic.isBlank() && row.materials.isEmpty()
        ) null else row
    }
}

private fun JsonObject.text(vararg keys: String): String {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        val value = primitive.content.trim()
        if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) return value
    }
    return ""
}

internal object CoursePageCache {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun encode(entry: CachedCoursePage): String = json.encodeToString(entry)

    internal fun decode(raw: String?): CachedCoursePage? =
        raw?.takeIf(String::isNotBlank)?.let {
            runCatching { json.decodeFromString<CachedCoursePage>(it) }.getOrNull()
        }

    /** Cache-first read. A corrupt or foreign entry is treated as no cache, never as fresh data. */
    internal fun read(semesterId: String?, courseCode: String): CachedCoursePage? {
        val entry = decode(SettingsManager.getNullableString(SettingsManager.CACHE_COURSE_PAGE))
            ?: return null
        return entry.takeIf { it.matches(semesterId, courseCode) }
    }

    internal fun write(entry: CachedCoursePage) {
        SettingsManager.setString(SettingsManager.CACHE_COURSE_PAGE, encode(entry))
    }
}

/** Honest age from the real `fetchedAt` stamp; no freshness claim beyond elapsed time. */
internal fun coursePageCacheAge(fetchedAt: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - fetchedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 1_440L -> "${minutes / 60L} h ago"
        else -> "${minutes / 1_440L} d ago"
    }
}
