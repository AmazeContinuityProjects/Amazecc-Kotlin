package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentItem(
    val title: String = "",
    val maxMark: String = "",
    val weightagePercent: String = "",
    val status: String = "",
    val scoredMark: String = "",
    val weightageMark: String = "",
    /** "ETH" / "ELA" / "Theory Only" / "Lab Only" — which component this assessment belongs to. */
    val component: String? = null
)

@Serializable
data class MarksCourseItem(
    val classNbr: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val courseSystem: String = "",
    val faculty: String = "",
    val slot: String = "",
    /** Credit count for this component, used to weight an embedded pair's combined mark. */
    val credits: String? = null,
    val component: String? = null,
    /**
     * Credit-weighted total out of [maxMark], set by the merge when an ETH/ELA pair is combined.
     * A plain sum of the pair's weightage marks is wrong — it reports out of 200 against a max
     * of 100 — so the UI prefers this when present.
     */
    val totalMark: Double? = null,
    val maxMark: Double? = null,
    val assessments: List<AssessmentItem> = emptyList()
)
