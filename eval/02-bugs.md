# Evaluation 02 — Bug Catalog

All bugs with exact `file:line`, verified in source (or clearly marked "verified by agent trace" where the trace is deterministic). Severity: **CRITICAL / HIGH / MED / LOW**.

---

## CRITICAL

### C1. Demo-login backdoor in production
`AmazeClient.kt:97` — `if (useMockData || username.lowercase() == "demo" || username.uppercase() == "DEMO123")` returns `success=true` + fake cookies. Username `DEMO123` (or `demo`) is accepted on any install, any build. Every downstream endpoint then serves mock data. See `07-security.md`.
- Related UI: `LoginScreen.kt:329-333` (auto-enables mock after real login with demo creds), `:358-392` (explicit "Explore in Demo Mode" button), `:376` (`setUseMockData(true)`).
- `AppState.kt:550` resets `setUseMockData(false)` after `restoreSession()` — but the session was already established as DEMO123.

### C2. Plaintext credentials + session tokens
- `SettingsManager.kt:16` `KEY_PASSWORD`, `:48` `KEY_LIBRARY_PASSWORD`, `:73` `KEY_MOODLE_PASSWORD`, `:41-44` session cookies/CSRF/authorizedID — all unencrypted in SharedPreferences.
- Passwords re-sent in clear to `api.amazecc.com`: `AmazeClient.kt:1176-1187` (getEventPreview), `:1200-1210` (registerForEvent), `:1225-1228` (eventLogin), `:1234` (SessionManager.saveEventHubSession).
- `AmazeClient.kt:122-136` — full `cookies` string (`vtop_session_cookie=…; csrf_token=…`) transmitted verbatim as a JSON body field on every `postAuthorized`.

### C3. Logout leaks the previous user's data
`AppState.kt:1945-2031` — `logout()` never clears (memory or disk): `_qcmView`, `_calendarsList`, `_vtopPhotoBase64`, `_tasks`, `CACHE_PROFILE_IMAGES`, `CACHE_BANK_INFO`, `CACHE_DAYBOARDER`, `CACHE_EPT_SCHEDULE`, `CACHE_REGISTRATION_SCHEDULE`, `CACHE_APAAR_ID`, `CACHE_QCM_VIEW`, `CACHE_CALENDARS_LIST`, `CACHE_TRANSPORT_DATA`, `CACHE_BUSES`, `CACHE_CIRCULARS`, `CACHE_VTOP_PHOTO`, `CACHE_ATTENDANCE_NOTES`, `course_note_*`, `CACHE_TASKS`, `CACHE_CAB_LOCAL_TRIPS`, `CACHE_OD_TRACKER_STATE`. Next login sees the previous student's tasks/notes/photo.
- Also: `_allSemesterMarks`/`_allSemesterAttendance`/`_allSemesterExams` cleared twice each (:1965/1995, :1966/1996, :1967/1997) — harmless duplication, symptom of unmaintained code.

### C4. QBank paper upload always 404s
`AmazeClient.kt:1507` — `postAuthorized("/api/qbank/upload", …)`; the helper at `:128` builds `"$baseUrl/api/$endpoint"` → `https://api.amazecc.com/api//api/qbank/upload`. Only endpoint with a leading `/api/` (all 52 others are relative). Fix: `"qbank/upload"`.

---

## HIGH

### H1. Academics hub attendance stat always 0%
`AcademicsScreen.kt:65` — `attendancePercentage` is a `String` carrying `%` (`"75%"`, see `AttendanceModels.kt:37`). `it.attendancePercentage.toDoubleOrNull()` → `null` → `0.0`. Every other consumer strips `%` first (`AttendanceScreen.kt:504,1053-1064`, `CourseAttendanceScreen.kt:196`). This one doesn't.

### H2. Weekly timetable dialog always says "No classes on X"
`TimetableComponents.kt:62` — `attendanceCourses.filter { it.slotName.uppercase().take(3) == selectedDay }`. `slotName` is `"A1+TA1"` (AmazeClient.kt:173; AttendanceModels.kt:31) → `.take(3)` = `"A1+"` ≠ `"MON"`. The day is the *key* in `SlotMap.map`, never part of slotName. Dialog is permanently empty.

### H3. `clearPendingNotifications()` cancels nothing
`NotificationsUtils.android.kt:57-61` — builds a PendingIntent with `requestCode=0` and cancels it; real alarms use request codes 1000/2000/4000/9999 (`NotificationsUtils.kt:42,80,120,134`). `AlarmManager.cancel` matches intent+requestCode → never matches. Every sync calls `clearPendingNotifications()` then re-schedules (commonMain:37); disabling a reminder toggle never cancels the old alarm; stale reminders keep firing after data changes. iOS actual (removeAllPendingNotificationRequests) is correct — Android-only bug.

### H4. Notification ID collision — only one reminder visible per type
`AlarmReceiver.kt:53` — `generateId(title)` = `title.hashCode()`. All class reminders share title "Upcoming Class" (`NotificationsUtils.kt:61`) → same notification ID → each `notify()` replaces the previous. Assignment/task alerts too.

### H5. CabShare fake-success on every failure
- `AppState.kt:2175-2186` — auth failure = logged in as `local_only` user, UI reports success.
- `AppState.kt:2236-2246` — trip create failure = "Trip saved locally!" success.
- `AppState.kt:2281` — join request failure = "Request saved locally!".
- `AppState.kt:2296` — match action failure = "Updated locally!".
- UI greens any message containing "sent"/"locally" (`CabShareScreen.kt:336`). Users are actively misled about sharing trips with other people.

### H6. iOS build broken by leftover `MarksSync.kt`
`MarksSync.kt:10` — `expect suspend fun hashStringSha256` has an actual only in androidMain (`MarksSync.android.kt:5`); iosArm64/iosSimulatorArm64 have none → `compileKotlinIosArm64`/`compileKotlinIosSimulatorArm64` fail with missing-actual. BUGS.md H5 claims this object was deleted; the file survived. Delete the whole file (zero callers).

### H7. iOS FileSaver is a silent no-op; Android <Q "saves" to cacheDir
- `FileSaver.ios.kt:6` — always returns false.
- `FileSaver.android.kt:21-31` — `openOutputStream` null → still returns `true`; API 24-28 writes to `context.cacheDir` (invisible, evictable) and returns `true`. Consumers: CourseDetailScreen.kt:1253, CurriculumScreen.kt:141 (PDF syllabus save).

### H8. Reminders lost on reboot
No `RECEIVE_BOOT_COMPLETED` permission/receiver anywhere (AndroidManifest.xml:4-10) — AlarmManager alarms die with the device; nothing re-schedules.

### H9. FFCS silently drops fully-blocked courses
`FfcsViewModel.kt:212-225` — `optionsPerCourse` built with `.mapNotNull { … .ifEmpty { null } }`; any course whose lock/block filters eliminate all offerings is dropped, and the engine's own empty-guard (`FfcsEngine.kt:95-97`) can never fire. A 5-course selection can silently produce a 4-course timetable with no notice.

### H10. FFCS lock model is cross-product, not pair-based
`FfcsViewModel.kt:154-178` — selecting (A2,F1)+(B1,F2) yields `allowedSlots=[A2,B1], allowedFaculty=[F1,F2]`; the engine's AND-filter (`FfcsEngine.kt:80-85`) generates F1@B1 and F2@A2 — combos never picked. Deselecting one offering removes a shared slot from the other. `setLock` (:142) also dead.

### H11. FFCS result IDs collide across timetables
`FfcsEngine.kt:144` — `id = "id_${c.code}_${i}"` — same id in every generated timetable (index-based, no timetable index). Duplicate-key trap for any `key(id)` consumer.

### H12. FFCS metrics lie
- `FfcsMetrics.kt:67-69` — an entirely free day counts as 1 half-day (should be 2).
- `FfcsMetrics.kt:104` — `gapsToday += gapMins / 60` integer floor: a 59-min gap = 0h, 61-min = 1h. "Gaps Xh" badge undercounts.
- `FfcsMetrics.kt:123` — class ending 1:25 PM (805) misses the free-afternoon credit (threshold 800 = 13:20) — off-by-5 at lunch boundary.
- `FfcsMetrics.kt:127-145` — building "dashes" computed between chronologically adjacent courses with **no time-gap check** (8 AM class + 5 PM class = "dash"), and `distinct()` on slots collapses same-day two-slot courses, breaking adjacency.
- `FfcsMetrics.kt:165` — `socialScore = 0` never computed.
- `FfcsMetrics.kt:154-156` — long-weekend flag: Mon-or-Fri empty (a Mon/Tue/Wed schedule flags as long weekend).
- `gapDetails`/`dashDetails` computed but never rendered — `selectedGapDetails` never passed by any caller (`FfcsTimetableGrid.kt:83`); gap highlighting permanently dead.

### H13. Widgets never push-updated; free-classrooms widget is fake
- No `AppWidgetManager.notifyAppWidgetViewDataChanged`/update broadcast anywhere after sync → widgets stale up to 15+ min (system batching on API 31+).
- `WidgetDataUtils.kt:202-243` `getFreeClassroomsSample` — hardcoded 5 rooms, filters by the user's own attendance, ignores the real `FacultyFreeSlotsUtil` engine and calendar day-order override (uses `now.dayOfWeek` raw).

### H14. `headerBackOverride` never cleared
`AppState.kt:2676-2698` — `setHeader(onBackOverride = null)` (the default) doesn't clear a previously set override; the next screen inherits the previous screen's back handler. `clearHeaderBackOverride()` exists with zero callers.

### H15. Notification channels misrouted + task offset ignored
- `NotificationsUtils.android.kt:36` — every schedule hardcodes `CHANNEL_CLASSES`; CHANNEL_ASSIGNMENTS/CHANNEL_TASKS (created :84-100) never used.
- `NotificationsUtils.kt:129` — task reminders hardcode 7:00 AM on due date; the `offsetMinutes: Int = 60` param (:112) is unused.
- `NotificationsUtils.kt:84-96` — assignment due-date parsed as `YYYY-MM-DD` from `split(" ")`; VTOP "DD-MM-YYYY HH:MM" → `LocalDateTime(20, 08, 2026)` → invalid → silently skipped. Moodle ISO `"2026-08-20T10:00"` has no space → skipped. **Moodle deadlines never notify.**

### H16. Session token slot collision
`SessionManager.kt:38-41` — `saveEventHubSession(jsessionid)` writes the EventHub JSESSIONID into the **same `clubToken` slot** used by VTOP login (:27-36). `getImageBytes` (`AmazeClient.kt:1311-1313`) then sends `Cookie: JSESSIONID=$token` to eventhubcc.vit.ac.in — if the slot holds the VTOP clubToken, the wrong cookie goes out. The two tokens can never coexist; `clearSession` destroys both.

### H17. MainTabPager renders a blank page for pinable screens
`SettingsScreen.kt:69-74` allows pinning `FFCS_PLANNER` and `FREE_CLASSROOMS`; `MainTabPager.kt:69-83` has no branch for them (`else -> {}`) → **blank pager page** if the user pins them. `BottomNavigationBar.kt:109` similarly renders `Icons.Rounded.Circle to "Unknown"` for `CALENDAR`/`PROJECTS`/`WISHLIST` pins.

### H18. QR encoder broken for versions ≥4
`QRCodeGenerator.kt` — `ecBytesPerBlock`/`blockCount` (4→18/2, 5→24/2, 6→16/4) are per-block values but `rsEncode` computes **one** block over the entire stream (v4 should be 2×18=36 codewords, gets 18); no alignment patterns placed for v2+; `blockCount(6)` returns 1. Versions 4-6 → guaranteed decode failure. Input >108 bytes → `null` (SocialScreen.kt:147 silently shows nothing; share codes routinely exceed this).

### H19. QR-capacity: `abs(hash) % size` negative-index risk
`SocialUtils.kt:138` — `colors[abs(hash) % colors.size]`; `abs(Int.MIN_VALUE)` is negative → IndexOutOfBoundsException (low probability, unguarded).

### H20. AcademicsScreen duplicated constants
`AcademicsScreen.kt:62` `totalRequiredCredits = 160.0` and `CurriculumScreen.kt` `totalRequired = 160` — same magic number twice, will drift.

---

## MEDIUM

### M1. `AttendanceItem.attendancePercentage: String`
`AttendanceModels.kt:37` — stringly-typed percentage with `%`; 6 call sites, only 5 strip it (H1 is the casualty). Backend emitting "0.79" or "79" breaks silently. Should be `Double` in the model.

### M2. `TimetableDialog` filter (covered by H2) + `getTodayDayIndex` off-by-one
`TimeMath.kt:52` — returns `DayOfWeek.ordinal` (MON=0). Zero callers, but a trap (ISO would be 1-7).

### M3. Settings toggles that gate nothing / don't persist
- `KEY_SYNC_ARREAR` written (`AppState.kt:2572`) never read (`AppState.kt:325-359`).
- `syncExam`/`syncAdditional` toggles — no gating logic in `loadAllData` (exam schedule always synced, `:986`).
- `setDecimalValues`/`setFriendlyName`/`setCalendarView` (`AppState.kt:2511-2519`) mutate flows, never persist — reset every launch.

### M4. `clearAll()` wipes everything but leaves SessionManager in-memory logged in
`SettingsManager.kt:132-134` (`clearAll`, wired to SettingsScreen.kt:454 "Clear All Local Caches") — persistence gone, in-memory session intact → inconsistent state until restart.

### M5. SyncEngine data races + cancellation mislabel
- `SyncEngine.kt:148-149` — `activeJobs`/`syncSessionModules` plain mutable collections mutated across coroutines.
- `SyncEngine.kt:285-288, 317-319` — read-modify-write on `_moduleStates`/`logLines` from multiple coroutines (lost updates).
- `SyncEngine.kt:338-341` — `catch (e: Exception)` swallows `CancellationException` → cancelled syncs marked ERROR.
- `SyncEngine.kt:323-347` — check-then-act duplicate-start race.
- App.kt:111 — "Sync All" calls `resetAllStates()` while jobs may be mid-flight → corrupted progress.

### M6. Empty catch blocks (14+)
`SyncEngine.kt:267`; `FriendsViewModel.kt:43,49,56,62`; `AppState.kt:336,340,1239,1450,1863`; `NotificationsUtils.android.kt:26`; `MainActivity.kt:34`; `AlarmReceiver.kt:50` (SecurityException — justified); `FeedbackStatusScreen.kt:143`. Corrupted caches → silently empty UI, no repair.

### M7. Silent data loss on parse failures
- `AttendanceTimetable.kt:19-26` — `parseViewLink` on double-escaped JSON → `JsonPrimitive` → every consumer's `.jsonArray` throws → swallowed → empty history (ODTrackerScreen.kt:77-85, WidgetDataUtils.kt:63-69, CourseAttendanceScreen.kt:568).
- `AnalyzeCalendar.kt:94-96` — 2-digit year in month string → `LocalDate(26, …)` wrong century.
- `AnalyzeCalendar.kt:172` — months with no `days` entries are classified **holiday** → inflated holiday counts.
- `AnalyzeCalendar.kt:211` — aliases dead: text normalized to `"cat1"` but aliases stored raw `"cat-1,cat 1"` → never match.
- `AnalyzeCalendar.kt:167` — non-object event → `jsonObject` throws → whole calendar analysis aborts.

### M8. Faculty matching over-permissive
- `FacultyUtils.kt:37` — alphanumeric 4-8 char token = ID; a name like "AM4IT" is swallowed.
- `FacultyUtils.kt:45` — last token 2-6 letters all-caps = "school"; surnames like "KUMAR" truncated.
- `FacultyFreeSlotsUtil.kt:98-104` — single-token faculty is a subset of every CSV row containing it → false matches (comment at :96-97 even warns about the behavior the code implements).
- `knownSchools` contains `"SMEC"` twice (`FacultyUtils.kt:12-16`).

### M9. FFCS CSV parsing fragile
`FfcsCourseProcessor.kt:49-50` — `endsWith("L")/("P")` suffix detection rewrites ALL courses of a base code (including plain TH) into embedded pairs; `:83,88` — post-remap `endsWith` conditions always false (dead); slot-prefix heuristic `startsWith("L")` misclassifies TH-in-L-lab and LO-in-theory-slot; `:94-116` — pairing `removeAt(0)` can produce a phantom leftover offering duplicating the pair's session; `:10-23` — `\"` escapes flip `inQuotes`, no multi-line quoted fields.

### M10. Free-classrooms "current period" lies
`FreeClassroomsScreen.kt:163` — `nowMinutes in (startMins-15)..endMins` — inclusive end (8:50 counts as ongoing at 8:50) + 15-min pre-roll; `:169` — `found.ifEmpty { timePeriods.first() }` claims period 1 when nothing matches; `:154` — weekends fall back to `"mon"`.

### M11. FFCS grid lies about the timetable
`FfcsTimetableGrid.kt:38-41` — hardcoded 9 theory periods (8:00–12:30, 2:00–5:35) missing S11/S15/S8B/L6..L30 (12:30-1:25), all evening slots (TG2, S3, S1/S2/S4, L35+). Courses in those slots render as "Free" and **cannot be blocked**, while the engine happily places them.
- `:63-78` — gutter/day-label drift: 38dp label rows vs 34dp cells → labels drift 4dp/row.
- `:119` — every cell clickable, including occupied course cells; blocking "MON|8:00-8:50" kills A1 **and** L1 (time-granularity, not slot-granularity).

### M12. `FreeClassroomsScreen` timing formats
`CampusSchemas.kt` mixes `"8:00 AM"` vs `"08:00 AM"` (theory vs lab); `timeToMinutes` (`FreeClassroomsScreen.kt:69-82`) only handles `"HH:MM AM/PM"`.

### M13. GPAPredictor bakes in VIT assessment math
`GPAPredictorScreen.kt:593-625` — `(c1 / 50.0 * 30.0) + (c2 / 50.0 * 30.0) + q` hardcodes 50-mark CATs with 30-point weight; `(requiredTotal - theoryInternal) / 0.4` hardcodes FAT weight 0.4. Silent wrong answers if weights change. Also seeds `ProjectedCourse(title, 3.0, "A")` — 3.0 credits hardcoded (:47-48 area).

### M14. GradesScreen semester label parsing
`GradesScreen.kt:136` — `id.substring(4,6)` assumes `"CH20YY27SS"` shape; mislabels any non-CH id. `:303-304` — highest/lowest matched by `grandTotal` equality → duplicate totals shown twice as "Highest".

### M15. CircularsScreen expand state keyed by title
`CircularsScreen.kt:105-119` — `folderName in expandedFolders`; two folders with the same title toggle together. Also item rows (:182-217) are not clickable — no preview/open/download despite ids.

### M16. ExamScheduleScreen duplicate-key risk
`ExamScheduleScreen.kt:129` — `key = "${courseCode}-${examDate}-${slot}"`; two exams sharing code+date+slot → Compose duplicate-key error.

### M17. TasksScreen quirks
- `TasksScreen.kt:1073` — finishing a 0-min pomodoro session logs 1 minute.
- `TasksScreen.kt:596-598` — kanban split on `actualMinutesSpent == 0` → To Do; a partially-tracked task never leaves To Do.
- `TasksScreen.kt:1082-1109` — `AddTaskDialog` delegates to a bottom sheet (dialog-over-sheet layering).

### M18. ODTracker mislabeled defaults
`ODTrackerScreen.kt:64` — `validHours = lab+theory-wasted+recovered`; auto-detected ODs seeded as `"wasted"` (:202), flipped only by manual toggle → default state mislabels recovered hours.

### M19. Dashboard dead wiring
- `DashboardScreen.kt:63-64` — `commandPaletteTrigger` only ever set `false`.
- `DashboardScreen.kt:14` — `showAddTaskDialog` never set true.
- `DashboardScreen.kt:44,55` — `UpdateResultDialog` gated on `showManualUpdateResult`, never set true → "check update" result unreachable.
- `DashboardWidgets.kt:990-1012, 1201-1207` — `while(true) delay(60_000)` infinite clocks, duplicate logic in CurrentNextClass vs TodayClasses.

### M20. App.kt bug-report says "Platform: Android" on all platforms
`App.kt:311` — hardcoded string in a KMP app.

### M21. `_error` dialog is all-or-nothing
`AppState.kt:375-386` — any module error → full-screen error dialog; no per-module dismissal; `e.toString()` user-facing (`App.kt:284` leaks ktor internals).

### M22. Sync progress popup re-shows after dismiss
`SyncProgressPopup.kt:66` — `userDismissed = remember(isSyncing) { false }` — resets whenever `isSyncing` toggles; the log list keys `"${timestamp}-${module}-${message}"` (`:546-547`) can collide for identical consecutive entries → Compose key crash; `onSaveOffline` param (:50) passed but never read.

### M23. `SyncSettingsDialog` new-profile name
`SyncSettingsDialog.kt:157` — `"Custom Config ${profiles.size - 2}"` assumes exactly 2 built-in profiles.

### M24. Version drift + stale fallback
- Root `version.properties` = 2.0.0/16; committed `shared/src/commonMain/composeResources/files/version.properties` = 2.0.2/18 (build task overwrites it, but IDE-cached builds can report the wrong version).
- `UpdateConfig.kt:23,27` — fallback `"1.9.2"` (phantom update prompts).
- iOS Info.plist `CFBundleShortVersionString=1.0.0` vs Android 2.0.x.
- `compareVersions` (`AppState.kt:188-199`) strips prerelease suffixes → dismissing `v2.1.0-beta` blocks stable `2.1.0`.

### M25. Back stack issues
- `App.kt:158` — every tab press calls `navigateTo` → previous screen pushed; back walks whole tab history.
- `AppState.kt:753-773` — `switchTopLevel` clears the stack (FfcsPlannerScreen.kt:515) → back does nothing.
- On empty stack at non-root tab, back is consumed — app can't exit from tab screens.
- Widget deep links (`MainActivity.kt:29-35`) create a new activity each time → stacked duplicates, each pushing SPLASH.

### M26. `MoodleLoginModal`/`PushPromptModal` iOS gaps
- `NotificationPermissionManager.kt:9` + `LocalNotificationPermissionManager` provided only in MainActivity (Android). On iOS `current` is null → push prompt silently no-ops (`MoreScreen.kt:64`, `OnboardingScreen.kt:646`).

### M27. EventHubRepository dedup bug
`EventHubRepository.kt:27` — merge branch requires *both* eligibilities non-empty; `contains` is substring ("Technical" ⊆ "Technical Fest"). (Repo is dead, but the bug is representative.)

### M28. `getCabHubs` returns hardcoded list; duplicated in two files
`AmazeClient.kt:39-54` + `AppState.kt:235-250` — same 14 hubs, two copies to keep in sync.

### M29. `getCalendars` fabricates data
`AmazeClient.kt:580-590` — "calendars list" is synthesized from `getCalendar`; `CACHE_CALENDARS_LIST` persists synthetic data.

### M30. Canvas of hardcoded dates/semesters
- `TransportScreen.kt:351-358` — semesters end "Summer 2026".
- `CalendarScreen.kt:81` — `?: 2026` year fallback.
- `CourseDetailScreen.kt:129` / `CourseDashboard.kt:517` — `?: "CH20262701"` semesterId fallback.
- `AppState.kt:252-262` — `semesterMap` hardcoded 8 semesters ending Winter 2026-27; `_selectedSemester` default `"CH20262701"`.
- `IcsUtils.kt:16` — year fallback `2026` (dead file, but representative).

---

## LOW

1. `AmazeClient.kt:90-94` — no `HttpTimeout`, no `expectSuccess`, no retry, no cookie jar → requests can hang indefinitely.
2. `AmazeClient.kt:973` — query string inside the path argument of `postAuthorized` (works, fragile).
3. `AmazeClient.kt:640-643` — wallet balance = `walletLedger[0]` (assumes first entry is the balance).
4. `AmazeClient.kt:1174-1234` — event endpoints return `null` on any failure; callers can't distinguish auth failure from server down.
5. `AmazeClient.kt:519-521, 1110-1111` — `error = e.toString()` user-facing.
6. `AppState.kt:2104-2113` — `requestJoinTrip` never invokes `onResult` on exception → UI callbacks silently never fire.
7. `AppState.kt:2146-2161` — join-request cache eviction `current.keys.first()` = arbitrary, not oldest.
8. `AppState.kt:1252` — `updateModuleStatesFromCache()` re-stamps all cached modules SUCCESS at end of every sync, even ones that just failed.
9. `AppState.kt:375-386` — global error dialog from any module.
10. `AppState.kt:311-323` — `allSemesterExams` loops 7 sequential HTTP calls per sync; per-iteration failures swallowed.
11. `AppState.kt:2531` — `setPinnedNavTabs` persists enum *names*; renaming a Screen silently drops user config.
12. `AppState.kt:2685-2690` — header flows are public `MutableStateFlow`.
13. `AppState.kt:433-438` — `loadFromCache()` fires network syncs (Moodle, Library) on cold start.
14. `AppState.kt:875` — session-refresh failure swallowed → every module reports "Empty response".
15. `WidgetDataUtils.kt:133` — inclusive `endMins` boundary vs `AttendanceTimetable:262` exclusive `until` — mismatch at exact end time.
16. `AttendanceTimetable.kt:66` — `h < 8 → h += 12`: "8:00 PM" would decode as AM (no such slots today).
17. `AttendanceTimetable.kt:90-107` — holiday titled "Monday Eid" yields a MON day-override.
18. `AttendanceTimetable.kt:211-235` — merges classes with gap 0 (simultaneous labs); slotName grows unbounded `A1+F1+D1…`.
19. `SocialUtils.kt:100-151` — `|`-delimiter share codes: a `|` in name/venue shifts every field; slots silently dropped; `addedAt = "Now"`.
20. `SocialUtils.kt:172-176` — lab-overlap: user with lab L1 (8:00-8:50) is reported FREE for theory A1 (same time).
21. `QRCodeGenerator.kt:10,38-39` — terminator bits written twice (harmless over-fill).
22. `LatexViewer` (android:12-34, ios:14-59) — raw `$latex` interpolated into HTML (XSS vector); MathJax loaded from CDN on every recompose; hardcoded `#E0E0E0` text color ignores theme.
23. `ScreenHeader.kt:126-133` — `rememberInfiniteTransition` 0→360° rotation runs **unconditionally** (even when not syncing), forever, for the header's lifetime.
24. `ScreenHeader.kt:286-295` — `DynamicIslandLiveClass` infinite pulse 800ms while any live-class card exists.
25. `ChangelogModal.kt:53,76,101` — hardcoded `Color(0xFF3B82F6)`/`0xFF10B981`.
26. `MoodleLoginModal.kt:57` — `Color.Red`.
27. `PushPromptModal.kt:48,54` — hardcoded blue.
28. `Components.kt:75` — AmazeButton primary dispatch via exact color equality `colors.accent == Color(0xFF0EA5E9) || == Color(0xFF8B5CF6)`.
29. `FreeClassroomsWidgetProvider.kt` — catch resets only `widget_pct_value`; CGPA/credits/status badge keep stale values.
30. Widgets share one PendingIntent identity (`requestCode=0`, FLAG_UPDATE_CURRENT) — fragile.
31. `proguard-rules.pro` — only template comments; R8 release has zero keep rules for kotlinx.serialization models used by widgets.
32. `Manifest:14` — `usesCleartextTraffic="true"` app-wide.
33. `Manifest:8-9` — both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` declared (contradictory; Play policy risk on targetSdk 35).
34. `MainActivity.kt:34` — empty catch swallowing widget deep-link failures.
35. `AndroidNfcManager.kt:53-68` — no try/finally around `ndef.connect()`; no MIME check; US-ASCII decode ignores NDEF headers; `isListening` written never read; `stopListening` never called (reader mode stays active for activity lifetime).
36. `AlarmReceiver.kt:36-45` — deprecated `ic_dialog_info` small icon (API 33+ renders blank on some OEMs).
37. `NotificationsUtils.android.kt:15-29` — "permission request" opens the system settings screen from a background coroutine mid-sync.
38. `FriendsViewModel.kt:25-26` — raw cache key strings not in SettingsManager; sync disk I/O on first composition.
39. `SettingsManager` — `getString` never returns null; `getNullableString` conflates empty with missing; `course_note_*` unbounded keys.
40. `ExamScheduleScreen.kt:74` — semester label `full.take(4).takeLast(2)` assumes 4-digit year.
41. `CourseDetailScreen.kt:944-949` — grade-boundary thresholds hardcoded in UI text ("S≥90…E≥40"), duplicated with `CourseDetailScreen.kt:92-95` + `GPAPredictorScreen.kt:490-498`.
42. `CourseDetailScreen.kt:1188` — predictor chips hardcode `["CAT1","CAT2","LID"]` (no CAT3/FAT).
43. `Theme.kt:318-350` — AMOLED branch inlines 7 raw hexes not in Color.kt; `NavBorderDark` near-twin `#181824` vs AMOLED `#1A1A1A`.
44. `Type.kt` — `outfit_black.ttf`/`geist_black.ttf` bundled, never referenced.
45. `AppState.kt:68` — singleton scope `Dispatchers.Default` for network+JSON work (works, but blocks CPU pool).
46. `FfcsViewModel.kt:209` — lock/block values read inside the coroutine at execution time, not snapshot at click (mid-generation toggle mixes states).
47. `FfcsViewModel.kt:38-39` — `initFromParsedCourses` would leave `isLoading=true` forever.
48. `FfcsViewModel.kt:199` — `selected` is a `Set`; iteration order not guaranteed → course→color assignment varies between generations.
49. `FfcsEngine.kt:163` — sort `halfDays*10 + (20-gaps)*5`: magic cap 20 → gaps >20 yields negative term; incompatible scales.
50. `FfcsEngine.kt:121-123` — "before 2 PM" check tests start (not end) time — label/behavior mismatch.
51. `FfcsEngine.kt:15-20` + `FfcsModels.kt:109-114` — identical 18-color list defined twice.
52. `FfcsModels.kt` — `DAYS`, `TYPE_LABELS`/`getTypeLabel`, `ParsedCourse.batch/originalCode/linkId`, `TimetableState.variants`, `TimetableMetrics.bestFriendMatches`, `CourseLock.offerings` (feeds nothing) — all dead.
53. `FfcsCourseSelector.kt:156-158` — blocked-dot check compares time-string keys against slot codes → always false.
54. `AnalyzeCalendar.kt:127-130` — latent `DateTimeException` if `totalDays` > days-in-month.
55. `AmazeTests.kt:47-57` — references `Screen.DASHBOARD`, which doesn't exist → commonTest broken.
56. `MainTabPager.kt:51` — pager state not keyed to tab-list changes (indices drift after pin changes).
57. `MainActivity.kt:27` — `AndroidNfcManager(this)` per onCreate, never disabled onDestroy.
58. `NotificationService` (all 3) — expect/actual stub; only `NotificationsUtils` works.

---

## Stats

- CRITICAL: 4 (C1-C4)
- HIGH: 20 (H1-H20)
- MEDIUM: 30 (M1-M30)
- LOW: 58 (L1-L58)
- **Total: 112 distinct issues** (many with sub-issues).
