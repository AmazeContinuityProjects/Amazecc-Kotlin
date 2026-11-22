package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
internal data class CourseMaterialClass(
    val semesterId: String,
    val classId: String,
    val facultyErpId: String,
    val label: String,
)

@Serializable
internal data class CourseMaterialRef(
    val label: String = "",
    val materialId: String = "",
    val materialDate: String = "",
    val classId: String = "",
    val semSubId: String = "",
)

@Serializable
internal data class CoursePlanRow(
    val slNo: String = "",
    val date: String = "",
    val slot: String = "",
    val topic: String = "",
    val materials: List<CourseMaterialRef> = emptyList(),
)

@Serializable
internal data class CourseMaterialsPage(
    val rows: List<CoursePlanRow> = emptyList(),
    val attachmentsAvailable: Boolean = true,
) {
    fun forClass(target: CourseMaterialClass): CourseMaterialsPage = copy(rows = rows.map { row ->
        row.copy(materials = row.materials.filter { ref ->
            ref.classId == target.classId && ref.semSubId == target.semesterId &&
                ref.materialId.isNotBlank() && ref.materialDate.isNotBlank()
        })
    })
}

internal data class CourseMaterialFile(val name: String, val bytes: ByteArray)

internal fun courseMaterialFilename(headerName: String?, fallback: String): String =
    headerName.orEmpty().replace(Regex("[^\\p{L}\\p{N}_. -]"), "_")
        .trim('.', ' ').take(150).ifBlank { fallback }

internal fun isCourseMaterialBinary(contentType: String, bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val type = contentType.substringBefore(';').trim().lowercase()
    if (type.startsWith("text/") && type != "text/csv") return false
    if (type.endsWith("/json") || type.endsWith("+json")) return false
    val start = bytes.decodeToString(endIndex = minOf(128, bytes.size)).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    return !start.startsWith("<") && !start.startsWith("{") && !start.startsWith("[")
}
