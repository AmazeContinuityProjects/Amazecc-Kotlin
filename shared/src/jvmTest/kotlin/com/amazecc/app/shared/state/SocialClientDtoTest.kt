package com.amazecc.app.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** D-006/D-013: every social wire response must decode, especially the COARSE busyMap array. */
class SocialClientDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun ffcs_match_state_decodes() {
        val state = json.decodeFromString<FfcsMatchState>(
            """
            {"success":true,"semesterId":"CH20262705","handles":["AMZ-4F7K-2Q9X"],"participants":3,
             "me":{"handle":"AMZ-4F7K-2Q9X","name":"Aravind","joined":true,"discoverable":true,
                   "source":"registered","classIds":["CH2026270102069"],
                   "updatedAt":"2026-11-20T10:00:00Z","changesLeft":3},
             "sections":[{"classId":"CH2026270102069",
                          "offering":{"classId":"CH2026270102069","code":"CSE101","title":"Data Structures",
                                      "type":"TH","credits":"4","room":"R1","slot":"A1","faculty":"Dr X"},
                          "registerNos":["21BCE1042"],
                          "people":[{"handle":"AMZ-AAAA-BBBB","name":"Peer","regNo":"21BCE1043","source":"registered"}]}],
             "overlaps":[]}
            """.trimIndent()
        )
        assertTrue(state.success)
        assertEquals(3, state.participants)
        assertEquals("CSE101", state.sections.first().offering?.code)
        assertEquals("Peer", state.sections.first().people.first().name)
        assertEquals(3, state.me.changesLeft)
    }

    @Test
    fun ffcs_classes_response_decodes() {
        val rosters = json.decodeFromString<FfcsClassesResponse>(
            """
            {"success":true,"semesterId":"CH20262705","classes":[{
              "classId":"CH2026270102069","courseCode":"CSE101","courseTitle":"Data Structures",
              "courseType":"TH","slot":"A1","faculty":"Dr X","credits":"4","room":"R1",
              "students":[{"regNo":"21BCE1043","name":"Peer","handle":"AMZ-AAAA-BBBB"}]}]}
            """.trimIndent()
        )
        assertEquals("CSE101", rosters.classes.first().courseCode)
        assertEquals("AMZ-AAAA-BBBB", rosters.classes.first().students.first().handle)
    }

    @Test
    fun person_response_decodes() {
        val person = json.decodeFromString<PersonResponse>(
            """{"success":true,"person":{"handle":"AMZ-AAAA-BBBB","displayName":"Peer","alreadyPaired":false}}"""
        )
        assertEquals("AMZ-AAAA-BBBB", person.person.handle)
        assertEquals(false, person.person.alreadyPaired)
    }

    @Test
    fun claim_response_decodes() {
        val claim = json.decodeFromString<ClaimResponse>(
            """
            {"success":true,"grantId":"grant_1","secret":"sec_1","created":true,"visibility":"full",
             "peer":{"handle":"AMZ-AAAA-BBBB","name":"Peer","lastPublishedAt":null,"semesters":["CH20262705"]}}
            """.trimIndent()
        )
        assertEquals("grant_1", claim.grantId)
        assertEquals(SocialVisibility.FULL, claim.visibility)
        assertEquals("CH20262705", claim.peer.semesters.first())
    }

    @Test
    fun grant_revoke_and_visibility_responses_decode() {
        val revoked = json.decodeFromString<GrantRevokeResponse>(
            """{"success":true,"grantId":"grant_1","revoked":true}"""
        )
        assertEquals("grant_1", revoked.grantId)
        val visibility = json.decodeFromString<GrantVisibilityResponse>(
            """{"success":true,"grantId":"grant_1","visibility":"coarse"}"""
        )
        assertEquals(SocialVisibility.COARSE, visibility.visibility)
    }

    @Test
    fun coarse_peer_timetable_decodes_busy_map_as_a_key_array() {
        val timetable = json.decodeFromString<PeerTimetableResponse>(
            """
            {"success":true,
             "identity":{"handle":"AMZ-AAAA-BBBB","displayName":"Peer","semesterId":"CH20262705",
                         "semesterLabel":"Fall 2026","publishedAt":"2026-11-20T10:00:00Z","slotmapVersion":"v1"},
             "visibility":"coarse","stale":false,"version":2,
             "busyMap":["MON:A1","TUE:B2"],"courses":[]}
            """.trimIndent()
        )
        assertEquals(SocialVisibility.COARSE, timetable.visibility)
        assertEquals(2, timetable.busyMap.size)
        assertEquals(BusyEntry(), timetable.busyMap["MON:A1"])
        assertTrue(timetable.busyMap.containsKey("TUE:B2"))
        assertTrue(timetable.courses.isEmpty())
    }

    @Test
    fun full_peer_timetable_decodes_busy_map_as_an_entry_object() {
        val timetable = json.decodeFromString<PeerTimetableResponse>(
            """
            {"success":true,
             "identity":{"handle":"AMZ-AAAA-BBBB","semesterId":"CH20262705","publishedAt":"2026-11-20T10:00:00Z"},
             "visibility":"full","stale":false,"version":1,
             "busyMap":{"MON:A1":{"c":"CSE101","t":"Data Structures","v":"R1"}},
             "courses":[{"code":"CSE101","title":"Data Structures","venue":"R1","ltpjc":"4",
                         "category":"Programme Core Courses","componentType":"Theory Only",
                         "classId":"CH2026270102069","faculty":"Dr X"}]}
            """.trimIndent()
        )
        assertEquals("CSE101", timetable.busyMap["MON:A1"]?.c)
        assertEquals("Data Structures", timetable.busyMap["MON:A1"]?.t)
        assertEquals("R1", timetable.courses.first().venue)
    }

    @Test
    fun semester_response_decodes() {
        val semester = json.decodeFromString<SemesterResponse>(
            """{"success":true,"semesterId":"CH20262705","semesterLabel":"Fall 2026","resolvedAt":"2026-11-20T10:00:00Z"}"""
        )
        assertEquals("CH20262705", semester.semesterId)
    }

    @Test
    fun identity_sync_payload_decodes() {
        val payload = json.decodeFromString<SocialSyncPayload>(
            """
            {"success":true,"handles":["AMZ-4F7K-2Q9X"],
             "identity":{"ownerKey":"reg_21bce1042","handle":"AMZ-4F7K-2Q9X","displayName":"Aravind",
                         "semesterId":"CH20262705","semesterLabel":"Fall 2026",
                         "derivedAt":"2026-11-20T10:00:00Z","slotmapVersion":"v1"},
             "semesterSource":"cached","version":1,"busyMap":{},"courses":[],
             "peers":[],"grantSecrets":[]}
            """.trimIndent()
        )
        assertEquals("AMZ-4F7K-2Q9X", payload.handles.first())
        assertEquals("cached", payload.semesterSource)
    }
}
