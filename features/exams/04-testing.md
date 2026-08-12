# Exam Awareness — Testing

## 1. Unit tests

**Location**: `shared/src/commonTest/kotlin/com/amazecc/app/shared/utils/ExamUtilsTest.kt`

### Date parsing (`parseExamDateToLocalDate`)
| Input | Expected |
|---|---|
| `"19-Nov-2025"` | `2025-11-19` |
| `"19-Nov-2025 09:15"` (with time suffix) | `2025-11-19` |
| `"2025-11-19"` | `2025-11-19` |
| `"19/11/2025"` | `2025-11-19` |
| `"19-11-2025"` | `2025-11-19` |
| `"03-Mar-2026"` | `2026-03-03` |
| `""`, `"garbage"`, `"31-Feb-2025"` | `null` |

### Time parsing (`examTimeToMinutes`)
| Input | Expected |
|---|---|
| `"09:00 AM"` | `540` |
| `"09:15 AM - 12:30 PM"` | `555` |
| `"12:00 PM"` | `720` |
| `"12:30 AM"` | `30` |
| `"2:00 PM"` | `840` |
| `""` | `null` |

### Seat location (`calculateSeatLocation`)
| seatNo | courseTitle | Expected |
|---|---|---|
| `"1"` | `"Data Structures and Algorithms"` | `"R1C1"` |
| `"2"` | `"Data Structures and Algorithms"` | `"R1C2"` |
| `"18"` | `"Data Structures and Algorithms"` | `"R9C2"` |
| `"19"` | `"Data Structures and Algorithms"` | `"R1C3"` |
| `"24"` | `"Operating Systems"` | `"R3C4"` |
| `"41"` | `"Complex Variables and Linear Algebra"` | `"R3C5"` |
| `"41"` | `"Qualitative Skills Practice II"` | `"-"` (exempt course) |
| `"29"` | `"French I"` | `"-"` (exempt course) |
| `"0"` / `"abc"` | anything | `"-"` |

> Note: web `demoData.json` `seatLocation` values (e.g. seat 41 → `R5C3`) are real VTOP
> data, **not** formula predictions — the formula only applies when `seatLocation == "-"`.

### 24h window (`nextExamWithin`)
- Exam at now+23h59m → returned; now+24h01m → null
- Reporting time `09:00 AM` tomorrow at 08:30 today → returned
- Past exams → null
- Selected-semester empty → fallback scan all semesters (test the selection helper)

### Day helpers
- `examsForDate` returns only matching; `isExamDate` true iff non-empty
- `sortedExamDays` orders by date, then time, then code

## 2. Manual QA checklist

### Calendar
1. Open Calendar → month with exams → day cells show exam tint.
2. Tap an exam day → event list shows full card: code, type badge, date, time, session,
   reporting time, venue, seat `R8C7 · No. 41`.
3. Month view still consolidates exam ranges; other event types (classes/holidays/ODs)
   unchanged; filters (Classes/Exams/Holidays/ODs) still work.
4. Exam cards navigate to Exam Schedule on tap.

### Exam Schedule screen
5. Each card: slot badge, session, reporting time, seat location + seat no.
6. Today's exams highlighted; past exams dimmed; groups sorted by date.

### Timetable
7. Daily Planner: day chip shows `EXAM` on exam days; banner above timeline with full
   details; classes still listed.
8. Timetable Grid: EXAM marker on chips; banner in overview and selected-day view.

### Notifications
9. Settings → Data & Sync → enable "Exam Reminders".
10. Exam ≥24h away → a "Exam Tomorrow" notification lands at T−24h (test with a near
    exam or temporarily tweak `withinHours`).
11. On exam day, reporting-time notification fires at `reportingTime − lead offset`.
12. **Suppression**: with Class Reminders enabled, a date with an exam produces NO class
    reminder, only the exam reminder. A normal day still produces class reminders.
13. Toggling Exam Reminders off reschedules (no exam alarms left). Test notification
    still works (id 9999 unaffected).
14. Reboot/force-stop → `rescheduleFromCache` re-arms exam alarms (BootReceiver path).

### Home widget
15. No exam within 24h → widget invisible.
16. Exam within 24h → "EXAM ALERT" card with countdown, venue, reporting time, session,
    seat; countdown ticks every minute.
17. Tap card → Exam Schedule screen opens.
18. Manage Widgets: toggle "Exam Alert (24h)" on/off; reorder; "Hidden Widgets" row shows
    it when disabled.

### Edge cases
19. Old cached exam data (missing new fields) → displays `TBD`, no crash, refreshes after
    next sync.
20. `seatLocation = "-"` with valid seatNo → computed `R..C..`; soft-skill/language
    courses → `TBD`.
21. Two exams same day → both notified; single suppression day.
22. Semester dropdown switch → widget + notifications follow the newly selected semester.

## 3. Non-goals verified
- No changes to sync modules or cache keys; existing cached data still loads.
- Class reminder IDs (1000–1999) unchanged; exam IDs occupy the previously-free 3000–3999.
