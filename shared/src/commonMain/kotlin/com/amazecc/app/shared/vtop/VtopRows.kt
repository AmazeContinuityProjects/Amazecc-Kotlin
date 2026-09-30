package com.amazecc.app.shared.vtop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Reads a string field, or null. Shared by the parsers in this file. */
private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

/** Positional rows plus per-row captures from one VTOP data POST. */
data class VtopRows(
    val ok: Boolean,
    val error: String? = null,
    val rows: List<List<String>> = emptyList(),
    /** One entry per row, each holding that row's captures in declaration order. */
    val captures: List<List<String?>> = emptyList(),
    val keyValuePairs: Map<String, String> = emptyMap()
) {
    fun cell(row: Int, index: Int): String = rows.getOrNull(row)?.getOrNull(index)?.trim().orEmpty()

    /** Capture [index] for [row], or null. See the `captures` list on the calling script. */
    fun capture(row: Int, index: Int): String? = captures.getOrNull(row)?.getOrNull(index)

    /** Row count, or 0 when the fetch failed — callers treat both as "no data". */
    val size: Int get() = if (ok) rows.size else 0

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): VtopRows {
            val root = try {
                json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return VtopRows(ok = false, error = "unparseable response")

            if (root.str("ok") != "true") {
                return VtopRows(ok = false, error = root.str("error"))
            }

            val rows = (root["rows"] as? JsonArray).orEmpty().mapNotNull { rowEl ->
                (rowEl as? JsonArray)?.map { cell ->
                    (cell as? JsonPrimitive)?.content.orEmpty()
                }
            }

            val captures = (root["captures"] as? JsonArray).orEmpty().map { rowEl ->
                (rowEl as? JsonArray).orEmpty().map { cell ->
                    (cell as? JsonPrimitive)?.content
                }
            }

            val pairs = (root["keyValuePairs"] as? JsonObject).orEmpty()
                .mapNotNull { (k, v) ->
                    val p = v as? JsonPrimitive ?: return@mapNotNull null
                    if (p.isString) k to p.content else null
                }.toMap()

            return VtopRows(ok = true, rows = rows, captures = captures, keyValuePairs = pairs)
        }
    }
}

/** One course plus its nested assessment rows, from `examinations/doStudentMarkView`. */
data class VtopMarksCourse(
    /** Raw course-row cells, read positionally by [VtopDataSource]. */
    val cells: List<String>,
    /** Each entry is 7 values: slNo, title, maxMark, weightagePercent, status, scoredMark, weightageMark. */
    val assessments: List<List<String>>
)

data class VtopMarksResult(
    val ok: Boolean,
    val error: String? = null,
    val courses: List<VtopMarksCourse> = emptyList()
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): VtopMarksResult {
            val root = try {
                json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return VtopMarksResult(ok = false, error = "unparseable response")

            if (root.str("ok") != "true") {
                return VtopMarksResult(ok = false, error = root.str("error"))
            }

            val courses = (root["courses"] as? JsonArray).orEmpty().mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val cells = (obj["cells"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
                val assessments = (obj["assessments"] as? JsonArray).orEmpty().map { aEl ->
                    (aEl as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
                }
                VtopMarksCourse(cells, assessments)
            }
            return VtopMarksResult(ok = true, courses = courses)
        }
    }
}

/** CGPA summary from `get/dashboard/current/cgpa/credits`. */
data class VtopCgpaSummary(
    val creditsRequired: String? = null,
    val creditsEarned: String? = null,
    val cgpa: String? = null,
    val nonGradedRequirement: String? = null
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): VtopCgpaSummary? {
            val root = try {
                json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return null
            if (root.str("ok") != "true") return null
            val cgpa = root["cgpa"] as? JsonObject ?: return VtopCgpaSummary()
            return VtopCgpaSummary(
                creditsRequired = cgpa.str("creditsRequired"),
                creditsEarned = cgpa.str("creditsEarned"),
                cgpa = cgpa.str("cgpa"),
                nonGradedRequirement = cgpa.str("nonGradedRequirement")
            )
        }
    }
}

/** Dues page state from `p2p/Payments`. */
data class VtopPaymentStatus(
    val ok: Boolean,
    val error: String? = null,
    val title: String = "",
    val message: String = "",
    val hasDues: Boolean = false
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): VtopPaymentStatus {
            val root = try {
                json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return VtopPaymentStatus(ok = false, error = "unparseable response")

            if (root.str("ok") != "true") {
                return VtopPaymentStatus(ok = false, error = root.str("error"))
            }
            return VtopPaymentStatus(
                ok = true,
                title = root.str("title").orEmpty(),
                message = root.str("message").orEmpty(),
                hasDues = root.str("hasDues") == "true"
            )
        }
    }
}
