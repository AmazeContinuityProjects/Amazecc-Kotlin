package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.SocialClient.FfcsMatchRequest
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-193 part 1 — the social transport/client works in LOCAL VTOP mode with only an Amaze ID
 * token, and never pretends a non-2xx or a malformed `{}` body is a successful "0 friends".
 * Every call is driven through the AmazeClient.socialRequestOverride seam: no network, no
 * WebView, no captcha, no fake success.
 */
class SocialClientAuthTest {

    private data class Snapshot(
        val source: VtopSource,
        val baseUrl: String,
        val cookies: String?,
        val csrf: String?,
        val authorizedID: String?,
        val clubToken: String?,
        val token: String?,
        val dpop: String?,
        val confirmation: String?
    )

    private lateinit var snapshot: Snapshot
    private val captured = mutableListOf<AmazeClient.SocialRequestDescriptor>()
    private val canned = mutableListOf<AmazeClient.SocialHttpResponse>()

    private val stateJson = """{
        "success":true,"semesterId":"CH20262705","participants":1,
        "handles":["AMZ-TEST-0001"],
        "me":{"handle":"AMZ-TEST-0001","name":"Test Student","joined":true,"discoverable":true,
              "source":"registered","classIds":[],"changesLeft":3},
        "sections":[],"overlaps":[]
    }"""

    private val stateJsonWithoutHandles = """{
        "success":true,"semesterId":"CH20262705","participants":1,
        "me":{"handle":"AMZ-TEST-0001","name":"Test Student","joined":true},
        "sections":[],"overlaps":[]
    }"""

    private val semesterJson =
        """{"success":true,"semesterId":"CH20262705","semesterLabel":"Winter 2026-27","resolvedAt":"2026-11-01"}"""

    @BeforeTest
    fun setUp() {
        snapshot = Snapshot(
            source = AmazeClient.vtopSource,
            baseUrl = AmazeClient.baseUrl,
            cookies = SessionManager.cookies.value,
            csrf = SessionManager.csrf.value,
            authorizedID = SessionManager.authorizedID.value,
            clubToken = SessionManager.clubToken.value,
            token = SessionManager.amazeAccessToken.value,
            dpop = SessionManager.amazeDpopHeader.value,
            confirmation = SessionManager.amazeTokenConfirmation.value
        )
        captured.clear()
        canned.clear()
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        AmazeClient.baseUrl = "https://social.invalid"
        SessionManager.clearSession()
        SessionManager.clearAmazeIdSession()
        AmazeClient.clearAmazeIdSignInRequired()
        AmazeClient.socialRequestOverride = { request ->
            captured += request
            if (canned.isEmpty()) error("Unexpected social request: $request")
            canned.removeAt(0)
        }
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.socialRequestOverride = null
        AmazeClient.clearAmazeIdSignInRequired()
        SessionManager.clearSession()
        SessionManager.clearAmazeIdSession()
        val cookies = snapshot.cookies
        val csrf = snapshot.csrf
        val authorizedID = snapshot.authorizedID
        if (cookies != null && csrf != null && authorizedID != null) {
            SessionManager.saveSession(cookies, csrf, authorizedID, snapshot.clubToken)
        }
        val token = snapshot.token
        if (!token.isNullOrBlank()) {
            SessionManager.saveAmazeIdSession(token, snapshot.dpop, snapshot.confirmation)
        }
        AmazeClient.baseUrl = snapshot.baseUrl
        AmazeClient.setVtopSource(snapshot.source)
        AmazeClient.setUseMockData(false)
    }

    private fun signIn(token: String = "amaze-token", dpop: String? = null, confirmation: String? = null) {
        SessionManager.saveAmazeIdSession(token, dpop, confirmation)
    }

    private fun localVtopSession() {
        SessionManager.saveInMemorySession(SessionManager.LOCAL_SESSION_MARKER, "local-csrf", "21BCE1234")
    }

    private fun enqueue(status: Int, body: String) {
        canned += AmazeClient.SocialHttpResponse(status, body)
    }

    private fun stringField(request: AmazeClient.SocialRequestDescriptor, key: String): String? =
        (request.body[key] as? JsonPrimitive)?.content

    // -- request bodies -----------------------------------------------------------------

    @Test
    fun get_state_uses_canonical_semester_and_carries_no_vtop_credentials() = runBlocking {
        signIn()
        localVtopSession() // LOCAL marker must not be mistaken for a REMOTE cookie requirement
        enqueue(200, stateJson)

        val state = SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262701"))

        assertEquals("AMZ-TEST-0001", state.me.handle)
        val request = captured.single()
        assertEquals("social/ffcs-match", request.path)
        assertEquals("POST", request.method)
        assertEquals("CH20262705", stringField(request, "semesterId"))
        assertEquals("get", stringField(request, "op"))
        assertNull(request.body["cookies"])
        assertNull(request.body["authorizedID"])
        assertNull(request.body["csrf"])
        assertTrue(request.query.isEmpty())
        assertTrue(request.url.startsWith("https://social.invalid/api/social/ffcs-match"), request.url)
        assertFalse(request.url.contains("cookies"), "No credentials in URLs")
        assertEquals("Bearer amaze-token", request.headers["Authorization"])
        assertNull(request.headers["DPoP"])
    }

    @Test
    fun publish_sends_json_boolean_and_vtop_credentials_in_the_body_only() = runBlocking {
        signIn()
        localVtopSession()
        enqueue(200, stateJson)

        SocialClient.ffcsMatchState(
            FfcsMatchRequest.Publish("winter-2026-2027", discoverable = false)
        )

        val request = captured.single()
        assertEquals("publish", stringField(request, "op"))
        assertEquals("registered", stringField(request, "source"))
        assertEquals("CH20262705", stringField(request, "semesterId"))
        assertEquals("false", (request.body["discoverable"] as? JsonPrimitive)?.content)
        assertEquals(SessionManager.LOCAL_SESSION_MARKER, stringField(request, "cookies"))
        assertEquals("local-csrf", stringField(request, "csrf"))
        assertEquals("21BCE1234", stringField(request, "authorizedID"))
        assertTrue(request.query.isEmpty())
        assertFalse(request.url.contains("JSESSIONID"))
    }

    @Test
    fun visibility_body_and_semester_get_are_both_credential_free() = runBlocking {
        signIn()
        localVtopSession()
        enqueue(200, stateJson)
        enqueue(200, semesterJson)

        SocialClient.ffcsMatchState(FfcsMatchRequest.Visibility("CH20262701", discoverable = true))
        val semester = SocialClient.fetchCurrentSemester()

        assertEquals("CH20262705", semester.semesterId)
        val visibility = captured[0]
        assertEquals("visibility", stringField(visibility, "op"))
        assertEquals("true", (visibility.body["discoverable"] as? JsonPrimitive)?.content)
        assertNull(visibility.body["cookies"])

        val get = captured[1]
        assertEquals("GET", get.method)
        assertEquals("social/semester", get.path)
        assertTrue(get.query.isEmpty(), "semester GET has no query parameters")
        assertTrue(get.body.isEmpty(), "semester GET has no body / VTOP credentials")
        assertEquals("Bearer amaze-token", get.headers["Authorization"])
    }

    @Test
    fun dpop_bound_token_uses_dpop_scheme_and_proof_header() = runBlocking {
        signIn(token = "dpop-token", dpop = "dpop-proof", confirmation = "cnf-1")
        enqueue(200, semesterJson)

        SocialClient.fetchCurrentSemester()

        val headers = captured.single().headers
        assertEquals("DPoP dpop-token", headers["Authorization"])
        assertEquals("dpop-proof", headers["DPoP"])
    }

    // -- auth gating ---------------------------------------------------------------------

    @Test
    fun local_mode_without_remote_cookies_is_a_valid_social_call() = runBlocking {
        // setUp left SessionManager with no VTOP session: only the Amaze ID token is stored.
        signIn()
        enqueue(200, stateJson)

        val state = SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))

        assertEquals("AMZ-TEST-0001", state.me.handle)
        assertEquals(1, captured.size, "LOCAL mode must not be blocked by the REMOTE-cookie guard")
        assertNull(SessionManager.cookies.value)
    }

    @Test
    fun missing_amaze_id_token_fails_before_any_request() = runBlocking {
        assertFalse(SocialClient.hasAmazeIdSession())
        val error = assertFailsWith<SocialApiError> { SocialClient.fetchCurrentSemester() }
        assertEquals("amazeid_session_expired", error.code)
        assertEquals(SocialSignInKind.AMAZE_ID, error.requiredSignIn)
        assertTrue(captured.isEmpty(), "No request without an Amaze ID token")

        signIn()
        assertTrue(SocialClient.hasAmazeIdSession())
        enqueue(200, semesterJson)
        assertEquals("CH20262705", SocialClient.fetchCurrentSemester().semesterId)
    }

    @Test
    fun server_requested_vtop_retry_adds_credentials_on_the_second_call() = runBlocking {
        signIn()
        localVtopSession()
        enqueue(401, """{"success":false,"error":"vtop_session_required"}""")
        enqueue(200, stateJson)

        val state = SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))

        assertEquals("AMZ-TEST-0001", state.me.handle)
        assertEquals(2, captured.size)
        assertNull(captured[0].body["cookies"])
        assertEquals(SessionManager.LOCAL_SESSION_MARKER, stringField(captured[1], "cookies"))
    }

    @Test
    fun vtop_session_required_is_not_reported_as_an_amaze_id_failure() = runBlocking {
        signIn() // no VTOP session stored, so there is nothing to retry with
        enqueue(401, """{"success":false,"error":"vtop_session_required","detail":{"detail":"refresh VTOP"}}""")

        val error = assertFailsWith<SocialApiError> {
            SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))
        }

        assertEquals("vtop_session_required", error.code)
        assertEquals(SocialSignInKind.VTOP, error.requiredSignIn)
        assertTrue(error.needsVtopSignIn)
        assertFalse(error.needsAmazeIdSignIn)
        assertEquals(401, error.httpStatus)
        assertEquals("refresh VTOP", error.detail)
    }

    @Test
    fun successful_call_consumes_a_previously_raised_auth_signal() = runBlocking {
        signIn()
        SessionManager.markAmazeAuthRequired()
        val blocked = assertFailsWith<SocialApiError> { SocialClient.fetchCurrentSemester() }
        assertEquals("amazeid_session_expired", blocked.code)
        assertTrue(AmazeClient.isAmazeIdSignInRequired())

        // Once the signal is cleared, a normal successful call must not re-raise it.
        AmazeClient.clearAmazeIdSignInRequired()
        enqueue(200, semesterJson)
        SocialClient.fetchCurrentSemester()
        assertFalse(AmazeClient.isAmazeIdSignInRequired())
    }

    // -- typed errors / required fields --------------------------------------------------

    @Test
    fun error_envelopes_map_to_distinct_typed_codes() = runBlocking {
        signIn()
        val cases = listOf(
            "vtop_session_expired" to SocialSignInKind.VTOP,
            "signin_required" to SocialSignInKind.VTOP,
            "amazeid_signin_required" to SocialSignInKind.AMAZE_ID,
            "amazeid_session_expired" to SocialSignInKind.AMAZE_ID,
            "change_limit" to SocialSignInKind.NONE,
            "not_joined" to SocialSignInKind.NONE
        )
        cases.forEachIndexed { index, (code, expectedKind) ->
            enqueue(422, """{"success":false,"error":"$code","detail":{"detail":"$code detail"}}""")
            val error = assertFailsWith<SocialApiError> {
                SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))
            }
            assertEquals(code, error.code, "case $index")
            assertEquals(expectedKind, error.requiredSignIn, "case $index")
            assertEquals(422, error.httpStatus, "case $index")
            assertEquals("$code detail", error.detail, "case $index")
        }
        assertEquals(cases.size, captured.size)
    }

    @Test
    fun an_empty_object_is_an_error_not_zero_friends() = runBlocking {
        signIn()
        enqueue(200, "{}")

        val error = assertFailsWith<SocialApiError> {
            SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262701"))
        }

        assertEquals("unexpected_response", error.code)
        assertEquals(200, error.httpStatus)
    }

    @Test
    fun a_wrong_host_answer_without_required_fields_is_rejected() = runBlocking {
        signIn()
        enqueue(200, """{"success":true,"semesterId":"CH20262705"}""")
        val rostersError = assertFailsWith<SocialApiError> {
            SocialClient.ffcsMatchClasses("CH20262701")
        }
        assertEquals("unexpected_response", rostersError.code)

        enqueue(200, """{"success":true}""")
        val semesterError = assertFailsWith<SocialApiError> { SocialClient.fetchCurrentSemester() }
        assertEquals("unexpected_response", semesterError.code)
    }

    @Test
    fun non_2xx_without_a_json_body_is_still_a_typed_error() = runBlocking {
        signIn()
        enqueue(503, "")

        val error = assertFailsWith<SocialApiError> { SocialClient.fetchCurrentSemester() }

        assertEquals("http_503", error.code)
        assertEquals(503, error.httpStatus)
    }

    // -- roster GET / handles / grants ----------------------------------------------------

    @Test
    fun roster_get_canonicalises_semester_and_keeps_class_ids_out_of_the_body() = runBlocking {
        signIn()
        enqueue(200, """{"success":true,"semesterId":"CH20262705","classes":[]}""")

        val rosters = SocialClient.ffcsMatchClasses("winter-2026-2027", listOf(" CH202627050001 ", ""))

        assertEquals("CH20262705", rosters.semesterId)
        assertTrue(rosters.classes.isEmpty())
        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("social/ffcs-match", request.path)
        assertEquals("CH20262705", request.query["semesterId"])
        assertEquals("CH202627050001", request.query["classIds"])
        assertTrue(request.body.isEmpty())
        assertNull(request.body["cookies"])
        assertFalse(request.url.contains("cookies"))
    }

    @Test
    fun handles_array_is_used_and_missing_handles_fall_back_to_me_handle() = runBlocking {
        signIn()
        enqueue(
            200,
            stateJson.replace(
                """"handles":["AMZ-TEST-0001"]""",
                """"handles":["AMZ-TEST-0001","AMZ-OTHER-0002"]"""
            )
        )
        val withHandles = SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))
        assertEquals(listOf("AMZ-TEST-0001", "AMZ-OTHER-0002"), withHandles.handles)

        enqueue(200, stateJsonWithoutHandles)
        val fallback = SocialClient.ffcsMatchState(FfcsMatchRequest.Get("CH20262705"))
        assertEquals(listOf("AMZ-TEST-0001"), fallback.handles)
    }

    @Test
    fun an_existing_grant_without_a_secret_is_valid_and_not_a_new_grant() = runBlocking {
        signIn()
        enqueue(
            200,
            """{"success":true,"grantId":"grant-1","created":false,"visibility":"full","peer":{"handle":"AMZ-PEER-0001"}}"""
        )
        val existing = SocialClient.claimPair("AMZ-PEER-0001")

        assertTrue(existing.isExistingGrant)
        assertFalse(existing.hasSecret)
        assertNull(existing.secret)
        assertEquals("grant-1", existing.grantId)

        enqueue(
            200,
            """{"success":true,"grantId":"grant-2","secret":"bearer-secret","created":true,"visibility":"full"}"""
        )
        val created = SocialClient.claimPair("AMZ-PEER-0002")
        assertFalse(created.isExistingGrant)
        assertTrue(created.hasSecret)
        assertEquals("bearer-secret", created.secret)

        enqueue(200, """{"success":true,"grantId":"grant-3","created":true}""")
        val error = assertFailsWith<SocialApiError> { SocialClient.claimPair("AMZ-PEER-0003") }
        assertEquals("unexpected_response", error.code, "A new grant without a secret is malformed")
    }
}
