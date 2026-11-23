package com.amazecc.app.shared.ffcs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min

/**
 * D-181: coroutine-backed worker bridge + inline fallback for the FFCS
 * generator, ported from `reference/04-generation-engine.md:626-735`.
 *
 * There is no Web Worker on Kotlin/Multiplatform, so the "worker" is a
 * [Worker] seam running the same suspend core the inline fallback uses. The
 * driver ([startGeneration]) owns the [GenerationHandle], translates worker
 * replies into the typed result, and implements the two failure paths from
 * `generator.ts:227-238`:
 *  - failure before the first reply -> retry inline (headless JVM needs no worker);
 *  - failure mid-stream -> settle `cancelled` with the partial results so far.
 *
 * Cancellation is cooperative: [GenerationHandle.cancel] cancels the job, and
 * the walk checks liveness with [yield], which throws [CancellationException]
 * at the next node boundary. The partial results collected up to that point
 * are preserved in the `cancelled` reply.
 */
object FfcsGeneratorRunner {

    /** The worker seam. The default implementation is [InlineWorker]. */
    fun interface Worker {
        suspend fun run(request: WorkerRequest, emit: (WorkerReply) -> Unit)
    }

    /**
     * Default worker: the generation core itself, on the caller's coroutine
     * (the driver launches it on [Dispatchers.Default]). No platform worker is
     * required — this is the headless fallback made primary.
     */
    class InlineWorker(val cache: SnapshotCache = SnapshotCache()) : Worker {
        override suspend fun run(request: WorkerRequest, emit: (WorkerReply) -> Unit) {
            generateStreaming(
                params = request.params,
                effectiveCourses = effectiveCourses(request, cache),
                emit = emit,
                batchSize = request.batchSize,
                maxResults = request.maxResults,
                streamReplies = request.isStream
            )
        }
    }

    // --- driver ------------------------------------------------------------

    /** Start a one-shot or stream generation. Stream is opt-in via [options] callbacks. */
    fun startGeneration(
        params: GenerationParams,
        options: GenerationOptions = GenerationOptions(),
        cache: SnapshotCache = SnapshotCache(),
        worker: Worker? = null
    ): GenerationHandle {
        val request: WorkerRequest = if (options.stream) {
            WorkerRequest.Stream(
                params, options.runId, options.droppedCodes, options.batchSize, options.maxResults
            )
        } else {
            WorkerRequest.OneShot(
                params, options.runId, options.droppedCodes, options.batchSize, options.maxResults
            )
        }
        return startWithRequest(request, options, cache, worker)
    }

    /** Start a drop-ladder retry: only the run id and dropped codes are posted. */
    fun startRetry(
        retry: FfcsRetryRequest,
        options: GenerationOptions = GenerationOptions(),
        cache: SnapshotCache,
        worker: Worker? = null
    ): GenerationHandle = startWithRequest(
        retry.asWorkerRequest(options.batchSize, options.maxResults), options, cache, worker
    )

    /** Convenience matching the web `generateTimetablesAsync = startGeneration(...).result`. */
    suspend fun generateTimetablesAsync(
        params: GenerationParams,
        options: GenerationOptions = GenerationOptions()
    ): FfcsGenerateResult = startGeneration(params, options).result.await()

    private fun startWithRequest(
        request: WorkerRequest,
        options: GenerationOptions,
        cache: SnapshotCache,
        worker: Worker?
    ): GenerationHandle {
        val workerImpl = worker ?: InlineWorker(cache)
        val result = CompletableDeferred<FfcsGenerateResult>()
        val partials = mutableListOf<TimetableState>()

        fun deliver(reply: WorkerReply) {
            when (reply) {
                is WorkerReply.Batch -> {
                    partials.addAll(reply.timetables)
                    options.onBatch?.invoke(reply.timetables)
                }
                is WorkerReply.Progress -> options.onProgress?.invoke(reply)
                is WorkerReply.Success ->
                    result.complete(FfcsGenerateResult.Success(reply.timetables, reply.capped))
                is WorkerReply.Cancelled ->
                    result.complete(FfcsGenerateResult.Cancelled(reply.timetables))
                is WorkerReply.Error ->
                    result.complete(FfcsGenerateResult.Error(reply.code, reply.subjectCode, reply.message))
            }
        }

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val job: Job = scope.launch {
            try {
                // Same-tick cancel observability, mirroring `setTimeout(..., 0)`
                // in the web inline fallback (04:660).
                yield()
                var emittedAny = false
                try {
                    workerImpl.run(request) { reply ->
                        emittedAny = true
                        deliver(reply)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    if (!emittedAny) {
                        // Worker died before its first message: fall back inline
                        // (04:661, generator.ts:227-238).
                        generateStreaming(
                            params = request.params,
                            effectiveCourses = effectiveCourses(request, cache),
                            emit = { deliver(it) },
                            batchSize = request.batchSize,
                            maxResults = request.maxResults,
                            streamReplies = request.isStream
                        )
                    } else if (!result.isCompleted) {
                        // Mid-run death in stream mode: settle cancelled with partials.
                        result.complete(FfcsGenerateResult.Cancelled(partials.toList()))
                    }
                }
            } catch (ce: CancellationException) {
                if (!result.isCompleted) {
                    result.complete(FfcsGenerateResult.Cancelled(partials.toList()))
                }
            } catch (t: Throwable) {
                if (!result.isCompleted) {
                    result.complete(FfcsGenerateResult.Error(FfcsErrorCode.ERROR, message = t.message))
                }
            }
        }
        // Settle the result synchronously on cancel. A coroutine launched with
        // CoroutineStart.DEFAULT that is cancelled before it is dispatched never
        // executes its body, so the `catch (CancellationException)` above cannot
        // be relied on to complete `result` (it left the deferred pending forever).
        // Complete first with the partials collected so far — empty before start,
        // the flushed batches mid-run — then cancel the job. The `isCompleted`
        // guard keeps cancel() a no-op once a terminal Success/Error was posted.
        return GenerationHandle(result, {
            if (!result.isCompleted) {
                result.complete(FfcsGenerateResult.Cancelled(partials.toList()))
            }
            job.cancel()
        })
    }

    // --- worker bridge / generation core -----------------------------------

    /** Resolve the catalogue a request walks: snapshot first, then apply drops. */
    private fun effectiveCourses(request: WorkerRequest, cache: SnapshotCache): List<ParsedCourse> {
        val master = cache.coursesForRun(request.params, request.runId)
        if (request.droppedCodes.isEmpty()) return master
        val dropped = request.droppedCodes.map { it.uppercase() }.toSet()
        return master.filterNot { it.code.uppercase() in dropped }
    }

    /**
     * The synchronous (cooperative-suspend) walk shared by the worker bridge
     * and the inline fallback. Emits `batch`/`progress` only in stream mode;
     * terminal replies are always emitted exactly once.
     */
    private suspend fun generateStreaming(
        params: GenerationParams,
        effectiveCourses: List<ParsedCourse>,
        emit: (WorkerReply) -> Unit,
        batchSize: Int,
        maxResults: Int,
        streamReplies: Boolean
    ) {
        // Group offerings by code, preserving report order, and apply locks
        // (the first of the engine's option filters — see 04:604).
        val groups = LinkedHashMap<String, MutableList<ParsedCourse>>()
        effectiveCourses.forEach { groups.getOrPut(it.code.uppercase()) { mutableListOf() }.add(it) }
        val lockMap = params.courseLocks.associateBy { it.code.uppercase() }
        val options = LinkedHashMap<String, List<ParsedCourse>>()
        for ((code, offerings) in groups) {
            val lock = lockMap[code]
            options[code] = if (lock != null && lock.allowedOfferings.isNotEmpty()) {
                offerings.filter { it.offeringKey() in lock.allowedOfferings }
            } else {
                offerings
            }
        }

        if (options.isEmpty()) {
            emit(WorkerReply.Error(FfcsErrorCode.NO_COURSES_SELECTED))
            return
        }
        val emptyCode = options.entries.firstOrNull { it.value.isEmpty() }?.key
        if (emptyCode != null) {
            emit(WorkerReply.Error(FfcsErrorCode.NO_VALID_SLOTS, subjectCode = emptyCode))
            return
        }

        StreamingWalk(
            optionsPerCourse = options,
            codes = options.keys.toList(),
            maxResults = maxResults,
            batchSize = batchSize.coerceAtLeast(1),
            streamReplies = streamReplies,
            emit = emit
        ).run()
    }

    /** Mutable state machine for one generation walk. */
    private class StreamingWalk(
        private val optionsPerCourse: Map<String, List<ParsedCourse>>,
        private val codes: List<String>,
        private val maxResults: Int,
        private val batchSize: Int,
        private val streamReplies: Boolean,
        private val emit: (WorkerReply) -> Unit
    ) {
        val accepted = mutableListOf<TimetableState>()
        var nodes = 0
            private set
        var found = 0
            private set

        private val pending = mutableListOf<TimetableState>()
        private val combo = mutableListOf<ParsedCourse>()
        private val periods = mutableListOf<FfcsEngine.Period>()

        suspend fun run() {
            try {
                walk(0)
            } catch (ce: CancellationException) {
                // Cooperative cancel keeps the partial results collected so far.
                if (streamReplies && pending.isNotEmpty()) emit(WorkerReply.Batch(pending.toList()))
                pending.clear()
                if (streamReplies) emit(WorkerReply.Progress(nodes, found, accepted.size))
                emit(WorkerReply.Cancelled(accepted.toList()))
                return
            }

            if (streamReplies) emit(WorkerReply.Progress(nodes, found, accepted.size))
            if (pending.isNotEmpty()) emit(WorkerReply.Batch(pending.toList()))
            pending.clear()

            if (accepted.isEmpty()) {
                emit(WorkerReply.Error(FfcsErrorCode.NO_CONFLICT_FREE))
            } else {
                val capped = maxResults != Int.MAX_VALUE && found >= maxResults
                emit(WorkerReply.Success(accepted.toList(), capped))
            }
        }

        private suspend fun walk(index: Int) {
            if (accepted.size >= maxResults) return
            yield() // cooperative cancellation + fairness at every node boundary
            nodes++
            if (index == codes.size) {
                found++
                val timetable = buildTimetable(accepted.size)
                accepted.add(timetable)
                pending.add(timetable)
                if (pending.size >= batchSize) {
                    if (streamReplies) emit(WorkerReply.Progress(nodes, found, accepted.size))
                    emit(WorkerReply.Batch(pending.toList()))
                    pending.clear()
                }
                return
            }
            for (offering in optionsPerCourse[codes[index]].orEmpty()) {
                val offeringPeriods = FfcsEngine.getPeriodsForSlot(offering.slot)
                if (overlaps(offeringPeriods, periods)) continue
                combo.add(offering)
                periods.addAll(offeringPeriods)
                walk(index + 1)
                repeat(offeringPeriods.size) { periods.removeLast() }
                combo.removeLast()
                if (accepted.size >= maxResults) return
            }
        }

        private fun buildTimetable(index: Int): TimetableState {
            val added = combo.mapIndexed { i, course ->
                AddedCourse(
                    id = "tt${index}_${course.code}_$i",
                    code = course.code,
                    title = course.title,
                    slots = course.slot.split("+").map { it.trim() },
                    faculty = course.faculty,
                    venue = course.room,
                    credits = course.credits,
                    type = course.type,
                    color = FfcsConstants.COLORS[i % FfcsConstants.COLORS.size]
                )
            }
            return TimetableState(
                id = "tt_$index",
                name = "TT ${index + 1}",
                courses = added,
                metrics = FfcsMetrics.calculateTimetableMetrics(added),
                firstStartMin = periods.minOfOrNull { it.startMin } ?: 0,
                lastEndMin = periods.maxOfOrNull { it.endMin } ?: 0
            )
        }

        private fun overlaps(a: List<FfcsEngine.Period>, b: List<FfcsEngine.Period>): Boolean {
            for (pa in a) {
                for (pb in b) {
                    if (pa.day == pb.day && max(pa.startMin, pb.startMin) < min(pa.endMin, pb.endMin)) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
