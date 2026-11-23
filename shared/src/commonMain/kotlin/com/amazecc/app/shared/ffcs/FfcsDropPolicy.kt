package com.amazecc.app.shared.ffcs

import androidx.compose.runtime.Immutable

/**
 * Budget for one drop ladder: three, mirroring the web app's `DEFAULT_MAX_DROPS`.
 * Enough to rescue a five- or six-course week that is one unlucky slot apart,
 * small enough that a "result" without four courses would be a different plan
 * rather than a fix.
 */
const val DEFAULT_MAX_DROPS = 3

/**
 * Pure course/drop-order policy, ported from the FFCS web app's
 * `logic/dropPolicy.ts` ("All Subjects Mode: OFF" — the drop ladder).
 *
 * Priority order is the **selected-course order**: courses enter the plan in
 * the order the student added them, and the tail of that order is dropped first
 * ("added last, needed least"). This object never runs the generator; it only
 * decides *which* courses to leave out. The driver that feeds attempts to the
 * generator lives in [FfcsGenerationRun].
 */
@Immutable
data class DropPolicy(
    /** Selected course codes, highest priority first. */
    val courseOrder: List<String>,
    /**
     * Codes that must never be dropped.
     *
     * A course the student explicitly targeted — restricted slots, faculty, or
     * exact offerings — means "I thought about this one"; silently dropping it
     * would break a stated intent. Unconstrained selections are droppable.
     */
    val protectedCodes: List<String> = emptyList(),
    /** How many courses may be left out in one retry ladder. */
    val maxDrops: Int = DEFAULT_MAX_DROPS
)

/**
 * Minimal structural view of a generation failure, matching the web app's
 * `{ kind, code?, subjectCode? }` wire shape. `kind` is the result discriminant
 * (`"ok"` / `"cancelled"` / `"error"`), `code` is the typed failure code.
 */
@Immutable
data class DropFailure(
    val kind: String,
    val code: String? = null,
    val subjectCode: String? = null
)

/**
 * Retry only for "nothing matched" outcomes — never for a solver crash.
 *
 * Only `kind == "error"` is eligible, and even then the generic `error`
 * (crash) code and `no_courses_selected` (nothing to do; retrying cannot help)
 * are excluded.
 */
fun isRetryableFailure(result: DropFailure?): Boolean {
    if (result == null || result.kind != "error") return false
    return result.code != "error" && result.code != "no_courses_selected"
}

/**
 * The droppable codes, **lowest priority first**.
 *
 * Protected codes and duplicates are skipped (first occurrence wins), then the
 * surviving selection-order list is reversed once so every consumer can speak
 * in "next to drop" order.
 */
fun droppableTail(policy: DropPolicy): List<String> {
    val protectedSet = policy.protectedCodes.toSet()
    val seen = HashSet<String>()
    val droppable = ArrayList<String>()
    for (code in policy.courseOrder) {
        if (code in protectedSet || !seen.add(code)) continue
        droppable.add(code)
    }
    droppable.reverse()
    return droppable
}

/**
 * The next course to drop after an attempt failed.
 *
 * Normally the lowest-priority course not yet dropped — the ladder drops the
 * tail cumulatively: `{last}`, `{last-1, last}`, `{last-2, last-1, last}`…
 * One exception: `no_valid_slots` names a course that has *no* valid offering
 * under the current filters, and shuffling other courses will not fix that. It
 * jumps the queue (if the named course is still droppable), because it is the
 * only failure that already told us which course is the problem.
 */
fun nextDrop(
    droppableTailOrder: List<String>,
    dropped: List<String>,
    failure: DropFailure? = null
): String? {
    val droppedSet = dropped.toSet()
    val remaining = droppableTailOrder.filterNot { it in droppedSet }
    if (remaining.isEmpty()) return null
    if (failure != null &&
        failure.kind == "error" &&
        failure.code == "no_valid_slots" &&
        !failure.subjectCode.isNullOrBlank()
    ) {
        val named = remaining.firstOrNull { it == failure.subjectCode }
        if (named != null) return named
    }
    // `droppableTailOrder` is lowest-priority-first; `remaining` preserves that.
    return remaining.first()
}

/** One rung of the ladder: the codes kept and the codes left out for this attempt. */
@Immutable
data class DropAttempt(
    val kept: List<String>,
    val dropped: List<String>
)

/**
 * The whole ladder as a sequence — one yielded attempt per retry.
 *
 * The first attempt drops nothing; each subsequent attempt adds exactly one
 * course to the cumulative drop set, capped at [DropPolicy.maxDrops]. A policy
 * with no droppable courses still yields the initial (full-course) attempt.
 */
fun attemptLadder(policy: DropPolicy): Sequence<DropAttempt> = sequence {
    val tail = droppableTail(policy)
    val dropped = ArrayList<String>()
    val max = policy.maxDrops
    while (dropped.size <= max) {
        val droppedSet = dropped.toSet()
        yield(
            DropAttempt(
                kept = policy.courseOrder.filterNot { it in droppedSet },
                dropped = dropped.toList()
            )
        )
        if (dropped.size == max) break
        val next = nextDrop(tail, dropped) ?: break
        dropped.add(next)
    }
}
