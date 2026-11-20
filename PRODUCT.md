# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

VIT University students, checked mostly on a phone between classes. Their main job is finding out where they stand in a semester — attendance percentage and bunk margin — and then reaching grades, timetable, and campus services without switching to the VTOP portal or the web app.

## Product Purpose

AmazeCC is the native Android client for the AmazeCC student OS, reading academic and campus data from VTOP through `hono.amazecc.com`. Its point is attendance-first: the fastest, clearest answer to "am I safe this semester?" against the 75% threshold, with the rest of the student's data available in the same place. Success means a student can check attendance safety at a glance and continue to marks, timetable, hostel, payments, library, and transport from one app.

## Positioning

Attendance-first tracker, not a general campus portal. The VTOP portal exposes the same underlying records; AmazeCC's claim is that it turns attendance and bunk status into the primary, immediate job, with the remaining campus data as supporting context rather than the headline.

## Operating Context

Used in short sessions on a phone, often between classes. Sign-in takes VTOP credentials with captcha auto-solving and cookie-based session management. Normal flow includes a semester selector and visible sync status / error banners when the backend is unavailable. Attendance is read against a 75% safety threshold; course cards, sub-tabs (All / Theory / Lab), and expandable assessment cards are part of the established workflow.

## Capabilities and Constraints

- **VTOP session auth:** cookie-based login with captcha auto-solving via `hono.amazecc.com`; must be preserved.
- **Web parity tracking:** screens and data track the AmazeCC web app; `docs/M3E_SCREEN_MIGRATION_PLAN.md` and `docs/WEB_PARITY_DRIFT_REGISTER.md` are the working references.
- **75% attendance threshold:** attendance safety badges and bunk calculations are built around it and are core to the product's job.
- **Stateless Compose + tests:** immutable-state composables that emit actions via lambdas, `@Preview` coverage, and headless JVM verification are documented architecture principles.
- **Shared design language:** one consistent design across the KMP targets; no per-OS redesign. Platform token remains `android` because the native Android app is the shipping product; the `iosApp` directory is a KMP scaffold, not a separate design language.
- **Roadmap boundary:** Phase 1 (foundation/UI) and Phase 2 (core academics) are complete; Phase 3–5 campus, hostel, and social features are planned or stubbed and must not be described as shipped behavior.

## Brand Commitments

- Name: **AmazeCC**; native port of the web app of the same name.
- App icon derived from the web app favicon.
- Existing visual identity documented in `README.md`, `UI_DESIGN_SYSTEM.md`, and `docs/M3E_SCREEN_MIGRATION_PLAN.md`: two first-class appearances — Midnight pitch-black OLED and Classic Light — plus selectable accent palettes, shared with the web app. Recorded here as an existing commitment, not as a new direction.

## Evidence on Hand

- `README.md` — product overview, feature phases, API, theme table.
- `UI_DESIGN_SYSTEM.md` — existing component, motion, and theming documentation.
- `docs/M3E_SCREEN_MIGRATION_PLAN.md`, `docs/WEB_PARITY_DRIFT_REGISTER.md`, `docs/TYPED_API_CONTRACTS.md` — screen inventory, web-parity status, and API contracts.
- Live backend at `https://hono.amazecc.com` using the same data as the AmazeCC web app.
- No customer testimonials, case studies, press, or external proof assets are available; future work must not fabricate them.

## Product Principles

1. **Attendance first.** The quickest path to "am I safe this semester?" outranks breadth of campus features.
2. **One source of truth.** Academic data comes from VTOP through the shared API; the app never invents or hard-codes student records.
3. **Keep step with the web app.** Mobile screens, terminology, and behavior stay aligned with AmazeCC web.
4. **Design once across targets.** A single shared design language serves the KMP targets; OS-specific redesign is out of scope.
5. **Keep UI dumb and verifiable.** Immutable state in, actions out, and headless tests as the default proof.
