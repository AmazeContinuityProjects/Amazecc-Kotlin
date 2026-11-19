package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Headless-VTOP port of the exam-schedule parser (amazecc-api-hono `ffcs.ts:90-139`,
 * AmazeCC-API `/api/ffcs/route.ts:75-130`).
 *
 * The WebView is used only as a cookie jar + synchronous XHR runtime: it POSTs the form, walks
 * `table.customTable tr` with `DOMParser`, and emits *raw positional cells*. All interpretation
 * (exam-type grouping, cell mapping, seat fallback) lives here in Kotlin so it is deterministic
 * and unit-testable without a browser. No row is ever invented: a failed/unauthorized response
 * returns null, and a section with no parseable rows stays empty.
 *
 * This is a standalone implementation; it does not touch `VtopDataSource` or `VtopScripts`.
 */
object VtopExamScraper {

    private const val PATH = "/vtop/examinations/doSearchExamScheduleForStudent"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Prefixes VTOP/the web app treat as seat-exempt (no derived R..C.. location). */
    private val EXEMPT_SEAT_COURSES = listOf(
        "Qualitative", "Quantitative", "French", "German", "Spanish", "Japanese"
    )

    /**
     * Fetches and parses the exam schedule for [semesterId].
     *
     * Returns null when there is no live session, the engine is unsupported, or the request /
     * response is not a usable VTOP schedule. Never prompts for a captcha.
     */
    suspend fun fetchExamSchedule(semesterId: String): ExamScheduleRes? = withContext(Dispatchers.Default) {
        if (!vtopEngineSupported) return@withContext null
        val body = examBody(semesterId) ?: return@withContext null
        val script = buildScript(body, semesterId)
        val raw = runCatching {
            Vtop.engineInstance().evaluate(script, timeoutMs = 30_000)
        }.getOrNull() ?: return@withContext null
        parsePayload(raw)
    }

    /**
     * Form body for the POST. Reuses the shared `verifyMenu` token string (which already carries
     * `verifyMenu=true`, `authorizedID`, `_csrf` and `nocache`) and appends `semesterSubId` — the
     * missing field in the old data-source path. Uses only public [VtopSession] helpers.
     */
    private fun examBody(semesterId: String): String? {
        val base = VtopSession.verifyMenuParams(Clock.System.now().toEpochMilliseconds()) ?: return null
        val sem = semesterId.takeIf { it.isNotBlank() }
            ?: VtopSession.semesterSubId.value?.takeIf { it.isNotBlank() }
            ?: return null
        return "$base&semesterSubId=$sem"
    }

    /** Pure decode + map of the JS payload. Exposed for headless port tests. */
    fun parsePayload(raw: String): ExamScheduleRes? {
        val root = runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) }.getOrNull() as? JsonObject
            ?: return null
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        val groupsEl = root["groups"] as? JsonArray ?: return null
        val groups = runCatching {
            json.decodeFromJsonElement(ListSerializer(VtopExamGroup.serializer()), groupsEl)
        }.getOrNull() ?: return null

        val schedule = LinkedHashMap<String, List<ExamItem>>()
        for (group in groups) {
            val type = group.type.trim()
            if (type.isBlank()) continue
            // Keep the type even if every row was rejected — an empty list is not a fabricated row.
            schedule[type] = group.rows.mapNotNull(::cellsToItem)
        }
        return ExamScheduleRes(success = true, rawScheduleUpper = schedule)
    }

    /**
     * Positional mapping. Column 0 (S.No) and column 3 are unused, exactly as in the reference.
     * Out-of-range cells read as blank rather than throwing (jQuery's missing-cell behaviour).
     * A row with no course code is discarded as noise.
     */
    private fun cellsToItem(cells: List<String>): ExamItem? {
        fun cell(i: Int): String = cells.getOrNull(i)?.trim().orEmpty()
        val courseCode = cell(1)
        if (courseCode.isBlank()) return null
        return ExamItem(
            courseCode = courseCode,
            courseTitle = cell(2),
            classId = cell(4),
            slot = cell(5),
            examDate = cell(6),
            examSession = cell(7),
            reportingTime = cell(8),
            examTime = cell(9),
            venue = cell(10),
            seatLocation = normalizeSeatLocation(cell(11), cell(12), cell(2)),
            seatNo = cell(12)
        )
    }

    /**
     * VTOP emits `"-"` for the seat *location* on many rows while still giving a numeric seat
     * number. Derive `R{row}C{col}` with the web app's 18-seats-per-2-column-group layout, but only
     * for a positive numeric seat number on a non-exempt course; otherwise leave the raw value
     * untouched (never fabricate a location).
     */
    fun normalizeSeatLocation(seatLocation: String, seatNo: String, courseTitle: String): String {
        val raw = seatLocation.trim()
        if (raw.isNotBlank() && raw != "-") return raw
        val n = seatNo.trim().toIntOrNull() ?: return raw
        if (n <= 0) return raw
        val title = courseTitle.trim()
        if (EXEMPT_SEAT_COURSES.any { title.startsWith(it) }) return raw

        val groupIndex = (n - 1) / 18
        val c1 = groupIndex * 2 + 1
        val pos = (n - 1) % 18
        val row = pos / 2 + 1
        val col = if (pos % 2 == 0) c1 else c1 + 1
        return "R${row}C$col"
    }

    // ── JS builder ──────────────────────────────────────────────────────────

    private fun buildScript(body: String, semesterId: String): String = wrapped(
        """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $PATH_LITERAL, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send(${jsString(body)});

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        if (xhr.status >= 400) return JSON.stringify({ ok: false, error: 'http error ' + xhr.status });

        var res = xhr.responseText || '';
        var lower = res.toLowerCase();
        if (lower.indexOf('not authorized') !== -1 ||
            lower.indexOf('session timed out') !== -1 ||
            lower.indexOf('invalid session') !== -1 ||
            lower.indexOf('session expired') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var rows = doc.querySelectorAll('table.customTable tr');
        var groups = [];
        var byType = {};
        var current = null;

        for (var r = 0; r < rows.length; r++) {
          var row = rows[r];
          var tds = row.querySelectorAll('td');

          // A single colspan=13 cell names the exam type for every following row.
          if (tds.length === 1 && (tds[0].getAttribute('colspan') || '') === '13') {
            var label = (tds[0].textContent || '').replace(/\s+/g, ' ').trim();
            if (label) {
              current = label;
              if (!byType[current]) { byType[current] = []; groups.push({ type: current, rows: byType[current] }); }
            }
            continue;
          }
          if ((row.className || '').indexOf('tableHeader') !== -1) continue;
          if (!current) continue;
          if ((row.className || '').indexOf('tableContent') !== -1 && tds.length > 1) {
            var cells = [];
            for (var c = 0; c < tds.length; c++) {
              cells.push((tds[c].textContent || '').replace(/\s+/g, ' ').trim());
            }
            byType[current].push(cells);
          }
        }

        return JSON.stringify({ ok: true, status: xhr.status, semester: ${jsString(semesterId)}, groups: groups });
        """
    )

    private val PATH_LITERAL: String get() = jsString(PATH)

    /** Escapes [value] as a JavaScript string literal (JSON-style escaping). */
    private fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 2).append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun wrapped(body: String): String = """
        (function() {
          try {
            $body
          } catch (e) {
            return JSON.stringify({ ok: false, __error: String(e && e.message ? e.message : e) });
          }
        })();
    """.trimIndent()

    @Serializable
    private data class VtopExamGroup(
        val type: String = "",
        val rows: List<List<String>> = emptyList()
    )
}
