package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.model.LMSAssignment
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.security.Encryption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The upgrade path a real install actually takes, end to end: bytes on disk → [AppDataStore.restore]
 * → in-memory snapshot → write again.
 *
 * [SnapshotCodecTest] covers the codec in isolation; this covers the parts around it that a real
 * upgrade also has to survive - the encrypted-at-rest wrapper, the marker reaching the store, and
 * the re-persist that makes the migration one-way.
 *
 * [AppDataStore] and [SettingsManager] are singletons shared by the whole test JVM, so each test
 * seeds the key explicitly and clears it afterwards rather than relying on ordering.
 */
class AppDataStoreUpgradeTest {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    @BeforeTest
    fun clear() {
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        SettingsManager.remove(SettingsManager.KEY_SELECTED_SEMESTER)
        AppDataStore.clear()
    }

    @AfterTest
    fun cleanup() {
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        SettingsManager.remove(SettingsManager.KEY_SELECTED_SEMESTER)
        AppDataStore.clear()
    }

    private fun legacyV2() = AppDataSnapshot(
        academic = AcademicData(
            semesters = mapOf(
                "CH20262701" to SemesterData(
                    semesterId = "CH20262701",
                    semesterName = "Fall Semester 2026-27",
                    gpa = "8.9",
                    courses = mapOf(
                        "BAECE203" to StoredCourse(courseCode = "BAECE203", courseTitle = "Analog Electronics")
                    ),
                )
            )
        ),
        // The modules the first fromLegacy silently dropped.
        payments = com.amazecc.app.shared.model.PaymentsRes(success = true, walletBalance = "999"),
        laundrySchedule = com.amazecc.app.shared.model.LaundryRes(
            list = listOf(com.amazecc.app.shared.model.LaundrySlotItem(Date = "2026-10-11", RoomNumber = "B204"))
        ),
        tasks = listOf(
            com.amazecc.app.shared.model.HomeworkTask(
                id = "t1", courseCode = "BAECE203", courseTitle = "Analog",
                title = "Lab 1", dueDate = "2026-10-10", createdAt = "2026-09-01T00:00:00Z",
            )
        ),
    )

    private fun write(plain: String) {
        SettingsManager.setString(SettingsManager.CACHE_APP_DATA, Encryption.encryptOrPlain(plain))
    }

    private fun readPlain(): String? =
        SettingsManager.getNullableString(SettingsManager.CACHE_APP_DATA)
            ?.let { Encryption.decryptOrPlain(it) }

    // ── the upgrade path ───────────────────────────────────────────────────────

    @Test
    fun aStoredV2BlobIsRestoredAndImmediatelyRewrittenAsV3() {
        write(json.encodeToString(AppDataSnapshot.serializer(), legacyV2()))
        assertTrue("\"academic\"" in assertNotNull(readPlain()), "seeded as v2")

        AppDataStore.restore()

        val restored = AppDataStore.data.value
        assertEquals("Analog Electronics", restored.academic.semesters["CH20262701"]!!.courses["BAECE203"]!!.courseTitle)

        // And the migration is one-way: the same key now holds a v3 blob.
        val rewritten = assertNotNull(readPlain())
        assertEquals(SnapshotCodec.Schema.V3_DOMAIN, SnapshotCodec.detect(rewritten))
        assertEquals(DomainSnapshot.SCHEMA_VERSION, SnapshotCodec.decodeDomain(rewritten)!!.schemaVersion)
    }

    @Test
    fun theV2ToV3UpgradeDoesNotLoseTheModulesTheOldBridgeDropped() {
        // This is the whole reason fromLegacy was rewritten: promoted as-is, these three fields
        // would have been deleted on upgrade with no error anywhere.
        write(json.encodeToString(AppDataSnapshot.serializer(), legacyV2()))
        AppDataStore.restore()

        val restored = AppDataStore.data.value
        assertEquals("999", restored.payments!!.walletBalance, "payments survived")
        assertEquals("B204", restored.laundrySchedule!!.list.single().RoomNumber, "laundry survived")
        assertEquals("Lab 1", restored.tasks.single().title, "tasks survived")
    }

    @Test
    fun aStoredV1BlobIsMigratedAllTheWayToV3() {
        write("""{"lms":{"success":true,"assignments":[{"name":"X/Y/Z"}]}}""")
        AppDataStore.restore()
        assertEquals("X/Y/Z", AppDataStore.data.value.lms!!.assignments.single().name)
        assertEquals(
            SnapshotCodec.Schema.V3_DOMAIN,
            SnapshotCodec.detect(assertNotNull(readPlain())),
        )
    }

    @Test
    fun aSecondRestoreIsANoOpAndKeepsTheSameSchema() {
        write(SnapshotCodec.encode(legacyV2()))
        AppDataStore.restore()
        val first = assertNotNull(readPlain())

        AppDataStore.restore()
        assertEquals(first, readPlain(), "reading an already-v3 blob must not rewrite it")
    }

    @Test
    fun theChosenSemesterSurvivesAWriteReadCycle() {
        SettingsManager.setString(SettingsManager.KEY_SELECTED_SEMESTER, "CH20262701")
        write(SnapshotCodec.encode(legacyV2()))
        AppDataStore.restore()

        assertEquals(
            "CH20262701",
            SnapshotCodec.decodeDomain(assertNotNull(readPlain()))!!.academics.selectedSemesterId,
        )
    }

    @Test
    fun withNoStoredSnapshotNothingIsWritten() {
        AppDataStore.restore()
        assertEquals(null, readPlain(), "a fresh install must not fabricate a snapshot")
    }

    @Test
    fun anUnreadableBlobLeavesTheStoreEmptyRatherThanThrowing() {
        write("}{ not json")
        AppDataStore.restore()
        assertTrue(AppDataStore.data.value.academic.semesters.isEmpty())
    }

    // ── the widget/notification path ───────────────────────────────────────────

    @Test
    fun loadPersistedSnapshotReadsTheSameWayWithoutDisturbingMemory() {
        write(SnapshotCodec.encode(legacyV2()))
        AppDataStore.clear()
        assertTrue(AppDataStore.data.value.academic.semesters.isEmpty(), "memory cleared")

        val loaded = AppDataStore.loadPersistedSnapshot()
        assertEquals("Analog Electronics", loaded.academic.semesters["CH20262701"]!!.courses["BAECE203"]!!.courseTitle)
        assertTrue(
            AppDataStore.data.value.academic.semesters.isEmpty(),
            "a side-effect-free read must not populate the live store",
        )
    }

    @Test
    fun loadPersistedSnapshotUpgradesAnOldBlobToo() {
        // Widgets run in the same process but must see the same data as the app, including from a
        // blob written before this upgrade existed.
        write(json.encodeToString(AppDataSnapshot.serializer(), legacyV2()))
        val loaded = AppDataStore.loadPersistedSnapshot()
        assertEquals("999", loaded.payments!!.walletBalance)
    }

    // ── backup is a separate contract ──────────────────────────────────────────

    @Test
    fun backupExportStaysOnTheV2ShapeSoOlderBackupsRemainImportable() {
        AppDataStore.restore()
        AppDataStore.importSnapshot(legacyV2())

        val exported = AppDataStore.exportSnapshot()
        assertEquals(
            SnapshotCodec.Schema.V2_APP_DATA,
            SnapshotCodec.detect(exported),
            "the backup envelope has its own formatVersion and must not be silently changed",
        )
        assertEquals("999", assertNotNull(json.decodeFromString(AppDataSnapshot.serializer(), exported)).payments!!.walletBalance)
    }

    @Test
    fun anImportedBackupIsPersistedAsV3Afterwards() {
        AppDataStore.importSnapshot(legacyV2())
        AppDataStore.persistNow()
        assertEquals(
            SnapshotCodec.Schema.V3_DOMAIN,
            SnapshotCodec.detect(assertNotNull(readPlain())),
        )
        assertEquals("999", AppDataStore.data.value.payments!!.walletBalance)
    }

    @Test
    fun aModuleSetterFlowsThroughToTheV3Blob() {
        AppDataStore.setLms(LMSRes(success = true, assignments = listOf(LMSAssignment(name = "A/B/C"))))
        val domain = SnapshotCodec.decodeDomain(assertNotNull(readPlain()))!!
        assertEquals("A/B/C", domain.services.lms!!.assignments.single().name)
    }
}
