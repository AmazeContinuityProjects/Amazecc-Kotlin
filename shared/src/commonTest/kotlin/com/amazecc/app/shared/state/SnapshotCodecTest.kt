package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.LMSAssignment
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.model.PaymentsRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stored shape is now [DomainSnapshot] (v3), so every blob already on a device has to be
 * readable and upgraded exactly once.
 *
 * The risk these cover is specific: the three schemas overlap heavily, and `kotlinx.serialization`
 * defaults every unknown field. Decoding a v3 blob as v2 would therefore *succeed* into a mostly
 * empty snapshot rather than fail - a silent wipe of everything the student had synced. So the
 * detection order is itself the thing under test.
 */
class SnapshotCodecTest {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private fun populated() = AppDataSnapshot(
        academic = AcademicData(
            semesters = mapOf(
                "CH20262701" to SemesterData(
                    semesterId = "CH20262701",
                    semesterName = "Fall Semester 2026-27",
                    gpa = "9.1",
                    courses = mapOf(
                        "BAECE203" to StoredCourse(
                            courseCode = "BAECE203",
                            courseTitle = "Analog Electronics",
                            courseType = "Embedded Theory",
                            credits = "3",
                            classId = "CH2026270102001",
                        )
                    ),
                    exams = listOf(
                        ExamItem(courseCode = "BAECE203", examDate = "2026-11-20", seatNo = "17")
                    ),
                )
            )
        ),
        payments = PaymentsResFactory(),
        lms = LMSRes(
            success = true,
            assignments = listOf(LMSAssignment(name = "BAECE203/Analog/A1")),
        ),
        tasks = listOf(
            HomeworkTask(
                id = "t1", courseCode = "BAECE203", courseTitle = "Analog",
                title = "Lab 1", dueDate = "2026-10-10", createdAt = "2026-09-01T00:00:00Z",
            )
        ),
    )

    private fun PaymentsResFactory() = PaymentsRes(
        success = true,
        walletBalance = "1200",
        payments = listOf(
            PaymentItem(
                billingId = "b1", description = "Hostel", amount = "45000", status = "Paid",
            )
        ),
    )

    // ── writing ────────────────────────────────────────────────────────────────

    @Test
    fun encodingProducesTheV3DomainSchema() {
        val encoded = SnapshotCodec.encode(populated())
        assertEquals(SnapshotCodec.Schema.V3_DOMAIN, SnapshotCodec.detect(encoded))
        assertEquals(DomainSnapshot.SCHEMA_VERSION, SnapshotCodec.decodeDomain(encoded)!!.schemaVersion)
        assertTrue("\"academics\"" in encoded, "v3 marker must be present")
        assertTrue("\"academic\":" !in encoded, "the v2 field name must be gone entirely")
    }

    @Test
    fun encodeDecodeRoundTripsTheWholeSnapshot() {
        val original = populated()
        val restored = assertNotNull(SnapshotCodec.decode(SnapshotCodec.encode(original)))
        assertEquals("CH20262701", restored.academic.semesters.keys.single())
        assertEquals("Analog Electronics", restored.academic.semesters["CH20262701"]!!.courses["BAECE203"]!!.courseTitle)
        assertEquals("17", restored.academic.semesters["CH20262701"]!!.exams.single().seatNo)
        assertEquals("1200", restored.payments!!.walletBalance)
        assertEquals(1, restored.lms!!.assignments.size)
        assertEquals("Lab 1", restored.tasks.single().title)
    }

    @Test
    fun theSelectedSemesterIsPersistedRatherThanGuessed() {
        val encoded = SnapshotCodec.encode(populated(), selectedSemesterId = "CH20262701")
        assertEquals("CH20262701", SnapshotCodec.decodeDomain(encoded)!!.academics.selectedSemesterId)
    }

    @Test
    fun aStaleSelectedSemesterIsIgnoredRatherThanStored() {
        // The stored id must name a semester that exists, or a cold start lands on nothing.
        val domain = SnapshotCodec.decodeDomain(
            SnapshotCodec.encode(populated(), selectedSemesterId = "CH19999999")
        )!!
        assertTrue(domain.academics.selectedSemesterId != "CH19999999")
    }

    // ── reading ────────────────────────────────────────────────────────────────

    @Test
    fun aV2BlobIsDetectedAndUpgraded() {
        val v2 = json.encodeToString(AppDataSnapshot.serializer(), populated())
        assertEquals(SnapshotCodec.Schema.V2_APP_DATA, SnapshotCodec.detect(v2))
        val restored = assertNotNull(SnapshotCodec.decode(v2))
        assertEquals("Analog Electronics", restored.academic.semesters["CH20262701"]!!.courses["BAECE203"]!!.courseTitle)
        assertEquals("1200", restored.payments!!.walletBalance)
    }

    @Test
    fun aV1BlobIsDetectedAndMigrated() {
        // v1 has no `academic` key and mirrors everything into four flat fields.
        val v1 = """{"attendance":{"success":true},"marks":{"success":true},"lms":{"success":true,"assignments":[{"name":"X/Y/Z"}]}}"""
        assertEquals(SnapshotCodec.Schema.V1_LEGACY, SnapshotCodec.detect(v1))
        val restored = assertNotNull(SnapshotCodec.decode(v1))
        assertEquals("X/Y/Z", restored.lms!!.assignments.single().name)
    }

    @Test
    fun anEmptyObjectIsTreatedAsV1AndYieldsTheMigratorResultNotACrash() {
        // Everything is defaulted, so this must still decode to something usable.
        val restored = assertNotNull(SnapshotCodec.decode("{}"))
        assertTrue(restored.academic.semesters.isEmpty())
    }

    @Test
    fun unreadableInputReturnsNullRatherThanAnEmptySnapshot() {
        // This distinction matters: callers treat null as "no snapshot, start fresh" but an empty
        // snapshot as "the student has no data", which is a very different thing to overwrite.
        assertNull(SnapshotCodec.decode("not json at all"))
        assertNull(SnapshotCodec.decode(""))
    }

    // ── the ordering hazard ────────────────────────────────────────────────────

    @Test
    fun v3IsNeverMistakenForV2() {
        // `"academic"` must not match inside `"academics"`. The closing quote is what stops it;
        // dropping the quotes from the marker would make this test fail and every install wipe.
        val v3 = SnapshotCodec.encode(populated())
        assertTrue("\"academics\"" in v3)
        assertTrue("\"academic\"" !in v3)
    }

    @Test
    fun v3IsNeverMistakenForV1() {
        val v3 = SnapshotCodec.encode(populated())
        assertEquals(SnapshotCodec.Schema.V3_DOMAIN, SnapshotCodec.detect(v3))
    }

    @Test
    fun decodingAV3BlobAsV2WouldProduceAnEmptySnapshotWhichIsWhyOrderMatters() {
        // Documents the hazard rather than asserting it is impossible: it *is* possible, and it is
        // silent, because every v2 field defaults. The guard is [detect] checking V3 first.
        val v3 = SnapshotCodec.encode(populated())
        val mistaken = json.decodeFromString(AppDataSnapshot.serializer(), v3)
        assertTrue(
            mistaken.academic.semesters.isEmpty(),
            "if this ever stops being empty the ordering bug would silently look harmless",
        )
    }

    @Test
    fun theVersionConstantAndTheMarkersAgree() {
        assertEquals(3, DomainSnapshot.SCHEMA_VERSION)
        assertEquals(2, AppDataSnapshot.SCHEMA_VERSION)
    }

    @Test
    fun anEmptySnapshotStillCarriesItsV3Marker() {
        // Regression: with encodeDefaults off, an all-default snapshot omitted `academics`
        // entirely, so the writer produced a v3 blob that read back as v1.
        val encoded = SnapshotCodec.encode(AppDataSnapshot())
        assertTrue("\"academics\"" in encoded, "the marker must survive an empty snapshot")
        assertEquals(SnapshotCodec.Schema.V3_DOMAIN, SnapshotCodec.detect(encoded))
        assertEquals(DomainSnapshot.SCHEMA_VERSION, SnapshotCodec.decodeDomain(encoded)!!.schemaVersion)
    }

    @Test
    fun nullDFieldsAreStillOmitted() {
        // The opposite guard on the encoder change: `academics` must be written, but the dozens of
        // null transport DTOs must not bloat every blob with `"messMenu":null`.
        val encoded = SnapshotCodec.encode(populated())
        assertTrue("\"messMenu\"" !in encoded, "absent DTOs stay absent")
        assertTrue("\"lms\"" in encoded, "present DTOs are written")
    }
}
