package com.amazecc.app.shared.state

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-context tag attached by [SyncEngine.networkScope]. Every HTTP request made inside that
 * scope is attributed to the module/label in the gate log. Requests made outside a scope are tagged
 * "direct" — they are still logged and cancellable, but they are visible as bypasses.
 */
class NetworkScope(val module: SyncModule?, val label: String) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<NetworkScope>
}

data class NetworkLogLine(
    val id: Long,
    val at: Instant,
    val level: NetworkLogLevel,
    val transport: String,
    val method: String,
    val url: String,
    val module: SyncModule?,
    val label: String,
    val detail: String,
    val durationMs: Long? = null,
)

enum class NetworkLogLevel { INFO, WARN, ERROR }

data class ActiveNetworkCall(
    val id: Long,
    val startedAt: Instant,
    val transport: String,
    val method: String,
    val url: String,
    val module: SyncModule?,
    val label: String,
    val job: Job?,
)

data class ModuleNetworkActivity(
    val requests: Int = 0,
    val inFlight: Int = 0,
    val errors: Int = 0,
    val lastUrl: String? = null,
)

/**
 * Single choke point for every network operation in the app.
 *
 * The Ktor plugin below is installed on **all** live HttpClient instances (AmazeClient,
 * VtopLms and VtopEventHub). The WebView transport calls [run] from its
 * platform implementations. This gives one place that:
 *  - can refuse new work ([close]) when a sweep is cancelled or the user logs out,
 *  - knows every in-flight request ([active]) and can cancel its owning job,
 *  - emits a structured, timestamped [log] with URL, module, status and duration.
 *
 * Nothing here decides *success* of a module — that remains the job of the modules themselves.
 * The gate only reports what actually went over the wire.
 */
object NetworkGate {
    private val _active = MutableStateFlow<List<ActiveNetworkCall>>(emptyList())
    val active: StateFlow<List<ActiveNetworkCall>> = _active.asStateFlow()

    private val _log = MutableStateFlow<List<NetworkLogLine>>(emptyList())
    val log: StateFlow<List<NetworkLogLine>> = _log.asStateFlow()

    private data class Totals(var requests: Int = 0, var errors: Int = 0)

    private val _totals = MutableStateFlow<Map<SyncModule?, Totals>>(emptyMap())

    /**
     * Per-module request accounting for the Module tab. Derived from the gate's own traffic, so a
     * module shows requests made by screens/bridges too, not only by sync sweeps. The extra `null`
     * bucket is "unattributed" (health checks, GitHub update, faculty/QBank endpoints) — nothing
     * goes missing from the accounting.
     */
    val moduleActivity: Flow<Map<SyncModule?, ModuleNetworkActivity>> = combine(_active, _log, _totals) { active, log, totals ->
        (SyncModule.entries.toList() + null).associateWith { module ->
            val total = totals[module]
            ModuleNetworkActivity(
                requests = total?.requests ?: 0,
                inFlight = active.count { it.module == module },
                errors = total?.errors ?: 0,
                lastUrl = log.lastOrNull { it.module == module && it.detail.startsWith("→ ") }?.url,
            )
        }
    }

    @Volatile
    private var open = true

    @Volatile
    private var closedReason = "not closed"

    fun isOpen(): Boolean = open

    /** Called by SyncEngine when a sweep starts / ends. */
    fun openGate() {
        open = true
        closedReason = "not closed"
    }

    /** Refuse all new requests and cancel everything already in flight. */
    fun closeGate(reason: String) {
        open = false
        closedReason = reason
        val calls = _active.value
        calls.forEach { it.job?.cancel(CancellationException(reason)) }
        _active.value = emptyList()
        record(
            level = NetworkLogLevel.WARN,
            transport = "gate",
            method = "CLOSE",
            url = "",
            module = null,
            label = "NetworkGate",
            detail = "Circuit closed: $reason (${calls.size} in-flight request(s) cancelled)",
        )
    }

    fun begin(
        transport: String,
        method: String,
        url: String,
        module: SyncModule?,
        label: String,
        job: Job?,
    ): Long {
        var id = 0L
        _active.update { list ->
            id = (list.firstOrNull()?.id ?: 0L) + 1L
            list + ActiveNetworkCall(
                id = id,
                startedAt = Clock.System.now(),
                transport = transport,
                method = method,
                url = url,
                module = module,
                label = label,
                job = job,
            )
        }
        _totals.update { map ->
            val current = map[module] ?: Totals()
            map + (module to Totals(current.requests + 1, current.errors))
        }
        record(
            level = NetworkLogLevel.INFO,
            transport = transport,
            method = method,
            url = url,
            module = module,
            label = label,
            detail = "→ $method ${url.ifBlank { "(no url)" }}",
        )
        return id
    }

    fun finish(id: Long, detail: String, startedAt: Long?, level: NetworkLogLevel = NetworkLogLevel.INFO) {
        val call = _active.value.firstOrNull { it.id == id }
        _active.update { list -> list.filterNot { it.id == id } }
        val duration = startedAt?.let { Clock.System.now().toEpochMilliseconds() - it }
        if (call == null) return
        if (level == NetworkLogLevel.ERROR) {
            _totals.update { map ->
                val current = map[call.module] ?: Totals()
                map + (call.module to Totals(current.requests, current.errors + 1))
            }
        }
        record(
            level = level,
            transport = call.transport,
            method = call.method,
            url = call.url,
            module = call.module,
            label = call.label,
            detail = detail,
            durationMs = duration,
        )
    }

    private fun record(
        level: NetworkLogLevel,
        transport: String,
        method: String,
        url: String,
        module: SyncModule?,
        label: String,
        detail: String,
        durationMs: Long? = null,
    ) {
        _log.update { list ->
            val id = (list.firstOrNull()?.id ?: 0L) + 1L
            (list + NetworkLogLine(
                id = id,
                at = Clock.System.now(),
                level = level,
                transport = transport,
                method = method,
                url = url,
                module = module,
                label = label,
                detail = detail,
                durationMs = durationMs,
            )).takeLast(400)
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    /** Wrapper for transports that are not HTTP (currently the hidden VTOP WebView). */
    suspend fun <T> run(
        transport: String = "webview",
        method: String,
        url: String,
        label: String,
        module: SyncModule? = null,
        block: suspend () -> T,
    ): T {
        if (!open) throw CancellationException("Network gate closed: $closedReason")
        val scope = currentCoroutineContext()[NetworkScope]
        val effectiveModule = module ?: scope?.module ?: inferModule(url)
        val effectiveLabel = scope?.label?.let { "$it → $label" } ?: label
        val start = Clock.System.now().toEpochMilliseconds()
        val id = begin(transport, method, url, effectiveModule, effectiveLabel, currentCoroutineContext()[Job])
        return try {
            val result = block()
            finish(id, "OK", start)
            result
        } catch (e: CancellationException) {
            finish(id, "CANCELLED: ${e.message ?: "no reason"}", start, NetworkLogLevel.WARN)
            throw e
        } catch (e: Throwable) {
            finish(id, "${e::class.simpleName}: ${e.message}", start, NetworkLogLevel.ERROR)
            throw e
        }
    }

    /** Human-readable lines for the sync console. */
    fun recentLines(limit: Int = 120): List<String> = _log.value.takeLast(limit).map { line ->
        val who = line.module?.displayName ?: line.label
        val dur = line.durationMs?.let { " ${it}ms" } ?: ""
        "${line.level.name.padEnd(5)} ${who}: ${line.detail}$dur"
    }
}

/**
 * Endpoint → module fallback for calls made outside a [NetworkScope] (screens, bridges, widgets).
 * An explicit scope always wins; this only makes the unscoped traffic attributable.
 */
internal fun inferModule(url: String): SyncModule? {
    val u = url.lowercase()
    return when {
        "/api/health" in u || "github" in u -> null
        "/api/remote/whoami" in u || "/api/student" in u || "/api/profile-images" in u ||
            "/api/bank-info" in u || "/api/apaar" in u || "/api/dayboarder" in u ||
            "/api/ept-schedule" in u || "/api/registration-schedule" in u ||
            "/api/university-day" in u || "/api/credentials" in u -> SyncModule.STUDENT_PROFILE
        "/api/attendance" in u || "/api/academic" in u -> SyncModule.ATTENDANCE
        "/api/marks" in u -> SyncModule.MARKS
        "/api/all-grades" in u || "/api/grades" in u -> SyncModule.GRADES
        "/api/timetable" in u -> SyncModule.TIMETABLE
        "/api/curriculum" in u || "/api/syllabus" in u -> SyncModule.CURRICULUM
        "/api/hostel" in u -> SyncModule.HOSTEL_DETAILS
        "/api/schedule" in u || "/api/exam" in u -> SyncModule.EXAM_SCHEDULE
        "/api/calendar" in u -> SyncModule.CALENDAR
        "/api/payment" in u || "/api/wallet" in u || "/api/receipt" in u -> SyncModule.PAYMENTS
        "/api/library" in u || "/koha" in u -> SyncModule.LIBRARY
        "/api/transport" in u -> SyncModule.TRANSPORT
        // D-192: the public cache route is /transport/buses; Hono/legacy use /api/buses.
        "/api/buses" in u || "/transport/buses" in u -> SyncModule.BUSES
        "/api/lms-data" in u -> SyncModule.LMS
        "/api/events" in u -> SyncModule.EVENTS
        "/api/clubs" in u -> SyncModule.CLUBS
        "/api/qcm" in u -> SyncModule.QCM_VIEW
        "/api/circular" in u || "/api/remote/circular" in u -> SyncModule.CIRCULARS
        "/api/free-classroom" in u -> SyncModule.FREE_CLASSROOMS
        "/api/document" in u || "/api/bonafide" in u || "/api/e-transcript" in u ||
            "/api/additional-learning" in u -> SyncModule.DOCUMENTS
        "/api/od" in u -> SyncModule.OD_DETAILS
        "/api/feedback" in u -> SyncModule.FEEDBACK_STATUS
        else -> null
    }
}

private val GateStart = AttributeKey<Long>("AmazeGateStart")
private val GateId = AttributeKey<Long>("AmazeGateId")

/**
 * Install once on every HttpClient in the process. Ktor's request hooks run in the caller's
 * coroutine context, so [NetworkScope] set by [SyncEngine.networkScope] is visible here.
 */
val AmazeNetworkGatePlugin = createClientPlugin("AmazeNetworkGate") {
    onRequest { request, _ ->
        if (!NetworkGate.isOpen()) {
            throw CancellationException("Network gate closed by sync engine")
        }
        val url = request.url.toString()
        val method = request.method.value
        val scope = currentCoroutineContext()[NetworkScope]
        // Explicit sync scope wins; otherwise infer from the endpoint so screen-originated
        // requests still land in the right module bucket of the Module tab.
        val module = scope?.module ?: inferModule(url)
        val label = scope?.label ?: "direct"
        val id = NetworkGate.begin("http", method, url, module, label, currentCoroutineContext()[Job])
        request.attributes.put(GateStart, Clock.System.now().toEpochMilliseconds())
        request.attributes.put(GateId, id)
    }

    onResponse { response ->
        val attrs = response.call.request.attributes
        val id = attrs.getOrNull(GateId) ?: return@onResponse
        val start = attrs.getOrNull(GateStart)
        NetworkGate.finish(id, "HTTP ${response.status.value}", start)
    }

    // Ktor 2.3's createClientPlugin has no `onError` hook; wrap the request pipeline instead so
    // transport failures still close the gate entry without changing the Ktor version.
    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
        val request = context
        try {
            proceed()
        } catch (t: Throwable) {
            val id = request.attributes.getOrNull(GateId)
            if (id != null) {
                val start = request.attributes.getOrNull(GateStart)
                NetworkGate.finish(
                    id,
                    "ERROR ${t::class.simpleName}: ${t.message}",
                    start,
                    NetworkLogLevel.ERROR
                )
            }
            throw t
        }
    }
}
