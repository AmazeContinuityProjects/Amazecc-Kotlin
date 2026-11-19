package com.amazecc.app.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
 * One campus contact row from the folded `/api/remote/whoami` `contact` block.
 * Mirrors the Hono `parseContact` shape (`office`, `description`, optional `email`).
 */
@Serializable
data class ContactEntry(
    val office: String = "",
    val description: String = "",
    val email: String? = null
)

/** A single document listed on the admissions acknowledgement page. */
@Serializable
data class AcknowledgementDocument(
    val serialNo: String? = null,
    val documentName: String? = null,
    val status: String? = null
)

/**
 * Admissions acknowledgement block folded into the consolidated whoami payload.
 * Unknown/extra scalar keys stay out of the model; the canonical fields below are
 * the ones the Hono `cleanAcknowledgement` helper emits.
 */
@Serializable
data class AcknowledgementInfo(
    val title: String? = null,
    val documents: List<AcknowledgementDocument> = emptyList(),
    val applicationNo: String? = null,
    val registerNo: String? = null,
    val programme: String? = null,
    val branch: String? = null,
    val campus: String? = null
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
    /**
     * Tri-state residence derived from the consolidated whoami payload.
     * Values: "HOSTELLER", "DAYSCHOLAR", or null (unknown — fall back to the
     * onboarding choice). Kept as a STRING (not a boolean) so the ME tier can
     * overwrite it in BOTH directions — [UserMerge.boolean] only propagates true.
     */
    val residenceStatus: String? = null,
    val friendlyName: String? = null,
    val applicationNumber: String? = null,
    // Academic
    val program: String? = null,
    val school: String? = null,
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
    val friendMobile: String? = null,
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
    val dayboarderTitle: String? = null,
    // Folded whoami-only sources (contact / acknowledgement / EPT)
    val contacts: List<ContactEntry> = emptyList(),
    val contactTitle: String? = null,
    val acknowledgement: AcknowledgementInfo? = null,
    val eptTitle: String? = null,
    val eptFields: List<KeyValueRow> = emptyList(),
    val eptMessages: List<KeyValueRow> = emptyList(),
    val apaar: ApaarInfo? = null,
    val bank: BankInfo? = null
) {
    val displayName: String get() = friendlyName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: regNo ?: "Student"

    val displayRegNo: String get() = regNo ?: ""

    val initials: String get() {
        // A real name yields the first letter of up to two words ("Aditya Sharma" -> AS).
        // When no name is available the registration number is the identity, so use its
        // leading two characters ("21BCE1001" -> 21) rather than a single "2".
        val named = friendlyName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
        val fromName = named?.trim()?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("") { it.first().uppercase() }
            ?.takeIf { it.isNotBlank() }
        return fromName ?: regNo?.trim()?.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "?"
    }

    val hasIdentity: Boolean
        get() = regNo != null || name != null || photoBase64 != null
}

/** Canonical values carried by [StudentIdentity.residenceStatus]. */
const val RESIDENCE_HOSTELLER = "HOSTELLER"
const val RESIDENCE_DAYSCHOLAR = "DAYSCHOLAR"

/**
 * Mapped result of the consolidated whoami fetch. [identity] is already in the
 * canonical [StudentIdentity] shape and is merged into the store as a whole
 * under [com.amazecc.app.shared.state.IdentitySource.ME]. Kept (rather than
 * replaced) so existing callers keep compiling; [WhoamiRes] is the wire shape.
 */
@Serializable
data class MeRes(
    val success: Boolean = false,
    val identity: StudentIdentity? = null,
    val error: String? = null
)

/**
 * Flat wire response of `POST /api/remote/whoami` (legacy alias `/api/whoami`).
 *
 * The server spreads the canonical identity at the top level rather than nesting
 * it under an `identity` key. Nested blocks are typed as [JsonElement] on purpose:
 * the sibling parser's shapes evolve independently and the extractor deciphers
 * them defensively. HTTP 200 may still report `success = false` with [fetchErrors].
 */
@Serializable
data class WhoamiRes(
    val success: Boolean = false,
    val error: String? = null,
    val fetchErrors: Map<String, String>? = null,
    // ── Identity core + academic + personal (flat scalars) ──
    val regNo: String? = null,
    val name: String? = null,
    val email: String? = null,
    val mobile: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val photoBase64: String? = null,
    val isHosteller: Boolean = false,
    val friendlyName: String? = null,
    val applicationNumber: String? = null,
    val program: String? = null,
    val school: String? = null,
    val campus: String? = null,
    val batch: String? = null,
    val section: String? = null,
    val advisorName: String? = null,
    val nationality: String? = null,
    val nativeLanguage: String? = null,
    val nativeState: String? = null,
    val community: String? = null,
    val religion: String? = null,
    val caste: String? = null,
    val physicallyChallenged: String? = null,
    val aadharNumber: String? = null,
    val friendMobile: String? = null,
    val guardian: String? = null,
    @Contextual val currentAddress: JsonElement? = null,
    @Contextual val permanentAddress: JsonElement? = null,
    @Contextual val father: JsonElement? = null,
    @Contextual val mother: JsonElement? = null,
    // ── Folded nested blocks (resilient raw JSON) ──
    @Contextual val proctor: JsonElement? = null,
    @Contextual val hodDean: JsonElement? = null,
    @Contextual val credentials: JsonElement? = null,
    @Contextual val ranks: JsonElement? = null,
    @Contextual val apaar: JsonElement? = null,
    @Contextual val bank: JsonElement? = null,
    @Contextual val contact: JsonElement? = null,
    val contactTitle: String? = null,
    @Contextual val acknowledgement: JsonElement? = null,
    @Contextual val dayboarder: JsonElement? = null,
    @Contextual val eptSchedule: JsonElement? = null
)
