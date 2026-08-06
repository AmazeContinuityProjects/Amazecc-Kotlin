# Evaluation 05 — Dead Code Inventory

Everything verified (cross-grep) to have zero callers, or to be structurally dead. Grouped so each row is directly deletable. **Nothing here is used by live UI — deletion is safe** (re-verify each with a grep after edits).

---

## 1. Whole files — safe to delete

| File | Size | Notes |
|---|---|---|
| `ui/screens/academics/AcademicsHub.kt` | 3 ln | "kept for backward compatibility" — nothing left |
| `ui/screens/academics/VitolScreen.kt` | 1 ln | obsolete marker |
| `ui/screens/academics/MarksTimelineScreen.kt` | 1 ln | obsolete marker |
| `ui/screens/academics/MakeupCompreScreen.kt` | 1 ln | obsolete marker |
| `utils/MarksSync.kt` (+ `androidMain/.../MarksSync.android.kt`) | 18+7 ln | **deleting restores the iOS build** (expect without iOS actual) |
| `utils/PastDataSync.kt` | 131 ln | zero callers |
| `utils/ErrorUtils.kt` | 61 ln | zero callers; stub user-message branch |
| `utils/StringSimilarity.kt` | 49 ln | zero callers |
| `utils/FfcsPlannerAlgorithm.kt` | 83 ln | superseded by `FfcsEngine`; contains `// Mock metrics for now` stub |
| `utils/IcsUtils.kt` | 56 ln | zero callers |
| `ffcs/FfcsCourseSelector.kt` | 213 ln | `CourseSearchPanel` — zero callers; contains always-false blocked check |
| `repository/BusRepository.kt` | 23 ln | zero consumers |
| `repository/EventHubRepository.kt` | 87 ln | zero consumers; contains its own dedup bug |
| `repository/QBankRepository.kt` | 77 ln | zero consumers |
| `model/LoginModels.kt` | 25 ln | entirely dead (CaptchaResponse, LoginRequestBody…) |
| `ui/components/ErrorDiagnosticCard.kt` | 74 ln | zero callers |
| `ui/components/SyncNotification.kt` | 27 ln | self-documented deprecated, zero callers |
| `ui/components/ReelScroller.kt` | 66 ln | placeholder reels, zero callers |
| `ui/components/EventPalette.kt` + `LibraryPalette.kt` | 33+34 ln | rendered in App.kt but untriggerable — delete with their App.kt blocks + `openEventPalette/openLibraryPalette` |
| `ui/components/AmazePageScaffold.kt` | 34 ln | zero callers |

## 2. Dead classes & models (delete fields/classes)

**Dead classes (defined, zero external usage):**
`ApiMessage` (ArrearModels.kt:19), `DetailedAttendance` (AttendanceModels.kt:20), `EffectiveGrade`, `CurriculumItem`, `GradeCounts`, `FeedbackCategoryStatus`, `FeedbackStatus` (GradesModels.kt:7,15,22,43,49), `HolidayEvent`, `CalendarRequestBody`, `CalendarInput` (SemTTModels.kt:26,34,39), `ClubRole` + `LoginResponse.clubRoles` (MiscModels.kt:28,23), `HostelLeaveRes` (MiscModels.kt:78), `BusPlacement` + `BusRoute.placements` (MiscModels.kt:219,239), `CabSearchRequest` (MiscModels.kt:516), `CabShareSeatOptions` (MiscModels.kt:590), `CabShareMatchRequest` (MiscModels.kt:596), `DirectoryCCProfile` (MiscModels.kt:479), `Schedule` typealias (ScheduleModels.kt:20).

**Dead fields (~35):**
- `AttendanceItem.registrationDate`, `.attendanceDate` (AttendanceModels.kt:33-34); `slNo`, `classId`, `credits` UI-side
- `EventHubRegisteredEvent.orderId/receiptLink/certificateLink/payNowLink/payLaterLink` (EventHubModels.kt:24,28-31); `paymentStatus`
- `TransportDataRes.programme/branch/fpReference/pageCsrf` (MiscModels.kt:256-263)
- `CabTrip.seatsTotal/seatsAvailable/driverRating/isOwnTrip/vehicleModel/vehicleColor/vehiclePlate` (MiscModels.kt:494-504); `CabJoinRequest.requesterName` (:547); `CabShareTrip.from_hub_id/gender_preference/seat_options` (:573,580-581)
- `QBankQuestion.exam_year/image_url` (:313-314); `QBankPaper.paper_id` (:327)
- `FeedPost.has_promoted/promote_count` (:382-383); `ClubItem.recruitmentLink` (:359)
- `ProfileImagesCredential.venueDate/defaultCredentials/seatLocation` (:634-637)
- `CGPAResult.creditsRequired/nonGradedRequirement` (:44,47); `CGPA.creditsRequired/nonGradedRequirement/grades` (GradesModels.kt:35-39)
- `BookItem.issueDate` (MiscModels.kt:196)
- `ExamItem.classId/reportingTime/examSession/seatLocation` (ScheduleModels.kt:9-16)
- `HomeworkTask.referenceUrl` (TaskModels.kt:28)
- `GitHubRelease.publishedAt` (UpdateModels.kt:11)
- `HostelModels.LeaveItem.leaveId/remarks`
- `GradeBreakdown.slNo`, `GradeItem.slNo/courseId`; `AssessmentItem.slNo`; `MarksCourseItem.courseMode`
- `CourseItem.facultyDetails/LTPJC/category` (UI-dead)
- `MoodleAssignment` fields are fine (all used)

**FFCS dead fields:** `ParsedCourse.batch/originalCode/linkId` (FfcsModels.kt:14-16), `TimetableState.variants` (:75), `TimetableMetrics.bestFriendMatches` (:65), `FfcsConstants.DAYS` (:101-107), `FfcsConstants.TYPE_LABELS`/`getTypeLabel` (:116-129), `CourseOffering.toKey()`+`CourseLock.offerings` (:97).

## 3. Dead functions (delete)

**AmazeClient:** `getAttendance` (wrapper), `getMakeupExam` :1412, `getMakeupSchedule` :1435, `getCompreInfo` :1453, `getVtopStudentPhoto` :1324, `promoteFeedPost` :1801, `getFFCSReport` :1817 (typo domain anyway), `getLocalCabTrips` :1040.

**AppState:** `removeCache` :470 (@Suppress("unused")), `setSearchOpen`/`showSearch` :87-89 (delete + App.kt:66 read), `clearHeaderBackOverride` (no callers — keep only if you fix the H14 leak by calling it), `setDecimalValues`/`setFriendlyName`/`setCalendarView` (or persist them).

**SyncEngine (execution API — delete or make AppState use it):** `startSync` :323, `startSyncGroup`, `startSyncAll` :356, `cancelSync(module)` :364, `logSaveOffline` :400, `markSessionRefreshed` :409, `markSyncButtonTapped` :424, `lastSyncTime` :418, `lastSessionRefresh`, `lastSyncButtonTap`, `isModuleEnabled` :191.

**FfcsViewModel:** `initFromParsedCourses` :101, `setLock` :142, uncollected flows `allCourses`/`uniqueFaculty`/`morningPreference`/`maxResults` (screen uses local state).

**SessionManager:** `currentTheme`/`currentAccent` :21-22.

**SettingsManager:** `KEY_APP_ICON` :14, legacy `CACHE_TRANSPORT`/`CACHE_TRANSPORT_ROUTES`/`CACHE_TRANSPORT_PASS` :63-67 (only removed on logout).

**TimeMath:** `getTodayDayIndex` :52.

**AttendanceTimetable:** `findCurrentClass` :258, `findNextClass` :266, `getTodayAttendanceClasses` :244, `minutesUntil` :280.

**FacultyFreeSlotsUtil:** `formatFreeSlotSummary` :166, `getDayLabel` :171.

**FfcsTimetableGrid:** `getCoursesForDayPeriod` :168-173.

**WidgetDataUtils:** `getFreeClassroomsSample` :202 (replace with real engine before deleting the fake).

**UI components:** `AmazeGlassCard` :235, `MetricCard` :258, `ActionCard` :336, `AmazeDropdown` :515, `PageHeaderContainer` :584 (Components.kt); `Modifier.bouncyClick` :94, `Modifier.subtleGlow` :121, `courseColor()` :145 (AnimationUtils.kt); `CardVariant.GLASS` + SUCCESS/WARNING/DANGER/INFO variants (never selected); `SyncProgressPopup.onSaveOffline` param; `UpdateResultDialog.onCheckAgain` param; `DashboardScreen` dead slots (`commandPaletteTrigger`, `addTaskDialog`, `showManualUpdateResult` block).

**NotificationsUtils:** `createNotificationChannels` iOS stub (or wire channels properly).

## 4. Dead / unused resources & data

| Asset | Status |
|---|---|
| `shared/src/commonMain/composeResources/files/campus/{chennai,ap,bhopal}.json` | never read at runtime — only `files/version.properties` is read (UpdateConfig.kt:16) |
| `shared/src/commonMain/composeResources/files/ffcsReport.csv` (167 KB) | never read — hardcoded `FfcsReportData.CSV_DATA` used instead |
| `shared/src/commonMain/composeResources/files/team.json, quickLinks.json, demoData.json, dayscholar_buses.json, changelog.json` | never read (grep) |
| `CampusSchemas.AP_JSON`, `BHOPAL_JSON` (CampusSchemas.kt:277-568) | dead — only `CHENNAI_JSON` consumed (FreeClassroomsScreen:120) |
| `composeResources/font/outfit_black.ttf`, `geist_black.ttf` | bundled, never referenced (Type.kt registers only regular/bold) |
| `qrcode-kotlin` library (libs.versions.toml:20,35) | declared, unused — hand-rolled `QRCodeGenerator` used instead (either delete the lib or adopt it to fix H18) |
| Root `version.properties` (2.0.0/16) vs committed shared copy (2.0.2/18) | drift — pick one source of truth (build task already overwrites) |
| `hs_err_pid31392.log`, `error.jpg`, `free_lines.txt`, `patch_predictor.py`, `add_imports.py`, `keystore_base64.txt`, `events.json`, `release.keystore` | repo-root clutter; **`keystore_base64.txt` + `release.keystore` + `local.properties` should be gitignored/removed** (credentials in repo!) |

## 5. Dead strings / UI copy

- `Strings.kt` — ~97 of 101 entries unused (only `cancel`, `loading`, `signIn`, `loggingIn` referenced); incl. the whole settings row set (darkMode…contactUs) with no UI.
- `AboutScreen.kt:31-37` ≡ `ChangelogScreen.kt:28-34` — duplicate hardcoded lists (pick one source).

## 6. Structural dead weight (bigger than a file)

1. **The `AppState` god-object pattern is what makes dead code undetectable** — see `06-modularization.md` for the restructuring.
2. **Legacy `cab/*` API family** — `searchCabTrips`/`createCabTrip`/`getMyCabTrips`/`requestJoinTrip`/`getCabJoinRequests`/`acceptCabJoinRequest`/`rejectCabJoinRequest` (AmazeClient.kt:786-888) — the `cabshare/*` family superseded it; only the `New` AppState variants are wired.
3. **Two hub lists** (AmazeClient:39-54 + AppState:235-250) — collapse to one.
4. **`getCalendars` synthetic data path** — delete or rename to reflect it fabricates.
5. **Demo-mode UI in LoginScreen** (:329-392) — remove with the backdoor (C1) or gate behind `BuildConfig.DEBUG`.

## 7. Estimated cleanup yield

| Category | Count | Est. LOC removed |
|---|---|---|
| Whole dead files | 19 | ~1,100 |
| Dead classes + fields | 18 + ~35 | ~400 |
| Dead functions | ~45 | ~800 |
| Dead resources/data | 12 assets + 500+ ln CSV/JSON constants | ~3,000 |
| Dead strings + duplicates | ~100 | ~150 |
| **Total** | | **~5,000+ LOC (≈25% of codebase)** |

All safe to remove; re-grep each symbol before/after removal to confirm zero references. The single highest-value deletion: `MarksSync.kt` (unbreaks iOS).
