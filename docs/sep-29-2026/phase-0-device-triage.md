# Phase 0 — Device Triage Checklist

**Date:** 2026-09-29
**Status:** Not yet run — requires a physical device on an Indian network.

## Why this exists

The plan's route triage (see `vtop-local-integration-plan.md` §2) is **inferred**, not measured.
`api.amazecc.com` is served from Render Singapore and cannot reach VTOP, but the exact set of the
57 app endpoints that are broken has not been confirmed. Run this before Phase 3 so the work is
scoped correctly.

## What to check

Open the app with `vtop_source = LOCAL` and record the outcome per module.

| Host | Endpoint | Reachable from device? | Result |
|---|---|---|---|
| `vtopcc.vit.ac.in` | `GET /vtop` | expected yes | |
| `vtopcc.vit.ac.in` | `GET /vtop/prelogin/setup` | | |
| `vtopcc.vit.ac.in` | `POST /vtop/login` | | |
| `lms.vit.ac.in` | `GET /login/index.php` | expected **no** — same VIT block | |
| `vitolcc.vit.ac.in` | `/` | unknown | |
| `eventhubcc.vit.ac.in` | EventHub login | unknown | |
| `api.amazecc.com` | `GET /api/health` | expected yes | |

## Verifying in-app

1. Install a debug build.
2. Settings → **Data Source** → leave on `LOCAL`.
3. Log in. The captcha prompt only appears if the recogniser is not confident.
4. For per-endpoint results, watch logcat:

```
adb logcat -s AmazeCC:V chromium:V
```

## Things to record

- Whether `VtopPageState.LANDING` is reached, and whether the prelogin handshake succeeds. The
  port 302-redirects to `/vtop/init/page` and 404s if the session is not primed.
- Whether `#captchaBlock img` is present or VTOP serves a **reCAPTCHA** instead. The reference
  implementation sees reCAPTCHA when it is served a datacenter IP; a residential Indian IP
  should get the plain image captcha. If reCAPTCHA appears, Phase 2's solver is bypassed and
  every login needs manual input — that materially changes the UX.
- Whether `EXTRACT_CONTENT` finds `#authorizedIDX`, `input[name=_csrf]` and `#winImage`.
- Whether `FETCH_SEMESTERS` returns a non-empty map, or `not authorized` (the User-Agent block
  signal that triggers rotation).

## Also worth capturing

The public egress IP of the test device's network. If a specific range is needed on an
allowlist later, this is what to send VIT.
