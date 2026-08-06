# Evaluation 00 — Executive Summary

**AmazeCC-Kotlin: Kotlin Multiplatform (Compose) VIT student app — Android + iOS.**

---

## 1. What the app actually is

A student-portal companion: attendance, marks, timetable, grades, CGPA predictor, exam schedules, circulars, calendar, OD tracker, tasks/pomodoro, FFCS course planner, QBank, event hub, club hub, cab share, transport/bus info, hostel (mess/laundry/counselling), library, Moodle, vitol, payments, faculty directory, social/friends with QR schedule sharing, and home-screen widgets. 168 Kotlin files ≈ 2.0 MB, of which ~55% is UI, ~20% state/API, ~15% models/config data (incl. a 2,362-line hardcoded CSV), ~10% platform code.

**Architecture:** singleton `AppState` god-object (2,716 lines) owns ~50 `StateFlow`s + all sync orchestration. `AmazeClient` singleton (1,889 lines) is the HTTP client with ~55 endpoints, most double-gated behind a `useMockData`/`DEMO123` branch. Screens collect flows via `collectAsState` — no repository layer is actually used (3 "repositories" are dead classes). Navigation is a manual `Screen` enum + hand-rolled backstack.

## 2. Verdict by layer

| Layer | Verdict | Grade |
|---|---|---|
| Navigation/router (`App.kt`, backstack) | Works, but back behavior broken on tabs; iOS has no system back | C |
| `AppState` | Functional but a god-object; logout privacy bug; fake-success paths | C |
| `AmazeClient` | Real network code mixed with ~70 mock branches + demo backdoor | C– |
| `SyncEngine` | Execution API dead; races; cancellation mislabeled | D |
| Models | Duplication, dead fields, `success=true` defaults | C– |
| FFCS planner | Real algorithm, several correctness bugs, stale hardcoded catalog | C |
| Notifications | Real, but cancel path broken, ID collisions, lost on reboot | C– |
| Widgets | Real, but never push-updated; free-classrooms widget is fake data | C |
| NFC | Entirely dead, no UI, crash-prone | F |
| Utils | 9 dead files; QR broken ≥v4; iOS file-saver no-op | D |
| UI components | 14 dead; theme drift (36 hardcoded colors, ~100 fontSize overrides) | C |
| Screens | 4 empty stubs, 6 dead buttons, fake data shipped, heavy duplication | C |
| Tests | 2 test files, one references a `Screen.DASHBOARD` that no longer exists | D |
| Docs | BUGS.md "58/58 fixed" is inaccurate; IMPLEMENTATION_PLAN.md largely done but stale; README claims Midnight theme that differs from code | D |

## 3. Top 20 defects (see the dedicated reports for everything else)

1. **CRITICAL — Demo-login backdoor in production.** `AmazeClient.kt:97` — logging in with username `demo`/`DEMO123` returns `success=true` with fake cookies; every downstream endpoint serves mock data. Any user can bypass VTOP auth. Also reachable via "Explore in Demo Mode" button (`LoginScreen.kt:358-392`).
2. **CRITICAL — Plaintext credentials.** VTOP password, Moodle password, Library password + full session cookie string stored unencrypted in SharedPreferences (`SettingsManager.kt:16,41-44,48,73`). Passwords also re-sent plaintext to `api.amazecc.com` for event login/preview/register (`AmazeClient.kt:1176-1234`).
3. **HIGH — Logout leaks previous user's data.** `AppState.kt:1945-2031` doesn't clear ~20 cache keys, tasks, attendance notes, course notes, profile photo, calendar lists — next login sees the previous student's data.
4. **HIGH — `postQBankPaper` 404s.** `AmazeClient.kt:1507` passes `/api/qbank/upload` into a helper that prepends `/api/` → `https://api.amazecc.com/api//api/qbank/upload`. QBank paper upload is broken.
5. **HIGH — CabShare fake success everywhere.** `AppState.kt:2175-2186, 2236-2246, 2271-2284, 2286-2299` — auth failure, trip creation, join requests, match actions all report `success=true ("saved locally!")` on any exception. Users believe trips are shared; they exist only on-device.
6. **HIGH — `clearPendingNotifications()` cancels nothing.** `NotificationsUtils.android.kt:57-61` cancels requestCode 0; real alarms use ids 1000+. Stale reminders fire forever; toggling a reminder off never cancels it.
7. **HIGH — Notification ID collision.** `AlarmReceiver.kt:53` — `generateId(title)` uses `title.hashCode()`; all class reminders share title → only one notification visible at a time.
8. **HIGH — Academics hub attendance always shows 0%.** `AcademicsScreen.kt:65` — `attendancePercentage` is a `"75%"` string; `toDoubleOrNull()` returns null → `0.0`.
9. **HIGH — Weekly timetable dialog always empty.** `TimetableComponents.kt:62` — filters by `slotName.uppercase().take(3) == "MON"`, but slotName is `"A1+TA1"`. Every day shows "No classes on X".
10. **HIGH — MarksSync.kt breaks the iOS build.** Expect `hashStringSha256` (`MarksSync.kt:10`) has an actual only in androidMain — no iOS actual. Leftover from a deleted object (BUGS.md claims it was deleted).
11. **HIGH — iOS FileSaver is a no-op** (`FileSaver.ios.kt:6`, returns false) — PDF/ICS saving silently fails on iOS; and Android < API 29 "saves" to an invisible cache dir while returning true.
12. **HIGH — 6 dead buttons.** Track Bus (`TransportScreen.kt:317`), Payments Top Up/History/Receipt/Pay Now (`PaymentsScreen.kt:95,105,236,238`), Hostel "Request Counselling" (`HostelScreen.kt:421`).
13. **HIGH — Reminders lost after reboot.** No `RECEIVE_BOOT_COMPLETED` receiver; alarms never restored.
14. **HIGH — FFCS silent course-drop.** `FfcsViewModel.kt:212-225` — any course whose lock/block filters eliminate all offerings is silently dropped from results with no error.
15. **HIGH — Widgets never push-updated.** Sync doesn't notify widget providers; home screens show stale data until the 15-min system refresh.
16. **HIGH — `headerBackOverride` never cleared.** `AppState.kt:2676-2698` — the previous screen's back handler leaks into the next screen.
17. **MED — QR generator broken for ≥v4.** `QRCodeGenerator.kt` — versions 4–6 compute one EC block where the spec requires 2–4, interleave wrong → decode failure; v1–3 OK. Share codes >108 bytes silently produce no QR.
18. **MED — Free-classrooms widget is fake data.** `WidgetDataUtils.kt:202-243` — hardcoded 5 rooms filtered by a "sample" algorithm, not the real free-classroom engine.
19. **MED — NFC feature dead.** No UI caller; `startSharing()` no-op (API 34); unguarded tag I/O can crash the process; no iOS actual.
20. **MED — 18 dead model classes, 3 dead repositories, 9 dead utils, 14 dead components, 4 empty screen stubs, ~70 mock branches** — see `05-dead-code.md` for the full inventory.

## 4. Quantified summary

| Metric | Count |
|---|---|
| Kotlin files | 168 |
| Mock/DEMO123 branches in `AmazeClient` | ~70 (only 11 endpoints are mock-free) |
| Files with zero callers (dead) | ~30 (incl. 4 empty stub screens, 3 repositories, 9 utils) |
| Dead components/params/flows | 14 components, ~40 fields, ~35 functions |
| Empty `catch` blocks | 14+ |
| Hardcoded `Color(0xFF…)` after cleanup claim | ~36 in screens + ~20 in components/theme |
| `fontSize = X.sp` overrides after cleanup claim | 100+ |
| Plaintext credential stores | 3 (VTOP, Moodle, Library) + session tokens |
| `success=true` default on response models | ~35 |
| Hardcoded date/semester constants | 6+ (all going stale) |

## 5. The single biggest structural problem

Everything funnels through the `AppState` singleton — screens bind to `AppState.x.collectAsState()`, which makes unit testing, modularization, and dead-code detection impossible, and it's why the "repositories" could go dead: `AmazeClient` + `AppState` absorbed their job. The path forward (see `06-modularization.md`) is to promote `AppState` flows to **typed reactive hooks** (a small `DataStore`-backed `FlowRepository` per domain, injected via `CompositionLocal`), have screens consume narrow flows, and delete the dead layers.
