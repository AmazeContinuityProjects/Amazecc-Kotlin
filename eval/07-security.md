# Evaluation 07 — Security & Privacy

The credential-handling, backdoor, and token issues found during the audit. All `file:line` verified.

---

## 1. CRITICAL — Demo-login backdoor in production builds

**`AmazeClient.kt:97`:**
```kotlin
if (useMockData || username.lowercase() == "demo" || username.uppercase() == "DEMO123") {
    return LoginResponse(success = true, message = "Login successful (Demo Mode)!",
        cookies = "vtop_session_cookie=demo_session_123; csrf_token=demo_csrf_abc", ...)
}
```
- Any user typing `demo` / `DEMO123` (username only; password ignored) is authenticated with a fabricated session, bypassing VTOP entirely.
- `LoginScreen.kt:329-333` — after a *real* login with demo creds, `setUseMockData(true)` is applied; `:358-392` exposes a prominent **"Explore in Demo Mode"** button that logs in as `DEMO123`, saves the session, and `:376` re-enables mock mode.
- `AppState.kt:550` disables mock after `restoreSession()` — but the DEMO123 session itself remains valid.
- **Impact:** account-free access to the whole app; all data served from ~70 mock branches (fake CGPA 8.54, fake attendance — presented as real). If the mock branches ever diverge from the real API, a demo user sees fabricated academic data.
- **Fix:** remove the string check; gate `useMockData` behind `BuildConfig.DEBUG` + a hidden dev flag; never persist a demo session.

## 2. CRITICAL — Plaintext credentials on disk

| Store | Key | Location |
|---|---|---|
| VTOP password | `KEY_PASSWORD` | `SettingsManager.kt:16` |
| Library password | `KEY_LIBRARY_PASSWORD` | `SettingsManager.kt:48` |
| Moodle password | `KEY_MOODLE_PASSWORD` | `SettingsManager.kt:73` |
| Session cookie string (`vtop_session_cookie=…; csrf_token=…`) | `KEY_COOKIES` etc. | `SettingsManager.kt:41-44` |
| EventHub JSESSIONID / clubToken | — | `SessionManager.kt:38-41` |

- Storage backend: `com.russhwolf:multiplatform-settings` → Android `SharedPreferences` (unencrypted XML, exposed via backups/adb on rooted devices), iOS `NSUserDefaults`.
- `saveCredentials()` (`SettingsManager.kt:137-139`) is invoked at `LoginScreen.kt:328` (real creds) and `:375` (demo); Moodle creds (`:164-175`), library creds (`:148-157`).
- Automatic re-login re-sends these repeatedly: `AppState.kt:862, 1822, 2429`; session refresh at `FeedbackStatusScreen.kt:127-144`.
- **Fix:** Android → `EncryptedSharedPreferences` (or Android Keystore-backed cipher); iOS → Keychain (`KeychainSettings` exists in the ecosystem); never persist the raw cookie string (re-login with stored creds is safer than storing live session tokens). At minimum: Base64 is **not** encryption — use a real cipher.

## 3. HIGH — Plaintext credentials transmitted to third-party API

- `getEventPreview` (`AmazeClient.kt:1176-1187`), `registerForEvent` (:1200-1210), `eventLogin` (:1225-1228) — each posts the plaintext VTOP username+password (from `SettingsManager.getCredentials()`) to `api.amazecc.com` endpoints (`/api/events/preview`, `/api/events/register`, `/api/events/login`).
- If `api.amazecc.com` is compromised (or the TLS terminates at a proxy), VTOP credentials are exfiltrated.
- `getVtopStudentPhoto` (`:1324-1338`, dead code) sends the full VTOP cookie string with a `regNo` query param — a live token-drip if ever wired.

## 4. HIGH — Session tokens as body fields + wrong-cookie risk

- Every `postAuthorized` (`AmazeClient.kt:122-136`) transmits `cookies`, `authorizedID`, `csrf` as JSON body fields on every call. Matches the web app's contract, but it means the full session token is in every request body (and every proxy log).
- `getImageBytes` (`:1308-1322`) sends `Cookie: JSESSIONID=$token` to any URL containing `eventhubcc.vit.ac.in`; `SessionManager.saveEventHubSession` writes the JSESSIONID into the same `clubToken` slot as the VTOP club token (`SessionManager.kt:38-41`) — the two tokens overwrite each other and the wrong cookie can be sent (bugs H16).
- No cookie jar, no `expectSuccess`, no timeout on the shared `HttpClient` (`:90-94`).

## 5. HIGH — Logout privacy leak

`AppState.kt:1945-2031` — see bugs C3. Previous user's tasks, attendance/course notes, profile photo, calendar data, cab local trips persist in memory + on disk across logins. Shared-device use leaks student data.

## 6. HIGH — WebView XSS vector

`LatexViewer` android (`androidMain/.../LatexViewer.kt:12-34`) and iOS (`iosMain/.../LatexViewer.kt:14-59`): `$latex` interpolated raw into HTML loaded in a WebView/WKWebView with `javaScriptEnabled=true`; MathJax loaded from a CDN on every recompose. Any course/assignment/answer text containing markup (QBank answer viewer — `QBankScreen.kt:206,276`) executes in the WebView context.

## 7. MEDIUM — Secrets & keys in the repository

- `keystore_base64.txt` — Base64-encoded release keystore committed to the repo root.
- `release.keystore` — the keystore itself committed.
- `local.properties` — local SDK paths (usually gitignored; present in listing).
- `androidApp/build.gradle.kts:30-108` — release signing config reads the keystore with 3 fallback sources including writing the Base64 keystore to the build dir at config time; **debug builds sign with the release keystore when present** (`:98-101`) — a "debug" APK can be release-signed and vice-versa.
- Fix: gitignore + rotate the keystore (it's public now).

## 8. MEDIUM — Network hygiene

- `AndroidManifest.xml:14` — `usesCleartextTraffic="true"` app-wide; plaintext HTTP allowed everywhere.
- `getFFCSReport` (`AmazeClient.kt:1817`) — malformed host `https://amazecc.como/…` (typo; `.como` is a real TLD) — dead, but a working example of a bad URL; it would attempt a request to a random Colombian TLD if ever wired.
- No `HttpTimeout` — requests can hang forever; error text `e.toString()` surfaced to the user (`App.kt:284`) leaks ktor internals/URLs.

## 9. MEDIUM — AppState mutation surface

- Header flows are public `MutableStateFlow` (`AppState.kt:2685-2690`) — any code can mutate global UI state.
- `SettingsManager.clearAll()` (`:132-134`) wipes session + creds but leaves the in-memory session — half-logged-out state until restart.

## 10. Priority order for remediation

1. Remove the DEMO123/demo backdoor (gate mock behind debug).
2. Encrypt credential storage; stop sending raw cookie strings in bodies where avoidable.
3. Fix logout to clear every domain (use the `resetAll()` from the hooks plan — eval/06).
4. Sanitize `$latex` input before injecting into the WebView; bundle MathJax locally.
5. Remove keystore/secrets from git; rotate the keystore.
6. Add `HttpTimeout`/`expectSuccess`; remove `usesCleartextTraffic` or scope it.
