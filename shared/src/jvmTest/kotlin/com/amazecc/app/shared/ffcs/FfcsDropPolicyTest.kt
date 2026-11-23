package com.amazecc.app.shared.ffcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-175: golden vectors lifted from the real AmazeCC vitest suite
 * (`src/__tests__/ffcsDropPolicy.test.ts:22-90`) plus the generation-driver
 * no-retry contract from `useGenerationRun.ts` / `logic/generator.ts`.
 */
class FfcsDropPolicyTest {

    /** Golden `courseOrder` — highest priority first. */
    private val goldenOrder = listOf("MAT2005", "PHY1020", "CSE2001", "ENG1010")

    private fun offering(code: String) = ParsedCourse(
        code = code,
        title = "Title $code",
        type = "Theory",
        credits = "3",
        room = "SJT-101",
        slot = "A1",
        faculty = "Dr. A"
    )

    // --- droppableTail -----------------------------------------------------

    @Test
    fun droppableTail_matchesGoldenVector() {
        val policy = DropPolicy(courseOrder = goldenOrder)
        assertEquals(listOf("ENG1010", "CSE2001", "PHY1020", "MAT2005"), droppableTail(policy))
    }

    @Test
    fun droppableTail_skipsProtectedCodes() {
        val policy = DropPolicy(
            courseOrder = goldenOrder,
            protectedCodes = listOf("ENG1010", "PHY1020")
        )
        assertEquals(listOf("CSE2001", "MAT2005"), droppableTail(policy))
    }

    @Test
    fun droppableTail_dedupesKeepingFirstOccurrence() {
        assertEquals(listOf("B", "A"), droppableTail(DropPolicy(courseOrder = listOf("A", "B", "A"))))
    }

    @Test
    fun droppableTail_emptyWhenEverythingIsProtected() {
        assertTrue(droppableTail(DropPolicy(goldenOrder, protectedCodes = goldenOrder)).isEmpty())
    }

    // --- nextDrop ----------------------------------------------------------

    @Test
    fun nextDrop_noValidSlotsJumpsToNamedCourse() {
        val tail = droppableTail(DropPolicy(goldenOrder))
        val failure = DropFailure(kind = "error", code = "no_valid_slots", subjectCode = "CSE2001")
        assertEquals("CSE2001", nextDrop(tail, emptyList(), failure))
    }

    @Test
    fun nextDrop_jumpTargetAlreadyDroppedFallsBackToTail() {
        val tail = droppableTail(DropPolicy(goldenOrder))
        val failure = DropFailure(kind = "error", code = "no_valid_slots", subjectCode = "CSE2001")
        assertEquals("ENG1010", nextDrop(tail, listOf("CSE2001"), failure))
    }

    @Test
    fun nextDrop_protectedJumpTargetIsIgnored() {
        val tail = droppableTail(DropPolicy(goldenOrder, protectedCodes = listOf("CSE2001")))
        val failure = DropFailure(kind = "error", code = "no_valid_slots", subjectCode = "CSE2001")
        assertEquals("ENG1010", nextDrop(tail, emptyList(), failure))
    }

    @Test
    fun nextDrop_returnsNullWhenNothingRemains() {
        assertNull(nextDrop(listOf("A", "B"), listOf("A", "B")))
        assertNull(nextDrop(emptyList(), emptyList()))
    }

    // --- retryable table ---------------------------------------------------

    @Test
    fun isRetryableFailure_matchesGoldenTable() {
        val cases = listOf(
            "no_conflict_free" to true,
            "no_valid_slots" to true,
            "below_min_half_days" to true,
            "below_min_faculty_scores" to true,
            "no_separate_labs" to true,
            "no_consecutive_labs" to true,
            "error" to false,
            "no_courses_selected" to false,
            "ok" to false,
            "cancelled" to false
        )
        for ((code, expected) in cases) {
            val nonErrorKind = code == "ok" || code == "cancelled"
            val result = DropFailure(kind = if (nonErrorKind) code else "error", code = code)
            assertEquals(expected, isRetryableFailure(result), "code=$code")
        }
        assertFalse(isRetryableFailure(null))
    }

    @Test
    fun isRetryableFailure_requiresErrorKind() {
        assertFalse(isRetryableFailure(DropFailure(kind = "cancelled", code = "no_conflict_free")))
        assertFalse(isRetryableFailure(DropFailure(kind = "ok", code = "no_valid_slots")))
    }

    // --- ladder ------------------------------------------------------------

    @Test
    fun dropPolicy_maxDropsDefaultsToThree() {
        assertEquals(3, DEFAULT_MAX_DROPS)
        assertEquals(3, DropPolicy(courseOrder = goldenOrder).maxDrops)
    }

    @Test
    fun attemptLadder_dropsCumulativelyLowestPriorityFirst() {
        val ladder = attemptLadder(DropPolicy(courseOrder = listOf("A", "B", "C", "D", "E"))).toList()
        assertEquals(4, ladder.size, "Initial attempt plus three single-course drops.")
        assertEquals(
            listOf(emptyList<String>(), listOf("E"), listOf("E", "D"), listOf("E", "D", "C")),
            ladder.map { it.dropped }
        )
        assertEquals(listOf("A", "B", "C", "D", "E"), ladder.first().kept)
        assertEquals(listOf("A", "B"), ladder.last().kept)
    }

    @Test
    fun attemptLadder_neverDropsProtectedCodes() {
        val ladder = attemptLadder(
            DropPolicy(courseOrder = listOf("A", "B", "C", "D", "E"), protectedCodes = listOf("C"))
        ).toList()
        assertTrue(ladder.all { "C" !in it.dropped })
        assertEquals(
            listOf(emptyList<String>(), listOf("E"), listOf("E", "D"), listOf("E", "D", "B")),
            ladder.map { it.dropped }
        )
    }

    @Test
    fun attemptLadder_respectsCustomMaxDrops() {
        // reference/02 §1050-1060: courseOrder is highest-priority-first, so the
        // tail is the LOWEST-priority courses. For [A,B,C,D] the ladder drops D
        // then C (cumulative {last}, {last-1,last}); A/B are kept. The old
        // expectation [B,A] would have sacrificed the top-priority courses.
        val ladder = attemptLadder(
            DropPolicy(courseOrder = listOf("A", "B", "C", "D"), maxDrops = 2)
        ).toList()
        assertEquals(3, ladder.size, "Initial attempt plus two single-course drops.")
        assertEquals(listOf("D", "C"), ladder.last().dropped)
        assertEquals(listOf("A", "B"), ladder.last().kept)
    }

    @Test
    fun attemptLadder_yieldsSingleAttemptWhenNothingIsDroppable() {
        val ladder = attemptLadder(
            DropPolicy(courseOrder = goldenOrder, protectedCodes = goldenOrder)
        ).toList()
        assertEquals(1, ladder.size)
        assertEquals(emptyList<String>(), ladder.single().dropped)
        assertEquals(goldenOrder, ladder.single().kept)
    }

    // --- generation driver -------------------------------------------------

    @Test
    fun errorCodes_exposeAllTypedWireValues() {
        assertEquals(
            listOf(
                "no_courses_selected",
                "no_valid_slots",
                "no_conflict_free",
                "below_min_half_days",
                "below_min_faculty_scores",
                "no_separate_labs",
                "no_consecutive_labs",
                "error"
            ),
            FfcsErrorCode.values().map { it.wire }
        )
        FfcsErrorCode.values().forEach { assertEquals(it, FfcsErrorCode.fromWire(it.wire)) }
        assertNull(FfcsErrorCode.fromWire("unknown"))
        assertNull(FfcsErrorCode.fromWire(null))
    }

    @Test
    fun driver_reusesRunIdAndSnapshotAndPostsOnlyDroppedCodes() {
        val cache = FfcsGenerationRun.SnapshotCache()
        val driver = FfcsGenerationRun.Driver(
            policy = DropPolicy(courseOrder = goldenOrder, protectedCodes = listOf("ENG1010", "PHY1020")),
            initialParams = GenerationParams(
                masterCourses = goldenOrder.map { offering(it) },
                courseLocks = goldenOrder.map { CourseLock(code = it, title = "Lock $it") }
            ),
            runId = "run-1",
            snapshots = cache
        )

        val first = driver.firstAttempt()
        assertEquals("run-1", first.runId)
        assertEquals(emptyList<String>(), first.droppedCodes)
        assertEquals(4, first.params.masterCourses.size)
        assertEquals(1, cache.size)

        val retry = driver.nextAttemptAfter(
            FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_CONFLICT_FREE)
        )
        assertNotNull(retry)
        assertEquals("run-1", retry.runId)
        assertEquals(1, cache.size, "The catalog snapshot is created once per runId.")
        assertTrue(cache.contains("run-1"))
        assertEquals(listOf("CSE2001"), retry.droppedCodes, "Protected tail is [CSE2001, MAT2005].")
        assertEquals(FfcsGenerationRun.RetryRequest("run-1", listOf("CSE2001")), retry.retryWire)

        // withCoursesDropped removes codes from BOTH the catalogue and the locks.
        assertFalse(retry.params.masterCourses.any { it.code == "CSE2001" })
        assertFalse(retry.params.courseLocks.any { it.code == "CSE2001" })
        assertEquals(3, retry.params.masterCourses.size)
        assertEquals(3, retry.params.courseLocks.size)
        assertEquals(3, cache.effectiveCourses("run-1", retry.droppedCodes)?.size)

        val secondRetry = driver.nextAttemptAfter(
            FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_CONFLICT_FREE)
        )
        assertNotNull(secondRetry)
        assertEquals("run-1", secondRetry.runId)
        assertEquals(listOf("CSE2001", "MAT2005"), secondRetry.droppedCodes)
    }

    @Test
    fun driver_noValidSlotsFailureJumpsTheQueue() {
        val driver = FfcsGenerationRun.Driver(
            policy = DropPolicy(courseOrder = goldenOrder),
            initialParams = GenerationParams(masterCourses = goldenOrder.map { offering(it) }),
            runId = "run-jump"
        )
        driver.firstAttempt()
        val retry = driver.nextAttemptAfter(
            FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_VALID_SLOTS, subjectCode = "CSE2001")
        )
        assertEquals(listOf("CSE2001"), retry?.droppedCodes)
    }

    @Test
    fun driver_doesNotRetryCancelledCrashSuccessNoCoursesOrAfterBatches() {
        fun newDriver() = FfcsGenerationRun.Driver(
            policy = DropPolicy(courseOrder = goldenOrder),
            initialParams = GenerationParams(masterCourses = goldenOrder.map { offering(it) }),
            runId = "run-gate"
        )

        val cancelled = newDriver().let {
            it.firstAttempt()
            it.nextAttemptAfter(FfcsGenerationRun.Outcome.Cancelled())
        }
        assertNull(cancelled, "cancelled must never restart the ladder.")

        val crash = newDriver().let {
            it.firstAttempt()
            it.nextAttemptAfter(FfcsGenerationRun.Outcome.Error(FfcsErrorCode.ERROR))
        }
        assertNull(crash, "A crash (code 'error') must never restart the ladder.")

        val noCourses = newDriver().let {
            it.firstAttempt()
            it.nextAttemptAfter(FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_COURSES_SELECTED))
        }
        assertNull(noCourses, "no_courses_selected cannot be fixed by dropping courses.")

        val success = newDriver().let {
            it.firstAttempt()
            it.nextAttemptAfter(FfcsGenerationRun.Outcome.Success())
        }
        assertNull(success)

        val afterBatches = newDriver().let {
            it.firstAttempt()
            it.recordBatch()
            assertTrue(it.hasStreamedBatches)
            it.nextAttemptAfter(FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_CONFLICT_FREE))
        }
        assertNull(afterBatches, "Once batches were streamed a failure must not replay the run.")
    }

    @Test
    fun driver_stopsAfterMaxDrops() {
        val driver = FfcsGenerationRun.Driver(
            policy = DropPolicy(courseOrder = goldenOrder),
            initialParams = GenerationParams(masterCourses = goldenOrder.map { offering(it) }),
            runId = "run-budget"
        )
        driver.firstAttempt()
        var retryCount = 0
        repeat(10) {
            if (driver.nextAttemptAfter(
                    FfcsGenerationRun.Outcome.Error(FfcsErrorCode.NO_CONFLICT_FREE)
                ) != null
            ) {
                retryCount++
            }
        }
        assertEquals(3, retryCount)
        assertEquals(listOf("ENG1010", "CSE2001", "PHY1020"), driver.dropped)
    }
}
