package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.AttendanceItem
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/** D-007: web share-code parity, round-tripped through the real Kotlin reader/writer. */
class SocialUtilsTest {
    private val attendance = listOf(
        AttendanceItem(
            courseCode = "CSE101",
            courseTitle = "Data Structures",
            slotName = "A1",
            slotVenue = "Room 101"
        ),
        AttendanceItem(
            courseCode = "MAT101",
            courseTitle = "Mathematics",
            slotName = "B1",
            slotVenue = "Room 202"
        )
    )

    @Test
    fun v6_code_round_trips_name_registration_and_slots() {
        val code = SocialUtils.exportScheduleCode(attendance, "Aravind", "21BCE1042", 10)
        assertTrue(code.startsWith("v6|"), code)
        assertEquals(6, code.split("|").size)
        val friend = SocialUtils.importScheduleCode(code)
        assertEquals("Aravind", friend.name)
        assertEquals("21BCE1042", friend.regNumber)
        assertTrue(friend.classSlots.isNotEmpty())
        assertTrue(friend.classSlots.all { it.slotId == "A1" || it.slotId == "B1" })
    }

    @Test
    fun v5_code_round_trips_without_expiry() {
        val code = SocialUtils.exportScheduleCode(attendance, "Aravind", "21BCE1042")
        assertTrue(code.startsWith("v5|"), code)
        val friend = SocialUtils.importScheduleCode(code)
        assertEquals("Aravind", friend.name)
        assertEquals("21BCE1042", friend.regNumber)
        assertTrue(friend.classSlots.isNotEmpty())
    }

    @Test
    fun hash_share_link_decodes_the_embedded_v5_code() {
        val link = SocialUtils.exportShareableLink(attendance, "Aravind", "21BCE1042")
        assertTrue(link.contains("#s="), link)
        val friend = SocialUtils.importScheduleCode(link)
        assertEquals("Aravind", friend.name)
        assertEquals("21BCE1042", friend.regNumber)
        assertTrue(friend.classSlots.isNotEmpty())
    }

    @Test
    fun amz_profile_code_preserves_utf8_names() {
        // Web encodeURIComponent(JSON.stringify(payload)) — non-ASCII bytes must survive.
        val payload = """["José","21BCE1043",["Math","Physics"],[["MON","A1",0]]]"""
        val code = "amz-profile-" + Base64.Default.encode(uriEncode(payload).encodeToByteArray())
        val friend = SocialUtils.importScheduleCode(code)
        assertEquals("José", friend.name)
        assertEquals("21BCE1043", friend.regNumber)
        assertEquals("Math", friend.classSlots.first().courseTitle)
        assertEquals("A1", friend.classSlots.first().slotId)
    }

    @Test
    fun v3_and_v2_formats_decode_titles_and_courses() {
        val v3 = SocialUtils.importScheduleCode("v3|Aravind|21BCE1042|Math;Physics|MON,A1,0;TUE,B1,1")
        assertEquals(2, v3.classSlots.size)
        assertEquals("Math", v3.classSlots[0].courseTitle)
        assertEquals("Physics", v3.classSlots[1].courseTitle)

        val v2 = SocialUtils.importScheduleCode("v2|Aravind|21BCE1042|CSE101~Data Structures~Room 101|MON,A1,0")
        assertEquals("CSE101", v2.classSlots.first().courseCode)
        assertEquals("Data Structures", v2.classSlots.first().courseTitle)
    }

    @Test
    fun v1_legacy_code_still_decodes() {
        val friend = SocialUtils.importScheduleCode(
            "Aravind|21BCE1042|Monday|8:00 AM|CSE101|Data Structures|Room 101|A1"
        )
        assertEquals("Aravind", friend.name)
        assertEquals("A1", friend.classSlots.first().slotId)
    }

    @Test
    fun expired_v6_code_reports_how_long_ago_it_expired() {
        val expiredAt = Clock.System.now().toEpochMilliseconds() / 1000 - 120
        val error = assertFailsWith<Exception> {
            SocialUtils.importScheduleCode("v6|$expiredAt|Aravind|21BCE1042|Math|0a0")
        }
        assertTrue(error.message?.contains("expired") == true, error.message)
        assertTrue(error.message?.contains("minute") == true, error.message)
    }

    /** Mirrors encodeURIComponent closely enough for the UTF-8 code path under test. */
    private fun uriEncode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            if (b in 'A'.code..'Z'.code || b in 'a'.code..'z'.code || b in '0'.code..'9'.code) {
                append(b.toChar())
            } else {
                append('%').append(b.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
