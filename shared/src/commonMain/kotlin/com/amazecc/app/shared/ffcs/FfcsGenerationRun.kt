package com.amazecc.app.shared.ffcs

import androidx.compose.runtime.Immutable

/**
 * Typed generation failure codes — the exact wire strings used by the FFCS
 * web engine (`ffcsGenerate.ts`). [ERROR] is the generic solver crash and is
 * never retried; [NO_COURSES_SELECTED] is a no-op outcome, also never retried.
 */
enum class FfcsErrorCode(val wire: String) {
    NO_COURSES_SELECTED("no_courses_selected"),
    NO_VALID_SLOTS("no_valid_slots"),
    NO_CONFLICT_FREE("no_conflict_free"),
    BELOW_MIN_HALF_DAYS("below_min_half_days"),
    BELOW_MIN_FACULTY_SCORES("below_min_faculty_scores"),
    NO_SEPARATE_LABS("no_separate_labs"),
    NO_CONSECUTIVE_LABS("no_consecutive_labs"),
    ERROR("error");

    companion object {
        fun fromWire(value: String?): FfcsErrorCode? = values().firstOrNull { it.wire == value }
    }
}

/**
 * The part of the generator parameters the drop ladder touches.
 *
 * Mirrors the web app's `withCoursesDropped` (`useGenerationRun.ts:81-89`): a
 * retry must remove the dropped codes from **both** the master catalogue and
 * the course locks, so a lock on a dropped course cannot resurrect it.
 */
@Immutable
data class GenerationParams(
    val masterCourses: List<ParsedCourse> = emptyList(),
    val courseLocks: List<CourseLock> = emptyList()
) {
    fun withCoursesDropped(codes: Collection<String>): GenerationParams {
        if (codes.isEmpty()) return this
        val dropped = codes.toSet()
        return copy(
            masterCourses = masterCourses.filterNot { it.code in dropped },
            courseLocks = courseLocks.filterNot { it.code in dropped }
        )
    }
}

/**
 * Generation-run plumbing for the drop ladder, kept separate from the pure
 * policy in [DropPolicy].
 *
 * Contract (ported from `hooks/useGenerationRun.ts` + `logic/generator.ts`):
 *  - one `runId` and one catalogue snapshot are reused for the whole ladder;
 *  - retries post **only** `droppedCodes` — the worker resolves courses from
 *    the snapshot cached under that `runId`;
 *  - no retry on `cancelled`, on a crash (`code == "error"`), or once the
 *    caller has streamed a batch;
 *  - the budget is [DEFAULT_MAX_DROPS] by default.
 */
object FfcsGenerationRun {

    /** Wire payload a retry posts: no courses, no locks — just the id and codes. */
    @Immutable
    data class RetryRequest(
        val runId: String,
        val droppedCodes: List<String>
    )

    /** State for a single attempt in the ladder. */
    @Immutable
    data class Attempt(
        val params: GenerationParams,
        val runId: String,
        val droppedCodes: List<String> = emptyList()
    ) {
        /** The retry message derived from this attempt (posted only on a retry). */
        val retryWire: RetryRequest get() = RetryRequest(runId, droppedCodes)
    }

    /** Terminal shape of one attempt, matching the web app's `GenerateResult`. */
    sealed class Outcome {
        data class Success(val timetableCount: Int = 0, val capped: Boolean = false) : Outcome()
        data class Cancelled(val timetableCount: Int = 0) : Outcome()
        data class Error(val code: FfcsErrorCode, val subjectCode: String? = null) : Outcome()

        /** Adapt this outcome to the structural shape [isRetryableFailure] expects. */
        fun asDropFailure(): DropFailure? = when (this) {
            is Success -> DropFailure(kind = "ok")
            is Cancelled -> DropFailure(kind = "cancelled")
            is Error -> DropFailure(kind = "error", code = code.wire, subjectCode = subjectCode)
        }
    }

    /**
     * Catalogue snapshot cache keyed by `runId`. The first attempt seeds it;
     * every retry reuses that exact list rather than re-reading fresh params.
     */
    class SnapshotCache {
        private val snapshots = LinkedHashMap<String, List<ParsedCourse>>()

        /** Returns the already-cached list for [runId], seeding it on first use. */
        fun masterCoursesFor(runId: String, masterCourses: List<ParsedCourse>): List<ParsedCourse> =
            snapshots.getOrPut(runId) { masterCourses.toList() }

        /** The catalogue the worker actually walks for a given run + drop set. */
        fun effectiveCourses(runId: String, droppedCodes: Collection<String>): List<ParsedCourse>? {
            val snapshot = snapshots[runId] ?: return null
            val dropped = droppedCodes.toSet()
            return snapshot.filterNot { it.code in dropped }
        }

        val size: Int get() = snapshots.size
        fun contains(runId: String): Boolean = snapshots.containsKey(runId)
    }

    /**
     * Drives one ladder. The caller feeds each attempt's outcome into
     * [nextAttemptAfter]; it returns the next attempt's request, or `null` when
     * the ladder is finished (success, no retry allowed, or budget exhausted).
     */
    class Driver(
        val policy: DropPolicy,
        initialParams: GenerationParams,
        val runId: String,
        private val snapshots: SnapshotCache = SnapshotCache()
    ) {
        private val tail = droppableTail(policy)
        private val droppedCodes = ArrayList<String>()
        private var current = initialParams
        private var batchesSeen = false

        init {
            // Seed once. The same runId always resolves to this same snapshot.
            snapshots.masterCoursesFor(runId, initialParams.masterCourses)
        }

        /** Cumulative codes left out so far, lowest-priority-first. */
        val dropped: List<String> get() = droppedCodes.toList()
        val hasStreamedBatches: Boolean get() = batchesSeen
        val snapshotCache: SnapshotCache get() = snapshots

        /** The first attempt: full catalogue, no drops. */
        fun firstAttempt(): Attempt {
            current = current.copy(masterCourses = snapshots.masterCoursesFor(runId, current.masterCourses))
            return Attempt(params = current, runId = runId)
        }

        /** Called when a streaming batch has been handed to the caller. */
        fun recordBatch() {
            batchesSeen = true
        }

        /** The retry wire message for the current drop set. */
        fun retryWire(): RetryRequest = RetryRequest(runId, dropped)

        /**
         * Decide whether a failed attempt earns another run.
         *
         * No retry when batches were already streamed, when the budget is
         * spent, on `cancelled`/`ok`, on a crash (`code == "error"`) or
         * `no_courses_selected` (handled by [isRetryableFailure]), or when no
         * droppable course remains.
         */
        fun nextAttemptAfter(result: Outcome): Attempt? {
            if (batchesSeen) return null
            if (droppedCodes.size >= policy.maxDrops) return null
            val failure = result.asDropFailure() ?: return null
            if (!isRetryableFailure(failure)) return null
            val next = nextDrop(tail, droppedCodes, failure) ?: return null

            droppedCodes.add(next)
            // Remove from both catalogue and locks, but keep the same runId —
            // the retry itself posts only the cumulative droppedCodes.
            current = current.withCoursesDropped(droppedCodes)
            return Attempt(params = current, runId = runId, droppedCodes = dropped)
        }
    }
}
