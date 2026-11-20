package com.amazecc.app.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Typed mirrors of the cohort-statistics response shapes (D-012).
 *
 * Truth: `../amazecc-api-hono/src/routes/services/curves.ts` and
 * `../amazecc-api-hono/src/lib/marksStats.ts`.
 *
 * `POST /api/services/curves` answers one of two shapes under the SAME `stats` key:
 *  - single class:  `{ success, classId, semesterId, stats: {count, mean, sd, component?, title?}, classmates }`
 *  - batch classes: `{ success, stats: { [classId]: {count, mean, sd, assessments: { [key]: AssessmentStats } } } }`
 *
 * A single `stats` object cannot be decoded into two different Kotlin fields, so the envelope
 * keeps it as a [JsonElement] and the two pure helpers below decode it into [CurvesClassStatsDto]
 * (single) or a `Map<classId, CurvesClassStatsDto>` (batch). Both helper paths are deliberately
 * total: a malformed/absent stat becomes `null`/empty rather than throwing through a network call.
 */

/** One published statistic. `count < 5` is published as `{count, mean: 0, sd: 0}` by the API and
 *  means *no data*, not an average of zero — callers must check [count] before using it. */
@Serializable
data class CohortStatDto(
    val count: Int = 0,
    val mean: Double = 0.0,
    val sd: Double = 0.0,
    val component: String? = null,
    val title: String? = null,
)

/** A whole class: its overall curve plus per-assessment curves keyed by the server's HMAC id. */
@Serializable
data class CurvesClassStatsDto(
    val count: Int = 0,
    val mean: Double = 0.0,
    val sd: Double = 0.0,
    val assessments: Map<String, CohortStatDto> = emptyMap(),
)

@Serializable
data class CurvesClassmateDto(
    @SerialName("reg_no") val regNo: String = "",
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class CurvesEnvelopeDto(
    val success: Boolean = false,
    val classId: String? = null,
    val semesterId: String? = null,
    /** Single-class object or batch `{ classId: stats }` map — see the two helpers below. */
    val stats: JsonElement? = null,
    val classmates: List<CurvesClassmateDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

/** Lenient Json used only for the `stats` union; the network client has an identical config. */
internal val curvesJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Decode the single-class `stats` object, or null when the response is the batch shape. */
fun CurvesEnvelopeDto.singleClassStats(): CurvesClassStatsDto? {
    val obj = stats as? JsonObject ?: return null
    // A batch response is keyed by class id, not by count/mean/sd; don't mistake it for a stat.
    if (obj["count"] == null && obj["mean"] == null) return null
    return runCatching { curvesJson.decodeFromJsonElement<CurvesClassStatsDto>(obj) }.getOrNull()
}

/** Decode the batch `stats` map (classId → curve), or an empty map for a single-class response. */
fun CurvesEnvelopeDto.batchClassStats(): Map<String, CurvesClassStatsDto> {
    val obj = stats as? JsonObject ?: return emptyMap()
    if (obj["count"] != null || obj["mean"] != null) return emptyMap()
    return obj.mapNotNull { (id, element) ->
        runCatching { id to curvesJson.decodeFromJsonElement<CurvesClassStatsDto>(element) }.getOrNull()
    }.toMap()
}

/** A server-held HMAC token (presence/continuity only — never the mark value). */
@Serializable
data class MarkTokenDto(
    val classId: String = "",
    val scope: String = "",
    val assessmentKey: String = "",
    val token: String = "",
    val updatedAt: Long = 0L,
)

/** `POST /api/services/marks/tokens` — `{ success, tokens, legacy }`. */
@Serializable
data class MarksTokensEnvelopeDto(
    val success: Boolean = false,
    val tokens: List<MarkTokenDto> = emptyList(),
    /** Old-scheme tokens; the server currently answers `[]`. Type it narrowly but tolerate extras. */
    val legacy: List<MarkTokenDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)
