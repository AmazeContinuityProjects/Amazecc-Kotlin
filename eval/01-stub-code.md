# Evaluation 01 — Stub Code

Everything in the codebase that is a stub, placeholder, mock, fake-success, no-op, or "coming soon". **Line numbers verified in source.**

---

## 1. The mock-data system (biggest stub surface)

`AmazeClient.kt` (1,889 lines) gates ~70 endpoints behind `useMockData || SessionManager.authorizedID.value == "DEMO123"` (`:97`, `:168` … `:1777`). Two toggles:
- `setUseMockData(true/false)` — set from `LoginScreen.kt:330-332` (a "Demo Mode" toggle UI) and `LoginScreen.kt:376` (the "Explore in Demo Mode" button).
- The `DEMO123` special-case is **hardcoded in production** (see `07-security.md`).

### Mock boundary map (function — mock line — what the fake returns)

| Function | Mock @ | Fake payload |
|---|---|---|
| `login` | :97 | `success=true`, fake cookies `"vtop_session_cookie=demo_session_123; csrf_token=demo_csrf_abc"`, authorizedID `DEMO123` |
| `getAcademicData` | :168-272 | ~28 dates × 4 fake courses attendance + `getMarks` fake |
| `getTimetable` | :297 | 3 fake courses |
| `getMarks` | :317 | Fake assessments, CGPA `("120","84","8.54","Completed")` |
| `getAllGrades` | :349 | 2 fake semesters |
| `getHostelDetails` | :400 | Fake room + leaves |
| `getExamSchedule` | :424 | Fake; label map only covers 6 semesters vs AppState's 8 |
| `getCurriculum` | :455-457 | **STUB**: `return CurriculumRes(success = true, title = "Curriculum Overview") // return empty mock for now` |
| `getCalendar` | :468 | Fake months; `type` param pretends "extra months" |
| `getCalendars` | :525 | 4 fake named calendars |
| `getPayments` | :593 | Fake bills; **synthesizes a fake `"due-pending"` UNPAID row** when `hasDues == true` (:610-621) |
| `getLibrary` | :657 | 2 fake books |
| `searchLibrary` | :683 | 2 fake books |
| `renewLibraryBook` | :701 | `BasicRes(success = true, message = "Book renewed successfully.")` — **fake success** |
| `getTransportData` | :721 | Fake registration |
| `getBuses` | :742 | 1 fake route |
| `submitTransportRegistration` | :767 | Fake `REG-<random>` |
| `searchCabTrips` | :786 | 4 fake trips with **mojibake fares** `"â‚¹250"` (broken ₹ encoding) |
| `createCabTrip` | :806 | Fake success |
| `getMyCabTrips` | :824 | Fake own trips |
| `requestJoinTrip` | :841 | Fake success |
| `getCabJoinRequests` | :853 | 2 fake requests |
| `acceptCabJoinRequest` / `rejectCabJoinRequest` | :871 / :883 | Fake success |
| `cabShareAuth` | :895 | Fake user |
| `searchCabShareTrips` | :924 | `success=true, trips = emptyList()` |
| `createCabShareTrip` | :944 | Fake success |
| `getMyCabShareTrips` | :969 | `CabShareTripsRes(success = true)` — **empty fake** |
| `getLMSAssignments` | :1048 | 2 fake assignments |
| `getQBankQuestions` | :1064 | 1 fake question |
| `getQBankPapers` | :1092 | Mock gated on **`useMockData` only** — inconsistent (DEMO123 users hit the real API here) |
| `getQcmView` | :1115 | Fake QCM rows |
| `getEvents` | :1142 | 2 fake events |
| `getEventsProfile` | :1244 | 1 fake registered event |
| `getClubsDetails` | :1276 | 1 fake club |
| `getStudentProfile` | :1341 | Fake student "Alexander Pierce" / "23BCE1234" |
| `getProfileImages` | :1366 | `ProfileImagesRes(success = true)` — **success with no data** |
| `getEptSchedule` | :1377 | `EptScheduleRes(success = true)` — **empty fake** |
| `getRegistrationSchedule` | :1384 | empty fake |
| `getBankInfo` | :1391 | empty fake |
| `getDayboarderInfo` | :1398 | empty fake |
| `getApaarId` | :1405 | empty fake |
| `getMakeupExam` / `getMakeupSchedule` / `getCompreInfo` | :1412 / :1435 / :1453 | Rich fakes — **all three functions are dead (no callers)** |
| `getCirculars` | :1478 | 3 fake groups |
| `postQBankPaper` | :1504 | Fake success + **double-`/api/` 404 bug** (`:1507`) |
| `getQBankCourses` | :1516 | 5 fake courses |
| `getFacultySchools` | :1529 | 13 fake schools |
| `postFacultyScrape` | :1559 | Fake SCOPE roster |
| `searchFacultyDirectory` | :1630 | `return null` (mock = nothing found) |
| `getCourseOptionChange` … `getAdditionalLearning` (10 endpoints) | :1655-1786 | All rich fakes |

**Endpoints with NO mock branch** (real network only): `fetchMoodleData` :377, `requestCabShareJoin` :980, `cabShareMatchAction` :994, `getImageBytes` :1308, `getVtopStudentPhoto` :1324, `getClubFeed` :1788, `promoteFeedPost` :1801, `getFFCSReport` :1817, `getSyllabusPdf` :1828, `checkForUpdate` :1854, plus `getCabHubs` :919 — which isn't a stub but returns a **hardcoded hub list** ("fetch" is fake end-to-end; the same 14 hubs are duplicated in `AppState.kt:235-250`).

### Consequence
The mock shapes are the only "backend contract" evidence. Real-backend field mismatches are untestable; several "live" features (curriculum, bank info, dayboarder, APAAR, EPT, registration schedule, profile images) have **never run against the real API**.

---

## 2. Fake-success paths (worst kind of stub — lies to the user)

| Where | What |
|---|---|
| `AppState.kt:2175-2186` (`cabShareLogin`) | Any exception → `fallbackUser(local_only=true)` + `onResult(true, "Offline mode - saved locally")`. **Wrong password = "authenticated".** |
| `AppState.kt:2236-2246` (`cabCreateTripNew`) | Server failure → silently writes local trip, `onResult(true, "Trip saved locally!")`. User believes the trip is public. |
| `AppState.kt:2271-2284` (`cabRequestJoinNew`) | `catch { onResult(true, "Request saved locally!") }` |
| `AppState.kt:2286-2299` (`cabHandleMatchAction`) | `catch { onResult(true, "Updated locally!") }` |
| `AppState.kt:1104-1117` | "Registered Events" module reports `success=true` when there's no club token — for a module that never ran. |
| `AppState.kt:1192-1193` | Profile group reports `success=true` for skipped/disabled work. |
| `AppState.kt:907-908, 1377-1378` | Once `_pastSemestersSynced` is set, past-semester sync never re-runs but is reported as freshly successful every sync. |
| `AppState.kt:1257-1258` | `finally` always shows "Sync completed" notification even when modules failed. |
| `AmazeClient.kt:610-621` | `getPayments` fabricates a fake `PaymentItem("due-pending", …, "UNPAID")` UI fiction when `hasDues == true`. |
| `AppState.kt:2586-2587` | `vtopPhotoBase64` initialized once from cache, **never written** — consumed by ProfileScreen/DashboardWidgets, can never have data. |
| `AppState.kt:491` | `saveOffline()`: `if (_libraryLoginRequired.value) { /* skip — no data to cache */ }` |

## 3. No-op / stub platform services

| Where | What |
|---|---|
| `NotificationService.kt` (commonMain:3-5, androidMain:5-10, iosMain:5-10) | expect/actual is a **Log/NSLog stub** — "Push Notification" posts nothing. Real path is `NotificationsUtils`. |
| `AndroidNfcManager.kt:17-32` | `startSharing()` builds an NdefMessage then only logs — "setNdefPushMessage is removed in API 34". **No-op.** |
| `AndroidNfcManager.kt:34-41` | `stopSharing()` no-op log. |
| `FileSaver.ios.kt:6` | `actual fun rememberFileSaver() = { _, _ -> false }` — **iOS save always fails silently**. |
| `FileSaver.android.kt:21-31` | Returns `true` when `openOutputStream` is null; API<29 "saves" to `cacheDir` (invisible, evicted) and returns `true`. |
| `ErrorUtils.kt:37,58` | `// Could integrate with a toast/notification system here` — stub branch, whole object dead. |
| `MarksSync.kt` (whole file) | Leftover scaffolding of a deleted object: dangling `expect hashStringSha256` + `// Assuming Settings or similar Key-Value store interface is provided` + unused imports. **No iOS actual → breaks iOS compile.** |
| `NotificationsUtils.ios.kt:53-56` | `createNotificationChannels` comment-only stub (fine for iOS, but dead surface). |

## 4. Empty/stub screens

| File | Content |
|---|---|
| `AcademicsHub.kt` | 3 lines: "This file is kept for backward compatibility" — nothing left. |
| `VitolScreen.kt` | 1 line: "This file is obsolete and its contents have been removed." |
| `MarksTimelineScreen.kt` | 1 line: same obsolete marker. |
| `MakeupCompreScreen.kt` | 1 line: same obsolete marker. |

All four still compiled into the tree; BUGS.md M9 even claims a fix in `VitolScreen.kt` — a deleted file.

## 5. Dead buttons (visible UI that does nothing)

| Where | Button | Handler |
|---|---|---|
| `TransportScreen.kt:317` | "Track Bus" | `onClick = { /* TODO: Implement tracking or open VTOP */ }` |
| `PaymentsScreen.kt:95` | "Top Up" | `onClick = { /* TODO */ }` |
| `PaymentsScreen.kt:105` | "History" | `onClick = { /* TODO */ }` |
| `PaymentsScreen.kt:236` | "Receipt" | `onClick = { /* TODO: download PDF */ }` |
| `PaymentsScreen.kt:238` | "Pay Now" | `onClick = { /* TODO: Pay */ }` |
| `HostelScreen.kt:421` | "Request Counselling" | `onClick = { /* Handle Click */ }` |
| `EventHubScreen.kt:758` | RegisteredButton | `onClick = {}` + `enabled = false` (harmless, still a stub) |
| `ClubDetailScreen.kt:168` | Enroll toggle | `isEnrolled = !isEnrolled` — **local-only**, no API, resets on nav |
| `PaymentsScreen.kt:159-161` | "wallet" sub-tab | **Unreachable**: only `"due"`/`"receipts"` chips rendered (`:129`); the "Wallet ledger coming soon" empty state can never show |

## 6. "Coming soon" / placeholder content shipped to production

| Where | Content |
|---|---|
| `HallOfFameScreen.kt:28-33` | **Fake contributor names**: "Jane Doe", "John Smith", "Alice Johnson", "Bob Williams" |
| `HostelScreen.kt:216-229` | Hardcoded mess menu (Idli/Dosa, "Steamed Rice"…) |
| `HostelScreen.kt:323-340` | Laundry slot data only for A/B blocks — C/D blocks show empty |
| `HostelScreen.kt:408-414` | **Fake advisor**: "Dr. Rajesh Kumar, Professor, CSE Department", rajesh.kumar@vit.ac.in |
| `ClubDetailScreen.kt:147,161` | Hardcoded blurb + literal "Feed Integration Coming Soon" |
| `AboutScreen.kt:31-37` / `ChangelogScreen.kt:28-34` | Identical hardcoded phase lists, no dates |
| `TransportScreen.kt:350-359` | Hardcoded semester list ending "Summer 2026" — stale every term |
| `SocialScreen.kt:324-329` | Hardcoded `standardSlots` timetable grid |
| `LibrariesScreen.kt` palettes | `LibraryPalette.kt:13-14`: "In the future, this can query a Koha catalog API… For now, it provides a placeholder search experience" |
| `ReelScroller.kt:20-24` | "this acts as a placeholder visual representation of the Reels component" — dead |
| `EventPalette.kt:17-20` / `LibraryPalette.kt:17-20` | `onSelect = { /* Trigger deep search */ }` — empty handler; palettes untriggerable anyway |
| `FfcsPlannerAlgorithm.kt:77` | `socialScore = 0, // Mock metrics for now` (whole file dead) |
| `FfcsMetrics.kt:165` | `socialScore = 0` — mock metric, never computed (this one is live code) |
| `WidgetDataUtils.kt:202` | `getFreeClassroomsSample` — hardcoded "sample" room list behind a real widget |
| `UpdateConfig.kt:23,27` | Hardcoded fallback version `"1.9.2"` — phantom "update available" if resource missing |
| `AmazeClient.kt:456` | `// return empty mock for now` (curriculum) |
| `FriendsViewModel.kt:96` | `createdAt = "Now"` — literal string timestamp for every group |

## 7. Placeholder state flows wired to nothing

- `AppState.showSearch` / `setSearchOpen` (`AppState.kt:87-89`) — **zero callers**; the Spotlight Search feature (IMPLEMENTATION_PLAN §3.15) is wired to nothing; App.kt:66 collects it and never uses the value.
- `AppState.vtopPhotoBase64` — consumed by ProfileScreen + DashboardWidgets, **produced by nothing**.
- `SyncEngine` execution API (`startSync`, `startSyncGroup`, `startSyncAll`, `cancelSync(module)`, `logSaveOffline`, `markSessionRefreshed`, `markSyncButtonTapped`, `lastSyncTime`) — **zero callers**; the entire execution engine is dead scaffolding; only state flows are consumed.
- `FfcsViewModel.initFromParsedCourses` (:101-134) — zero callers (would leave `isLoading=true` forever if ever used).
- `SessionManager.currentTheme`/`currentAccent` (:21-22) — zero readers (duplicate of `AppState._theme/_accent`).
- `SettingsManager.KEY_APP_ICON` (:14) — zero usages. App-icon switching (plan §3.11) never implemented.
- `SettingsManager.KEY_SYNC_ARREAR` (:22) — written by `setSyncArrear` (`AppState.kt:2572`) but **never read back** → "Include Arrear Info" toggle resets to true every launch; `syncExam`/`syncAdditional` toggles gate nothing either (`AppState.kt:325-359` init loads only syncExam/syncProfile/syncAdditional; `loadAllData` syncs exams regardless :986).

## 8. Stub / placeholder markers remaining in code (grep results)

`TODO|FIXME|placeholder|not implemented|coming soon` hits (excluding legit TextField `placeholder` params) — the complete list of real stubs:

- `TransportScreen.kt:317` (TODO track bus)
- `PaymentsScreen.kt:95,105,236,238` (4 TODOs)
- `LibraryPalette.kt:14`, `ReelScroller.kt:23` (placeholder comments)
- `EventHubScreen.kt:758` (`onClick = {}`)
- `HostelScreen.kt:421` (`/* Handle Click */`)
- `ErrorUtils.kt:37,58`, `MarksSync.kt:12`, `NotificationsUtils.ios.kt:54-55`, `AndroidNfcManager.kt:26-27`, `NotificationService` android:7/ios:7 (stub comments)

No `FIXME`/`HACK` tokens exist.

## 9. Summary

| Stub class | Count |
|---|---|
| Mock endpoints (AmazeClient) | ~70 |
| Fake-success paths | 11 |
| No-op platform services | 6 |
| Empty stub screen files | 4 |
| Dead buttons/onClick | 8 |
| Shipped placeholder content | 16 |
| Wired-to-nothing flows/params | 10+ |
