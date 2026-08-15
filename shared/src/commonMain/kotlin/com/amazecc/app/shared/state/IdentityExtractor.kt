package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AccountCredential
import com.amazecc.app.shared.model.ApaarIdRes
import com.amazecc.app.shared.model.ApaarInfo
import com.amazecc.app.shared.model.BankInfo
import com.amazecc.app.shared.model.BankInfoRes
import com.amazecc.app.shared.model.CredentialsRes
import com.amazecc.app.shared.model.DayboarderInfo
import com.amazecc.app.shared.model.DayboarderRes
import com.amazecc.app.shared.model.EptScheduleRes
import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.Official
import com.amazecc.app.shared.model.ProfileImagesHodDeanPerson
import com.amazecc.app.shared.model.ProfileImagesProctor
import com.amazecc.app.shared.model.ProfileImagesRes
import com.amazecc.app.shared.model.ProfileProctor
import com.amazecc.app.shared.model.RankInfo
import com.amazecc.app.shared.model.RegistrationScheduleRes
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.model.StudentProfileRes
import com.amazecc.app.shared.model.UniversityDayRes
import com.amazecc.app.shared.model.VtopTable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Deciphers raw endpoint payloads into clean [StudentIdentity] fragments.
 * Every function returns a fragment containing ONLY filled fields — nulls, blanks,
 * placeholder values and empty collections are filtered out here, never in the UI.
 */
object IdentityExtractor {

    // ── Fragments from session / legacy sources ──

    fun fromSession(authorizedID: String?): StudentIdentity = StudentIdentity(
        regNo = authorizedID?.takeIf { it.isNotBlank() }
    )

    fun fromVtopPhoto(base64: String?): StudentIdentity = StudentIdentity(
        photoBase64 = base64?.takeIf { it.isNotBlank() }
    )

    // ── /api/student ──

    fun fromStudentProfile(res: StudentProfileRes?): StudentIdentity {
        val p = res?.data ?: return StudentIdentity()
        return StudentIdentity(
            regNo = p.regNo.ifBlank { null },
            name = p.name.ifBlank { null },
            email = p.email.ifBlank { null },
            mobile = p.mobile.ifBlank { null },
            dob = p.dob?.ifBlank { null },
            gender = p.gender?.ifBlank { null },
            bloodGroup = p.bloodGroup?.ifBlank { null },
            photoBase64 = p.photoBase64?.ifBlank { null },
            isHosteller = p.isHosteller,
            program = p.program.ifBlank { null },
            campus = p.campus.ifBlank { null },
            batch = p.batch.ifBlank { null },
            section = p.section?.ifBlank { null },
            advisorName = p.advisorName?.ifBlank { null },
            nationality = p.nationality?.ifBlank { null },
            nativeLanguage = p.nativeLanguage?.ifBlank { null },
            nativeState = p.nativeState?.ifBlank { null },
            community = p.community?.ifBlank { null },
            religion = p.religion?.ifBlank { null },
            caste = p.caste?.ifBlank { null },
            physicallyChallenged = p.physicallyChallenged?.ifBlank { null },
            aadharNumber = p.aadharNumber?.ifBlank { null },
            guardian = p.guardian?.ifBlank { null },
            currentAddress = toStringRows(p.currentAddress),
            permanentAddress = toStringRows(p.permanentAddress),
            father = toStringRows(p.father),
            mother = toStringRows(p.mother),
            proctor = p.proctor?.toOfficial()
        )
    }

    private fun ProfileProctor.toOfficial(): Official = Official(
        role = "Proctor",
        name = name?.ifBlank { null },
        designation = designation?.ifBlank { null },
        email = email?.ifBlank { null },
        phone = mobile?.ifBlank { null },
        school = school?.ifBlank { null },
        cabin = cabin?.ifBlank { null },
        department = department?.ifBlank { null },
        intercom = intercom?.ifBlank { null },
        facultyId = facultyId?.ifBlank { null }
    )

    // ── /api/profile-images ──

    fun fromProfileImages(res: ProfileImagesRes?): StudentIdentity {
        if (res == null) return StudentIdentity()
        return StudentIdentity(
            photoBase64 = (res.studentPhoto ?: res.student?.photoBase64 ?: res.profile?.photoBase64)?.ifBlank { null },
            proctor = res.proctor?.toOfficial(),
            hodDean = res.hodDean?.people?.mapNotNull { it.toOfficial() }.orEmpty()
        )
    }

    private fun ProfileImagesProctor.toOfficial(): Official {
        val (typed, extras) = typedDetails(details)
        return Official(
            role = "Proctor",
            name = typed.name,
            designation = typed.designation,
            email = typed.email,
            phone = typed.phone,
            school = typed.school,
            cabin = typed.cabin,
            department = typed.department,
            intercom = typed.intercom,
            facultyId = typed.facultyId,
            photoBase64 = photoBase64?.ifBlank { null },
            extras = extras
        )
    }

    private fun ProfileImagesHodDeanPerson.toOfficial(): Official? {
        val (typed, extras) = typedDetails(details)
        val role = role.takeIf { it.isNotBlank() }
        if (role == null && typed.name == null && typed.designation == null && extras.isEmpty()) return null
        return Official(
            role = role,
            name = typed.name,
            designation = typed.designation,
            email = typed.email,
            phone = typed.phone,
            school = typed.school,
            cabin = typed.cabin,
            department = typed.department,
            intercom = typed.intercom,
            facultyId = typed.facultyId,
            photoBase64 = photoBase64?.ifBlank { null },
            extras = extras
        )
    }

    private class TypedDetails(
        val name: String? = null,
        val designation: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val school: String? = null,
        val cabin: String? = null,
        val department: String? = null,
        val intercom: String? = null,
        val facultyId: String? = null
    )

    private fun typedDetails(details: Map<String, String>): Pair<TypedDetails, List<KeyValueRow>> {
        var name: String? = null
        var designation: String? = null
        var email: String? = null
        var phone: String? = null
        var school: String? = null
        var cabin: String? = null
        var department: String? = null
        var intercom: String? = null
        var facultyId: String? = null
        val extras = mutableListOf<KeyValueRow>()
        details.forEach { (key, raw) ->
            val value = raw.trim().takeIf { it.isNotBlank() } ?: return@forEach
            when (key.lowercase().replace(" ", "")) {
                "name" -> name = value
                "designation" -> designation = value
                "email" -> email = value
                "phone", "mobile", "mobilenumber" -> phone = value
                "school" -> school = value
                "cabin" -> cabin = value
                "department" -> department = value
                "intercom" -> intercom = value
                "facultyid", "faculty_id" -> facultyId = value
                else -> extras += KeyValueRow(humanizeKey(key), value)
            }
        }
        return TypedDetails(name, designation, email, phone, school, cabin, department, intercom, facultyId) to extras
    }

    // ── /api/credentials ──

    fun fromCredentials(res: CredentialsRes?): StudentIdentity {
        if (res == null) return StudentIdentity()
        return StudentIdentity(
            credentials = res.credentials.mapNotNull { c ->
                if (c.account.isBlank()) return@mapNotNull null
                AccountCredential(
                    account = c.account,
                    username = c.username,
                    password = c.defaultCredentials,
                    url = c.url?.ifBlank { null },
                    venueDate = c.venueDate,
                    seatLocation = c.seatLocation
                )
            },
            ranks = res.ranks.mapNotNull { r ->
                if (r.name.isBlank() || r.rank.isBlank()) return@mapNotNull null
                RankInfo(name = r.name, rank = r.rank)
            }
        )
    }

    // ── /api/bank-info ──

    fun fromBankInfo(res: BankInfoRes?): StudentIdentity {
        if (res == null) return StudentIdentity()
        var name: String? = null
        var branch: String? = null
        var address: String? = null
        (res.bankDetails as? JsonObject)?.forEach { (key, element) ->
            val value = (element as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            when (key.lowercase().replace(" ", "")) {
                "bankname", "bank" -> name = value
                "branch" -> branch = value
                "address" -> address = value
            }
        }
        val fields = normalizeFields(res.fields)
        if (name == null && branch == null && address == null && fields.isEmpty()) return StudentIdentity()
        return StudentIdentity(bank = BankInfo(name, branch, address, fields))
    }

    // ── /api/dayboarder ──

    fun fromDayboarder(res: DayboarderRes?): StudentIdentity {
        val fields = res?.fields
        if (fields.isNullOrEmpty()) return StudentIdentity()
        val rows = decipherDayboarderFields(fields)
        return StudentIdentity(
            dayboarder = DayboarderInfo(isDayboarder = rows.isNotEmpty(), fields = rows)
        )
    }

    // ── Record payloads (ept / registration / university-day / apaar) ──

    fun fromEptSchedule(res: EptScheduleRes?): StudentIdentity = StudentIdentity(
        eptTables = normalizeTables(res?.tables)
    )

    fun fromRegistrationSchedule(res: RegistrationScheduleRes?): StudentIdentity = StudentIdentity(
        registrationFields = normalizeFields(res?.keyValuePairs),
        registrationTables = normalizeTables(res?.tables)
    )

    fun fromUniversityDay(res: UniversityDayRes?): StudentIdentity = StudentIdentity(
        universityDayTitle = res?.title?.ifBlank { null },
        universityDayFields = normalizeFields(res?.formFields) + normalizeFields(res?.keyValuePairs),
        universityDayTables = normalizeTables(res?.tables)
    )

    fun fromApaarId(res: ApaarIdRes?): StudentIdentity {
        if (res?.hasApaar != true) return StudentIdentity()
        return StudentIdentity(
            apaar = ApaarInfo(
                hasApaar = true,
                fields = normalizeFields(res.formFields) + normalizeFields(res.keyValuePairs),
                tables = normalizeTables(res.tables)
            )
        )
    }

    // ── Shared decipher helpers ──

    private fun toStringRows(map: Map<String, JsonElement>?): List<KeyValueRow> {
        if (map.isNullOrEmpty()) return emptyList()
        return map.entries.mapNotNull { (key, element) ->
            val value = (element as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KeyValueRow(humanizeKey(key), value)
        }
    }

    private fun normalizeFields(fields: Map<String, JsonElement>?): List<KeyValueRow> {
        if (fields.isNullOrEmpty()) return emptyList()
        return fields.entries.mapNotNull { (key, element) ->
            val unwrapped = unwrapValue(element)
            val label = unwrapped?.first?.takeIf { it.isNotBlank() } ?: humanizeKey(key)
            val value = unwrapped?.second?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KeyValueRow(label, value)
        }
    }

    private fun normalizeTables(tables: List<JsonElement>?): List<VtopTable> {
        if (tables.isNullOrEmpty()) return emptyList()
        return tables.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val caption = (element["caption"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    val headers = (element["headers"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .filter { it.isNotBlank() }
                    if (headers.isNotEmpty()) {
                        val rows = (element["rows"] as? JsonArray).orEmpty().mapNotNull { row ->
                            val obj = row as? JsonObject ?: return@mapNotNull null
                            val cells = headers.map { h -> jsonToText(obj[h] ?: JsonNull).trim() }
                            if (cells.all { it.isBlank() }) null else cells
                        }
                        if (rows.isEmpty() && caption == null) null else VtopTable(caption, headers, rows)
                    } else {
                        val cells = element.entries.mapNotNull { (k, v) ->
                            val child = jsonToText(v).trim()
                            if (child.isBlank()) null else "$k: $child"
                        }
                        if (cells.isEmpty()) null else VtopTable(null, emptyList(), listOf(cells))
                    }
                }
                is JsonArray -> {
                    val objects = element.filterIsInstance<JsonObject>()
                    if (objects.isNotEmpty() && objects.size == element.size) {
                        val headers = buildList {
                            objects.forEach { obj -> obj.keys.forEach { if (it !in this) add(it) } }
                        }
                        val rows = objects.mapNotNull { obj ->
                            val cells = headers.map { h -> jsonToText(obj[h] ?: JsonNull).trim() }
                            if (cells.all { it.isBlank() }) null else cells
                        }
                        if (rows.isEmpty()) null else VtopTable(null, headers, rows)
                    } else {
                        val cells = element.mapNotNull { jsonToText(it).trim().takeIf { c -> c.isNotBlank() } }
                        if (cells.isEmpty()) null else VtopTable(null, emptyList(), listOf(cells))
                    }
                }
                else -> null
            }
        }
    }

    // VTOP-style wrapper objects like {"index": 0, "value": "...", "label": "..."} → (label, display)
    private fun unwrapValue(element: JsonElement): Pair<String, String>? {
        val obj = element as? JsonObject ?: return null
        val valueEl = obj["value"] ?: return null
        val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: ""
        return label to jsonToText(valueEl)
    }

    private fun jsonToText(value: JsonElement): String = when (value) {
        JsonNull -> ""
        is JsonPrimitive -> value.content
        is JsonObject -> unwrapValue(value)?.let { (label, display) ->
            if (label.isBlank()) display else "$label: $display"
        } ?: value.entries.joinToString(" · ") { (k, v) ->
            val child = jsonToText(v)
            if (child.isBlank()) k else "$k: $child"
        }
        is JsonArray -> value.joinToString(", ") { jsonToText(it) }
    }

    fun humanizeKey(key: String): String =
        key.replace("_", " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    // ── Dayboarder: fuzzy canonical ordering of raw VTOP form fields ──

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

    private fun decipherDayboarderFields(fields: Map<String, JsonElement>): List<KeyValueRow> {
        val parsed = fields.mapNotNull { (key, element) ->
            val obj = element as? JsonObject
            if (obj == null) {
                val raw = (element as? JsonPrimitive)?.content ?: ""
                return@mapNotNull ParsedField(key, humanizeKey(key), raw.trim())
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
            .filter { (label, value) -> label.isNotBlank() && value.isNotBlank() && value != NOT_SET }
            .map { (label, value) -> KeyValueRow(label, value) }
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
}
