# Implementation Plan

## Phase 1 — Client-side store (this repo)

Ordered tasks, each verified before moving on.

1. **Docs** — this directory. ✅
2. **Model** — `model/StudentIdentity.kt`: `StudentIdentity` + sub-models + computed helpers. Extended `StudentProfile` (MiscModels.kt) with `dob`, `gender`, `isHosteller`, `nativeState`, addresses, family, guardian, `ProfileProctor`. ✅
3. **Merge core** — `state/UserMerge.kt` (internal pure functions): `mergeIdentity(current, fragment, order, sources)` with per-field dotted paths, defaults, list/object/bool rules. ✅
4. **Extractor** — `state/IdentityExtractor.kt`: 11 pure functions + moved decipher helpers (tables, fields, fuzzy dayboarder, select resolution, humanizeKey). ✅
5. **Store** — `state/UserStore.kt`: `StateFlow`, `merge`, `loadFromCache`, `clear`, encrypted persist via `Encryption` + `SettingsManager.CACHE_USER_IDENTITY`. ✅
6. **SettingsManager** — added `CACHE_USER_IDENTITY` const. ✅
7. **AppState wiring** — both sync sites (loadAllData + refreshProfile), 4 login handlers, `loadCachedData`, `logout`, `saveOffline`, `reconcileExamSeatAlerts`; deleted legacy helpers/flows (`persistProfileImages`, `persistCredentials`, `loadCredentials`, `updateStudentProfile`, `_vtopPhotoBase64`, profile flows). ✅
8. **Consumer migration** — ProfileHub, ProfileIdentityPages (enriched with DOB/gender/mobile/nativeState + Address & Family), ProfileRecordsPages (rewritten as thin renderers over typed `VtopTable`/`KeyValueRow`), ProfileAchievementsPage, DashboardWidgets, SocialScreen, CommandRegistry. ✅
9. **Tests** — `commonTest/UserStoreTest.kt` written (empty-filtering, no-erase, priority tiers, deep proctor merge, bool rules, list replacement, cache round-trip, extractor shapes). ✅ written — ⚠️ cannot execute on Windows: module targets are android + ios only (no `jvm()`); iOS test compilation is host-restricted. Run on macOS (`iosSimulatorArm64Test`) or CI when available.
10. **Legacy removal** — grep-verified zero reads/writes of the 9 legacy profile cache keys (`CACHE_STUDENT_PROFILE` … `CACHE_APAAR_ID`); `CACHE_VTOP_PHOTO` read once for one-time migration of a pre-store photo. Constants kept as documented export blocklist. `ProfileImagesCredential` DTO kept (needed to deserialize `/api/profile-images`). ✅
11. **Whole-app sweep** — no identity-display surfaces left outside the migrated consumers; `SyncEngine` profile module `cacheKey`s repointed to `CACHE_USER_IDENTITY` (metadata only); `SettingsScreen` clear-cache now also clears the in-memory store; `ExportImportManager.neverExportKeys` expanded to `CACHE_USER_IDENTITY` + all 9 legacy profile caches + `CACHE_VTOP_PHOTO` (excluded from both export and import — PII can never leave the device). ✅
12. **Verify** — `gradlew.bat :shared:compileAndroidMain` BUILD SUCCESSFUL (only pre-existing deprecation warnings). Manual check pending: login → profile pages render from store; photo resolves; logout clears; demo mode renders.

### Verification commands

```
gradlew.bat :shared:compileAndroidMain          (verified 2026-08-15)
gradlew.bat :shared:iosSimulatorArm64Test       (unit tests — macOS host only)
```

## Phase 2 — `/api/me` (AmazeCC-API + client)

- **Server** — `AmazeCC-API/src/app/api/me/route.ts`: `POST /api/me` runs 6 VTOP scrapes in parallel (`StudentProfileAllView`, `viewProctorDetails`, `viewHodDeanDetails`, `viewStudentCredentials`, `apaarid/upload`, `BankInfoStudent`), normalizes them into the canonical `StudentIdentity` shape via `src/lib/identity.ts` (mirrors the Kotlin extractor semantics: filled-only values, typed officials + `extras`, tables passthrough), and returns `{ success, identity }`. ✅
- **Shared parser** — student-page parse extracted to `src/lib/parsers/student-profile.ts`; `/api/student` refactored to use it (response contract unchanged). ✅
- **Client** — `AmazeClient.getMe()` (`MeRes{ success, identity, error }`); the profile sweep becomes a single fetch → one `UserStore.merge(identity, IdentitySource.ME)` (tier 8, highest). Fallback: when `/api/me` is unavailable (older server, demo mode, partial failure), the five per-endpoint fetches (tiers 2–6) run as before. Both sync sites (`refreshProfile`, `loadAllData`) share one `syncIdentityModules()` helper. ✅
- **Tests** — ME-tier cases added to `UserStoreTest` (ME overwrites lower tiers; lower tiers can't overwrite ME). ✅ written — ⚠️ run on macOS (`iosSimulatorArm64Test`) or CI.
- **Verify** — `gradlew.bat :shared:compileAndroidMain` BUILD SUCCESSFUL; API repo typechecks (`npx tsc --noEmit`). Server-side scrape correctness needs a live VTOP session check against the deployed API.

Reduces 5 client requests (7 VTOP calls) to 1 (6 VTOP calls); VTOP session pressure per refresh unchanged.

## Phase 3 — Centralised app-data store (client)

Ordered tasks, each verified before moving on.

1. **Model** — `state/AppModels.kt`: `AppDataSnapshot` (30 fields) + `TimetableCourseInfo`/`TimetableSlot`/`StoredQcmRow`/`StoredQcmTable`; `MiscModels.kt` `TimetableRes` gained `courseInfo`+`slots`, `QcmViewRes` gained `tables`. ✅
2. **Sanitizers** — `state/AppSanitizers.kt` (pure): `clean()`/`cleanPercent()` (trim + drop placeholders `-`,`--`,`—`,`–`,`not set`,`tbd`,`tba`,`nil`,`null`,`n/a` case-insensitive), drop-by-identity-key, `decodeAttendanceLogs`, per-module `sanitize*` (attendance incl. typed `logs` + `viewLinkRaw=null`, marks, grades, timetable via `courseInfo` slots + `SlotMap` fallback, exam schedule, calendar(s), QCM decode → `tables` + `data=null`, curriculum, hostel, mess, laundry, counselling, payments, library, transport, buses, lms, events, registered events, clubs, recursive circulars, moodle, cab user, FFCS, tasks). ✅
3. **Store** — `state/AppDataStore.kt`: `object` with `StateFlow<AppDataSnapshot>`, 30 derived per-module flows (`distinctUntilChanged` + `stateIn(Eagerly)`), `update()` (equality guard + `persist()`), `restore()` (decrypt `CACHE_APP_DATA` else `migrateLegacyCaches()`), `loadPersistedSnapshot()` (side-effect-free, for widget/notification processes), `persistNow()`, `clear()`, `importSnapshot()`, `exportSnapshot()`, all `setX` setters (sanitize at the store boundary), task ops, `migrateLegacyCaches()` reading+deleting all 28 legacy `cache_*` keys. `SettingsManager.CACHE_APP_DATA` added. ✅
4. **AmazeClient / API** — `getTimetable` already decodes `TimetableRes`; server `courseInfo` field names match — no changes needed. ✅
5. **AppState rewiring** — all 30 module `StateFlow`s delegate to `AppDataStore`; every write site (`loadAllData`, `loadSemesterData`, `refreshCurrentSemester`, `refreshAllAcademic`, `refreshPastSemesters`, `runLightReload`, `syncOnboarding*`, `refresh*` family, `syncEventsAndClubs`, `updateAttendance/Marks/Moodle`, `saveLibrary/MoodleCredentials`, cab fns) now calls `AppDataStore.setX(...)`; `loadFromCache()` → `AppDataStore.restore()` + `UserStore.loadFromCache()` + VTOP-photo merge + `reconcileExamSeatAlerts()` + moodle/library auto-sync; `saveOffline()` → `SyncEngine.resetLogs()` + `AppDataStore.persistNow()`; `logout()` → `AppDataStore.clear()`; deleted `loadCachedData`/`cacheData`/`loadTasks`/`saveTasks`/task JSON plumbing. ✅
6. **Consumers** — `WidgetDataUtils` (4 getters + `computeODHours` via typed `logs`), `NotificationsUtils.selectedSemesterExams`/`rescheduleFromCache` (via `loadPersistedSnapshot`), `ExportImportManager` (exports decrypted `appData` snapshot, imports it via `AppDataStore.importSnapshot`, legacy `cache_*` settings skipped when snapshot present), `CourseDetailScreen` QCM card renders `StoredQcmTable`/`StoredQcmRow`, `CourseAttendanceScreen`/`ODTrackerScreen` log parsing via `AttendanceItem.logs`. ✅
7. **Verify** — `gradlew.bat :shared:compileAndroidMain` BUILD SUCCESSFUL (repeatedly during rewiring; final clean). ⚠️ Widget/notification processes + migration-from-old-install need device testing; unit tests on macOS/CI.
8. **Legacy removal** — grep-verified: no reads of legacy per-endpoint `CACHE_*` keys outside `AppDataStore.migrateLegacyCaches()` + identity keys; constants kept as migration source + export blocklist. ✅

## Risks

| Risk | Mitigation |
|---|---|
| Parallel arrival order → nondeterministic merge | Priority tiers; covered by UserStoreTest (run on macOS/CI) |
| Credential passwords leak into plain cache | Whole identity cache encrypted |
| Removing `studentProfile` breaks a widget/screen not in the migration table | Grep-verified deletion step; build catches references |
| Old cached payloads (pre-store) decode differently after model extension | `ignoreUnknownKeys`; old caches are simply no longer read |
| `VtopTable` normalisation changes record page appearance | Table rendering mirrors old `JsonTemplateTableCard`/`JsonTableCard` output cell-for-cell |
| Tests can't run on Windows (no jvm target, Apple host restriction) | Added `commonTest` only; run `iosSimulatorArm64Test` on macOS or CI |
| Sanitizer drops a legit value (aggressive placeholder rules) | Placeholder list is conservative; `clean()` only trims/drops blank-ish values |
| Widget/notification processes read a stale snapshot | They always call `loadPersistedSnapshot()` before reading; main process `persist()` on every update |
| Backup file from an old version has `cache_*` settings, no snapshot | Import still applies legacy entries; next `restore()` migrates them into the store |