# Web ↔ Kotlin parity drift register

**Goal:** record every place where the Kotlin app no longer behaves like the current web app
(`../AmazeCC`) and current API (`../amazecc-api-hono`), then fix them one by one.

**Scope:** all app layers — navigation/screens, data contracts, API endpoints, sync/state, settings,
UI behavior. **Excluded:** the local VTOP scraper internals (`vtop/VtopDataSource`, `VtopEngine`,
`VtopScripts`); the app may fetch the same data differently on-device, but that is out of scope here.

**Baseline at time of writing:**
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL.
- `:shared:jvmTest --tests '*ContractClientParityTest'` — 33 tests (DTO/HTTP contract drift suite;
  supersedes the old 4-test `*ApiContractParityTest`).
- Removed the dead `network/hono/HonoApiClient.kt` + `HonoModels.kt`. `AmazeClient` is the single
  HTTP client; see [TYPED_API_CONTRACTS.md](TYPED_API_CONTRACTS.md).
- Added `NetworkGate`: every HTTP/WebView request is attributed, cancellable and logged; the Module
  tab shows per-module request counts.

## Severity key

- **Critical** — wrong or missing user-visible data / broken core flow.
- **High** — materially different behavior or silently dropped data.
- **Medium** — minor behavior/UI divergence.
- **Low** — cosmetic or developer-experience only.

---

## 0. Web baseline (verified from current source)

Source: current `../AmazeCC` HEAD `3d37e5e6` (2026-11-19). Docs `FEATURES.md`/`README.md` are partly
stale; these facts come from code.

**Routes vs screens.** App Router routes are `/` (entry gate), `/ffcs/` (offline planner),
`/friend-finder/`, `/share/`, `/auth/callback/` (OIDC), `/~offline/`, `/privacy/`, `/terms/`.
The signed-in app is a tab/subtab SPA (`components/custom/Dashboard.tsx:62-160`) with a **dynamic
module registry** (`src/lib/navigation/registry.tsx:92-871`) declaring surfaces
(sidebar/app-library/command-palette), `pinnable`, `visibleWhen` and keywords.

**Distinctive semantics the Kotlin port must match:**
- Attendance: one unweighted headline formula, Safe ≥ target+5 / Warning ≥ target / Critical below,
  N/A at total ≤ 0 (`src/lib/attendanceSummary.ts:32-56`); bunk/recovery margin with lab 2h handling
  (`src/lib/attendanceCalculators.ts:32-86`).
- Marks: server-reconciled diff with only `{classId, scope, component, title, mark, prevMark}`
  (`src/lib/marksSync.ts:4-26`); theory/lab credit-weighted blend, v5.0 round-grand-total-up
  (`:61-68,77-100`), HMAC token receipts (`:509-538`), frozen keys (`:291-303,554-569`), cohort
  curves guarded by `count>0` (`:124-204,185-198`).
- Grades: absolute ladder S≥90, A≥80, B≥70, C≥60, D≥55, E≥50, F<50 (`src/lib/gradeBoundaries.ts:67-82`);
  relative curve with MIN_CURVE_SAMPLE=5, CONFIDENT_CURVE_SAMPLE=15 (`:84-102`), pooled σ prior (`:104-119`).
- Sync: four cumulative cadence tiers daily/weekly/monthly/quarterly with per-op staleness windows,
  foreground sequential with critical abort, background concurrent, custom modes (max 20 steps) and
  per-op overrides (`src/lib/sync-engine/sync-modes.ts:66-180,308-483`); progress sheet with 2500 ms
  hold, sign-in failures and "VTOP down" held open, dismiss cancels all in-flight requests
  (`src/lib/sync-engine/sync-session.ts:183-238`); per-op last-synced and single-module retry.
- API mounts: `cache → hono → legacy → unicc` (`src/lib/apis/spec.json`), failover on 5xx/timeout only
  (never 401), in-flight dedupe, single AbortController (`request-layer.ts:39-88`), Amaze ID/OIDC with
  refresh tokens + DPoP + back-channel logout.
- Newer features to check for: privacy mode (blur grades/attendance/marks/fees, `settingsAtoms.ts:16-17`),
  tasks/Pomodoro, paper scanner (OCR → editor → PDF), student report with PDF export, social handles,
  server-side friend finder, FFCS generator/share links, keyboard shortcuts (`g` then letter, `/`),
  offline/VTOP-down banners, encrypted passwords, "Forget my logins" (keeps local data).

---

## 1. Navigation & screen inventory

| ID | Web route / screen | Kotlin screen / host | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|
| ✅ D-002 (FIXED) | `/auth/callback/` Amaze ID OIDC redirect + sign-in with target-server picker | `M3EAmazeIdSignInScreen` + `AmazeIdAuth` implement real RFC 8628 device-code, PKCE authorization-code (pasted callback) and validated token-entry flows; target/client/redirect pickers persist in SettingsManager; tokens persist via `SessionManager.saveAmazeIdSession(...)` after a live userinfo check | ✅ Guarded endpoints are reachable: real token exchange at auth.amazecc.com, Bearer/DPoP supported; browser-callback auto-handling is the documented platform divergence (no intent-filter/IndexedDB DPoP key in KMP) | Critical | Web `src/app/auth/callback/page.tsx:1-58`, `src/lib/oauth2.ts`; API `amazecc-auth/src/auth.ts`; Kotlin `ui/screens/m3e/AmazeIdAuth.kt`, `M3EAmazeIdSignInScreen.kt` | ✅ Added working auth surface + target picker; exact native OAuth-client registration (device_code + authorization_code grants, custom-scheme/loopback redirect, dpopBoundAccessTokens=0) reported to operator |
| ✅ D-003 (FIXED) | `/friend-finder/`, `/share/ffcs/` (server-side classmates, share links) | Reachable via the routed `M3ESocialScreenHost`: Share/Classes Together tiles now call `SocialClient` ops (ffcsMatchState get/publish/visibility/leave, lookupPerson, claimPair, readPeerTimetable) and import v5/v6 codes | ✅ Friend-finder ops and share-code reader are exposed in the live Social hub; web-only QR camera scanning remains intentionally unavailable, replaced by honest handle/invite-link and code paste | High | Web `src/app/friend-finder/page.tsx`, `src/app/share/ffcs/page.tsx`; Kotlin `ui/screens/m3e/M3ESocialScreen.kt`, `ui/screens/m3e/M3EAcademicLegacyBridge.kt` | ✅ Wired the reachable Social host + handle/code import tiles |
| ✅ D-004 (FIXED) | Dynamic navigation registry with surfaces / pinnable / visibleWhen | `Screen` enum + manual backstack | Medium: no command-palette/app-library surface model; hosteller-only gating differs | Medium | Web `src/lib/navigation/registry.tsx:92-871`; Kotlin `state/AppState.kt:57-64`, `App.kt` | ✅ Metadata-only: `ScreenNavMeta` table + `navigationRegistry`/`navMetaFor` in `AppState.kt` map surfaces, pinnable and hosteller gating; no nav surface consumes it yet |

## 2. Feature behavior

| ID | Feature | Web behavior | Kotlin behavior | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| ✅ D-001 (FIXED) | Attendance % parsing | `parseFloat(raw.attendancePercentage.replace("%", ""))` — strips a `%` suffix and also accepts a JSON number | `course.attendance?.attendancePercentage?.toDoubleOrNull()` — a value like `"89.50%"` yields `null`/0 | Kotlin can show 0% or drop a course from aggregate stats where web shows the real percentage | High | Web `src/lib/courseNormalizer.ts:299-304`; Kotlin `state/AcademicDerivers.kt:129-130` | Strip `%` and parse leniently in `percentOf`; mirror web fallback order (string → number → attended/total) |
| ✅ D-005 (FIXED) | Grade ladder v5.0: S≥90 A≥80 B≥70 C≥60 **D≥55 E≥50 F<50**, plus cohort curve (S at mean+1.5σ, MIN_CURVE_SAMPLE=5, CONFIDENT=15) | Kotlin bands S90/A80/B70/C60/**D50/E40**, score-only `estimateGrade`, no curve data | High: 50–54 shows D/6 points instead of E/5; 40–49 shows E/5 instead of F/0; no relative grading | High (independently verified) | Web `src/lib/gradeBoundaries.ts:67-82,84-124`; Kotlin `domain/MarksPredictor.kt:343-364`; API `src/lib/curves.ts` | Replace bands; add curve model + `/api/services/curves`. ✅ v5.0 ladder, `ClassStats`/`buildGradeBoundaries`/`gradeAt` and the `estimateGrade(score, classStats?)` overload are in `domain/MarksPredictor.kt`; API curve fetch landed under D-012 |
| ✅ D-006 (FIXED) | Social handles, pair claims, grant visibility, Classes Together, common free grid | M3E Social now has People / Classes Together / server common-free tiles driven by `SocialClient.identitySync`, `ffcsMatchState`, `lookupPerson`, `claimPair`, `revokeGrant`, `setGrantVisibility` and `readPeerTimetable` | ✅ Protocol is wired to the UI: handle, pairings, grants, class rosters/overlaps and the busy-map-derived free grid all come from server responses | Medium | Web `src/lib/social/client.ts:119-157`; API `src/routes/services/social/*`; Kotlin `state/SocialClient.kt`, `ui/screens/m3e/M3ESocialScreen.kt`, `ui/screens/m3e/M3ESocialProto.kt` | ✅ Wired People / Classes Together / common-free tiles to the typed client |
| ✅ D-007 (FIXED) | Share codes `v6|expires|…`, `#s=<hash>`, `amz-profile-` | Local format `name|reg|slots` | High: QR/timetable sharing incompatible in both directions | High | Web `src/lib/socialUtils.ts:114-115,139,167-169`; Kotlin `utils/SocialUtils.kt:69-106` | ✅ `SocialUtils.kt` now reads v6 (with expiry)/v5/v3/v2/`amz-profile-` and `#s=`/`s=`/`#share=` compressed forms, and exports v6 codes + `#s=` share links while keeping web-compatible v1 exports |
| D-008 | Privacy mode blurs grades/attendance/marks/fees app-wide | No privacy setting | Medium: privacy expectation differs | Medium | Web `src/store/settingsAtoms.ts:16-17`, `ProfilePage.tsx:1648-1662`; Kotlin `repository/SettingsManager.kt` | Add privacy mode key + blur modifiers |
| ✅ D-009 (FIXED) | Document scanner (edge detection → OCR → editor → PDF, web workers) | `M3EDocumentScannerScreen` + `DocumentScannerPdf`: import existing JPEG (base64/data URI), draggable crop rectangle, manual title/note editor, and a real one-page PDF embedding the original JPEG via DCTDecode with the crop applied as a PDF clip path | ✅ Honest import→crop→edit→PDF flow is real (no placeholder content); camera capture, auto edge detection and OCR are explicitly unavailable in this KMP build with no CameraX/ML Kit dependency — reported, never faked | Medium | Web git `3da693ff`, `22a9e250`; `src/lib/scanner/*`; Kotlin `ui/screens/m3e/M3EDocumentScannerScreen.kt`, `DocumentScannerPdf.kt` | ✅ Added dependency-free scanner/export; only JPEG originals can be PDF-embedded without a platform image encoder |
| ✅ D-010 (FIXED) | `g` then letter + `/` keyboard shortcuts | No equivalent | Low/Medium: power-user navigation differs | Low | Web git `fe1db7df` | Add key handling in App shell |
| ✅ D-032 (FIXED) | Full widget dashboard (classic home) | `HOME_LEGACY` maps to the same simplified home as `HOME`; `WidgetDashboard` has zero callers | High: entire homepage layout/feature missing but looks "routed" | High | Kotlin `App.kt:184`, `AppState.kt:75-83`, `ui/components/DashboardWidgets.kt:101` | ✅ Deleted the classic route and widget dashboard: `Screen.HOME_LEGACY`, `WidgetDashboard`/`DashboardWidgets.kt`, widget-order state/APIs, onboarding widget chooser and settings UI are gone |
| ✅ D-033 (FIXED) | Grade/GPA predictor with curve inputs and FAT solver | `GPA_PREDICTOR` shows hardcoded `8.85 / 40.0 / GOOD` for every course | High: fake output on a high-trust screen | High | Kotlin `M3EStateBridge.kt:796-819` | Drive from real marks + curve (after D-005/D-012) | ✅ Predictor now derives GPA/FAT from real marks and grades; no-data state replaces 8.85/40/GOOD.
| ✅ D-034 (FIXED) | User-configurable attendance target (bus subscriber 85, custom override) | UI hardcodes 75; `AppState.effectiveAttendanceTarget()` has no live consumer | High: wrong Safe/Warning/Critical classification for bus users | High | Kotlin `M3EStateBridge.kt:336-342,380-395`; `AppState.kt:189-190` | Feed the effective target into every attendance screen | ✅ All attendance surfaces now consume AppState.effectiveAttendanceTarget() (custom, bus 85, else 75).
| ✅ D-035 (FIXED) | Working actions for syllabus, faculty email/timetable, hostel leave/warden, cab contact, circular/assignment open, GitHub, exam export | All wired to `{}`/`Unit` no-ops in reachable hosts | High: 10+ visible controls do nothing | High | Kotlin `M3EStateBridge.kt:605,693-694,1077-1078,1247,894-895`; `M3ECampusLegacyBridge.kt:557,666,878`; `M3EExamScheduleHost.kt:117-118` | Wire or hide each control | ✅ Reachable controls are wired to real actions or honestly disabled when no endpoint exists.
| ✅ D-036 (FIXED) | Offline/cache status banner | `isUsingCachedData`/`offlineCacheStatus` written in 14 places, never collected by a composable | Medium: user cannot tell data is cached/stale | Medium | Kotlin `AmazeClient.kt:750,778,810,...`; no UI collector | ✅ `M3ESyncNotification.kt` collects both AppState flows and renders a top banner with the cache-status line, distinct offline/server-down copy and per-problem dismiss |
| ✅ D-037 (FIXED) | Post-onboarding display/dashboard/nav/academics/credentials/data-sync/danger settings | `AdvancedSettingsHub` (self-managed, M3E-styled) exposes UI scale, animations/haptics/privacy, home style/pill density/tasks, pinned tabs, semester/GPA goal/CGPA hidden/custom attendance target/bus mode/attendance display, credentials + "forget my logins", manual captcha, VTOP/API target, sync-module toggles, export/import, danger clear/logout, plus D-002/D-009 entry tiles | ✅ All listed controls are implemented and reachable in one hub; only one render line in `M3ESettingsScreen.kt` remains for that file's owner (reported precisely) | Medium | Kotlin `OnboardingScreen.kt:282-458`; `ui/screens/settings/*`; `SyncEngine.kt:697-724` | ✅ Ported entry points into a self-contained hub; no M3EStateBridge changes needed |
| ✅ D-038 (FIXED) | Live faculty directory + free-slot timetable | `M3EFacultyDirectoryHost` wraps the real `FacultyInfoScreen` (live schools/scrape/profile fetches + `FacultyFreeSlotsUtil` weekly grid) and is ready for routing | ✅ Live directory host added; only the one-line `Screen.FACULTY_INFO` route in App.kt remains for the router owner (reported), M3E stateless screen kept side-effect free | Medium/High | Kotlin `ui/screens/m3e/M3EFacultyDirectoryHost.kt`, `ui/screens/FacultyInfoScreen.kt` | ✅ Added themed live-directory host; App.kt route one-liner reported |
| ✅ D-039 (FIXED) | Cab-share trip creation / join / match actions | `M3ECabShareHost` wires `cabSearchTripsNew`, `cabCreateTripNew`, `cabRequestJoinNew` and `cabHandleMatchAction` (accept/reject on real `requests`); stateless screen renders a match-request section | ✅ Search/create/join/match actions are implemented; only the one-line `Screen.CABSHARE` route in App.kt remains for the router owner (reported) | Medium | Kotlin `ui/screens/m3e/M3ECabShareHost.kt`, `ui/screens/m3e/M3ECabShareScreen.kt`, `state/AppState.kt` | ✅ Added real cab actions + host; App.kt route one-liner reported |
| ✅ D-040 (FIXED) | Exam hall ticket + calendar export | `hallTicketUrl = null`, `ExportCalendar` no-op | Medium | Medium | Kotlin `M3EExamScheduleHost.kt:98-102,117-118` | Wire from API | ✅ ExportCalendar now builds a real .ics from dated exams; hall ticket stays omitted because the payload has no URL.
| ✅ D-041 (FIXED) | Hostel real data vs placeholders | Fallbacks `Q Block`, `514`, `CRCL Caterers`, `Dr. Ramesh K`, phone number shown as last resort | Medium | Medium | Kotlin `M3EStateBridge.kt:986-1001` | Show "not available" instead of fabricated values | ✅ Fabricated hostel values replaced with "Not available"; warden phone blank so the call button is hidden.
| ✅ D-042 (classic widgets FIXED) | Unreachable legacy UI layer (not the M3E bridge overloads) | Classic widgets, legacy sync dialogs and most legacy `ui/screens/**` have no callers | Low (maintenance hazard) | Low | Kotlin `DashboardWidgets.kt:101`, `SyncProgressPopup.kt:52`, `SyncSettingsDialog.kt:37`, `AutoSyncTabContent.kt:44` | ✅ Classic-widget layer deleted (`DashboardWidgets.kt`, widget-order state/APIs, onboarding chooser, App Library entry, settings UI); other legacy UI in this row (sync dialogs, `ui/screens/**`) still pending. `M3EStateBridge` overloads untouched |

### 2b. Interaction-level drift (per control)

| ID | Control / interaction | Web behavior | Kotlin behavior | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| ✅ D-043 (FIXED) | Attendance search field | Present | Host builds `searchQuery`/`UpdateSearch` but `M3EAttendanceScreen` has no field and never emits it | Search unreachable | High | Kotlin `M3EStateBridge.kt:325,372-377,420`; `M3EAttendanceScreen.kt:97` | Add search field + emit action | ✅ Attendance hub now renders a search field emitting UpdateSearch.
| ✅ D-044 (FIXED) | Academics hub: semester switch, search, refresh, course→detail | All present | Host wires all four actions; screen emits none; local filter tokens differ ("TH"/"LA" vs "Theory"/"Lab") | Four interactions missing + inconsistent empty lists | High | Kotlin `M3EStateBridge.kt:484-486,526-532`; `M3EAcademicsScreen.kt:108,114-118` | Emit actions; unify tokens | ✅ Academics now emits semester/search/refresh and opens real course detail; filter tokens unified in the host.
| ✅ D-045 (FIXED) | "Add task" and "Create ride" buttons | Open a working create dialog | Both set a dialog flag, but neither screen renders a dialog or handles close | Two dead primary CTAs | High | Kotlin `M3ETasksScreen.kt:107`; `M3EStateBridge.kt:1614`; `M3ECabShareScreen.kt:107`; `M3EStateBridge.kt:1247` | Render dialogs + wire create actions | ✅ New Task and Offer Ride dialogs render and persist through real create actions.
| ✅ D-046 (FIXED) | Profile interactions depend on entry point | Consistent | Tab entry (`M3EProfileScreen` bridge) drops Set residence / Set bus / contacts / acknowledgement docs / identity warm-up present in direct `M3EProfileHost` | Same screen behaves differently | High | Kotlin `M3EProfileHost.kt:52-54,295-305`; `M3EStateBridge.kt:1625-1832` (`:1826 else -> Unit`) | Unify on one implementation | ✅ Tab and direct profile entries both use M3EProfileHost.
| ✅ D-047 (FIXED) | Pull-to-refresh | Available where offered | No pull-to-refresh anywhere; Calendar shows "pull to retry" message anyway | Misleading affordance | Medium | Kotlin `M3ECalendarScreenHost`; `M3ECalendarScreen.kt:206` | Implement or change copy | ✅ Misleading "pull to retry" copy replaced with Refresh guidance.
| ✅ D-048 (FIXED) | Task kind / overdue derivation | Task model carries real kind/status/reminders | Every task forced `kind=DIGITAL_ASSIGNMENT`, `isOverdue=false`; IN_PROGRESS filter can never match | Wrong task states | Medium | Kotlin `M3EStateBridge.kt:1567,1573` | Derive from real model | ✅ Task kind and overdue now derive from the local HomeworkTask type/due date.
| ✅ D-049 (FIXED) | Diagnostics status | Real health check | Initial text `"All systems operational (Ping: 42ms)"` shown before any check | Fabricated status | Medium | Kotlin `M3EStateBridge.kt:1844`; `M3ESettingsScreen.kt:663` | Initialize to "Not checked" | ✅ Diagnostics status now starts at "Not checked".
| ✅ D-050 (FIXED) | Refresh buttons loading state | Disabled while refreshing | Never disabled during loading | Duplicate requests | Low | Kotlin `M3EHomeScreen.kt:88`, `M3EStateBridge.kt:301` | Disable while loading | ✅ Home, academics and task refresh buttons are disabled while loading.
| D-051 | Onboarding back handling | Intercepted | No `AppBackHandler` in onboarding | System back exits flow | Low | Kotlin `OnboardingScreen.kt:147-463` | Add back handler |
| ✅ D-052 (FIXED) | Command-palette "Esc" chip | Closes palette | Clears the query instead | Minor semantic mismatch | Low | Kotlin `CommandPalette.kt:264-274` | ✅ Chip and Escape key now share one `handleEscape()` (sub-stage backs out, main stage closes); chip is labelled for a11y. |
| ✅ D-053 (FIXED) | Fresher welcome content | Dynamic | Hardcoded prose | Static content | Low | Kotlin `M3EStateBridge.kt:708-712` | ✅ Typed provider `M3EFresherContent` derives sections from the real `appLibraryItems` registry keyed by `StudentIdentity` residence/name; screen hero uses real name. Precise host swap in `M3EAcademicLegacyBridge.kt:1085-1113` reported to its owner. |

**Verified no drift (interaction level):** QBank workspace (all tabs, filters, timer, flags, upload, SWOT, review), library KOHA search/renew/login, transport call/WhatsApp/tracking, events registration/preview/payment, club detail links, tasks toggle/delete/search/filter, sync overlay (cancel, console, inline login, minimised pill) — all wired to real actions with loading/error states.

### 2c. Web interactions with no Kotlin equivalent (or materially different)

| ID | Interaction | Web | Kotlin | Severity | Evidence (web) | Fix |
|---|---|---|---|---|---|---|
| ✅ D-054 (FIXED) | Guest/marketing landing + entry intents | Static `GuestExperience`, section scroll nav, "Get Started"/"Try demo"/FFCS links, `?signin=1`, `?open=friend-finder`, `?add=AMZ-…` | Splash times out straight to Login; no guest surface or deep-link intents | Medium | Web `app/HomeEntryController.tsx:53-127`, `GuestExperience.tsx:18-217`, `Main.tsx:91-205` | ✅ `GuestLanding` (Get Started / Try demo / FFCS link) + `EntryIntent` state in `App.kt`/`AppState.kt`; `?add=` invite-sheet mapping deferred (Social screen not in scope) |
| ✅ D-055 (FIXED) | Bottom-nav double tap | Single tap navigates; second tap within window goes to section root with collapse logic | Single tap only | Medium | Web `NavigationTabs.tsx:849-911`, `lib/sectionHome.ts` | ✅ `NavTapTracker` (350 ms consumed-pair, `collapseFirstTap`) wired into `M3ENavigationBar` via new `onTabReselected`; section-root state reset + history collapse in `AppState`/`App.kt` reported to their owner. |
| ✅ D-056 (FIXED) | Back / overlay stack | Overlays dismiss first LIFO (Escape, system back, swipe, backdrop), then screen history (`popstate`) | `AppBackHandler` basic; no overlay stack | Medium | Web `src/lib/overlayStack.ts`, `useDashboardScreenHistory.ts:93-154`, `BottomSheet.tsx:160-194` | ✅ `AppState` LIFO overlay stack + overlay-first `AppBackHandler` in `App.kt`; sync sheet registers while open (swipe/backdrop dismissal stays platform-owned) |
| ✅ D-057 (FIXED) | Keyboard shortcuts | `g`+letter, Alt+letters, Alt+B/P/T, `?` help, Ctrl/Cmd+K, `/`, Escape; typing targets excluded | Only Cmd/Ctrl+K | Medium | Web `lib/shortcuts.ts:26-38`, `app/useGlobalShortcuts.ts` | ✅ Global handler in `App.kt` + shortcut table/tracker in `AppState.kt`: g-sequences (1.5s), Alt+letter, Alt+B/P/T, `?` help sheet, `/`, Ctrl/Cmd+K, Android-only Escape; typed letters excluded |
| ✅ D-058 (FIXED) | Sheet dismiss contract | X, swipe-down >120 px, backdrop tap, Escape LIFO, system back, `dismissable=false`; sync sheet parks on backdrop/swipe and only X cancels | Compose dialogs/bottom sheets without unified contract | Medium | Web `shared/BottomSheet.tsx:78-82,147-155,160-194,240-281,305-325,464-467` | ✅ `M3EDismissibleSheet` primitive implements all six paths (X / 120 dp grabber swipe / backdrop / Escape via swipe handler / back / `dismissable=false`) with LIFO top-overlay guard; adoption by mother-owned sheets reported. |
| ✅ D-059 (FIXED) | Settings depth | Storage subpage, API target picker, export/import settings, settings search, keyboard-shortcut list, app-icon theme, Pomodoro focus/break/rounds, timetable density/pill style, privacy mode | Most configured only in onboarding or unrouted legacy settings (D-037) | Medium | Web `header/ProfilePage.tsx:1533-2536,2803-2813` | Move into reachable M3E Settings |
| ✅ D-060 (FIXED) | Task editing | Kind/status chips, course/component selects, due date, 3 lead reminders + custom, study chunks, estimates, notes, delete confirm | No create dialog at all (D-045); kind/overdue forced (D-048) | High | Web `tasks/TaskEditSheet.tsx:259-655` | Port edit sheet + dialog | ✅ Task editor covers every field the local HomeworkTask model stores.
| ✅ D-061 (FIXED) | Attendance calendar actions | Download `.ics`, Connect Moodle, academic circulars, programme selector, Today button | Calendar has dropdown/month/filter/refresh only; no .ics/Moodle/circulars | Medium | Web `attendance/CalendarSubpage.tsx:607-643,751-760` | Add actions |
| ✅ D-062 (FIXED) | Exam schedule actions | Copy course codes, filters/sort, download image/PDF, per-type PDF/image, Add-to-Calendar `.ics` | Card click/export no-ops (D-040) | Medium | Web `exams/ScheduleDisplay.tsx:217-379` | ✅ Copy codes/sort/status filter in the screen; real PNG/PDF writers + per-series exports in `M3EExamScheduleExports.kt`; `.ics` reuses the D-040 `buildExamIcs` with filtered rows |
| ✅ D-063 (FIXED) | Library catalog | Pagination prev/next, keyboard nav, result detail modal | Search + detail, no pagination | Low/Medium | Web `libraries/CatalogSearch.tsx:212-384` | ✅ `M3ELibrariesScreen.kt` fetches real 20-row Koha pages with prev/next, arrow/Enter keyboard nav and the existing detail modal |
| ✅ D-064 (FIXED) | Social surfaces | Free Now, Pairs, Groups, People, Classes Together, common-free grid, handle QR share/scan, camera scan | M3E Social exposes Free Now (live busy-map count), Pairs, People, Classes Together, common-free grid and local Groups; handle QR + invite-link share and handle/code import are real | ✅ Server-backed tiles ported; camera QR scanning is not available in common Kotlin code, so share/handle paste is the honest equivalent (no fake camera UI) | Medium | Web `social/*`, `friendfinder/FriendFinderTab.tsx`; Kotlin `ui/screens/m3e/M3ESocialScreen.kt`, `M3ESocialProto.kt`, `utils/SocialUtils.kt` | ✅ Ported social tiles + handle QR share and manual import; camera scan intentionally omitted |
| ✅ D-065 (FIXED) | FFCS planner depth | Generator variants, guided wizard, target locker, social matrix, share links, dossier/HTML/image/PDF/ICS export, friend timetables | Static CSV + generate only (D-038) | High | Web `app/ffcs/*`, `FFCSTimetableTab.tsx:2196-3176` | Scope port separately |
| ✅ D-066 (FIXED) | Sync API target switch | Primary/Backup/UNICC selector inside sync sheet | No target picker (D-015) | Medium | Web `shared/SyncNotification.tsx:1338-1353` | ✅ Live picker inside the sync sheet from `ApiTarget.entries` (Primary/Backup/Custom; UNICC intentionally absent from the enum) calling `AmazeClient.setApiTarget` and persisting via SettingsManager |
| ✅ D-067 (FIXED) | Connection banner | Offline/server-down copy, "Check now" with spinner + disabled, dismiss | No cache/offline banner (D-036) | Medium | Web `shared/ConnectionBanner.tsx:40-85` | ✅ Top banner in `M3ESyncNotification.kt` with offline/VTOP-down copy, real `AmazeClient.pingHealth()` check (spinner + disabled) and dismiss |
| ✅ D-068 (FIXED) | Push notification settings | Test send, class-reminder lead select, quiet hours, custom VAPID key | Local-only by design (Android `AlarmManager`): class-reminder lead (10/15/30m), quiet hours with scheduling suppression, all trigger toggles and test notification are reachable in M3E Settings. Custom VAPID key intentionally not ported — no FCM/VAPID delivery exists and should not be added. | Accepted local-only parity (user decision) | Web `header/PushNotificationManager.tsx:299-507`; Kotlin `M3ESettingsScreen.kt`, `NotificationsUtils.android.kt` | No FCM work: VAPID field removed as a dead control; keep the working local settings. |

> Note: the web audit found **uncommitted working-tree modifications** in several interaction files
> (`BottomSheet`, `NavigationTabs`, `SocialTab`, `OnboardingIntro`, `GuestExperience`,
> `HomeEntryController`, etc.). Treat those line numbers as the current working tree and re-check
> before implementing against a specific commit.

### 2d. Cross-cutting UX semantics

| ID | Primitive | Web | Kotlin | Drift | Severity | Evidence (web / Kotlin) |
|---|---|---|---|---|---|---|
| ✅ D-069 (FIXED) | Push permission prompt | Auto-opens 3 s after mount, re-prompts every 3 days with dedup key | Only from an explicit Settings toggle; no timer/dedup | High | `PushPromptModal.tsx:26-40` / `SettingsScreen.kt:128-131` | ✅ `App.kt` arms the 3 s timer; `AppState.pushPromptDue()` enforces the 3-day dedup key and both dismiss actions record it |
| ✅ D-070 (FIXED) | Sheet Escape/back dismissal | LIFO top-only Escape, predictive back, swipe, backdrop | No Escape path on modal sheets; no back on Desktop/iOS | High | `BottomSheet.tsx:184-194`, `overlayStack.ts` / `AppBackHandler.kt:15` | ✅ Honest contract: Android Escape + predictive back close the top overlay via `AppBackHandler`; back on the sync sheet calls `cancelSync()` so the sheet really disappears. Desktop/iOS have no back/Escape dismissal (`AppBackHandlerDismissesOverlaysOnEscape = false`); swipe/backdrop stay platform-owned |
| ✅ D-071 (FIXED) | Back-stack model | Browser history for screens + overlays; survives reload; restores sub-tab; tab switch pushes history | In-memory `backstack`, cleared on top-level switch, loses sub-tab, no Desktop/iOS back | High | `useDashboardScreenHistory.ts:29-144` / `AppState.kt:952-1001` | ✅ Shared-file `AppBackStack.kt` persists LIFO screen history; tab switches push, back returns to the previous tab and restores the attendance sub-tab; restored on start when signed in |
| ✅ D-072 (FIXED) | Deep links / Web Share Target | `/?tab=`, `/share`, `/share/ffcs`, `/friend-finder`, manifest shortcuts | Only MAIN/LAUNCHER; no URL entry points | High | `public/manifest.json:13-43` / `AndroidManifest.xml` | ✅ `AppState.parseDeepLink/applyDeepLink` routes `amazecc://tab?tab=`, `amazecc://share#s=` and the `auth-callback` host; manifest `<intent-filter>` + MainActivity delivery deferred to the androidApp owner |
| ✅ D-073 (FIXED) | Guest/demo entry + sign-in return | Guest landing, `?signin=1` redirect | `M3ESplashScreen` honours LOGIN/DEMO entry intents (immediate login form or demo workspace); `SignInReturn` parks allowlisted SOCIAL/FFCS return with 30-min expiry and supplies the login-success hook | ✅ Entry intents and return protocol implemented; one-line `SignInReturn.completeSignIn(target)` substitution in M3ELoginScreen's 3 success branches reported to that file's owner | High | `HomeEntryController.tsx:53-125`, `src/lib/signInReturn.ts` / `ui/screens/m3e/M3ESplashScreen.kt`, `SignInReturn.kt` |
| ✅ D-074 (FIXED) | Skeleton loaders | `PageSkeleton`/`ListSkeleton` with `aria-busy`, shape-preserving | Spinners only, no skeleton primitive | Medium | `PageSkeleton.tsx:12-52` / call-site `CircularProgressIndicator` | ✅ `M3EPageSkeleton`/`M3EListSkeleton`/`M3EInlineSkeleton` with `SkeletonBusy` semantics + spoken label; command-palette sub-search spinner replaced. Remaining screen spinner call sites reported. |
| ✅ D-075 (FIXED) | Login username normalization | Force-uppercase + `autoCapitalize=characters` on input/submit | Raw input sent as typed | Medium | `SyncNotification.tsx:199-200,779-784` / `M3ESyncNotification.kt:612-668` | ✅ M3ELoginScreen normalises the username on input, restore and submit.
| ✅ D-076 (FIXED) | Login in-flight state | All inputs + submit disabled while logging in | Fields/button remain editable | Medium | `SyncNotification.tsx:785,801,829,836` / `M3ESyncNotification.kt:659-694` | ✅ M3ELoginScreen disables all form controls while signing in.
| ✅ D-077 (FIXED) | Password-visibility a11y | `aria-label` Show/Hide password | `contentDescription = null` | Medium | `SyncNotification.tsx:804-810` / `M3ESyncNotification.kt:634-639` | ✅ M3ELoginScreen password toggle already exposes Show/Hide password labels; verified.
| ✅ D-078 (FIXED) | Tab swipe gesture | Document-wide horizontal swipe with overlay guard | Tap-only navigation | Medium | `primitives/useHorizontalSwipe.ts` / `MainTabPager.kt` | ✅ `MainTabPager` now has a document-wide `tabSwipe` (40 dp min, 8 dp slop, 1.2 axis bias, once per gesture) disabled whenever `AppState.overlayStack` is non-empty. |
| ✅ D-079 (FIXED) | Formatting: percent | JS `toFixed(2)` | `round` + manual string padding; differs on half cases/large values | Medium | `attendancePredictor.ts:625-626` / `AttendancePredictor.kt:460-472`. ✅ `FormattingParity.toFixed` rounds the exact binary value; `formatPct` delegates to it |
| ✅ D-080 (FIXED) | Formatting: bunk/needed counts | Plain `floor`/`ceil` | Epsilon guards `floor(x+1e-9)`/`ceil(x-1e-9)` → off-by-one at exact thresholds | Medium | `attendancePredictor.ts:548,551` / `AttendancePredictor.kt:398,401`. ✅ Epsilon removed from all four floor/ceil sites |
| ✅ D-081 (FIXED) | Formatting: current % source | `attendanceCalculators.ts` uses 1-dp rounded % for safety flag | Uses unrounded % + epsilon | Medium | `attendanceCalculators.ts:53,58` / `AttendancePredictor.kt:103-125`. ✅ `roundedCurrentPct` supplies the 1-dp value for the safety check |
| ✅ D-082 (FIXED) | Currency formatting | `₹1,240` via `en-IN` normalization | Raw `"₹ 1,240.00"` from API | Medium | `payments.ts:13-24` / `M3EPaymentsScreen.kt:451,606,613`. ✅ `FormattingParity.fmtAmt` emits `₹` + en-IN grouping, no space/`.00`; payments UI call sites still need to call it |
| ✅ D-083 (FIXED) | Time parsing | One parser, bare "08:00" treated as 24 h | Three entry points; VIT afternoon heuristic can shift 12 h | Medium | `timeUtils.ts:11-72` / `TimeMath.kt:14-94`. ✅ `TimeMath.parseTimeToMinutes` mirrors the web parser; legacy call sites still need migrating |
| ✅ D-084 (FIXED) | OS reduced motion | `MotionConfig reducedMotion="user"` + CSS media query | Only in-app `animationsEnabled` toggle | Medium | `app/layout.tsx:136`, `globals.css:726` / `AppState.kt:195` | ✅ `LocalMotionReduced` + platform expect/actual (Android animation scales, iOS `isReduceMotionEnabled`, Desktop false) combined with the existing in-app toggle in `AmazeM3ETheme`; all M3E spring specs and skeleton shimmer collapse when set. |
| ✅ D-085 (FIXED) | Clickable a11y contract | `clickable()` helper sets role/tabIndex/Enter-Space | Plain `Modifier.clickable`, no explicit semantics | Medium | `a11y.ts:17-32` / `Components.kt:206-214` | ✅ `Modifier.clickableA11y` applies Role.Button + focusable + Enter/Space activation (Compose clickable) + label; adopted by surface cards, nav pills, command rows and sheets. |
| ✅ D-086 (FIXED) | Sync sheet park vs cancel | Backdrop/swipe park; X cancels | Conflated; relies on platform gestures | Medium | `SyncNotification.tsx:688-696` / `M3ESyncNotification.kt:504-515` | ✅ Contract: X hides the dialog, then `App.kt` observes the hide while still syncing and calls `cancelSync()`; Android back/Escape calls the same via `closeTopOverlay()`. Dismiss = cancel; Kotlin has no park state |
| D-087 | Toast/tooltip durations | Copy 2000 ms; sync success closes after 1500 ms | Copy 1800 ms; sync closes after 2500 ms | Low | `SyncNotification.tsx:322,386-390` / `M3ESyncNotification.kt:193-198,864-878` | **Precise wiring request (mother-owned file):** in `ui/m3e/components/M3ESyncNotification.kt` change `:227 delay(2500L)` → `delay(com.amazecc.app.shared.ui.m3e.theme.M3EFeedbackDurations.SYNC_SUCCESS_CLOSE_MS)` (and its `// Auto-dismiss 2.5s` comment) and `:950 delay(1800)` → `delay(M3EFeedbackDurations.COPY_TOAST_MS)`. Constants live in `ui/m3e/theme/M3EFeedbackDurations.kt`. |
| ✅ D-088 (FIXED) | Validation copy | "Please enter both your VTOP username and password." | "Please enter both username and password." | Low | `SyncNotification.tsx:202` / `M3ESyncNotification.kt:662` | ✅ M3ELoginScreen validation copy matches the web wording.
| D-089 | Tab transitions | Animated crossfade/scale | `tween(0)` — instantaneous | Low | framer-motion `AnimatePresence` / `App.kt:133-138` | **Precise wiring request (mother-owned file):** in `App.kt:195-203` replace the whole `transitionSpec` tab branch with `if (targetState in tabList && initialState in tabList) com.amazecc.app.shared.ui.m3e.theme.M3EMotion.tabSwitchTransform() else fadeIn() togetherWith fadeOut()`. The ready-made transform (crossfade + 0.98 scale, 180 ms, 0 ms under reduced motion) lives in `ui/m3e/theme/M3EMotion.kt`; no new imports needed in App.kt. |

**Verified parity (cross-cutting):** semester naming, "Sept" month abbreviation, password-visibility
toggle + inline field errors, active tab in memory only, network gated on login. **Kotlin ahead:** one-tap
search clear, haptic feedback — record as intentional enhancements, not drift.

## 3. API endpoints & auth

| ID | Endpoint | API contract | Kotlin client | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| ✅ D-011 (FIXED) | All guarded prefixes (`/api/qbank/upload`, `/api/social*`, `/api/profile*`, `/api/cabshare*`, `/api/koha/patron*`, `/api/faculty/scrape*`, `/api/services/ffcs-match`) require Amaze ID bearer/DPoP | `postAuthorized()` sends only `{cookies, authorizedID, csrf}` in the body, no `Authorization` header; non-200 → `null` | Critical: in REMOTE mode these return 401 → `null` → silent empty UI; in LOCAL/OFFLINE unreachable | Critical (independently verified) | API `src/lib/guard.ts:19-60`, `src/lib/amazeId.ts:9-10`; Kotlin `AmazeClient.kt:384-412`, `SessionManager.kt` | Add token storage, `Authorization` header and explicit "sign in with Amaze ID" result | ✅ Amaze ID bearer/DPoP storage + Authorization header + explicit auth-required signal.
| ✅ D-012 (FIXED) | `POST /api/services/curves` (cohort stats), `/api/marks/stats`, `/api/marks/tokens` | No callers | High: curve/grade features cannot work | High | API `src/routes/services/curves.ts`; Kotlin `AmazeClient.kt` | ✅ Typed DTOs (`model/CurvesModels.kt`), typed callers routed through the mount list, and a curve-input seam in `domain/MarksPredictor.kt` (`asCurveStats`, `estimateGradeWithCurve`, curve-aware `computeCoursePrediction`). Hono publishes cohort stats at `/api/services/curves`; there is no separate `/api/marks/stats` route. |
| ✅ D-013 (FIXED) | `/api/social/*` (8 sub-routers) | `SocialClient` calls all 8 routes via `AmazeClient.postAuthorizedValues`/`socialGetRaw`; guarded routes still require the D-002 Amaze ID token | Medium: client delivered; no M3E host calls it yet by design (UI deferred) | Medium | API `src/routes/services/social/index.ts:20-27`; Kotlin `state/SocialClient.kt`, `api/AmazeClient.kt` | Wire client from the Social host; guarded routes need D-002 |
| ✅ D-014 (FIXED) | Failover on 5xx/timeout/network **only, never 401/4xx** (`FAIL_STATUSES`) | `kohaShouldFailOver` fails over on any non-2xx and any `success:false`, opposite rule | Medium: authoritative 4xx gets retried against other hosts, burning login attempts | Medium | Web `src/lib/apis/registry.ts:9-10,69-72`; Kotlin `AmazeClient.kt` | ✅ Predicate now fires only for status 0 (transport/timeout) or 500/502/503/504; 401/4xx and 2xx-with-`success:false` are answers that stop the chain in both `kohaWithFailover` and `mountedPost`. Covered by `KohaFailoverTest`. |
| ✅ D-015 (FIXED) | Declarative mount list `cache → hono → legacy → unicc`, override via `NEXT_PUBLIC_API_MOUNTS`, per-op auth selection | Single base URL from `KEY_HONO_API_URL`; no mount list or selector | Medium: no failover, no target picker | Medium | Web `src/lib/apis/spec.ts:68-85`, `README.md:32-40`; Kotlin `AmazeClient.kt` | ✅ Declarative `ApiMounts` registry (`cache → hono → legacy → unicc` + CUSTOM) in `domain/ApiTarget.kt` with per-op/per-auth route resolution; `AmazeClient.mountedPost` tries candidates in order with the D-014 failover rule. `NEXT_PUBLIC_API_MOUNTS` env override is not applicable to a mobile client; the existing ApiTarget picker supplies the preferred mount. Covered by `ApiMountsTest`. |

**Native OAuth client registration (D-002 operator action).** The device-code grant is opt-in per
client on `auth.amazecc.com` (see `amazecc-auth/scripts/register-client.mjs` and the operator SQL
procedure in AmazeCC-API `docs/01-authentication/amaze-id-migration.md`; public clients such as
Kotlin/Flutter are plain `INSERT`s). Register a public client — suggested id `amazecc-kotlin` — with
`token_endpoint_auth_method = none`, `grant_types` including `urn:ietf:params:oauth:grant-type:device_code`
and `authorization_code` (+ `refresh_token`), `redirect_uris` including the app's native handler
`amazecc://auth/callback` (or a loopback `http://127.0.0.1:<port>/callback`, which the auth server
explicitly allows), `dpopBoundAccessTokens = 0` so a plain `Bearer` token is issued, `skip_consent`
as desired, and scopes `openid profile email offline_access`. Enter that client id / redirect URI in
the Amaze ID sign-in screen. Automatically receiving the redirect would additionally require an
`amazecc://auth/callback` `<intent-filter>` in `androidApp/src/main/AndroidManifest.xml` plus
`MainActivity` delivery to `AmazeIdAuth.parseCallback`; the current build uses honest manual paste
instead, so neither is required for the flow to work.

### 3b. Endpoint mapping breakage (mostly masked by LOCAL/cache fallback)

> These are calls to paths the current Hono API does not expose. They appear to work on devices with
> a live VTOP session because `AmazeClient` falls back to the on-device scraper or local cache
> (`AmazeClient.kt:967-982,1012-1019,1049-1056`). They fail outright in pure REMOTE mode, and for
> features with no scraper fallback they fail regardless of mode.

| ID | Kotlin call | Current API reality | Severity | Evidence | Fix |
|---|---|---|---|---|---|
| ✅ D-090 (FIXED) | `postAuthorized("marks")`, `("timetable")` | No such routes — both embedded in `/api/attendance` (`attendance.ts:287,429`) | High (verified) | Kotlin `AmazeClient.kt:1008,1045`; API `routes/remote/index.ts:30-48` | Decode attendance response into marks/timetable, drop the calls | ✅ Decode attendance (`attRes`/`marksRes`) instead of phantom marks/timetable routes.
| ✅ D-091 (FIXED) | `postAuthorized("lms-data")` with cookies | Requires `{username,pass}`, returns bare `Assignment[]` not `LMSRes` | High | Kotlin `:1827`; API `routes/remote/lms-data.ts:156-164` | Send credentials; decode array | ✅ LMS path now sends Moodle creds and decodes the bare Assignment array.
| ✅ D-092 (FIXED) | `/api/events`, `/events/preview`, `/events/register`, `/events/login`, `events/profile` | Events router not mounted at all (README "NOT ported") | High (verified) | Kotlin `:1899,1936,1970,2019,2064`; API `app.ts:61-71` | Remove/route to legacy host; Events tab otherwise blank | ✅ Dropped all unmounted /api/events* proxy calls; cached/local only.
| ✅ D-093 (FIXED) | `GET /api/clubs/details` | Not mounted; only `/api/club-admin/details`, `/api/admin/clubs` | High | Kotlin `:2076`; API `club-admin/index.ts:22` | Repoint or remove | ✅ Dropped unmounted /api/clubs/details; no public list endpoint exists.
| ✅ D-094 (FIXED) | `kohaGet /koha/search`, `/koha/detail`, `kohaPost /koha/renew` | No koha router mounted | High | Kotlin `:1541,1554,1577`; API `app.ts:61-71` | Repoint to legacy host | ✅ Koha probes the legacy host first; Hono mounts no koha router.
| ✅ D-095 (FIXED) | `GET /api/buses` | No public buses GET (only admin POST) | High | Kotlin `:1676`; API `admin/index.ts:43` | Repoint to legacy/cache | ✅ Buses now serve the persisted cache only (no public GET).
| ✅ D-096 (FIXED) | `GET /api/faculty/schools`, `POST /api/faculty/scrape` | Live only under `/api/services/faculty/*` (no `/api/*` alias) | High | Kotlin `:2569,2583`; API `routes/services/faculty.ts:203,273` | Prefix `/api/services/` | ✅ Faculty calls repointed to /api/services/faculty/*.
| ✅ D-097 (FIXED) | `transport/register` submission | `/api/transport` is read-only | High | Kotlin `:1700`; API `routes/remote/transport.ts:49-120` | Remove action or add API support | ✅ Removed transport/register write; API is read-only.
| ✅ D-098 (FIXED) | `postAuthorized("qcm")` | No route; QCM lives in `/api/ffcs section=qcm` and returns objects for `semesters` | High | Kotlin `:1875`; API `routes/remote/ffcs.ts:256-261,338,352` | Repoint + fix DTO (`List<String>` → objects) | ✅ QCM repointed to /api/ffcs section=qcm; semester options decode as objects or strings.
| ✅ D-099 (FIXED) | `getGrades()` decodes `{effectiveGrades}` | `/api/grades` returns `{overall, semesters}` | High | Kotlin `:2744`, `GradesModels.kt:210-215`; API `grades.ts:195-205` | Decode `AllGradesRes` | ✅ getGrades decodes {overall,semesters} and projects into SemesterGradesRes.
| ✅ D-100 (FIXED) | `hostel-counselling`, `registration-schedule`, `project`, `bonafide`, `e-transcript`, `additional-learning` | No routes; available through `POST /api/bulk` or `/api/ffcs section=registration` | High | Kotlin `:1196,2206,2669,2764,2769,2774`; API `bulk.ts:11-22` | Use bulk | ✅ Hostel counselling/registration via /api/bulk; wishlist via /api/ffcs; rest report unavailable.
| ✅ D-101 (FIXED) | `student`, `profile-images`, `credentials`, `ept-schedule`, `university-day`, `bank-info`, `dayboarder`, `apaarid` | No routes; all folded into `/api/whoami` (already used by `getMe()`) | High | Kotlin `:2128-2270`; API `whoami.ts:186-280` | Delete per-endpoint calls | ✅ Per-endpoint identity calls replaced by whoami-backed slices.
| ✅ D-102 (FIXED) | Cabshare auth/search/my-trips/match | Wrong method + VTOP body instead of DPoP Bearer (search POST hits *create*) | High | Kotlin `:1716-1799`; API `cabshare/{auth,trips,trips-me,match}.ts` | Reimplement against DPoP contract (see D-011) | ✅ Cabshare reimplemented with bearer auth, correct methods/body; explicit auth-required result.
| ✅ D-103 (FIXED) | QBank upload sends `link`, `type` | API expects `fileUrl`, `paperType?` | Medium | Kotlin `:2532-2537`; API `qbank/public.ts:86-104` | Rename fields | ✅ QBank upload sends fileUrl/paperType.
| ✅ D-104 (FIXED) | `getCabHubs()` returns hardcoded list | `GET /api/cabshare/hubs` exists but is never called | Medium | Kotlin `:1723-1725`; API `cabshare/hubs.ts:13` | Call endpoint | ✅ getCabHubs calls GET /api/cabshare/hubs.
| ✅ D-105 (FIXED) | Legacy alias fallbacks `all-grades`, `schedule`, `library-due`, `feedback-status` | No routes; wasted 404s before canonical branch | Medium | Kotlin `:1105,1238,1521,2707` | Drop fallbacks | ✅ Dropped all-grades/schedule/library-due/feedback-status alias fallbacks.
| ✅ D-106 (FIXED) | `QBankPaper(link,type)` decode of `papers_archive` | Missing fields throw `MissingFieldException` → papers list always fails | High | Kotlin `MiscModels.kt:497-501`; API `qbank/public.ts:29-32` | See D-016 (fix is the same) | ✅ QBankPaper made tolerant with source/file_url/source_type aliases.
| ✅ D-107 (FIXED) | `AttendanceSyncResponse` expects top-level fields | API returns `{attRes, marksRes}`; `marksRes` may be a string on failure → whole decode throws | Medium | Kotlin `:119-128,951-957`; API `attendance.ts:308,429` | Make DTO tolerant | ✅ AttendanceSyncResponse tolerates string marksRes and nested attRes.semester.
| ✅ D-108 (FIXED) | New service endpoints unused | `/api/services/enroll`, `/curves`, `/marks/tokens`, `/remote/bulk`, `/course-page`, `/vitol-data` have no Kotlin callers | Medium | API `enroll.ts`, `curves.ts`, `marks-tokens.ts`, `bulk.ts` | Needed for D-005/D-012 | ✅ Added callers for enroll/curves/marks-tokens/course-page/vitol-data/bulk.

## 4. Data model contracts

| ID | Domain | TS contract | Kotlin DTO | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| ✅ D-016 (FIXED) | QBank paper | `source_id`, `title`, `file_url`, `source_type`, `exam_semester`, `exam_year`, `course_code` | `QBankPaper{title, link, type}` | High: `file_url`→`link` and `source_type`→`type` renamed, so links/types always empty; 5 fields lost | High | Web `src/types/qbank.types.ts:6-14`; API `src/routes/qbank/public.ts:29-32`; Kotlin `model/MiscModels.kt:497-501`, `AmazeClient.kt:1846-1857` | Add `@SerialName` aliases + missing fields | ✅ QBankPaper fields with @SerialName aliases and defaults.
| ✅ D-017 (FIXED) | Library dues | `{library:{books:[{title,author,isbn,dueAmount,daysOverdue}], tables, keyValuePairs}}` | `LibraryRes` expects flat `booksIssued/searchResults/total`, `fineAmount: String?` | High: nested payload decodes to empty books; `daysOverdue` number would throw | High | API `src/routes/remote/finance.ts:317-366,429,443-447`; Kotlin `model/MiscModels.kt:224-244`, `AmazeClient.kt:1519-1522` | Mirror nested shape + numeric types | ✅ LibraryRes mirrors nested library.books with numeric-tolerant daysOverdue/dueAmount.
| ✅ D-018 (FIXED) | EventHub registration | All fields optional in `EventHubRegistration` | `eid/title/location` non-null with no defaults | High: one missing field throws `MissingFieldException`, dropping entire response | High | Web `src/types/data/eventhub.ts:35-47`; Kotlin `model/EventHubModels.kt:28-33` | Make fields nullable/defaulted; add `registrationDate`, `certificateEligible` | ✅ EventHub registration fields optional/defaulted; registrationDate + certificateEligible added.
| ✅ D-019 (FIXED) | CGPA | `creditsRequired`, `creditsEarned`, `cgpa`, `nonGradedRequirement` | Only `creditsEarned`, `cgpa` | Medium: two fields dropped | Medium | Web `src/types/data/marks.d.ts:25-37`; Kotlin `model/MiscModels.kt:42-45` | Add fields | ✅ CGPA creditsRequired + nonGradedRequirement added.
| ✅ D-020 (FIXED) | Hostel leave | `leaveId`, `remarks` | Both missing | Medium: no per-record identity or remarks | Medium | Web `src/types/data/hostel.d.ts:9-18`; Kotlin `model/HostelModels.kt:6-13` | Add fields | ✅ LeaveItem leaveId + remarks added.
| ✅ D-021 (FIXED) | Transport | `programme`, `fpReference`, `paymentStatus`, `pageCsrf` | Missing | Medium: registration details invisible | Medium | API `src/routes/remote/transport.ts:93-120`; Kotlin `model/MiscModels.kt:344-357` | Add fields | ✅ Transport programme/fpReference/paymentStatus/pageCsrf added.
| ✅ D-022 (FIXED) | Payments | rich dues/receipts/ledger/USD ledger shapes | Manual lossy `JsonObject` projection keeps only balance | Medium: payment history, refunds, invoices, USD ledger dropped | Medium | Web `src/types/payments.ts:11-53`; Kotlin `AmazeClient.kt:1438-1485`, `model/MiscModels.kt:204-221` | Replace manual parse with typed DTOs | ✅ Finance sections decoded with typed DTOs (receipts, refunds, INR/USD ledgers).
| ✅ D-023 (FIXED) | QBank question | `source_type?`, `exam_year?`, `image_url?` | Missing | Medium: images/year metadata unavailable | Medium | Web `src/types/qbank.types.ts:16-28`; Kotlin `model/MiscModels.kt:477-486` | Add fields | ✅ QBank question source_type/exam_year/image_url added.
| ✅ D-024 (FIXED) | Student profile identity | `registerNo` alias; web accepts `registerNumber`/`registrationNumber` | Only `applicationNumber` | Medium: empty regNo when alias used | Medium | API `src/lib/parsers/student-profile.ts:20,69,145`; Kotlin `model/MiscModels.kt:583-612` | Add aliases | ✅ StudentProfile accepts registerNo/registerNumber/registrationNumber aliases.
| ✅ D-025 (FIXED) | EventHub event | `registeredDetails?`, `isPastEvent?` | Dropped | Low/Medium | Web `src/types/data/eventhub.ts:1-12`; Kotlin `model/EventHubModels.kt:7-17` | Add fields | ✅ EventHubEvent registeredDetails + isPastEvent added.
| ✅ D-026 (FIXED) | Grade ordinal / circular title | `GradeItem.slNo`, `CircularsResponse.title` | Missing | Low | Web `src/types/data/allgrades.d.ts:34`, `src/lib/storage.ts:215-219`; Kotlin `GradesModels.kt:36-58`, `ArrearModels.kt:45-50` | Add fields | ✅ GradeItem.slNo + CircularsRes.title added.
| ✅ D-027 (FIXED) | QBank courses | API returns `{data:[{code,title}]}` | DTO expects `{courses:[…]}` | Low (latent — appears unused) | API `src/routes/qbank/public.ts:12-14`; Kotlin `model/MiscModels.kt:575-580` | Add `@SerialName("data")` | ✅ QBankCoursesRes accepts the API {data:[...]} shape.

## 5. Sync, caching & offline

| ID | Behavior | Web | Kotlin | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| ✅ D-028 (FIXED) | Sync op registry with `auth`/`critical`/`cost` metadata, per-op enable overrides, custom modes (max 20 steps) | Hard-coded `SyncModule` set + profiles; no op metadata | Medium: cannot express new ops; overrides/custom modes diverge | Medium | Web `src/lib/sync-engine/operations.ts`, `sync-modes.ts:184-227,308-483`; Kotlin `state/SyncEngine.kt:25-66,346-442` | Move to op registry |
| ✅ D-029 (FIXED) | Progress sheet: per-op deltas, holds sign-in/VTOP-down open, dismiss cancels all, single-module retry, per-op last-synced | Kotlin has live module states + cancel via NetworkGate, but no per-op progress deltas or single-module retry | Medium | Medium | Web `src/lib/sync-engine/sync-session.ts:183-238`; Kotlin `state/SyncEngine.kt`, `ui/m3e/components/M3ESyncNotification.kt` | Add retry affordance + last-synced display |
| ✅ D-030 (FIXED) | Auto-sync 15/30/60m with cadence gate, paused while reloading | `SyncScheduler` light/full alarms with daily/weekly rules | Medium: different cadence model | Medium | Web `src/components/custom/Main.tsx:278-311`; Kotlin `state/SyncScheduler.kt` | Reconcile cadence configuration |
| ✅ D-031 (FIXED) | Foreground steps sequential with critical abort; background concurrent | Kotlin runs most modules in one `supervisorScope` | Medium: no critical-abort semantics | Medium | Web `src/lib/sync-engine/index.ts:230-311`; Kotlin `state/AppState.kt:loadAllData` | Introduce op tiers |

## Verification log

Independent spot-checks performed directly against both source trees (not delegated):

- **V-001 (attendance percentage parsing):** confirmed drift → **D-001**. Web strips `%` and accepts
  a number; Kotlin calls `toDoubleOrNull()` directly.
- **V-002 (attendance status + aggregate formula):** verified **no drift**. Web
  `src/lib/attendanceSummary.ts:32-55` and Kotlin `domain/Projections.kt:195-224` both use an
  unweighted sum of attended/total, `Safe >= target+5`, `Warning >= target`, otherwise `Critical`,
  `N/A` when total <= 0. Kotlin additionally exposes `hasData`, which is additive.
- **V-003 (grade ladder):** independently confirmed **D-005**. Web `gradeBoundaries.ts:74-82` has
  D≥55/E≥50/F<50; Kotlin `MarksPredictor.kt:344-351` has D≥50/E≥40.
- **V-004 (API guard + auth):** independently confirmed **D-011**. API `guard.ts:19-60` requires
  Amaze ID on cabshare/social/profile/etc.; Kotlin `AmazeClient.kt:384-412` sends no bearer header.
- **V-005 (attendance & marks DTOs):** independently verified **no remaining drift** in those two
  mirrors after the null-tolerant serializers and additive fields.
- **V-006 (Kotlin reachable stubs):** independently confirmed **D-033** (`M3EStateBridge.kt:796-819`
  hardcodes `requiredFatMark=40.0`, `tone=GOOD`, `predictedGpa=8.85`, grade `"A"`) and **D-034**
  (`:332-343` hardcodes `75.0` instead of `AppState.effectiveAttendanceTarget()`).
- **V-007 (dead CTA dialogs):** independently confirmed **D-045**. `M3ETasksScreen.kt:107` emits
  `OpenCreateDialog`; a directory-wide grep for `isCreateDialogOpen` finds matches **only** in
  `M3EStateBridge.kt` (`:1165,1229,1240-1241,1560,1601,1614-1615`) — neither screen reads the flag.
- **V-008 (endpoint breakage):** independently confirmed **D-090/D-092**. API
  `routes/remote/index.ts:30-48` registers no `marks`/`timetable` routes, and `app.ts:61-71` mounts no
  events router; Kotlin `AmazeClient.kt:1008,1045,1899` calls all three.
- **V-009 (parity controls):** semester naming, "Sept" abbreviation, active-tab in-memory behavior,
  login gating and password-visibility toggle verified **no drift**; search-clear and haptics are
  Kotlin-only enhancements.

## Fix order (updated as audits land)

1. **D-090–D-108** — endpoint mapping breakage in REMOTE mode (many core screens 404 today).
2. **D-011 / D-002 / D-102** — Amaze ID auth (blocks Cab Share, Social, QBank upload, profile, patron lookups).
3. **D-005 / D-012 / D-108** — grade ladder v5.0 + cohort curves (wrong grades/GPA today).
4. **D-043–D-046** — missing search/semester actions, dead create dialogs, inconsistent Profile entry.
5. **D-016–D-018 / D-106** — QBank papers, library dues, EventHub registrations (broken/missing data).
6. **D-001, D-019–D-027** — remaining parsing/model drift.
7. **D-032, D-035–D-042** — classic home, no-op controls, fake diagnostics, settings reachability.
8. **D-069–D-073, D-079–D-086** — navigation/back/deep links, push prompt, sheets, formatting.
9. **D-006 / D-007 / D-003 / D-060 / D-064–D-065** — social protocol, task editor, FFCS depth (largest feature work).
10. **D-014–D-015, D-028–D-031, D-047–D-068, D-074–D-078, D-087–D-089** — sync/mount alignment + interaction polish.
11. **D-008–D-010** — privacy mode, scanner, shortcuts.
