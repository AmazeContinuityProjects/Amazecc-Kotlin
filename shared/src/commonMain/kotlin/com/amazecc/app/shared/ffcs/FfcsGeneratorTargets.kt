package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.CourseLock

/**
 * D-178 — generator target picks (reference/07 §4).
 *
 * The durable `ffcs_generator_targets` list and the `targets` field of the
 * session preferences are two copies of the same user choice. A stale code is
 * not harmless: the generator fails the whole run with `no_valid_slots` when a
 * remembered target is no longer offered, so both stores must be pruned when
 * the course catalogue changes.
 */
object FfcsGeneratorTargets {
    const val KEY: String = FfcsStorageKeys.GENERATOR_TARGETS

    fun load(storage: FfcsStorage = FfcsStorage.Default): List<CourseLock> =
        storage.load<List<CourseLock>>(KEY, emptyList()) ?: emptyList()

    fun save(targets: List<CourseLock>, storage: FfcsStorage = FfcsStorage.Default) =
        storage.store(KEY, targets)

    /**
     * Drop every target whose code is absent from [offeredCodes]. Each store
     * is rewritten only when it actually changed, so a no-op prune leaves the
     * persisted bytes untouched.
     */
    fun prune(offeredCodes: Set<String>, storage: FfcsStorage = FfcsStorage.Default) {
        val stored = load(storage)
        val kept = stored.filter { target -> target.code in offeredCodes }
        if (kept.size != stored.size) save(kept, storage)

        val prefs = FfcsGeneratorSessionPrefs.load(storage)
        val prefTargets = prefs.targets ?: emptyList()
        val keptPrefs = prefTargets.filter { target -> target.code in offeredCodes }
        if (keptPrefs.size != prefTargets.size) {
            FfcsGeneratorSessionPrefs.save(prefs.copy(targets = keptPrefs), storage)
        }
    }
}

/** Web-compatible entry point (`pruneGeneratorTargets(offeredCodes)`). */
fun pruneGeneratorTargets(offeredCodes: Set<String>, storage: FfcsStorage = FfcsStorage.Default) =
    FfcsGeneratorTargets.prune(offeredCodes, storage)
