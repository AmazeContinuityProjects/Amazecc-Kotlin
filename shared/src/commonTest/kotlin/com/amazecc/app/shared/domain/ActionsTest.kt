package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppDataStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layer 3 contract tests: a command either changes something and says so, or says why it did not.
 *
 * `commonTest` has no `kotlinx-coroutines-test`, so [runSuspend] starts the coroutine directly.
 * Every runner used here completes without suspending, which is what makes that safe.
 *
 * [AppDataStore] and [SettingsManager] are singletons shared by the whole test JVM, so each test
 * seeds them explicitly and clears them afterwards rather than relying on ordering - the same
 * discipline [com.amazecc.app.shared.state.AppDataStoreUpgradeTest] uses.
 */
class ActionsTest {

    private val task = HomeworkTask(
        id = "t1",
        courseCode = "BAMAT209",
        courseTitle = "Applied Mathematics",
        title = "Problem set 4",
        dueDate = "2026-10-05",
        createdAt = "2026-10-01 09:00",
    )

    @BeforeTest
    fun seed() {
        Actions.registerRunner(null)
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        AppDataStore.clear()
    }

    @AfterTest
    fun cleanup() {
        Actions.registerRunner(null)
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        AppDataStore.clear()
    }

    // ── completeTask ──

    @Test
    fun completeTaskWritesTheFlag() {
        AppDataStore.addTask(task)
        val outcome = Actions.completeTask("t1", done = true)
        assertEquals(SyncOutcome.Synced(listOf("Tasks")), outcome)
        assertTrue(AppDataStore.data.value.tasks.single().completed)
    }

    @Test
    fun completeTaskCanUndo() {
        AppDataStore.addTask(task.copy(completed = true))
        val outcome = Actions.completeTask("t1", done = false)
        assertEquals(SyncOutcome.Synced(listOf("Tasks")), outcome)
        assertFalse(AppDataStore.data.value.tasks.single().completed)
    }

    @Test
    fun completeTaskIsIdempotent() {
        AppDataStore.addTask(task)
        Actions.completeTask("t1", done = true)
        assertEquals(SyncOutcome.Skipped, Actions.completeTask("t1", done = true))
        assertTrue(AppDataStore.data.value.tasks.single().completed)
    }

    @Test
    fun completeTaskReportsUnknownIdInsteadOfSilentlySucceeding() {
        val outcome = Actions.completeTask("nope", done = true)
        assertIs<SyncOutcome.Failed>(outcome)
        assertEquals("nope", outcome.module)
        assertTrue(AppDataStore.data.value.tasks.isEmpty())
    }

    // ── removeTask ──

    @Test
    fun removeTaskDeletesIt() {
        AppDataStore.addTask(task)
        assertEquals(SyncOutcome.Synced(listOf("Tasks")), Actions.removeTask("t1"))
        assertTrue(AppDataStore.data.value.tasks.isEmpty())
    }

    @Test
    fun removeTaskReportsUnknownId() {
        assertIs<SyncOutcome.Failed>(Actions.removeTask("nope"))
    }

    // ── clearCache ──

    @Test
    fun clearCacheDropsBothTheMemoryCopyAndThePersistedKey() {
        AppDataStore.addTask(task)
        assertTrue(SettingsManager.getNullableString(SettingsManager.CACHE_APP_DATA) != null)

        assertTrue(Actions.clearCache().isSuccess)

        assertTrue(AppDataStore.data.value.tasks.isEmpty())
        assertNull(SettingsManager.getNullableString(SettingsManager.CACHE_APP_DATA))
    }

    // ── sync: the seam ──

    @Test
    fun syncAnswersSkippedWhileNoRunnerIsRegistered() {
        assertEquals(SyncOutcome.Skipped, runSuspend { Actions.sync(SyncScope()) })
    }

    @Test
    fun syncForwardsTheScopeUnchanged() {
        var seen: SyncScope? = null
        Actions.registerRunner { scope ->
            seen = scope
            SyncOutcome.Synced(listOf("Timetable"))
        }
        val asked = SyncScope(semesterId = "CH20262701", modules = setOf("Timetable"), force = true)

        assertEquals(SyncOutcome.Synced(listOf("Timetable")), runSuspend { Actions.sync(asked) })
        assertEquals(asked, seen)
    }

    @Test
    fun syncTurnsAThrowingRunnerIntoAFailureRatherThanAcrossTheBoundary() {
        Actions.registerRunner { throw IllegalStateException("network down") }
        val outcome = runSuspend { Actions.sync(SyncScope(modules = setOf("Grades"))) }

        assertIs<SyncOutcome.Failed>(outcome)
        assertEquals("Grades", outcome.module)
        assertEquals("network down", outcome.reason)
    }

    @Test
    fun syncStillEscapesCancellation() {
        Actions.registerRunner { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> {
            runSuspend { Actions.sync(SyncScope()) }
        }
    }

    @Test
    fun refreshCurriculumAsksForCurriculumOnly() {
        var seen: SyncScope? = null
        Actions.registerRunner { scope ->
            seen = scope
            SyncOutcome.Skipped
        }
        runSuspend { Actions.refreshCurriculum("CH20262701") }

        assertEquals(SyncScope(semesterId = "CH20262701", modules = setOf("Curriculum"), force = true), seen)
    }

    // ── SyncScope ──

    @Test
    fun scopeLabelNamesTheModulesItWasGiven() {
        assertEquals("Grades+Timetable", SyncScope(modules = setOf("Timetable", "Grades")).label())
    }

    @Test
    fun scopeLabelFallsBackToTheSemesterThenToAll() {
        assertEquals("semester:CH20262701", SyncScope(semesterId = "CH20262701").label())
        assertEquals("all", SyncScope().label())
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var out: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
        return out!!.getOrThrow()
    }
}
