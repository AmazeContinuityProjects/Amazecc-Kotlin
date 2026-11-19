package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
import com.amazecc.app.shared.model.WhoamiRes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A representative FLAT `/api/remote/whoami` payload: identity spread at the top
 * level plus all ten folded VTOP blocks. Shared with the getMe branch test.
 */
internal const val FLAT_WHOAMI = """
{
  "success": true,
  "regNo": "21BCE1001",
  "name": "Aarav Test",
  "email": "aarav@vitstudent.ac.in",
  "mobile": "9876543210",
  "dob": "01-Jan-2004",
  "gender": "Male",
  "bloodGroup": "B+",
  "photoBase64": "photo-base64",
  "isHosteller": false,
  "program": "B.Tech CSE",
  "campus": "Vellore",
  "nationality": "Indian",
  "nativeLanguage": "Tamil",
  "nativeState": "Tamil Nadu",
  "community": "OC",
  "religion": "Hindu",
  "caste": "General",
  "physicallyChallenged": "No",
  "aadharNumber": "123456789012",
  "guardian": "Guardian Name",
  "currentAddress": [
    {"label": "Friend Mobile No", "value": "9999999999"},
    {"label": "City", "value": "Chennai"}
  ],
  "permanentAddress": [{"label": "City", "value": "Madurai"}],
  "father": [{"label": "Name", "value": "Father Name"}],
  "mother": [{"label": "Name", "value": "Mother Name"}],
  "proctor": {
    "role": "Proctor", "name": "Dr P", "designation": "Professor",
    "email": "p@vit.ac.in", "school": "SCOPE",
    "extras": [{"label": "Cabin", "value": "SJT-1"}]
  },
  "hodDean": [{"role": "Dean", "name": "Dr D", "photoBase64": "dean-photo"}],
  "credentials": [{
    "account": "ERP", "username": "21BCE1001", "password": "secret",
    "url": "https://vtop", "venueDate": "01-01-2025", "seatLocation": "A1"
  }],
  "ranks": [{"name": "Rank 1", "rank": "1"}],
  "bank": {
    "name": "State Bank", "branch": "Chennai", "address": "Anna Nagar",
    "fields": [{"label": "Account No", "value": "1234"}]
  },
  "apaar": {
    "hasApaar": true,
    "fields": [{"label": "Apaar ID", "value": "APAAR123"}],
    "tables": [{"caption": "Apaar Table", "headers": ["Name", "Value"],
                "rows": [{"Name": "Field1", "Value": "Val1"}]}]
  },
  "contact": [{"office": "Proctor Office", "description": "Room 101", "email": "office@vit.ac.in"}],
  "contactTitle": "Campus Contacts",
  "acknowledgement": {
    "title": "Acknowledgement",
    "documents": [{"serialNo": "1", "documentName": "Fee Receipt", "status": "Verified"}],
    "applicationNo": "APP123", "registerNo": "21BCE1001",
    "programme": "B.Tech", "branch": "CSE", "campus": "Vellore"
  },
  "dayboarder": {
    "title": "Dayboarder",
    "fields": {
      "f0": {"label": "Staying Type", "value": "DB", "selectedText": "Day Boarder"},
      "f1": {"label": "Pincode", "value": "600001"}
    }
  },
  "eptSchedule": {
    "title": "EPT Schedule",
    "tables": [{"caption": "EPT", "headers": ["Course", "Date"],
                "rows": [{"Course": "EPT1", "Date": "2025-01-01"}]}],
    "keyValuePairs": {"Mode": "Online"},
    "formFields": {"Semester": "Fall"},
    "messages": {"warning": "Check schedule"},
    "message": "Exam will be scheduled"
  }
}
"""

class WhoamiProfileParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses flat identity core and academic fields`() {
        val obj = json.parseToJsonElement(FLAT_WHOAMI).jsonObject
        val identity = IdentityExtractor.fromWhoami(obj)

        assertEquals("21BCE1001", identity.regNo)
        assertEquals("Aarav Test", identity.name)
        assertEquals("aarav@vitstudent.ac.in", identity.email)
        assertEquals("9876543210", identity.mobile)
        assertEquals("01-Jan-2004", identity.dob)
        assertEquals("Male", identity.gender)
        assertEquals("B+", identity.bloodGroup)
        assertEquals("photo-base64", identity.photoBase64)
        assertEquals("B.Tech CSE", identity.program)
        assertEquals("Vellore", identity.campus)
        assertEquals("Indian", identity.nationality)
        assertEquals("Tamil Nadu", identity.nativeState)
        assertEquals("123456789012", identity.aadharNumber)
        assertEquals("Guardian Name", identity.guardian)
        assertEquals("APP123", identity.applicationNumber)
    }

    @Test
    fun `maps every folded source`() {
        val identity = IdentityExtractor.fromWhoami(json.parseToJsonElement(FLAT_WHOAMI).jsonObject)

        // friend mobile is recovered from the address rows
        assertEquals("9999999999", identity.friendMobile)
        assertEquals(2, identity.currentAddress.size)
        assertEquals("Madurai", identity.permanentAddress.first().value)
        assertEquals("Father Name", identity.father.first().value)
        assertEquals("Mother Name", identity.mother.first().value)

        // proctor / hodDean
        assertEquals("Dr P", identity.proctor?.name)
        assertEquals("SCOPE", identity.school)
        assertEquals("SJT-1", identity.proctor?.extras?.first()?.value)
        assertEquals("Dean", identity.hodDean.first().role)
        assertEquals("dean-photo", identity.hodDean.first().photoBase64)

        // credentials + ranks
        assertEquals("secret", identity.credentials.first().password)
        assertEquals("ERP", identity.credentials.first().account)
        assertEquals("1", identity.ranks.first().rank)

        // bank + apaar
        assertEquals("State Bank", identity.bank?.name)
        assertEquals("1234", identity.bank?.fields?.first()?.value)
        assertEquals(true, identity.apaar?.hasApaar)
        assertEquals(listOf("Field1", "Val1"), identity.apaar?.tables?.first()?.rows?.first())

        // contact + acknowledgement
        assertEquals("Proctor Office", identity.contacts.first().office)
        assertEquals("Campus Contacts", identity.contactTitle)
        assertEquals("Fee Receipt", identity.acknowledgement?.documents?.first()?.documentName)
        assertEquals("APP123", identity.acknowledgement?.applicationNo)

        // dayboarder + ept (with selectedText winning for display)
        assertEquals("Dayboarder", identity.dayboarderTitle)
        assertEquals("Day Boarder", identity.dayboarder?.fields?.first { it.label == "Staying Type" }?.value)
        assertEquals("600001", identity.dayboarder?.fields?.first { it.label == "Pincode" }?.value)
        assertEquals("EPT Schedule", identity.eptTitle)
        assertEquals(listOf("EPT1", "2025-01-01"), identity.eptTables.first().rows.first())
        assertTrue(identity.eptFields.any { it.label == "Mode" && it.value == "Online" })
        assertTrue(identity.eptMessages.any { it.label == "Message" && it.value == "Exam will be scheduled" })

        // derivation from the selectedText "Day Boarder"
        assertEquals(RESIDENCE_DAYSCHOLAR, identity.residenceStatus)
    }

    @Test
    fun `normalizes vtop tables whose rows are key-value objects`() {
        val table = IdentityExtractor.fromWhoami(json.parseToJsonElement(FLAT_WHOAMI).jsonObject)
            .eptTables.first()
        assertEquals("EPT", table.caption)
        assertEquals(listOf("Course", "Date"), table.headers)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("EPT1", "2025-01-01"), table.rows[0])
    }

    @Test
    fun `decodes the flat WhoamiRes wire model including fetchErrors`() {
        val res = json.decodeFromString<WhoamiRes>(FLAT_WHOAMI)
        assertTrue(res.success)
        assertEquals("21BCE1001", res.regNo)
        assertNotNull(res.proctor)
        assertNotNull(res.dayboarder)

        // the typed-model overload must decode to the same canonical identity
        val fromModel = IdentityExtractor.fromWhoami(res)
        assertEquals("21BCE1001", fromModel.regNo)
        assertEquals(RESIDENCE_DAYSCHOLAR, fromModel.residenceStatus)

        val partial = json.decodeFromString<WhoamiRes>(
            """{"success":false,"fetchErrors":{"bank":"HTTP 500","contact":"timeout"}}"""
        )
        assertEquals("HTTP 500", partial.fetchErrors?.get("bank"))
        assertEquals("timeout", partial.fetchErrors?.get("contact"))
    }
}
