package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.buildAllGradesFromGradeHistory
import com.amazecc.app.shared.model.CgpaSummary
import com.amazecc.app.shared.model.GradeCounts
import com.amazecc.app.shared.model.GradeHistoryCourse
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.vtop.VtopGradeHistoryResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Cold-start behaviour of the published CGPA in both the snapshot and the grade-payload flow. */
class PublishedCgpaStoreTest {

    private val gradeCacheKey = "cache_all_grades"

    private fun clearPersisted() {
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        SettingsManager.remove(gradeCacheKey)
        SettingsManager.remove(SettingsManager.KEY_SELECTED_SEMESTER)
    }

    @BeforeTest
    fun setUp() {
        clearPersisted()
        AppDataStore.clear()
    }

    @AfterTest
    fun tearDown() {
        AppDataStore.clear()
        clearPersisted()
    }

    private fun localResponse(cgpa: String) = buildAllGradesFromGradeHistory(
        VtopGradeHistoryResult(
            ok = true,
            courses = listOf(
                GradeHistoryCourse(
                    code = "BCSE101",
                    title = "Programming and Data Structures",
                    type = "Theory Only",
                    credits = "4",
                    grade = "A"
                )
            ),
            cgpa = CgpaSummary(
                creditsRegistered = "160",
                creditsEarned = "158",
                cgpa = cgpa,
                grades = GradeCounts(S = 12, A = 30, B = 8)
            )
        ),
        "CH20262701"
    )

    @Test
    fun upsertPublishesCgpaOnBothTheSnapshotAndTheGradeFlow() {
        AppDataStore.upsertGrades(localResponse("8.74"))

        assertEquals("8.74", AppDataStore.data.value.academic.publishedCgpa?.cgpa)
        assertEquals("8.74", AppDataStore.allGrades.value?.publishedCgpa)
        assertEquals(8, AppDataStore.data.value.academic.publishedCgpa?.grades?.B)
    }

    @Test
    fun coldStartRestoreBringsBackPublishedCgpaAndTheFullGradePayload() {
        AppDataStore.upsertGrades(localResponse("8.74"))

        // Simulate a fresh process: memory is empty, the persisted snapshot/cache are not.
        AppDataStore.clear()
        assertNull(AppDataStore.data.value.academic.publishedCgpa)
        assertNull(AppDataStore.allGrades.value)

        AppDataStore.restore()

        val cgpa = assertNotNull(AppDataStore.data.value.academic.publishedCgpa)
        assertEquals("8.74", cgpa.cgpa)
        assertEquals("160", cgpa.creditsRegistered)
        assertEquals("158", cgpa.creditsEarned)
        assertEquals(12, cgpa.grades?.S)
        assertEquals(30, cgpa.grades?.A)
        assertEquals("8.74", AppDataStore.allGrades.value?.publishedCgpa)
        assertNotNull(
            AppDataStore.allGrades.value?.allSemesters?.get("CH20262701"),
            "the full payload, not just the number, is restored"
        )
    }

    @Test
    fun loadPersistedSnapshotAlsoRestoresTheGradeFlow() {
        AppDataStore.upsertGrades(localResponse("9.01"))
        AppDataStore.clear()

        val snapshot = AppDataStore.loadPersistedSnapshot()

        assertEquals("9.01", snapshot.academic.publishedCgpa?.cgpa)
        assertEquals("9.01", AppDataStore.allGrades.value?.publishedCgpa)
    }

    @Test
    fun clearResetsBothPublishedCgpaReads() {
        AppDataStore.upsertGrades(localResponse("8.74"))

        AppDataStore.clear()

        assertNull(AppDataStore.data.value.academic.publishedCgpa)
        assertNull(AppDataStore.allGrades.value)
    }

    @Test
    fun snapshotCgpaAloneIsEnoughWhenTheFullPayloadCacheIsMissing() {
        AppDataStore.upsertGrades(localResponse("7.88"))
        SettingsManager.remove(gradeCacheKey)
        AppDataStore.clear()

        AppDataStore.restore()

        assertEquals("7.88", AppDataStore.data.value.academic.publishedCgpa?.cgpa)
        assertEquals("7.88", AppDataStore.allGrades.value?.publishedCgpa)
    }
}
