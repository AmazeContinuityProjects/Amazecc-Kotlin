package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

/** A single label → value detail row attached to a document card. */
@Serializable
data class DocumentRow(
    val label: String,
    val value: String
)

/**
 * One card in the Documents list.
 *
 * The list is the flattened `/api/circulars` folder tree plus the small set of
 * static official-document entries; there is no `/api/documents` endpoint.
 *
 * [id] is non-null on purpose: the M3E host passes it straight into
 * `M3EDocumentCard(id: String)`. A folder node with no id falls back to its name,
 * and a leaf with neither yields an empty id (harmless — the screen only reads
 * it for a future deep link).
 */
@Serializable
data class DocumentItem(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val rows: List<DocumentRow> = emptyList(),
    val messages: List<String> = emptyList(),
    val error: String? = null
)
