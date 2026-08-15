# Centralised User Identity Store

## Problem

User identity data arrives from ~9 parallel API endpoints, each with its own JSON shape:

- `/api/student` returns a nested `{ profile: {...} }` with **every field conditionally absent**
- `/api/profile-images` returns proctor/HoD/Dean with a different shape (`{title, photoBase64, details}`) than `/api/student` (`profile.proctor.*`)
- `/api/credentials`, `/api/bank-info`, `/api/apaarid`, `/api/dayboarder`, `/api/ept-schedule`, `/api/registration-schedule`, `/api/university-day` each return partially-parsed VTOP pages with `tables`, `keyValuePairs`, `formFields` as raw JSON

The same concept is spelled differently everywhere (`authorizedID` vs `reg_number` vs `applicationNumber`; `mobile` vs `phone`; `defaultCredentials` vs password). The app's pages compensate by re-implementing fallback chains and JSON deciphering per page:

- `ProfileHub.kt` — 4-way photo fallback chain, `profile?.name ?: authorizedID` fallbacks
- `ProfileIdentityPages.kt` — per-field null-guarding of the same fields in two pages
- `ProfileRecordsPages.kt` — ~400 lines of generic JSON-to-UI deciphering (`JsonElementCard`, `unwrapObject`, fuzzy dayboarder matching, ...)
- `DashboardWidgets.kt`, `SocialScreen.kt`, `CommandRegistry.kt` — duplicated name/photo fallbacks

## Goal

Build a **centralised identity store** that every endpoint syncs into:

1. **Decipher** — each endpoint's payload is converted by a pure extractor into typed, clean data. Raw VTOP JSON (`tables`, `keyValuePairs`, `formFields`, `JsonElement`) never reaches the UI.
2. **Filter** — empty fields (null, blank, empty collections) are dropped at extraction time. Pages never see placeholder "Not set" rows or missing keys.
3. **Infer** — the store computes derived facts once: `displayName`, `initials`, `isDayboarder`, `hasApaar`, "Scheduled / Not scheduled", `Proctor · 2`, etc.
4. **Merge safely** — overlapping endpoint data (photo, proctor, regNo, credentials) resolves deterministically via priority tiers. A field is only ever replaced by a non-empty value from a source of equal-or-higher priority.
5. **One source of truth** — a single `StateFlow<StudentIdentity>` (persisted encrypted under one cache key) that every page renders directly. Pages become pretty UI over already-decoded data.

## Architecture

```
Ktor endpoints ──► raw Res models
                        │
                        ▼
              IdentityExtractor.fromX(res)      ← pure, filters empties, deciphers JSON, infers flags
                        │
                        ▼
              UserStore.merge(fragment, source) ← priority tiers, per-field, deterministic
                        │
                        ▼
        StateFlow<StudentIdentity> ──persist──► CACHE_USER_IDENTITY (encrypted)
                        │
                        ▼
        Pages (ProfileHub, records, widgets, social, commands) — thin renderers
```

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Scope | All user data incl. record payloads | "UI is just pretty UI" — sync deciphers everything, pages render |
| Conflict resolution | Priority tiers (deterministic) | Parallel fetches make last-writer-wins non-deterministic; identity data is static |
| Persistence | One encrypted cache | Merged blob contains aadhar, bank, credential passwords — no longer safe as plaintext |
| Legacy flows | Removed after migration (grep-verified) | Dead state lingers otherwise |
| Backend | Phase 2: `POST /api/me` (optional, both repos) | Fewer requests, canonical server shape |

## Files

- `01-data-model.md` — the `StudentIdentity` model and source mapping matrix
- `02-merge-semantics.md` — filtering rules, priority tiers, edge cases
- `03-store-api.md` — `UserStore` / `IdentityExtractor` APIs and AppState wiring
- `04-consumer-migration.md` — page-by-page migration and legacy deletion list
- `05-implementation-plan.md` — ordered tasks, tests, Phase 2