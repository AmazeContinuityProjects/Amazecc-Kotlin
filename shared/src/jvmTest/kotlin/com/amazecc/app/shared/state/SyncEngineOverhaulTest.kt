package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.datetime.Instant
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
