package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** How a message participates in the swarm. */
enum class BoardKind { REQUEST, REPORT, DONE, QUESTION, DONE_VERIFIED }

@Serializable
data class BoardMessage(
    val id: Long,
    val at: Instant,
    val from: String,
    val to: String,                 // member name, or "all" to broadcast
    val kind: BoardKind,
    val threadId: Long? = null,     // when non-null, this message belongs to the work thread [threadId]
    val subject: String,
    val body: String,
)

/**
 * Shared message board for the parity swarm. Every member (mother/Sentinel, workers/Echoes,
 * critics, challengers, auditors) posts here and reads here — this is how they talk to each
 * other and to the mother instead of silently editing files. The *user* watches the readable
 * digest in [boardMarkdown], which the mother regenerates into `docs/SWARM_BOARD.md`.
 */
object SwarmBoard {
    private const val KEY = "swarm_board_v2"
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<BoardMessage>>(loadAll())
    val messages: StateFlow<List<BoardMessage>> = _messages.asStateFlow()

    private fun loadAll(): List<BoardMessage> {
        val raw = SettingsManager.getString(KEY)
        if (raw.isEmpty()) return emptyList()
        return try { json.decodeFromString<List<BoardMessage>>(raw) } catch (_: Exception) { emptyList() }
    }

    private fun save() {
        SettingsManager.setString(KEY, json.encodeToString(_messages.value))
    }

    /** Post to the board and return the new message id. */
    fun post(from: String, to: String, kind: BoardKind, subject: String, body: String, threadId: Long? = null): Long {
        lateinit var created: BoardMessage
        _messages.update { list ->
            val id = (list.maxOfOrNull { it.id } ?: 0L) + 1L
            list + BoardMessage(id, Clock.System.now(), from, to, kind, threadId, subject, body).also { created = it }
        }
        save()
        return created.id
    }

    /** All messages addressed to [member] or broadcast ("all"), oldest-first. */
    fun messagesTo(member: String): List<BoardMessage> = _messages.value.filter {
        it.to == member || it.to == "all"
    }

    fun byId(id: Long): BoardMessage? = _messages.value.firstOrNull { it.id == id }

    fun threadIds(): Set<Long> = _messages.value.mapNotNull { it.threadId }.toSet()

    fun thread(threadId: Long): List<BoardMessage> = _messages.value.filter { it.threadId == threadId }.sortedBy { it.id }

    fun clear() {
        _messages.value = emptyList()
        save()
    }

    /** Human-readable full dump — the page the user watches. */
    fun boardMarkdown(): String = _messages.value.sortedBy { it.id }.joinToString("\n") { m ->
        buildString {
            append("**[${m.id} | ${m.at.toString().substringBefore('T')}] ${m.kind.name} → ${m.to}")
            m.threadId?.let { append(" (thread #$it)") }
            append(" — ${m.subject}**\n")
            append("  *from ${m.from}*\n")
            m.body.lines().take(20).forEach { append("  $it\n") }
        }
    }
}
