package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.StudentIdentity

/**
 * Priority tier of an identity source. Higher order = more authoritative.
 * Tiers are fixed and deterministic, so parallel sync fetches resolve
 * identically regardless of arrival order.
 */
enum class IdentitySource(val order: Int) {
    SESSION(0),         // login response
    VTOP_PHOTO(1),      // legacy pre-store photo cache
    STUDENT(2),         // /api/student — canonical identity
    PROFILE_IMAGES(3),  // /api/profile-images — proctor photo/details, HoD/Dean
    CREDENTIALS(4),     // /api/credentials — linked accounts + ranks
    BANK(5),            // /api/bank-info
    APAAR(6),           // /api/apaarid
    RECORDS(7),          // ept / registration / university-day / dayboarder
    ME(8)                // /api/me — consolidated snapshot of tiers 2–6, highest authority
}

/**
 * Per-leaf merge of two [StudentIdentity] instances.
 *
 * Rules:
 *  - fragments carry only filled values; an empty incoming value never erases existing data
 *  - a filled incoming value replaces the current one only when [order] >= the recorded
 *    (or default) source of that path
 *  - collections replace wholesale when non-empty; booleans propagate only `true`
 *
 * [sources] maps dotted paths (e.g. "proctor.email") to the tier that last wrote them.
 */
internal fun mergeIdentity(
    current: StudentIdentity,
    fragment: StudentIdentity,
    order: Int,
    sources: MutableMap<String, Int>
): StudentIdentity {
    val m = Merger(order, sources)
    return StudentIdentity(
        regNo = m.str(current.regNo, fragment.regNo, "regNo"),
        name = m.str(current.name, fragment.name, "name"),
        email = m.str(current.email, fragment.email, "email"),
        mobile = m.str(current.mobile, fragment.mobile, "mobile"),
        dob = m.str(current.dob, fragment.dob, "dob"),
        gender = m.str(current.gender, fragment.gender, "gender"),
        bloodGroup = m.str(current.bloodGroup, fragment.bloodGroup, "bloodGroup"),
        photoBase64 = m.str(current.photoBase64, fragment.photoBase64, "photoBase64"),
        isHosteller = m.boolean(current.isHosteller, fragment.isHosteller, "isHosteller"),
        program = m.str(current.program, fragment.program, "program"),
        campus = m.str(current.campus, fragment.campus, "campus"),
        batch = m.str(current.batch, fragment.batch, "batch"),
        section = m.str(current.section, fragment.section, "section"),
        advisorName = m.str(current.advisorName, fragment.advisorName, "advisorName"),
        nationality = m.str(current.nationality, fragment.nationality, "nationality"),
        nativeLanguage = m.str(current.nativeLanguage, fragment.nativeLanguage, "nativeLanguage"),
        nativeState = m.str(current.nativeState, fragment.nativeState, "nativeState"),
        community = m.str(current.community, fragment.community, "community"),
        religion = m.str(current.religion, fragment.religion, "religion"),
        caste = m.str(current.caste, fragment.caste, "caste"),
        physicallyChallenged = m.str(current.physicallyChallenged, fragment.physicallyChallenged, "physicallyChallenged"),
        aadharNumber = m.str(current.aadharNumber, fragment.aadharNumber, "aadharNumber"),
        currentAddress = m.list(current.currentAddress, fragment.currentAddress, "currentAddress"),
        permanentAddress = m.list(current.permanentAddress, fragment.permanentAddress, "permanentAddress"),
        father = m.list(current.father, fragment.father, "father"),
        mother = m.list(current.mother, fragment.mother, "mother"),
        guardian = m.str(current.guardian, fragment.guardian, "guardian"),
        proctor = m.official(current.proctor, fragment.proctor, "proctor"),
        hodDean = m.list(current.hodDean, fragment.hodDean, "hodDean"),
        credentials = m.list(current.credentials, fragment.credentials, "credentials"),
        ranks = m.list(current.ranks, fragment.ranks, "ranks"),
        eptTables = m.list(current.eptTables, fragment.eptTables, "eptTables"),
        registrationFields = m.list(current.registrationFields, fragment.registrationFields, "registrationFields"),
        registrationTables = m.list(current.registrationTables, fragment.registrationTables, "registrationTables"),
        universityDayTitle = m.str(current.universityDayTitle, fragment.universityDayTitle, "universityDayTitle"),
        universityDayFields = m.list(current.universityDayFields, fragment.universityDayFields, "universityDayFields"),
        universityDayTables = m.list(current.universityDayTables, fragment.universityDayTables, "universityDayTables"),
        dayboarder = m.dayboarder(current.dayboarder, fragment.dayboarder, "dayboarder"),
        apaar = m.apaar(current.apaar, fragment.apaar, "apaar"),
        bank = m.bank(current.bank, fragment.bank, "bank")
    )
}

/** Lowest tier that may legitimately hold a path; used until a live merge records the real source. */
internal fun defaultSourceFor(path: String): Int {
    val source = when {
        path == "photoBase64" || path == "regNo" || path == "name" -> IdentitySource.STUDENT
        path == "proctor" || path.startsWith("proctor.") || path == "hodDean" -> IdentitySource.STUDENT
        else -> IdentitySource.SESSION
    }
    return source.order
}

private class Merger(
    private val order: Int,
    private val sources: MutableMap<String, Int>
) {
    private fun canWrite(path: String): Boolean = order >= (sources[path] ?: defaultSourceFor(path))

    fun str(current: String?, incoming: String?, path: String): String? {
        val value = incoming?.takeIf { it.isNotBlank() }
        if (value == null) return current
        return if (canWrite(path)) {
            sources[path] = order
            value
        } else {
            current
        }
    }

    fun boolean(current: Boolean, incoming: Boolean, path: String): Boolean {
        if (!incoming) return current
        return if (canWrite(path)) {
            sources[path] = order
            true
        } else {
            current
        }
    }

    fun <T> list(current: List<T>, incoming: List<T>, path: String): List<T> {
        if (incoming.isEmpty()) return current
        return if (canWrite(path)) {
            sources[path] = order
            incoming
        } else {
            current
        }
    }

    fun official(current: com.amazecc.app.shared.model.Official?, incoming: com.amazecc.app.shared.model.Official?, path: String): com.amazecc.app.shared.model.Official? {
        if (incoming == null) return current
        val c = current ?: com.amazecc.app.shared.model.Official()
        return com.amazecc.app.shared.model.Official(
            role = str(c.role, incoming.role, "$path.role"),
            name = str(c.name, incoming.name, "$path.name"),
            designation = str(c.designation, incoming.designation, "$path.designation"),
            email = str(c.email, incoming.email, "$path.email"),
            phone = str(c.phone, incoming.phone, "$path.phone"),
            school = str(c.school, incoming.school, "$path.school"),
            cabin = str(c.cabin, incoming.cabin, "$path.cabin"),
            department = str(c.department, incoming.department, "$path.department"),
            intercom = str(c.intercom, incoming.intercom, "$path.intercom"),
            facultyId = str(c.facultyId, incoming.facultyId, "$path.facultyId"),
            photoBase64 = str(c.photoBase64, incoming.photoBase64, "$path.photoBase64"),
            extras = list(c.extras, incoming.extras, "$path.extras")
        )
    }

    fun dayboarder(current: com.amazecc.app.shared.model.DayboarderInfo?, incoming: com.amazecc.app.shared.model.DayboarderInfo?, path: String): com.amazecc.app.shared.model.DayboarderInfo? {
        if (incoming == null) return current
        val c = current ?: com.amazecc.app.shared.model.DayboarderInfo()
        return com.amazecc.app.shared.model.DayboarderInfo(
            isDayboarder = boolean(c.isDayboarder, incoming.isDayboarder, "$path.isDayboarder"),
            fields = list(c.fields, incoming.fields, "$path.fields")
        )
    }

    fun apaar(current: com.amazecc.app.shared.model.ApaarInfo?, incoming: com.amazecc.app.shared.model.ApaarInfo?, path: String): com.amazecc.app.shared.model.ApaarInfo? {
        if (incoming == null) return current
        val c = current ?: com.amazecc.app.shared.model.ApaarInfo()
        return com.amazecc.app.shared.model.ApaarInfo(
            hasApaar = boolean(c.hasApaar, incoming.hasApaar, "$path.hasApaar"),
            fields = list(c.fields, incoming.fields, "$path.fields"),
            tables = list(c.tables, incoming.tables, "$path.tables")
        )
    }

    fun bank(current: com.amazecc.app.shared.model.BankInfo?, incoming: com.amazecc.app.shared.model.BankInfo?, path: String): com.amazecc.app.shared.model.BankInfo? {
        if (incoming == null) return current
        val c = current ?: com.amazecc.app.shared.model.BankInfo()
        return com.amazecc.app.shared.model.BankInfo(
            name = str(c.name, incoming.name, "$path.name"),
            branch = str(c.branch, incoming.branch, "$path.branch"),
            address = str(c.address, incoming.address, "$path.address"),
            fields = list(c.fields, incoming.fields, "$path.fields")
        )
    }
}
