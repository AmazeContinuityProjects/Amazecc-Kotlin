package com.amazecc.app.shared.utils

import kotlinx.datetime.*
import kotlinx.serialization.json.*

// Expect functions to be implemented in platform-specific code
// (Android: NotificationManager, iOS: UNUserNotificationCenter)
expect suspend fun requestNotificationPermissions(): Boolean

expect suspend fun scheduleLocalNotification(
    id: Int,
    title: String,
    body: String,
    triggerTimeMs: Long
)

expect suspend fun clearPendingNotifications()

object NotificationsUtils {

    suspend fun scheduleLocalNotification(title: String, body: String, delayMs: Long = 5000): Boolean {
        val hasPermission = requestNotificationPermissions()
        if (!hasPermission) {
            println("Notification permissions not granted")
            return false
        }

        val triggerTime = Clock.System.now().toEpochMilliseconds() + delayMs
        val uniqueId = (Clock.System.now().toEpochMilliseconds() % Int.MAX_VALUE).toInt()

        scheduleLocalNotification(
            id = uniqueId,
            title = title,
            body = body,
            triggerTimeMs = triggerTime
        )

        return true
    }

    suspend fun testLocalNotification(): Boolean {
        return scheduleLocalNotification(
            title = "AmazeCC Reminder",
            body = "This is a local notification triggered from KMP!",
            delayMs = 5000
        )
    }

    suspend fun scheduleClassNotifications(
        attendance: List<Map<String, Any>>,
        slotMap: Map<String, Map<String, SlotInfo>>,
        offsetMinutes: Int = 15
    ) {
        val hasPermission = requestNotificationPermissions()
        if (!hasPermission) return

        // Clear existing notifications to avoid duplicates when rescheduling
        clearPendingNotifications()

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        
        var idCounter = 1000 // Start at 1000 to avoid ID collisions

        // Schedule for the next 7 days
        for (i in 0 until 7) {
            val targetDate = today.plus(DatePeriod(days = i))
            val currentMomentForTarget = targetDate.atStartOfDayIn(tz)
            
            // Re-using the logic from AttendanceTimetable.kt
            // In KMP we need to pass a specific date to get classes. 
            // We can extend AttendanceTimetable to accept a LocalDate.
            // For now, we will simulate this by checking dayOfWeek.
            
            val dayOfWeek = targetDate.dayOfWeek
            val attendanceDay = when (dayOfWeek) {
                DayOfWeek.MONDAY -> AttendanceDay.MON
                DayOfWeek.TUESDAY -> AttendanceDay.TUE
                DayOfWeek.WEDNESDAY -> AttendanceDay.WED
                DayOfWeek.THURSDAY -> AttendanceDay.THU
                DayOfWeek.FRIDAY -> AttendanceDay.FRI
                DayOfWeek.SATURDAY -> AttendanceDay.SAT
                DayOfWeek.SUNDAY -> AttendanceDay.SUN
                else -> AttendanceDay.MON
            }
            
            val dayCardsMap = AttendanceTimetable.buildAttendanceDayCardsMap(attendance, slotMap)
            val classes = dayCardsMap[attendanceDay] ?: emptyList()

            for (c in classes) {
                val timeRange = AttendanceTimetable.getAttendanceTimeRange(c.time)
                val classStartHours = timeRange.start / 60
                val classStartMins = timeRange.start % 60
                
                val classStartTime = LocalDateTime(
                    targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth,
                    classStartHours, classStartMins, 0, 0
                )
                
                val classStartTimeInstant = classStartTime.toInstant(tz)
                val notifyTime = classStartTimeInstant.minus(offsetMinutes.toLong(), DateTimeUnit.MINUTE)
                
                // Only schedule if the notification time is in the future
                if (notifyTime > now) {
                    val timeString = c.time.split("-").firstOrNull()?.trim() ?: ""
                    
                    scheduleLocalNotification(
                        id = idCounter++,
                        title = "Upcoming Class",
                        body = "${c.courseTitle} starts in $offsetMinutes minutes at $timeString (${c.courseType})",
                        triggerTimeMs = notifyTime.toEpochMilliseconds()
                    )
                }
            }
        }
    }
}
