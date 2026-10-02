package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.LMSAssignment
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.state.AppSanitizers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `lms.vit.ac.in` (Moodle), scraped on-device.
 *
 * Same split as [VtopEventHub]: Ktor does the network, the WebView supplies only a DOM. Cross-origin
 * XHR from the VTOP WebView is rejected by CORS, so the pages have to come over Ktor and be parsed
 * by the engine.
 *
 * The DTO shape here is the one a live login actually returns, not the one the hosted route used to
 * invent: Moodle gives `courseCode` as `BAMAT209_FALL26-27` and `courseTitle` as
 * `Mathematical Foundations for Computation(BAMAT209)`, with the due date living in the *calendar
 * cell* as day/month/year plus a human-readable `due` string. Nothing returns `maxMarks`, `status`
 * or `score`, so the old DTO could never deserialise a real payload.
 *
 * Credentials are separate from VTOP's (`SettingsManager.getMoodleCredentials()`), matching the
 * original app's MoodleConnectSheet.
 */
object VtopLms {

    private const val LMS = "https://lms.vit.ac.in"

    /**
     * How many months either side of the current one to walk.
     *
     * The dashboard only renders the month it opens on, and the calendar exposes no "all upcoming"
     * view, so coverage has to be built by paging. Forward matters most — that is where deadlines
     * live — but one month back catches anything just passed that is still open.
     */
    private const val MONTHS_BACK = 1
    private const val MONTHS_FORWARD = 3

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 25_000
        }
    }

    suspend fun fetchAssignments(username: String, password: String): LMSRes? =
        withContext(Dispatchers.Default) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext LMSRes(success = false, message = "Moodle credentials not set")
            }
            val cookies = login(username, password)
                ?: return@withContext LMSRes(success = false, message = "Moodle login failed")

            val assignments = runCatching { collect(cookies) }.getOrElse { e ->
                return@withContext LMSRes(success = false, message = e.message, error = e.toString())
            }

            AppSanitizers.sanitizeLms(LMSRes(success = true, assignments = assignments))
        }

    // ── login ───────────────────────────────────────────────────────────────

    /**
     * Moodle's three-step login: the login page mints a `logintoken`, the POST must *not* follow the
     * redirect (following it loses the `Set-Cookie` that carries the session), and the resulting
     * `Location` must then be fetched to confirm a `sesskey` came back.
     */
    private suspend fun login(username: String, password: String): String? {
        val loginRes = http.get("$LMS/login/index.php") { header(HttpHeaders.UserAgent, ua()) }
        if (loginRes.status.value !in 200..299) return null
        val loginPage = loginRes.bodyAsText()

        val token = LOGIN_TOKEN.find(loginPage)?.groupValues?.get(1) ?: return null
        var cookies = cookieHeader(loginRes)

        val posted = http.post("$LMS/login/index.php") {
            header(HttpHeaders.UserAgent, ua())
            header(HttpHeaders.Cookie, cookies)
            header("Referer", "$LMS/login/index.php")
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            setBody(
                Parameters.build {
                    append("logintoken", token)
                    append("username", username)
                    append("password", password)
                }
            )
        }
        cookies = mergeCookies(cookies, cookieHeader(posted))

        // A redirect means the credentials were accepted; a 200 with the form again means they were not.
        val location = posted.headers[HttpHeaders.Location] ?: return null
        val dashboard = http.get(location) {
            header(HttpHeaders.UserAgent, ua())
            header(HttpHeaders.Cookie, cookies)
        }.takeIf { it.status.value in 200..299 }?.bodyAsText() ?: return null

        return if (SESSKEY.containsMatchIn(dashboard)) cookies else null
    }

    // ── collection ──────────────────────────────────────────────────────────

    private suspend fun collect(cookies: String): List<LMSAssignment> {
        val dashboard = http.get("$LMS/my/") {
            header(HttpHeaders.UserAgent, ua())
            header(HttpHeaders.Cookie, cookies)
        }.bodyAsText()

        val first = parseCalendar(dashboard) ?: return emptyList()

        // Month paging: the calendar's own arrows, because the paging URL needs a `time` epoch.
        // Each hop yields the next arrow, so the whole window is collected before any event is
        // visited - that keeps the number of sequential WebView parses to one per page.
        val monthHtml = mutableListOf<String>()
        var back: String? = first.prevMonthUrl.takeIf { it.isNotBlank() }
        var forward: String? = first.nextMonthUrl.takeIf { it.isNotBlank() }
        repeat(MONTHS_BACK) {
            val url = back ?: return@repeat
            val html = get(url, cookies) ?: return@repeat
            monthHtml += html
            back = parseCalendar(html)?.prevMonthUrl?.takeIf { it.isNotBlank() }
        }
        repeat(MONTHS_FORWARD) {
            val url = forward ?: return@repeat
            val html = get(url, cookies) ?: return@repeat
            monthHtml += html
            forward = parseCalendar(html)?.nextMonthUrl?.takeIf { it.isNotBlank() }
        }

        val seen = mutableSetOf<String>()
        val out = mutableListOf<LMSAssignment>()

        // Sequential by necessity: the WebView script is synchronous, so overlapping page loads
        // would interleave on one JS thread.
        for (html in listOf(dashboard) + monthHtml) {
            val cal = parseCalendar(html) ?: continue
            for (event in cal.events) {
                if (event.url.isBlank() || !seen.add(event.url)) continue
                val a = assignmentFor(event, cookies) ?: continue
                out += a
            }
        }
        return out
    }

    private suspend fun get(url: String, cookies: String): String? = runCatching {
        http.get(url) {
            header(HttpHeaders.UserAgent, ua())
            header(HttpHeaders.Cookie, cookies)
        }.takeIf { it.status.value in 200..299 }?.bodyAsText()
    }.getOrNull()

    private suspend fun assignmentFor(event: LmsCalendarEvent, cookies: String): LMSAssignment? {
        val eventHtml = get(event.url, cookies) ?: return null
        val parsed = parseEvent(eventHtml) ?: return null

        // Teachers need the course page: the event page has no section context for the module.
        var teachers: List<String> = emptyList()
        if (!parsed.courseId.isNullOrBlank() && !parsed.moduleId.isNullOrBlank()) {
            val courseHtml = get("$LMS/course/view.php?id=${parsed.courseId}", cookies)
            if (courseHtml != null) teachers = parseTeachers(courseHtml, parsed.moduleId)
        }

        // `name` is `courseCode/courseTitle/assignmentTitle`, which is where the DTO's own fields
        // come from - Moodle has no separate "course code" input.
        val parts = parsed.name.split('/')
        val code = parts.getOrNull(0).orEmpty()
        val title = parts.getOrNull(1).orEmpty()
        val assignment = parts.getOrNull(2).orEmpty()

        return LMSAssignment(
            name = parsed.name,
            courseCode = code,
            courseTitle = title,
            assignmentTitle = assignment,
            due = parsed.due,
            done = parsed.done,
            day = event.day,
            month = event.month,
            year = event.year,
            url = event.url,
            teachers = teachers,
        )
    }

    // ── engine-backed parsing ───────────────────────────────────────────────

    private data class LmsCalendar(
        val events: List<LmsCalendarEvent>,
        val prevMonthUrl: String,
        val nextMonthUrl: String
    )

    private data class LmsCalendarEvent(
        val url: String,
        val day: Int?,
        val month: Int?,
        val year: Int?
    )

    private data class LmsEvent(
        val name: String,
        val due: String,
        val done: Boolean,
        val moduleId: String?,
        val courseId: String?
    )

    private suspend fun evaluate(script: String): JsonObject? {
        val raw = runCatching { Vtop.engineInstance().evaluate(script) }.getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) }.getOrNull()
        return obj as? JsonObject
    }

    private fun ok(o: JsonObject?): JsonObject? =
        o?.takeIf { (it["ok"] as? JsonPrimitive)?.content == "true" }

    private suspend fun parseCalendar(html: String): LmsCalendar? {
        val o = ok(evaluate(VtopScripts.parseLmsCalendar(html))) ?: return null
        val events = (o["events"] as? JsonArray).orEmpty().mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            LmsCalendarEvent(
                url = e.asString("url"),
                day = e.asIntOrNull("day"),
                month = e.asIntOrNull("month"),
                year = e.asIntOrNull("year"),
            )
        }
        return LmsCalendar(
            events = events,
            prevMonthUrl = o.asString("prevMonthUrl"),
            nextMonthUrl = o.asString("nextMonthUrl"),
        )
    }

    private suspend fun parseEvent(html: String): LmsEvent? {
        val o = ok(evaluate(VtopScripts.parseLmsEvent(html))) ?: return null
        return LmsEvent(
            name = o.asString("name"),
            due = o.asString("due"),
            done = o["done"] == JsonPrimitive(true),
            moduleId = o.asStringOrNull("moduleId"),
            courseId = o.asStringOrNull("courseId"),
        )
    }

    private suspend fun parseTeachers(html: String, moduleId: String): List<String> {
        val o = ok(evaluate(VtopScripts.parseLmsTeachers(html, moduleId))) ?: return emptyList()
        return (o["teachers"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun JsonObject.asString(key: String): String = asStringOrNull(key).orEmpty()

    private fun JsonObject.asStringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.asIntOrNull(key: String): Int? = asStringOrNull(key)?.toIntOrNull()

    // ── cookies ─────────────────────────────────────────────────────────────

    /**
     * Moodle hands out several cookies and later pages need all of them, so the whole `Set-Cookie`
     * list is carried forward rather than only the session cookie.
     */
    private fun cookieHeader(res: HttpResponse): String =
        res.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .map { it.substringBefore(';').trim() }
            .filter { it.isNotEmpty() && !it.endsWith('=') }
            .joinToString("; ")

    private fun mergeCookies(a: String, b: String): String {
        val merged = LinkedHashMap<String, String>()
        for (header in listOf(a, b)) {
            for (part in header.split("; ")) {
                val eq = part.indexOf('=')
                if (eq > 0) merged[part.substring(0, eq)] = part.substring(eq + 1)
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun ua(): String = UserAgentPool.current()

    private val LOGIN_TOKEN = Regex("""name="logintoken"\s+value="([^"]+)"""")
    private val SESSKEY = Regex(""""sesskey":"[^"]+"""")
}
