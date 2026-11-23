package com.amazecc.app.shared.ffcs

import kotlinx.coroutines.Deferred

/**
 * D-181: wire contract for the FFCS generator worker, ported from
 * `reference/04-generation-engine.md:626-735` (AmazeCC `logic/generator.ts` +
 * `workers/generator.worker.ts`).
 *
 * The Kotlin port has no real Web Worker: this file holds the plain-data wire
 * types, and [FfcsGeneratorRunner] supplies the coroutine-backed worker bridge
 * plus the inline fallback. Keeping the types here (with no `Set`, no class
 * instances with behaviour, no functions) preserves the structured-clone
 * boundary the web engine relies on.
 */
object FfcsWorkerContract {
    /** Only a request carrying this `type` is a stream request; everything else is bare params. */
    const val WIRE_TYPE_STREAM = "stream"

    /** Default batch flush size, matching the web engine's 50-timetable batches. */
    const val DEFAULT_BATCH_SIZE = 50

    /** Hard cap on accepted timetables, matching the web engine's 30K limit. */
    const val MAX_RESULTS = 30_000

    /** FIFO eviction size of the runId -> catalogue snapshot cache. */
    const val SNAPSHOT_CACHE_LIMIT = 8
}

/**
 * The union of replies a worker posts back to the driver. Mirrors `WorkerReply`
 * in both `logic/generator.ts:631-637` and `workers/generator.worker.ts:672-677`.
 *
 * [Batch] and [Progress] are incremental (opt-in stream) messages; [Success],
 * [Cancelled] and [Error] are terminal and settle a [GenerationHandle.result].
 */
sealed class WorkerReply {
    data class Success(
        val timetables: List<TimetableState>,
        val capped: Boolean = false
    ) : WorkerReply()

    data class Batch(val timetables: List<TimetableState>) : WorkerReply()

    data class Progress(val nodes: Int, val found: Int, val accepted: Int) : WorkerReply()

    data class Cancelled(val timetables: List<TimetableState>) : WorkerReply()

    data class Error(
        val code: FfcsErrorCode,
        val subjectCode: String? = null,
        val message: String? = null
    ) : WorkerReply()
}

/**
 * Wire request posted to the worker. [OneShot] is a bare `GenerateParams`
 * (plus optional wire fields); [Stream] is the explicit `{type:"stream", ...}`
 * envelope. Anything that is not explicitly a stream is one-shot — matching
 * `self.onmessage` (`04:698-701`), where even `{ nonsense: true }` is treated
 * as bare params.
 */
sealed class WorkerRequest {
    abstract val params: GenerationParams
    abstract val runId: String?
    abstract val droppedCodes: List<String>
    abstract val batchSize: Int
    abstract val maxResults: Int

    val isStream: Boolean get() = this is Stream
    val wireType: String? get() = if (this is Stream) FfcsWorkerContract.WIRE_TYPE_STREAM else null

    data class OneShot(
        override val params: GenerationParams = GenerationParams(),
        override val runId: String? = null,
        override val droppedCodes: List<String> = emptyList(),
        override val batchSize: Int = FfcsWorkerContract.DEFAULT_BATCH_SIZE,
        override val maxResults: Int = FfcsWorkerContract.MAX_RESULTS
    ) : WorkerRequest()

    data class Stream(
        override val params: GenerationParams = GenerationParams(),
        override val runId: String? = null,
        override val droppedCodes: List<String> = emptyList(),
        override val batchSize: Int = FfcsWorkerContract.DEFAULT_BATCH_SIZE,
        override val maxResults: Int = FfcsWorkerContract.MAX_RESULTS
    ) : WorkerRequest()
}

/**
 * Settled value of [GenerationHandle.result]. A handle **never rejects**:
 * crashes arrive as [Error], cooperative cancellation as [Cancelled].
 */
sealed class FfcsGenerateResult {
    data class Success(val timetables: List<TimetableState>, val capped: Boolean = false) : FfcsGenerateResult()
    data class Cancelled(val timetables: List<TimetableState>) : FfcsGenerateResult()
    data class Error(
        val code: FfcsErrorCode,
        val subjectCode: String? = null,
        val message: String? = null
    ) : FfcsGenerateResult()
}

/**
 * Driver-side options. Stream is strictly opt-in: it is enabled only when a
 * caller supplies [onBatch] or [onProgress] (`04:662`).
 */
data class GenerationOptions(
    val onBatch: ((List<TimetableState>) -> Unit)? = null,
    val onProgress: ((WorkerReply.Progress) -> Unit)? = null,
    val batchSize: Int = FfcsWorkerContract.DEFAULT_BATCH_SIZE,
    val maxResults: Int = FfcsWorkerContract.MAX_RESULTS,
    val runId: String? = null,
    val droppedCodes: List<String> = emptyList()
) {
    val stream: Boolean get() = onBatch != null || onProgress != null
}

/**
 * Handle returned by `startGeneration`. [result] completes with a typed
 * [FfcsGenerateResult] and never throws. [cancel] is idempotent and safe before
 * the coroutine starts and after it finishes (04:658-659).
 */
class GenerationHandle(
    val result: Deferred<FfcsGenerateResult>,
    private val cancelAction: () -> Unit
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        if (cancelled) return
        cancelled = true
        cancelAction()
    }

    val isCancelled: Boolean get() = cancelled
}

/**
 * A drop-ladder retry posts only the run id and the codes to drop — no
 * catalogue, no locks (`04:734`, `useGenerationRun.ts:81-89`). The worker
 * resolves courses from the snapshot cached under [runId].
 */
data class FfcsRetryRequest(val runId: String, val droppedCodes: List<String>) {
    fun asWorkerRequest(
        batchSize: Int = FfcsWorkerContract.DEFAULT_BATCH_SIZE,
        maxResults: Int = FfcsWorkerContract.MAX_RESULTS
    ): WorkerRequest.Stream = WorkerRequest.Stream(
        params = GenerationParams(),
        runId = runId,
        droppedCodes = droppedCodes,
        batchSize = batchSize,
        maxResults = maxResults
    )
}

/**
 * Catalogue snapshot cache keyed by run id, with 8-entry FIFO eviction
 * (`04:681-694`). The first attempt for a run seeds it; retries for that run
 * reuse the exact same catalogue and filter out only the dropped codes.
 */
class SnapshotCache {
    private val snapshots = LinkedHashMap<String, List<ParsedCourse>>()

    /** Returns the cached snapshot for [runId], seeding it on first use. */
    fun coursesForRun(params: GenerationParams, runId: String?): List<ParsedCourse> {
        if (runId == null) return params.masterCourses
        snapshots[runId]?.let { return it }
        val snapshot = params.masterCourses.toList()
        snapshots[runId] = snapshot
        if (snapshots.size > FfcsWorkerContract.SNAPSHOT_CACHE_LIMIT) {
            snapshots.keys.firstOrNull()?.let { snapshots.remove(it) }
        }
        return snapshot
    }

    /** The catalogue a run actually walks after applying its cumulative drops. */
    fun effectiveCourses(runId: String, droppedCodes: Collection<String>): List<ParsedCourse>? {
        val snapshot = snapshots[runId] ?: return null
        if (droppedCodes.isEmpty()) return snapshot
        val dropped = droppedCodes.map { it.uppercase() }.toSet()
        return snapshot.filterNot { it.code.uppercase() in dropped }
    }

    val size: Int get() = snapshots.size
    fun contains(runId: String): Boolean = snapshots.containsKey(runId)
    fun clear() = snapshots.clear()
}
