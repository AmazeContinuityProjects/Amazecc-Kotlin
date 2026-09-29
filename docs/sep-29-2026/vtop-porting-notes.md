# VTOP Porting Notes — 2026-09-29

Reference for the Flutter (`fkvit`) -> Kotlin (`AmazeCC-Kotlin`) port. Source files are in
`../fkvit/lib/features/authentication/` unless noted.

---

## 1. Login sequence

Source: `core/auth_service.dart`. All traffic is synchronous `$.ajax` inside a hidden WebView.

```
1. GET  /vtop                       -> onPageFinished -> _detectPageState()
2.   if LANDING: POST /vtop/prelogin/setup   body = $('#stdForm').serialize()
             then GET /vtop/login
3. GET  /vtop/login                 -> LOGIN -> _getCaptchaType()
4.   $('input#gResponse').length === 1  -> GRECAPTCHA
     else                                -> DEFAULT
5.   DEFAULT: $('#captchaBlock img').get(0).src   (data:image/...;base64)
     GRECAPTCHA: grecaptcha.execute() -> token over JS channel (tokens > 20 chars)
6. POST /vtop/login                 body = $('#vtopLoginForm').serialize()
                                     captcha written to BOTH captchaStr and gResponse
7.   success iff response body contains 'authorizedIDX'
8. GET  /vtop/content               -> #authorizedIDX, input[name=_csrf], #winImage
9. POST /vtop/academics/common/StudentTimeTableChn -> #semesterSubId options
```

### Page state probe

```js
document.body === null                      -> BODY_NOT_READY  (ignore)
$('input[id="authorizedIDX"]').length === 1 -> HOME
$('form[id="vtopLoginForm"]').length === 1  -> LOGIN
else                                        -> LANDING
```

Landing retry: `maxLandingPageAttempts = 5`, `landingPageTimeout = 5s` (`auth_constants.dart:36`).

### Login response classification

Applied to the **lowercased** response body:

| Code | Pattern | Action |
|---|---|---|
| 0 | `authorizedIDX` present | success -> `GET /vtop/content` |
| 1 | `/invalid\s*captcha/` | retry, reload `/vtop/login` for a fresh captcha |
| 2 | `/invalid\s*(user\s*name\|login\s*id\|user\s*id)\s*\/\s*password/` | hard stop |
| 3 | `/account\s*is\s*locked/` | hard stop |
| 4 | `/maximum\s*fail\s*attempts/` | hard stop |
| 5 | contains `login` + (`error` or `fail`) | report, no stop |
| — | contains `___INTERNAL___RESPONSE___` | **WAF/internal marker, no handling -> Unknown** |

UA block signal: any response body containing `not authorized` (case-insensitive) -> rotate UA, retry.

---

## 2. Captcha solver

Source: `ocr_custom/`. A plain dense layer + softmax. No ONNX, no TFLite.

| Constant | Value |
|---|---|
| image size | 200 x 40 |
| charset | `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (32 — no `I`, `O`, `0`, `1`) |
| num classes | 32 |
| num characters | 6 |
| confidence threshold | 0.70 |
| high confidence | 0.90 |
| weights | `assets/ml/vellore_weights.json` — `{ "biases": [32], "weights": [[input] x 32] }` |

### Preprocessing (`vellore_preprocessor.dart`)

1. decode -> bilinear `copyResize` to 200x40
2. **HSV saturation channel**: `sat = ((max - min) * 255) / max`
3. reshape to 40 rows x 200 cols
4. slice 6 blocks:
   ```
   x1 = (a+1) * 25 + 2
   y1 = 7 + 5 * (a % 2) + 1
   x2 = (a+2) * 25 + 1
   y2 = 35 - 5 * ((a+1) % 2)
   ```
5. per block: binarize by block mean -> `val > avg ? 1.0 : 0.0`, flatten

### Inference (`vellore_model.dart:94-123`)

```
logits[i] = sum_j( input[j] * weights[j][i] ) + biases[i]
prob       = softmax(logits)          // numerically stable
char       = charset[argmax(prob)]
confidence = max(prob)
```

Auto-submit when mean confidence >= 0.70. Otherwise show a manual dialog pre-filled with the prediction.

Model derived from `pratyush3124/VtopCaptchaSolver3.0` (>95%). **Chennai weights are corrupt; Vellore weights are used** (`constants.dart:60`).

---

## 3. Data endpoint param template

```js
'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val()
              + '&_csrf='        + $('input[name="_csrf"]').val()
              + '&nocache='      + Date.now()
```

`verifyMenu` family additionally needs `winImage` from `#winImage`.

All data endpoints are **relative to `/vtop/`** and all are POST returning **HTML fragments** parsed with `DOMParser`.

---

## 4. Route mapping (app -> VTOP)

Only the `LOCAL` rows move on-device. The `REMOTE` rows keep calling `api.amazecc.com`.

| App endpoint | Mode | Notes |
|---|---|---|
| `attendance` | LOCAL | `processViewStudentAttendance` |
| `timetable` | LOCAL | `processViewTimeTable` |
| `schedule` | LOCAL | same endpoint, different shaping |
| `marks` | LOCAL | `examinations/doStudentMarkView` |
| `all-grades` | LOCAL | `examinations/examGradeView/StudentGradeHistory` |
| `grades` | LOCAL | `examinations/examGradeView/doStudentGradeView` |
| `student`, `me` | LOCAL | `studentsRecord/StudentProfileAllView` |
| `curriculum` | LOCAL | + `curriculum/syllabus` |
| `circulars` | LOCAL | |
| `calendar` | LOCAL | |
| `hostel`, `hostel-counselling` | LOCAL | |
| `payments`, `payment-receipts`, `wallet` | LOCAL | `p2p/Payments`, `p2p/getReceiptsApplno` |
| `credentials` | LOCAL | |
| `apaarid`, `bank-info`, `dayboarder` | LOCAL | |
| `feedback-status` | LOCAL | |
| `ept-schedule`, `registration-schedule`, `university-day` | LOCAL | |
| `minor-honour`, `course-completion`, `bonafide` | LOCAL | |
| `additional-learning`, `exc-registration` | LOCAL | |
| `e-transcript` | LOCAL | |
| `profile-images` | LOCAL | |
| `faculty/scrape`, `faculty/schools` | LOCAL | |
| `lms-data` | LOCAL | `lms.vit.ac.in` — separate host, same geo block |
| `events/*` | LOCAL | `eventhubcc.vit.ac.in` |
| `qbank/*` | REMOTE | own Postgres |
| `clubs/details`, `club-admin/feed` | REMOTE | own Postgres + `clubToken` |
| `cabshare/*` | REMOTE | own Postgres |
| `transport`, `transport/register` | REMOTE | own Postgres |
| `wishlist` | REMOTE | own Postgres |

---

## 5. Assets to copy from `fkvit`

| From | To |
|---|---|
| `android/app/src/main/res/raw/vtop_cert.pem` | `androidApp/src/main/res/raw/vtop_cert.pem` |
| `android/app/src/main/res/raw/sectigo_ca_chain.pem` | `androidApp/src/main/res/raw/sectigo_ca_chain.pem` |
| `android/app/src/main/res/raw/eventhub_cert.pem` | `androidApp/src/main/res/raw/eventhub_cert.pem` |
| `assets/ml/vellore_weights.json` | `androidApp/src/main/assets/vellore_weights.json` |
| `android/.../res/xml/network_security_config.xml` | `androidApp/src/main/res/xml/network_security_config.xml` |

`network_security_config.xml` is **trust-anchor augmentation, not pinning** — `system` certs remain in the list. It exists to survive Sectigo root expiry/misissuance.
