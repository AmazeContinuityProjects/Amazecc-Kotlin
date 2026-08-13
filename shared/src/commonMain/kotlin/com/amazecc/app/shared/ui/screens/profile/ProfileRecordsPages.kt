package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun EptSchedulePage() {
    val eptSchedule by AppState.eptSchedule.collectAsState()
    val tables = eptSchedule?.tables.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("EPT Schedule")
        if (tables.isEmpty()) {
            EmptyStateCard("No EPT scheduled")
        } else {
            tables.forEach { table ->
                JsonElementCard(title = null, element = table)
            }
        }
    }
}

@Composable
fun RegistrationSchedulePage() {
    val registrationSchedule by AppState.registrationSchedule.collectAsState()
    val tables = registrationSchedule?.tables.orEmpty()
    val keyValuePairs = registrationSchedule?.keyValuePairs.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Registration Schedule")
        if (tables.isEmpty() && keyValuePairs.isEmpty()) {
            EmptyStateCard("No registration schedule available")
        } else {
            keyValuePairs.takeIf { it.isNotEmpty() }?.let { pairs ->
                JsonFieldCard(title = null, fields = pairs)
            }
            tables.forEach { table ->
                JsonElementCard(title = null, element = table)
            }
        }
    }
}

@Composable
fun UniversityDayPage() {
    val universityDay by AppState.universityDay.collectAsState()
    val res = universityDay
    val tables = res?.tables.orEmpty()
    val keyValuePairs = res?.keyValuePairs.orEmpty()
    val formFields = res?.formFields.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("University Day")
        if (res?.title?.isNotBlank() == true) {
            Text(
                res.title,
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = AmazeTheme.colors.textPrimary)
            )
        }
        if (tables.isEmpty() && keyValuePairs.isEmpty() && formFields.isEmpty()) {
            EmptyStateCard("No University Day details available")
        } else {
            formFields.takeIf { it.isNotEmpty() }?.let { fields ->
                JsonFieldCard(title = null, fields = fields)
            }
            keyValuePairs.takeIf { it.isNotEmpty() }?.let { pairs ->
                JsonFieldCard(title = null, fields = pairs)
            }
            tables.forEach { table ->
                JsonElementCard(title = null, element = table)
            }
        }
    }
}

@Composable
fun DayboarderPage() {
    val dayboarder by AppState.dayboarder.collectAsState()
    val fields = dayboarder?.fields

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Dayboarder Details")
        val rows = fields?.let(::parseDayboarderFields).orEmpty()
        if (fields.isNullOrEmpty() || rows.isEmpty()) {
            EmptyStateCard("No dayboarder details available")
        } else {
            LabelValueCard(title = null, rows = rows)
        }
    }
}

@Composable
fun ApaarIdPage() {
    val apaarId by AppState.apaarId.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("APAAR ID")
        if (apaarId?.hasApaar != true) {
            EmptyStateCard("APAAR ID not generated yet")
        } else {
            apaarId?.formFields?.takeIf { it.isNotEmpty() }?.let { fields ->
                JsonFieldCard(title = "Form Fields", fields = fields)
            }
            apaarId?.keyValuePairs?.takeIf { it.isNotEmpty() }?.let { pairs ->
                JsonFieldCard(title = "Details", fields = pairs)
            }
            apaarId?.tables?.forEach { table ->
                JsonElementCard(title = null, element = table)
            }
        }
    }
}

@Composable
private fun JsonElementCard(title: String?, element: JsonElement) {
    when (element) {
        is JsonObject -> {
            val headers = element["headers"] as? JsonArray
            val rows = element["rows"] as? JsonArray
            if (headers != null && rows != null) {
                val headerTexts = headers.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (headerTexts.isNotEmpty()) {
                    val rowsObjects = rows.filterIsInstance<JsonObject>()
                    if (headerTexts.size == 2) {
                        val labelValue = rowsObjects.mapNotNull { row ->
                            val label = (row[headerTexts[0]] as? JsonPrimitive)?.contentOrNull
                                ?: return@mapNotNull null
                            val value = (row[headerTexts[1]] as? JsonPrimitive)?.contentOrNull ?: ""
                            label to value
                        }
                        if (labelValue.isNotEmpty()) {
                            LabelValueCard(title = title, rows = labelValue)
                            return
                        }
                    }
                    JsonTemplateTableCard(title = title, headers = headerTexts, rows = rowsObjects)
                    return
                }
            }
            JsonFieldCard(title = title, fields = element)
        }
        is JsonArray -> {
            val objects = element.filterIsInstance<JsonObject>()
            if (objects.isNotEmpty() && objects.size == element.size) {
                JsonTableCard(title = title, rows = objects)
            } else {
                JsonFieldCard(title = title, fields = element.mapIndexed { index, value ->
                    "item_${index + 1}" to value
                }.toMap())
            }
        }
        else -> JsonFieldCard(title = title, fields = mapOf("value" to element))
    }
}

@Composable
private fun JsonTemplateTableCard(title: String?, headers: List<String>, rows: List<JsonObject>) {
    val colors = AmazeTheme.colors
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(AmazeTheme.radius.xs))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        humanizeKey(header),
                        modifier = Modifier.weight(1f),
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    headers.forEach { header ->
                        Text(
                            jsonToDisplay(row[header] ?: JsonNull),
                            modifier = Modifier.weight(1f),
                            style = AmazeTheme.typography.caption.copy(color = colors.textPrimary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonFieldCard(title: String?, fields: Map<String, JsonElement>) {
    LabelValueCard(title = title, rows = fields.entries.map { (key, value) ->
        val unwrapped = if (value is JsonObject) unwrapObject(value) else null
        if (unwrapped != null) unwrapped else humanizeKey(key) to jsonToDisplay(value)
    })
}

@Composable
private fun LabelValueCard(title: String?, rows: List<Pair<String, String>>) {
    val colors = AmazeTheme.colors
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column {
            if (!title.isNullOrBlank()) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        title,
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
            }
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        value,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = if (value == NOT_SET) FontWeight.Normal else FontWeight.Bold,
                            color = if (value == NOT_SET) colors.textMuted else colors.textPrimary
                        )
                    )
                }
                if (index < rows.size - 1) SettingsRowDivider()
            }
        }
    }
}

@Composable
private fun JsonTableCard(title: String?, rows: List<JsonObject>) {
    val colors = AmazeTheme.colors
    val headers = buildList {
        rows.forEach { row ->
            row.keys.forEach { key -> if (key !in this) add(key) }
        }
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(AmazeTheme.radius.xs))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        humanizeKey(header),
                        modifier = Modifier.weight(1f),
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro
                        )
                    )
                }
            }
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    headers.forEach { header ->
                        Text(
                            jsonToDisplay(row[header] ?: JsonNull),
                            modifier = Modifier.weight(1f),
                            style = AmazeTheme.typography.caption.copy(color = colors.textPrimary)
                        )
                    }
                }
            }
        }
    }
}

private fun humanizeKey(key: String): String =
    key.replace("_", " ").split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private fun jsonToDisplay(value: JsonElement): String = when (value) {
    JsonNull -> ""
    is JsonPrimitive -> value.content
    is JsonObject -> {
        unwrapObject(value)?.let { (label, display) ->
            if (label.isBlank()) display else "$label: $display"
        } ?: value.entries.joinToString(" · ") { (k, v) ->
            val child = jsonToDisplay(v)
            if (child.isBlank()) k else "$k: $child"
        }
    }
    is JsonArray -> value.joinToString(", ") { jsonToDisplay(it) }
}

// Recognizes VTOP-style wrapper objects like {"index": 0, "value": "...", "label": "..."}
// or {"value": "..."} / {"label": "...", "value": "..."} and returns their display pair.
// Returns null when the object is ordinary data rather than a wrapper.
private fun unwrapObject(obj: JsonObject): Pair<String, String>? {
    val valueEl = obj["value"] ?: return null
    val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: ""
    val display = jsonToDisplay(valueEl)
    return (label to display)
}

// Turns an arbitrary JSON element (possibly deeply nested) into readable label/value rows.
// Nested maps expand to "Parent > Child" rows; arrays expand to numbered rows.
private fun flattenNestedFields(element: JsonElement): List<Pair<String, String>> = when (element) {
    JsonNull -> emptyList()
    is JsonPrimitive -> if (element.content.isBlank()) emptyList()
        else listOf("Value" to element.content)
    is JsonObject -> {
        unwrapObject(element)?.let { (label, display) ->
            return listOf(label.ifBlank { "Value" } to display.ifBlank { NOT_SET })
        }
        element.entries.flatMap { (key, value) ->
            val label = humanizeKey(key)
            when (value) {
                is JsonPrimitive -> listOf(label to value.content.ifBlank { NOT_SET })
                is JsonObject -> unwrapObject(value)?.let { (innerLabel, display) ->
                    listOf(label to display.ifBlank { NOT_SET })
                } ?: flattenNestedFields(value).map { (k, v) -> "$label > $k" to v }
                is JsonArray -> value.flatMapIndexed { index, item ->
                    when (item) {
                        is JsonObject -> unwrapObject(item)?.let { (_, display) ->
                            listOf("$label ${index + 1}" to display.ifBlank { NOT_SET })
                        } ?: flattenNestedFields(item).map { (k, v) -> "$label ${index + 1} > $k" to v }
                        else -> listOf("$label ${index + 1}" to jsonToDisplay(item).ifBlank { NOT_SET })
                    }
                }
                JsonNull -> listOf(label to NOT_SET)
            }
        }
    }
    is JsonArray -> element.flatMapIndexed { index, item ->
        when (item) {
            is JsonObject -> flattenNestedFields(item)
            else -> listOf("Item ${index + 1}" to jsonToDisplay(item).ifBlank { NOT_SET })
        }
    }
}

private const val NOT_SET = "Not set"
private const val DAYBOARDER_MATCH_THRESHOLD = 0.55f

private val DayboarderFieldLabels = listOf(
    "Staying Type",
    "Father/House Owner Name",
    "Father/House Owner Mobile No.",
    "Door No. / Street Name",
    "Pincode",
    "Area Name",
    "District Name",
    "State Name",
    "Land Mark",
    "Friend - 1 Register Number",
    "Friend - 2 Register Number"
)

private data class ParsedField(val key: String, val label: String, val value: String)

private fun parseDayboarderFields(fields: Map<String, JsonElement>): List<Pair<String, String>> {
    val parsed = fields.mapNotNull { (key, element) ->
        val obj = element as? JsonObject
        if (obj == null) {
            val raw = when (element) {
                is JsonPrimitive -> element.content
                else -> ""
            }
            return@mapNotNull ParsedField(key, humanizeKey(key), raw.ifBlank { NOT_SET })
        }
        val label = (obj["label"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: humanizeKey(key)
        val rawValue = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: ""
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
        val options = (obj["options"] as? JsonArray).orEmpty().mapNotNull { opt ->
            val o = opt as? JsonObject ?: return@mapNotNull null
            val optionValue = (o["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val optionLabel = (o["label"] as? JsonPrimitive)?.contentOrNull ?: ""
            optionValue to optionLabel
        }
        ParsedField(key, label, resolveFieldValue(rawValue, type, options))
    }

    val matchedCanonical = IntArray(parsed.size) { -1 }
    parsed.indices
        .sortedByDescending { i ->
            DayboarderFieldLabels.indices.maxOfOrNull { j ->
                maxOf(
                    fuzzyScore(parsed[i].label, DayboarderFieldLabels[j]),
                    fuzzyScore(parsed[i].key, DayboarderFieldLabels[j])
                )
            } ?: 0f
        }
        .forEach { i ->
            var best = -1
            var bestScore = DAYBOARDER_MATCH_THRESHOLD
            DayboarderFieldLabels.indices.forEach { j ->
                if (j !in matchedCanonical) {
                    val score = maxOf(
                        fuzzyScore(parsed[i].label, DayboarderFieldLabels[j]),
                        fuzzyScore(parsed[i].key, DayboarderFieldLabels[j])
                    )
                    if (score > bestScore) {
                        bestScore = score
                        best = j
                    }
                }
            }
            matchedCanonical[i] = best
        }

    val rows = mutableListOf<Pair<String, String>>()
    val used = mutableSetOf<Int>()
    parsed.indices
        .sortedBy { matchedCanonical[it].takeIf { c -> c >= 0 } ?: Int.MAX_VALUE }
        .forEach { i ->
            val canonical = matchedCanonical[i]
            if (canonical >= 0 && canonical !in used) {
                used += canonical
                rows += DayboarderFieldLabels[canonical] to parsed[i].value
            } else {
                rows += parsed[i].label to parsed[i].value
            }
        }
    return rows
}

private fun resolveFieldValue(raw: String, type: String?, options: List<Pair<String, String>>): String {
    val value = raw.trim()
    if (type == "select") {
        val matched = options.firstOrNull { it.first == value }?.second
        if (!matched.isNullOrBlank() && !isPlaceholderLabel(matched)) return matched
        if (value.isNotBlank() && options.isEmpty()) return value
        return NOT_SET
    }
    return value.ifBlank { NOT_SET }
}

private fun isPlaceholderLabel(label: String): Boolean {
    val l = label.lowercase()
    return l.isBlank() || "select" in l || "choose" in l || "please" in l || "pick" in l
}

private fun fuzzyScore(a: String, b: String): Float {
    val x = a.lowercase().filter { it.isLetterOrDigit() }
    val y = b.lowercase().filter { it.isLetterOrDigit() }
    if (x.isEmpty() || y.isEmpty() || x.length == 1 || y.length == 1) return 0f
    if (x == y) return 1f
    val bx = (0 until x.length - 1).map { x.substring(it, it + 2) }.toSet()
    val by = (0 until y.length - 1).map { y.substring(it, it + 2) }.toSet()
    val overlap = bx.count { it in by }
    return 2f * overlap / (bx.size + by.size)
}
