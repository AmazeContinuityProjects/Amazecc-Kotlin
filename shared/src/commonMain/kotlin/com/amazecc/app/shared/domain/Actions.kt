package com.amazecc.app.shared.domain

import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.vtop.CaptchaChallenge
import kotlin.coroutines.cancellation.CancellationException

/**
 * Layer 3: functional. User intent in, an explicit result out.
 *
 * The other two layers are passive - [DomainSnapshot] is what the app *has*, [Projections] is how
 * it is *read*. This one is the only place a command is run. Screens call [Actions] and react to
 * the returned [SyncOutcome]; they do not call `AmazeClient`, do not call [AppDataStore] setters,
 * and do not call `SyncEngine` directly.
 *
 * Nothing here throws across the boundary and nothing here writes to a screen field.
 */
sealed interface SyncOutcome {

    /** The command ran and changed something. [modules] names what was touched. */
    data class Synced(val modules: List<String>) : SyncOutcome

    /** The command ran and did not. [module] identifies the target, [reason] says why. */
    data class Failed(val module: String, val reason: String) : SyncOutcome

    /** The command needs the user before it can proceed. */
    data class NeedsCaptcha(val challenge: CaptchaChallenge) : SyncOutcome

    /**
     * Deliberately did nothing: not applicable, already in the requested state, or nothing is
     * registered to perform it. Distinct from [Failed] because it is not an error.
     */
    data object Skipped : SyncOutcome
}

/**
 * What a command was asked to do, carried as a value.
 *
 * Explicit rather than read from global state, which is what makes [Actions.sync] callable from a
 * widget, a notification action, or a screen without any of them reaching into `AppState`.
 */
data class SyncScope(
    /** Which semester's data to refresh; `null` means "whatever is selected". */
    val semesterId: String? = null,
    /** Module names to refresh. Empty means "the default sweep". */
    val modules: Set<String> = emptySet(),
    /** Refresh even when the module reports it is already current. */
    val force: Boolean = false,
) {
    /** Stable label for logs and for [SyncOutcome.Failed.module]. */
    fun label(): String = when {
        modules.isNotEmpty() -> modules.sorted().joinToString("+")
        semesterId != null -> "semester:$semesterId"
        else -> "all"
    }
}

/**
 * The one place a command is issued.
 *
 * Two kinds of member live here. The [completeTask]/[removeTask]/[clearCache] family is fully
 * implemented: they are cheap, deterministic, and every one of them is covered by
 * `commonTest/.../domain/ActionsTest.kt`.
 *
 * [sync] and [refreshCurriculum] are the *seam*. `AppState` owns the ~65 module fetches and is
 * still fire-and-forget - `launchSweep` launches a job and returns immediately - so until that
 * sweep can report back, no runner is registered and [sync] honestly answers
 * [SyncOutcome.Skipped] rather than inventing an outcome. The day the sweep is joinable
 * (`sweepJob` already exists and is awaitable), `AppState` calls [registerRunner] once and every
 * caller - screen, widget, notification - gets the same real result.
 */
object Actions {

    private var runner: (suspend (SyncScope) -> SyncOutcome)? = null

    /**
     * Installs (or, with `null`, uninstalls) the engine behind [sync].
     *
     * Lives on [Actions] rather than being a constructor argument so that a test can swap in a
     * fake for the duration of one method.
     */
    fun registerRunner(block: (suspend (SyncScope) -> SyncOutcome)?) {
        runner = block
    }

    /** Runs the registered sweep for [scope]. No runner installed → [SyncOutcome.Skipped]. */
    suspend fun sync(scope: SyncScope): SyncOutcome {
        val block = runner ?: return SyncOutcome.Skipped
        return try {
            block(scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncOutcome.Failed(scope.label(), e.message ?: e.toString())
        }
    }

    /** Refreshes curriculum for [semesterId]. See [sync] for why this is a seam today. */
    suspend fun refreshCurriculum(semesterId: String): SyncOutcome =
        sync(SyncScope(semesterId = semesterId, modules = setOf(MODULE_CURRICULUM), force = true))

    /**
     * Marks a task done or not done.
     *
     * Answers [SyncOutcome.Skipped] when the task already has the requested value, so that a
     * double-tap is idempotent rather than a second write.
     *
     * Reads [AppDataStore.data] rather than the derived `AppDataStore.tasks`: that one is a
     * `stateIn` flow, so it can still be showing the previous snapshot one dispatcher hop after a
     * write, and a command that checks before it writes must not act on a stale list.
     */
    fun completeTask(id: String, done: Boolean): SyncOutcome {
        val task = AppDataStore.data.value.tasks.firstOrNull { it.id == id }
            ?: return SyncOutcome.Failed(id, "no such task")
        if (task.completed == done) return SyncOutcome.Skipped
        AppDataStore.updateTask(id) { it.copy(completed = done) }
        return SyncOutcome.Synced(listOf(MODULE_TASKS))
    }

    /** Deletes a task. Missing ids are a [SyncOutcome.Failed], not a silent success. */
    fun removeTask(id: String): SyncOutcome {
        if (AppDataStore.data.value.tasks.none { it.id == id }) {
            return SyncOutcome.Failed(id, "no such task")
        }
        AppDataStore.removeTask(id)
        return SyncOutcome.Synced(listOf(MODULE_TASKS))
    }

    /**
     * Drops the cached snapshot: the in-memory copy plus the persisted key it is restored from.
     *
     * Deliberately narrow - it clears the app *data* cache only. Credentials, settings, and
     * anything under `SettingsManager.clearAll()` are not touched, because forgetting everything
     * and forgetting what you fetched are different actions.
     */
    fun clearCache(): Result<Unit> = runCatching {
        AppDataStore.clear()
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
    }

    private const val MODULE_TASKS = "Tasks"
    private const val MODULE_CURRICULUM = "Curriculum"
}
