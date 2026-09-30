package com.amazecc.app.shared.vtop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One table lifted out of a page, with its header labels.
 *
 * Rows are kept positionally against [headers] so a lookup by column name and a lookup by index
 * both work — VTOP's own pages are inconsistently keyed, and the extractors need both.
 */
data class VtopPageTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val caption: String = ""
) {
    fun cell(row: Int, index: Int): String = rows.getOrNull(row)?.getOrNull(index)?.trim().orEmpty()

    /** Value of the first column whose header matches [predicate], for [row]. */
    fun byHeader(row: Int, predicate: (String) -> Boolean): String {
        val idx = headers.indexOfFirst(predicate)
        return if (idx >= 0) cell(row, idx) else ""
    }

    fun headerIndex(predicate: (String) -> Boolean): Int = headers.indexOfFirst(predicate)

    fun rowCount(): Int = rows.size
}

/**
 * A whole VTOP page reduced to its structure.
 *
 * Direct counterpart of AmazeCC-API's `ParsedVtopPage` (`src/lib/parsers/auto-parse.ts`), serving
 * the pages that are just a form plus a couple of tables: APAAR, EPT Schedule, Registration
 * Schedule, University Day and Dayboarder.
 */
data class VtopPage(
    val ok: Boolean,
    val error: String? = null,
    val title: String = "",
    val selectOptions: Map<String, List<VtopOption>> = emptyMap(),
    val tables: List<VtopPageTable> = emptyList(),
    val keyValuePairs: Map<String, String> = emptyMap(),
    val formFields: Map<String, String> = emptyMap(),
    val hiddenFields: Map<String, String> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
    /** APAAR only. See [VtopScripts.parsePage] for the five-clause heuristic. */
    val hasApaar: Boolean = false
) {
    /** Non-blank values of the named column from the first table. */
    fun column(name: String): List<String> {
        val table = tables.firstOrNull() ?: return emptyList()
        val idx = table.headerIndex { it.equals(name, ignoreCase = true) }
        if (idx < 0) return emptyList()
        return table.rows.map { table.cell(0, idx) }.filter { it.isNotBlank() }
    }

    /** First non-blank value for a label, matched case-insensitively as a substring. */
    fun valueLike(fragment: String): String? =
        keyValuePairs.entries.firstOrNull { it.key.contains(fragment, ignoreCase = true) }?.value

    /** First non-blank form field whose *name* contains [fragment], case-insensitively. */
    fun formValueLike(fragment: String): String? {
        val key = formFields.keys.firstOrNull { it.contains(fragment, ignoreCase = true) } ?: return null
        return formFields[key]
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

        private fun JsonObject.bool(key: String): Boolean = str(key) == "true"

        private fun JsonObject.stringMap(key: String): Map<String, String> {
            val obj = this[key] as? JsonObject ?: return emptyMap()
            return obj.mapNotNull { (k, v) ->
                val p = v as? JsonPrimitive ?: return@mapNotNull null
                if (p.isString) k to p.content else null
            }.toMap()
        }

        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

        fun parse(raw: String): VtopPage {
            val root = try {
                json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return VtopPage(ok = false, error = "unparseable response")

            if (!root.bool("ok")) {
                return VtopPage(ok = false, error = root.str("error"))
            }

            val options = (root["selectOptions"] as? JsonObject).orEmpty().mapNotNull { (name, el) ->
                val list = (el as? JsonArray).orEmpty().mapNotNull { o ->
                    val obj = o as? JsonObject ?: return@mapNotNull null
                    VtopOption(
                        value = obj.str("value").orEmpty(),
                        text = obj.str("text").orEmpty(),
                        selected = obj.bool("selected")
                    )
                }
                if (list.isEmpty()) null else name to list
            }.toMap()

            val tables = (root["tables"] as? JsonArray).orEmpty().mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val headers = obj.strings("headers")
                val rows = (obj["rows"] as? JsonArray).orEmpty().mapNotNull { r ->
                    (r as? JsonObject)?.let { row ->
                        // Project each row onto the header order so cell() and byHeader() agree.
                        headers.map { h -> (row[h] as? JsonPrimitive)?.content.orEmpty() }
                    }
                }
                if (headers.isEmpty() || rows.isEmpty()) null
                else VtopPageTable(headers = headers, rows = rows, caption = obj.str("caption").orEmpty())
            }

            return VtopPage(
                ok = true,
                title = root.str("title").orEmpty(),
                selectOptions = options,
                tables = tables,
                keyValuePairs = root.stringMap("keyValuePairs"),
                formFields = root.stringMap("formFields"),
                hiddenFields = root.stringMap("hiddenFields"),
                messages = root.stringMap("messages"),
                hasApaar = root.bool("hasApaar")
            )
        }
    }
}

/** One `<option>`. */
data class VtopOption(val value: String, val text: String, val selected: Boolean)

