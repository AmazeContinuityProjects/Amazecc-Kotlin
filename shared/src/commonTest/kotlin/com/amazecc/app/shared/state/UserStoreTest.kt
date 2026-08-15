package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AccountCredential
import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.Official
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.model.VtopTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserStoreTest {

    private fun resetStore() {
        UserStore.clear()
        UserStore.loadFromCache()
        UserStore.clear()
    }

    // ── Filtering & fragment hygiene ──

    @Test
    fun emptyFragmentIsIgnored() {
        resetStore()
        UserStore.merge(StudentIdentity(), IdentitySource.SESSION)
        assertEquals(StudentIdentity(), UserStore.identity.value)
    }

    @Test
    fun blankValuesAreFilteredOut() {
        resetStore()
        UserStore.merge(
            StudentIdentity(name = "   ", regNo = "25BYB1043", email = ""),
            IdentitySource.SESSION
        )
        val identity = UserStore.identity.value
        assertEquals("25BYB1043", identity.regNo)
        assertNull(identity.name)
        assertNull(identity.email)
    }

    @Test
    fun placeholderAndEmptyRanksAreDropped() {
        resetStore()
        UserStore.merge(
            StudentIdentity(credentials = emptyList(), ranks = emptyList()),
            IdentitySource.CREDENTIALS
        )
        assertEquals(StudentIdentity(), UserStore.identity.value)
    }

    // ── Merge semantics ──

    @Test
    fun emptyNeverErasesFilled() {
        resetStore()
        UserStore.merge(StudentIdentity(name = "Aarav", mobile = "9876543210"), IdentitySource.SESSION)
        UserStore.merge(StudentIdentity(name = null, mobile = ""), IdentitySource.STUDENT)
        val identity = UserStore.identity.value
        assertEquals("Aarav", identity.name)
        assertEquals("9876543210", identity.mobile)
    }

    @Test
    fun lowerTierCannotOverwriteHigherTier() {
        resetStore()
        UserStore.merge(StudentIdentity(name = "Aarav"), IdentitySource.STUDENT)
        UserStore.merge(StudentIdentity(name = "Session Name"), IdentitySource.SESSION)
        assertEquals("Aarav", UserStore.identity.value.name)
    }

    @Test
    fun higherTierOverwritesLowerTier() {
        resetStore()
        UserStore.merge(StudentIdentity(name = "Session Name"), IdentitySource.SESSION)
        UserStore.merge(StudentIdentity(name = "Aarav Kumar"), IdentitySource.STUDENT)
        assertEquals("Aarav Kumar", UserStore.identity.value.name)
    }

    @Test
    fun sameTierLetsNewerValueWin() {
        resetStore()
        UserStore.merge(StudentIdentity(section = "A"), IdentitySource.STUDENT)
        UserStore.merge(StudentIdentity(section = "B"), IdentitySource.STUDENT)
        assertEquals("B", UserStore.identity.value.section)
    }

    @Test
    fun booleansOnlyPropagateTrue() {
        resetStore()
        UserStore.merge(StudentIdentity(isHosteller = true), IdentitySource.STUDENT)
        assertTrue(UserStore.identity.value.isHosteller)
        UserStore.merge(StudentIdentity(isHosteller = false), IdentitySource.STUDENT)
        assertTrue(UserStore.identity.value.isHosteller)
    }

    @Test
    fun nonEmptyListsReplaceWholesale() {
        resetStore()
        UserStore.merge(
            StudentIdentity(credentials = listOf(AccountCredential(account = "Wifi"))),
            IdentitySource.CREDENTIALS
        )
        UserStore.merge(
            StudentIdentity(credentials = listOf(AccountCredential(account = "VTOP"))),
            IdentitySource.CREDENTIALS
        )
        assertEquals(listOf("VTOP"), UserStore.identity.value.credentials.map { it.account })
    }

    @Test
    fun proctorDeepMergeSurvivesPartialFragments() {
        resetStore()
        UserStore.merge(
            StudentIdentity(proctor = Official(role = "Proctor", name = "Dr. Meera")),
            IdentitySource.PROFILE_IMAGES
        )
        UserStore.merge(
            StudentIdentity(proctor = Official(role = "Proctor", email = "meera@vit.ac.in")),
            IdentitySource.STUDENT
        )
        val proctor = UserStore.identity.value.proctor!!
        assertEquals("Dr. Meera", proctor.name)
        assertEquals("meera@vit.ac.in", proctor.email)
        assertEquals("Proctor", proctor.role)
    }

    @Test
    fun hodDeanReplacesAsAList() {
        resetStore()
        UserStore.merge(
            StudentIdentity(hodDean = listOf(Official(role = "HoD", name = "One"))),
            IdentitySource.PROFILE_IMAGES
        )
        UserStore.merge(
            StudentIdentity(hodDean = listOf(Official(role = "Dean", name = "Two"))),
            IdentitySource.PROFILE_IMAGES
        )
        assertEquals(listOf("Two"), UserStore.identity.value.hodDean.map { it.name })
    }

    @Test
    fun recordFragmentsMergeUnderRecordsTier() {
        resetStore()
        UserStore.merge(StudentIdentity(eptTables = listOf(VtopTable(headers = listOf("Exam"), rows = listOf(listOf("EPA"))))), IdentitySource.RECORDS)
        UserStore.merge(StudentIdentity(registrationFields = listOf(KeyValueRow("Slot", "M1"))), IdentitySource.RECORDS)
        assertEquals(1, UserStore.identity.value.eptTables.size)
        assertEquals("M1", UserStore.identity.value.registrationFields.first().value)
    }

    // ── Persistence round-trip ──

    @Test
    fun mergePersistsAndLoadRestores() {
        resetStore()
        UserStore.merge(
            StudentIdentity(
                name = "Aarav Kumar",
                regNo = "25BYB1043",
                aadharNumber = "1234",
                credentials = listOf(AccountCredential(account = "VTOP", password = "secret")),
                proctor = Official(role = "Proctor", name = "Dr. Meera")
            ),
            IdentitySource.CREDENTIALS
        )
        UserStore.clear()
        assertEquals(StudentIdentity(), UserStore.identity.value)
        UserStore.loadFromCache()
        val restored = UserStore.identity.value
        assertEquals("Aarav Kumar", restored.name)
        assertEquals("25BYB1043", restored.regNo)
        assertEquals("1234", restored.aadharNumber)
        assertEquals("secret", restored.credentials.first().password)
        assertEquals("Dr. Meera", restored.proctor?.name)
    }

    // ── Extractors ──

    @Test
    fun fromSessionOnlyCarriesRegNo() {
        val fragment = IdentityExtractor.fromSession("25BYB1043")
        assertEquals("25BYB1043", fragment.regNo)
        assertNull(fragment.name)
        assertFalse(fragment.hasIdentity)
    }

    @Test
    fun fromVtopPhotoFiltersBlank() {
        assertNull(IdentityExtractor.fromVtopPhoto("  ").photoBase64)
        assertEquals("data:image/png;base64,abc", IdentityExtractor.fromVtopPhoto("data:image/png;base64,abc").photoBase64)
    }
}