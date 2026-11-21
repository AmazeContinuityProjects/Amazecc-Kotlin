package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.extractFeedbackStatus
import com.amazecc.app.shared.model.FeedbackSemester
import com.amazecc.app.shared.model.FeedbackStatusRes
import com.amazecc.app.shared.model.FeedbackTableRow
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-154: feedback status is a durable part of the FFCS data contract. The mirror key keeps
 * it alive across cold starts without adding a field to [AppDataSnapshot], and one shared
 * extractor handles both FFCS wire shapes.
 */
class FeedbackStatusCacheTest {

    private fun clearPersisted() {
        SettingsManager.remove(SettingsManager.CACHE_APP_DATA)
        SettingsManager.remove(SettingsManager.CACHE_FFCS_FEEDBACK_STATUS)
        SettingsManager.remove(SettingsManager.KEY_SELECTED_SEMESTER)
    }

    @BeforeTest
    fun setUp() {
        clearPersisted()
        AppDataStore.clear()
    }

    @AfterTest
    fun tearDown() {
        AppDataStore.clear()
        clearPersisted()
    }

    // Non-ASCII is written with \u escapes so the fixture is encoding-independent.
    private val sample = FeedbackStatusRes(
        success = false,
        error = "unavailable \u2014 \u0939\u093F\u0928\u094D\u0926\u0940",
        semesters = listOf(
            FeedbackSemester(text = "Fall 2026-27 \u00B7 \u4E2D\u6587", value = "CH20262701", selected = true),
            FeedbackSemester(text = "Summer 2026 \u2600", value = "CH20262601")
        ),
        feedbackTable = listOf(
            FeedbackTableRow(
                feedbackType = "CSE1001 \u00B7 Programming / Dr. Anita R\u00E1o",
                midSemester = "Feed Back Given For : CSE1001 \u2713",
                teeSemester = "Not Given"
            ),
            FeedbackTableRow(feedbackType = "MSE \u00B7 \u0935\u093F\u0937\u092F", midSemester = "\u0926\u093F\u092F\u093E", teeSemester = "")
        )
    )

    @Test
    fun setThenColdStartRestorePreservesEveryField() {
        AppDataStore.setFeedbackStatus(sample)
        assertEquals(sample, AppDataStore.feedbackStatus.value)

        // Simulate a fresh process: memory is gone, the persisted mirror is not.
        AppDataStore.clear()
        assertNull(AppDataStore.feedbackStatus.value)

        AppDataStore.restore()

        val restored = AppDataStore.feedbackStatus.value
        assertEquals(sample.success, restored?.success)
        assertEquals(sample.error, restored?.error)
        assertEquals(sample.semesters, restored?.semesters)
        assertEquals(sample.feedbackTable, restored?.feedbackTable)
        assertEquals(sample, restored)
    }

    @Test
    fun absentOrCorruptStoredPayloadRestoresToNullWithoutThrowing() {
        // Absent mirror key.
        AppDataStore.setFeedbackStatus(sample)
        SettingsManager.remove(SettingsManager.CACHE_FFCS_FEEDBACK_STATUS)
        AppDataStore.clear()
        AppDataStore.restore()
        assertNull(AppDataStore.feedbackStatus.value)

        // Corrupt JSON.
        SettingsManager.setString(SettingsManager.CACHE_FFCS_FEEDBACK_STATUS, "not-json")
        AppDataStore.restore()
        assertNull(AppDataStore.feedbackStatus.value)

        // Well-formed JSON, wrong shape for FeedbackStatusRes.
        SettingsManager.setString(SettingsManager.CACHE_FFCS_FEEDBACK_STATUS, "[1,2,3]")
        AppDataStore.restore()
        assertNull(AppDataStore.feedbackStatus.value)
    }

    /** The status object as it appears either nested under `feedbackStatus` or as the whole body. */
    private fun statusObject() = buildJsonObject {
        put("success", false)
        put("error", "unavailable \u2014 \u0939\u093F\u0928\u094D\u0926\u0940")
        put("semesters", buildJsonArray {
            add(buildJsonObject {
                put("text", "Fall 2026-27 \u00B7 \u4E2D\u6587")
                put("value", "CH20262701")
                put("selected", true)
            })
        })
        put("feedbackTable", buildJsonArray {
            add(buildJsonObject {
                put("feedbackType", "CSE1001 \u00B7 Programming / Dr. Anita R\u00E1o")
                put("midSemester", "Given \u2713")
                put("teeSemester", "Not Given")
            })
        })
    }

    @Test
    fun extractionHelperHandlesBothShapesWithUnicodePreserved() {
        // Combined section=all envelope: the nested slice wins over the outer envelope.
        val nested = buildJsonObject {
            put("success", true)
            put("registrationSchedule", buildJsonObject { put("raw", "schedule") })
            put("feedbackStatus", statusObject())
            put("feedback", buildJsonObject { put("raw", "simple") })
            put("wishlist", buildJsonObject { put("raw", "wishlist") })
            put("schedule", buildJsonObject { put("raw", "schedule") })
            put("qcmView", buildJsonObject { put("raw", "qcm") })
        }
        val fromNested = extractFeedbackStatus(nested)
        assertEquals(false, fromNested?.success)
        assertEquals("unavailable \u2014 \u0939\u093F\u0928\u094D\u0926\u0940", fromNested?.error)
        assertEquals("Fall 2026-27 \u00B7 \u4E2D\u6587", fromNested?.semesters?.single()?.text)
        assertEquals("CSE1001 \u00B7 Programming / Dr. Anita R\u00E1o", fromNested?.feedbackTable?.single()?.feedbackType)
        assertEquals("Given \u2713", fromNested?.feedbackTable?.single()?.midSemester)

        // section=feedback body: the status is the whole payload.
        val fromBare = extractFeedbackStatus(statusObject())
        assertEquals(fromNested, fromBare)

        // Web parity: a present `feedbackStatus` (even null) wins — no top-level fallback.
        val presentNull = buildJsonObject {
            put("feedbackStatus", JsonNull)
            put("semesters", buildJsonArray {})
        }
        assertNull(extractFeedbackStatus(presentNull))
    }
}
