package com.amazecc.app.shared.ffcs

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-181: contract tests for the worker/stream port in
 * `reference/04-generation-engine.md:626-735`.
 *
 * Test data uses five slot tokens (S11/S1/S4/S2/S15), each of which occurs on
 * exactly one weekday, so every offering is on a distinct day. That makes all
 * combos where five courses pick five distinct tokens clash-free: 5! = 120
 * accepted timetables, deterministic with no RNG.
 */
class FfcsWorkerProtocolTest {

    private val days = listOf("S11", "S1", "S4", "S2", "S15")

    private fun sampleParams(): GenerationParams = GenerationParams(
        masterCourses = (0 until 5).flatMap { c ->
            days.map { slot ->
                ParsedCourse(
                    code = "C$c",
                    title = "Course $c",
                    type = "Theory",
                    credits = "3",
                    room = "R$c-$slot",
                    slot = slot,
                    faculty = "F$c"
                )
            }
        }
    )

    private fun partial(id: String) = TimetableState(id = id, name = id, courses = emptyList())

    // --- wire shape --------------------------------------------------------

    @Test
    fun wireRequest_discriminatesStreamFromBareParams() {
        val bare = WorkerRequest.OneShot(sampleParams())
        val stream = WorkerRequest.Stream(sampleParams(), runId = "r", droppedCodes = listOf("C0"))

        assertNull(bare.wireType)
        assertFalse(bare.isStream)
        assertEquals(FfcsWorkerContract.WIRE_TYPE_STREAM, stream.wireType)
        assertTrue(stream.isStream)
        assertEquals("r", stream.runId)
        assertEquals(listOf("C0"), stream.droppedCodes)

        assertEquals(FfcsWorkerContract.DEFAULT_BATCH_SIZE, 50)
        assertEquals(FfcsWorkerContract.MAX_RESULTS, 30_000)
        assertEquals(FfcsWorkerContract.SNAPSHOT_CACHE_LIMIT, 8)
    }

    @Test
    fun workerReply_unionCarriesAllFiveVariants() {
        val replies: List<WorkerReply> = listOf(
            WorkerReply.Success(emptyList(), capped = true),
            WorkerReply.Batch(listOf(partial("b"))),
            WorkerReply.Progress(nodes = 1, found = 2, accepted = 3),
            WorkerReply.Cancelled(listOf(partial("c"))),
            WorkerReply.Error(FfcsErrorCode.NO_VALID_SLOTS, subjectCode = "CSE2001", message = "oops")
        )
        assertEquals(5, replies.size)
        assertTrue(replies[0] is WorkerReply.Success && (replies[0] as WorkerReply.Success).capped)
        assertEquals(3, (replies[2] as WorkerReply.Progress).accepted)
        assertEquals("CSE2001", (replies[4] as WorkerReply.Error).subjectCode)
    }

    // --- stream == one-shot ------------------------------------------------

    @Test
    fun stream_resultMatchesOneShot() = runBlocking {
        val oneShot = FfcsGeneratorRunner.generateTimetablesAsync(sampleParams())
        val streamed = FfcsGeneratorRunner.generateTimetablesAsync(
            sampleParams(),
            GenerationOptions(batchSize = 7, onBatch = {}, onProgress = {})
        )
        assertEquals(oneShot, streamed)
        val success = oneShot as FfcsGenerateResult.Success
        assertEquals(120, success.timetables.size)
        assertFalse(success.capped)
    }

    // --- batching ----------------------------------------------------------

    @Test
    fun batches_respectBatchSize_andEmitEachAcceptedOnce() = runBlocking {
        val batches = mutableListOf<List<TimetableState>>()
        val result = FfcsGeneratorRunner.generateTimetablesAsync(
            sampleParams(),
            GenerationOptions(batchSize = 7, onBatch = { batches.add(it) })
        ) as FfcsGenerateResult.Success

        assertTrue(batches.isNotEmpty())
        assertTrue(batches.all { it.size <= 7 }, "Every batch must be <= batchSize.")
        val flattened = batches.flatten()
        assertEquals(result.timetables, flattened, "Batches must partition the success result in order.")
        assertEquals(flattened.size, flattened.distinctBy { it.id }.size, "Each accepted emitted once.")
    }

    @Test
    fun progress_isMonotonic() = runBlocking {
        val seen = mutableListOf<WorkerReply.Progress>()
        FfcsGeneratorRunner.generateTimetablesAsync(
            sampleParams(),
            GenerationOptions(batchSize = 5, onProgress = { seen.add(it) })
        )
        assertTrue(seen.isNotEmpty())
        for (i in 1 until seen.size) {
            assertTrue(seen[i].nodes >= seen[i - 1].nodes, "nodes must not decrease")
            assertTrue(seen[i].found >= seen[i - 1].found, "found must not decrease")
            assertTrue(seen[i].accepted >= seen[i - 1].accepted, "accepted must not decrease")
        }
        assertEquals(120, seen.last().accepted)
    }

    @Test
    fun capped_whenFoundReachesFiniteLimit() = runBlocking {
        val result = FfcsGeneratorRunner.generateTimetablesAsync(
            sampleParams(),
            GenerationOptions(maxResults = 10)
        ) as FfcsGenerateResult.Success
        assertEquals(10, result.timetables.size)
        assertTrue(result.capped)
    }

    // --- cancellation ------------------------------------------------------

    @Test
    fun cancel_beforeStart_isIdempotent() = runBlocking {
        val handle = FfcsGeneratorRunner.startGeneration(sampleParams())
        handle.cancel()
        handle.cancel() // idempotent before start
        val result = withTimeout(5_000) { handle.result.await() }
        assertTrue(result is FfcsGenerateResult.Cancelled)
        assertTrue(handle.isCancelled)
    }

    @Test
    fun cancel_afterFinish_leavesSuccessUnchanged() = runBlocking {
        val handle = FfcsGeneratorRunner.startGeneration(sampleParams())
        val before = withTimeout(5_000) { handle.result.await() }
        assertTrue(before is FfcsGenerateResult.Success)
        handle.cancel()
        handle.cancel() // idempotent after finish
        assertEquals(before, handle.result.await(), "Cancel after finish must be a no-op on the result.")
    }

    @Test
    fun cancel_midRun_keepsPartialResults() = runBlocking {
        // Gate the first batch so the walk is provably still running when we cancel.
        val reached = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        var first = true
        var handle: GenerationHandle? = null
        handle = FfcsGeneratorRunner.startGeneration(
            sampleParams(),
            GenerationOptions(batchSize = 2, onBatch = {
                if (first) {
                    first = false
                    reached.countDown()
                    proceed.await(5, TimeUnit.SECONDS) // blocks the worker thread until the test cancels
                }
            })
        )
        assertTrue(reached.await(5, TimeUnit.SECONDS))
        handle!!.cancel()
        proceed.countDown()

        val result = withTimeout(5_000) { handle!!.result.await() }
        assertTrue(result is FfcsGenerateResult.Cancelled, "expected cancelled, got $result")
        val partials = result.timetables
        assertTrue(partials.isNotEmpty(), "Cancellation must keep the timetables accepted so far.")
        assertTrue(partials.size < 120, "The walk was cancelled before completion.")
    }

    // --- worker failures ---------------------------------------------------

    @Test
    fun workerFailure_beforeFirstMessage_fallsBackInline() = runBlocking {
        val crashing = FfcsGeneratorRunner.Worker { _, _ -> throw IllegalStateException("worker crashed") }
        val expected = FfcsGeneratorRunner.generateTimetablesAsync(sampleParams())
        val result = FfcsGeneratorRunner.startGeneration(sampleParams(), worker = crashing)
            .result.await()
        assertEquals(expected, result)
        assertTrue(result is FfcsGenerateResult.Success)
    }

    @Test
    fun workerFailure_midStream_settlesCancelledWithPartials() = runBlocking {
        val midFail = FfcsGeneratorRunner.Worker { _, emit ->
            emit(WorkerReply.Batch(listOf(partial("p1"), partial("p2"))))
            throw IllegalStateException("worker died mid-run")
        }
        val result = FfcsGeneratorRunner.startGeneration(sampleParams(), worker = midFail)
            .result.await()
        assertTrue(result is FfcsGenerateResult.Cancelled)
        assertEquals(listOf("p1", "p2"), result.timetables.map { it.id })
    }

    // --- snapshot cache / retries ------------------------------------------

    @Test
    fun snapshotCache_evictsOldestAfterEightEntries() {
        val cache = SnapshotCache()
        for (i in 0 until 9) {
            cache.coursesForRun(
                GenerationParams(listOf(partialCourse("C$i"))),
                "run-$i"
            )
        }
        assertEquals(8, cache.size)
        assertFalse(cache.contains("run-0"), "run-0 is the oldest and must be evicted first.")
        assertTrue(cache.contains("run-1"))
        assertTrue(cache.contains("run-8"))
    }

    @Test
    fun retry_postsOnlyDroppedCodes_andResolvesFromSnapshot() = runBlocking {
        val cache = SnapshotCache()
        cache.coursesForRun(sampleParams(), "ladder")

        val retry = FfcsRetryRequest("ladder", listOf("C4"))
        val request = retry.asWorkerRequest()
        assertTrue(request.isStream)
        assertEquals("ladder", request.runId)
        assertEquals(listOf("C4"), request.droppedCodes)
        assertTrue(request.params.masterCourses.isEmpty(), "The retry posts no catalogue.")

        val effective = cache.effectiveCourses("ladder", retry.droppedCodes)
        assertNotNull(effective)
        assertEquals(20, effective!!.size, "5 courses x 5 offerings minus the dropped course.")

        val result = FfcsGeneratorRunner.startRetry(retry, cache = cache).result.await()
        assertTrue(result is FfcsGenerateResult.Success)
        assertTrue(result.timetables.all { tt -> tt.courses.none { it.code == "C4" } })
    }

    private fun partialCourse(code: String) = ParsedCourse(
        code = code, title = code, type = "Theory", credits = "3",
        room = "R", slot = "A1", faculty = "F"
    )
}
