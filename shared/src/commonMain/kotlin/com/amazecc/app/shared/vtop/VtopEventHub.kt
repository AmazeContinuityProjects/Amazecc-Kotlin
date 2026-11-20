package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.state.AmazeNetworkGatePlugin
import com.amazecc.app.shared.model.EventHubEvent
import com.amazecc.app.shared.model.EventHubPreview
import com.amazecc.app.shared.model.EventHubRegisterRes
import com.amazecc.app.shared.model.EventHubRegisteredEvent
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.repository.SettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
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
 *
 * No endpoint uses a captcha. Every authenticated call logs in on the form post itself
 * (`username`, `password`, `validateVitian=1`) and reuses the returned `JSESSIONID` cookie. Because
 * EventHub answers a rejected login with HTTP 200 + Set-Cookie + the login form, [login] checks the
 * body rather than trusting the cookie. Any caller-supplied URL (payment redirect, payment form
 * action, download target) is pinned to the Event Hub host over HTTPS by [resolveEventHubUrl].
 */
object VtopEventHub {

    private const val EVENTHUB_HOST = "eventhubcc.vit.ac.in"
    private const val EVENTHUB = "https://$EVENTHUB_HOST"
    private const val BASE_URL = "$EVENTHUB/EventHub"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient {
        install(AmazeNetworkGatePlugin)
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
            http.get("$BASE_URL/") { header(HttpHeaders.UserAgent, ua()) }
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

    // ── EVENT PREVIEW ───────────────────────────────────────────────────────

    /**
     * `POST /EventHub/eventPreview` — the detail page behind a card. Credentials are read from
     * [SettingsManager] so the caller only supplies the event id. The poster is a public URL built
     * from that id (`AmazeCC-API /api/events/preview`), not a proxied image.
     *
     * Returns null on a login page (expired session) or any parse/network failure; the caller
     * keeps its previous state rather than showing stale/partial data as a success.
     */
    suspend fun fetchEventPreview(eid: String): EventHubPreview? = withContext(Dispatchers.Default) {
        if (eid.isBlank()) return@withContext null
        val creds = SettingsManager.getCredentials() ?: return@withContext null
        val cookie = login(creds.first, creds.second) ?: return@withContext null

        val html = postFormPage(
            "/EventHub/eventPreview", cookie,
            Parameters.build { append("eid", eid) }
        ) ?: return@withContext null
        if (looksLikeLoginPage(html)) return@withContext null

        val parsed = evaluateObject(VtopScripts.parseEventHubPreview(html)) ?: return@withContext null
        if (isNotOk(parsed)) return@withContext null

        val description = (parsed["description"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val meta = (parsed["metaDetails"] as? JsonObject)?.let { obj ->
            obj.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
        }
        val imageSrc = "$BASE_URL/image/?id=${eid.encodeURLParameter()}"
        EventHubPreview(eid = eid, imageSrc = imageSrc, description = description, metaDetails = meta)
    }

    // ── REGISTRATION ────────────────────────────────────────────────────────

    /**
     * `POST /EventHub/registerEvent`. Two-step flow, mirroring AmazeCC-API
     * `src/app/api/events/register/route.ts`:
     *  1. POST `eventPreview` with `eid` to collect every hidden form token and to detect an
     *     already-registered / expired-session state (the register button disappears).
     *  2. Replay those hidden inputs plus `id=<eid>` to `registerEvent` with Origin/Referer.
     *
     * The 302 targets are pinned to Event Hub before being fetched or returned. There is no
     * unregister endpoint on Event Hub, so none is exposed here.
     */
    suspend fun registerForEvent(eid: String): EventHubRegisterRes? = withContext(Dispatchers.Default) {
        if (eid.isBlank()) return@withContext null
        val creds = SettingsManager.getCredentials() ?: return@withContext null
        val cookie = login(creds.first, creds.second) ?: return@withContext EventHubRegisterRes(
            status = "invalid_credentials", message = "Event Hub authentication failed."
        )

        val previewHtml = postFormPage(
            "/EventHub/eventPreview", cookie,
            Parameters.build { append("eid", eid) }
        ) ?: return@withContext null

        if (looksLikeLoginPage(previewHtml)) {
            return@withContext EventHubRegisterRes(status = "session_expired", message = "Event Hub session expired.")
        }

        val form = evaluateObject(VtopScripts.parseEventHubRegistrationForm(previewHtml))
            ?: return@withContext null
        if (isNotOk(form)) return@withContext null
        if (primitive(form, "sessionExpired") == "true") {
            return@withContext EventHubRegisterRes(status = "session_expired", message = "Event Hub session expired.")
        }
        if (primitive(form, "alreadyRegistered") == "true") {
            return@withContext EventHubRegisterRes(
                status = "already_registered",
                message = "You have already registered for this event, or registrations are closed."
            )
        }
        val hidden = (form["hiddenFields"] as? JsonObject)?.let { obj ->
            obj.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
        }.orEmpty()

        val body = Parameters.build {
            hidden.forEach { (k, v) -> append(k, v) }
            append("id", eid)
        }
        val res = runCatching {
            http.post("$BASE_URL/registerEvent") {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                header("Origin", EVENTHUB)
                header("Referer", "$BASE_URL/eventPreview")
                setBody(body)
            }
        }.getOrNull() ?: return@withContext null

        classifyRegisterResponse(res, cookie)
    }

    /**
     * `POST /EventHub/paynow` equivalent: build a self-submitting login page that authenticates
     * the user into Event Hub and lands them on the allow-listed payment/T&C URL. No server-side
     * HTML fetch is involved — the browser in which the returned HTML is loaded carries the
     * cookie, which is why the credentials are embedded in a hidden form.
     */
    suspend fun buildPaymentLoginHtml(url: String): EventHubRegisterRes? = withContext(Dispatchers.Default) {
        val pinned = resolveEventHubUrl(url) ?: return@withContext null
        val creds = SettingsManager.getCredentials() ?: return@withContext null
        EventHubRegisterRes(
            status = "payment_form",
            url = pinned,
            html = paymentLoginHtml(creds.first, creds.second)
        )
    }

    /** Streams an allow-listed Event Hub file (receipt/certificate) with an authenticated session. */
    suspend fun downloadEventFile(url: String): ByteArray? = withContext(Dispatchers.Default) {
        val pinned = resolveEventHubUrl(url) ?: return@withContext null
        val creds = SettingsManager.getCredentials() ?: return@withContext null
        val cookie = login(creds.first, creds.second) ?: return@withContext null
        runCatching {
            http.get(pinned) {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
            }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.body<ByteArray>()
    }

    // ── AUTH — no captcha ───────────────────────────────────────────────────

    /** Returns the full cookie header, or null when the credentials were rejected. */
    private suspend fun login(username: String, password: String): String? {
        if (username.isBlank() || password.isBlank()) return null
        val res = runCatching {
            http.post("$BASE_URL/mainDashboard") {
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

        // EventHub returns HTTP 200 + Set-Cookie even for REJECTED credentials — a failed login
        // simply re-renders the login form. The cookie alone proves nothing; the body decides.
        val body = runCatching { res.bodyAsText() }.getOrNull() ?: return null
        if (looksLikeLoginPage(body)) return null
        return cookies
    }

    private suspend fun getPage(path: String, cookie: String): String? =
        runCatching {
            http.get("$EVENTHUB$path") {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
            }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.bodyAsText()

    private suspend fun postFormPage(path: String, cookie: String, body: Parameters): String? =
        runCatching {
            http.post("$EVENTHUB$path") {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(body)
            }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.bodyAsText()

    /** `src/lib/eventHubAuth.ts`: a login page still advertises the dashboard form. */
    internal fun looksLikeLoginPage(html: String): Boolean =
        Regex("""action=["']/EventHub/mainDashboard["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)

    /**
     * SSRF guard for externally supplied Event Hub URLs (redirect target, payment form action,
     * download target). Absolute HTTPS URLs on `eventhubcc.vit.ac.in` pass; relative paths are
     * resolved against that origin. Anything else — another host, a subdomain-suffix trick, plain
     * HTTP, userinfo, a port — is rejected. The char after the host must be `/`, `?`, `#` or end.
     */
    internal fun resolveEventHubUrl(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val raw = candidate.trim()
        val absolute = when {
            raw.startsWith("/") -> "$EVENTHUB$raw"
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
            else -> "$BASE_URL/$raw"
        }
        val origin = EVENTHUB
        if (!absolute.startsWith(origin, ignoreCase = true)) return null
        val tail = absolute.drop(origin.length)
        if (tail.isNotEmpty() && tail[0] !in "/?#") return null
        if (tail.any { it == '\\' || it == '@' }) return null
        return absolute
    }

    // ── REGISTRATION OUTCOMES ───────────────────────────────────────────────

    private suspend fun classifyRegisterResponse(res: HttpResponse, cookie: String): EventHubRegisterRes {
        return when (res.status.value) {
            302, 303 -> {
                val location = res.headers[HttpHeaders.Location]
                val pinned = resolveEventHubUrl(location) ?: return EventHubRegisterRes(
                    status = "error", message = "Unexpected payment redirect target"
                )
                val target = location.orEmpty()
                when {
                    target.contains("showPaymentTC", ignoreCase = true) -> followPaymentTerms(pinned, cookie)
                    target.contains("payment", ignoreCase = true) ||
                        target.contains("billdesk", ignoreCase = true) ||
                        target.contains("paytm", ignoreCase = true) ||
                        target.contains("razorpay", ignoreCase = true) ->
                        EventHubRegisterRes(status = "payment_required", url = pinned)
                    target.contains("success", ignoreCase = true) ||
                        target.contains("goBackToEventList", ignoreCase = true) ->
                        EventHubRegisterRes(status = "success", message = "Successfully registered!")
                    else -> EventHubRegisterRes(status = "redirect", url = pinned)
                }
            }
            200 -> {
                val body = runCatching { res.bodyAsText() }.getOrDefault("")
                val lower = body.lowercase()
                val externalForm = Regex(
                    """<form[^>]+action=["']https?://(?!eventhubcc\.vit\.ac\.in)""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(body)
                val looksLikePayment = externalForm || listOf(
                    "billdesk", "paytm", "razorpay", "payment", "name=\"msg\"", "name=\"merchant_id\""
                ).any { lower.contains(it) }
                if (looksLikePayment) EventHubRegisterRes(status = "payment_form", html = injectBase(body))
                else EventHubRegisterRes(status = "success", message = "Successfully registered!")
            }
            else -> EventHubRegisterRes(
                status = "error", message = "Registration failed with status ${res.status.value}"
            )
        }
    }

    /** Follows the `showPaymentTC` redirect, agrees to T&C and submits the payment form. */
    private suspend fun followPaymentTerms(tcUrl: String, cookie: String): EventHubRegisterRes {
        val tcHtml = runCatching {
            http.get(tcUrl) {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
            }
        }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }?.bodyAsText()
            ?: return EventHubRegisterRes(status = "payment_required", url = tcUrl)

        val parsed = evaluateObject(VtopScripts.parseEventHubPaymentForm(tcHtml))
            ?: return EventHubRegisterRes(status = "payment_required", url = tcUrl)
        if (isNotOk(parsed)) return EventHubRegisterRes(status = "payment_required", url = tcUrl)
        val action = resolveEventHubUrl(primitive(parsed, "action"))
            ?: return EventHubRegisterRes(status = "error", message = "Unexpected payment form target")
        if (!action.contains("doPtmPayment", ignoreCase = true)) {
            return EventHubRegisterRes(status = "payment_required", url = action)
        }
        val hidden = (parsed["hiddenFields"] as? JsonObject)?.let { obj ->
            obj.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
        }.orEmpty()
        val payBody = Parameters.build {
            hidden.forEach { (k, v) -> append(k, v) }
            if (!hidden.containsKey("checkbox")) append("checkbox", "on")
        }
        val finalRes = runCatching {
            http.post(action) {
                header(HttpHeaders.UserAgent, ua())
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                header("Origin", EVENTHUB)
                header("Referer", tcUrl)
                setBody(payBody)
            }
        }.getOrNull() ?: return EventHubRegisterRes(status = "payment_required", url = action)

        if (finalRes.status == HttpStatusCode.OK) {
            val html = runCatching { finalRes.bodyAsText() }.getOrDefault("")
            return EventHubRegisterRes(status = "payment_form", url = action, html = injectBase(html))
        }
        return EventHubRegisterRes(status = "payment_required", url = action)
    }

    /** Relative forms in a proxied payment page must submit to Event Hub, not to localhost. */
    private fun injectBase(html: String): String = when {
        html.contains("<base", ignoreCase = true) -> html
        html.contains("<head>", ignoreCase = true) ->
            html.replace("<head>", "<head><base href=\"$EVENTHUB/\">", ignoreCase = true)
        else -> "<base href=\"$EVENTHUB/\">\n$html"
    }

    private fun paymentLoginHtml(username: String, password: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Redirecting to Event Hub...</title></head>
        <body>
            <form id="loginForm" action="$BASE_URL/mainDashboard" method="POST">
                <input type="hidden" name="username" value="${escapeHtmlAttr(username)}" />
                <input type="hidden" name="password" value="${escapeHtmlAttr(password)}" />
                <input type="hidden" name="validateVitian" value="1" />
            </form>
            <script>document.getElementById("loginForm").submit();</script>
        </body>
        </html>
        """.trimIndent()

    private fun escapeHtmlAttr(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun ua(): String = UserAgentPool.current()

    /** `Set-Cookie` values, `name=value` only, joined for a `Cookie` header. */
    private fun HttpResponse.cookieHeader(): String =
        headers.getAll(HttpHeaders.SetCookie)
            ?.mapNotNull { it.substringBefore(";").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .joinToString("; ")

    private fun isNotOk(obj: JsonObject): Boolean = (obj["ok"] as? JsonPrimitive)?.content != "true"

    private fun primitive(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.content

    /** Runs [script] on the engine and decodes the object it returns, or null if parsing failed. */
    private suspend fun evaluateObject(script: String): JsonObject? {
        val raw = runCatching { Vtop.engineInstance().evaluate(script) }.getOrNull() ?: return null
        return try {
            json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** Runs [script] on the engine and decodes the array at [key], or null if the parse failed. */
    private suspend fun <T> parseWithEngine(script: String, serializer: KSerializer<T>, key: String): List<T>? {
        val obj = evaluateObject(script) ?: return null
        if (isNotOk(obj)) return null
        val arr = obj[key] as? JsonArray ?: return null
        return try {
            json.decodeFromJsonElement(ListSerializer(serializer), arr)
        } catch (_: Exception) {
            null
        }
    }
}
