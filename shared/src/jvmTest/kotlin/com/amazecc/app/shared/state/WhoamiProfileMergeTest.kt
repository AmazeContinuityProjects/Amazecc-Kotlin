package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
import com.amazecc.app.shared.model.RESIDENCE_HOSTELLER
import com.amazecc.app.shared.model.StudentIdentity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhoamiProfileMergeTest {

    @BeforeTest
    fun setup() {
        UserStore.clear()
    }

    @AfterTest
    fun tearDown() {
        UserStore.clear()
        com.amazecc.app.shared.repository.SettingsManager.remove(
            com.amazecc.app.shared.repository.SettingsManager.CACHE_USER_IDENTITY
        )
    }

    @Test
    fun `ME overwrites residence both directions while boolean only propagates true`() {
        UserStore.merge(
            StudentIdentity(isHosteller = true, residenceStatus = RESIDENCE_HOSTELLER),
            IdentitySource.ME
        )
        assertEquals(RESIDENCE_HOSTELLER, UserStore.identity.value.residenceStatus)
        assertTrue(UserStore.identity.value.isHosteller)

        // ME → DAYSCHOLAR (the string overwrites even though the boolean cannot go false)
        UserStore.merge(
            StudentIdentity(isHosteller = false, residenceStatus = RESIDENCE_DAYSCHOLAR),
            IdentitySource.ME
        )
        assertEquals(RESIDENCE_DAYSCHOLAR, UserStore.identity.value.residenceStatus)
        assertTrue(UserStore.identity.value.isHosteller) // boolean() ignores false

        // ME → HOSTELLER again (reverse direction)
        UserStore.merge(
            StudentIdentity(isHosteller = true, residenceStatus = RESIDENCE_HOSTELLER),
            IdentitySource.ME
        )
        assertEquals(RESIDENCE_HOSTELLER, UserStore.identity.value.residenceStatus)
    }

    @Test
    fun `lower tier cannot overwrite an ME-authored residence`() {
        UserStore.merge(StudentIdentity(residenceStatus = RESIDENCE_DAYSCHOLAR), IdentitySource.ME)
        UserStore.merge(StudentIdentity(residenceStatus = RESIDENCE_HOSTELLER), IdentitySource.STUDENT)
        assertEquals(RESIDENCE_DAYSCHOLAR, UserStore.identity.value.residenceStatus)

        // another ME write still wins
        UserStore.merge(StudentIdentity(residenceStatus = RESIDENCE_HOSTELLER), IdentitySource.ME)
        assertEquals(RESIDENCE_HOSTELLER, UserStore.identity.value.residenceStatus)
    }

    @Test
    fun `folded-only fields merge and survive persistence round-trip`() {
        UserStore.merge(
            StudentIdentity(
                regNo = "21BCE1001",
                residenceStatus = RESIDENCE_DAYSCHOLAR,
                contactTitle = "Campus Contacts",
                acknowledgement = com.amazecc.app.shared.model.AcknowledgementInfo(
                    title = "Ack",
                    documents = listOf(
                        com.amazecc.app.shared.model.AcknowledgementDocument(
                            serialNo = "1", documentName = "Fee Receipt", status = "Verified"
                        )
                    )
                )
            ),
            IdentitySource.ME
        )
        UserStore.persistNow()
        UserStore.loadFromCache()

        assertEquals("21BCE1001", UserStore.identity.value.regNo)
        assertEquals(RESIDENCE_DAYSCHOLAR, UserStore.identity.value.residenceStatus)
        assertEquals("Campus Contacts", UserStore.identity.value.contactTitle)
        assertEquals("Fee Receipt", UserStore.identity.value.acknowledgement?.documents?.first()?.documentName)
        assertFalse(UserStore.identity.value.isHosteller)
    }
}
