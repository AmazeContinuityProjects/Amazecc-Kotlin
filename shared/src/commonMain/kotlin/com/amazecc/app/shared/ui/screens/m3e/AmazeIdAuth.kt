package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * D-002 — real Amaze ID (OIDC) client for the Kotlin app, backed by the same
 * auth.amazecc.com server the web app uses (`../AmazeCC/src/lib/oauth2.ts` and
 * `../amazecc-auth/src/auth.ts` are the source of truth).
 *
 * Platform divergence (documented, not hidden): a common-KMP app cannot host
 * the web's registered `https://amazecc.com/auth/callback/` redirect — there is
 * no browser tab, no intent-filter callback and no IndexedDB DPoP key in this
 * codebase. Two honest native-equivalent flows are implemented instead:
 *
 *  1. **RFC 8628 Device Authorization Grant** (recommended). The app obtains a
 *     short `user_code` + `verification_uri`, the student approves in any
 *     browser, and the app polls the token endpoint. No callback handler and no
 *     DPoP key are needed — the auth server issues a plain `Bearer` token when
 *     the client does not opt into DPoP-bound tokens.
 *  2. **Authorization code + PKCE with a pasted callback**, plus a direct
 *     access-token paste fallback. The app mints the PKCE verifier/state and
 *     builds the real authorize URL; the student opens it in a browser and
 *     pastes back the `?code=…&state=…` URL (or the token JSON). State is
 *     verified, the code is exchanged at the real token endpoint, and the
 *     token is validated against userinfo before anything is persisted.
 *
 * A DPoP-bound token can still be used: paste its DPoP proof header and the
 * server `confirmation` value in the manual entry form. Nothing ever fabricates
 * a successful sign-in — every path calls the live server and fails closed.
 */
object AmazeIdAuth {
    private const val DEFAULT_BASE_URL = "https://auth.amazecc.com"
    private const val DEFAULT_CLIENT_ID = "amazecc-web"
    const val DEFAULT_REDIRECT_URI = "amazecc://auth/callback"

    private const val KEY_BASE_URL = "amaze_id_auth_base_url"
    private const val KEY_CLIENT_ID = "amaze_id_client_id"
    private const val KEY_REDIRECT_URI = "amaze_id_redirect_uri"
    private const val KEY_PENDING_VERIFIER = "amaze_id_pkce_verifier"
    private const val KEY_PENDING_STATE = "amaze_id_pkce_state"
    private const val KEY_PENDING_REDIRECT = "amaze_id_pkce_redirect"
    private const val KEY_PENDING_AT = "amaze_id_pkce_at"

    private const val SCOPE = "openid profile email offline_access"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
    private const val PENDING_MAX_AGE_MS = 15L * 60 * 1000

    /** Server presets. CUSTOM is represented by whatever URL is stored. */
    data class Server(val label: String, val baseUrl: String)

    val SERVERS = listOf(
        Server("Primary · auth.amazecc.com", "https://auth.amazecc.com"),
        Server("Local dev · 127.0.0.1:8787", "http://127.0.0.1:8787"),
    )

    fun baseUrl(): String = SettingsManager.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
        .trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }

    fun setBaseUrl(url: String) = SettingsManager.setString(KEY_BASE_URL, url.trim().trimEnd('/'))

    fun clientId(): String = SettingsManager.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID)
        .trim().ifBlank { DEFAULT_CLIENT_ID }

    fun setClientId(id: String) = SettingsManager.setString(KEY_CLIENT_ID, id.trim())

    /** Must exactly match a redirect URI registered on the OAuth client. */
    fun redirectUri(): String = SettingsManager.getString(KEY_REDIRECT_URI, DEFAULT_REDIRECT_URI)
        .trim().ifBlank { DEFAULT_REDIRECT_URI }

    fun setRedirectUri(uri: String) = SettingsManager.setString(KEY_REDIRECT_URI, uri.trim())

    val isCustomBaseUrl: Boolean
        get() = SERVERS.none { it.baseUrl == baseUrl() }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 20_000
        }
    }

    private fun tokenEndpoint() = "${baseUrl()}/api/auth/oauth2/token"
    private fun userinfoEndpoint() = "${baseUrl()}/api/auth/oauth2/userinfo"

    /** Percent-encodes a form value (RFC 3986 unreserved set kept verbatim). */
    private fun formEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            when {
                c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> append(c.toChar())
                else -> append('%').append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private fun formBody(pairs: List<Pair<String, String>>): String =
        pairs.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }

    private suspend fun postForm(url: String, body: String, dpopHeader: String? = null): Pair<Int, String> {
        val response = http.post(url) {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            if (!dpopHeader.isNullOrBlank()) header("DPoP", dpopHeader)
            setBody(body)
        }
        return response.status.value to response.bodyAsText()
    }

    private suspend fun postJson(url: String, body: String): Pair<Int, String> {
        val response = http.post(url) {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        return response.status.value to response.bodyAsText()
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    val hex = ch.code.toString(16).uppercase().padStart(2, '0')
                    append("\\u00").append(hex)
                } else append(ch)
            }
        }
        append('"')
    }

    // ── Wire DTOs (tolerant: the server may add claims) ───────────────────────

    @Serializable
    data class DeviceCodeRes(
        val device_code: String = "",
        val user_code: String = "",
        val verification_uri: String = "",
        val verification_uri_complete: String? = null,
        val expires_in: Long = 600,
        val interval: Long = 5,
    )

    @Serializable
    data class TokenRes(
        val access_token: String = "",
        val token_type: String = "Bearer",
        val expires_in: Long = 0,
        val refresh_token: String? = null,
        val id_token: String? = null,
        val scope: String? = null,
        val confirmation: String? = null,
        val error: String? = null,
        val error_description: String? = null,
    )

    @Serializable
    data class UserInfoRes(
        val sub: String? = null,
        val email: String? = null,
        val name: String? = null,
        val role: String? = null,
    )

    sealed class DevicePoll {
        data object Pending : DevicePoll()
        data class SlowDown(val seconds: Long) : DevicePoll()
        data class SignedIn(val displayName: String) : DevicePoll()
        data class Failed(val message: String) : DevicePoll()
    }

    // ── Device authorization grant (RFC 8628) ────────────────────────────────

    suspend fun requestDeviceCode(): Result<DeviceCodeRes> = runCatching {
        val body = "{\"client_id\":${jsonString(clientId())},\"scope\":${jsonString(SCOPE)}}"
        val (status, text) = postJson("${baseUrl()}/api/auth/device/code", body)
        if (status !in 200..299) error(serverError(status, text, "Could not start device sign-in"))
        val res = json.decodeFromString<DeviceCodeRes>(text)
        if (res.device_code.isBlank() || res.user_code.isBlank() || res.verification_uri.isBlank()) {
            error("The sign-in server returned an incomplete device code. Please try again.")
        }
        res
    }

    suspend fun pollDeviceToken(deviceCode: String): DevicePoll {
        val body = formBody(
            listOf(
                "grant_type" to DEVICE_GRANT,
                "device_code" to deviceCode,
                "client_id" to clientId(),
            )
        )
        val (status, text) = runCatching { postForm(tokenEndpoint(), body) }
            .getOrElse { return DevicePoll.Failed("Connection error: ${it.message}") }
        if (status in 200..299) {
            val token = runCatching { json.decodeFromString<TokenRes>(text) }.getOrNull()
            if (token == null || token.access_token.isBlank()) {
                return DevicePoll.Failed("The sign-in server returned an empty token.")
            }
            // Result.Success/Failure are internal in commonMain — use fold so the
            // live userinfo validation result maps to the real UI outcome.
            return validateAndStore(token.access_token, null, token.confirmation).fold(
                onSuccess = { DevicePoll.SignedIn(it) },
                onFailure = { DevicePoll.Failed(it.message ?: "Token validation failed.") },
            )
        }
        val err = runCatching { json.decodeFromString<TokenRes>(text) }.getOrNull()
        return when (err?.error) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown(err.error_description?.toLongOrNull()?.takeIf { it > 0 } ?: 5)
            "expired_token" -> DevicePoll.Failed("The sign-in code expired. Start a new one.")
            "access_denied" -> DevicePoll.Failed("Sign-in was declined.")
            else -> DevicePoll.Failed(serverError(status, text, "Waiting for approval…"))
        }
    }

    // ── Authorization code + PKCE (manual callback paste) ────────────────────

    @OptIn(ExperimentalEncodingApi::class)
    private fun randomUrlSafe(byteCount: Int): String =
        Base64.UrlSafe.encode(Random.nextBytes(byteCount)).trimEnd('=')

    @OptIn(ExperimentalEncodingApi::class)
    private fun sha256Base64Url(input: String): String =
        Base64.UrlSafe.encode(sha256(input.encodeToByteArray())).trimEnd('=')

    data class PkceChallenge(val authorizeUrl: String, val state: String, val redirectUri: String)

    /** Mints a fresh PKCE pair, persists it, and returns a real authorize URL. */
    fun beginAuthorizationCode(): PkceChallenge {
        val redirectUri = redirectUri()
        val verifier = randomUrlSafe(48)
        val state = randomUrlSafe(24)
        SettingsManager.setString(KEY_PENDING_VERIFIER, verifier)
        SettingsManager.setString(KEY_PENDING_STATE, state)
        SettingsManager.setString(KEY_PENDING_REDIRECT, redirectUri)
        SettingsManager.setLong(KEY_PENDING_AT, Clock.System.now().toEpochMilliseconds())
        val query = listOf(
            "client_id" to clientId(),
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to SCOPE,
            "code_challenge" to sha256Base64Url(verifier),
            "code_challenge_method" to "S256",
            "state" to state,
        ).joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }
        return PkceChallenge("${baseUrl()}/api/auth/oauth2/authorize?$query", state, redirectUri)
    }

    data class Callback(val code: String, val state: String?)

    /** Parses a pasted callback URL, `code=…&state=…` fragment, or bare code. */
    fun parseCallback(input: String): Result<Callback> = runCatching {
        val raw = input.trim()
        if (raw.isBlank()) error("Paste the callback URL or authorization code first.")
        val query = raw.substringAfter('?', missingDelimiterValue = raw)
            .substringBefore('#')
        val params = query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else percentDecode(part.substring(0, eq)) to percentDecode(part.substring(eq + 1))
        }.toMap()
        val code = params["code"]?.takeIf { it.isNotBlank() }
            ?: raw.takeIf { "?" !in it && "&" !in it && "=" !in it && !it.startsWith('{') }
        if (code.isNullOrBlank()) error("No authorization code found in that response.")
        Callback(code, params["state"])
    }

    /** Verifies the pasted state against the pending PKCE challenge. */
    fun verifyState(pastedState: String?): Result<Unit> = runCatching {
        val expected = pendingVerifier()?.let { SettingsManager.getString(KEY_PENDING_STATE) }
        if (expected.isNullOrBlank()) {
            error("No sign-in attempt is pending here. Tap 'Open Amaze ID sign-in' and try again.")
        }
        if (pastedState.isNullOrBlank() || pastedState != expected) {
            error("The sign-in response does not match this device (state mismatch). Please try again.")
        }
    }

    private fun pendingVerifier(): String? {
        val verifier = SettingsManager.getString(KEY_PENDING_VERIFIER).takeIf { it.isNotBlank() } ?: return null
        val at = SettingsManager.getLong(KEY_PENDING_AT)
        if (at <= 0L || Clock.System.now().toEpochMilliseconds() - at > PENDING_MAX_AGE_MS) {
            clearPendingPkce()
            return null
        }
        return verifier
    }

    private fun clearPendingPkce() {
        listOf(KEY_PENDING_VERIFIER, KEY_PENDING_STATE, KEY_PENDING_REDIRECT, KEY_PENDING_AT)
            .forEach(SettingsManager::remove)
    }

    suspend fun exchangeAuthorizationCode(code: String): Result<TokenRes> = runCatching {
        val verifier = pendingVerifier()
            ?: error("The PKCE verifier expired. Start a new sign-in attempt.")
        val body = formBody(
            listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to SettingsManager.getString(KEY_PENDING_REDIRECT, DEFAULT_REDIRECT_URI),
                "client_id" to clientId(),
                "code_verifier" to verifier,
            )
        )
        val (status, text) = postForm(tokenEndpoint(), body)
        if (status !in 200..299) error(serverError(status, text, "Token exchange failed"))
        val token = json.decodeFromString<TokenRes>(text)
        if (token.access_token.isBlank()) {
            error(token.error_description ?: token.error ?: "The server did not return an access token.")
        }
        token
    }

    /** Parses a pasted token-endpoint JSON response (the "token" callback form). */
    fun parseTokenResponse(input: String): Result<TokenRes> = runCatching {
        val raw = input.trim()
        if (!raw.startsWith('{')) error("That is not a token response (expected JSON).")
        val token = json.decodeFromString<TokenRes>(raw)
        if (token.access_token.isBlank()) error(token.error_description ?: "No access_token in that response.")
        token
    }

    /**
     * Final gate for every path: ask the live userinfo endpoint who this token
     * belongs to, and only then persist it. A bogus pasted token is rejected.
     */
    suspend fun validateAndStore(
        accessToken: String,
        dpopHeader: String?,
        confirmation: String?,
    ): Result<String> = runCatching {
        if (accessToken.isBlank()) error("Empty access token.")
        val scheme = if (dpopHeader.isNullOrBlank()) "Bearer" else "DPoP"
        val response = http.get(userinfoEndpoint()) {
            header(HttpHeaders.Authorization, "$scheme $accessToken")
            if (!dpopHeader.isNullOrBlank()) header("DPoP", dpopHeader)
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            error(serverError(response.status.value, text, "The access token was rejected by Amaze ID"))
        }
        val user = runCatching { json.decodeFromString<UserInfoRes>(text) }.getOrNull()
        val label = user?.email?.takeIf { it.isNotBlank() }
            ?: user?.name?.takeIf { it.isNotBlank() }
            ?: user?.sub?.takeIf { it.isNotBlank() }
            ?: "Amaze ID"

        SessionManager.saveAmazeIdSession(accessToken, dpopHeader, confirmation)
        clearPendingPkce()
        label
    }

    fun signOut() {
        SessionManager.clearAmazeIdSession()
        clearPendingPkce()
    }

    // ── Small pure helpers ────────────────────────────────────────────────────

    private fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            when {
                value[i] == '+' -> { bytes.add(' '.code.toByte()); i++ }
                value[i] == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val byte = hex.toIntOrNull(16)?.toByte()
                    if (byte == null) { bytes.add(value[i].code.toByte()); i++ }
                    else { bytes.add(byte); i += 3 }
                }
                else -> { bytes.add(value[i].code.toByte()); i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun serverError(status: Int, text: String, fallback: String): String {
        val parsed = runCatching { json.decodeFromString<TokenRes>(text) }.getOrNull()
        val detail = parsed?.error_description ?: parsed?.error
        return when {
            !detail.isNullOrBlank() -> "$fallback: $detail"
            text.isNotBlank() && text.length <= 200 && !text.trim().startsWith('<') -> "$fallback ($status): ${text.trim()}"
            else -> "$fallback (HTTP $status)."
        }
    }

    /** SHA-256 (FIPS 180-4) — pure Kotlin so the PKCE challenge works on every target. */
    private fun sha256(input: ByteArray): ByteArray {
        val k = intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(),
            0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb,
            0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c,
            0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814.toInt(),
            0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )

        val bitLen = input.size.toLong() * 8
        val padded = input.copyOf(((input.size + 9 + 63) / 64) * 64)
        padded[input.size] = 0x80.toByte()
        for (i in 0..7) padded[padded.size - 1 - i] = (bitLen ushr (8 * i)).toByte()

        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372.toInt()
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19
        val w = IntArray(64)

        for (offset in padded.indices step 64) {
            for (i in 0..15) {
                val j = offset + i * 4
                w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                    ((padded[j + 1].toInt() and 0xFF) shl 16) or
                    ((padded[j + 2].toInt() and 0xFF) shl 8) or
                    (padded[j + 3].toInt() and 0xFF)
            }
            for (i in 16..63) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0..63) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + k[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
        }
        return ByteArray(32) { i ->
            val word = when (i / 4) { 0 -> h0; 1 -> h1; 2 -> h2; 3 -> h3; 4 -> h4; 5 -> h5; 6 -> h6; else -> h7 }
            (word ushr (24 - 8 * (i % 4))).toByte()
        }
    }

    private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
}
