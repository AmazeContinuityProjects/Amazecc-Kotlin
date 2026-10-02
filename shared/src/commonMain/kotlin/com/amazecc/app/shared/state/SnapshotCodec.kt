package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.VtopIngestor
import kotlinx.serialization.json.Json

/**
 * Reads and writes the one persisted snapshot, across all three of its schemas.
 *
 * The stored shape is [DomainSnapshot] (`schemaVersion` 3). Two older shapes still exist on disk and
 * are upgraded on read:
 *
 * | version | shape | marker | how it is recognised |
 * |---|---|---|---|
 * | 3 | `DomainSnapshot` | `"academics"` | current writer |
 * | 2 | `AppDataSnapshot` | `"academic"` | one string shorter |
 * | 1 | `LegacyAppDataSnapshot` | neither | per-module mirror fields |
 *
 * Detection is by marker rather than by reading `schemaVersion`, because v1 never wrote one and
 * because the markers are unambiguous: `"academic"` cannot match `"academics"` - the closing quote
 * has to follow the `s`.
 *
 * Ordering matters and is checked in this order (v3, then v2, then v1). Getting it backwards would
 * try to decode a domain blob as a legacy snapshot and, since every field there is defaulted, could
 * "succeed" into an empty snapshot - a silent data wipe rather than a crash.
 */
object SnapshotCodec {

    private const val MARKER_V3 = "\"academics\""
    private const val MARKER_V2 = "\"academic\""

    /**
     * Decoder config.
     *
     * `encodeDefaults = false` on the *encode* path would omit any field still at its default -
     * including `academics` itself when the snapshot is empty. That writes a v3 blob carrying no
     * v3 marker, which then reads back as v1 and takes the legacy migration path. Harmless only
     * while that migrator returns empty for empty input, which is exactly the kind of "harmless"
     * that breaks later. So the encoder always writes its markers.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val encoder = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Which schema a persisted blob is in. */
    enum class Schema { V3_DOMAIN, V2_APP_DATA, V1_LEGACY }

    fun detect(encoded: String): Schema = when {
        MARKER_V3 in encoded -> Schema.V3_DOMAIN
        MARKER_V2 in encoded -> Schema.V2_APP_DATA
        else -> Schema.V1_LEGACY
    }

    /**
     * Decodes any supported schema into the in-memory shape.
     *
     * Returns null only when the blob is unreadable, which callers treat as "no snapshot" and which
     * must never be treated as "empty snapshot" - see [AppDataStore.restore].
     *
     * @param selectedSemesterId the user's own choice, applied on the way in so the domain snapshot
     *   records it instead of re-guessing from the semester ids.
     */
    fun decode(encoded: String, selectedSemesterId: String? = null): AppDataSnapshot? =
        when (detect(encoded)) {
            Schema.V3_DOMAIN -> runCatching {
                VtopIngestor.toLegacy(
                    json.decodeFromString(DomainSnapshot.serializer(), encoded)
                )
            }.getOrNull()

            Schema.V2_APP_DATA -> runCatching {
                json.decodeFromString(AppDataSnapshot.serializer(), encoded)
            }.getOrNull()

            Schema.V1_LEGACY -> runCatching {
                SnapshotMigrator.toV2(
                    json.decodeFromString(LegacyAppDataSnapshot.serializer(), encoded)
                )
            }.getOrNull()
        }

    /** Encodes the in-memory shape as the current schema. */
    fun encode(snapshot: AppDataSnapshot, selectedSemesterId: String? = null): String =
        encoder.encodeToString(
            DomainSnapshot.serializer(),
            VtopIngestor.fromLegacy(snapshot, selectedSemesterId),
        )

    /** The domain shape as persisted, for callers that want to read the canonical form directly. */
    fun decodeDomain(encoded: String): DomainSnapshot? = runCatching {
        json.decodeFromString(DomainSnapshot.serializer(), encoded)
    }.getOrNull()
}
