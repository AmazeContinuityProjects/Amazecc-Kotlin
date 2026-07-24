# Bug Tracker — AmazeCC-Kotlin

Generated: 2026-07-24

---

## 🚨 Critical — Will Cause Crashes or Incorrect Behavior

All critical issues resolved. ✅

---


## 🟡 High Priority

| # | Issue | Location | Fix |
|---|-------|----------|-----|
| H1 | Blanket `@file:Suppress("unused", ...)` on 9 files hides dead code | 9 files | Remove suppress, fix exposed issues |
| H2 | 14 empty `catch` blocks — errors silently swallowed | `AppState.kt`, `CalendarScreen.kt`, `SettingsScreen.kt`, `AlarmReceiver.kt` | Add logging or recovery |
| H3 | `MainScope().launch` without cancellation — coroutine leak | `CourseDetailScreen.kt:1480` | Use `rememberCoroutineScope()` |
| H4 | `_marks`, `_moodleData`, `_vitolData` exposed without `.asStateFlow()` | `AppState.kt:207,213,398` | Add `.asStateFlow()` |
| H5 | `@Suppress("unused")` on 17+ individual elements masks dead code | Various | Remove or implement |
| H6 | `AppState` scope uses `Dispatchers.Main` for network calls | `AppState.kt:41` | Consider `Dispatchers.Default` for heavy work |
| H7 | `_residentialStatus` default hardcoded as `"Hosteller"` | `AppState.kt:94` | Load from persisted settings |
| H8 | `_attendanceDisplayMode` and `_calendarView` are raw strings, not enums | `AppState.kt:88,91` | Use sealed class/enum |
| H9 | `HostelDetails` model duplicated in `MiscModels.kt` and `HostelModels.kt` | `MiscModels.kt:54`, `HostelModels.kt:6` | Consolidate |
| H10 | Pervasive hardcoded `Color(0xFF...)` instead of `colors.*` (~80+ instances) | 15+ screens | Use semantic `colors.*` |
| H11 | Pervasive hardcoded `RoundedCornerShape(X.dp)` instead of `AmazeTheme.radius.*` (~80+ instances) | 20+ screens | Use theme radius tokens |
| H12 | Pervasive hardcoded `fontSize = X.sp` instead of `AmazeTheme.typography.*` (~40+ instances) | All screens | Use theme typography |
| H13 | Pervasive hardcoded spacing/padding instead of `AmazeTheme.spacing.*` (~200+ instances) | All screens | Use theme spacing tokens |
| H14 | 8 custom card/component sets duplicating `AmazeCard` etc. | Multiple screens | Use shared components |
| H15 | `GlassMorphismScreen` unreachable (no navigation entry point) | — | Remove or add entry point |
| H16 | `onBack` callback passed but never wired to actual back button | `App.kt` (6+ screens) | Remove dead parameter |
| H17 | `CircularItem` has both `title` and `name` — confusing model | `ArrearModels.kt:35` | Consolidate fields |

---

## 🟡 Medium Priority

| # | Issue | Location | Fix |
|---|-------|----------|-----|
| M1 | Text without `maxLines`/`overflow` on dynamic content (~50 instances) | All screens | Add truncation |
| M2 | Hardcoded user-facing strings instead of resources (~60 instances) | All screens | Extract to constants |
| M3 | Small touch targets (36.dp icons) below 44-48dp Material minimum | ~15 instances | Increase to 44-48dp |
| M4 | `OutlinedTextField` used instead of `AmazeTextField` | `OnboardingScreen.kt` (6 instances) | Migrate to `AmazeTextField` |
| M5 | LazyColumn screens missing `contentPadding` (bottom=88dp) | 4 screens | Add content padding |
| M6 | `FfcsViewModel` uses `Dispatchers.Default` instead of `Main` | `FfcsViewModel.kt:32` | Fix dispatcher |
| M7 | `FriendsViewModel` has no persistence | `FriendsViewModel.kt` | Add persistence |
| M8 | `AmazeCard` used with `.clickable()` modifier instead of `onClick` param (misses animation) | 3 instances | Use `onClick` parameter |
| M9 | `AmazeCard` used without `.fillMaxWidth()` — may collapse | 2 instances | Add fillMaxWidth |
| M10 | `getCalendar()` and `getCalendars()` share same `/calendar` endpoint | `AmazeClient.kt:425,482` | Consolidate |
| M11 | `Payments` response manually parsed from `JsonObject` instead of model class | `AmazeClient.kt:595-638` | Use `@Serializable` model |
| M12 | `getSyllabusPdf()` passes `authorizedID` as query param (security) | `AmazeClient.kt:1479` | Use auth header |
| M13 | `CircularsScreen` has both `CircularItem.id` and `title`+`name` ambiguity | `ArrearModels.kt:35` | Clarify model |
| M14 | `GradeItem.grandTotal` appears unused | `GradesModels.kt:82` | Remove or document |

---

## 🟢 Low Priority

| # | Issue | Location | Fix |
|---|-------|----------|-----|
| L1 | `Icons.Rounded.ArrowBack`/`ArrowForward` not AutoMirrored | `OnboardingScreen.kt:162,178` | Use `AutoMirrored.Rounded.*` |
| L2 | `gradle.properties` redundantly defines versions conflicting with `libs.versions.toml` | Root | Clean up stale values |
| L3 | `baseUrl` in `AmazeClient.kt` is `var` but never reassigned | `AmazeClient.kt:52` | Change to `val` |
| L4 | `AmazeButton` hardcodes 48.dp height | `Components.kt:83` | Make configurable |
| L5 | Stale/misleading comments referencing "old" code or placeholders | 5+ files | Update or remove |
| L6 | `@OptIn` for experimental APIs that may break on updates | 7 files | Track API stability |
| L7 | Wildcard imports mixed with explicit imports — inconsistent | All screens | Standardize |
| L8 | `Icons.Rounded.MenuBook` deprecated (use AutoMirrored) | `DashboardScreen.kt:766` | Update |
| L9 | `Icons.Rounded.Chat` deprecated | `ClubDetailScreen.kt:99` | Update |
| L10 | `Icons.Rounded.Feed` deprecated | `ClubHubScreen.kt:230` | Update |
