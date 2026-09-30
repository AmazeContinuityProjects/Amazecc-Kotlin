# Local VTOP Integration — Implementation Status

**Date:** 2026-09-29
**Verification:** `gradlew.bat :androidApp:compileDebugKotlin` — BUILD SUCCESSFUL, no new warnings.

---

## Done

### Phase 1 — engine and login
| File | Purpose |
|---|---|
| `vtop/VtopEngine.kt` | `expect class` + `vtopEngineSupported` |
| `androidMain/.../VtopEngine.android.kt` | offscreen 1×1 `INVISIBLE` WebView in the Activity content view |
| `iosMain/.../VtopEngine.ios.kt` | stub — throws with a pointer to this doc |
| `vtop/VtopScripts.kt` | `PAGE_STATE`, `PRELOGIN`, `READ_CAPTCHA`, `submitLogin`, `EXTRACT_CONTENT`, `FETCH_SEMESTERS`, `fetchRows` |
| `vtop/Vtop.kt` | login orchestration, captcha parking, UA rotation |
| `vtop/VtopSession.kt` | csrf / authorizedID / authorizedIDX / winImage / semester |
| `vtop/UserAgentPool.kt` | 12 hardcoded UAs, rotate on block |
| `vtop/ImageDecoding.*` | `data:` URI → `ImageBitmap` for the captcha preview |
| `services/AndroidApp.kt` | added `attachActivity` / `detachActivity` — a WebView needs an Activity to be attached |

### Phase 2 — captcha
| File | Purpose |
|---|---|
| `captcha/CaptchaMath.kt` | block geometry, HSV saturation, binarise, dense+softmax, argmax |
| `captcha/CaptchaEngine.*` | `expect`; Android loads `assets/vellore_weights.json` and decodes with `Bitmap` |
| `CaptchaHandlers.kt` | `AutoCaptchaHandler` (auto-submit ≥ 0.70, else delegate), `UiCaptchaHandler` |
| `ui/components/VtopCaptchaPrompt.kt` | manual entry dialog, reuses `AmazeTextField` / `AmazeButton` |

### Phase 3 — data
| File | Purpose |
|---|---|
| `vtop/VtopRows.kt` | positional rows + per-row captures, and the marks/cgpa/dues result types |
| `vtop/VtopDataSource.kt` | six modules, all ported |
| `api/AmazeClient.kt` | `getAcademicData`, `getTimetable`, `getMarks`, `getGrades`, `getHostelDetails`, `getPayments` dispatch on `vtop_source` |

`VtopScripts.fetchRows` carries the whole page-specific configuration as parameters —
`tableSelector` + `tableIndex` (grades reaches `#fixedTableContainer table` `.eq(1)`),
`headerRowsToSkip` (the `table.table-bordered` pages), and `captures` (attributes or input values
pulled from inside a row). Four extractors needed bespoke scripts because they are not
positional-row reads: `fetchMarks` (nested `customTable-level1` table, values inside `<output>`
elements), `fetchCgpaSummary` (Bootstrap list group), `fetchPaymentStatus` (a green `<font>`
means *no* dues, inverted), and `fetchRows`' own key/value block.

Assets copied from `fkvit`: `assets/vellore_weights.json`, `res/raw/{vtop,sectigo_ca_chain,eventhub}_cert.pem`,
`res/xml/network_security_config.xml`.

### Migration prompt and diagnostics
| File | Purpose |
|---|---|
| `ui/components/VtopMigrationPrompt.kt` | one-time notice for the switch to on-device VTOP |
| `vtop/VtopDiagnostics.kt` | connection checks (no VTOP calls) and per-module checks (real calls) |
| `SettingsPages.kt` | `VtopSourcePage` + `VtopDiagnosticsSection` |
| `SettingsModels.kt` | `SettingsSubScreen.VTOP_SOURCE` entry |

The prompt fires once, guarded by `SettingsManager.KEY_VTOP_SOURCE`-adjacent
`vtop_source_prompted`, and is **skipped where `Vtop.isSupported` is false** so iOS is never
offered a choice it cannot honour. It is raised at the top of `LoginScreen`'s `LaunchedEffect`,
*before* the session restore, so existing installs see it rather than being routed straight past
to Home. `onDismissRequest` is a no-op so it cannot be swiped away by accident.

---

## Release build

```
gradlew.bat :androidApp:assembleRelease
```

**Two flags are required on this checkout** — neither is a code problem:

- `--no-configuration-cache` — `lintVitalAnalyzeRelease` fails to serialise
  `DefaultConfigurableFileCollection` into the configuration cache
- **no `--offline`** — lint resolves the JVM/desktop variants of the KMP dependencies, which are
  not in the local cache

Result: **BUILD SUCCESSFUL**, `androidApp/build/outputs/apk/release/androidApp-release.apk`,
4.25 MB.

Verified inside the artifact:

| Check | Result |
|---|---|
| APK signature | v2 scheme, verifies |
| Signer | **`CN=Android Debug`** — see below |
| R8 minify + resource shrink | ran; `mapping.txt` 94 MB, `usage.txt` 5.9 MB |
| New classes survived R8 | `VtopEngine`, `VtopSource`, `VtopSourcePage`, `AmazeClient.setVtopSource` all present in the mapping |
| `assets/vellore_weights.json` | present, 245.9 KB |
| Cert PEMs | all 3 present (`res/jy.pem` 6.4 KB, `res/nI.pem` 2.3 KB, `res/rW.pem` 4.2 KB — AGP obfuscates resource paths) |
| `networkSecurityConfig` | wired: `android:networkSecurityConfig=@0x7f110002` |

### Not distributable as-is

The APK is signed with the **Android debug certificate**, not a release key. `resolveSigningConfig()`
in `androidApp/build.gradle.kts` returns null when no keystore or
`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars are set, and `release` then falls back to
`signingConfigs.debug`. Fine for on-device testing, useless for distribution. Supply a keystore
and re-run, or use the CI workflow (`.github/workflows/release.yml`) which injects
`KEYSTORE_BASE64` and the passwords as secrets.

---

## Not done

- **Transport** — stays remote; it is backed by our own Postgres and is unaffected by the
  geo-restriction.
- **iOS engine** — stub only.
- **~26 further VTOP routes** — curriculum, circulars, calendar, student profile, bonafide,
  apaarid, bank info, dayboarder, ept/registration schedule, university day, lms-data, EventHub,
  and so on. Same mechanism, same per-page work.

---

## Blocked / pre-existing

**Tests cannot run in this repo.** `shared/src/commonTest` only reaches the iOS targets, and
`kotlin.native.ignoreDisabledTargets=true` disables those on Windows and Linux. Verified by
planting a deliberate type error and watching the task still pass. The AGP 9 KMP library plugin
here exposes no `androidLibrary` extension and `withHostTestBuilder` does not resolve, so there
is no Android host-test compilation to attach to. `shared/build.gradle.kts` is **unmodified**.

64 test cases are written (`VtopLogicTest`, `VtopDataSourceLogicTest`, `VtopPortedModuleParseTest`)
and will run via `:shared:allTests` on macOS, once someone adds an Android host-test compilation.

---

## Risks

1. **The APK is debug-signed.** See the release build section above. Needs a real keystore before
   it can be distributed.
2. **Column indices are coupled to VTOP's markup.** They are reproduced verbatim from
   AmazeCC-API's parsers. A markup change produces *plausible but wrong* data, not an error —
   the single most dangerous failure mode here, which is why the diagnostics page exists.
3. **Per-course attendance detail is sequential.** The server used concurrency 3; the WebView
   script is synchronous, so overlapping requests would interleave. Expect slower syncs.
4. **reCAPTCHA remains unsolved on-device.** `AutoCaptchaHandler` falls through to manual entry
   if VTOP escalates. Phase 0 on a real device will show whether it escalates at all for a
   residential Indian IP.
5. **Tests cannot run on this checkout** — see the blocked section above.
