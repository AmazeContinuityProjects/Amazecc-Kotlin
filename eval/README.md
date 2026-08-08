# AmazeCC-Kotlin — Full Codebase Evaluation

**Date:** 2026-08-06
**Scope:** Every `.kt` file in `shared/` (commonMain, androidMain, iosMain), `androidApp/`, manifests, build scripts, resources, repo docs. 168 Kotlin files, ~2.0 MB.
**Method:** Full-file reads (not greps only) across 8 parallel deep audits + first-hand verification of every critical finding + a fresh `:androidApp:compileDebugKotlin` build attempt. Nothing was modified; `BUGS.md` claims of "58/58 fixed" were treated as unverified.

> **Headline: this codebase is roughly 60% real product and 40% scaffolding.** It compiles, looks polished, and a lot of the *flows* are real — but it ships with a live demo-login backdoor, fake success paths, ~30 dead screens/components/files, a broken notification cancel path, stale hardcoded data masquerading as live features, and plaintext credentials.

---

## Report index

| File | Covers |
|---|---|
| [00-summary.md](00-summary.md) | Executive summary, verdict per layer, top 20 defects |
| [01-stub-code.md](01-stub-code.md) | Every stub: mock data, placeholder UI, empty handlers, no-op services |
| [02-bugs.md](02-bugs.md) | Bug catalog (critical/high/medium/low) with exact file:line |
| [03-ui-audit.md](03-ui-audit.md) | Broken/unapplied UI, unreachable screens, theme/design-system drift |
| [04-connectivity.md](04-connectivity.md) | What's wired to what: API↔model↔state↔UI map, dead endpoints, sync engine |
| [05-dead-code.md](05-dead-code.md) | Complete dead-code inventory + safe-deletion list |
| [06-modularization.md](06-modularization.md) | How to modularize; subscribable-hooks (reactive state) plan |
| [07-security.md](07-security.md) | Security & privacy: backdoor, plaintext creds, token handling, XSS |
| [08-roadmap.md](08-roadmap.md) | Prioritized remediation roadmap |

---

## How to read this

- Every finding carries `file:line`. Paths are relative to repo root (e.g. `shared/src/commonMain/...`).
- Severity legend: **CRITICAL** = wrong behavior/security now; **HIGH** = broken feature or data corruption; **MED** = degraded feature/UX; **LOW** = cleanup/risk.
- "Dead" = zero callers anywhere in the repo (verified by cross-grep), unless noted.

## One-paragraph verdict per layer

- **API client (`AmazeClient.kt`, 1889 ln):** Real HTTP layer under a thin veil of ~70 mock branches; a production demo backdoor; one guaranteed-404 URL; no timeouts; session tokens in request bodies; plaintext credentials re-sent in event flows.
- **State (`AppState.kt`, 2716 ln):** Massive god-object; works, but logout leaks the previous user's data, fake-success paths in cab-share, sync toggles that gate nothing, a header back-override that never clears.
- **Sync engine (`SyncEngine.kt`, 430 ln):** Its entire *execution* API is dead; only state flows are used; data races in `activeJobs`/`_moduleStates`; cancellation marked as ERROR.
- **Models:** 18 dead classes, ~35 dead fields, 3 date formats, `success=true` defaults that mask failures, twin classes (`CGPA`/`CGPAResult`).
- **FFCS engine:** Real and mostly correct backtracking generator, but silent course-dropping, a grid that can't show evening slots, dead gap-highlighting, an entirely dead `CourseSearchPanel`.
- **Utils:** 9 fully dead files/chains (`ErrorUtils`, `StringSimilarity`, `PastDataSync`, `MarksSync` — the last one **breaks the iOS build**), a broken QR encoder above v3, a fake free-classrooms widget, iOS file-saver that always fails.
- **UI components:** 14 dead components; infinite animations in ScreenHeader; pinning FFCS/Free-Classrooms to tabs renders a blank page; two palettes are wired but untriggerable.
- **Screens:** 4 empty stub files; 6 dead buttons; fake data shipped (Hall of Fame names, Hostel advisor, mess menu); duplicated `KPICard` ×5; ~100 `fontSize` overrides and ~36 hardcoded colors after "58/58 fixed".
- **Platform:** NFC feature fully dead (no UI caller, no-op sharing, unguarded tag I/O); notifications: `clearPendingNotifications()` cancels nothing, ID collisions, no boot restore; widgets never push-updated; R8 release has zero keep rules.

## Phase 0 executed (2026-08-06)

All 10 security/data-integrity fixes from `08-roadmap.md` are implemented, compile-verified, and logged per-report: demo backdoor (C1) removed, credentials encrypted (Android Keystore AES-GCM; iOS fallback documented), logout wipe expanded, QBank upload path fixed, `MarksSync` deleted (iOS restored), keystore-secrets claim corrected (never committed), notification IDs fixed, cab fake-success killed, HTTP timeouts added. See fix-log sections in each report; build: `.\gradlew.bat :androidApp:compileDebugKotlin` → BUILD SUCCESSFUL.

## Demo Mode executed (2026-08-07)

The always-visible "Explore in Demo Mode" entry now drives every `if (useMockData)` branch in `AmazeClient.kt` (61 sites) from a single bundled `demoData.json` (65 endpoint-keyed sections) through the new `DemoData` loader (commonMain, `Res.readBytes`). No inline mock fixtures remain in compiled code (AmazeClient shrank ~820 lines). Demo session is in-memory only — wiped on logout/app restart. Build: `.\gradlew.bat :androidApp:compileDebugKotlin` +> BUILD SUCCESSFUL. See Demo Mode Fix Logs in 01-stub-code.md and 07-security.md.
