package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

/**
 * A single cleaned label/value row. Both fields are guaranteed non-blank after filtering.
 */
@Serializable
data class KeyValueRow(
    val label: String = "",
    val value: String = ""
)

/**
 * A normalized VTOP table: string cells only, empty rows dropped. When [headers] is empty
 * the table is a plain list of text lines rendered as one column.
 */
@Serializable
data class VtopTable(
    val caption: String? = null,
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

/**
 * A university official (proctor / HoD / Dean) merged from the shapes the API returns
 * (/api/student `profile.proctor` and /api/profile-images `proctor` / `hodDean`).
 * Unrecognized VTOP labels surface in [extras].
 */
@Serializable
data class Official(
    val role: String? = null,
    val name: String? = null,
    val designation: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val school: String? = null,
    val cabin: String? = null,
    val department: String? = null,
    val intercom: String? = null,
    val facultyId: String? = null,
    val photoBase64: String? = null,
    val extras: List<KeyValueRow> = emptyList()
)

/**
 * A linked campus account (was `ProfileImagesCredential`; `password` was `defaultCredentials`).
 */
@Serializable
data class AccountCredential(
    val account: String = "",
    val username: String = "",
    val password: String = "",
    val url: String? = null,
    val venueDate: String = "",
    val seatLocation: String = ""
)

@Serializable
data class RankInfo(
    val name: String = "",
    val rank: String = ""
)

/**
 * Dayboarder details deciphered from the raw VTOP form. [fields] are ordered canonically
 * via fuzzy label matching; placeholders are filtered out.
 */
@Serializable
data class DayboarderInfo(
    val isDayboarder: Boolean = false,
    val fields: List<KeyValueRow> = emptyList()
)

@Serializable
data class ApaarInfo(
    val hasApaar: Boolean = false,
    val fields: List<KeyValueRow> = emptyList(),
    val tables: List<VtopTable> = emptyList()
)

@Serializable
data class BankInfo(
    val name: String? = null,
    val branch: String? = null,
    val address: String? = null,
    val fields: List<KeyValueRow> = emptyList()
)

/**
 * The single source of truth for the student's identity. Every profile endpoint syncs a
 * filtered fragment into this model via [com.amazecc.app.shared.state.UserStore].
 * All fields are optional; empty data is `null` / empty list / `false` — never placeholders.
 */
@Serializable
data class StudentIdentity(
    // Identity core
    val regNo: String? = null,
    val name: String? = null,
    val email: String? = null,
    val mobile: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val photoBase64: String? = null,
    val isHosteller: Boolean = false,
    // Academic
    val program: String? = null,
    val campus: String? = null,
    val batch: String? = null,
    val section: String? = null,
    val advisorName: String? = null,
    // Personal
    val nationality: String? = null,
    val nativeLanguage: String? = null,
    val nativeState: String? = null,
    val community: String? = null,
    val religion: String? = null,
    val caste: String? = null,
    val physicallyChallenged: String? = null,
    val aadharNumber: String? = null,
    // Family & residence
    val currentAddress: List<KeyValueRow> = emptyList(),
    val permanentAddress: List<KeyValueRow> = emptyList(),
    val father: List<KeyValueRow> = emptyList(),
    val mother: List<KeyValueRow> = emptyList(),
    val guardian: String? = null,
    // Officials
    val proctor: Official? = null,
    val hodDean: List<Official> = emptyList(),
    // Credentials & ranks
    val credentials: List<AccountCredential> = emptyList(),
    val ranks: List<RankInfo> = emptyList(),
    // Records (deciphered)
    val eptTables: List<VtopTable> = emptyList(),
    val registrationFields: List<KeyValueRow> = emptyList(),
    val registrationTables: List<VtopTable> = emptyList(),
    val universityDayTitle: String? = null,
    val universityDayFields: List<KeyValueRow> = emptyList(),
    val universityDayTables: List<VtopTable> = emptyList(),
    val dayboarder: DayboarderInfo? = null,
    val apaar: ApaarInfo? = null,
    val bank: BankInfo? = null
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: regNo ?: "Student"

    val displayRegNo: String get() = regNo ?: ""

    val initials: String get() = displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    val hasIdentity: Boolean
        get() = regNo != null || name != null || photoBase64 != null
}
