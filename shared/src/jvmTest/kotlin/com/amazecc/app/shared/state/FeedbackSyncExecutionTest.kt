package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.FeedbackStatusRes
import com.amazecc.app.shared.model.FeedbackTableRow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-156: execution-level proof that the canonical feedback pipeline really fetches, caches and
 * reports a result. The injectable fetcher AND the cached flow are restored in `finally` so no
 * fake payload leaks into a later test or into production.
 *
 * The `retryModule(SyncModule.FEEDBACK_STATUS)` route is verified by source-level review: it
 * delegates to [AppState.refreshFeedbackStatus], which calls the same
 * [AppState.syncFeedbackStatusModule] helper exercised below — no fragile reflection.
 */
class FeedbackSyncExecutionTest {

    @Test
    fun syncFeedbackStatusModule_runsInjectedFetchOnceAndCachesRealPayload() = runTest {
        val originalFetcher = AppState.feedbackStatusFetcher
        val originalStatus = AppDataStore.feedbackStatus.value
        val seenSemesters = mutableListOf<String>()
        val payload = FeedbackStatusRes(
            success = true,
            semesters = emptyList(),
            feedbackTable = listOf(
                FeedbackTableRow(
                    feedbackType = "Course Feedback — कक्षा",
                    midSemester = "Given",
                    teeSemester = "Not Given"
                )
            )
        )
        try {
            AppState.feedbackStatusFetcher = { sem ->
                seenSemesters += sem
                payload
            }

            val result = AppState.syncFeedbackStatusModule("TESTSEM")

            assertEquals(listOf("TESTSEM"), seenSemesters)
            // The non-ASCII row survives intact: no folding, no fabricated status.
            assertEquals(payload, AppDataStore.feedbackStatus.value)
            assertTrue(result.success)
        } finally {
            AppState.feedbackStatusFetcher = originalFetcher
            AppDataStore.setFeedbackStatus(originalStatus)
        }
    }
}
