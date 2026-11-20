package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.model.ExamScheduleRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Headless checks for the exam-schedule host wiring: the exam-specific semester selector,
 * error-state derivation from both exception and payload, and recognition of real VTOP
 * exam-type labels so their papers do not disappear behind the CAT/FAT filters.
 */
class ExamWiringTest {

    // ── Error state: getExamSchedule returns non-null failure payloads ──

    @Test
    fun `exception error takes precedence over a failed payload`() {
        val payload = ExamScheduleRes(success = false, error = "Payload error", message = "Payload message")
        assertEquals("Network down", examScheduleErrorMessage("Network down", payload))
    }

    @Test
    fun `failed payload error and message are surfaced when there was no exception`() {
        assertEquals(
            "Empty response",
            examScheduleErrorMessage(null, ExamScheduleRes(success = false, error = "Empty response"))
        )
        assertEquals(
            "Empty response",
            examScheduleErrorMessage(null, ExamScheduleRes(success = false, message = "Empty response"))
        )
    }

    @Test
    fun `a successful payload has no error even when its schedule is empty`() {
        assertNull(examScheduleErrorMessage(null, ExamScheduleRes(success = true)))
        assertNull(examScheduleErrorMessage(null, null))
    }

    // ── Real VTOP exam-type labels fold into a concrete, offered filter ──

    @Test
    fun `mid semester examination lands in a concrete series rather than ALL`() {
        assertEquals(ExamSeries.CAT2, M3EExamScheduleComputations.seriesFor("MID SEMESTER EXAMINATION"))
        assertEquals(ExamSeries.CAT2, M3EExamScheduleComputations.seriesFor("MIDTERM EXAMINATION"))
        assertEquals(ExamSeries.FAT, M3EExamScheduleComputations.seriesFor("END SEMESTER EXAMINATION"))
        assertEquals(ExamSeries.FAT, M3EExamScheduleComputations.seriesFor("FAT"))
    }

    @Test
    fun `availableSeries offers the recognised bucket for real VTOP keys`() {
        assertEquals(
            listOf(ExamSeries.ALL, ExamSeries.CAT2),
            M3EExamScheduleComputations.availableSeries(listOf("MID SEMESTER EXAMINATION"))
        )
        assertEquals(
            listOf(ExamSeries.ALL, ExamSeries.CAT2, ExamSeries.FAT),
            M3EExamScheduleComputations.availableSeries(listOf("MID SEMESTER EXAMINATION", "FAT"))
        )
    }

    @Test
    fun `truly unknown labels still fold into ALL without a phantom filter`() {
        assertEquals(ExamSeries.ALL, M3EExamScheduleComputations.seriesFor("something unrecognised"))
        assertEquals(
            listOf(ExamSeries.ALL),
            M3EExamScheduleComputations.availableSeries(listOf("something unrecognised"))
        )
    }
}
