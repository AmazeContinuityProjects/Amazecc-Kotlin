package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.CourseLock
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-178 — storage/settings contract, ported from ffcs-planner reference/07.
 *
 * Every test uses two fresh [InMemoryFfcsKeyValueStore] backends, so no test
 * touches the shared SettingsManager singleton and the JVM run is deterministic.
 */
class FfcsStorageTest {

    private class Fixture {
        val persistent = InMemoryFfcsKeyValueStore()
        val session = InMemoryFfcsKeyValueStore()
        val storage = FfcsStorage(persistent, session)

        init {
            // Session state is process-scoped by contract — start every test clean.
            storage.clearSession()
        }
    }

    /** A backend that always fails, modelling a blocked/quota-exceeded store. */
    private class ThrowingStore : FfcsKeyValueStore {
        override fun get(key: String): String? = throw IllegalStateException("blocked")
        override fun set(key: String, value: String) = throw IllegalStateException("quota")
        override fun remove(key: String) = throw IllegalStateException("blocked")
        override fun clear() = throw IllegalStateException("blocked")
        override fun keys(): Set<String> = throw IllegalStateException("blocked")
    }

    private fun lock(code: String) = CourseLock(
        code = code,
        title = code,
        allowedSlots = emptyList(),
        allowedFaculty = emptyList(),
    )

    // ── settings defaults & per-field fallback ───────────────────────────────

    @Test
    fun settings_missingAndJunk_fallBackPerFieldNotPerRecord() {
        val f = Fixture()
        assertEquals(FFCSSettingsDefaults.snapshot(), f.storage.loadSettings())

        f.persistent.seed(FfcsStorageKeys.SETTINGS, "{not json")
        assertEquals(FFCSSettingsDefaults.snapshot(), f.storage.loadSettings())

        f.persistent.seed(
            FfcsStorageKeys.SETTINGS,
            """{"revision":2,"resultCap":"oops","defaultSortBy":"bogus","allSubjectsMode":false,"minHalfDays":99}""",
        )
        val s = f.storage.loadSettings()
        assertEquals(FFCSSettingsDefaults.resultCap, s.resultCap)
        assertEquals(FFCSSettingsDefaults.defaultSortBy, s.defaultSortBy)
        assertFalse(s.allSubjectsMode) // a good field survives its neighbours' junk
        assertEquals(12, s.minHalfDays)
        assertEquals(FFCSS_SETTINGS_REVISION, s.revision)
    }

    @Test
    fun settings_sanitizerDedupesPriorityOrderAndCompletesCanonicalTail() {
        val f = Fixture()
        f.persistent.seed(
            FfcsStorageKeys.SETTINGS,
            """{"revision":2,"rankingPriorityOrder":["sort","sort","bogus","time"]}""",
        )
        assertEquals(
            listOf(
                RankingPriority.SORT,
                RankingPriority.TIME,
                RankingPriority.PROFILE,
                RankingPriority.FACULTY,
            ),
            f.storage.loadSettings().rankingPriorityOrder,
        )
    }

    @Test
    fun saveSettings_stampsRevisionAndSanitizes() {
        val f = Fixture()
        f.storage.saveSettings(FFCSSettings(resultCap = 5, defaultSortBy = SortBy.SOCIAL))
        val stored = f.persistent.get(FfcsStorageKeys.SETTINGS)!!
        assertTrue(stored.contains("\"revision\":2"), stored)
        val loaded = f.storage.loadSettings()
        assertEquals(FFCSS_MIN_RESULT_CAP, loaded.resultCap)
        assertEquals(SortBy.SOCIAL, loaded.defaultSortBy)
    }

    // ── the one-shot 50 -> 30,000 migration ──────────────────────────────────

    @Test
    fun resultCap_oneShotMigrationOnlyForPreRevisionFifty() {
        val f = Fixture()
        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":1,"resultCap":50}""")
        assertEquals(FFCSS_HARD_RESULT_LIMIT, f.storage.loadSettings().resultCap)

        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":2,"resultCap":50}""")
        assertEquals(50, f.storage.loadSettings().resultCap) // deliberate pick, preserved

        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"resultCap":50}""") // missing revision -> 1
        assertEquals(FFCSS_HARD_RESULT_LIMIT, f.storage.loadSettings().resultCap)

        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":3,"resultCap":50}""") // clamped to 2
        assertEquals(50, f.storage.loadSettings().resultCap)
    }

    @Test
    fun resultCap_minAndMaxAreClamped() {
        val f = Fixture()
        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":2,"resultCap":5}""")
        assertEquals(FFCSS_MIN_RESULT_CAP, f.storage.loadSettings().resultCap)

        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":2,"resultCap":999999}""")
        assertEquals(FFCSS_MAX_RESULT_CAP, f.storage.loadSettings().resultCap)

        f.persistent.seed(FfcsStorageKeys.SETTINGS, """{"revision":2,"resultCap":55.6}""")
        assertEquals(56, f.storage.loadSettings().resultCap) // Math.round, half away from zero
    }

    // ── JSON vs raw key semantics ────────────────────────────────────────────

    @Test
    fun keyEncoding_threeRawKeysAreVerbatimAndPromptSeenIsJson() {
        val f = Fixture()
        f.storage.saveActiveTimetableId("week-7")
        assertEquals("week-7", f.persistent.get(FfcsStorageKeys.ACTIVE_TIMETABLE_ID))

        f.storage.saveLayoutMode("modern")
        assertEquals("modern", f.persistent.get(FfcsStorageKeys.LAYOUT_MODE))

        f.storage.saveRawString(FfcsStorageKeys.DISMISS_FACULTY_GUIDE, "true")
        assertEquals("true", f.persistent.get(FfcsStorageKeys.DISMISS_FACULTY_GUIDE))

        // Looks raw, is JSON.
        f.storage.saveLayoutModePromptSeen("true")
        assertEquals("\"true\"", f.persistent.get(FfcsStorageKeys.LAYOUT_MODE_PROMPT_SEEN))
        assertEquals("true", f.storage.loadLayoutModePromptSeen())

        // Normal keys are JSON-encoded.
        f.storage.saveTipsDismissed(listOf("tip-1"))
        assertEquals("""["tip-1"]""", f.persistent.get(FfcsStorageKeys.TIPS_DISMISSED))

        // Web raw-string keys go through the explicit raw accessor.
        f.storage.saveRawString(FfcsStorageKeys.TOUR_SEEN, "3")
        assertEquals("3", f.persistent.get(FfcsStorageKeys.TOUR_SEEN))
    }

    @Test
    fun keyInventory_isNamespacedAndUsesCamelCaseFacultyPriority() {
        assertTrue(FfcsStorageKeys.ALL.all { it.startsWith("ffcs_") })
        assertEquals("ffcs_facultyPriority", FfcsStorageKeys.FACULTY_PRIORITY)
        assertEquals("ffcs_raw_courses_CH20262705", FfcsStorageKeys.rawCoursesFor("CH20262705"))
        assertEquals(25, FfcsStorageKeys.ALL.size)
    }

    @Test
    fun facultyPriority_usesCamelCaseKeyAndSanitizesPerEntry() {
        val f = Fixture()
        f.persistent.seed(FfcsStorageKeys.FACULTY_PRIORITY, """{"CSE1":["Prof A","PROF A"]}""")
        assertEquals(mapOf("CSE1" to listOf("Prof A")), f.storage.loadFacultyPriority())
        f.persistent.seed(FfcsStorageKeys.FACULTY_PRIORITY, "{junk")
        assertEquals(emptyMap<String, List<String>>(), f.storage.loadFacultyPriority())
    }

    // ── session store separation ─────────────────────────────────────────────

    @Test
    fun sessionPrefs_liveOnlyInTheSessionStore() {
        val f = Fixture()
        val prefs = GeneratorSessionPreferences(
            resultCap = 500,
            sortBy = SortBy.HALFDAYS,
            targets = listOf(lock("A")),
        )
        FfcsGeneratorSessionPrefs.save(prefs, f.storage)
        assertNull(f.persistent.get(FfcsStorageKeys.GENERATOR_SESSION_PREFS))
        assertTrue(f.session.get(FfcsStorageKeys.GENERATOR_SESSION_PREFS)!!.contains("500"))
        assertEquals(prefs, FfcsGeneratorSessionPrefs.load(f.storage))

        // A corrupt DURABLE copy must be ignored: the session record is the only
        // source of truth, so the prefs saved above are still what loads back.
        f.persistent.seed(FfcsStorageKeys.GENERATOR_SESSION_PREFS, "{junk")
        assertEquals(prefs, FfcsGeneratorSessionPrefs.load(f.storage))

        // Corrupting the SESSION record is what actually degrades to a blank record.
        f.session.seed(FfcsStorageKeys.GENERATOR_SESSION_PREFS, "{junk")
        assertEquals(GeneratorSessionPreferences(), FfcsGeneratorSessionPrefs.load(f.storage))

        // ...and nothing was ever written back to durable storage by a load.
        assertEquals("{junk", f.persistent.get(FfcsStorageKeys.GENERATOR_SESSION_PREFS))
    }

    // ── stale-target pruning ─────────────────────────────────────────────────

    @Test
    fun pruneGeneratorTargets_removesStaleCodesFromBothStores() {
        val f = Fixture()
        val a = lock("A")
        val b = lock("B")
        val c = lock("C")
        FfcsGeneratorTargets.save(listOf(a, b), f.storage)
        FfcsGeneratorSessionPrefs.save(GeneratorSessionPreferences(targets = listOf(a, c)), f.storage)

        FfcsGeneratorTargets.prune(setOf("A"), f.storage)
        assertEquals(listOf(a), FfcsGeneratorTargets.load(f.storage))
        assertEquals(listOf(a), FfcsGeneratorSessionPrefs.load(f.storage).targets)

        // A no-op prune must not rewrite the stored bytes.
        val before = f.persistent.get(FfcsGeneratorTargets.KEY)
        FfcsGeneratorTargets.prune(setOf("A"), f.storage)
        assertEquals(before, f.persistent.get(FfcsGeneratorTargets.KEY))
    }

    @Test
    fun junkTargets_degradeToEmptyList() {
        val f = Fixture()
        f.persistent.seed(FfcsStorageKeys.GENERATOR_TARGETS, "{not json")
        assertEquals(emptyList<CourseLock>(), FfcsGeneratorTargets.load(f.storage))
    }

    // ── blocked/quota storage never crashes the caller ───────────────────────

    @Test
    fun blockedStorage_degradesToDefaultsWithoutThrowing() {
        val s = FfcsStorage(ThrowingStore(), ThrowingStore())
        s.store(FfcsStorageKeys.TIMETABLES, emptyList<TimetableState>())
        s.rawSet(FfcsStorageKeys.SETTINGS, "x")
        s.remove(FfcsStorageKeys.SETTINGS)
        assertEquals(FFCSSettingsDefaults.snapshot(), s.loadSettings())
        assertEquals(emptyList<TimetableState>(), s.loadTimetables())
        assertEquals(emptyList<CourseLock>(), FfcsGeneratorTargets.load(s))
        assertNull(FfcsGeneratorSessionPrefs.load(s).targets)
    }
}
