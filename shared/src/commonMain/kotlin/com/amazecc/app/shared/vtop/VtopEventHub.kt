package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.EventHubEvent
import com.amazecc.app.shared.model.EventHubRegisteredEvent
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `eventhubcc.vit.ac.in` — the one non-VTOP host that is reachable and worth porting today.
 *
 * Deliberately **not** part of [VtopDataSource]. The WebView that serves VTOP cannot fetch other
 * origins: a cross-origin XHR would be rejected by CORS before the response body became readable.
 * So Ktor does the network and the WebView supplies only a DOM, which is the part a plain HTTP
 * client genuinely cannot do.
 *
 * That split also leaves TLS to the platform rather than re-implementing the
 * `rejectUnauthorized: false` agents the server used; on Android the certificate is covered by
 * `res/xml/network_security_config.xml`.
 */
object VtopEventHub {

    private const val EVENTHUB = "https://eventhubcc.vit.ac.in"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient {
        // AmazeCC-API's LMSClient sets no timeout at all, so axios runs with `timeout: 0`.
        // Not reproduced here.
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 25_000
        }
    }

    // ── EVENTS — public, no auth ────────────────────────────────────────────

    suspend fun fetchEvents(): List<EventHubEvent>? = withContext(Dispatchers.Default) {
        val html = runCatching {
            http.get("$EVENTHUB/EventHub/") { header(HttpHeaders.UserAgent, ua()) }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.bodyAsText()
            ?: return@withContext null

        parseWithEngine(VtopScripts.parseEventHubEvents(html), EventHubEvent.serializer(), "events")
    }

    // ── EVENTS_PROFILE — JSESSIONID + cookiesession1 ────────────────────────

    /**
     * EventHub authenticates on the form post itself and returns both `JSESSIONID` and
     * `cookiesession1` in `Set-Cookie`. The profile page needs both, so the whole cookie header is
     * carried forward rather than just the session id.
     *
     * If a cached session has expired the login page comes back instead of the profile; that is
     * retried once with fresh credentials before giving up.
     */
    suspend fun fetchEventsProfile(username: String, password: String): EventHubRegisteredEventsRes? =
        withContext(Dispatchers.Default) {
            val cookie = login(username, password) ?: return@withContext null

            var html = getPage("/EventHub/profile", cookie)
            if (html != null && looksLikeLoginPage(html) && username.isNotBlank()) {
                login(username, password)?.let { fresh ->
                    getPage("/EventHub/profile", fresh)?.let { html = it }
                }
            }
            if (html == null) return@withContext null
            if (looksLikeLoginPage(html)) {
                return@withContext EventHubRegisteredEventsRes(
                    success = false,
                    message = "session_expired",
                    error = "EventHub session expired"
                )
            }

            val events = parseWithEngine(
                VtopScripts.parseEventHubProfile(html), EventHubRegisteredEvent.serializer(), "events"
            )
            EventHubRegisteredEventsRes(success = true, events = events.orEmpty())
        }

    /** Returns the full cookie header, or null when the credentials were rejected. */
    private suspend fun login(username: String, password: String): String? {
        val res = runCatching {
            http.post("$EVENTHUB/EventHub/mainDashboard") {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(
                    Parameters.build {
                        append("username", username)
                        append("password", password)
                        append("validateVitian", "1")
                    }
                )
            }
        }.getOrNull() ?: return null

        val cookies = res.cookieHeader()
        // No JSESSIONID in Set-Cookie means the login itself failed.
        if (!cookies.split("; ").any { it.startsWith("JSESSIONID") }) return null
        return cookies
    }

    private suspend fun getPage(path: String, cookie: String): String? =
        runCatching {
            http.get("$EVENTHUB$path") {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
            }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.bodyAsText()

    /** `src/lib/eventHubAuth.ts`: a login page still advertises the dashboard form. */
    private fun looksLikeLoginPage(html: String): Boolean =
        Regex("""action=["']/EventHub/mainDashboard["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun ua(): String = UserAgentPool.current()

    /** `Set-Cookie` values, `name=value` only, joined for a `Cookie` header. */
    private fun io.ktor.client.statement.HttpResponse.cookieHeader(): String =
        headers.getAll(HttpHeaders.SetCookie)
            ?.mapNotNull { it.substringBefore(";").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .joinToString("; ")

    /** Runs [script] on the engine and decodes the array at [key], or null if the parse failed. */
    private suspend fun <T> parseWithEngine(script: String, serializer: KSerializer<T>, key: String): List<T>? {
        val raw = runCatching { Vtop.engineInstance().evaluate(script) }.getOrNull() ?: return null
        val obj = (try { json.parseToJsonElement(raw.ifBlank { "{}" }) } catch (_: Exception) { null })
            as? JsonObject ?: return null
        if ((obj["ok"] as? JsonPrimitive)?.content != "true") return null
        val arr = obj[key] as? JsonArray ?: return null
        return try {
            json.decodeFromJsonElement(ListSerializer(serializer), arr)
        } catch (_: Exception) {
            null
        }
    }
}
