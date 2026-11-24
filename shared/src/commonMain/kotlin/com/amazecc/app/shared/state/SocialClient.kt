package com.amazecc.app.shared.state

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Typed client for the server-backed social protocol (D-006/D-013) --- the Kotlin port of
 * `../AmazeCC/src/lib/social/client.ts` against the Hono routes
 * `../amazecc-api-hono/src/routes/services/social-routes`, mounted at `/api/social`
 * (see `AmazeClient.socialRequest`, the D-193 social transport).
 *
 * Like the web client this deliberately does NOT cache: every call is a fresh read of
 * server truth, so "the pair was not found" and "server down" arrive as distinct
 * [SocialApiError] codes. The transport keeps the HTTP status and raw body for non-2xx
 * responses, so the machine-readable `{ success: false, error, detail }` envelope is
 * mapped here rather than collapsing into "no data".
 */
object SocialClient {
    /** The only semester the friend finder is open for (web `UPCOMING_FFCS_SEMESTER`). */
    const val SOCIAL_SEMESTER_ID = "CH20262705"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Codes only a fresh VTOP sign-in can fix (web `SIGN_IN_CODES`). */
    private val vtopSignInCodes = setOf(
        "signin_required", "missing_credentials", "vtop_session_expired",
        "vtop_identity_unresolved", "vtop_session_required"
    )

    /** Codes only a fresh Amaze ID sign-in can fix (web `AMAZE_ID_SIGN_IN_CODES`). */
    private val amazeIdSignInCodes = setOf("amazeid_signin_required", "amazeid_session_expired")

    /** An Amaze ID can read existing enrollments; a fresh publish may need VTOP credentials. */
    private val vtopRetryCodes = setOf("vtop_session_required", "profile_incomplete")

    /** Student-facing messages for the server's codes (web `ERROR_MESSAGES`). */
    private val errorMessages = mapOf(
        "signin_required" to "Your VTOP sign-in needs refreshing before you can use the friend finder.",
        "missing_credentials" to "Your VTOP sign-in needs refreshing before you can use the friend finder.",
        "vtop_session_expired" to "Your VTOP session expired. Sign in again and retry.",
        "vtop_identity_unresolved" to "VTOP didn't confirm who you are. Sign in again and retry.",
        "amazeid_signin_required" to "Sign in with Amaze ID to use the friend finder.",
        "amazeid_session_expired" to "Your Amaze ID session expired. Sign in again and retry.",
        "vtop_session_required" to "Sign in with VTOP and load this semester's attendance before sharing your classes.",
        "change_limit" to "You've changed your classes too many times today. Try again tomorrow.",
        "not_joined" to "Share your classes first.",
        "no_current_semester" to "No social semester has been published yet.",
        "server_error" to "The server is unreachable right now.",
        "unexpected_response" to "The server did not return the expected data."
    )

    /** Public so hosts can pick the right sign-in affordance without string comparisons. */
    fun isAmazeIdSignInCode(code: String): Boolean = code in amazeIdSignInCodes
    fun isVtopSignInCode(code: String): Boolean = code in vtopSignInCodes
    fun messageFor(code: String): String? = errorMessages[code]

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    /** The server sends `detail` either as a string or as `{ detail: "..." }`. */
    private fun JsonObject.detailOf(): String? =
        stringOrNull("detail")
            ?: ((this["detail"] as? JsonObject)?.get("detail") as? JsonPrimitive)
                ?.content?.takeIf { it.isNotBlank() }

    /**
     * Resolve the caller's semester to the one the friend finder actually supports. The app's
     * selected semester is a VTOP id (often the fall one) and the web uses the "winter-2026-2027"
     * label; both must reach the server as `CH20262705`, never as a blocked/unsupported semester.
     */
    fun canonicalSocialSemester(requested: String?): String {
        val value = requested?.trim()?.uppercase().orEmpty()
        return when {
            value.isEmpty() -> SOCIAL_SEMESTER_ID
            value == SOCIAL_SEMESTER_ID -> SOCIAL_SEMESTER_ID
            value == "WINTER-2026-2027" -> SOCIAL_SEMESTER_ID
            value.startsWith("CH") && value.length == 10 -> SOCIAL_SEMESTER_ID
            else -> value
        }
    }

    /** Local token check only — no network, no cookies, no VTOP source requirement. */
    fun hasAmazeIdSession(): Boolean = SessionManager.hasAmazeIdAuth

    private fun requireAmazeIdSession() {
        if (!hasAmazeIdSession() || AmazeClient.isAmazeIdSignInRequired()) {
            throw SocialApiError("amazeid_session_expired", "Sign in with Amaze ID to use Social.")
        }
    }

    /** VTOP credentials are body data, sent only for publish or a server-requested retry. */
    private fun vtopSessionBody(): Map<String, JsonElement> = buildMap {
        val cookies = SessionManager.cookies.value?.takeIf { it.isNotBlank() }
        val authorizedID = SessionManager.authorizedID.value?.takeIf { it.isNotBlank() }
        val csrf = SessionManager.csrf.value?.takeIf { it.isNotBlank() }
        if (cookies != null && authorizedID != null && csrf != null) {
            put("cookies", JsonPrimitive(cookies))
            put("authorizedID", JsonPrimitive(authorizedID))
            put("csrf", JsonPrimitive(csrf))
        }
    }

    /** Web `handles[]` fallback: an older server only returns `me.handle`. */
    private fun withHandlesFallback(obj: JsonObject, handle: String?): JsonObject {
        val declared = (obj["handles"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.takeIf { h -> h.isNotBlank() }
        }
        val handles = if (declared.isNullOrEmpty()) {
            listOfNotNull(handle?.takeIf { it.isNotBlank() })
        } else declared
        return JsonObject(obj.toMap() + ("handles" to JsonArray(handles.map { JsonPrimitive(it) })))
    }

    private sealed interface Envelope {
        data class Ok(val body: JsonObject, val status: Int) : Envelope
        data class Err(val code: String, val detail: String?, val status: Int) : Envelope
    }

    /** Preserves status + body: a non-2xx is an [Envelope.Err], never a missing value. */
    private suspend fun send(
        path: String,
        method: String,
        query: Map<String, String>,
        body: Map<String, JsonElement>
    ): Envelope {
        val response = AmazeClient.socialRequest(path, method, query, JsonObject(body))
        val status = response.status
        val text = response.body?.takeIf { it.isNotBlank() }
        if (text == null) {
            val code = when (status) {
                401 -> "amazeid_session_expired"
                0 -> "server_error"
                else -> "http_$status"
            }
            return Envelope.Err(code, null, status)
        }
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
        if (element !is JsonObject) {
            return Envelope.Err("unexpected_response", "The server did not return a Social response.", status)
        }
        if (element.stringOrNull("success") == "false") {
            val code = element.stringOrNull("error")
                ?: if (status == 401) "amazeid_session_expired" else "http_$status"
            return Envelope.Err(code, element.detailOf(), status)
        }
        if (status !in 200..299) {
            val code = if (status == 401) "amazeid_session_expired" else "http_$status"
            return Envelope.Err(code, element.detailOf(), status)
        }
        return Envelope.Ok(element, status)
    }

    /** One validated social call: typed envelope errors, required-field checks, signal cleanup. */
    private suspend fun <T : Any> call(
        serializer: KSerializer<T>,
        path: String,
        what: String,
        body: Map<String, JsonElement> = emptyMap(),
        method: String = "POST",
        query: Map<String, String> = emptyMap(),
        retryWithVtop: Boolean = false,
        normalize: (JsonObject) -> JsonObject = { it },
        validate: (T) -> Boolean = { true }
    ): T {
        requireAmazeIdSession()
        var envelope = send(path, method, query, body)
        if (retryWithVtop && envelope is Envelope.Err && envelope.code in vtopRetryCodes) {
            val credentials = vtopSessionBody()
            if (credentials.isNotEmpty()) envelope = send(path, method, query, body + credentials)
        }
        val (obj, status) = when (envelope) {
            is Envelope.Ok -> envelope.body to envelope.status
            is Envelope.Err -> throw SocialApiError(envelope.code, envelope.detail, envelope.status)
        }
        val value = runCatching { json.decodeFromJsonElement(serializer, normalize(obj)) }
            .getOrElse { throw SocialApiError("unexpected_response", "The server did not return $what.", status) }
        if (!validate(value)) {
            throw SocialApiError("unexpected_response", "The server did not return $what.", status)
        }
        AmazeClient.clearAmazeIdSignInRequired()
        return value
    }

    // -- FFCS friend finder (D-006, op style port of POST /api/social/ffcs-match) ------

    /**
     * One request body for [ffcsMatchState], mirroring the web `ClassmatesRequest` union
     * from `../AmazeCC/src/lib/ffcsClassmates.ts`.
     */
    sealed interface FfcsMatchRequest {
        val semesterId: String

        /** POST op=get --- Classes Together state: per-class rosters and pairs of students. */
        data class Get(override val semesterId: String) : FfcsMatchRequest

        /** POST op=publish --- publish the caller's registered timetable as their classes. */
        data class Publish(
            override val semesterId: String,
            val source: String = "registered",
            val discoverable: Boolean = true
        ) : FfcsMatchRequest

        /** POST op=visibility --- flip the discoverability of the joined pair. */
        data class Visibility(override val semesterId: String, val discoverable: Boolean) : FfcsMatchRequest

        /** POST op=leave --- the caller leaves the match. */
        data class Leave(override val semesterId: String) : FfcsMatchRequest

        fun toBody(): Map<String, JsonElement> = buildMap {
            put("semesterId", JsonPrimitive(SocialClient.canonicalSocialSemester(semesterId)))
            when (this@FfcsMatchRequest) {
                is Get -> put("op", JsonPrimitive("get"))
                is Publish -> {
                    put("op", JsonPrimitive("publish"))
                    put("source", JsonPrimitive(source))
                    // JSON boolean: Hono reads `typeof body.discoverable !== "false"`.
                    put("discoverable", JsonPrimitive(discoverable))
                }
                is Visibility -> {
                    put("op", JsonPrimitive("visibility"))
                    put("discoverable", JsonPrimitive(discoverable))
                }
                is Leave -> put("op", JsonPrimitive("leave"))
            }
        }
    }

    /**
     * POST /api/social/ffcs-match --- Classes Together state for [request].
     *
     * Requires only the Amaze ID token in LOCAL mode. VTOP credentials ride in the JSON body
     * for a publish, and are added on a server-requested `vtop_session_required` retry.
     */
    suspend fun ffcsMatchState(request: FfcsMatchRequest): FfcsMatchState {
        val body = if (request is FfcsMatchRequest.Publish) {
            request.toBody() + vtopSessionBody()
        } else {
            request.toBody()
        }
        return call(
            FfcsMatchState.serializer(),
            "social/ffcs-match",
            "friend finder data",
            body = body,
            retryWithVtop = true,
            normalize = { obj ->
                withHandlesFallback(obj, (obj["me"] as? JsonObject)?.stringOrNull("handle"))
            },
            validate = { state ->
                state.success && state.semesterId.isNotBlank() && state.me.handle.isNotBlank()
            }
        )
    }

    /** GET /api/social/ffcs-match --- discoverable class rosters (no VTOP session needed). */
    suspend fun ffcsMatchClasses(semesterId: String, classIds: List<String> = emptyList()): FfcsClassesResponse {
        val query = buildMap<String, String> {
            put("semesterId", canonicalSocialSemester(semesterId))
            if (classIds.isNotEmpty()) {
                put("classIds", classIds.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.joinToString(","))
            }
        }
        return call(
            FfcsClassesResponse.serializer(),
            "social/ffcs-match",
            "class rosters",
            method = "GET",
            query = query,
            validate = { it.success && it.semesterId.isNotBlank() }
        )
    }

    // -- People (D-006/D-013) -----------------------------------------------------------

    /** POST /api/social/people --- look up a person by AMZ handle. */
    suspend fun lookupPerson(handle: String): PersonResponse = call(
        PersonResponse.serializer(),
        "social/people",
        "a person",
        body = mapOf("handle" to JsonPrimitive(handle.trim())),
        validate = { it.success && it.person.handle.isNotBlank() }
    )

    /**
     * POST /api/social/pair/claim --- claim a pairing grant with a peer.
     * An existing grant answers `created:false` with no `secret`; that is a valid result, not an
     * error and never a reason to store a fabricated secret.
     */
    suspend fun claimPair(handle: String, visibility: SocialVisibility = SocialVisibility.FULL): ClaimResponse = call(
        ClaimResponse.serializer(),
        "social/pair/claim",
        "a pairing",
        body = mapOf(
            "handle" to JsonPrimitive(handle.trim()),
            "visibility" to JsonPrimitive(visibility.name.lowercase())
        ),
        validate = {
            it.success && it.grantId.isNotBlank() && (!it.created || it.hasSecret)
        }
    )

    /** POST /api/social/grant/revoke --- revoke a grant (either participant). */
    suspend fun revokeGrant(grantId: String, secret: String): GrantRevokeResponse = call(
        GrantRevokeResponse.serializer(),
        "social/grant/revoke",
        "a revocation",
        body = mapOf("grantId" to JsonPrimitive(grantId), "secret" to JsonPrimitive(secret)),
        validate = { it.success && it.grantId.isNotBlank() }
    )

    /** POST /api/social/grant/visibility --- change a grant's visibility. */
    suspend fun setGrantVisibility(grantId: String, visibility: SocialVisibility): GrantVisibilityResponse = call(
        GrantVisibilityResponse.serializer(),
        "social/grant/visibility",
        "a visibility change",
        body = mapOf(
            "grantId" to JsonPrimitive(grantId),
            "visibility" to JsonPrimitive(visibility.name.lowercase())
        ),
        validate = { it.success && it.grantId.isNotBlank() }
    )

    /** POST /api/social/timetable --- read a peer's timetable through a grant. */
    suspend fun readPeerTimetable(secret: String, handle: String, semester: String? = null): PeerTimetableResponse {
        val body = buildMap<String, JsonElement> {
            put("secret", JsonPrimitive(secret))
            put("handle", JsonPrimitive(handle.trim()))
            if (!semester.isNullOrBlank()) put("semester", JsonPrimitive(semester))
        }
        return call(
            PeerTimetableResponse.serializer(),
            "social/timetable",
            "a timetable",
            body = body,
            validate = { it.success && it.identity.handle.isNotBlank() }
        )
    }

    /** GET /api/social/semester --- the current social semester (Amaze ID auth, no VTOP creds). */
    suspend fun fetchCurrentSemester(): SemesterResponse = call(
        SemesterResponse.serializer(),
        "social/semester",
        "a social semester",
        method = "GET",
        validate = { it.success && !it.semesterId.isNullOrBlank() }
    )

    /** POST /api/social/identity/sync --- push our own derived state and pull peers + grants. */
    suspend fun identitySync(proposedSemesterId: String? = null): SocialSyncPayload {
        val body = if (proposedSemesterId.isNullOrBlank()) emptyMap() else mapOf("proposedSemesterId" to JsonPrimitive(proposedSemesterId))
        return call(
            SocialSyncPayload.serializer(),
            "social/identity/sync",
            "an identity",
            body = body,
            normalize = { obj ->
                withHandlesFallback(obj, (obj["identity"] as? JsonObject)?.stringOrNull("handle"))
            },
            validate = { it.success && it.identity.handle.isNotBlank() }
        )
    }
}

/** Which identity the caller must refresh; kept distinct so the UI never asks for the wrong one. */
enum class SocialSignInKind { NONE, AMAZE_ID, VTOP }

/** Machine-readable failure of a social call (D-006/D-013, port of web `SocialApiError`). */
class SocialApiError(
    /** The server's `error` code, e.g. `"handle_not_found"`. */
    val code: String,
    /** The server's human-readable `detail`, when it sent one. */
    val detail: String? = null,
    /** HTTP status of the failed call, when one was received. */
    val httpStatus: Int? = null
) : Exception(detail ?: SocialClient.messageFor(code) ?: code) {
    /** UI hook: true when the caller should surface "Sign in with Amaze ID". */
    val needsAmazeIdSignIn: Boolean
        get() = SocialClient.isAmazeIdSignInCode(code)

    /** UI hook: true when the caller should surface "Sign in with VTOP". */
    val needsVtopSignIn: Boolean
        get() = SocialClient.isVtopSignInCode(code)

    /** Single decision for the sign-in affordance; never conflates the two identities. */
    val requiredSignIn: SocialSignInKind
        get() = when {
            needsAmazeIdSignIn -> SocialSignInKind.AMAZE_ID
            needsVtopSignIn -> SocialSignInKind.VTOP
            else -> SocialSignInKind.NONE
        }
}

// -- DTO set: mirrors ../AmazeCC/src/types/social.ts + API src/routes/services/social-routes --

/** Mirror of web `SocialVisibility = "coarse" | "full"`. */
@Serializable
enum class SocialVisibility {
    @kotlinx.serialization.SerialName("coarse") COARSE,
    @kotlinx.serialization.SerialName("full") FULL
}

/** One occupied slot. Coarse mode returns `{}` --- keys only, no values. */
@Serializable
@Immutable
data class BusyEntry(
    val c: String? = null,
    val t: String? = null,
    val v: String? = null
)

/**
 * Tolerant `BusyMap` codec (D-006). API `routes/services/social/timetable.ts:143` returns an
 * object of `{ c?, t?, v? }` entries for FULL grants, but `Object.keys(busyMap)` — a plain
 * `string[]` — for COARSE grants. Both are normalised to `key -> BusyEntry()` / decoded
 * entries so callers only ever handle one map shape, and a coarse array never throws a
 * `SerializationException`.
 */
object CoarseOrFullBusyMapSerializer : KSerializer<Map<String, BusyEntry>> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Map<String, BusyEntry> {
        val element = JsonElement.serializer().deserialize(decoder)
        return when (element) {
            is JsonObject -> element.entries.associate { (key, value) ->
                val obj = value as? JsonObject
                key to BusyEntry(
                    c = obj?.get("c")?.let { (it as? JsonPrimitive)?.content },
                    t = obj?.get("t")?.let { (it as? JsonPrimitive)?.content },
                    v = obj?.get("v")?.let { (it as? JsonPrimitive)?.content }
                )
            }
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.associateWith { BusyEntry() }
            else -> emptyMap()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, BusyEntry>) {
        encoder.encodeSerializableValue(
            JsonElement.serializer(),
            JsonObject(value.mapValues { (_, entry) ->
                JsonObject(buildMap<String, JsonElement> {
                    entry.c?.let { put("c", JsonPrimitive(it)) }
                    entry.t?.let { put("t", JsonPrimitive(it)) }
                    entry.v?.let { put("v", JsonPrimitive(it)) }
                })
            })
        )
    }
}

/** Mirror of web `SocialCourse` (per-course timetable entry in a peer timetable). */
@Serializable
@Immutable
data class SocialCourse(
    val code: String = "",
    val title: String = "",
    val venue: String = "",
    val ltpjc: String = "",
    val category: String = "",
    val componentType: String = "",
    val classId: String = "",
    val faculty: String = ""
)

/** Mirror of web `SocialIdentity` (cached for display only, never trusted server-side). */
@Serializable
@Immutable
data class SocialIdentity(
    val ownerKey: String = "",
    val handle: String = "",
    val displayName: String = "",
    val semesterId: String = "",
    val semesterLabel: String = "",
    val derivedAt: String = "",
    val slotmapVersion: String = ""
)

/** Mirror of web `SocialPeer` --- a person the student already has a grant with. */
@Serializable
@Immutable
data class SocialPeer(
    val handle: String = "",
    val name: String? = null,
    val visibility: SocialVisibility = SocialVisibility.FULL,
    val shared: Boolean = false,
    val lastPublishedAt: String? = null,
    val semesterId: String? = null,
    val isSelf: Boolean = false
)

/** Mirror of web `SocialStoredGrant` --- a pairing grant + its bearer secret. */
@Serializable
@Immutable
data class SocialStoredGrant(
    val grantId: String = "",
    val secret: String = "",
    val peerHandle: String? = null,
    val visibility: SocialVisibility = SocialVisibility.COARSE,
    val createdAt: String? = null
)

/** Mirror of web `SocialSyncPayload` (`POST /api/social/identity/sync`). */
@Serializable
@Immutable
data class SocialSyncPayload(
    val success: Boolean = false,
    val handles: List<String> = emptyList(),
    val identity: SocialIdentity = SocialIdentity(),
    val semesterSource: String = "",
    val version: Int = 0,
    val busyMap: Map<String, BusyEntry> = emptyMap(),
    val courses: List<SocialCourse> = emptyList(),
    val peers: List<SocialPeer> = emptyList(),
    val grantSecrets: List<SocialStoredGrant> = emptyList()
)

// -- DTO set: FFCS friend-finder (POST /api/social/ffcs-match) -------------------------

/** Per-class entry of a roster --- GET-side shape used by FFCS matrix/social surfaces. */
@Serializable
@Immutable
data class FfcsRosterClass(
    val classId: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val slot: String = "",
    val faculty: String = "",
    val credits: String = "",
    val room: String = "",
    val students: List<FfcsRosterStudent> = emptyList()
)

/** A student enrolled in a roster class (`POST /ffcs-match` GET roster shape). */
@Serializable
@Immutable
data class FfcsRosterStudent(
    val regNo: String = "",
    val name: String = "",
    val handle: String = ""
)

/** FFCS offering record (`buildMatchState` sections[].offering; nullable upstream). */
@Serializable
@Immutable
data class FfcsOffering(
    val classId: String = "",
    val code: String = "",
    val title: String = "",
    val type: String = "",
    val credits: String = "",
    val room: String = "",
    val slot: String = "",
    val faculty: String = ""
)

/** A peer appearing in one class roster or in the pair overlaps. */
@Serializable
@Immutable
data class FfcsMatchPerson(
    val handle: String = "",
    val name: String = "",
    val regNo: String = "",
    val source: String = "registered"
)

/** One Classes Together section: a class + the students paired in it. */
@Serializable
@Immutable
data class FfcsMatchSection(
    val classId: String = "",
    val offering: FfcsOffering? = null,
    val registerNos: List<String> = emptyList(),
    val people: List<FfcsMatchPerson> = emptyList()
)

/** Two students who share >= 2 classes (or identical timetables). */
@Serializable
@Immutable
data class FfcsMatchOverlap(
    val handle: String = "",
    val name: String = "",
    val regNo: String = "",
    val source: String? = "registered",
    val shared: Int = 0,
    val sharedClassIds: List<String> = emptyList(),
    val identical: Boolean = false,
    val updatedAt: String = ""
)

/** The caller's own Classes Together state (`POST /ffcs-match` op=get). */
@Serializable
@Immutable
data class FfcsMatchState(
    val success: Boolean = false,
    val semesterId: String = "",
    val handles: List<String> = emptyList(),
    val participants: Int = 0,
    val me: FfcsMatchMe = FfcsMatchMe(),
    val sections: List<FfcsMatchSection> = emptyList(),
    val overlaps: List<FfcsMatchOverlap> = emptyList()
)

@Serializable
@Immutable
data class FfcsMatchMe(
    val handle: String = "",
    val name: String = "",
    val joined: Boolean = false,
    val discoverable: Boolean = false,
    val source: String? = null,
    val classIds: List<String> = emptyList(),
    val updatedAt: String? = null,
    val changesLeft: Int = 0
)

/** GET /ffcs-match shape --- public class rosters for FFCS matrix surfaces. */
@Serializable
@Immutable
data class FfcsClassesResponse(
    val success: Boolean = false,
    val semesterId: String = "",
    /** Required by the wire contract; absence means a wrong host/proxy, never "zero classes". */
    val classes: List<FfcsRosterClass>
)

// -- DTO set: people / pairing (POST /api/social/people, /social/pair/claim) -----------

@Serializable
@Immutable
data class PersonData(
    val handle: String = "",
    val displayName: String? = null,
    val alreadyPaired: Boolean = false
)

@Serializable
data class PersonResponse(
    val success: Boolean = false,
    val person: PersonData = PersonData()
)

@Serializable
@Immutable
data class ClaimPeer(
    val handle: String = "",
    val name: String? = null,
    val lastPublishedAt: String? = null,
    val semesters: List<String> = emptyList()
)

@Serializable
@Immutable
data class ClaimResponse(
    val success: Boolean = false,
    val grantId: String = "",
    val secret: String? = null,
    val created: Boolean = false,
    val visibility: SocialVisibility = SocialVisibility.COARSE,
    val peer: ClaimPeer = ClaimPeer()
) {
    /** A newly created grant always carries a bearer secret. */
    val hasSecret: Boolean get() = !secret.isNullOrBlank()

    /** `created:false, secret:null` is a valid existing grant — never fabricate a secret. */
    val isExistingGrant: Boolean get() = success && grantId.isNotBlank() && !created
}

@Serializable
@Immutable
data class GrantRevokeResponse(
    val success: Boolean = false,
    val grantId: String = ""
)

@Serializable
@Immutable
data class GrantVisibilityResponse(
    val success: Boolean = false,
    val grantId: String = "",
    val visibility: SocialVisibility = SocialVisibility.COARSE
)

// -- DTO set: timetable read (POST /api/social/timetable) -----------------------------

@Serializable
@Immutable
data class PeerTimetableIdentity(
    val handle: String = "",
    val displayName: String? = null,
    val semesterId: String = "",
    val semesterLabel: String = "",
    val publishedAt: String = "",
    val slotmapVersion: String = ""
)

@Serializable
@Immutable
data class PeerTimetableResponse(
    val success: Boolean = false,
    val identity: PeerTimetableIdentity = PeerTimetableIdentity(),
    val visibility: SocialVisibility = SocialVisibility.FULL,
    val stale: Boolean = false,
    val version: Int = 0,
    @Serializable(with = CoarseOrFullBusyMapSerializer::class)
    val busyMap: Map<String, BusyEntry> = emptyMap(),
    val courses: List<SocialCourse> = emptyList()
)

@Serializable
@Immutable
data class SemesterResponse(
    val success: Boolean = false,
    val semesterId: String? = null,
    val semesterLabel: String = "",
    val resolvedAt: String? = null
)
