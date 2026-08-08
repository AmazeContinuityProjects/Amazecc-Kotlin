package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.utils.cancelSyncAlarms
import com.amazecc.app.shared.utils.scheduleSyncAlarm
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Rule computation + persistence for the leveled sync automation:
 *  - LIGHT reload: daily at HH:MM, or every N days at HH:MM
 *  - FULL reload: weekly on a chosen weekday (1=Mon..7=Sun) at HH:MM
 *
 * Next-run instants are persisted so foreground catch-up works without
 * alarms; alarms are (re)armed through the expect/actual in SyncAlarm.kt.
 */
object SyncScheduler {
    const val LIGHT_KIND = "light"
    const val FULL_KIND = "full"
    const val DEFAULT_LIGHT_PROFILE = "daily_reload"
    const val DEFAULT_FULL_PROFILE = "full_sync"

    private fun parseInstant(raw: String): Instant? = try { Instant.parse(raw) } catch (_: Exception) { null }

    // ── Master switch ──

    fun isEnabled(): Boolean = SettingsManager.getBoolean(SettingsManager.KEY_AUTO_SYNC_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        SettingsManager.setBoolean(SettingsManager.KEY_AUTO_SYNC_ENABLED, enabled)
        if (enabled) rescheduleAlarms() else cancelSyncAlarms()
    }

    // ── Light rule (daily or every-N-days) ──

    fun lightProfileId(): String = SettingsManager.getString(SettingsManager.KEY_LIGHT_PROFILE_ID).ifBlank { DEFAULT_LIGHT_PROFILE }
    fun setLightProfileId(id: String) { SettingsManager.setString(SettingsManager.KEY_LIGHT_PROFILE_ID, id); rescheduleAlarms() }

    fun isLightDaily(): Boolean = SettingsManager.getString(SettingsManager.KEY_LIGHT_RECURRENCE, "daily") == "daily"
    fun setLightRecurrence(daily: Boolean) { SettingsManager.setString(SettingsManager.KEY_LIGHT_RECURRENCE, if (daily) "daily" else "interval"); rescheduleAlarms() }

    fun lightIntervalDays(): Int = SettingsManager.getString(SettingsManager.KEY_LIGHT_INTERVAL_DAYS).toIntOrNull() ?: 1
    fun setLightIntervalDays(days: Int) { SettingsManager.setString(SettingsManager.KEY_LIGHT_INTERVAL_DAYS, days.coerceIn(1, 30).toString()); rescheduleAlarms() }

    fun lightHour(): Int = SettingsManager.getString(SettingsManager.KEY_LIGHT_HOUR).toIntOrNull() ?: 7
    fun lightMinute(): Int = SettingsManager.getString(SettingsManager.KEY_LIGHT_MINUTE).toIntOrNull() ?: 0
    fun setLightTime(hour: Int, minute: Int) {
        SettingsManager.setString(SettingsManager.KEY_LIGHT_HOUR, hour.coerceIn(0, 23).toString())
        SettingsManager.setString(SettingsManager.KEY_LIGHT_MINUTE, minute.coerceIn(0, 59).toString())
        rescheduleAlarms()
    }

    // ── Full rule (weekly) ──

    fun fullProfileId(): String = SettingsManager.getString(SettingsManager.KEY_FULL_PROFILE_ID).ifBlank { DEFAULT_FULL_PROFILE }
    fun setFullProfileId(id: String) { SettingsManager.setString(SettingsManager.KEY_FULL_PROFILE_ID, id); rescheduleAlarms() }

    fun fullDayOfWeek(): Int = SettingsManager.getString(SettingsManager.KEY_FULL_DAY_OF_WEEK).toIntOrNull() ?: 7
    fun setFullDayOfWeek(day: Int) { SettingsManager.setString(SettingsManager.KEY_FULL_DAY_OF_WEEK, day.coerceIn(1, 7).toString()); rescheduleAlarms() }

    fun fullHour(): Int = SettingsManager.getString(SettingsManager.KEY_FULL_HOUR).toIntOrNull() ?: 21
    fun fullMinute(): Int = SettingsManager.getString(SettingsManager.KEY_FULL_MINUTE).toIntOrNull() ?: 0
    fun setFullTime(hour: Int, minute: Int) {
        SettingsManager.setString(SettingsManager.KEY_FULL_HOUR, hour.coerceIn(0, 23).toString())
        SettingsManager.setString(SettingsManager.KEY_FULL_MINUTE, minute.coerceIn(0, 59).toString())
        rescheduleAlarms()
    }

    // ── Next-run state ──

    fun getNextRun(kind: String): Instant? {
        val key = if (kind == FULL_KIND) SettingsManager.KEY_NEXT_FULL_SYNC else SettingsManager.KEY_NEXT_LIGHT_SYNC
        val raw = SettingsManager.getString(key)
        if (raw.isBlank()) return null
        return parseInstant(raw)
    }

    fun lastSyncedAt(): Instant? {
        val raw = SettingsManager.getString(SettingsManager.KEY_LAST_SYNCED_AT)
        if (raw.isBlank()) return null
        return parseInstant(raw)
    }

    fun markSynced() {
        SettingsManager.setString(SettingsManager.KEY_LAST_SYNCED_AT, Clock.System.now().toString())
    }

    // ── Occurrence computation ──

    private fun nextOccurrence(
        hour: Int,
        minute: Int,
        weekday: Int? = null,
        intervalDays: Int? = null,
        anchor: Instant? = null,
        now: Instant = Clock.System.now()
    ): Instant {
        val tz = TimeZone.currentSystemDefault()
        val nowLdt = now.toLocalDateTime(tz)
        if (weekday != null) {
            var days = (weekday - (nowLdt.dayOfWeek.ordinal + 1) + 7) % 7
            val todayAt = LocalDateTime(nowLdt.date, LocalTime(hour, minute)).toInstant(tz)
            if (days == 0 && todayAt > now) return todayAt
            if (days == 0) days = 7
            return LocalDateTime(nowLdt.date.plus(DatePeriod(days = days)), LocalTime(hour, minute)).toInstant(tz)
        }
        if (intervalDays != null && intervalDays > 1 && anchor != null) {
            val anchorLdt = anchor.toLocalDateTime(tz)
            var days = 0
            var next: Instant
            do {
                days += intervalDays
                next = LocalDateTime(anchorLdt.date.plus(DatePeriod(days = days)), LocalTime(hour, minute)).toInstant(tz)
            } while (next <= now)
            return next
        }
        var candidate = LocalDateTime(nowLdt.date, LocalTime(hour, minute)).toInstant(tz)
        if (candidate <= now) {
            candidate = LocalDateTime(nowLdt.date.plus(DatePeriod(days = 1)), LocalTime(hour, minute)).toInstant(tz)
        }
        return candidate
    }

    private fun setNextRun(kind: String, at: Instant) {
        val key = if (kind == FULL_KIND) SettingsManager.KEY_NEXT_FULL_SYNC else SettingsManager.KEY_NEXT_LIGHT_SYNC
        SettingsManager.setString(key, at.toString())
    }

    /** Compute + persist + arm the next run for a schedule kind. */
    fun advanceAndArm(kind: String) {
        if (!isEnabled()) return
        val now = Clock.System.now()
        val next = when (kind) {
            FULL_KIND -> nextOccurrence(fullHour(), fullMinute(), weekday = fullDayOfWeek(), now = now)
            else -> {
                val anchor = getNextRun(LIGHT_KIND) ?: now
                nextOccurrence(
                    hour = lightHour(), minute = lightMinute(),
                    intervalDays = if (isLightDaily()) 1 else lightIntervalDays(),
                    anchor = anchor, now = now
                )
            }
        }
        setNextRun(kind, next)
        scheduleSyncAlarm(next.toEpochMilliseconds(), kind)
    }

    /** Recompute both schedules (e.g. after settings change or boot). */
    fun rescheduleAlarms() {
        if (!isEnabled()) return
        advanceAndArm(LIGHT_KIND)
        advanceAndArm(FULL_KIND)
    }
}
