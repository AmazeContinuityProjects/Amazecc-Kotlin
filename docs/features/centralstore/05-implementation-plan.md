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

## Phase 2 — `/api/me` (AmazeCC-API, optional)

- New `POST /api/me` in `src/app/api/me/route.ts`: parallel-scrape student + profile-images + credentials + apaarid + bank-info, normalize into the `StudentIdentity` shape (Phase 1's schema becomes the canonical server schema).
- Client: `AmazeClient.getMe()`; the profile sweep becomes a single fetch → one `UserStore.merge(..., IdentitySource.ME)`.
- Gate: only after Phase 1 lands and is stable. Reduces ~6 requests to 1; keeps VTOP session pressure unchanged.

## Risks

| Risk | Mitigation |
|---|---|
| Parallel arrival order → nondeterministic merge | Priority tiers; covered by UserStoreTest (run on macOS/CI) |
| Credential passwords leak into plain cache | Whole identity cache encrypted |
| Removing `studentProfile` breaks a widget/screen not in the migration table | Grep-verified deletion step; build catches references |
| Old cached payloads (pre-store) decode differently after model extension | `ignoreUnknownKeys`; old caches are simply no longer read |
| `VtopTable` normalisation changes record page appearance | Table rendering mirrors old `JsonTemplateTableCard`/`JsonTableCard` output cell-for-cell |
| Tests can't run on Windows (no jvm target, Apple host restriction) | Added `commonTest` only; run `iosSimulatorArm64Test` on macOS or CI |