package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.LibraryRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Headless-VTOP port of the library-dues parser (amazecc-api-hono `finance.ts:317-374`, route
 * registered at `:429`: `POST /vtop/finance/libraryPayments`).
 *
 * The WebView is used only as a cookie jar + synchronous XHR runtime: it POSTs the standard
 * `verifyMenu` form and returns the page's generic table structure (`parseVtopHtml`). All
 * interpretation — header detection, book mapping, day normalisation, message extraction —
 * lives here in Kotlin so it is deterministic and unit-testable without a browser. No book is
 * ever invented: a failed/unauthorized response yields null and a row without a title is dropped.
 *
 * This is a standalone implementation; it does not touch `VtopDataSource` or `VtopScripts`.
 */
object VtopLibraryScraper {

    private const val PATH = "/vtop/finance/libraryPayments"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Fetches and parses the library dues for the current session.
     *
     * Returns null when there is no live session, the engine is unsupported, or the request /
     * response is not a usable VTOP page. Never prompts for a captcha.
     */
    suspend fun fetchLibraryDues(): LibraryRes? = withContext(Dispatchers.Default) {
        if (!vtopEngineSupported) return@withContext null
        // Reuse the shared verifyMenu token string (verifyMenu/authorizedID/_csrf/nocache).
        val body = VtopSession.verifyMenuParams(Clock.System.now().toEpochMilliseconds())
            ?: return@withContext null
        val script = buildScript(body)
        val raw = runCatching {
            Vtop.engineInstance().evaluate(script, timeoutMs = 30_000)
        }.getOrNull() ?: return@withContext null
        parsePayload(raw)
    }

    /**
     * Pure decode + mapping of the JS payload, exposed for headless port tests.
     *
     * Accepts this scraper's envelope (`{ok, tables:[{headers,rows:{header:value}}],
     * keyValuePairs, messages}`) as well as the reference parser's `{books, tables,
     * keyValuePairs, messages}` shape. JSON parsing is wrapped in `runCatching`, so malformed
     * input yields null rather than throwing. A valid but empty page yields an empty `LibraryRes`
     * (never a fabricated book).
     */
    fun parsePayload(raw: String): LibraryRes? {
        if (raw.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        // An explicit `ok` must be true; a payload without one is treated as bare data.
        if (root.containsKey("ok") && (root["ok"] as? JsonPrimitive)?.booleanOrNull != true) {
            return null
        }

        val tables = (root["tables"] as? JsonArray).orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val headers = (obj["headers"] as? JsonArray).orEmpty().mapNotNull { h ->
                (h as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            }
            val rows = (obj["rows"] as? JsonArray).orEmpty().mapNotNull { r ->
                val row = r as? JsonObject ?: return@mapNotNull null
                headers.associateWith { h -> (row[h] as? JsonPrimitive)?.content?.trim().orEmpty() }
                    .filterValues { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
            }
            if (headers.isEmpty() || rows.isEmpty()) null else RawTable(headers, rows)
        }

        val books = mutableListOf<BookItem>()
        for (t in tables) {
            val titleKey = t.headers.firstOrNull { it.contains("title", true) || it.contains("book", true) }
            val amountKey = t.headers.firstOrNull {
                it.contains("amount", true) || it.contains("fine", true) || it.contains("due", true)
            }
            val daysKey = t.headers.firstOrNull { it.contains("overdue", true) || it.contains("day", true) }
            val authorKey = t.headers.firstOrNull { it.contains("author", true) }
            val isbnKey = t.headers.firstOrNull { it.contains("isbn", true) }
            if (titleKey == null && amountKey == null) continue
            for (row in t.rows) {
                val title = titleKey?.let { row[it] }?.trim().orEmpty()
                // A dues row with no title is not a book — drop it instead of fabricating one.
                if (title.isBlank()) continue
                val author = authorKey?.let { row[it] }?.trim()?.takeIf { it.isNotBlank() }
                val isbn = isbnKey?.let { row[it] }?.trim()?.takeIf { it.isNotBlank() }
                val fine = amountKey?.let { row[it] }?.trim()?.takeIf { it.isNotBlank() }
                val daysRaw = daysKey?.let { row[it] }?.trim()?.takeIf { it.isNotBlank() }
                // Match the reference: strip to digits/dash, fall back to raw text if not numeric.
                val digits = daysRaw?.let { DIGITS.replace(it, "") }
                val days = digits?.takeIf { it.isNotBlank() } ?: daysRaw
                books += BookItem(
                    bookId = isbn.orEmpty(),
                    title = title,
                    author = author,
                    // The lead's mapping: dueDate carries daysOverdue (fineAmount carries dueAmount).
                    dueDate = days,
                    fineAmount = fine,
                    isbn = isbn,
                    daysOverdue = days
                )
            }
        }

        // Also accept a precomputed `books` array (the reference parser's output shape) when
        // there were no tables to derive from.
        if (books.isEmpty()) {
            (root["books"] as? JsonArray).orEmpty().forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val title = o.str("title")?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                val isbn = o.str("isbn")?.trim()?.takeIf { it.isNotBlank() }
                val daysRaw = o.str("daysOverdue")?.trim()?.takeIf { it.isNotBlank() }
                val digits = daysRaw?.let { DIGITS.replace(it, "") }
                books += BookItem(
                    bookId = isbn.orEmpty(),
                    title = title,
                    author = o.str("author")?.trim()?.takeIf { it.isNotBlank() },
                    dueDate = digits?.takeIf { it.isNotBlank() } ?: daysRaw,
                    fineAmount = o.str("dueAmount")?.trim()?.takeIf { it.isNotBlank() },
                    isbn = isbn,
                    daysOverdue = digits?.takeIf { it.isNotBlank() } ?: daysRaw
                )
            }
        }

        // keyValuePairs have no dedicated LibraryRes field; surface a message-style entry via
        // `message` so the page's "no dues" / remark text is not lost. Explicit messages win.
        val kv = (root["keyValuePairs"] as? JsonObject).orEmpty().mapNotNull { entry ->
            (entry.value as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
                ?.let { entry.key to it }
        }.toMap()
        val messages = root["messages"] as? JsonObject
        val errorMsg = messages?.str("error")?.takeIf { it.isNotBlank() }
        val successMsg = messages?.str("success")?.takeIf { it.isNotBlank() }
        val warningMsg = messages?.str("warning")?.takeIf { it.isNotBlank() }
        val kvMessage = kv.entries.firstOrNull { entry ->
            val k = entry.key
            k.contains("message", true) || k.contains("dues", true) || k.contains("remark", true)
        }?.value
        val message = successMsg ?: warningMsg ?: kvMessage

        return LibraryRes(
            success = true,
            booksIssued = books,
            total = books.size,
            error = errorMsg,
            message = message
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private data class RawTable(val headers: List<String>, val rows: List<Map<String, String>>)

    // ── JS builder ──────────────────────────────────────────────────────────

    /**
     * Posts the form and walks every table generically — the same header-detection algorithm as
     * `VtopScripts.parsePage` / `parseVtopHtml`, which is what the reference `parseLibraryDues`
     * consumes. Emitting the raw structure keeps all interpretation in Kotlin.
     */
    private fun buildScript(body: String): String = wrapped(
        """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', ${jsString(PATH)}, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send(${jsString(body)});

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        var raw = xhr.responseText || '';
        var lower = raw.toLowerCase();
        if (lower.indexOf('not authorized') !== -1 ||
            lower.indexOf('session timed out') !== -1 ||
            lower.indexOf('invalid session') !== -1 ||
            lower.indexOf('session expired') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(raw, 'text/html');
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\\s+/g, ' ').trim(); }

        var tables = [];
        var tableEls = doc.querySelectorAll('table');
        for (var ti = 0; ti < tableEls.length; ti++) {
          var trs = tableEls[ti].querySelectorAll('tr');
          var headerIndex = -1;
          for (var hi = 0; hi < trs.length; hi++) {
            var ths = trs[hi].querySelectorAll('th');
            var tds0 = trs[hi].querySelectorAll('td');
            var use = false;
            if (ths.length > 1) use = true;
            else if (tds0.length > 1) {
              use = true;
              for (var ci = 0; ci < tds0.length; ci++) {
                if (parseInt(tds0[ci].getAttribute('colspan') || '1', 10) > 3) { use = false; break; }
              }
            }
            if (use) { headerIndex = hi; break; }
          }
          if (headerIndex === -1) continue;

          var headers = [];
          var hcells = trs[headerIndex].querySelectorAll('th, td');
          for (var hj = 0; hj < hcells.length; hj++) {
            var ht = squish(hcells[hj]);
            headers.push(ht ? ht : ('col' + headers.length));
          }

          var rows = [];
          for (var ri = headerIndex + 1; ri < trs.length; ri++) {
            var row = {};
            var hasData = false;
            var rcells = trs[ri].querySelectorAll('td');
            for (var rj = 0; rj < rcells.length; rj++) {
              var rt = squish(rcells[rj]);
              if (!rt) continue;
              row[headers[rj] || ('col' + rj)] = rt;
              hasData = true;
            }
            if (hasData) rows.push(row);
          }
          if (headers.length > 0 && rows.length > 0) {
            var capEl = tableEls[ti].querySelector('caption');
            tables.push({ caption: capEl ? squish(capEl) : '', headers: headers, rows: rows });
          }
        }

        var keyValuePairs = {};
        if (tableEls.length > 0) {
          var ktrs = tableEls[0].querySelectorAll('tr');
          for (var ki = 0; ki < ktrs.length; ki++) {
            var kc = ktrs[ki].querySelectorAll('td');
            if (kc.length !== 2) continue;
            var k = squish(kc[0]);
            var v = squish(kc[1]);
            if (k && v && k !== v) keyValuePairs[k] = v;
          }
        }

        var messages = {};
        var warn = doc.querySelector('input#warning, input[name=warning]');
        var err = doc.querySelector('input#error, input[name=error]');
        var okEl = doc.querySelector('input#success, input[name=success]');
        if (warn && warn.value) messages.warning = warn.value;
        if (err && err.value) messages.error = err.value;
        if (okEl && okEl.value) messages.success = okEl.value;

        return JSON.stringify({
          ok: true, tables: tables, keyValuePairs: keyValuePairs, messages: messages
        });
        """
    )

    private val DIGITS = Regex("[^0-9-]")

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
}
