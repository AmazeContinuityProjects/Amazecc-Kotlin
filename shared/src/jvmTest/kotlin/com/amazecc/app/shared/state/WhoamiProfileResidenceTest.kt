package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
import com.amazecc.app.shared.model.RESIDENCE_HOSTELLER
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhoamiProfileResidenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun extract(payload: String) =
        IdentityExtractor.fromWhoami(json.parseToJsonElement(payload).jsonObject)

    @Test
    fun `explicit isHosteller true is authoritative even over a dayboarder staying type`() {
        val identity = extract(
            """
            {
              "isHosteller": true,
              "dayboarder": {"fields": {
                "f0": {"label": "Staying Type", "value": "DB", "selectedText": "Day Boarder"}
              }}
            }
            """.trimIndent()
        )
        assertEquals(RESIDENCE_HOSTELLER, identity.residenceStatus)
        assertTrue(identity.isHosteller)
    }

    @Test
    fun `canonical staying type DAYBOARDER derives dayscholar`() {
        val identity = extract(
            """
            {"isHosteller": false, "dayboarder": {"fields": {
              "f0": {"label": "Staying Type", "value": "DAYBOARDER", "selectedText": "Day Boarder"}
            }}}
            """.trimIndent()
        )
        assertEquals(RESIDENCE_DAYSCHOLAR, identity.residenceStatus)
        assertFalse(identity.isHosteller)
    }

    @Test
    fun `staying type HOSTELLER re-syncs the boolean when flag was false`() {
        val identity = extract(
            """
            {"isHosteller": false, "dayboarder": {"fields": {
              "f0": {"label": "Staying Type", "value": "HOSTELLER"}
            }}}
            """.trimIndent()
        )
        assertEquals(RESIDENCE_HOSTELLER, identity.residenceStatus)
        assertTrue(identity.isHosteller)
    }

    @Test
    fun `dayboarder block with meaningful fields but no staying type derives dayscholar`() {
        val identity = extract(
            """
            {"isHosteller": false, "dayboarder": {"fields": {
              "f1": {"label": "Pincode", "value": "600001"},
              "f2": {"label": "Area Name", "value": "Anna Nagar"}
            }}}
            """.trimIndent()
        )
        assertEquals(RESIDENCE_DAYSCHOLAR, identity.residenceStatus)
    }

    @Test
    fun `selectedText drives derivation when raw value is an opaque code`() {
        val rows = listOf(KeyValueRow("Staying Type", "Day Scholar"))
        assertEquals(RESIDENCE_DAYSCHOLAR, IdentityExtractor.deriveResidenceStatus(false, rows))
    }

    @Test
    fun `placeholder staying type is not evidence of residence`() {
        val rows = listOf(KeyValueRow("Staying Type", "Please select..."))
        assertNull(IdentityExtractor.deriveResidenceStatus(false, rows))
    }

    @Test
    fun `real VTOP staying type option text derives dayscholar`() {
        val rows = listOf(KeyValueRow("Staying Type", "Staying with parents/Guardian"))
        assertEquals(RESIDENCE_DAYSCHOLAR, IdentityExtractor.deriveResidenceStatus(false, rows))
    }

    @Test
    fun `unknown residence stays null so onboarding choice is the fallback`() {
        val identity = extract("""{"isHosteller": false}""".trimIndent())
        assertNull(identity.residenceStatus)
        assertFalse(identity.isHosteller)

        val fromEmptyBlock = extract("""{"isHosteller": false, "dayboarder": {"title": "Dayboarder"}}""".trimIndent())
        assertNull(fromEmptyBlock.residenceStatus)
    }
}
