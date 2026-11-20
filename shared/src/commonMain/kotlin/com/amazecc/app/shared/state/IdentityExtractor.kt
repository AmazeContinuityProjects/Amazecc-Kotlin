package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AccountCredential
import com.amazecc.app.shared.model.AcknowledgementDocument
import com.amazecc.app.shared.model.AcknowledgementInfo
import com.amazecc.app.shared.model.ApaarIdRes
import com.amazecc.app.shared.model.ApaarInfo
import com.amazecc.app.shared.model.BankInfo
import com.amazecc.app.shared.model.BankInfoRes
import com.amazecc.app.shared.model.CredentialsRes
import com.amazecc.app.shared.model.ContactEntry
import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
import com.amazecc.app.shared.model.RESIDENCE_HOSTELLER
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
import com.amazecc.app.shared.model.WhoamiRes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
            proctor = p.proctor?.toOfficial(),
            school = p.proctor?.school?.ifBlank { null },
            applicationNumber = p.regNo.ifBlank { null },
            friendMobile = (p.currentAddress["friendMobile"] as? JsonPrimitive)?.contentOrNull
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

    // ── /api/remote/whoami (flat consolidated payload) ──

    private val whoamiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Convenience overload for callers that decoded the wire model directly. */
    fun fromWhoami(res: WhoamiRes): StudentIdentity =
        fromWhoami(whoamiJson.encodeToJsonElement(WhoamiRes.serializer(), res).jsonObject)

    /**
     * Deciphers the flat `/api/remote/whoami` response into a single canonical
     * fragment. The server spreads the identity at the top level and folds ten
     * VTOP pages (student, proctor, hodDean, credentials, apaar, bank, contact,
     * acknowledgement, dayboarder, eptSchedule) into nested blocks. Every folded
     * field is mapped; nested shapes are read defensively so a sibling-parser
     * change can never crash the client.
     */
    fun fromWhoami(obj: JsonObject): StudentIdentity {
        val regNo = str(obj, "regNo") ?: str(obj, "applicationNumber")
        val isHostellerFlag = (obj["isHosteller"] as? JsonPrimitive)?.booleanOrNull ?: false

        val currentRows = rowsFromJson(obj["currentAddress"])
        val permanentRows = rowsFromJson(obj["permanentAddress"])
        val fatherRows = rowsFromJson(obj["father"])
        val motherRows = rowsFromJson(obj["mother"])
        val friendMobile = str(obj, "friendMobile")
            ?: (currentRows + permanentRows).firstOrNull {
                it.label.contains("friend", true) && it.label.contains("mobile", true)
            }?.value

        val proctorOfficial = officialFromJson(obj["proctor"])
        val hodDeanOfficials = officialsFromJson(obj["hodDean"])
        val credentials = credentialsFromJson(obj["credentials"])
        val ranks = ranksFromJson(obj["ranks"])
        val bank = bankFromJson(obj["bank"])
        val apaar = apaarFromJson(obj["apaar"])
        val contacts = contactsFromJson(obj["contact"])
        val acknowledgement = acknowledgementFromJson(obj["acknowledgement"])

        val (dayboarderTitle, dayboarderRows) = dayboarderFieldsFromJson(obj["dayboarder"] as? JsonObject)
        val dayboarder = dayboarderRows.takeIf { it.isNotEmpty() }
            ?.let { DayboarderInfo(isDayboarder = true, fields = it) }

        val ept = eptFromJson(obj["eptSchedule"])

        val residence = deriveResidenceStatus(isHostellerFlag, dayboarderRows)
        // Keep the legacy boolean in sync with the tri-state status where possible.
        val effectiveHosteller = when (residence) {
            RESIDENCE_HOSTELLER -> true
            RESIDENCE_DAYSCHOLAR -> false
            else -> isHostellerFlag
        }

        return StudentIdentity(
            regNo = regNo,
            name = str(obj, "name"),
            email = str(obj, "email"),
            mobile = str(obj, "mobile"),
            dob = str(obj, "dob"),
            gender = str(obj, "gender"),
            bloodGroup = str(obj, "bloodGroup"),
            photoBase64 = str(obj, "photoBase64"),
            isHosteller = effectiveHosteller,
            residenceStatus = residence,
            friendlyName = str(obj, "friendlyName"),
            // Prefer the consolidated acknowledgement's application number when the flat
            // snapshot has none; fall back to regNo as the legacy student parser did.
            applicationNumber = str(obj, "applicationNumber") ?: acknowledgement?.applicationNo ?: regNo,
            program = str(obj, "program"),
            school = str(obj, "school") ?: proctorOfficial?.school,
            campus = str(obj, "campus"),
            batch = str(obj, "batch"),
            section = str(obj, "section"),
            advisorName = str(obj, "advisorName"),
            nationality = str(obj, "nationality"),
            nativeLanguage = str(obj, "nativeLanguage"),
            nativeState = str(obj, "nativeState"),
            community = str(obj, "community"),
            religion = str(obj, "religion"),
            caste = str(obj, "caste"),
            physicallyChallenged = str(obj, "physicallyChallenged"),
            aadharNumber = str(obj, "aadharNumber"),
            friendMobile = friendMobile,
            currentAddress = currentRows,
            permanentAddress = permanentRows,
            father = fatherRows,
            mother = motherRows,
            guardian = str(obj, "guardian"),
            proctor = proctorOfficial,
            hodDean = hodDeanOfficials,
            credentials = credentials,
            ranks = ranks,
            eptTables = ept.tables,
            eptTitle = ept.title,
            eptFields = ept.fields,
            eptMessages = ept.messages,
            dayboarder = dayboarder,
            dayboarderTitle = dayboarderTitle,
            contacts = contacts,
            contactTitle = str(obj, "contactTitle"),
            acknowledgement = acknowledgement,
            apaar = apaar,
            bank = bank
        )
    }

    /**
     * Residence precedence (mirrors the web client):
     *  1. an explicit `isHosteller = true` is authoritative → HOSTELLER;
     *  2. the canonical "Staying Type" dayboarder field text (raw value or its
     *     `selectedText`) containing DAYBOARDER/DAYSCHOLAR → DAYSCHOLAR, or
     *     HOSTELLER/HOSTEL → HOSTELLER;
     *  3. a dayboarder block that carries meaningful fields but no readable
     *     staying type → DAYSCHOLAR (a hosteller who also has the page is still
     *     covered by rule 1);
     *  4. otherwise null — the caller keeps the onboarding choice as fallback.
     */
    fun deriveResidenceStatus(isHosteller: Boolean, dayboarderRows: List<KeyValueRow>): String? {
        if (isHosteller) return RESIDENCE_HOSTELLER
        // Ignore placeholder rows ("Please select...") — they are not evidence of residence.
        val meaningful = dayboarderRows.filterNot { isPlaceholderLabel(it.value) }
        val staying = meaningful.firstOrNull {
            it.label.contains("staying", true) && it.label.contains("type", true)
        }?.value.orEmpty()
        if (staying.contains("DAYBOARDER", true) ||
            staying.contains("DAY BOARDER", true) ||
            staying.contains("DAY BOARDING", true) ||
            staying.contains("DAYSCHOLAR", true) ||
            staying.contains("DAY SCHOLAR", true) ||
            // Real VTOP option texts for the dayboarder staying-type select.
            staying.contains("staying with parents", true) ||
            staying.contains("staying with self", true) ||
            staying.contains("with friends", true)
        ) return RESIDENCE_DAYSCHOLAR
        if (staying.contains("HOSTELLER", true) || staying.contains("HOSTEL", true)) return RESIDENCE_HOSTELLER
        if (meaningful.isNotEmpty()) return RESIDENCE_DAYSCHOLAR
        return null
    }

    // ── Flat-payload decipher helpers ──

    private fun str(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun rowsFromJson(el: JsonElement?): List<KeyValueRow> = when (el) {
        is JsonArray -> el.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val label = str(o, "label") ?: return@mapNotNull null
            val value = str(o, "value") ?: return@mapNotNull null
            KeyValueRow(label, value)
        }
        is JsonObject -> toStringRows(el)
        else -> emptyList()
    }

    private fun officialFromJson(el: JsonElement?): Official? {
        val o = el as? JsonObject ?: return null
        val extras = (o["extras"] as? JsonArray).orEmpty().mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val label = str(row, "label") ?: return@mapNotNull null
            val value = str(row, "value") ?: return@mapNotNull null
            KeyValueRow(label, value)
        }
        val official = Official(
            role = str(o, "role"),
            name = str(o, "name"),
            designation = str(o, "designation"),
            email = str(o, "email"),
            phone = str(o, "phone", "mobile", "mobileNumber"),
            school = str(o, "school"),
            cabin = str(o, "cabin"),
            department = str(o, "department"),
            intercom = str(o, "intercom"),
            facultyId = str(o, "facultyId", "faculty_id"),
            photoBase64 = str(o, "photoBase64"),
            extras = extras
        )
        return official.takeIf {
            it.role != null || it.name != null || it.designation != null ||
                it.photoBase64 != null || it.extras.isNotEmpty()
        }
    }

    private fun officialsFromJson(el: JsonElement?): List<Official> = when (el) {
        is JsonArray -> el.mapNotNull { officialFromJson(it) }
        is JsonObject -> listOfNotNull(officialFromJson(el))
        else -> emptyList()
    }

    private fun credentialsFromJson(el: JsonElement?): List<AccountCredential> = when (el) {
        is JsonArray -> el.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val account = str(o, "account") ?: return@mapNotNull null
            AccountCredential(
                account = account,
                username = str(o, "username") ?: "",
                password = str(o, "password", "defaultCredentials") ?: "",
                url = str(o, "url"),
                venueDate = str(o, "venueDate") ?: "",
                seatLocation = str(o, "seatLocation") ?: ""
            )
        }
        else -> emptyList()
    }

    private fun ranksFromJson(el: JsonElement?): List<RankInfo> = when (el) {
        is JsonArray -> el.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val name = str(o, "name") ?: return@mapNotNull null
            val rank = str(o, "rank") ?: return@mapNotNull null
            RankInfo(name, rank)
        }
        else -> emptyList()
    }

    private fun bankFromJson(el: JsonElement?): BankInfo? {
        val o = el as? JsonObject ?: return null
        val fields = rowsFromJson(o["fields"])
        val name = str(o, "name", "bankName", "bank")
        val branch = str(o, "branch")
        val address = str(o, "address")
        return if (name == null && branch == null && address == null && fields.isEmpty()) null
        else BankInfo(name, branch, address, fields)
    }

    private fun apaarFromJson(el: JsonElement?): ApaarInfo? {
        val o = el as? JsonObject ?: return null
        val has = (o["hasApaar"] as? JsonPrimitive)?.booleanOrNull ?: false
        val fields = rowsFromJson(o["fields"])
        val tables = whoamiTables(o["tables"])
        if (!has && fields.isEmpty() && tables.isEmpty()) return null
        return ApaarInfo(hasApaar = has, fields = fields, tables = tables)
    }

    private fun contactsFromJson(el: JsonElement?): List<ContactEntry> = when (el) {
        is JsonArray -> el.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val office = str(o, "office") ?: ""
            val description = str(o, "description") ?: ""
            if (office.isBlank() && description.isBlank()) return@mapNotNull null
            ContactEntry(office = office, description = description, email = str(o, "email"))
        }
        else -> emptyList()
    }

    private fun acknowledgementFromJson(el: JsonElement?): AcknowledgementInfo? {
        val o = el as? JsonObject ?: return null
        val documents = (o["documents"] as? JsonArray).orEmpty().mapNotNull { item ->
            val doc = item as? JsonObject ?: return@mapNotNull null
            AcknowledgementDocument(
                serialNo = str(doc, "serialNo"),
                documentName = str(doc, "documentName"),
                status = str(doc, "status")
            )
        }
        val info = AcknowledgementInfo(
            title = str(o, "title"),
            documents = documents,
            applicationNo = str(o, "applicationNo"),
            registerNo = str(o, "registerNo"),
            programme = str(o, "programme"),
            branch = str(o, "branch"),
            campus = str(o, "campus")
        )
        return info.takeIf {
            it.title != null || it.documents.isNotEmpty() || it.applicationNo != null ||
                it.registerNo != null || it.programme != null || it.branch != null || it.campus != null
        }
    }

    /**
     * `dayboarder.fields` is `{ key: { label, value, selectedText? } }`. We feed a
     * normalized copy through the existing fuzzy canonicalizer so the canonical
     * labels ("Staying Type", …) stay consistent with the per-endpoint path, and
     * let `selectedText` (the friendly selected option) win for display/derivation.
     */
    private fun dayboarderFieldsFromJson(raw: JsonObject?): Pair<String?, List<KeyValueRow>> {
        if (raw == null) return null to emptyList()
        val title = str(raw, "title")
        val fieldsObj = raw["fields"] as? JsonObject ?: return title to emptyList()
        val normalized = JsonObject(fieldsObj.entries.associate { (key, value) ->
            val rewritten = if (value is JsonObject) {
                val selectedText = str(value, "selectedText")
                // VTOP emits placeholder selections like "0" -> "Please select...". Promoting
                // those to the value made a blank form look like evidence of a dayscholar.
                if (selectedText != null && !isPlaceholderLabel(selectedText)) {
                    JsonObject(value.toMap() + ("value" to JsonPrimitive(selectedText)))
                } else value
            } else value
            key to rewritten
        })
        return title to decipherDayboarderFields(normalized)
    }

    private data class EptParts(
        val title: String?,
        val tables: List<VtopTable>,
        val fields: List<KeyValueRow>,
        val messages: List<KeyValueRow>
    )

    private fun eptFromJson(el: JsonElement?): EptParts {
        val o = el as? JsonObject ?: return EptParts(null, emptyList(), emptyList(), emptyList())
        val fields = rowsFromJson(o["keyValuePairs"]) + rowsFromJson(o["formFields"])
        val messages = rowsFromJson(o["messages"])
        val message = str(o, "message")
        val allMessages = if (message != null) messages + KeyValueRow("Message", message) else messages
        return EptParts(str(o, "title"), whoamiTables(o["tables"]), fields, allMessages)
    }

    /**
     * Hono `VtopTable.rows` are arrays of key/value objects (not Kotlin's
     * `List<List<String>>`). Derive headers from the declared list or the union
     * of row keys and flatten each row to cells in that order.
     */
    private fun whoamiTables(el: JsonElement?): List<VtopTable> = when (el) {
        is JsonArray -> el.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val caption = str(o, "caption")
            val declared = (o["headers"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { h -> h.isNotBlank() } }
            val rowsEl = o["rows"] as? JsonArray
            if (rowsEl == null) {
                if (caption == null) null else VtopTable(caption, declared, emptyList())
            } else {
                val rowObjects = rowsEl.mapNotNull { it as? JsonObject }
                val derivedHeaders = if (rowObjects.isNotEmpty() && rowObjects.size == rowsEl.size) {
                    declared.ifEmpty {
                        buildList {
                            rowObjects.forEach { obj -> obj.keys.forEach { k -> if (k !in this) add(k) } }
                        }
                    }
                } else declared
                val rows = rowsEl.mapNotNull { row ->
                    when (row) {
                        is JsonObject -> derivedHeaders.map { h -> str(row, h).orEmpty() }
                            .takeIf { cells -> cells.any { it.isNotBlank() } }
                        is JsonArray -> row.map { jsonToText(it).trim() }
                            .takeIf { cells -> cells.any { it.isNotBlank() } }
                        else -> null
                    }
                }
                if (rows.isEmpty() && caption == null) null else VtopTable(caption, derivedHeaders, rows)
            }
        }
        else -> emptyList()
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
