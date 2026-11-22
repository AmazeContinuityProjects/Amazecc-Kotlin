package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Durable LIFO stack of visited screens — the Kotlin counterpart of the web app's
 * browser-history screen stack (`src/components/custom/dashboard/useDashboardScreenHistory.ts`).
 *
 * The web survives reload because the history entries live in the browser; on Android,
 * process death wipes an in-memory list, so this stack is "via shared files": it is
 * persisted through the shared settings store, and the newest entry is restored on start.
 *
 * Invariant: [entries] is ordered oldest → newest, and the newest entry is the currently
 * visible screen. `AppState.navigateTo` pushes the destination, so back lands on the
 * previous entry — same LIFO order as the web's `popstate`-driven `goBack`.
 */
object AppBackStack {
    @Serializable
    data class Entry(
        /** [Screen.name] of the visited screen. */
        val screen: String,
        /** Sub-tab key when the screen carries one (e.g. the attendance initial view). */
        val subTab: String? = null,
        val courseCode: String? = null,
        val semesterId: String? = null
    )

    private const val KEY = "screen_history_stack"
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(readFromStorage())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun isEmpty(): Boolean = _entries.value.isEmpty()
    fun size(): Int = _entries.value.size
    fun peek(): Entry? = _entries.value.lastOrNull()

    fun push(entry: Entry) {
        val stack = _entries.value
        if (stack.lastOrNull() == entry) return
        _entries.value = stack + entry
        persist()
    }

    fun replaceTop(entry: Entry) {
        val stack = _entries.value
        if (stack.lastOrNull() == entry) return
        _entries.value = stack.dropLast(1) + entry
        persist()
    }

    fun pop(): Entry? {
        if (_entries.value.isEmpty()) return null
        val entry = _entries.value.last()
        _entries.value = _entries.value.dropLast(1)
        persist()
        return entry
    }

    fun clear() {
        if (_entries.value.isNotEmpty()) {
            _entries.value = emptyList()
            persist()
        }
    }

    private fun readFromStorage(): List<Entry> = try {
        val raw = SettingsManager.getString(KEY, "")
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist() {
        SettingsManager.setString(KEY, json.encodeToString(_entries.value))
    }
}
