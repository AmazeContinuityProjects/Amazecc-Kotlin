# Web ↔ Kotlin parity drift register

**Goal:** record every place where the Kotlin app no longer behaves like the current web app
(`../AmazeCC`) and current API (`../amazecc-api-hono`), then fix them one by one.

**Scope:** all app layers — navigation/screens, data contracts, API endpoints, sync/state, settings,
UI behavior. **Excluded:** the local VTOP scraper internals (`vtop/VtopDataSource`, `VtopEngine`,
`VtopScripts`); the app may fetch the same data differently on-device, but that is out of scope here.

**Baseline at time of writing:**
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL.
- `:shared:jvmTest --tests '*ApiContractParityTest'` — 4/4 pass.
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
| D-002 | `/auth/callback/` Amaze ID OIDC redirect + sign-in with target-server picker | No Kotlin route; login is VTOP-only | Critical: guarded endpoints are unreachable; web sign-in flow has no equivalent | Critical | Web `src/app/auth/callback/page.tsx:1-58`; Kotlin `ui/screens/LoginScreen.kt`, `M3ELoginScreen.kt` | Decide Amaze ID support; add token storage + bearer/DPoP header |
| D-003 | `/friend-finder/`, `/share/ffcs/` (server-side classmates, share links) | No dedicated screen (social is local-only) | High: whole feature absent in Kotlin | High | Web `src/app/friend-finder/page.tsx`, `src/app/share/ffcs/page.tsx`; Kotlin `ui/screens/SocialScreen.kt:62-63` | Port friend-finder op + share-code reader |
| D-004 | Dynamic navigation registry with surfaces / pinnable / visibleWhen | `Screen` enum + manual backstack | Medium: no command-palette/app-library surface model; hosteller-only gating differs | Medium | Web `src/lib/navigation/registry.tsx:92-871`; Kotlin `state/AppState.kt:57-64`, `App.kt` | Map registry metadata onto Screen enum or add surface metadata |

## 2. Feature behavior

| ID | Feature | Web behavior | Kotlin behavior | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| D-001 | Attendance % parsing | `parseFloat(raw.attendancePercentage.replace("%", ""))` — strips a `%` suffix and also accepts a JSON number | `course.attendance?.attendancePercentage?.toDoubleOrNull()` — a value like `"89.50%"` yields `null`/0 | Kotlin can show 0% or drop a course from aggregate stats where web shows the real percentage | High | Web `src/lib/courseNormalizer.ts:299-304`; Kotlin `state/AcademicDerivers.kt:129-130` | Strip `%` and parse leniently in `percentOf`; mirror web fallback order (string → number → attended/total) |
| D-005 | Grade ladder v5.0: S≥90 A≥80 B≥70 C≥60 **D≥55 E≥50 F<50**, plus cohort curve (S at mean+1.5σ, MIN_CURVE_SAMPLE=5, CONFIDENT=15) | Kotlin bands S90/A80/B70/C60/**D50/E40**, score-only `estimateGrade`, no curve data | High: 50–54 shows D/6 points instead of E/5; 40–49 shows E/5 instead of F/0; no relative grading | High (independently verified) | Web `src/lib/gradeBoundaries.ts:67-82,84-124`; Kotlin `domain/MarksPredictor.kt:343-364`; API `src/lib/curves.ts` | Replace bands; add curve model + `/api/services/curves` |
| D-006 | Social handles, pair claims, grant visibility, Classes Together, common free grid | `SocialScreen` has 4 legacy local tabs (Friends/Groups/Common Slots/Share Schedule) | High: server-backed feature entirely absent | High | Web `src/lib/social/client.ts:119-157`; API `src/routes/services/social/*`; Kotlin `ui/screens/SocialScreen.kt:62-63,137-154` | Port social op protocol |
| D-007 | Share codes `v6|expires|…`, `#s=<hash>`, `amz-profile-` | Local format `name|reg|slots` | High: QR/timetable sharing incompatible in both directions | High | Web `src/lib/socialUtils.ts:114-115,139,167-169`; Kotlin `utils/SocialUtils.kt:69-106` | Add v5/v6 reader + compressed URL support |
| D-008 | Privacy mode blurs grades/attendance/marks/fees app-wide | No privacy setting | Medium: privacy expectation differs | Medium | Web `src/store/settingsAtoms.ts:16-17`, `ProfilePage.tsx:1648-1662`; Kotlin `repository/SettingsManager.kt` | Add privacy mode key + blur modifiers |
| D-009 | Document scanner (edge detection → OCR → editor → PDF, web workers) | Absent | Medium: feature missing | Medium | Web git `3da693ff`, `22a9e250`; `src/lib/scanner/*` | Scope separately (camera/OCR heavy) |
| D-010 | `g` then letter + `/` keyboard shortcuts | No equivalent | Low/Medium: power-user navigation differs | Low | Web git `fe1db7df` | Add key handling in App shell |
| D-032 | Full widget dashboard (classic home) | `HOME_LEGACY` maps to the same simplified home as `HOME`; `WidgetDashboard` has zero callers | High: entire homepage layout/feature missing but looks "routed" | High | Kotlin `App.kt:184`, `AppState.kt:75-83`, `ui/components/DashboardWidgets.kt:101` | Wire classic home or delete the route |
| D-033 | Grade/GPA predictor with curve inputs and FAT solver | `GPA_PREDICTOR` shows hardcoded `8.85 / 40.0 / GOOD` for every course | High: fake output on a high-trust screen | High | Kotlin `M3EStateBridge.kt:796-819` | Drive from real marks + curve (after D-005/D-012) |
| D-034 | User-configurable attendance target (bus subscriber 85, custom override) | UI hardcodes 75; `AppState.effectiveAttendanceTarget()` has no live consumer | High: wrong Safe/Warning/Critical classification for bus users | High | Kotlin `M3EStateBridge.kt:336-342,380-395`; `AppState.kt:189-190` | Feed the effective target into every attendance screen |
| D-035 | Working actions for syllabus, faculty email/timetable, hostel leave/warden, cab contact, circular/assignment open, GitHub, exam export | All wired to `{}`/`Unit` no-ops in reachable hosts | High: 10+ visible controls do nothing | High | Kotlin `M3EStateBridge.kt:605,693-694,1077-1078,1247,894-895`; `M3ECampusLegacyBridge.kt:557,666,878`; `M3EExamScheduleHost.kt:117-118` | Wire or hide each control |
| D-036 | Offline/cache status banner | `isUsingCachedData`/`offlineCacheStatus` written in 14 places, never collected by a composable | Medium: user cannot tell data is cached/stale | Medium | Kotlin `AmazeClient.kt:750,778,810,...`; no UI collector | Add banner reading the flag |
| D-037 | Post-onboarding display/dashboard/nav/academics/credentials/data-sync/danger settings | Configured in onboarding or only in unrouted legacy settings (UI scale, CGPA hidden, widget order, nav pinning, GPA goal, custom target, manual captcha, module toggles, export/import/clear) | Medium: settings are one-time-only or unreachable | Medium | Kotlin `OnboardingScreen.kt:282-458`; `ui/screens/settings/*`; `SyncEngine.kt:697-724` only used by dead `SyncSettingsDialog.kt` | Port settings entry points into M3E Settings |
| D-038 | Live faculty directory + free-slot timetable | Routed `M3EFacultyScreen` derives a short list from proctor/course faculty; full legacy `FacultyInfoScreen` is unrouted | Medium/High | Medium | Kotlin `M3EStateBridge.kt:616`, `ui/screens/FacultyInfoScreen.kt:86,107,346` | Route the live faculty screen |
| D-039 | Cab-share trip creation / join / match actions | View-only; `cabCreateTripNew`, `cabSearchTripsNew`, `cabHandleMatchAction` have no M3E actions | Medium | Medium | Kotlin `AppState.kt:2841-2921`, `M3EStateBridge.kt:1156-1259` | Add compose actions |
| D-040 | Exam hall ticket + calendar export | `hallTicketUrl = null`, `ExportCalendar` no-op | Medium | Medium | Kotlin `M3EExamScheduleHost.kt:98-102,117-118` | Wire from API |
| D-041 | Hostel real data vs placeholders | Fallbacks `Q Block`, `514`, `CRCL Caterers`, `Dr. Ramesh K`, phone number shown as last resort | Medium | Medium | Kotlin `M3EStateBridge.kt:986-1001` | Show "not available" instead of fabricated values |
| D-042 | Dead duplicate M3E host overloads + unreachable legacy UI layer | Two same-package implementations per feature; classic widgets/settings/sync dialogs unrouted | Low (maintenance hazard, helps explain drift) | Low | Kotlin `M3EStateBridge.kt:1262,1315,1464,1553,1625,1835` | Delete unreachable layer once parity is settled |

### 2b. Interaction-level drift (per control)

| ID | Control / interaction | Web behavior | Kotlin behavior | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| D-043 | Attendance search field | Present | Host builds `searchQuery`/`UpdateSearch` but `M3EAttendanceScreen` has no field and never emits it | Search unreachable | High | Kotlin `M3EStateBridge.kt:325,372-377,420`; `M3EAttendanceScreen.kt:97` | Add search field + emit action |
| D-044 | Academics hub: semester switch, search, refresh, course→detail | All present | Host wires all four actions; screen emits none; local filter tokens differ ("TH"/"LA" vs "Theory"/"Lab") | Four interactions missing + inconsistent empty lists | High | Kotlin `M3EStateBridge.kt:484-486,526-532`; `M3EAcademicsScreen.kt:108,114-118` | Emit actions; unify tokens |
| D-045 | "Add task" and "Create ride" buttons | Open a working create dialog | Both set a dialog flag, but neither screen renders a dialog or handles close | Two dead primary CTAs | High | Kotlin `M3ETasksScreen.kt:107`; `M3EStateBridge.kt:1614`; `M3ECabShareScreen.kt:107`; `M3EStateBridge.kt:1247` | Render dialogs + wire create actions |
| D-046 | Profile interactions depend on entry point | Consistent | Tab entry (`M3EProfileScreen` bridge) drops Set residence / Set bus / contacts / acknowledgement docs / identity warm-up present in direct `M3EProfileHost` | Same screen behaves differently | High | Kotlin `M3EProfileHost.kt:52-54,295-305`; `M3EStateBridge.kt:1625-1832` (`:1826 else -> Unit`) | Unify on one implementation |
| D-047 | Pull-to-refresh | Available where offered | No pull-to-refresh anywhere; Calendar shows "pull to retry" message anyway | Misleading affordance | Medium | Kotlin `M3ECalendarScreenHost`; `M3ECalendarScreen.kt:206` | Implement or change copy |
| D-048 | Task kind / overdue derivation | Task model carries real kind/status/reminders | Every task forced `kind=DIGITAL_ASSIGNMENT`, `isOverdue=false`; IN_PROGRESS filter can never match | Wrong task states | Medium | Kotlin `M3EStateBridge.kt:1567,1573` | Derive from real model |
| D-049 | Diagnostics status | Real health check | Initial text `"All systems operational (Ping: 42ms)"` shown before any check | Fabricated status | Medium | Kotlin `M3EStateBridge.kt:1844`; `M3ESettingsScreen.kt:663` | Initialize to "Not checked" |
| D-050 | Refresh buttons loading state | Disabled while refreshing | Never disabled during loading | Duplicate requests | Low | Kotlin `M3EHomeScreen.kt:88`, `M3EStateBridge.kt:301` | Disable while loading |
| D-051 | Onboarding back handling | Intercepted | No `AppBackHandler` in onboarding | System back exits flow | Low | Kotlin `OnboardingScreen.kt:147-463` | Add back handler |
| D-052 | Command-palette "Esc" chip | Closes palette | Clears the query instead | Minor semantic mismatch | Low | Kotlin `CommandPalette.kt:264-274` | Match web semantics |
| D-053 | Fresher welcome content | Dynamic | Hardcoded prose | Static content | Low | Kotlin `M3EStateBridge.kt:708-712` | Wire content source |

**Verified no drift (interaction level):** QBank workspace (all tabs, filters, timer, flags, upload, SWOT, review), library KOHA search/renew/login, transport call/WhatsApp/tracking, events registration/preview/payment, club detail links, tasks toggle/delete/search/filter, sync overlay (cancel, console, inline login, minimised pill) — all wired to real actions with loading/error states.

### 2c. Web interactions with no Kotlin equivalent (or materially different)

| ID | Interaction | Web | Kotlin | Severity | Evidence (web) | Fix |
|---|---|---|---|---|---|---|
| D-054 | Guest/marketing landing + entry intents | Static `GuestExperience`, section scroll nav, "Get Started"/"Try demo"/FFCS links, `?signin=1`, `?open=friend-finder`, `?add=AMZ-…` | Splash times out straight to Login; no guest surface or deep-link intents | Medium | Web `app/HomeEntryController.tsx:53-127`, `GuestExperience.tsx:18-217`, `Main.tsx:91-205` | Add guest surface or document intentional divergence |
| D-055 | Bottom-nav double tap | Single tap navigates; second tap within window goes to section root with collapse logic | Single tap only | Medium | Web `NavigationTabs.tsx:849-911`, `lib/sectionHome.ts` | Add double-tap-to-root |
| D-056 | Back / overlay stack | Overlays dismiss first LIFO (Escape, system back, swipe, backdrop), then screen history (`popstate`) | `AppBackHandler` basic; no overlay stack | Medium | Web `src/lib/overlayStack.ts`, `useDashboardScreenHistory.ts:93-154`, `BottomSheet.tsx:160-194` | Introduce overlay back stack |
| D-057 | Keyboard shortcuts | `g`+letter, Alt+letters, Alt+B/P/T, `?` help, Ctrl/Cmd+K, `/`, Escape; typing targets excluded | Only Cmd/Ctrl+K | Medium | Web `lib/shortcuts.ts:26-38`, `app/useGlobalShortcuts.ts` | Extend shortcut set (see D-010) |
| D-058 | Sheet dismiss contract | X, swipe-down >120 px, backdrop tap, Escape LIFO, system back, `dismissable=false`; sync sheet parks on backdrop/swipe and only X cancels | Compose dialogs/bottom sheets without unified contract | Medium | Web `shared/BottomSheet.tsx:78-82,147-155,160-194,240-281,305-325,464-467` | Define one Compose sheet contract |
| D-059 | Settings depth | Storage subpage, API target picker, export/import settings, settings search, keyboard-shortcut list, app-icon theme, Pomodoro focus/break/rounds, timetable density/pill style, privacy mode | Most configured only in onboarding or unrouted legacy settings (D-037) | Medium | Web `header/ProfilePage.tsx:1533-2536,2803-2813` | Move into reachable M3E Settings |
| D-060 | Task editing | Kind/status chips, course/component selects, due date, 3 lead reminders + custom, study chunks, estimates, notes, delete confirm | No create dialog at all (D-045); kind/overdue forced (D-048) | High | Web `tasks/TaskEditSheet.tsx:259-655` | Port edit sheet + dialog |
| D-061 | Attendance calendar actions | Download `.ics`, Connect Moodle, academic circulars, programme selector, Today button | Calendar has dropdown/month/filter/refresh only; no .ics/Moodle/circulars | Medium | Web `attendance/CalendarSubpage.tsx:607-643,751-760` | Add actions |
| D-062 | Exam schedule actions | Copy course codes, filters/sort, download image/PDF, per-type PDF/image, Add-to-Calendar `.ics` | Card click/export no-ops (D-040) | Medium | Web `exams/ScheduleDisplay.tsx:217-379` | Wire exports |
| D-063 | Library catalog | Pagination prev/next, keyboard nav, result detail modal | Search + detail, no pagination | Low/Medium | Web `libraries/CatalogSearch.tsx:212-384` | Add pagination |
| D-064 | Social surfaces | Free Now, Pairs, Groups, People, Classes Together, common-free grid, handle QR share/scan, camera scan | 4 legacy local tabs (D-006) | High | Web `social/*`, `friendfinder/FriendFinderTab.tsx` | Port server social |
| D-065 | FFCS planner depth | Generator variants, guided wizard, target locker, social matrix, share links, dossier/HTML/image/PDF/ICS export, friend timetables | Static CSV + generate only (D-038) | High | Web `app/ffcs/*`, `FFCSTimetableTab.tsx:2196-3176` | Scope port separately |
| D-066 | Sync API target switch | Primary/Backup/UNICC selector inside sync sheet | No target picker (D-015) | Medium | Web `shared/SyncNotification.tsx:1338-1353` | Add mount picker |
| D-067 | Connection banner | Offline/server-down copy, "Check now" with spinner + disabled, dismiss | No cache/offline banner (D-036) | Medium | Web `shared/ConnectionBanner.tsx:40-85` | Add banner |
| D-068 | Push notification settings | Test send, class-reminder lead select, quiet hours, custom VAPID key | 3 toggles + test only | Medium | Web `header/PushNotificationManager.tsx:299-507` | Extend settings |

> Note: the web audit found **uncommitted working-tree modifications** in several interaction files
> (`BottomSheet`, `NavigationTabs`, `SocialTab`, `OnboardingIntro`, `GuestExperience`,
> `HomeEntryController`, etc.). Treat those line numbers as the current working tree and re-check
> before implementing against a specific commit.

### 2d. Cross-cutting UX semantics

| ID | Primitive | Web | Kotlin | Drift | Severity | Evidence (web / Kotlin) |
|---|---|---|---|---|---|---|
| D-069 | Push permission prompt | Auto-opens 3 s after mount, re-prompts every 3 days with dedup key | Only from an explicit Settings toggle; no timer/dedup | High | `PushPromptModal.tsx:26-40` / `SettingsScreen.kt:128-131` |
| D-070 | Sheet Escape/back dismissal | LIFO top-only Escape, predictive back, swipe, backdrop | No Escape path on modal sheets; no back on Desktop/iOS | High | `BottomSheet.tsx:184-194`, `overlayStack.ts` / `AppBackHandler.kt:15` |
| D-071 | Back-stack model | Browser history for screens + overlays; survives reload; restores sub-tab; tab switch pushes history | In-memory `backstack`, cleared on top-level switch, loses sub-tab, no Desktop/iOS back | High | `useDashboardScreenHistory.ts:29-144` / `AppState.kt:952-1001` |
| D-072 | Deep links / Web Share Target | `/?tab=`, `/share`, `/share/ffcs`, `/friend-finder`, manifest shortcuts | Only MAIN/LAUNCHER; no URL entry points | High | `public/manifest.json:13-43` / `AndroidManifest.xml` |
| D-073 | Guest/demo entry + sign-in return | Guest landing, `?signin=1` redirect | Splash always → Login | High | `HomeEntryController.tsx:53-125` / `M3ESplashScreen.kt:54-57` (see D-054) |
| D-074 | Skeleton loaders | `PageSkeleton`/`ListSkeleton` with `aria-busy`, shape-preserving | Spinners only, no skeleton primitive | Medium | `PageSkeleton.tsx:12-52` / call-site `CircularProgressIndicator` |
| D-075 | Login username normalization | Force-uppercase + `autoCapitalize=characters` on input/submit | Raw input sent as typed | Medium | `SyncNotification.tsx:199-200,779-784` / `M3ESyncNotification.kt:612-668` |
| D-076 | Login in-flight state | All inputs + submit disabled while logging in | Fields/button remain editable | Medium | `SyncNotification.tsx:785,801,829,836` / `M3ESyncNotification.kt:659-694` |
| D-077 | Password-visibility a11y | `aria-label` Show/Hide password | `contentDescription = null` | Medium | `SyncNotification.tsx:804-810` / `M3ESyncNotification.kt:634-639` |
| D-078 | Tab swipe gesture | Document-wide horizontal swipe with overlay guard | Tap-only navigation | Medium | `primitives/useHorizontalSwipe.ts` / `MainTabPager.kt` |
| D-079 | Formatting: percent | JS `toFixed(2)` | `round` + manual string padding; differs on half cases/large values | Medium | `attendancePredictor.ts:625-626` / `AttendancePredictor.kt:460-472` |
| D-080 | Formatting: bunk/needed counts | Plain `floor`/`ceil` | Epsilon guards `floor(x+1e-9)`/`ceil(x-1e-9)` → off-by-one at exact thresholds | Medium | `attendancePredictor.ts:548,551` / `AttendancePredictor.kt:398,401` |
| D-081 | Formatting: current % source | `attendanceCalculators.ts` uses 1-dp rounded % for safety flag | Uses unrounded % + epsilon | Medium | `attendanceCalculators.ts:53,58` / `AttendancePredictor.kt:103-125` |
| D-082 | Currency formatting | `₹1,240` via `en-IN` normalization | Raw `"₹ 1,240.00"` from API | Medium | `payments.ts:13-24` / `M3EPaymentsScreen.kt:451,606,613` |
| D-083 | Time parsing | One parser, bare "08:00" treated as 24 h | Three entry points; VIT afternoon heuristic can shift 12 h | Medium | `timeUtils.ts:11-72` / `TimeMath.kt:14-94` |
| D-084 | OS reduced motion | `MotionConfig reducedMotion="user"` + CSS media query | Only in-app `animationsEnabled` toggle | Medium | `app/layout.tsx:136`, `globals.css:726` / `AppState.kt:195` |
| D-085 | Clickable a11y contract | `clickable()` helper sets role/tabIndex/Enter-Space | Plain `Modifier.clickable`, no explicit semantics | Medium | `a11y.ts:17-32` / `Components.kt:206-214` |
| D-086 | Sync sheet park vs cancel | Backdrop/swipe park; X cancels | Conflated; relies on platform gestures | Medium | `SyncNotification.tsx:688-696` / `M3ESyncNotification.kt:504-515` |
| D-087 | Toast/tooltip durations | Copy 2000 ms; sync success closes after 1500 ms | Copy 1800 ms; sync closes after 2500 ms | Low | `SyncNotification.tsx:322,386-390` / `M3ESyncNotification.kt:193-198,864-878` |
| D-088 | Validation copy | "Please enter both your VTOP username and password." | "Please enter both username and password." | Low | `SyncNotification.tsx:202` / `M3ESyncNotification.kt:662` |
| D-089 | Tab transitions | Animated crossfade/scale | `tween(0)` — instantaneous | Low | framer-motion `AnimatePresence` / `App.kt:133-138` |

**Verified parity (cross-cutting):** semester naming, "Sept" month abbreviation, password-visibility
toggle + inline field errors, active tab in memory only, network gated on login. **Kotlin ahead:** one-tap
search clear, haptic feedback — record as intentional enhancements, not drift.

## 3. API endpoints & auth

| ID | Endpoint | API contract | Kotlin client | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| D-011 | All guarded prefixes (`/api/qbank/upload`, `/api/social*`, `/api/profile*`, `/api/cabshare*`, `/api/koha/patron*`, `/api/faculty/scrape*`, `/api/services/ffcs-match`) require Amaze ID bearer/DPoP | `postAuthorized()` sends only `{cookies, authorizedID, csrf}` in the body, no `Authorization` header; non-200 → `null` | Critical: in REMOTE mode these return 401 → `null` → silent empty UI; in LOCAL/OFFLINE unreachable | Critical (independently verified) | API `src/lib/guard.ts:19-60`, `src/lib/amazeId.ts:9-10`; Kotlin `AmazeClient.kt:384-412`, `SessionManager.kt` | Add token storage, `Authorization` header and explicit "sign in with Amaze ID" result |
| D-012 | `POST /api/services/curves` (cohort stats), `/api/marks/stats`, `/api/marks/tokens` | No callers | High: curve/grade features cannot work | High | API `src/routes/services/curves.ts`; Kotlin `AmazeClient.kt` | Add typed client functions + DTOs |
| D-013 | `/api/social/*` (8 sub-routers) | No callers | High: server social absent | High | API `src/routes/services/social/index.ts:20-27` | See D-006 |
| D-014 | Failover on 5xx/timeout/network **only, never 401/4xx** (`FAIL_STATUSES`) | `kohaShouldFailOver` fails over on any non-2xx and any `success:false`, opposite rule | Medium: authoritative 4xx gets retried against other hosts, burning login attempts | Medium | Web `src/lib/apis/registry.ts:9-10,69-72`; Kotlin `AmazeClient.kt:59-61,90-105` | Align failover predicate |
| D-015 | Declarative mount list `cache → hono → legacy → unicc`, override via `NEXT_PUBLIC_API_MOUNTS`, per-op auth selection | Single base URL from `KEY_HONO_API_URL`; no mount list or selector | Medium: no failover, no target picker | Medium | Web `src/lib/apis/spec.ts:68-85`, `README.md:32-40`; Kotlin `AmazeClient.kt:53,236-241` | Add mount registry if multi-host still required |

### 3b. Endpoint mapping breakage (mostly masked by LOCAL/cache fallback)

> These are calls to paths the current Hono API does not expose. They appear to work on devices with
> a live VTOP session because `AmazeClient` falls back to the on-device scraper or local cache
> (`AmazeClient.kt:967-982,1012-1019,1049-1056`). They fail outright in pure REMOTE mode, and for
> features with no scraper fallback they fail regardless of mode.

| ID | Kotlin call | Current API reality | Severity | Evidence | Fix |
|---|---|---|---|---|---|
| D-090 | `postAuthorized("marks")`, `("timetable")` | No such routes — both embedded in `/api/attendance` (`attendance.ts:287,429`) | High (verified) | Kotlin `AmazeClient.kt:1008,1045`; API `routes/remote/index.ts:30-48` | Decode attendance response into marks/timetable, drop the calls |
| D-091 | `postAuthorized("lms-data")` with cookies | Requires `{username,pass}`, returns bare `Assignment[]` not `LMSRes` | High | Kotlin `:1827`; API `routes/remote/lms-data.ts:156-164` | Send credentials; decode array |
| D-092 | `/api/events`, `/events/preview`, `/events/register`, `/events/login`, `events/profile` | Events router not mounted at all (README "NOT ported") | High (verified) | Kotlin `:1899,1936,1970,2019,2064`; API `app.ts:61-71` | Remove/route to legacy host; Events tab otherwise blank |
| D-093 | `GET /api/clubs/details` | Not mounted; only `/api/club-admin/details`, `/api/admin/clubs` | High | Kotlin `:2076`; API `club-admin/index.ts:22` | Repoint or remove |
| D-094 | `kohaGet /koha/search`, `/koha/detail`, `kohaPost /koha/renew` | No koha router mounted | High | Kotlin `:1541,1554,1577`; API `app.ts:61-71` | Repoint to legacy host |
| D-095 | `GET /api/buses` | No public buses GET (only admin POST) | High | Kotlin `:1676`; API `admin/index.ts:43` | Repoint to legacy/cache |
| D-096 | `GET /api/faculty/schools`, `POST /api/faculty/scrape` | Live only under `/api/services/faculty/*` (no `/api/*` alias) | High | Kotlin `:2569,2583`; API `routes/services/faculty.ts:203,273` | Prefix `/api/services/` |
| D-097 | `transport/register` submission | `/api/transport` is read-only | High | Kotlin `:1700`; API `routes/remote/transport.ts:49-120` | Remove action or add API support |
| D-098 | `postAuthorized("qcm")` | No route; QCM lives in `/api/ffcs section=qcm` and returns objects for `semesters` | High | Kotlin `:1875`; API `routes/remote/ffcs.ts:256-261,338,352` | Repoint + fix DTO (`List<String>` → objects) |
| D-099 | `getGrades()` decodes `{effectiveGrades}` | `/api/grades` returns `{overall, semesters}` | High | Kotlin `:2744`, `GradesModels.kt:210-215`; API `grades.ts:195-205` | Decode `AllGradesRes` |
| D-100 | `hostel-counselling`, `registration-schedule`, `project`, `bonafide`, `e-transcript`, `additional-learning` | No routes; available through `POST /api/bulk` or `/api/ffcs section=registration` | High | Kotlin `:1196,2206,2669,2764,2769,2774`; API `bulk.ts:11-22` | Use bulk |
| D-101 | `student`, `profile-images`, `credentials`, `ept-schedule`, `university-day`, `bank-info`, `dayboarder`, `apaarid` | No routes; all folded into `/api/whoami` (already used by `getMe()`) | High | Kotlin `:2128-2270`; API `whoami.ts:186-280` | Delete per-endpoint calls |
| D-102 | Cabshare auth/search/my-trips/match | Wrong method + VTOP body instead of DPoP Bearer (search POST hits *create*) | High | Kotlin `:1716-1799`; API `cabshare/{auth,trips,trips-me,match}.ts` | Reimplement against DPoP contract (see D-011) |
| D-103 | QBank upload sends `link`, `type` | API expects `fileUrl`, `paperType?` | Medium | Kotlin `:2532-2537`; API `qbank/public.ts:86-104` | Rename fields |
| D-104 | `getCabHubs()` returns hardcoded list | `GET /api/cabshare/hubs` exists but is never called | Medium | Kotlin `:1723-1725`; API `cabshare/hubs.ts:13` | Call endpoint |
| D-105 | Legacy alias fallbacks `all-grades`, `schedule`, `library-due`, `feedback-status` | No routes; wasted 404s before canonical branch | Medium | Kotlin `:1105,1238,1521,2707` | Drop fallbacks |
| D-106 | `QBankPaper(link,type)` decode of `papers_archive` | Missing fields throw `MissingFieldException` → papers list always fails | High | Kotlin `MiscModels.kt:497-501`; API `qbank/public.ts:29-32` | See D-016 (fix is the same) |
| D-107 | `AttendanceSyncResponse` expects top-level fields | API returns `{attRes, marksRes}`; `marksRes` may be a string on failure → whole decode throws | Medium | Kotlin `:119-128,951-957`; API `attendance.ts:308,429` | Make DTO tolerant |
| D-108 | New service endpoints unused | `/api/services/enroll`, `/curves`, `/marks/tokens`, `/remote/bulk`, `/course-page`, `/vitol-data` have no Kotlin callers | Medium | API `enroll.ts`, `curves.ts`, `marks-tokens.ts`, `bulk.ts` | Needed for D-005/D-012 |

## 4. Data model contracts

| ID | Domain | TS contract | Kotlin DTO | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| D-016 | QBank paper | `source_id`, `title`, `file_url`, `source_type`, `exam_semester`, `exam_year`, `course_code` | `QBankPaper{title, link, type}` | High: `file_url`→`link` and `source_type`→`type` renamed, so links/types always empty; 5 fields lost | High | Web `src/types/qbank.types.ts:6-14`; API `src/routes/qbank/public.ts:29-32`; Kotlin `model/MiscModels.kt:497-501`, `AmazeClient.kt:1846-1857` | Add `@SerialName` aliases + missing fields |
| D-017 | Library dues | `{library:{books:[{title,author,isbn,dueAmount,daysOverdue}], tables, keyValuePairs}}` | `LibraryRes` expects flat `booksIssued/searchResults/total`, `fineAmount: String?` | High: nested payload decodes to empty books; `daysOverdue` number would throw | High | API `src/routes/remote/finance.ts:317-366,429,443-447`; Kotlin `model/MiscModels.kt:224-244`, `AmazeClient.kt:1519-1522` | Mirror nested shape + numeric types |
| D-018 | EventHub registration | All fields optional in `EventHubRegistration` | `eid/title/location` non-null with no defaults | High: one missing field throws `MissingFieldException`, dropping entire response | High | Web `src/types/data/eventhub.ts:35-47`; Kotlin `model/EventHubModels.kt:28-33` | Make fields nullable/defaulted; add `registrationDate`, `certificateEligible` |
| D-019 | CGPA | `creditsRequired`, `creditsEarned`, `cgpa`, `nonGradedRequirement` | Only `creditsEarned`, `cgpa` | Medium: two fields dropped | Medium | Web `src/types/data/marks.d.ts:25-37`; Kotlin `model/MiscModels.kt:42-45` | Add fields |
| D-020 | Hostel leave | `leaveId`, `remarks` | Both missing | Medium: no per-record identity or remarks | Medium | Web `src/types/data/hostel.d.ts:9-18`; Kotlin `model/HostelModels.kt:6-13` | Add fields |
| D-021 | Transport | `programme`, `fpReference`, `paymentStatus`, `pageCsrf` | Missing | Medium: registration details invisible | Medium | API `src/routes/remote/transport.ts:93-120`; Kotlin `model/MiscModels.kt:344-357` | Add fields |
| D-022 | Payments | rich dues/receipts/ledger/USD ledger shapes | Manual lossy `JsonObject` projection keeps only balance | Medium: payment history, refunds, invoices, USD ledger dropped | Medium | Web `src/types/payments.ts:11-53`; Kotlin `AmazeClient.kt:1438-1485`, `model/MiscModels.kt:204-221` | Replace manual parse with typed DTOs |
| D-023 | QBank question | `source_type?`, `exam_year?`, `image_url?` | Missing | Medium: images/year metadata unavailable | Medium | Web `src/types/qbank.types.ts:16-28`; Kotlin `model/MiscModels.kt:477-486` | Add fields |
| D-024 | Student profile identity | `registerNo` alias; web accepts `registerNumber`/`registrationNumber` | Only `applicationNumber` | Medium: empty regNo when alias used | Medium | API `src/lib/parsers/student-profile.ts:20,69,145`; Kotlin `model/MiscModels.kt:583-612` | Add aliases |
| D-025 | EventHub event | `registeredDetails?`, `isPastEvent?` | Dropped | Low/Medium | Web `src/types/data/eventhub.ts:1-12`; Kotlin `model/EventHubModels.kt:7-17` | Add fields |
| D-026 | Grade ordinal / circular title | `GradeItem.slNo`, `CircularsResponse.title` | Missing | Low | Web `src/types/data/allgrades.d.ts:34`, `src/lib/storage.ts:215-219`; Kotlin `GradesModels.kt:36-58`, `ArrearModels.kt:45-50` | Add fields |
| D-027 | QBank courses | API returns `{data:[{code,title}]}` | DTO expects `{courses:[…]}` | Low (latent — appears unused) | API `src/routes/qbank/public.ts:12-14`; Kotlin `model/MiscModels.kt:575-580` | Add `@SerialName("data")` |

## 5. Sync, caching & offline

| ID | Behavior | Web | Kotlin | Drift | Severity | Evidence | Fix |
|---|---|---|---|---|---|---|---|
| D-028 | Sync op registry with `auth`/`critical`/`cost` metadata, per-op enable overrides, custom modes (max 20 steps) | Hard-coded `SyncModule` set + profiles; no op metadata | Medium: cannot express new ops; overrides/custom modes diverge | Medium | Web `src/lib/sync-engine/operations.ts`, `sync-modes.ts:184-227,308-483`; Kotlin `state/SyncEngine.kt:25-66,346-442` | Move to op registry |
| D-029 | Progress sheet: per-op deltas, holds sign-in/VTOP-down open, dismiss cancels all, single-module retry, per-op last-synced | Kotlin has live module states + cancel via NetworkGate, but no per-op progress deltas or single-module retry | Medium | Medium | Web `src/lib/sync-engine/sync-session.ts:183-238`; Kotlin `state/SyncEngine.kt`, `ui/m3e/components/M3ESyncNotification.kt` | Add retry affordance + last-synced display |
| D-030 | Auto-sync 15/30/60m with cadence gate, paused while reloading | `SyncScheduler` light/full alarms with daily/weekly rules | Medium: different cadence model | Medium | Web `src/components/custom/Main.tsx:278-311`; Kotlin `state/SyncScheduler.kt` | Reconcile cadence configuration |
| D-031 | Foreground steps sequential with critical abort; background concurrent | Kotlin runs most modules in one `supervisorScope` | Medium: no critical-abort semantics | Medium | Web `src/lib/sync-engine/index.ts:230-311`; Kotlin `state/AppState.kt:loadAllData` | Introduce op tiers |

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
