package com.amazecc.app.shared.repository

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-122 — the persisted web settings blob must round-trip exactly the way
 * `../AmazeCC/src/store/settingsAtoms.ts` behaves: defaults on a missing blob,
 * a merge over defaults for a partial blob, and the two legacy normalizations
 * performed by `normalizeDefaultSyncMode` / the `manual` → `off` shim.
 */
class SettingsManagerWebParityTest {

    @BeforeTest
    fun clearBlobBefore() {
        SettingsManager.remove(SettingsManager.KEY_WEB_SETTINGS)
    }

    @AfterTest
    fun clearBlobAfter() {
        SettingsManager.remove(SettingsManager.KEY_WEB_SETTINGS)
    }

    @Test
    fun missingBlobYieldsExactWebDefaults() {
        val defaults = SettingsManager.getWebSettings()
        assertEquals(SettingsManager.WebSettings.DEFAULT, defaults)
        assertEquals("quarterly", defaults.defaultSyncMode)
        assertEquals("off", defaults.autoSyncInterval)
        assertEquals(75, defaults.targetAttendance)
        assertEquals(15, defaults.classReminderLeadMinutes)
        assertEquals(listOf("academics", "attendance"), defaults.pinnedNavTabs)
        assertTrue(defaults.syncProfileData && defaults.syncAdditionalData)
        assertTrue(defaults.notifyAssignments && defaults.notifyLowAttendance)
    }

    @Test
    fun partialBlobMergesOverDefaults() {
        // The web getItem merges `{ ...initialValue, ...JSON.parse(raw) }` —
        // absent fields must pick up their default, not become null/false.
        SettingsManager.setString(SettingsManager.KEY_WEB_SETTINGS, """{"friendlyName":"Arjun"}""")
        val merged = SettingsManager.getWebSettings()
        assertEquals("Arjun", merged.friendlyName)
        // The blob supplied only `friendlyName`; every other field must come
        // from the defaults. Compare against the exact expected merge result.
        // (The earlier assertion compared DEFAULT against merged.copy(name),
        // which differs precisely in that supplied field; the merge was right.)
        assertEquals(
            SettingsManager.WebSettings.DEFAULT.copy(friendlyName = "Arjun"),
            merged,
        )
    }

    @Test
    fun legacySyncValuesAreNormalised() {
        SettingsManager.setString(
            SettingsManager.KEY_WEB_SETTINGS,
            """{"defaultSyncMode":"quick","autoSyncInterval":"manual"}""",
        )
        val normalised = SettingsManager.getWebSettings()
        assertEquals("daily", normalised.defaultSyncMode)
        assertEquals("off", normalised.autoSyncInterval)
    }

    @Test
    fun corruptBlobDegradesToDefaults() {
        SettingsManager.setString(SettingsManager.KEY_WEB_SETTINGS, "not-json")
        assertEquals(SettingsManager.WebSettings.DEFAULT, SettingsManager.getWebSettings())
    }

    @Test
    fun nonDefaultValuesRoundTripThroughTheBlob() {
        val edited = SettingsManager.WebSettings.DEFAULT.copy(
            calendarType = "WEI",
            targetAttendance = 85,
            residentialStatus = "dayscholar",
            timetablePillStyle = "detailed",
            pushQuietHoursEnabled = true,
            pushQuietHoursStart = "23:30",
            tasksInlineOnHome = false,
            taskPomodoroFocus = 40,
            pinnedNavTabs = listOf("attendance", "libraries"),
        )
        SettingsManager.setWebSettings(edited)
        assertEquals(edited, SettingsManager.getWebSettings())

        // Patch-in-place must preserve every untouched field.
        SettingsManager.updateWebSetting { it.copy(friendlyName = "Meera") }
        assertEquals("Meera", SettingsManager.getWebSettings().friendlyName)
        assertEquals("WEI", SettingsManager.getWebSettings().calendarType)
        assertEquals(85, SettingsManager.getWebSettings().targetAttendance)
    }

    @Test
    fun friendlyNameUsesTheSharedConstant() {
        SettingsManager.setFriendlyName("Arjun Kumar")
        assertEquals("Arjun Kumar", SettingsManager.getFriendlyName())
        assertEquals("Arjun Kumar", SettingsManager.getString(SettingsManager.KEY_FRIENDLY_NAME))
        assertEquals(SettingsManager.KEY_FRIENDLY_NAME, "friendly_name")
    }
}
