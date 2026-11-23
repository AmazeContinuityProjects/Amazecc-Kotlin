package com.amazecc.app.shared.ffcs

/**
 * D-180 — result limits, cooperative hooks and typed generation results.
 *
 * Pure data: nothing here reads the clock, random numbers, or anything outside
 * its arguments. The eight failure codes are the ones already ported in
 * [FfcsErrorCode] (D-175, `FfcsGenerationRun.kt`); the wire strings are the
 * web engine's `ffcsGenerate.ts:81-94`.
 *
 * `truncated` is reported honestly: `capped` describes the *search* stopping
 * at the limit, `truncated` describes the *returned* list being shorter than
 * the accepted set (after dedupe/grouping). Neither is inferred from the
 * other, so a caller can tell "we stopped at 50" from "we found 50 weeks but
 * only 12 distinct layouts survived".
 */

/** `ffcsGenerate.ts:409-426` — the hard ceiling is 30 000 weeks. */
const val HARD_RESULT_LIMIT: Int = 30_000

/** `ffcsGenerate.ts:410` — the default cap when the caller says nothing. */
const val DEFAULT_RESULT_LIMIT: Int = 50

/** `ffcsGenerate.ts:426` — progressive batches are flushed every 50 weeks. */
const val DEFAULT_BATCH_SIZE: Int = 50

/** `ffcsWalk.ts:328` — cancellation is checked every 4096 DFS nodes. */
const val TICK_INTERVAL: Int = 4096

/** `ffcsWalk.ts:88` — five days × morning/evening. */
const val HALF_DAY_CELLS: Int = 10

/** `timetableMetrics.ts:290` — the gap credit saturates at 20 hours. */
const val MAX_WEEKLY_GAP_MIN: Int = 1200

/**
 * Resolve the search limit exactly as `ffcsGenerate.ts:1246-1251`.
 *
 * `Long.MAX_VALUE` is the finite/infinite sentinel: `noLimit` + no hard-limit
 * removal means 30 000, `noLimit` + removal means "unbounded" (∞). Every other
 * path uses `resultCap` or the default of 50.
 */
fun resolveResultLimit(
    noLimit: Boolean,
    removeHardResultLimit: Boolean = false,
    resultCap: Int? = null,
): Long = when {
    noLimit && removeHardResultLimit -> Long.MAX_VALUE
    noLimit -> HARD_RESULT_LIMIT.toLong()
    else -> resultCap?.toLong() ?: DEFAULT_RESULT_LIMIT.toLong()
}

/** A settled, scored week plus the metadata the plain [TimetableState] cannot hold. */
data class FfcsOption(
    val timetable: TimetableState,
    val rankingScore: Double? = null,
    val rankingBreakdown: Map<String, Double> = emptyMap(),
    val facultyScore: Double? = null,
    /** Populated by `groupVariants`; the first child is "Variant 1". */
    val variants: List<FfcsOption> = emptyList(),
)

/**
 * Terminal shape of one generation run — the typed alternative to throwing.
 *
 * Only genuine solver crashes land in [Error] with [FfcsErrorCode.ERROR];
 * every "nothing matched" outcome carries one of the seven other codes, so
 * the D-175 drop ladder can decide from the code alone whether a retry helps.
 */
sealed class FfcsEngineResult {
    data class Ok(
        val timetables: List<FfcsOption>,
        /** True when the search stopped because it reached a finite limit. */
        val capped: Boolean = false,
        /** True when grouping/dedupe returned fewer options than were accepted. */
        val truncated: Boolean = false,
        /** Number of rule-legal weeks the walk visited, before grouping. */
        val found: Int = 0,
    ) : FfcsEngineResult()

    data class Cancelled(val timetables: List<FfcsOption> = emptyList()) : FfcsEngineResult()

    data class Error(
        val code: FfcsErrorCode,
        val subjectCode: String? = null,
    ) : FfcsEngineResult()
}

/** Observation seams for a progressive caller. All are optional; all stay pure. */
data class FfcsGenerationHooks(
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val onBatch: ((List<FfcsOption>) -> Unit)? = null,
    val onProgress: ((nodes: Int, found: Int, accepted: Int) -> Unit)? = null,
    /** Checked cooperatively by the walk; a `true` settles as [FfcsEngineResult.Cancelled]. */
    val shouldStop: () -> Boolean = { false },
)
