# Local VTOP Integration — Plan

**Date:** 2026-09-29
**Status:** In progress
**Scope:** Move VTOP data access from the `api.amazecc.com` server proxy into the Android app.

---

## 1. Problem

VTOP is reachable **only from Indian IP space.** Measured 2026-09-29:

- 30-node global TCP probe to `vtopcc.vit.ac.in:443` — **2 successes, both in India** (`AS218984` 22ms, `AS215228` 103ms). All 28 non-India nodes timed out.
- `api.amazecc.com` is hosted on Render `plan: free`, `region: singapore`, egress `74.220.52.0/24` + `74.220.60.0/24` (`type: shared`, `AS16509`).
- Render logs: `Error: connect ETIMEDOUT 118.95.161.134:443` — SYN dropped, no RST.
- Cloudflare Worker probe (12s and 90s budgets, 3 colos: KIX, MRS, FRA) — `example.com` 200 in 8ms, VTOP/LMS `HTTP 522` at ~19.8s. Cloudflare's own connect timeout, so extra time does not help.

VTOP resolves to `118.95.161.146`, a **Sify (`AS9583`) dynamic retail broadband** address in Tambaram. Single A record, no CDN, no redundancy.

**Conclusion:** server-side proxying of VTOP is not viable. No CDN, PaaS region, or Worker can fix it. Only Indian egress works.

**The fix:** an Android app running on a phone in India has Indian egress. VTOP becomes reachable, and the server proxy becomes unnecessary for VTOP.

## 2. What moves, and what does not

The app calls **57 endpoints** on `api.amazecc.com` (44 via `postAuthorized`, 18 via raw `$baseUrl`, with overlap). The server exposes 229 routes total, but only these 57 matter.

| Group | Examples | Fate |
|---|---|---|
| VTOP-backed (~32) | attendance, timetable, marks, grades, student, curriculum | **Port to device** |
| Other VIT systems (~4) | `lms-data`, `events/*`, vitol | **Port to device** |
| Own Postgres (~21) | `qbank/*`, `clubs/*`, `cabshare/*`, `transport/*`, `wishlist` | **Stays remote** — unaffected by geo-restriction |

This is a **hybrid**, not a wholesale replacement.

### Side benefits

- VTOP credentials and `JSESSIONID` no longer transit a server in Singapore.
- `postAuthorized` currently sends the session cookie in a **JSON body** rather than a `Cookie` header — that stops entirely.

## 3. Source of the port

`fkvit` (sibling repo) is a **Flutter/Dart** VTOP companion app. Its transport is:

> Every VTOP request is a synchronous `$.ajax({async:false})` inside a hidden `WebView`, responses parsed by injected JS `DOMParser`.

There is **no cookie-management code** — `JSESSIONID` lives in the WebView `CookieManager`; `_csrf` and `authorizedID` are scraped from the rendered DOM.

We mirror that design rather than replacing it with OkHttp. OkHttp would force hand-rolled cookie replay, DOM-scraped CSRF, and an unsolvable reCAPTCHA path.

## 4. Architecture

New package under `shared/`, following the existing `expect/actual` pattern (`Encryption`, `NfcManager`, `NotificationService`).

```
shared/src/commonMain/.../vtop/
  VtopEngine.kt              expect class: load(path), evalJs(script, timeout), clearCookies
  VtopSource.kt              enum LOCAL / REMOTE
  VtopSession.kt             csrf, authorizedID, authorizedIDX, semesterSubId, winImage
  VtopNavigator.kt           state machine: LANDING -> LOGIN -> HOME
  VtopDataSource.kt          typed suspend fns -> existing *Res DTOs
  UserAgentPool.kt           hardcoded UAs + rotation
  LoginResult.kt             success / InvalidCaptcha / InvalidCredentials / Locked / MaxAttempts / Unknown
  VtopErrors.kt
  parsers/*.kt               ports of AmazeCC-API src/lib/parsers/*.ts
  captcha/ManualCaptcha.kt
  captcha/VtopCaptchaSolver.kt + model
shared/src/androidMain/.../vtop/VtopEngine.android.kt   offscreen WebView
shared/src/iosMain/.../vtop/VtopEngine.ios.kt           stub
```

**Key de-risk:** `model/*Res` (13 files) already match the API's JSON contract. The work swaps *where data comes from* — screens, `AppSanitizers`, `AppDataStore`, `SyncEngine` are untouched.

## 5. Locked decisions

| Fork | Choice |
|---|---|
| iOS | Android-only engine. iOS actual is a stub; iOS stays `REMOTE`. |
| Source selection | Manual `vtop_source = LOCAL \| REMOTE`. No auto-fallback. |
| Captcha | Manual dialog in Phase 1, NN solver in Phase 2. |
| User-Agent | Hardcoded list, rotate on `not authorized`. |

**Compensation for no auto-fallback:** a VTOP Diagnostics screen runs the local pipeline per module, reports pass/fail, and offers a one-tap switch to `REMOTE`. Without it, a VTOP markup change strands users on a broken `LOCAL` setting.

## 6. Load-bearing porting details

Easy to lose; taken from `fkvit/lib/features/authentication/core/auth_service.dart`.

1. **Login POST body is the entire `#vtopLoginForm` serialized**, not four fields. Parse every `<input>`/`<select>` from the `/vtop/login` HTML — `_csrf` is in there. Setting only `username`/`password`/`captchaStr`/`gResponse` fails.
2. **Write the captcha to both `captchaStr` and `gResponse`.**
3. **Token discrimination by length:** `> 100` chars -> raw reCAPTCHA token; else strip to `[A-Za-z0-9]`, trim, uppercase.
4. **Success = login response body contains `authorizedIDX`.** Failure via regex on lowercased body -> codes 1-5. Code 1 retries with a fresh captcha; 2/3/4 hard-stop.
5. **`___INTERNAL___RESPONSE___` in the body** = unexplained WAF failure -> `Unknown`.
6. **`winImage` is required** for the `verifyMenu` endpoint family — scraped from `#winImage` on `/vtop/content`.
7. **Param template:** `verifyMenu=true&authorizedID=<>&_csrf=<>&nocache=<epochMillis>`.
8. **Semesters** come from `#semesterSubId` `<option>` values on `processViewTimeTable`.
9. **Keep synchronous XHR semantics** — the parse-then-return pattern depends on the AJAX having completed.
10. **Fresh login clears cache + localStorage but NOT cookies** (`fkvit` `auth_service.dart:1294`).

## 7. Phases

| Phase | Work | Done when |
|---|---|---|
| 0 | Triage VIT hosts from a real device; confirm which of the 57 are broken | Triage table signed off |
| 1 | `VtopEngine`, `VtopNavigator`, `UserAgentPool`, session wiring, manual captcha, diagnostics | Login succeeds on-device |
| 2 | Captcha NN solver (model + preprocessor) | Unattended login |
| 3 | Core data: the 7 existing `SyncModule`s | All 7 syncing locally |
| 4 | Remaining VTOP routes + `lms-data` + EventHub | Ported |
| 5 | Hybrid cleanup, `vtop_source` switch, settings migration | Done |

## 8. Known gap: `clubToken`

`clubToken` is minted server-side by `signClubToken` from a `club_representatives` DB lookup. It is **not** a VTOP artifact and cannot be produced on-device.

So `/api/club-admin/*` and `clubs/*` stay remote even in `LOCAL` mode, and `SessionManager.clubToken` must still be populated by a remote call after local login.

## 9. Verification

No PR CI exists in this repo. `:shared:testDebugUnitTest` does not exist — `commonTest` wires into iOS targets only. The working check is compile-based:

```
gradlew.bat :androidApp:compileDebugKotlin
```

`commonTest` coverage will be added for pure-Kotlin pieces that need no device: parser logic, captcha preprocessing, error-code classification, `UserAgentPool` rotation.

## 10. Out of scope

- iOS `VtopEngine` (stub only)
- `admin/*`, `qbank/admin/*`, `cron/*` — server-side only, stay remote
- Migrating Postgres-backed features to direct Supabase access

## 11. Risks

1. **VTOP markup drift.** Parsers are HTML-shape dependent. Diagnostics screen is the mitigation.
2. **Captcha model drift.** Model is Vellore weights; VIT Chennai weights are reportedly corrupt. Manual fallback covers it.
3. **UA blocking.** Hardcoded pool is smaller than fkvit's 5000+. Rotation on block only.
4. **reCAPTCHA escalation.** If VTOP serves reCAPTCHA to the device IP, manual entry is the only path.
5. **R8 is enabled for release** with only `-dontwarn org.slf4j.**`. Any reflection or `@Serializable` edge cases need ProGuard rules.
