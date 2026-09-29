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

    /** Refreshes the captcha image in place, without reloading the page. */
    val REFRESH_CAPTCHA = wrapped(
        """
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/vtop/get/new/captcha', false);
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send();
        if (xhr.status !== 200) return JSON.stringify({ ok: false, error: 'status ' + xhr.status });
        var block = document.getElementById('captchaBlock');
        if (!block) return JSON.stringify({ ok: false, error: 'captchaBlock not found' });
        block.innerHTML = xhr.responseText;
        var img = block.querySelector('img');
        if (!img) return JSON.stringify({ ok: false, error: 'no image after refresh' });
        return JSON.stringify({ ok: true, base64: img.getAttribute('src') });
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

    /**
     * POSTs a form-encoded body to a path relative to `/vtop/` and returns the raw HTML fragment.
     *
     * Used by the per-domain extractors added in phase 3. Returns [postForm] plus the response
     * so the caller can run a parser over it in the same WebView document.
     */
    fun postForm(path: String, body: String): String {
        val p = jsString(path)
        val b = jsString(body)
        return wrapped(
            """
        var xhr = new XMLHttpRequest();
        xhr.open('POST', $p, false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.send($b);
        return JSON.stringify({ status: xhr.status, body: xhr.responseText || '' });
        """
        )
    }

    /** Parses an HTML fragment inside the WebView with a caller-supplied extractor. */
    fun parseFragment(extractorBody: String): String = wrapped(
        """
        var doc = new DOMParser().parseFromString(window.__vtopFragment || '', 'text/html');
        $extractorBody
        """
    )
}
