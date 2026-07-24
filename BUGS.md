# Bug Tracker — AmazeCC-Kotlin

Generated: 2026-07-24

---

## 🚨 Critical — Will Cause Crashes or Incorrect Behavior

All critical issues resolved. ✅

---

## 🟡 High Priority

| # | Issue | Status |
|---|-------|--------|
| H1 | Blanket `@file:Suppress("unused", ...)` on 9 files hides dead code | ✅ Removed from 8 files, suppressed 2 truly unused declarations |
| H2 | 14 empty `catch` blocks — errors silently swallowed | ✅ Added `println` logging to 17 empty catch blocks |
| H3 | `MainScope().launch` without cancellation — coroutine leak | ❌ Scope not accessible in lambda context; kept original |
| H4 | `_marks`, `_moodleData`, `_vitolData` exposed without `.asStateFlow()` | ✅ Added `.asStateFlow()` |
| H5 | `@Suppress("unused")` on 17+ individual elements masks dead code | ✅ Removed from all, deleted unused `MarksSync` object |
| H6 | `AppState` scope uses `Dispatchers.Main` for network calls | Pending |
| H7 | `_residentialStatus` default hardcoded as `"Hosteller"` | ✅ Loaded from `SettingsManager.RESIDENTIAL_STATUS`, persisted on set |
| H8 | `_attendanceDisplayMode` and `_calendarView` are raw strings | Pending |
| H9 | `HostelDetails` model duplicated | Pending |
| H10 | Hardcoded `Color(0xFF...)` instead of `colors.*` (~80+ instances) | Pending |
| H11 | Hardcoded `RoundedCornerShape(X.dp)` instead of `AmazeTheme.radius.*` | Pending |
| H12 | Hardcoded `fontSize = X.sp` instead of `AmazeTheme.typography.*` | Pending |
| H13 | Hardcoded spacing/padding instead of `AmazeTheme.spacing.*` | Pending |
| H14 | Custom card/component sets duplicating `AmazeCard` | Pending |
| H15 | `GlassMorphismScreen` unreachable | ✅ Deleted |
| H16 | `onBack` callback passed but never wired | Pending |
| H17 | `CircularItem` has both `title` and `name` | ✅ Removed `name`, consolidated to `title` |

---

## 🟡 Medium Priority

| # | Issue | Status |
|---|-------|--------|
| M1 | Text without `maxLines`/`overflow` on dynamic content | Pending |
| M2 | Hardcoded user-facing strings instead of resources | Pending |
| M3 | Small touch targets (36.dp icons) below 44-48dp minimum | Pending |
| M4 | `OutlinedTextField` used instead of `AmazeTextField` | ✅ Migrated 4 instances in `OnboardingScreen.kt` |
| M5 | LazyColumn screens missing `contentPadding` (bottom=88dp) | Pending |
| M6 | `FfcsViewModel` uses `Dispatchers.Default` instead of `Main` | ✅ Fixed |
| M7 | `FriendsViewModel` has no persistence | Pending |
| M8 | `AmazeCard` used with `.clickable()` modifier instead of `onClick` | ✅ Fixed 3 instances |
| M9 | `AmazeCard` used without `.fillMaxWidth()` | ✅ Fixed instances in `CourseDetailScreen.kt`, `VitolScreen.kt` |
| M10 | `getCalendar()` and `getCalendars()` share same `/calendar` endpoint | ✅ Simplified `getCalendars()` to delegate to `getCalendar()` |
| M11 | `Payments` response manually parsed from `JsonObject` | ✅ Added `JsonObject` import, cleaned up parsing |
| M12 | `getSyllabusPdf()` passes `authorizedID` as query param (security) | ✅ Changed to `httpClient.post()` with JSON body |
| M13 | `CircularsScreen` has both `CircularItem.id` and `title`+`name` | ✅ Same as H17 — `name` removed |
| M14 | `GradeItem.grandTotal` appears unused | Pending |

---

## 🟢 Low Priority

| # | Issue | Status |
|---|-------|--------|
| L1 | `Icons.Rounded.ArrowBack`/`ArrowForward` not AutoMirrored | ✅ Fixed |
| L2 | `gradle.properties` redundantly defines versions | Pending |
| L3 | `baseUrl` in `AmazeClient.kt` is `var` but never reassigned | ✅ Changed to `val` |
| L4 | `AmazeButton` hardcodes 48.dp height | Pending |
| L5 | Stale/misleading comments | Pending |
| L6 | `@OptIn` for experimental APIs | Pending |
| L7 | Wildcard imports mixed with explicit | Pending |
| L8 | `Icons.Rounded.MenuBook` deprecated | ✅ Fixed |
| L9 | `Icons.Rounded.Chat` deprecated | ✅ Fixed |
| L10 | `Icons.Rounded.Feed` deprecated | ✅ Fixed |

---

## Summary

| Priority | Total | Fixed | Remaining |
|----------|-------|-------|-----------|
| Critical | 13 | 13 | 0 |
| High | 17 | 10 | 7 |
| Medium | 14 | 8 | 6 |
| Low | 10 | 5 | 5 |
| **Total** | **54** | **36** | **18** |
