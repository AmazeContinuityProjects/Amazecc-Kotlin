package com.amazecc.app.shared.domain

/**
 * D-166 — structured result of reading an academic document on-device.
 *
 * Every field is optional except [suggestedName] because the extractor must be
 * able to say "I don't know" instead of filling in plausible-looking data.
 * Absent facts are `null` (scalars) or empty lists (collections); the UI is
 * responsible for saying so rather than presenting a guess as a fact.
 *
 * @param assessmentType exam/assessment label as printed, e.g. "CIA-1", "ESE".
 * @param subject full subject name as printed, if any.
 * @param subjectCode alphanumeric course code as printed, if any.
 * @param classCodes class/section codes as printed, if any.
 * @param faculties faculty names as printed, if any.
 * @param documentDate date as printed (preferably normalised to YYYY-MM-DD), if any.
 * @param suggestedName filename-safe name derived from the fields above; never blank.
 * @param insights short factual observations about the document (0-4).
 * @param confidence optional model confidence in the 0f..1f range.
 */
data class PaperInference(
    val assessmentType: String? = null,
    val subject: String? = null,
    val subjectCode: String? = null,
    val classCodes: List<String> = emptyList(),
    val faculties: List<String> = emptyList(),
    val documentDate: String? = null,
    val suggestedName: String,
    val insights: List<String> = emptyList(),
    val confidence: Float? = null
)
