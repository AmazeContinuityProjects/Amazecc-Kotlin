# Evaluation 04 — Connectivity: Everything That's Connected

The full data-flow map of the app: API endpoints ↔ models ↔ AppState flows ↔ screens, plus dead connections, cross-wired systems, and the sync engine.

---

## 1. End-to-end flow inventory

```
AmazeClient (55+ endpoints, ~70 mock-gated)
  └── AppState (singleton: ~50 StateFlows, loadAllData/refreshX, tasks, cab)
        └── Screens (collectAsState)
              └── App.kt router (Screen enum, 44 values, all covered in `when`)
```

**Verified: the `Screen` enum (`AppState.kt:57-64`, 44 members) is fully in sync with `App.kt:161-206`.** No orphan screens, no missing branches, no references to non-existent screens (except the test file — `AmazeTests.kt:47-57` uses `Screen.DASHBOARD` which doesn't exist).

## 2. Live API endpoints → who consumes them

| Endpoint (path after `$baseUrl/api/`) | Function @ AmazeClient | Consumed by |
|---|---|---|
| `attendance` :275 | `getAttendance` | AppState loadAllData ✓ |
| `timetable` :310 | `getTimetable` | AppState ✓ |
| `marks` :342 | `getMarks` | AppState ✓ |
| `all-grades` :371 | `getAllGrades` | AppState ✓ |
| `lms-data` :379 / `fetchMoodleData` | | AppState ✓ (auto-syncs on cold start if creds :433-438) |
| `hostel` :417 | `getHostelDetails` | AppState ✓ |
| `schedule` :448 | `getExamSchedule` | AppState ✓ |
| `curriculum` :461 | `getCurriculum` | AppState ✓ (mock: empty success) |
| `calendar` :495 | `getCalendar` | AppState ✓ (real) |
| `getCalendars` :525 | synthesized from getCalendar :580-590 | AppState ✓ (synthetic data cached) |
| `payments`/`payment-receipts`/`wallet` :604-606 | `getPayments` | AppState ✓ |
| `library-due` :676 | `getLibrary` | AppState ✓ |
| `koha/search` :693 / `koha/renew` :707 | | LibrariesScreen ✓ |
| `transport` :735 | `getTransportData` | AppState ✓ |
| `buses` :755 | `getBuses` | AppState ✓ |
| `transport/register` :777 | `submitTransportRegistration` | TransportScreen ✓ |
| `cab/*` (search/create/my-trips/join/requests/accept/reject) :799-888 | | AppState ✓ (legacy cab path — replaced by cabshare? both live) |
| `cabshare/auth` :912, `cabshare/trips` :933/:961, `cabshare/trips/me` :973, `cabshare/match` :987/:1001 | | AppState ✓ |
| `lms-data` :1058 (dup) | `getLMSAssignments` | AppState ✓ |
| `qbank/questions` :1080 / `qbank/papers` :1103 | | QBankScreen ✓ |
| `qbank/upload` **:1507 — broken (double /api/)** | `postQBankPaper` | CourseDetailScreen.kt:1472 — **upload is broken in production** |
| `qbank/courses` :1525 | `getQBankCourses` | QBankScreen ✓ |
| `qcm-view` :1135 | `getQcmView` | CourseDetailScreen ✓ |
| `events` :1152 | `getEvents` | EventHubScreen ✓ |
| `events/preview` :1178 | `getEventPreview` | EventHubScreen ✓ (sends plaintext creds) |
| `events/register` :1201 | `registerForEvent` | EventHubScreen ✓ (plaintext creds) |
| `events/login` :1223 | `eventLogin` | EventHubScreen ✓ (plaintext creds) |
| `events/profile` :1268 | `getEventsProfile` | AppState ✓ |
| `clubs/details` :1291 | `getClubsDetails` | EventHubScreen ✓ |
| `student` :1359 | `getStudentProfile` | AppState ✓ |
| `profile-images` :1370 | `getProfileImages` | AppState ✓ (empty mock in demo) |
| `ept-schedule` :1379, `registration-schedule` :1386, `bank-info` :1393, `dayboarder` :1400, `apaarid` :1407 | | AppState ✓ (all empty fakes in demo) |
| `circulars` :1497 | `getCirculars` | AppState ✓ |
| `faculty/schools` :1547, `faculty/scrape` :1570 | | FacultyInfoScreen ✓ |
| `curriculum/syllabus` :1833 | `getSyllabusPdf` | CourseDetailScreen.kt:1253, CurriculumScreen.kt:377 ✓ (iOS save no-op) |
| `club-admin/feed` :1790 | `getClubFeed` | ClubHubScreen ✓ |
| `club-admin/feed/promote` :1807 | `promoteFeedPost` | **dead — zero callers** |
| `course-option-change`/`exc-registration`/`minor-honour`/`course-completion` :1663-1703 | | CourseManagementScreen ✓ |
| `project` :1715 | | ProjectsScreen ✓ |
| `wishlist` :1727 | | WishlistScreen ✓ |
| `feedback-status` :1744 | | FeedbackStatusScreen ✓ |
| `bonafide` :1759, `e-transcript` :1773, `additional-learning` :1785 | | DocumentsScreen ✓ |
| `makeup-exam` :1431, `makeup-schedule` :1449, `compre-info` :1474 | | **dead functions — zero callers** |
| `getFFCSReport` :1817 (`https://amazecc.como/…` — typo domain) | | **dead** |
| `getVtopStudentPhoto` :1324 | | **dead** (AppState.vtopPhotoBase64 never produced) |
| `checkForUpdate` :1854 (GitHub releases/latest) | | DashboardScreen ✓ |

## 3. Models consumed vs dead (see 05-dead-code for full list)

- **~55 endpoints, of which ~10 are dead** (no callers): `getMakeupExam`, `getMakeupSchedule`, `getCompreInfo`, `getVtopStudentPhoto`, `promoteFeedPost`, `getFFCSReport`, `getAttendance` (wrapper), `getLocalCabTrips`, plus the whole legacy `cab/*` legacy-vs-cabshare split (both live — two parallel cab APIs in the same app).
- **18 dead model classes, ~35 dead fields** — see `05-dead-code.md` §2.
- **4 screens reuse `ArrearResponse`** for projects/wishlist/documents/course-management (API side mirrors: AmazeClient.kt:1706,1718,1750,1762,1776) — one generic table model serving four distinct features; `ApiTable.title` + `KeyValuePair` render everything.

## 4. Cross-wired / conflicted systems

| Conflict | Details |
|---|---|
| Legacy `cab/*` + new `cabshare/*` | Two cab-share API families both live: `AppState.cabCreateTrip` (:2220 legacy?) vs `cabCreateTripNew`; UI only uses `New` variants. Legacy endpoints are fake-success-wrapped too. |
| `SessionManager.clubToken` slot collision | VTOP login and EventHub login write the same slot; `getImageBytes` sends `JSESSIONID=$token` — the wrong cookie goes to eventhubcc.vit.ac.in depending on which login happened last (see bugs H16) |
| Two search systems | `CommandPalette` (live: Ctrl/Cmd+K, header icon, dashboard) vs Spotlight `setSearchOpen` (dead) |
| Two cab hub lists | `AmazeClient.fallbackHubs` (:39-54) + `AppState.fallbackHubs` (:235-250) — must be kept in sync manually |
| Theme in two places | `AppState._theme/_accent` (live) + `SessionManager.currentTheme/currentAccent` (dead duplicate) |
| Notifications in two surfaces | `SettingsScreen.kt:517-537` toggles request notification permission for non-notification settings (Hide CGPA, sync modules) + `MoreScreen.kt:185-248` has the real reminder settings — duplicated settings surfaces |
| Widgets vs app data | Widgets read `SettingsManager` caches only; app writes them but **never notifies** the widget providers (see bugs H13) |
| FFCS "Save Timetable" | `FfcsPlannerScreen.kt:481-521` — generator output **overwrites the live attendance store** (`updateAttendance(AttendanceRes(attendance = items))`) with fake `AttendanceItem`s (courseCode/title/type/slotName/faculty only) — saved planner data pollutes the real attendance source |
| `syncExam`/`syncAdditional`/`syncArrear` toggles | Persisted/displayed but gate nothing; arrear toggle not even restored (see bugs M3) |
| `getCalendars` | Fabricates a calendar list from `getCalendar`; `CACHE_CALENDARS_LIST` holds synthetic data |
| Past-semester sync | Once `_pastSemestersSynced` set, never re-runs but reported successful forever (`AppState.kt:907-908,1377-1378`) |

## 5. Sync engine wiring

**Module registry:** 27 `SyncModule`s in `SyncEngine.kt`.

**What's actually connected:** `AppState.loadAllData()` (AppState.kt:600-1260) drives everything manually — `markAllLoading()` → per-module `withContext` fetch + `cacheData` + `updateModuleState` → `updateModuleStatesFromCache()` → `scheduleReminders()` in `finally`. The App-level popup (`SyncProgressPopup`), header spinner, and error dialog consume `_moduleStates`.

**What's dead:** `SyncEngine.startSync` / `startSyncGroup` / `startSyncAll` / `cancelSync(module)` / `logSaveOffline` / `markSessionRefreshed` / `markSyncButtonTapped` / `lastSyncTime` / `lastSessionRefresh` / `lastSyncButtonTap` / `isModuleEnabled` — the entire *execution* layer. `AppState` hand-rolls its own sync instead of using the engine API that was built for it.

**Structural bugs:**
- `activeJobs`/`syncSessionModules` unsynchronized across coroutines (:148-149)
- `_moduleStates`/`logLines` read-modify-write races (:285-288, :317-319)
- `CancellationException` → marked ERROR (:338-341)
- `resetAllStates` mid-flight (App.kt:111) corrupts progress
- 7 sequential HTTP calls for all-semester exams per sync (AppState.kt:999-1013, :1854-1864), failures swallowed per-iteration
- `_error` aggregates every module error into one full-screen dialog; no per-module dismissal; error text = `e.toString()` (App.kt:284)

## 6. What connects to what on platform

| Piece | Connected? |
|---|---|
| 5 widgets ↔ manifest | ✅ all declared (AndroidManifest.xml:36-94), RemoteViews IDs match layouts |
| Widgets ↔ live data | ❌ no push-update on sync (15-min system refresh only) |
| NFC (AndroidNfcManager) ↔ UI | ❌ zero callers; no lifecycle wiring in MainActivity; iOS has no actual |
| Alarms ↔ reboot | ❌ no BOOT_COMPLETED receiver — reminders lost on reboot |
| Alarms ↔ cancel | ❌ `clearPendingNotifications` cancels requestCode 0; real ids 1000+ |
| Notifications ↔ channels | ❌ all land in CHANNEL_CLASSES; assignments/tasks channels unused |
| NotificationPermissionManager | ⚠️ Android-only provider; iOS null → push prompt no-op |
| LatexViewer | ⚠️ real, but CDN-dependent + XSS-prone interpolation on both platforms |
| Widget deep links | ⚠️ work but stack duplicate activities, each pushing SPLASH |
| iOS version | ❌ Info.plist 1.0.0 vs Android 2.0.x — update checker diverges per platform |

## 7. State flows: produced vs consumed (verified)

**Consumed but never produced:** `vtopPhotoBase64` (ProfileScreen.kt:71,98, DashboardWidgets.kt:616,629,634) — always null/stale.

**Produced but never consumed (dead):**
- `showSearch` / `setSearchOpen`
- `SyncEngine` execution API + `lastSyncTime` etc.
- `FfcsViewModel.allCourses` / `uniqueFaculty` / `morningPreference` / `maxResults` flows (screen keeps local state, pushes via setters)
- `SessionManager.currentTheme/currentAccent`
- `AppState.removeCache` (@Suppress("unused"))
- `TimetableState.variants`, `TimetableMetrics.bestFriendMatches` (FFCS)

**Single-caller functions (inline candidates):** `restoreSession` (LoginScreen:75), `syncEventsAndClubs` (EventHubScreen:52,63), `todayTasks` (DailyPlanner:351), `checkForUpdate` (DashboardScreen:19,42,53), `eventLogin` (EventHubScreen:63), `getFacultySchools`/`postFacultyScrape`/`getFacultyProfile` (FacultyInfoScreen), `getSyllabusPdf` (CourseDetailScreen:1259 + CurriculumScreen:377), `postQBankPaper` (CourseDetailScreen:1472).

## 8. The three dead repositories

`BusRepository` (23 ln), `EventHubRepository` (87 ln), `QBankRepository` (77 ln) — **zero consumers** in the entire codebase. `AmazeClient` + `AppState` absorbed their roles. They duplicate endpoint logic (`EventHubRepository` re-hardcodes `https://api.amazecc.com`, has its own dedup bug) and their existence signals the missing repository layer (see `06-modularization.md`).

## 9. What "connected" means for the cleanup

The connectivity story in one sentence: **every screen reaches directly into the `AppState` singleton for data and into `AmazeClient` for actions, and nothing else does — which is why repositories, the sync engine's execution layer, and dozens of helpers could die without any UI noticing.** The fix direction (hooks pattern) is in `06-modularization.md`.

## Phase 0 Fix Log (2026-08-06)

- `postQBankPaper` path fixed (`/api/qbank/upload` → `qbank/upload`): the `postAuthorized` helper already prepends `/api/`, so uploads were POSTed to `https://api.amazecc.com/api//api/qbank/upload` and 404'd. QBank uploads now reach `https://api.amazecc.com/api/qbank/upload`.
- `HttpTimeout` installed on the shared client (request 30s / connect 15s / socket 30s) so dead sockets fail fast instead of hanging coroutines forever. `expectSuccess` deliberately not enabled — the client's endpoints check `response.status` manually and return error payloads on non-200.
