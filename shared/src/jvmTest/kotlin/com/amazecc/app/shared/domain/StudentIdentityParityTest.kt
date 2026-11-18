package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.IdentitySource
import com.amazecc.app.shared.state.UserStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class StudentIdentityParityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    @BeforeTest
    fun setup() {
        UserStore.clear()
    }

    @AfterTest
    fun tearDown() {
        UserStore.clear()
    }

    // =========================================================================
    // Tier 1: Feature Tests (≥5 tests for Feature 4)
    // =========================================================================

    @Test
    fun testStudentIdentityCoreFieldsPopulated() {
        val identity = StudentIdentity(
            regNo = "21BCE1001",
            name = "Aditya Sharma",
            email = "aditya.sharma2021@vitstudent.ac.in",
            mobile = "+91 9876543210",
            dob = "15-Aug-2003",
            gender = "Male",
            bloodGroup = "O+",
            photoBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
            isHosteller = true,
            program = "B.Tech Computer Science and Engineering",
            campus = "Vellore Campus",
            batch = "2021",
            section = "CSE01",
            advisorName = "Dr. Academic Advisor"
        )

        assertEquals("21BCE1001", identity.regNo)
        assertEquals("Aditya Sharma", identity.name)
        assertEquals("Aditya Sharma", identity.displayName)
        assertEquals("21BCE1001", identity.displayRegNo)
        assertEquals("AS", identity.initials)
        assertTrue(identity.hasIdentity)
        assertTrue(identity.isHosteller)
        assertEquals("Vellore Campus", identity.campus)
        assertEquals("B.Tech Computer Science and Engineering", identity.program)
        assertEquals("data:image/jpeg;base64,/9j/4AAQSkZJRg==", identity.photoBase64)
    }

    @Test
    fun testOfficialProctorAndSchoolFieldIntegrity() {
        val proctor = Official(
            role = "Proctor",
            name = "Dr. Ramesh Babu",
            designation = "Associate Professor Grade 1",
            email = "ramesh.babu@vit.ac.in",
            phone = "+91 98400 12345",
            school = "SCOPE",
            cabin = "SJT 413",
            department = "Software and Systems Engineering",
            intercom = "1413",
            facultyId = "10452",
            photoBase64 = "data:image/jpeg;base64,proctorPhotoBase64",
            extras = listOf(KeyValueRow(label = "Specialization", value = "Distributed Systems"))
        )

        val identity = StudentIdentity(
            regNo = "21BCE1001",
            name = "Aditya Sharma",
            proctor = proctor
        )

        assertNotNull(identity.proctor)
        val p = identity.proctor!!
        assertEquals("Dr. Ramesh Babu", p.name)
        assertEquals("SCOPE", p.school)
        assertEquals("SJT 413", p.cabin)
        assertEquals("ramesh.babu@vit.ac.in", p.email)
        assertEquals("1413", p.intercom)
        assertEquals("Distributed Systems", p.extras.first().value)
    }

    @Test
    fun testJsonSerializationAndDeserializationRoundtrip() {
        val original = StudentIdentity(
            regNo = "22BCE9999",
            name = "Priya Raman",
            email = "priya.raman2022@vitstudent.ac.in",
            mobile = "+91 9123456789",
            dob = "22-Oct-2004",
            gender = "Female",
            bloodGroup = "B+",
            isHosteller = false,
            program = "B.Tech Information Technology",
            campus = "Chennai Campus",
            proctor = Official(role = "Proctor", name = "Dr. Suresh", school = "SITE"),
            credentials = listOf(AccountCredential(account = "VTOP", username = "22BCE9999", password = "secretPassword")),
            ranks = listOf(RankInfo(name = "VITEEE Rank", rank = "1240")),
            bank = BankInfo(name = "Indian Bank", branch = "VIT Campus", address = "Katpadi"),
            apaar = ApaarInfo(hasApaar = true, fields = listOf(KeyValueRow("APAAR Number", "1234-5678-9012")))
        )

        val encoded = json.encodeToString(original)
        assertFalse(encoded.isBlank(), "Encoded JSON must not be blank")

        val decoded = json.decodeFromString<StudentIdentity>(encoded)
        assertEquals(original.regNo, decoded.regNo)
        assertEquals(original.name, decoded.name)
        assertEquals(original.email, decoded.email)
        assertEquals(original.isHosteller, decoded.isHosteller)
        assertEquals(original.proctor?.name, decoded.proctor?.name)
        assertEquals(original.proctor?.school, decoded.proctor?.school)
        assertEquals(original.credentials.size, decoded.credentials.size)
        assertEquals("secretPassword", decoded.credentials.first().password)
        assertEquals("1240", decoded.ranks.first().rank)
        assertEquals("Indian Bank", decoded.bank?.name)
        assertTrue(decoded.apaar?.hasApaar == true)
        assertEquals("1234-5678-9012", decoded.apaar?.fields?.first()?.value)
    }

    @Test
    fun testUserStoreMergePrecedenceBetweenStudentAndSession() {
        // Initial merge from SESSION (tier 0)
        UserStore.merge(
            StudentIdentity(regNo = "21BCE1001", name = "Session Name"),
            IdentitySource.SESSION
        )
        assertEquals("Session Name", UserStore.identity.value.name)

        // Incoming merge from STUDENT (tier 2 - higher precedence)
        UserStore.merge(
            StudentIdentity(regNo = "21BCE1001", name = "Aditya Sharma", program = "B.Tech CSE"),
            IdentitySource.STUDENT
        )
        assertEquals("Aditya Sharma", UserStore.identity.value.name, "Higher tier STUDENT must overwrite SESSION name")
        assertEquals("B.Tech CSE", UserStore.identity.value.program)

        // Lower tier SESSION attempts to overwrite STUDENT
        UserStore.merge(
            StudentIdentity(name = "Old Session Name"),
            IdentitySource.SESSION
        )
        assertEquals("Aditya Sharma", UserStore.identity.value.name, "Lower tier SESSION must NOT overwrite STUDENT name")
    }

    @Test
    fun testUserStoreMergeMeTierHighestPrecedence() {
        // Populate from various intermediate tiers
        UserStore.merge(StudentIdentity(name = "Student Name", email = "old@vit.ac.in"), IdentitySource.STUDENT)
        UserStore.merge(StudentIdentity(proctor = Official(name = "Old Proctor")), IdentitySource.PROFILE_IMAGES)
        UserStore.merge(StudentIdentity(bank = BankInfo(name = "Old Bank")), IdentitySource.BANK)

        // ME tier (tier 8) consolidated snapshot
        val meSnapshot = StudentIdentity(
            regNo = "21BCE1001",
            name = "Consolidated Me Name",
            email = "me@vit.ac.in",
            proctor = Official(name = "Authoritative Proctor", school = "SCOPE"),
            bank = BankInfo(name = "HDFC Bank")
        )
        UserStore.merge(meSnapshot, IdentitySource.ME)

        val merged = UserStore.identity.value
        assertEquals("Consolidated Me Name", merged.name)
        assertEquals("me@vit.ac.in", merged.email)
        assertEquals("Authoritative Proctor", merged.proctor?.name)
        assertEquals("SCOPE", merged.proctor?.school)
        assertEquals("HDFC Bank", merged.bank?.name)
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (≥5 tests for Feature 4)
    // =========================================================================

    @Test
    fun testNullAndEmptyStringFragmentValuesNeverEraseExistingData() {
        UserStore.merge(
            StudentIdentity(
                regNo = "21BCE1001",
                name = "Existing Name",
                mobile = "+91 99999 88888",
                bloodGroup = "A+"
            ),
            IdentitySource.STUDENT
        )

        // Incoming fragment with nulls and blanks at a higher tier
        UserStore.merge(
            StudentIdentity(
                name = null,
                mobile = "   ",
                bloodGroup = ""
            ),
            IdentitySource.ME
        )

        val result = UserStore.identity.value
        assertEquals("Existing Name", result.name, "Null incoming value must never erase existing name")
        assertEquals("+91 99999 88888", result.mobile, "Blank incoming value must never erase existing mobile")
        assertEquals("A+", result.bloodGroup, "Empty incoming value must never erase existing blood group")
    }

    @Test
    fun testCorruptedOrLargeBase64AvatarStringFidelity() {
        // 4KB base64 avatar data
        val largeAvatar = "data:image/png;base64," + "A".repeat(4096)
        val identity = StudentIdentity(
            regNo = "21BCE1001",
            photoBase64 = largeAvatar
        )

        val encoded = json.encodeToString(identity)
        val decoded = json.decodeFromString<StudentIdentity>(encoded)
        assertEquals(largeAvatar, decoded.photoBase64, "Large base64 avatar must survive JSON roundtrip intact")

        UserStore.merge(identity, IdentitySource.STUDENT)
        assertEquals(largeAvatar, UserStore.identity.value.photoBase64)
    }

    @Test
    fun testDayboarderStudentWithNullHostelInfo() {
        val dayboarder = StudentIdentity(
            regNo = "21BCE2002",
            name = "Kavya Dayboarder",
            isHosteller = false,
            dayboarder = DayboarderInfo(
                isDayboarder = true,
                fields = listOf(
                    KeyValueRow("Bus Route No", "Route 12"),
                    KeyValueRow("Boarding Point", "Tambaram Gate")
                )
            )
        )

        UserStore.merge(dayboarder, IdentitySource.RECORDS)
        val current = UserStore.identity.value

        assertFalse(current.isHosteller, "isHosteller should be false for dayboarder")
        assertNotNull(current.dayboarder)
        assertTrue(current.dayboarder!!.isDayboarder)
        assertEquals(2, current.dayboarder!!.fields.size)
        assertEquals("Route 12", current.dayboarder!!.fields.first().value)
    }

    @Test
    fun testFreshmanStudentWithEmptyAddressesAndNoCredentials() {
        val freshman = StudentIdentity(
            regNo = "24BCE0001",
            name = "Freshman Student",
            currentAddress = emptyList(),
            permanentAddress = emptyList(),
            credentials = emptyList(),
            ranks = emptyList()
        )

        val encoded = json.encodeToString(freshman)
        val decoded = json.decodeFromString<StudentIdentity>(encoded)

        assertEquals("24BCE0001", decoded.regNo)
        assertTrue(decoded.currentAddress.isEmpty())
        assertTrue(decoded.credentials.isEmpty())
        assertTrue(decoded.ranks.isEmpty())
    }

    @Test
    fun testInitialsComputationFallbackRules() {
        // Two names
        assertEquals("AS", StudentIdentity(name = "Aditya Sharma").initials)
        // Three names -> take first 2
        assertEquals("MK", StudentIdentity(name = "Mohit Kumar Verma").initials)
        // Single name
        assertEquals("A", StudentIdentity(name = "Ananya").initials)
        // Null name with regNo
        assertEquals("21", StudentIdentity(name = null, regNo = "21BCE1001").initials)
        // Null name and null regNo
        assertEquals("?", StudentIdentity(name = null, regNo = null).initials)
        // Blank name fallback
        assertEquals("?", StudentIdentity(name = "   ", regNo = null).initials)
    }

    // =========================================================================
    // Tier 3: Pairwise Combinations & Cache Restoration
    // =========================================================================

    @Test
    fun testPairwiseIdentitySourceMergeAndEncryptedCacheRestoration() {
        // Multi-tier sequential merge
        UserStore.merge(StudentIdentity(regNo = "21BCE1001"), IdentitySource.SESSION)
        UserStore.merge(StudentIdentity(name = "Aditya Sharma", program = "B.Tech CSE"), IdentitySource.STUDENT)
        UserStore.merge(StudentIdentity(proctor = Official(role = "Proctor", name = "Dr. Ramesh")), IdentitySource.PROFILE_IMAGES)
        UserStore.merge(StudentIdentity(bank = BankInfo(name = "Indian Bank")), IdentitySource.BANK)

        // In-memory verification
        val beforePersist = UserStore.identity.value
        assertEquals("21BCE1001", beforePersist.regNo)
        assertEquals("Aditya Sharma", beforePersist.name)
        assertEquals("Dr. Ramesh", beforePersist.proctor?.name)
        assertEquals("Indian Bank", beforePersist.bank?.name)

        // Clear in-memory state
        UserStore.clear()
        assertEquals(StudentIdentity(), UserStore.identity.value)

        // Load from encrypted persistent cache
        UserStore.loadFromCache()
        val restored = UserStore.identity.value
        assertEquals("21BCE1001", restored.regNo)
        assertEquals("Aditya Sharma", restored.name)
        assertEquals("B.Tech CSE", restored.program)
        assertEquals("Dr. Ramesh", restored.proctor?.name)
        assertEquals("Indian Bank", restored.bank?.name)
    }

    @Test
    fun testDeepMergeOfOfficialProctorDetailsAcrossTiers() {
        // Step 1: PROFILE_IMAGES tier provides proctor photo and cabin
        UserStore.merge(
            StudentIdentity(
                proctor = Official(
                    role = "Proctor",
                    photoBase64 = "photoData123",
                    cabin = "SJT 413"
                )
            ),
            IdentitySource.PROFILE_IMAGES
        )

        // Step 2: STUDENT tier provides name, email, school
        UserStore.merge(
            StudentIdentity(
                proctor = Official(
                    role = "Proctor",
                    name = "Dr. Ramesh Babu",
                    email = "ramesh.babu@vit.ac.in",
                    school = "SCOPE"
                )
            ),
            IdentitySource.STUDENT
        )

        val proctor = UserStore.identity.value.proctor
        assertNotNull(proctor)
        assertEquals("Dr. Ramesh Babu", proctor.name)
        assertEquals("ramesh.babu@vit.ac.in", proctor.email)
        assertEquals("SCOPE", proctor.school)
        assertEquals("SJT 413", proctor.cabin)
        assertEquals("photoData123", proctor.photoBase64)
    }

    @Test
    fun testStatusCardsAndDecipheredRecordsIntegrity() {
        val records = StudentIdentity(
            regNo = "21BCE1001",
            eptTables = listOf(VtopTable(caption = "EPT", headers = listOf("Subject", "Marks"), rows = listOf(listOf("English", "85")))),
            registrationFields = listOf(KeyValueRow("Credits Registered", "24")),
            universityDayTitle = "Annual Sports & Cultural Day 2026",
            apaar = ApaarInfo(hasApaar = true, fields = listOf(KeyValueRow("Status", "Verified"))),
            bank = BankInfo(name = "SBI", branch = "VIT", address = "Katpadi")
        )

        UserStore.merge(records, IdentitySource.RECORDS)
        val identity = UserStore.identity.value

        assertEquals(1, identity.eptTables.size)
        assertEquals("English", identity.eptTables.first().rows.first().first())
        assertEquals("24", identity.registrationFields.first().value)
        assertEquals("Annual Sports & Cultural Day 2026", identity.universityDayTitle)
        assertTrue(identity.apaar?.hasApaar == true)
        assertEquals("SBI", identity.bank?.name)
    }
}
