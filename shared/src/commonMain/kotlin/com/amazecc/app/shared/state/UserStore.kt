package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.security.Encryption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * The single source of truth for the student's identity. Every profile endpoint
 * syncs a clean, filtered fragment into [identity] via [merge]. Pages only ever
 * read [identity] — no per-page fallback chains, JSON deciphering or null-guarding.
 *
 * Persistence: the merged identity is stored encrypted under [SettingsManager.CACHE_USER_IDENTITY]
 * because it contains sensitive fields (aadhar, bank details, credential passwords).
 */
object UserStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _identity = MutableStateFlow(StudentIdentity())
    val identity: StateFlow<StudentIdentity> = _identity.asStateFlow()

    /** Dotted path → tier that last wrote it (session-scoped; defaults apply after restore). */
    private val sources = mutableMapOf<String, Int>()

    /**
     * Merges a filled [fragment] from [source] into the current identity and persists it.
     * Fragment values that are empty (null/blank/empty list/false) never erase existing data.
     */
    fun merge(fragment: StudentIdentity, source: IdentitySource) {
        val merged = mergeIdentity(_identity.value, fragment, source.order, sources)
        if (merged == _identity.value) return
        _identity.value = merged
        persist()
    }

    /** Restores the persisted identity (encrypted cache). Source bookkeeping resets to defaults. */
    fun loadFromCache() {
        val raw = SettingsManager.getNullableString(SettingsManager.CACHE_USER_IDENTITY) ?: return
        val decoded = runCatching { Encryption.decryptOrPlain(raw) }.getOrNull() ?: return
        val restored = runCatching { json.decodeFromString<StudentIdentity>(decoded) }.getOrNull() ?: return
        sources.clear()
        _identity.value = restored
    }

    fun persistNow() = persist()

    /** Clears the in-memory identity (logout). Cache is wiped by the caller's global cache clear. */
    fun clear() {
        sources.clear()
        _identity.value = StudentIdentity()
    }

    private fun persist() {
        val encoded = runCatching { json.encodeToString(StudentIdentity.serializer(), _identity.value) }.getOrNull()
            ?: return
        SettingsManager.setString(SettingsManager.CACHE_USER_IDENTITY, Encryption.encryptOrPlain(encoded))
    }
}
