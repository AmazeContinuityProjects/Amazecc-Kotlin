package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class SyncEngineOverhaulTest {

    private var previousVtopSource: VtopSource = VtopSource.REMOTE

    @BeforeTest
    fun setup() {
        previousVtopSource = AmazeClient.vtopSource
    }

    @AfterTest
    fun tearDown() {
        AmazeClient.setVtopSource(previousVtopSource)
    }

    @Test
    fun testBuiltInSyncModesExistAndAreConfigured() {
        val modes = BUILT_IN_MODES.associateBy { it.id }
        assertTrue(modes.containsKey("quick"), "Should have quick sync mode")
        assertTrue(modes.containsKey("balanced"), "Should have balanced sync mode")
        assertTrue(modes.containsKey("full"), "Should have full sync mode")
        assertTrue(modes.containsKey("academics"), "Should have academics sync mode")
        assertTrue(modes.containsKey("campus"), "Should have campus sync mode")
        assertTrue(modes.containsKey("daily"), "Should have daily cadence mode")
        assertTrue(modes.containsKey("weekly"), "Should have weekly cadence mode")
        assertTrue(modes.containsKey("monthly"), "Should have monthly cadence mode")
        assertTrue(modes.containsKey("quarterly"), "Should have quarterly cadence mode")

        val quickMode = modes["quick"]!!
        assertEquals("quick", quickMode.id)
        assertEquals("Quick Sync", quickMode.label)
        assertFalse(quickMode.steps.isEmpty())
        assertTrue(quickMode.steps.any { it.module == SyncModule.ATTENDANCE })
        assertTrue(quickMode.steps.any { it.module == SyncModule.MARKS })

        val fullMode = modes["full"]!!
        assertEquals("full", fullMode.id)
        assertEquals("Full Sync", fullMode.label)
        assertTrue(fullMode.steps.size >= 10, "Full mode should have at least 10 steps")

        val academicsMode = modes["academics"]!!
        assertEquals("Academics Only", academicsMode.label)
        assertTrue(academicsMode.steps.any { it.module == SyncModule.ATTENDANCE })
        assertTrue(academicsMode.steps.any { it.module == SyncModule.MARKS })
        assertTrue(academicsMode.steps.any { it.module == SyncModule.TIMETABLE })

        val campusMode = modes["campus"]!!
        assertEquals("Campus Life", campusMode.label)
        assertTrue(campusMode.steps.any { it.module == SyncModule.HOSTEL_DETAILS })
    }

    @Test
    fun testCadenceTiers() {
        val tiers = BUILT_IN_TIERS
        assertEquals(4, tiers.size)
        val daily = tiers.find { it.id == "daily" }
        assertNotNull(daily)
        assertEquals(1, daily.defaultStalenessDays)

        val weekly = tiers.find { it.id == "weekly" }
        assertNotNull(weekly)
        assertEquals(7, weekly.defaultStalenessDays)

        val monthly = tiers.find { it.id == "monthly" }
        assertNotNull(monthly)
        assertEquals(30, monthly.defaultStalenessDays)

        val quarterly = tiers.find { it.id == "quarterly" }
        assertNotNull(quarterly)
        assertEquals(90, quarterly.defaultStalenessDays)
    }

    @Test
    fun testStalenessTrackingAndRecording() {
        val module = SyncModule.ATTENDANCE
        val now = 10_000_000L
        
        // Record sync time
        SyncEngine.recordSyncTime(module, Instant.fromEpochMilliseconds(now))
        assertEquals(now, SyncEngine.getLastSyncedAt(module)?.toEpochMilliseconds())

        // Freshness test
        assertTrue(SyncEngine.isFresh(module, maxAgeMs = 5_000L, nowMs = now + 2_000L))
        assertFalse(SyncEngine.isFresh(module, maxAgeMs = 5_000L, nowMs = now + 10_000L))

        // Age formatting
        val formattedFresh = SyncEngine.formatSyncAge(module, nowMs = now + 30_000L)
        assertEquals("Just now", formattedFresh)

        val formattedMinutes = SyncEngine.formatSyncAge(module, nowMs = now + 5 * 60 * 1000L)
        assertEquals("5m ago", formattedMinutes)

        val formattedHours = SyncEngine.formatSyncAge(module, nowMs = now + 3 * 3600 * 1000L)
        assertEquals("3h ago", formattedHours)

        val formattedDays = SyncEngine.formatSyncAge(module, nowMs = now + 2L * 24 * 3600 * 1000L)
        assertEquals("2d ago", formattedDays)
    }

    @Test
    fun testEstimateModeCost() {
        val modes = BUILT_IN_MODES.associateBy { it.id }
        val quickMode = modes["quick"]!!
        val cost = SyncEngine.estimateModeCost(quickMode)
        assertTrue(cost > 0)
        
        val fullMode = modes["full"]!!
        val fullCost = SyncEngine.estimateModeCost(fullMode)
        assertTrue(fullCost > cost, "Full mode cost should be greater than quick mode cost")
    }

    @Test
    fun testResolveMode() {
        assertEquals("quick", SyncEngine.resolveMode("quick").id)
        assertEquals("full", SyncEngine.resolveMode("full").id)
        // Fallback to balanced if unknown
        assertEquals("balanced", SyncEngine.resolveMode("unknown_mode_123").id)
    }

    @Test
    fun testFeedbackStatusParticipatesInEveryBuiltInModeAndProfile() {
        // D-155: feedback status is fetched through POST /api/ffcs (section=feedback) as a sync-engine
        // step on every manual-sync level, gated by the web's `ffcs.every = 7` freshness cadence.
        val canonicalFeedbackSteps = DAILY_STEPS.filter { it.module == SyncModule.FEEDBACK_STATUS }
        assertEquals(1, canonicalFeedbackSteps.size, "DAILY_STEPS should define exactly one canonical feedback step")
        val canonicalFeedback = canonicalFeedbackSteps.single()
        assertEquals(7, canonicalFeedback.everyDays, "Feedback step cadence must match web ffcs.every = 7")
        assertTrue(canonicalFeedback.background, "Feedback step should be a background step")

        // Daily -> Weekly inherit the canonical DAILY_STEPS step. MONTHLY_STEPS is a standalone
        // list (not derived from DAILY_STEPS), so monthly/quarterly keep their own copy — assert
        // it carries the same 7-day cadence rather than assuming inheritance.
        val cadenceModes = SyncEngine.listModes().filter { it.id in setOf("daily", "weekly", "monthly", "quarterly") }
        assertEquals(4, cadenceModes.size, "All four cadence tiers must be present")
        cadenceModes.forEach { mode ->
            val feedbackSteps = mode.steps.filter { it.module == SyncModule.FEEDBACK_STATUS }
            assertEquals(1, feedbackSteps.size, "Cadence mode '${mode.id}' should have exactly one feedback step")
            assertEquals(7, feedbackSteps.single().everyDays, "Cadence mode '${mode.id}' feedback cadence must be 7 days")
        }

        // Every built-in mode (quick, balanced, full, academics, campus + cadence tiers) participates.
        SyncEngine.listModes().forEach { mode ->
            assertTrue(
                mode.steps.any { it.module == SyncModule.FEEDBACK_STATUS },
                "Built-in mode '${mode.id}' must include FEEDBACK_STATUS"
            )
        }

        // Every built-in profile participates too: quick_sync, campus_life and daily_reload were
        // the ones that previously omitted it.
        DEFAULT_SYNC_PROFILES.filter { it.isBuiltIn }.forEach { profile ->
            assertTrue(
                SyncModule.FEEDBACK_STATUS in profile.enabledModules,
                "Built-in profile '${profile.id}' must include FEEDBACK_STATUS"
            )
        }
    }

    @Test
    fun testV1ProfileMigrationBackfillsFeedbackAndPreservesUserCustomisations() {
        // D-155 follow-up: v1 devices stored built-in profiles without FEEDBACK_STATUS, and the old
        // loader only re-unioned defaults when the version key was empty. Seed that exact state and
        // run the real load/migration path; both settings keys are restored afterwards.
        val keys = listOf(SettingsManager.KEY_SYNC_PROFILES, SettingsManager.KEY_SYNC_PROFILES_VERSION)
        val saved = keys.associateWith { SettingsManager.getString(it) }
        try {
            fun builtIn(id: String) = DEFAULT_SYNC_PROFILES.first { it.id == id }
            val seededQuick = builtIn("quick_sync").copy(
                enabledModules = setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE, SyncModule.MARKS)
            )
            val seededCampus = builtIn("campus_life").copy(
                enabledModules = setOf(SyncModule.EVENTS, SyncModule.CLUBS)
            )
            val seededDaily = builtIn("daily_reload").copy(
                enabledModules = setOf(SyncModule.ATTENDANCE, SyncModule.MOODLE)
            )
            val balanced = builtIn("balanced_sync")
            // Unrelated, non-conflicting user choice: PAYMENTS deliberately disabled in a built-in.
            val seededBalanced = balanced.copy(enabledModules = balanced.enabledModules - SyncModule.PAYMENTS)
            val custom = SyncConfigProfile(
                id = "custom_my_plug",
                name = "My Plug",
                enabledModules = setOf(SyncModule.LIBRARY),
                isBuiltIn = false
            )
            SettingsManager.setString(
                SettingsManager.KEY_SYNC_PROFILES,
                Json.encodeToString(listOf(seededQuick, seededCampus, seededDaily, seededBalanced, custom))
            )
            SettingsManager.setString(SettingsManager.KEY_SYNC_PROFILES_VERSION, "1") // stored v1

            val migrated = SyncEngine.loadProfiles()

            listOf("quick_sync", "campus_life", "daily_reload").forEach { id ->
                val profile = migrated.first { it.id == id }
                assertTrue(
                    SyncModule.FEEDBACK_STATUS in profile.enabledModules,
                    "v1->v2 migration must backfill FEEDBACK_STATUS into stored '$id'"
                )
            }
            // Union semantics: modules the user already had in those profiles survive too.
            assertTrue(
                setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE, SyncModule.MARKS)
                    .all { it in migrated.first { it.id == "quick_sync" }.enabledModules }
            )
            assertTrue(SyncModule.CLUBS in migrated.first { it.id == "campus_life" }.enabledModules)
            assertTrue(SyncModule.MOODLE in migrated.first { it.id == "daily_reload" }.enabledModules)

            // Non-conflicting customisations survive: an unrelated disabled module stays disabled
            // and the user-created profile is carried through untouched (only built-ins are backfilled).
            val migratedBalanced = migrated.first { it.id == "balanced_sync" }
            assertFalse(SyncModule.PAYMENTS in migratedBalanced.enabledModules)
            assertTrue(SyncModule.FEEDBACK_STATUS in migratedBalanced.enabledModules)
            val survived = migrated.firstOrNull { it.id == custom.id }
            assertNotNull(survived)
            assertEquals(custom.name, survived?.name)
            assertEquals(custom.enabledModules, survived?.enabledModules)

            // One-shot: the version is stamped to the new value and the migrated set is persisted.
            assertEquals("2", SYNC_PROFILES_SCHEMA_VERSION)
            assertEquals(
                SYNC_PROFILES_SCHEMA_VERSION,
                SettingsManager.getString(SettingsManager.KEY_SYNC_PROFILES_VERSION)
            )
            val persisted = Json.decodeFromString<List<SyncConfigProfile>>(
                SettingsManager.getString(SettingsManager.KEY_SYNC_PROFILES)
            )
            assertTrue(SyncModule.FEEDBACK_STATUS in persisted.first { it.id == "quick_sync" }.enabledModules)

            // Re-loading at v2 is stable: no re-backfill, no resurrected user choices.
            val reloaded = SyncEngine.loadProfiles()
            assertTrue(SyncModule.FEEDBACK_STATUS in reloaded.first { it.id == "quick_sync" }.enabledModules)
            assertFalse(SyncModule.PAYMENTS in reloaded.first { it.id == "balanced_sync" }.enabledModules)
        } finally {
            saved.forEach { (key, value) ->
                if (value.isEmpty()) SettingsManager.remove(key) else SettingsManager.setString(key, value)
            }
        }
    }

    @Test
    fun testVtopSourceOfflineCacheEnum() {
        val offlineCache = VtopSource.OFFLINE_CACHE
        assertEquals("OFFLINE_CACHE", offlineCache.name)
        assertEquals("Pure Offline Cache", offlineCache.displayName)
        assertEquals(3, offlineCache.rank)
        assertTrue(offlineCache.description.contains("offline", ignoreCase = true))

        AmazeClient.setVtopSource(VtopSource.OFFLINE_CACHE)
        assertEquals(VtopSource.OFFLINE_CACHE, AmazeClient.vtopSource)

        AmazeClient.setVtopSource(VtopSource.REMOTE)
        assertEquals(VtopSource.REMOTE, AmazeClient.vtopSource)

        AmazeClient.setVtopSource(VtopSource.LOCAL)
        assertEquals(VtopSource.LOCAL, AmazeClient.vtopSource)
    }
}
