package com.amazecc.app.shared.state

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.api.AmazeClient
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
 * (see `AmazeClient.postAuthorized`, the single HTTP client).
 *
 * Like the web client this deliberately does NOT cache: every call is a fresh read of
 * server truth, so "the pair was not found" and "server down" arrive as distinct
 * [SocialApiError] codes. Unlike the web client it does NOT swallow errors ---
 * `AmazeClient` returns `null` for a non-OK status, so the machine-readable error
 * envelope is lost; [SocialClient] translates it back here.
 */
object SocialClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    /** Server error envelope: `{ success: false, error, detail: { detail }? }`. */
    private fun JsonObject.errorOf(): SocialApiError {
        val detail = ((this["detail"] as? JsonObject)?.get("detail") as? JsonPrimitive)
            ?.content?.takeIf { it.isNotBlank() }
        return SocialApiError(stringOrNull("error") ?: "unknown", detail)
    }

    private suspend fun <T : Any> post(serializer: KSerializer<T>, path: String, body: Map<String, JsonElement>, what: String): T {
        val raw = AmazeClient.postAuthorizedValues<JsonElement>(path, body)
            ?: throw SocialApiError("server_error", "The server did not return $what.")
        (raw as? JsonObject)?.let { obj -> if (obj.stringOrNull("success") == "false") throw obj.errorOf() }
        return json.decodeFromJsonElement(serializer, raw)
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
            put("semesterId", JsonPrimitive(semesterId.trim().uppercase()))
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

    /** POST /api/social/ffcs-match --- Classes Together state for [request] on [semesterId]. */
    suspend fun ffcsMatchState(request: FfcsMatchRequest): FfcsMatchState {
        val state = post(FfcsMatchState.serializer(), "social/ffcs-match", request.toBody(), "friend finder data")
        if (!state.success || state.me.handle.isEmpty()) {
            throw SocialApiError("unexpected_response", "The server did not return friend finder data.")
        }
        return state
    }

    /** GET /api/social/ffcs-match --- discoverable class rosters (no VTOP session needed). */
    suspend fun ffcsMatchClasses(semesterId: String, classIds: List<String> = emptyList()): FfcsClassesResponse {
        val query = buildMap<String, String> {
            put("semesterId", semesterId.trim().uppercase())
            if (classIds.isNotEmpty()) {
                put("classIds", classIds.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.joinToString(","))
            }
        }
        val raw = AmazeClient.socialGetRaw("social/ffcs-match", query)
            ?: throw SocialApiError("server_error", "The server did not return rosters.")
        (raw as? JsonObject)?.let { obj -> if (obj.stringOrNull("success") == "false") throw obj.errorOf() }
        return json.decodeFromJsonElement<FfcsClassesResponse>(raw)
    }

    // -- People (D-006/D-013) -----------------------------------------------------------

    /** POST /api/social/people --- look up a person by AMZ handle. */
    suspend fun lookupPerson(handle: String): PersonResponse {
        val person = post(PersonResponse.serializer(), "social/people", mapOf("handle" to JsonPrimitive(handle.trim())), "a person")
        if (!person.success || person.person.handle.isEmpty()) {
            throw SocialApiError("unexpected_response", "The server did not return a person.")
        }
        return person
    }

    /** POST /api/social/pair/claim --- claim a pairing grant with a peer. */
    suspend fun claimPair(handle: String, visibility: SocialVisibility = SocialVisibility.FULL): ClaimResponse {
        val pairing = post(
            ClaimResponse.serializer(),
            "social/pair/claim",
            mapOf(
                "handle" to JsonPrimitive(handle.trim()),
                "visibility" to JsonPrimitive(visibility.name.lowercase())
            ),
            "a pairing"
        )
        if (!pairing.success || pairing.grantId.isEmpty()) {
            throw SocialApiError("unexpected_response", "The server did not return a pairing.")
        }
        return pairing
    }

    /** POST /api/social/grant/revoke --- revoke a grant (either participant). */
    suspend fun revokeGrant(grantId: String, secret: String): GrantRevokeResponse {
        return post(GrantRevokeResponse.serializer(), "social/grant/revoke", mapOf("grantId" to JsonPrimitive(grantId), "secret" to JsonPrimitive(secret)), "a revocation")
    }

    /** POST /api/social/grant/visibility --- change a grant's visibility. */
    suspend fun setGrantVisibility(grantId: String, visibility: SocialVisibility): GrantVisibilityResponse {
        return post(
            GrantVisibilityResponse.serializer(),
            "social/grant/visibility",
            mapOf("grantId" to JsonPrimitive(grantId), "visibility" to JsonPrimitive(visibility.name.lowercase())),
            "a visibility change"
        )
    }

    /** POST /api/social/timetable --- read a peer's timetable through a grant. */
    suspend fun readPeerTimetable(secret: String, handle: String, semester: String? = null): PeerTimetableResponse {
        val body = buildMap<String, JsonElement> {
            put("secret", JsonPrimitive(secret))
            put("handle", JsonPrimitive(handle.trim()))
            if (!semester.isNullOrBlank()) put("semester", JsonPrimitive(semester))
        }
        val timetable = post(PeerTimetableResponse.serializer(), "social/timetable", body, "a timetable")
        if (!timetable.success || timetable.identity.handle.isEmpty()) {
            throw SocialApiError("unexpected_response", "The server did not return a timetable.")
        }
        return timetable
    }

    /** GET /api/social/semester --- the current social semester, public. */
    suspend fun fetchCurrentSemester(): SemesterResponse {
        val raw = AmazeClient.socialGetRaw("social/semester")
            ?: throw SocialApiError("server_error", "The server did not return a semester.")
        return json.decodeFromJsonElement<SemesterResponse>(raw)
    }

    /** POST /api/social/identity/sync --- push our own derived state and pull peers + grants. */
    suspend fun identitySync(proposedSemesterId: String? = null): SocialSyncPayload {
        val body = if (proposedSemesterId.isNullOrBlank()) emptyMap() else mapOf("proposedSemesterId" to JsonPrimitive(proposedSemesterId))
        return post(SocialSyncPayload.serializer(), "social/identity/sync", body, "an identity")
    }
}

/** Machine-readable failure of a social call (D-006/D-013, port of web `SocialApiError`). */
class SocialApiError(
    /** The server's `error` code, e.g. `"handle_not_found"`. */
    val code: String,
    /** The server's human-readable `detail`, when it sent one. */
    detail: String? = null
) : Exception(detail ?: code) {
    /** UI hook: true when the caller should surface "Sign in with Amaze ID". */
    val needsAmazeIdSignIn: Boolean
        get() = code == "amazeid_signin_required" || code == "amazeid_session_expired"
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
    val classes: List<FfcsRosterClass> = emptyList()
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
)

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
