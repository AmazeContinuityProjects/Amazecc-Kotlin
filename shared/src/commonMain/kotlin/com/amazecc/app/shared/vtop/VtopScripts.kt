package com.amazecc.app.shared.vtop

/**
 * JavaScript executed inside the hidden WebView.
 *
 * VTOP endpoints all return HTML fragments, and the app is KMP with no HTML parser available in
 * `commonMain`. Parsing therefore stays in the WebView — the same approach as the Flutter
 * reference (`fkvit/lib/core/services/vtop_data_service.dart`), where every step runs
 * `DOMParser` and returns JSON.
 *
 * Every snippet here is *synchronous* on purpose: the reference implementation's
 * `return JSON.stringify(...)` depends on the XHR having completed before the script returns.
 */

internal object VtopScripts {

    /**
     * Escapes [value] as a JavaScript string literal.
     *
     * The reference implementation substitutes credentials with `replaceAll("'", "\\\\'")`,
     * which breaks on backslashes and newlines. JSON-style escaping is correct for every input.
     */
    fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
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
        sb.append('"')
        return sb.toString()
    }

    private fun wrapped(body: String): String = """
        (function() {
          try {
            $body
          } catch (e) {
            return JSON.stringify({ __error: String(e && e.message ? e.message : e) });
          }
        })();
    """.trimIndent()

    /** Classifies the current document. Mirrors `_detectPageState()`. */
    val PAGE_STATE = wrapped(
        """
        if (!document.body) return JSON.stringify({ state: 'BODY_NOT_READY' });
        if (document.querySelector('input[id="authorizedIDX"]')) return JSON.stringify({ state: 'HOME' });
        if (document.querySelector('form[id="vtopLoginForm"]')) return JSON.stringify({ state: 'LOGIN' });
        return JSON.stringify({ state: 'LANDING' });
        """
    )

    /**
     * Prelogin handshake for the LANDING state: POST `#stdForm`, which leaves the session
     * primed so a subsequent `/login` renders the real login form.
     */
    val PRELOGIN = wrapped(
        """
        if (typeof $ === 'undefined') {
          if (typeof jQuery === 'undefined') return JSON.stringify({ ok: false, error: 'jQuery absent' });
        }
        var $ = (typeof jQuery !== 'undefined') ? jQuery : null;
        if (!$) return JSON.stringify({ ok: false, error: 'jQuery absent' });
        var form = document.getElementById('stdForm');
        if (!form) return JSON.stringify({ ok: false, error: 'stdForm not found' });
        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/vtop/prelogin/setup', false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($(form).serialize());
        return JSON.stringify({ ok: true, status: xhr.status });
        """
    )

    /**
     * Reads the captcha.
     *
     * `isRecaptcha` comes from the presence of `#gResponse`. The image `src` is an inline
     * `data:image/...;base64,` URI, so no extra HTTP request is needed.
     */
    val READ_CAPTCHA = wrapped(
        """
        var isRecaptcha = document.querySelectorAll('input#gResponse').length === 1;
        if (isRecaptcha) return JSON.stringify({ isRecaptcha: true, base64: null });
        var img = document.querySelector('#captchaBlock img');
        if (!img) return JSON.stringify({ isRecaptcha: false, base64: null, error: 'captcha image not found' });
        return JSON.stringify({ isRecaptcha: false, base64: img.getAttribute('src') });
        """
    )
    /**
     * Submits the login form and classifies the response.
     *
     * Three details are load-bearing and easy to lose in a port:
     *  1. The body is `$('#vtopLoginForm').serialize()` — *every* input, including `_csrf`.
     *     Sending only username/password/captchaStr fails.
     *  2. The captcha goes into **both** `captchaStr` and `gResponse`.
     *  3. Success is detected by `authorizedIDX` appearing in the response body.
     */
    fun submitLogin(username: String, password: String, captcha: String): String {
        val isRecaptcha = captcha.length > 100
        val processed = if (isRecaptcha) captcha else captcha.replace(Regex("[^A-Za-z0-9]"), "").trim().uppercase()
        val u = jsString(username)
        val p = jsString(password)
        val c = jsString(processed)
        return wrapped(
            """
        if (typeof jQuery === 'undefined') return JSON.stringify({ error_code: 0, error_message: 'jQuery absent' });
        var $ = jQuery;
        var form = document.getElementById('vtopLoginForm');
        if (!form) return JSON.stringify({ error_code: 0, error_message: 'login form not found' });

        $(form).find('[name="username"]').val($u);
        $(form).find('[name="password"]').val($p);
        $(form).find('[name="captchaStr"]').val($c);
        $(form).find('[name="gResponse"]').val($c);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/vtop/login', false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($(form).serialize());

        if (xhr.status === 0) return JSON.stringify({ error_code: 0, error_message: 'network error' });
        var res = xhr.responseText || '';

        // VTOP's internal/WAF marker. The reference implementation has no handling for it and
        // falls through to unknown-error; we do the same explicitly.
        if (res.indexOf('___INTERNAL___RESPONSE___') !== -1) {
          return JSON.stringify({ error_code: 0, error_message: 'internal response marker' });
        }

        if (res.indexOf('authorizedIDX') !== -1) {
          return JSON.stringify({ error_code: 0, authorized: true });
        }

        var body = res.toLowerCase();
        if (/invalid\s*captcha/.test(body)) {
          return JSON.stringify({ error_code: 1, error_message: 'Invalid Captcha' });
        }
        if (/invalid\s*(user\s*name|login\s*id|user\s*id)\s*\/\s*password/.test(body)) {
          return JSON.stringify({ error_code: 2, error_message: 'Invalid Username / Password' });
        }
        if (/account\s*is\s*locked/.test(body)) {
          return JSON.stringify({ error_code: 3, error_message: 'Account is locked' });
        }
        if (/maximum\s*fail\s*attempts/.test(body)) {
          return JSON.stringify({ error_code: 4, error_message: 'Maximum login attempts reached' });
        }
        if (body.indexOf('login') !== -1 && (body.indexOf('error') !== -1 || body.indexOf('fail') !== -1)) {
          return JSON.stringify({ error_code: 5, error_message: 'Login failed' });
        }
        return JSON.stringify({ error_code: 0, error_message: 'Login failed' });
        """
        )
    }

    /** Pulls the session tokens out of the rendered `/vtop/content` page. */
    val EXTRACT_CONTENT = wrapped(
        """
        var csrfInput = document.querySelector('input[name="_csrf"]');
        var idInput = document.querySelector('#authorizedIDX') || document.querySelector('input[name="authorizedid"]');
        var winInput = document.querySelector('#winImage');
        if (!idInput) return JSON.stringify({ ok: false, error: 'authorizedIDX not found' });
        return JSON.stringify({
          ok: true,
          csrf: csrfInput ? csrfInput.value : null,
          authorizedIDX: idInput.value,
          winImage: winInput ? winInput.value : null,
          href: window.location.href
        });
        """
    )

    /**
     * Fetches the semester list from `StudentTimeTableChn` and returns a name -> id map.
     *
     * A `not authorized` body is the User-Agent block signal.
     */
    val FETCH_SEMESTERS = wrapped(
        """
        var csrf = (document.querySelector('input[name="_csrf"]') || {}).value;
        var authId = (document.querySelector('#authorizedIDX') || {}).value;
        if (!csrf || !authId) return JSON.stringify({ ok: false, error: 'missing csrf or authorizedIDX' });

        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/vtop/academics/common/StudentTimeTableChn', false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send('_csrf=' + encodeURIComponent(csrf) + '&semesterSubId=&authorizedID=' + encodeURIComponent(authId));

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var select = doc.querySelector('#semesterSubId');
        if (!select) return JSON.stringify({ ok: false, error: 'semesterSubId not found' });
        var map = {};
        var options = select.querySelectorAll('option');
        for (var i = 0; i < options.length; i++) {
          var o = options[i];
          if (o.value) map[o.textContent.trim()] = o.value;
        }
        return JSON.stringify({ ok: true, semesters: map });
        """
    )

    /** One extra value captured from inside each row, beyond the cell texts. */
    data class Capture(val selector: String, val name: String, val value: Boolean = false)

    /**
     * POSTs a form body and returns the rows matched by [selector], positionally.
     *
     * The server's parsers index cells positionally (`cols.eq(7)`) and take *all* rows
     * including any header, so header detection would shift every column by one. Everything the
     * server did is expressed as a parameter here instead:
     *
     *  - [tableSelector] + [tableIndex] — scope to the Nth matching table, which is how the
     *    grades parser reaches `#fixedTableContainer table` `.eq(1)`/`.eq(5)`/`.eq(6)`
     *  - [headerRowsToSkip] — for pages whose first row is a header (`table.table-bordered`)
     *  - [captures] — attributes or input values pulled from inside the row, e.g. the
     *    attendance detail `onclick` and the receipt's hidden `applno`/`regno`
     */
    fun fetchRows(
        path: String,
        body: String,
        selector: String,
        tableSelector: String? = null,
        tableIndex: Int = 0,
        captures: List<Capture> = emptyList(),
        headerRowsToSkip: Int = 0
    ): String {
        val p = jsString(path)
        val b = jsString(body)
        val sel = jsString(selector)
        val tableSel = jsString(tableSelector ?: "")
        val tIdx = tableIndex.coerceAtLeast(0)
        val skip = headerRowsToSkip.coerceAtLeast(0)
        val captureJson = captures.joinToString(",", "[", "]") { c ->
            "{selector:${jsString(c.selector)},name:${jsString(c.name)},value:${c.value}}"
        }
        return wrapped(
            """
        var specs = $captureJson;

        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var scope = doc;
        if ($tableSel) {
          var tables = doc.querySelectorAll($tableSel);
          scope = tables[$tIdx] || null;
          if (!scope) return JSON.stringify({ ok: true, rows: [], captures: [], keyValuePairs: {} });
        }

        var trEls = scope.querySelectorAll($sel);
        var rows = [];
        var extra = [];
        var i = $skip;

        for (; i < trEls.length; i++) {
          var tds = trEls[i].querySelectorAll('td');
          if (tds.length === 0) continue;
          var cells = [];
          var hasData = false;
          for (var c = 0; c < tds.length; c++) {
            var text = (tds[c].textContent || '').replace(/\\s+/g, ' ').trim();
            cells.push(text);
            if (text) hasData = true;
          }
          if (!hasData) continue;
          rows.push(cells);

          var captured = [];
          for (var s = 0; s < specs.length; s++) {
            var el = trEls[i].querySelector(specs[s].selector);
            captured.push(el ? (specs[s].value ? (el.value || '') : (el.getAttribute(specs[s].name) || '')) : null);
          }
          extra.push(captured);
        }

        // Label/value blocks from the first two-column table, for the profile-style pages.
        var pairs = {};
        var firstTable = doc.querySelector('table');
        if (firstTable) {
          var firstRows = firstTable.querySelectorAll('tr');
          for (var pr = 0; pr < firstRows.length; pr++) {
            var pc = firstRows[pr].querySelectorAll('td');
            if (pc.length === 2) {
              var k = (pc[0].textContent || '').replace(/\\s+/g, ' ').trim();
              var v = (pc[1].textContent || '').replace(/\\s+/g, ' ').trim();
              if (k && v && k !== v) pairs[k] = v;
            }
          }
        }

        return JSON.stringify({ ok: true, status: xhr.status, rows: rows, captures: extra, keyValuePairs: pairs });
        """
        )
    }

    /**
     * `examinations/doStudentMarkView`.
     *
     * Structurally different from the other pages: each course is a `tr.tableContent` and its
     * assessments live in a *nested* `table.customTable-level1 > tbody > tr.tableContent-level1`
     * on the following row. Assessment values are read from `<output>` elements, not the cell
     * text. Column indices per AmazeCC-API `src/lib/marks.ts`.
     */
    fun fetchMarks(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var courses = [];
        var courseRows = doc.querySelectorAll('table.customTable > tbody > tr.tableContent');

        for (var r = 0; r < courseRows.length; r++) {
          var cols = courseRows[r].querySelectorAll('td');
          if (cols.length < 9) continue;

          var cells = [];
          for (var c = 0; c < cols.length; c++) {
            cells.push((cols[c].textContent || '').replace(/\\s+/g, ' ').trim());
          }

          // Assessments are in the next row of the same tbody.
          var next = courseRows[r].nextElementSibling;
          var assessments = [];
          if (next) {
            var aRows = next.querySelectorAll('table.customTable-level1 > tbody > tr.tableContent-level1');
            for (var a = 0; a < aRows.length; a++) {
              var acols = aRows[a].querySelectorAll('td');
              var avals = [];
              for (var v = 0; v < 7; v++) {
                var out = acols[v] ? acols[v].querySelector('output') : null;
                avals.push(out ? (out.textContent || '').replace(/\\s+/g, ' ').trim() : '');
              }
              assessments.push(avals);
            }
          }

          // The server drops courses with no assessments.
          if (assessments.length > 0) courses.push({ cells: cells, assessments: assessments });
        }

        return JSON.stringify({ ok: true, status: xhr.status, courses: courses });
        """
        )
    }

    /**
     * `get/dashboard/current/cgpa/credits`.
     *
     * The summary is a Bootstrap list group, not a table: `span.card-title` holds the label and
     * `span.fontcolor3 span` the value. Labels are matched by substring, as in the server.
     */
    fun fetchCgpaSummary(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var out = {};
        var items = doc.querySelectorAll('.list-group-item');

        for (var i = 0; i < items.length; i++) {
          var labelEl = items[i].querySelector('span.card-title');
          var valueEl = items[i].querySelector('span.fontcolor3 span');
          var label = labelEl ? (labelEl.textContent || '').trim() : '';
          var value = valueEl ? (valueEl.textContent || '').trim() : '';
          if (label.indexOf('Total Credits Required') !== -1) out.creditsRequired = value;
          else if (label.indexOf('Earned Credits') !== -1) out.creditsEarned = value;
          else if (label.indexOf('Current CGPA') !== -1) out.cgpa = value;
          else if (label.indexOf('Non-graded Core Requirement') !== -1) out.nonGradedRequirement = value;
        }

        return JSON.stringify({ ok: true, status: xhr.status, cgpa: out });
        """
        )
    }

    /**
     * `p2p/Payments` — the dues page.
     *
     * Not a table parse: a green `<font color='green'>` means "no dues", and any table at all
     * means dues exist. Mirrors `src/lib/parsers/payments.ts`.
     */
    fun fetchPaymentStatus(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var titleEl = doc.querySelector('h3.box-title');
        var msgEl = doc.querySelector("font[color='green']");

        var message = '';
        if (msgEl) {
          var clone = msgEl.cloneNode(true);
          var junk = clone.querySelectorAll('script, style');
          for (var s = 0; s < junk.length; s++) junk[s].parentNode.removeChild(junk[s]);
          message = (clone.textContent || '').replace(/\\s+/g, ' ').trim();
        }

        var hasTable = doc.querySelectorAll('table').length > 0;

        return JSON.stringify({
          ok: true,
          status: xhr.status,
          title: titleEl ? (titleEl.textContent || '').trim() : '',
          message: message,
          hasDues: !message && hasTable
        });
        """
        )
    }

    /**
     * POSTs a form body and returns the whole page as a generic structure.
     *
     * A direct port of AmazeCC-API's `src/lib/parsers/auto-parse.ts` `parseVtopHtml`, serving
     * APAAR, EPT Schedule, Registration Schedule, University Day and Dayboarder. Those five
     * pages are all "a form and a couple of tables" with no bespoke column mapping, so one
     * extractor covers them.
     *
     * Faithful details that matter:
     *  - header row = first `tr` with >1 `th`, else >1 `td` with no `td[colspan > 3]`
     *  - an empty header cell becomes `col${headers.length}` (0-based on current length)
     *  - an empty body cell is skipped but still consumes its `td` ordinal
     *  - `keyValuePairs` comes from the FIRST table only, and only 2-cell rows
     *  - a `select` never appears in `formFields`
     */
    fun parsePage(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);

        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });
        var raw = xhr.responseText || '';
        if (raw.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(raw, 'text/html');

        function txt(el) { return el ? (el.textContent || '').trim() : ''; }
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\\s+/g, ' ').trim(); }

        var titleEl = doc.querySelector('h3.box-title');
        var title = titleEl ? txt(titleEl) : '';
        if (!title) {
          var t = doc.querySelector('title');
          title = t ? txt(t) : '';
        }

        var hiddenFields = {};
        var hidden = doc.querySelectorAll('input[type=hidden]');
        for (var hi = 0; hi < hidden.length; hi++) {
          var hn = hidden[hi].getAttribute('name') || hidden[hi].getAttribute('id') || '';
          if (hn) hiddenFields[hn] = hidden[hi].getAttribute('value') || '';
        }

        var selectOptions = {};
        var selects = doc.querySelectorAll('select');
        for (var si = 0; si < selects.length; si++) {
          var sn = selects[si].getAttribute('name') || selects[si].getAttribute('id') || 'select';
          var opts = [];
          var olist = selects[si].querySelectorAll('option');
          for (var oi = 0; oi < olist.length; oi++) {
            opts.push({
              value: olist[oi].getAttribute('value') || '',
              text: txt(olist[oi]),
              selected: olist[oi].getAttribute('selected') !== null
            });
          }
          if (opts.length > 0) selectOptions[sn] = opts;
        }

        var formFields = {};
        var controls = doc.querySelectorAll('input:not([type=hidden]), textarea');
        for (var fi = 0; fi < controls.length; fi++) {
          var fname = controls[fi].getAttribute('name') || controls[fi].getAttribute('id') || '';
          if (!fname) continue;
          var fval = controls[fi].getAttribute('value');
          if (fval === null || fval === '') fval = txt(controls[fi]);
          formFields[fname] = fval || '';
        }

        var tables = [];
        var tableEls = doc.querySelectorAll('table');
        for (var ti = 0; ti < tableEls.length; ti++) {
          var capEl = tableEls[ti].querySelector('caption');
          var caption = capEl ? txt(capEl) : '';

          var trs = tableEls[ti].querySelectorAll('tr');
          var headerIndex = -1;
          for (var idx = 0; idx < trs.length; idx++) {
            var ths = trs[idx].querySelectorAll('th');
            var tds = trs[idx].querySelectorAll('td');
            var use = false;
            if (ths.length > 1) {
              use = true;
            } else if (tds.length > 1) {
              use = true;
              for (var ci = 0; ci < tds.length; ci++) {
                if (parseInt(tds[ci].getAttribute('colspan') || '1', 10) > 3) { use = false; break; }
              }
            }
            if (use) { headerIndex = idx; break; }
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
              var key = headers[rj] || ('col' + rj);
              row[key] = rt;
              hasData = true;
            }
            if (hasData) rows.push(row);
          }

          if (headers.length > 0 && rows.length > 0) {
            tables.push({ caption: caption, headers: headers, rows: rows });
          }
        }

        // keyValuePairs: first table only, two-cell rows only.
        var keyValuePairs = {};
        if (tableEls.length > 0) {
          var ktrs = tableEls[0].querySelectorAll('tr');
          for (var ki = 0; ki < ktrs.length; ki++) {
            var kc = ktrs[ki].querySelectorAll('td');
            if (kc.length !== 2) continue;
            var k = squish(kc[0]);
            var v = txt(kc[1]);
            if (k && v && k !== v && k.indexOf('<') !== 0) keyValuePairs[k] = v;
          }
        }

        var messages = {};
        var warn = doc.querySelector('input#warning, input[name=warning]');
        var err = doc.querySelector('input#error, input[name=error]');
        var ok = doc.querySelector('input#success, input[name=success]');
        if (warn && warn.value) messages.warning = warn.value;
        if (err && err.value) messages.error = err.value;
        if (ok && ok.value) messages.success = ok.value;

        // APAAR's hasApaar heuristic needs two raw-body signals that survive parsing.
        var hasApaar = Object.keys(keyValuePairs).length > 0
          || tables.some(function (t) { return t.rows.length > 0; })
          || Object.keys(formFields).some(function (k) {
               var fv = formFields[k];
               return fv && fv.length > 4 && fv !== '-' && fv.indexOf('0') !== 0;
             })
          || /\\.pdf/i.test(raw)
          || /already uploaded|submitted successfully/i.test(raw);

        return JSON.stringify({
          ok: true, status: xhr.status, title: title,
          selectOptions: selectOptions, tables: tables,
          keyValuePairs: keyValuePairs, formFields: formFields,
          hiddenFields: hiddenFields, messages: messages, hasApaar: hasApaar
        });
        """
        )
    }

    // ── Group B — key/value label scan ─────────────────────────────────────
    // Three modules that walk label→value pairs instead of tables. See
    // docs/sep-30-2026/modules/group-b-key-value-scan.md

    /**
     * `studentsRecord/StudentProfileAllView`.
     *
     * Walks every `table tr` in the document (all tables, nested rows included), `td` only,
     * and takes **cell 0 as the label, cell 1 as the value**. The label is matched
     * uppercased and *without* whitespace collapsing, because VTOP indents some labels and
     * the original relied on that literal text.
     *
     * The `else if` order below is load-bearing: the first match wins, so the more specific
     * alternatives ("PROGRAM / BRANCH", "YEAR OF PASSING | PASSED") must stay ahead of the
     * looser ones.
     */
    fun fetchStudentProfile(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');

        // Label → value. Keys are lowercased with all whitespace stripped, matching parseAddress().
        var parseAddress = function(html) {
          var out = {};
          var parts = String(html).split(/<br\\s*\\/?>/i);
          for (var i = 0; i < parts.length; i++) {
            var frag = new DOMParser().parseFromString(parts[i], 'text/html');
            var t = (frag.body.textContent || '').replace(/\\s+/g, ' ').trim();
            if (!t) continue;
            var ci = t.indexOf(':');
            if (ci <= 0) continue;
            var k = t.substring(0, ci).toLowerCase().replace(/\\s+/g, '');
            var v = t.substring(ci + 1).trim();
            if (k) out[k] = v;
          }
          return out;
        };

        var out = {};
        var father = {}, mother = {}, proctor = {};
        var hasFather = false, hasMother = false, hasProctor = false;

        var rows = doc.querySelectorAll('table tr');
        for (var r = 0; r < rows.length; r++) {
          var tds = rows[r].querySelectorAll('td');
          if (tds.length < 2) continue;
          var label = tds[0].textContent || '';          // deliberately not collapsed
          var value = (tds[1].textContent || '').replace(/\\s+/g, ' ').trim();
          var L = label.toUpperCase();
          if (!L) continue;

          if (L.indexOf('APPLICATION NUMBER') !== -1) out.applicationNumber = value;
          else if (L.indexOf('STUDENT NAME') !== -1) out.name = value;
          else if (L.indexOf('DATE OF BIRTH') !== -1) out.dob = value;
          else if (L.indexOf('BLOOD GROUP') !== -1) out.bloodGroup = value;
          else if (L.indexOf('PROGRAM / BRANCH') !== -1 || L.indexOf('BRANCH') !== -1) out.branch = value;
          else if (L.indexOf('GENDER') !== -1) out.gender = value;
          else if (L.indexOf('HOSTEL') !== -1) {
            var hv = value.toUpperCase();
            out.isHosteller = hv === 'HOSTELLER' || hv === 'YES';
          }
          else if (L.indexOf('NATIVE LANGUAGE') !== -1) out.nativeLanguage = value;
          else if (L.indexOf('NATIVE STATE') !== -1) out.nativeState = value;
          else if (L.indexOf('PHYSICALLY CHALLENGED') !== -1) out.physicallyChallenged = value;
          else if (L.indexOf('COMMUNITY') !== -1) out.community = value;
          else if (L.indexOf('RELIGION') !== -1) out.religion = value;
          else if (L.indexOf('CASTE') !== -1) out.caste = value;
          else if (L.indexOf('NATIONALITY') !== -1) out.nationality = value;
          else if (L.indexOf('AADHAR') !== -1 || L.indexOf('AADHAAR') !== -1) out.aadharNumber = value;
          else if (L.indexOf('MOBILE NUMBER') !== -1) out.mobileNumber = value;
          else if (L.indexOf('FRIEND MOBILE') !== -1) out.friendMobileNumber = value;
          else if (L.indexOf('CURRENT ADDRESS') !== -1) out.currentAddress = parseAddress(tds[1].innerHTML);
          else if (L.indexOf('PERMANENT ADDRESS') !== -1) out.permanentAddress = parseAddress(tds[1].innerHTML);
          else if (L.indexOf('APPLIED DEGREE') !== -1) out.appliedDegree = value;
          else if (L.indexOf('EDUCATIONAL QUALIFICATION') !== -1) out.educationalQualification = value;
          else if (L.indexOf('BRANCH / GROUP STUDIED') !== -1) out.branchStudied = value;
          else if (L.indexOf('SCHOOL NAME') !== -1 || L.indexOf('SCHOOL/COLLEGE NAME') !== -1) out.schoolName = value;
          else if (L.indexOf('MEDIUM OF STUDY') !== -1) out.mediumOfStudy = value;
          else if (L.indexOf('BOARD') !== -1 || L.indexOf('UNIVERSITY') !== -1) out.boardUniversity = value;
          else if (L.indexOf('REGISTER NO') !== -1) out.registerNo = value;
          else if (L.indexOf('CLASS OBTAINED') !== -1) out.classObtained = value;
          else if (L.indexOf('YEAR OF PASSING') !== -1 || L.indexOf('PASSED') !== -1) out.yearOfPassing = value;
          else if (L.indexOf('MONTH OF PASSING') !== -1 || L.indexOf('PASSED') !== -1) out.monthOfPassing = value;
          else if (L.indexOf('SCHOOL / COLLEGE ADDRESS') !== -1 || L.indexOf('SCHOOL ADDRESS') !== -1) out.schoolAddress = value;
          else if (L.indexOf('BREAK IN STUDY') !== -1) out.breakInStudy = value;
          else if (L.indexOf('NO.OF.BROTHERS') !== -1 || L.indexOf('NO.OF BROTHERS') !== -1) out.brothers = value;
          else if (L.indexOf('NO.OF.SISTERS') !== -1 || L.indexOf('NO.OF SISTERS') !== -1) out.sisters = value;
          else if (L.indexOf('BROTHER/SISTER STUDYING') !== -1 || L.indexOf('SIBLING') !== -1) out.siblingInVIT = value;
          else if (L.indexOf('GUARDIAN') !== -1) out.guardian = value;
          else if (L.indexOf('FACULTY') !== -1 || L.indexOf('NAME') !== -1 || L.indexOf('DESIGNATION') !== -1
                   || L.indexOf('SCHOOL') !== -1 || L.indexOf('DEPARTMENT') !== -1 || L.indexOf('EMAIL') !== -1
                   || L.indexOf('INTERCOM') !== -1 || L.indexOf('PHONE') !== -1 || L.indexOf('MOBILE') !== -1) {
            // Bare NAME/MOBILE/EMAIL lines belong to whichever relative block is open.
            if (L.indexOf('FATHER') !== -1) { father[relativeField(L, false)] = value; hasFather = true; }
            else if (L.indexOf('MOTHER') !== -1) { mother[relativeField(L, true)] = value; hasMother = true; }
            else { proctor[proctorField(L)] = value; hasProctor = true; }
          }
          else if (L.indexOf('CABIN') !== -1) { proctor.cabin = value; hasProctor = true; }
          else if (L.indexOf('FATHER') !== -1 || L.indexOf('MOTHER') !== -1) {
            var rel = L.indexOf('FATHER') !== -1 ? father : mother;
            if (L.indexOf('FATHER') !== -1) hasFather = true; else hasMother = true;
            rel[relativeField(L, L.indexOf('MOTHER') !== -1)] = value;
          }
        }

        function relativeField(l, isMother) {
          var w = isMother ? 'MOTHER' : 'FATHER';
          if (l.indexOf(w + ' NAME') !== -1) return 'name';
          if (l.indexOf('QUALIFICATION') !== -1) return 'qualification';
          if (l.indexOf('OCCUPATION') !== -1) return 'occupation';
          if (l.indexOf('ORGANISATION') !== -1 || l.indexOf('ORGANIZATION') !== -1) return 'organisation';
          if (l.indexOf('MOBILE') !== -1) return 'mobile';
          if (l.indexOf('EMAIL') !== -1) return 'email';
          if (l.indexOf('ANNUAL INCOME') !== -1) return 'annualIncome';
          if (l.indexOf('DESIGNATION') !== -1) return 'designation';
          if (l.indexOf('ADDRESS') !== -1) return 'address';
          return '';
        }

        // proctor flavour of camelCase: lowercase, then any non-alphanumeric run plus the
        // next character collapses to an uppercase character, and the first char is lowered.
        function proctorField(l) {
          var known = {
            'FACULTY NAME': 'name', 'NAME': 'name',
            'FACULTY EMAIL': 'email', 'EMAIL': 'email',
            'FACULTY MOBILE': 'phone', 'MOBILE NUMBER': 'phone',
            'FACULTY DESIGNATION': 'designation', 'DESIGNATION': 'designation'
          };
          if (known[l]) return known[l];
          var s = l.toLowerCase().replace(/[^a-z0-9]+([a-z0-9])/g, function(m, c) { return c.toUpperCase(); });
          return s.charAt(0).toLowerCase() + s.slice(1);
        }

        if (hasFather) out.father = father;
        if (hasMother) out.mother = mother;
        if (hasProctor) out.proctor = proctor;

        // Photo is a regex over the raw response, not the DOM.
        var photo = null;
        var m1 = res.match(/src="(data:[^"]+base64,[^"]+)"/i);
        if (m1) photo = m1[1];
        else {
          var m2 = res.match(/src="(data:image[^"]+)"/i);
          if (m2) photo = m2[1];
        }
        if (photo) out.photo = photo;

        return JSON.stringify({ ok: true, status: xhr.status, profile: out });
        """
        )
    }

    /**
     * `studentBankInformation/BankInfoStudent`.
     *
     * Two passes over the form, both scoped to `#bankInfoStudentForm`: one to label every
     * named control from its label row, one to collect the controls themselves in document
     * order. Field keys are VTOP's own dynamic `name` attributes, so order is what matters.
     * Title is `h3.box-title` — note the missing ` b`, unlike proctor/credentials.
     */
    fun fetchBankInfo(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var titleEl = doc.querySelector('h3.box-title');
        var title = titleEl ? (titleEl.textContent || '').replace(/\\s+/g, ' ').trim() : '';

        var form = doc.querySelector('#bankInfoStudentForm');
        var fields = {};
        if (form) {
          // Pass 1 — label the controls.
          var fieldLabels = {};
          var rows = form.querySelectorAll('table tr');
          for (var r = 0; r < rows.length; r++) {
            var td = rows[r].querySelector('td');
            if (!td) continue;
            var label = (td.textContent || '').replace(/\\s+/g, ' ').trim();
            var ctrl = rows[r].querySelector('input, select');
            if (!ctrl) continue;
            var nm = ctrl.getAttribute('name');
            if (nm) fieldLabels[nm] = label;
          }

          // Pass 2 — every named input/select in document order, no type filtering.
          // A checkbox's `checked` is deliberately ignored: value is attribute-only.
          var ctrls = form.querySelectorAll('input, select');
          for (var c = 0; c < ctrls.length; c++) {
            var el = ctrls[c];
            var name = el.getAttribute('name');
            if (!name) continue;
            var label = fieldLabels[name] || '';
            if (el.tagName === 'SELECT') {
              var opts = [];
              var os = el.querySelectorAll('option');
              for (var o = 0; o < os.length; o++) {
                opts.push({
                  value: os[o].getAttribute('value') || '',
                  selected: os[o].getAttribute('selected') !== null,
                  text: (os[o].textContent || '').trim()
                });
              }
              fields[name] = { type: 'select', value: el.value || '', label: label, options: opts };
            } else {
              fields[name] = {
                type: el.getAttribute('type') || 'text',
                value: el.getAttribute('value') || '',
                label: label
              };
            }
          }
        }

        // Bank details: every <b> inside the fragment, read positionally.
        var bankDetails = null;
        var frag = doc.querySelector('#ifscCodeBkFrag');
        if (frag) {
          var bs = frag.querySelectorAll('b');
          if (bs.length > 0) {
            var pick = function(i) {
              return i < bs.length ? (bs[i].textContent || '').replace(/\\s+/g, ' ').trim() : null;
            };
            bankDetails = { bankName: pick(0), branch: pick(1), address: pick(2) };
          }
        }

        return JSON.stringify({ ok: true, status: xhr.status, title: title, fields: fields, bankDetails: bankDetails });
        """
        )
    }

    /**
     * PROFILE_IMAGES — three POSTs against the same session, run **sequentially** because the
     * WebView script is synchronous and overlapping requests would interleave.
     *
     * The HOD/Dean role lookup is the awkward part: the original combined selector
     * `h3.box-title b, h3.box-title` yields `[h3#1, b#1, h3#2, b#2, …]`, and table `i` reads
     * index `i + 1` of that list. Reproduced here as two interleaved arrays.
     */
    fun fetchProfileImages(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var post = function(url) {
          var x = new XMLHttpRequest();
          x.open('POST', url, false);
          x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
          x.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
          x.send($b);
          return { status: x.status, text: x.responseText || '' };
        };

        var clean = function(s) { return (s || '').replace(/\\s+/g, ' ').trim(); };

        // proctor flavour of camelCase: lowercase, collapse non-alphanumerics into an
        // uppercase boundary, then lowercase the leading character.
        function camel(s) {
          var x = s.toLowerCase().replace(/[^a-z0-9]+([a-z0-9])/g, function(m, c) { return c.toUpperCase(); });
          return x.charAt(0).toLowerCase() + x.slice(1);
        }

        // Shared reader for the label/value tables on proctor and hod/dean.
        // `rejectAngle` drops rows whose label or value still contains markup.
        function readPairs(table, rejectAngle) {
          var out = {};
          var rows = table.querySelectorAll('tr');
          for (var r = 0; r < rows.length; r++) {
            var cells = rows[r].querySelectorAll('td');
            if (cells.length < 2) continue;
            var k = clean(cells[0].textContent).toUpperCase();
            var v = (cells[1].textContent || '').trim();
            if (rejectAngle && (k.indexOf('<') !== -1 || v.indexOf('<') !== -1)) continue;
            if (!k) continue;
            out[k] = v;
          }
          return out;
        }

        var docP = new DOMParser().parseFromString(post($p).text, 'text/html');
        var proctorTable = docP.querySelector('table.table');
        var proctor = {};
        var proctorPhoto = null;
        var proctorTitle = '';
        var pTitle = docP.querySelector('h3.box-title b');
        if (pTitle) proctorTitle = clean(pTitle.textContent);
        if (proctorTable) {
          var img = proctorTable.querySelector('img[src^="data:"]');
          if (img) proctorPhoto = img.getAttribute('src');
          var pairs = readPairs(proctorTable, false);
          for (var pk in pairs) {
            var l = pk, v = pairs[pk];
            if (l === 'FACULTY NAME') proctor.name = v;
            else if (l === 'FACULTY EMAIL') proctor.email = v;
            else if (l === 'FACULTY MOBILE' || l === 'MOBILE NUMBER') proctor.phone = v;
            else if (l === 'FACULTY DESIGNATION' || l === 'DESIGNATION') proctor.designation = v;
            else proctor[camel(l)] = v;
          }
        }

        var hodUrl = '/vtop/hrms/viewHodDeanDetails';
        var docH = new DOMParser().parseFromString(post(hodUrl).text, 'text/html');
        // Combined-selector semantics: h3 and its inner b alternate, so table i takes index i + 1.
        var combined = [];
        var h3s = docH.querySelectorAll('h3.box-title');
        for (var hi = 0; hi < h3s.length; hi++) {
          combined.push(h3s[hi]);
          var inner = h3s[hi].querySelector('b');
          if (inner) combined.push(inner);
        }
        var hodTables = docH.querySelectorAll('table');
        var firstH3 = docH.querySelector('h3.box-title');
        var hodDeanTitle = firstH3 ? clean(firstH3.textContent) : '';
        var hodDean = [];
        for (var ti = 0; ti < hodTables.length; ti++) {
          var roleEl = combined[ti + 1];
          var role = roleEl ? clean(roleEl.textContent) : ('Person ' + (ti + 1));
          var photo = null;
          var hi2 = hodTables[ti].querySelector('img[src^="data:"]');
          if (hi2) photo = hi2.getAttribute('src');
          var entry = { role: role, photoBase64: photo, details: {} };
          var hp = readPairs(hodTables[ti], true);
          for (var hk in hp) {
            var l2 = hk, v2 = hp[hk];
            if (l2 === 'NAME OF THE FACULTY' || l2 === 'NAME OF THE DEAN') entry.details.name = v2;
            else if (l2 === 'EMAIL') entry.details.email = v2;
            else if (l2 === 'MOBILE' || l2 === 'PHONE') entry.details.mobile = v2;
            else if (l2 === 'DESIGNATION') entry.details.designation = v2;
            else if (l2 === 'CABIN') entry.details.cabin = v2;
            else entry.details[camel(l2)] = v2;
          }
          hodDean.push(entry);
        }

        var credUrl = '/vtop/proctor/viewStudentCredentials';
        var docC = new DOMParser().parseFromString(post(credUrl).text, 'text/html');
        var credentials = $CREDENTIALS_JS

        return JSON.stringify({
          ok: true,
          proctor: { title: proctorTitle, photoBase64: proctorPhoto, details: proctor },
          hodDean: { title: hodDeanTitle, people: hodDean },
          credentials: credentials
        });
        """
        )
    }

    // ── Group C — table extractors ──────────────────────────────────────────

    /**
     * `examinations/doSearchExamScheduleForStudent`.
     *
     * Three gates must all pass before a row yields an item, and their order matters:
     * a single `td` with `colspan="13"` is a *section header* that re-labels every row after
     * it; `tableHeader` rows are dropped; and rows appearing before the first section header are
     * discarded because no exam type is known yet.
     *
     * Column 3 is never read, and column 0 (S.No) is skipped. The response uses uppercase
     * `Schedule` only, which `ExamScheduleRes` expects.
     */
    fun fetchExamSchedule(path: String, body: String, semesterId: String): String {
        val p = jsString(path)
        val b = jsString(body)
        val sem = jsString(semesterId)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var schedule = {};
        var currentExamType = null;

        var rows = doc.querySelectorAll('table.customTable tr');
        for (var r = 0; r < rows.length; r++) {
          var row = rows[r];
          var tds = row.querySelectorAll('td');

          // 1. Section header.
          if (tds.length === 1 && tds[0].getAttribute('colspan') === '13') {
            currentExamType = (tds[0].textContent || '').trim();
            continue;
          }
          // 2. Header row.
          if (row.className && row.className.indexOf('tableHeader') !== -1) continue;
          // 3. Nothing before the first section header.
          if (!currentExamType) continue;

          // 4. Data row.
          if (row.className && row.className.indexOf('tableContent') !== -1 && tds.length > 1) {
            var cell = function(i) { return i < tds.length ? (tds[i].textContent || '').trim() : ''; };
            if (!schedule[currentExamType]) schedule[currentExamType] = [];
            schedule[currentExamType].push({
              courseCode: cell(1),
              courseTitle: cell(2),
              classId: cell(4),
              slot: cell(5),
              examDate: cell(6),
              examSession: cell(7),
              reportingTime: cell(8),
              examTime: cell(9),
              venue: cell(10),
              seatLocation: cell(11),
              seatNo: cell(12)
            });
          }
        }

        return JSON.stringify({ ok: true, status: xhr.status, semester: $sem, Schedule: schedule });
        """
        )
    }

    /**
     * `admissions/costCentreCircularsViewPageController`.
     *
     * A recursive walk of `#tree1`. Only **direct** `li` children are visited — a `find` would
     * flatten the tree and duplicate every descendant under its ancestors. `li` with neither a
     * direct `<a>` nor a direct `<span>` is dropped silently.
     */
    fun fetchCirculars(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var h3 = doc.querySelector('h3');
        var title = h3 ? (h3.textContent || '').trim() : '';

        // Direct children only: element.children, never querySelectorAll.
        var parseUl = function(ul) {
          var out = [];
          var lis = ul.children;
          for (var i = 0; i < lis.length; i++) {
            var li = lis[i];
            if (li.tagName !== 'LI') continue;
            var link = null, span = null;
            for (var c = 0; c < li.children.length; c++) {
              if (!link && li.children[c].tagName === 'A') link = li.children[c];
              if (!span && li.children[c].tagName === 'SPAN') span = li.children[c];
            }
            if (link) {
              // VTOP emits both viewCertificate('123') and viewCertificate(123).
              var onclick = link.getAttribute('onclick') || '';
              var m = onclick.match(/viewCertificate\\(['"]?([^'")\\s]+)['"]?\\)/);
              out.push({ id: m ? m[1] : null, title: (link.textContent || '').trim() });
            } else if (span) {
              var childUl = null;
              for (var d = 0; d < li.children.length; d++) {
                if (li.children[d].tagName === 'UL') { childUl = li.children[d]; break; }
              }
              out.push({ name: (span.textContent || '').trim(), children: childUl ? parseUl(childUl) : [] });
            }
            // else: dropped
          }
          return out;
        };

        var tree = doc.querySelector('#tree1');
        var circulars = tree ? parseUl(tree) : [];

        return JSON.stringify({ ok: true, status: xhr.status, title: title, circulars: circulars });
        """
        )
    }

    /**
     * `proctor/viewStudentCredentials` as a standalone module.
     *
     * Same parse as the PROFILE_IMAGES leg, but the response **spreads** it
     * (`{success, title, credentials, ranks}`) instead of nesting it under `credentials`.
     */
    fun fetchCredentials(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var credentials = $CREDENTIALS_JS

        return JSON.stringify({ ok: true, status: xhr.status, title: credentials.title,
          credentials: credentials.credentials, ranks: credentials.ranks });
        """
        )
    }

    /**
     * `processViewCalendar`, one POST per month, **sequential** because the WebView script is
     * synchronous and overlapping requests would interleave.
     *
     * Emits a bare array of `{month, days}` so `AnalyzeCalendar.analyzeAllCalendars` can consume
     * it unchanged — that keeps the LOCAL path byte-identical to the REMOTE one instead of
     * bypassing the shared post-processing.
     *
     * Calendar cell layout: `span` 0 is the day number (empty means skip the day) and spans 1..n
     * are the events. Days with no events are dropped entirely.
     */
    fun fetchCalendar(
        path: String,
        body: String,
        semSubId: String,
        classGroupId: String,
        calDates: List<String>
    ): String {
        val p = jsString(path)
        val b = jsString(body)
        val sem = jsString(semSubId)
        val cg = jsString(classGroupId)
        val datesJson = calDates.joinToString(",", "[", "]") { jsString(it) }
        return wrapped(
            """
        var out = [];
        var dates = $datesJson;

        for (var mi = 0; mi < dates.length; mi++) {
          var calDate = dates[mi];
          var postBody = $b + '&semSubId=' + encodeURIComponent($sem)
                       + '&calDate=' + encodeURIComponent(calDate)
                       + '&classGroupId=' + encodeURIComponent($cg)
                       + '&x=' + Date.now();

          var xhr = new XMLHttpRequest();
          xhr.open('POST', $p, false);
          xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
          xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
          xhr.send(postBody);
          if (xhr.status === 0) continue;
          var res = xhr.responseText || '';
          if (res.toLowerCase().indexOf('not authorized') !== -1) continue;

          var doc = new DOMParser().parseFromString(res, 'text/html');
          var h4 = doc.querySelector('h4');
          var month = h4 ? (h4.textContent || '').trim() : '';

          var days = [];
          var cells = doc.querySelectorAll('table.calendar-table tbody tr td');
          for (var ci = 0; ci < cells.length; ci++) {
            var spans = cells[ci].querySelectorAll('span');
            if (spans.length === 0) continue;
            var dayText = (spans[0].textContent || '').replace(/\s+/g, ' ').trim();
            if (!dayText) continue;
            var date = parseInt(dayText, 10);
            if (isNaN(date)) continue;

            var events = [];
            for (var si = 1; si < spans.length; si++) {
              var el = spans[si];
              var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
              if (!text) continue;
              var color = null;
              var style = el.getAttribute('style') || '';
              var cm = style.match(/color:\s*([^;]+)/);
              if (cm) color = cm[1].trim();
              var lower = text.toLowerCase();
              var type = lower.indexOf('instructional') !== -1 ? 'Instructional Day'
                       : lower.indexOf('holiday') !== -1 ? 'Holiday'
                       : 'Other';
              var cat = text.match(/\(([^)]+)\)/);
              events.push({
                type: type,
                text: text,
                color: color,
                category: cat ? cat[1] : 'General'
              });
            }
            // Days with no events are dropped.
            if (events.length > 0) days.push({ date: date, events: events });
          }

          out.push({ month: month, days: days });
        }

        return JSON.stringify({ ok: true, calendars: out });
        """
        )
    }

    // ── Group D — curriculum ────────────────────────────────────────────────

    /**
     * `academics/common/Curriculum` — stage 1, the category list.
     *
     * `.categoty-card` is VTOP's own spelling of "category-card"; reproducing the typo is the
     * only way to match the markup.
     *
     * cheerio's `:contains()` has no CSS equivalent, so "does any `small` under this card contain
     * `Credit:`" becomes an explicit scan rather than a selector.
     */
    fun fetchCurriculumCategories(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        var h3 = doc.querySelector('h3');
        var title = h3 ? (h3.textContent || '').trim() : '';

        // :contains('Total Credits:') — no CSS equivalent, so scan the spans.
        var totalCredits = 0;
        var spans = doc.querySelectorAll('span');
        for (var si = 0; si < spans.length; si++) {
          var st = (spans[si].textContent || '').replace(/\s+/g, ' ').trim();
          if (st.indexOf('Total Credits:') === -1) continue;
          var m = st.match(/Total Credits:\s*(\d+)/);
          if (m) totalCredits = parseInt(m[1], 10);
          break;
        }

        // The stage-2 csrf is scraped from THIS page, not the login one.
        var csrfEl = doc.querySelector('input[name="_csrf"]');
        var pageCsrf = csrfEl ? (csrfEl.getAttribute('value') || '') : '';

        var categories = [], details = [];
        var cards = doc.querySelectorAll('.categoty-card');
        for (var ci = 0; ci < cards.length; ci++) {
          var card = cards[ci];

          var onclickEl = card.querySelector('[onclick]');
          var onclick = onclickEl ? (onclickEl.getAttribute('onclick') || '') : '';
          var cm = onclick.match(/categoryOnClick\('([^']+)'\)/);
          var code;
          if (cm) {
            code = cm[1];
          } else {
            var sym = card.querySelector('.symbol-label');
            code = sym ? (sym.textContent || '').trim().split('\n')[0].trim() : '';
          }

          var nameEl = card.querySelector('.text-sm');
          var name = nameEl ? (nameEl.textContent || '').trim() : '';

          // The credit figures live on a <small> but are read from its parent's text.
          var credit = 0, maxCredit = 0;
          var smalls = card.querySelectorAll('small');
          for (var k = 0; k < smalls.length; k++) {
            var parentText = smalls[k].parentElement ? (smalls[k].parentElement.textContent || '') : '';
            if (parentText.indexOf('Credit:') !== -1) {
              var cm2 = parentText.match(/Credit:\s*(\d+)/);
              if (cm2) credit = parseInt(cm2[1], 10);
            }
            if (parentText.indexOf('Max. Credit:') !== -1) {
              var mm = parentText.match(/Max\.\s*Credit:\s*(\d+)/);
              if (mm) maxCredit = parseInt(mm[1], 10);
            }
          }

          categories.push({ code: code, name: name, credits: credit, maxCredits: maxCredit });
          details.push({ code: code, name: name, baskets: [] });
        }

        return JSON.stringify({
          ok: true, status: xhr.status, title: title, totalCredits: totalCredits,
          pageCsrf: pageCsrf, categories: categories, details: details
        });
        """
        )
    }

    /**
     * `academics/common/curriculumCategoryView` — stage 2, run once per category **sequentially**.
     *
     * Two things differ from every other route: the body carries the stage-1 `pageCsrf`, and `x`
     * is a UTC date *string* rather than epoch milliseconds.
     *
     * Table discovery runs four strategies and stops at the first that yields anything, so a page
     * laid out as Bootstrap tabs never gets double-counted by the card strategy.
     */
    fun fetchCurriculumCategory(path: String, pageCsrf: String, categoryCode: String): String {
        val p = jsString(path)
        val csrf = jsString(pageCsrf)
        val cat = jsString(categoryCode)
        return wrapped(
            """
        var postBody = '_csrf=' + encodeURIComponent($csrf)
                     + '&categoryId=' + encodeURIComponent($cat)
                     + '&x=' + encodeURIComponent(new Date().toUTCString());

        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send(postBody);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');

        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function rows(table) { return table ? table.querySelectorAll('tr') : []; }

        /**
         * Columns are located by regex over the header labels rather than by position, because
         * VTOP reshuffles them between category layouts. DataTables responsive detail rows
         * (`.dtr-details`) take priority over cells because they survive responsive collapsing.
         */
        function parseTable(table, title) {
          var items = [];

          var dtrRows = {};
          var dtrLists = table.querySelectorAll('ul.dtr-details, .dtr-details');
          for (var d = 0; d < dtrLists.length; d++) {
            var ul = dtrLists[d];
            var tr = ul.closest ? ul.closest('tr') : null;
            var rowIdx = tr ? parseInt(tr.getAttribute('data-dt-row') || '-1', 10) : -1;
            if (!(rowIdx >= 0)) continue;
            if (!dtrRows[rowIdx]) dtrRows[rowIdx] = {};
            var lis = ul.querySelectorAll('li');
            for (var li = 0; li < lis.length; li++) {
              var colIdx = parseInt(lis[li].getAttribute('data-dtr-index') || '-1', 10);
              var tEl = lis[li].querySelector('.dtr-title');
              var vEl = lis[li].querySelector('.dtr-data');
              if (colIdx >= 0) dtrRows[rowIdx][squish(tEl).toLowerCase()] = squish(vEl);
            }
          }

          var allRows = rows(table);
          var headerRow = allRows[0];
          var firstRowIsHeader = false;
          if (headerRow) {
            var ths = headerRow.querySelectorAll('th');
            var tds = headerRow.querySelectorAll('td');
            firstRowIsHeader = ths.length > 0 || (tds.length === 1 && tds[0].getAttribute('colspan'));
          }

          // colspan cells are skipped while collecting headers; at most 10 are kept.
          var headers = [];
          if (headerRow) {
            var cells = headerRow.querySelectorAll('th, td');
            for (var c = 0; c < cells.length && headers.length < 10; c++) {
              var text = squish(cells[c]);
              if (text && !cells[c].getAttribute('colspan')) headers.push(text);
            }
          }

          var codeIdx = -1, nameIdx = -1, creditIdx = -1, typeIdx = -1;
          for (var h = 0; h < headers.length; h++) {
            var hl = headers[h].toLowerCase();
            if (/course\s*(code|no)|s\.?no|#|code|paper\s*code/i.test(hl)) codeIdx = h;
            else if (/course\s*name|subject|title|paper/i.test(hl)) nameIdx = h;
            else if (/credit|cr/i.test(hl)) creditIdx = h;
            else if (/type|category|mode/i.test(hl)) typeIdx = h;
          }

          for (var ri = 0; ri < allRows.length; ri++) {
            if (ri === 0 && firstRowIsHeader) continue;
            var tds2 = allRows[ri].querySelectorAll('td');
            if (tds2.length < 1) continue;
            if (tds2.length === 1 && tds2[0].getAttribute('colspan')) continue;

            var texts = [];
            for (var q = 0; q < tds2.length; q++) texts.push(squish(tds2[q]));

            var courseCode = '', courseName = '', courseType = '', creditsStr = '';

            var dtrIdx = parseInt(allRows[ri].getAttribute('data-dt-row') || '-1', 10);
            var dtrData = dtrIdx >= 0 ? dtrRows[dtrIdx] : null;
            if (dtrData) {
              for (var key in dtrData) {
                if (/course\s*(code|no)|code/i.test(key)) courseCode = dtrData[key];
                else if (/course\s*name|subject|paper/i.test(key)) courseName = dtrData[key];
                else if (/credit|cr/i.test(key)) creditsStr = dtrData[key];
                else if (/type|category|mode/i.test(key)) courseType = dtrData[key];
              }
            }

            if (!courseCode && codeIdx >= 0 && texts[codeIdx]) courseCode = texts[codeIdx];
            if (!courseName && nameIdx >= 0 && texts[nameIdx]) courseName = texts[nameIdx];
            if (!creditsStr && creditIdx >= 0 && texts[creditIdx]) creditsStr = texts[creditIdx];
            if (!courseType && typeIdx >= 0 && texts[typeIdx]) courseType = texts[typeIdx];

            // Auto-detect when the header mapping found nothing usable.
            if (!courseCode && !courseName) {
              if (texts[0] && /^[A-Z0-9]{2,}$/.test(texts[0].replace(/\s/g, ''))) courseCode = texts[0];
              if (!courseName && texts.length >= 2) courseName = texts[1] || texts[0] || '';
              if (!courseCode && !courseName) continue;
            }
            if (!creditsStr) {
              for (var t = 0; t < texts.length; t++) {
                if (/^\d+(\.\d+)?$/.test(texts[t])) { creditsStr = texts[t]; break; }
              }
            }

            var credits = parseFloat(creditsStr) || 0;
            items.push({
              code: courseCode, name: courseName, credits: credits,
              type: courseType || null
            });
          }

          var total = 0;
          for (var s = 0; s < items.length; s++) total += items[s].credits;
          return { title: title || 'Courses', credits: total, items: items };
        }

        var baskets = [];
        var seenTitles = {};

        // Strategy 1 — Bootstrap tabs.
        var tabButtons = doc.querySelectorAll('.nav-tabs button, .nav-tabs a, [role=tab]');
        for (var tb = 0; tb < tabButtons.length; tb++) {
          var btn = tabButtons[tb];
          var tabTitle = squish(btn);
          if (!tabTitle) continue;
          var targetId = btn.getAttribute('data-bs-target') || btn.getAttribute('href') || '';
          var paneId = targetId.replace(/^#/, '');
          var pane = null;
          if (paneId) pane = doc.getElementById(paneId);
          if (!pane) {
            var panes = doc.querySelectorAll('.tab-pane');
            pane = panes[tb] || null;
          }
          if (!pane) continue;
          var t1 = pane.querySelector('table');
          if (t1 && rows(t1).length > 1) baskets.push(parseTable(t1, tabTitle));
        }

        // Strategy 2 — cards / panels.
        if (baskets.length === 0) {
          var cards = doc.querySelectorAll('.card, .panel, [class*=card]');
          for (var cd = 0; cd < cards.length; cd++) {
            var el = cards[cd];
            var headerText = '';
            var kids = el.children;
            for (var kk = 0; kk < kids.length; kk++) {
              var cn = kids[kk].className || '';
              if (cn.indexOf('card-header') !== -1 || cn.indexOf('panel-heading') !== -1
                  || cn.indexOf('card-heading') !== -1 || cn === 'header'
                  || (typeof cn === 'string' && /^\\s*header\\s*$/.test(cn))) {
                headerText = squish(kids[kk]); break;
              }
            }
            var table2 = null;
            for (var kb = 0; kb < kids.length; kb++) {
              var kcn = kids[kb].className || '';
              if (kcn.indexOf('card-body') !== -1 || kcn.indexOf('panel-body') !== -1
                  || kcn === 'body' || /^\\s*body\\s*$/.test(kcn)) {
                table2 = kids[kb].querySelector('table'); break;
              }
            }
            if (!table2) table2 = el.querySelector('table');
            if (table2 && rows(table2).length > 1) {
              baskets.push(parseTable(table2, headerText || ('Group ' + (baskets.length + 1))));
            }
          }
        }

        // Strategy 3 — a heading followed by its table.
        if (baskets.length === 0) {
          var heads = doc.querySelectorAll('h4, h5, h6, strong.heading, .section-title, .group-label');
          for (var hd = 0; hd < heads.length; hd++) {
            var hEl = heads[hd];
            var hTitle = squish(hEl);
            if (!hTitle) continue;
            var table3 = null;
            var sib = hEl.nextElementSibling;
            while (sib) {
              if (sib.tagName === 'TABLE') { table3 = sib; break; }
              sib = sib.nextElementSibling;
            }
            if (!table3 && hEl.parentElement) table3 = hEl.parentElement.querySelector('table');
            if (table3 && rows(table3).length > 1 && !seenTitles[hTitle]) {
              seenTitles[hTitle] = true;
              baskets.push(parseTable(table3, hTitle));
            }
          }
        }

        // Strategy 4 — any table at all.
        if (baskets.length === 0) {
          var allTables = doc.querySelectorAll('table');
          for (var at = 0; at < allTables.length; at++) {
            var t4 = allTables[at];
            if (rows(t4).length <= 1) continue;
            var cap = t4.querySelector('caption');
            var tTitle = cap ? squish(cap) : '';
            if (!tTitle) {
              var firstCells = rows(t4)[0] ? rows(t4)[0].querySelectorAll('td, th') : [];
              if (firstCells.length === 1 && firstCells[0].getAttribute('colspan')) {
                tTitle = squish(firstCells[0]);
              }
            }
            if (!tTitle && t4.previousElementSibling) tTitle = squish(t4.previousElementSibling);
            baskets.push(parseTable(t4, tTitle || ('Basket ' + (baskets.length + 1))));
          }
        }

        return JSON.stringify({ ok: true, status: xhr.status, baskets: baskets });
        """
        )
    }

    // ── Group E — other hosts ───────────────────────────────────────────────
    // None of these can be fetched *from* the WebView: cross-origin XHR to lms.vit.ac.in or
    // eventhubcc.vit.ac.in would be blocked by CORS, so they would never get a readable
    // response. The split is therefore: Ktor does the network, the WebView only supplies a DOM.

    /**
     * Runs [body] against [html] with `doc` already bound to a parsed document.
     *
     * The HTML arrives as a JSON string literal, so it is decoded rather than injected as source.
     * No network happens here — this only exists for `DOMParser`.
     */
    fun parseInjectedHtml(html: String, body: String): String {
        val h = jsString(html)
        return wrapped(
            """
        var doc = new DOMParser().parseFromString($h, 'text/html');
        $body
        """
        )
    }

    /**
     * `eventhubcc.vit.ac.in/EventHub/` — public, no auth.
     *
     * The metadata fields are sniffed out of Font Awesome class names found in each `div`'s
     * *inner* HTML. The original runs an `else if` chain per div, so for a given field the
     **last** matching div wins — reproduced here by overwriting rather than first-write.
     */
    fun parseEventHubEvents(html: String): String {
        return parseInjectedHtml(
            html = html,
            body = """
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        var BASE = 'https://eventhubcc.vit.ac.in';
        var PAGE = 'https://eventhubcc.vit.ac.in/EventHub/';
        var events = [];
        var cards = doc.querySelectorAll('#events .card');

        for (var ci = 0; ci < cards.length; ci++) {
          var card = cards[ci];
          var titleEl = card.querySelector('.card-title span');
          var title = titleEl ? squish(titleEl) : '';
          // eid lives on a button's value attribute, not on a link href.
          var eidEl = card.querySelector('button[name="eid"]');
          var eid = eidEl ? (eidEl.getAttribute('value') || '') : '';
          if (!title || !eid) continue;

          var poster = null;
          var outer = card.outerHTML || '';
          var inner = card.innerHTML || '';
          var m = (outer + ' ' + inner).match(/<img[^>]+src=["']([^"']+)["']/i);
          if (m) {
            poster = m[1];
          } else {
            var bg = (outer + ' ' + inner).match(/background(?:-image)?\s*:\s*url\(['"]?([^'")]+)['"]?\)/i);
            if (bg) poster = bg[1];
          }
          if (poster && poster.indexOf('data:') !== 0) {
            if (poster.charAt(0) === '/') poster = BASE + poster;
            else poster = PAGE + poster;
          }

          var eligibility = '', date = '', location = '', price = '', type = '';
          var divs = card.querySelectorAll('div');
          for (var di = 0; di < divs.length; di++) {
            var html2 = divs[di].innerHTML || '';
            var text = squish(divs[di]);
            var paren = text.match(/\(([^)]+)\)/);
            var val = paren ? squish(paren[1]) : '';
            if (html2.indexOf('fa-people-carry-box') !== -1 || html2.indexOf('fa-user-large') !== -1) {
              eligibility = val; if (paren) type = val;
            } else if (html2.indexOf('fa-calendar-days') !== -1) {
              date = val;
            } else if (html2.indexOf('fa-map-location-dot') !== -1) {
              location = val;
            } else if (html2.indexOf('fa-indian-rupee-sign') !== -1) {
              price = val;
            }
          }

          events.push({
            eid: eid, title: title, eligibility: eligibility, type: type,
            date: date, location: location, price: price, posterUrl: poster
          });
        }

        return JSON.stringify({ ok: true, events: events });
        """
        )
    }

    /**
     * `/EventHub/profile` — authenticated.
     *
     * A table only counts if its first row mentions `event` **and** one of
     * `order` / `payment` / `receipt`. Data rows need at least 8 cells; the tail cells are scanned
     * for controls whose text names the action (`receipt`, `pay now`, `pay later`, …).
     */
    fun parseEventHubProfile(html: String): String {
        return parseInjectedHtml(
            html = html,
            body = """
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        var events = [];
        var tables = doc.querySelectorAll('table');

        for (var ti = 0; ti < tables.length; ti++) {
          var rows = tables[ti].querySelectorAll('tr');
          if (rows.length === 0) continue;
          var head = squish(rows[0]).toLowerCase();
          if (head.indexOf('event') === -1) continue;
          if (head.indexOf('order') === -1 && head.indexOf('payment') === -1 && head.indexOf('receipt') === -1) continue;

          for (var ri = 1; ri < rows.length; ri++) {
            var tds = rows[ri].querySelectorAll('td');
            if (tds.length < 8) continue;
            var cell = function(i) { return i < tds.length ? squish(tds[i]) : ''; };

            var eid = '', receiptLink = null, certificateLink = null,
                payNowLink = null, payLaterLink = null;

            // Column 7 onwards carries the action controls.
            for (var ci = 7; ci < tds.length; ci++) {
              var ctrls = tds[ci].querySelectorAll('button, a, input');
              for (var k = 0; k < ctrls.length; k++) {
                var el = ctrls[k];
                var text = squish(el).toLowerCase();
                var onclick = el.getAttribute('onclick') || '';
                var href = el.getAttribute('href') || el.getAttribute('formaction') || '';
                if (href === '#') href = '';

                if (!eid) {
                  var m1 = onclick.match(/getRecepit\(['"]?([^'")\s]+)['"]?\)/);
                  if (m1) eid = m1[1];
                  else {
                    var m2 = href.match(/studentRecepit\/([^/?#]+)/);
                    if (m2) eid = m2[1];
                    else {
                      var m3 = onclick.match(/paynow\(['"]?([^'")\s]+)['"]?\)/);
                      if (m3) eid = m3[1];
                    }
                  }
                }
                if (!receiptLink && text.indexOf('receipt') !== -1) {
                  receiptLink = href || ('/EventHub/studentRecepit/' + eid + '/');
                }
                if (!certificateLink && (text.indexOf('certificate') !== -1 || text.indexOf('download') !== -1)) {
                  certificateLink = href || null;
                }
                if (!payNowLink && (text.indexOf('pay now') !== -1 || onclick.indexOf('paynow') !== -1)) {
                  var m4 = onclick.match(/window\.location\.href\s*=\s*['"]([^'"]+)['"]/);
                  payNowLink = href || (m4 ? m4[1] : null);
                }
                if (!payLaterLink && text.indexOf('pay later') !== -1) {
                  payLaterLink = href || null;
                }
              }
            }

            events.push({
              name: cell(1), eid: eid, date: cell(3), venue: cell(4),
              time: cell(5), paymentStatus: cell(6),
              receiptLink: receiptLink, certificateLink: certificateLink,
              payNowLink: payNowLink, payLaterLink: payLaterLink
            });
          }
        }

        return JSON.stringify({ ok: true, events: events });
        """
        )
    }

    /**
     * Moodle month view → the list of event links to visit, plus the two month-paging links.
     *
     * Returns absolute URLs so the Kotlin side can fetch each one without re-resolving them.
     *
     * The month/year live on the cell's `a[data-action="view-day-link"]` (and on the wrapping
     * `.calendarwrapper`), **not** on the `a[data-action="view-event"]` that carries the href.
     * Reading them off the event link yields empty strings, which silently drops every due date
     * and with it every reminder — so they are read from the day link, then the wrapper.
     *
     * `prevMonthUrl` / `nextMonthUrl` come from the calendar's own arrows, which is the only
     * reliable way to reach adjacent months: the paging URL needs a `time` epoch parameter.
     */
    fun parseLmsCalendar(html: String): String {
        return parseInjectedHtml(
            html = html,
            body = """
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function abs(href) {
          if (!href) return '';
          return href.charAt(0) === '/' ? 'https://lms.vit.ac.in' + href : href;
        }

        var wrap = doc.querySelector('.calendarwrapper[data-month][data-year]');
        var wrapMonth = wrap ? (wrap.getAttribute('data-month') || '') : '';
        var wrapYear = wrap ? (wrap.getAttribute('data-year') || '') : '';

        var prevMonthUrl = '', nextMonthUrl = '';
        var navs = doc.querySelectorAll('a.arrow_link');
        for (var ni = 0; ni < navs.length; ni++) {
          var cls = navs[ni].getAttribute('class') || '';
          if (cls.indexOf('previous') !== -1) prevMonthUrl = abs(navs[ni].getAttribute('href'));
          else if (cls.indexOf('next') !== -1) nextMonthUrl = abs(navs[ni].getAttribute('href'));
        }

        var events = [];
        var dayCells = doc.querySelectorAll('td.day.hasevent');
        for (var di = 0; di < dayCells.length; di++) {
          var cell = dayCells[di];
          var day = cell.getAttribute('data-day');
          // The day link is the only element in the cell carrying data-month/data-year.
          var dayLink = cell.querySelector('a[data-action="view-day-link"]');
          var month = dayLink ? (dayLink.getAttribute('data-month') || '') : '';
          var year = dayLink ? (dayLink.getAttribute('data-year') || '') : '';
          if (!month) month = wrapMonth;
          if (!year) year = wrapYear;

          var links = cell.querySelectorAll('[data-region="event-item"] a[data-action="view-event"]');
          for (var li = 0; li < links.length; li++) {
            events.push({
              day: day ? parseInt(day, 10) : null,
              month: month === '' ? null : parseInt(month, 10),
              year: year === '' ? null : parseInt(year, 10),
              url: abs(links[li].getAttribute('href')),
              name: squish(links[li].querySelector('.eventname') || links[li]),
              done: links[li].querySelector('.icon-check, [data-event-type="due"], .tinyicon') !== null
            });
          }
        }

        return JSON.stringify({
          ok: true, events: events,
          month: wrapMonth === '' ? null : parseInt(wrapMonth, 10),
          year: wrapYear === '' ? null : parseInt(wrapYear, 10),
          prevMonthUrl: prevMonthUrl, nextMonthUrl: nextMonthUrl
        });
        """
        )
    }

    /**
     * One Moodle event page.
     *
     * `name` is `courseCode/courseName/assignmentName` so the app can group by course — the code
     * and name come from the breadcrumb's text and `title`, the assignment from `h1.h2`.
     *
     * `due` is read from the *parent* of a `strong` whose text contains `Due:`, with the label
     * stripped; `:contains()` is a cheerio extension, so this is an explicit scan.
     */
    fun parseLmsEvent(html: String): String {
        return parseInjectedHtml(
            html = html,
            body = """
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function queryParam(url, key) {
          var m = String(url).match(new RegExp('[?&]' + key + '=([^&#]*)'));
          return m ? decodeURIComponent(m[1]) : null;
        }

        var crumbs = doc.querySelectorAll('ol.breadcrumb li.breadcrumb-item a');
        var courseCodeFull = '', courseNameFull = '';
        if (crumbs.length > 0) {
          courseCodeFull = squish(crumbs[0]);
          courseNameFull = (crumbs[0].getAttribute('title') || '').trim();
        }
        var heading = doc.querySelector('h1.h2');
        var assignmentName = heading ? squish(heading) : '';
        var name = courseCodeFull + '/' + courseNameFull + '/' + assignmentName;

        var due = '';
        var strongs = doc.querySelectorAll('div.activity-dates strong');
        for (var si = 0; si < strongs.length; si++) {
          if (squish(strongs[si]).indexOf('Due:') === -1) continue;
          var parentText = strongs[si].parentElement ? squish(strongs[si].parentElement) : '';
          due = parentText.replace(/Due:/g, '').trim();
          break;
        }

        var done = doc.querySelector('[data-region="completion-info"] button.btn-success') !== null;

        // Teacher attribution needs the module's section, which the event page does not carry.
        var moduleId = null;
        var links = doc.querySelectorAll('a[href*="id="]');
        for (var li = 0; li < links.length; li++) {
          var cand = queryParam(links[li].getAttribute('href'), 'id');
          if (cand) { moduleId = cand; break; }
        }

        return JSON.stringify({
          ok: true, name: name, due: due, done: done, moduleId: moduleId,
          courseId: queryParam(crumbs.length > 0 ? (crumbs[0].getAttribute('href') || '') : '', 'id')
        });
        """
        )
    }

    /**
     * Moodle course page → the teachers for one module.
     *
     * Walks `#module-<id>` up to its containing `li[id^="section-"]`, then prefers the `Dr. ...`
     * prefix of the section title and falls back to the whole title when no such prefix exists.
     */
    fun parseLmsTeachers(html: String, moduleId: String): String {
        val mid = jsString(moduleId)
        return parseInjectedHtml(
            html = html,
            body = """
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        var teachers = [];
        var mod = doc.querySelector('#module-' + $mid);
        if (mod) {
          var section = null;
          var node = mod;
          while (node) {
            if (node.id && node.id.indexOf('section-') === 0) { section = node; break; }
            node = node.parentElement;
          }
          var sectionTitle = '';
          if (section) {
            var h3 = section.querySelector('h3.sectionname');
            sectionTitle = squish(h3 || section);
          }
          var m = sectionTitle.match(/^(Dr\.?\s+[A-Za-z.\s]+)/i);
          teachers = [m ? m[1].trim() : sectionTitle];
        }
        return JSON.stringify({ ok: true, teachers: teachers });
        """
        )
    }

    // ── QCM — genuinely two stages ────────────────────────────────────────

    /**
     * `getStudentLoginForQcm` — QCM stage 2, run **once per semester, sequentially**.
     *
     * This endpoint exists and returns real data, but AmazeCC-API's `api/qcm/route.ts` never
     * calls it: it only POSTs stage 1 and hands the shell to `parseVtopHtml`, which finds zero
     * tables because the page is a JS-required form ("Enable JavaScript to Access VTOP"). So
     * REMOTE QCM is structurally incapable of returning rows.
     *
     * Three things this needs that stage 1 does not have:
     *  - the param is **`semSubId`**, not `semesterSubId` (the stage-1 `<select>` uses
     *    `semesterSubId`, which is a trap)
     *  - `paramReturnId=getStudentLoginForQcm` is required
     *  - omitting `semSubId` returns "This menu is not available at present" rather than an error
     *
     * The response is a real table with 11 columns and no class hooks, so it is read by header
     * name rather than position.
     */
    fun fetchQcmForSemester(path: String, body: String, semSubId: String): String {
        val p = jsString(path)
        val b = jsString(body)
        val sem = jsString(semSubId)
        return wrapped(
            """
        var postBody = $b + '&semSubId=' + encodeURIComponent($sem)
                     + '&paramReturnId=getStudentLoginForQcm'
                     + '&x=' + new Date().toUTCString();

        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send(postBody);
        if (xhr.status === 0) return JSON.stringify({ ok: false, error: 'network error' });

        var res = xhr.responseText || '';
        if (res.toLowerCase().indexOf('not authorized') !== -1) {
          return JSON.stringify({ ok: false, error: 'not authorized' });
        }

        var doc = new DOMParser().parseFromString(res, 'text/html');
        function squish(el) { return (el ? (el.textContent || '') : '').replace(/\\s+/g, ' ').trim(); }

        var table = doc.querySelector('table');
        if (!table) return JSON.stringify({ ok: true, status: xhr.status, headers: [], rows: [] });

        // Headers carry newlines/tabs ("QCM No.\\n\\t\\tAction"), so they are squished before use.
        var headers = [];
        var headCells = table.querySelectorAll('tr')[0].querySelectorAll('th, td');
        for (var h = 0; h < headCells.length; h++) {
          var ht = squish(headCells[h]);
          if (ht && !headCells[h].getAttribute('colspan')) headers.push(ht);
        }

        var idx = function(name) {
          for (var i = 0; i < headers.length; i++) {
            if (headers[i].toLowerCase().indexOf(name.toLowerCase()) !== -1) return i;
          }
          return -1;
        };
        var iSem = idx('Sem Code');
        var iCourse = idx('Course Code');
        var iTitle = idx('Course Title');
        var iType = idx('Course Type');
        var iClass = idx('Class Nbr');
        var iFaculty = idx('Faculty');
        var iNo = idx('QCM No');
        var iAction = idx('Action');
        var iSug = idx('Suggestions');
        var iReply = idx('Faculty Reply');
        var iHod = idx('HOD Comments');

        var rows = [];
        var trs = table.querySelectorAll('tr');
        for (var r = 1; r < trs.length; r++) {
          var tds = trs[r].querySelectorAll('td');
          if (tds.length === 0) continue;
          // Empty cells are skipped but still consume an index, so read positionally.
          var cells = [];
          for (var c = 0; c < tds.length; c++) cells.push(squish(tds[c]));
          var pick = function(i) { return i >= 0 && i < cells.length ? cells[i] : ''; };
          rows.push({
            semesterCode: pick(iSem),
            courseCode: pick(iCourse),
            courseTitle: pick(iTitle),
            courseType: pick(iType),
            classNbr: pick(iClass),
            faculty: pick(iFaculty),
            qcmNo: pick(iNo),
            action: pick(iAction),
            suggestions: pick(iSug),
            facultyReply: pick(iReply),
            hodComments: pick(iHod)
          });
        }

        return JSON.stringify({
          ok: true, status: xhr.status, semesterId: $sem, headers: headers, rows: rows
        });
        """
        )
    }

    /**
     * Shared credentials-table parse, used by [fetchCredentials] and the PROFILE_IMAGES leg.
     * Declares `var clean` and returns `{title, credentials, ranks}`.
     */
    private val CREDENTIALS_JS = """
        var clean = function(s) { return (s || '').replace(/\s+/g, ' ').trim(); };
        var cTitle = doc.querySelector('h3.box-title b');
        var credentials = { title: cTitle ? clean(cTitle.textContent) : '', credentials: [], ranks: [] };
        var cTables = doc.querySelectorAll('table.customTable');
        for (var ct = 0; ct < cTables.length; ct++) {
          var headCells = [];
          var hr = cTables[ct].querySelectorAll('tr.tableHeader td');
          for (var q = 0; q < hr.length; q++) headCells.push(clean(hr[q].textContent).toUpperCase());
          var hasAccount = headCells.indexOf('ACCOUNT') !== -1;
          var hasName = headCells.indexOf('NAME') !== -1;

          if (hasAccount) {
            var bodyRows = cTables[ct].querySelectorAll('tr.tableContent');
            for (var ar = 0; ar < bodyRows.length; ar++) {
              var cs = bodyRows[ar].querySelectorAll('td');
              if (cs.length < 3) continue;
              var a = cs[3] ? cs[3].querySelector('a[href]') : null;
              credentials.credentials.push({
                account: (cs[0].textContent || '').trim(),
                username: (cs[1].textContent || '').trim(),
                defaultCredentials: (cs[2].textContent || '').trim(),
                url: a ? a.getAttribute('href') : ((cs[3].textContent || '').trim() || null),
                venueDate: cs[4] ? (cs[4].textContent || '').trim() : '',
                seatLocation: cs[5] ? (cs[5].textContent || '').trim() : ''
              });
            }
          } else if (hasName) {
            // Rank rows come from every tr except the header row.
            var allRows = cTables[ct].querySelectorAll('tr');
            for (var rr = 0; rr < allRows.length; rr++) {
              var row = allRows[rr];
              if (row.querySelector('td.tableHeader')) continue;
              if (row.className && row.className.indexOf('tableHeader') !== -1) continue;
              var rs = row.querySelectorAll('td');
              if (rs.length < 2) continue;
              credentials.ranks.push({
                name: (rs[0].textContent || '').trim(),
                rank: (rs[1].textContent || '').trim()
              });
            }
          }
        }
    """
}
