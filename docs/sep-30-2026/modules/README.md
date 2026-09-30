# Remaining VTOP Module Port — 2026-09-30

**Status:** in progress
**Predecessor:** `../sep-29-2026/vtop-implementation-status.md`
**Verification:** `gradlew.bat :androidApp:compileDebugKotlin`

---

## Scope

29 `SyncModule`s exist. **17 need porting.** The rest are unaffected by the geo-restriction and
stay remote.

### Stays remote — backed by our own Postgres, not VTOP

`TRANSPORT`, `BUSES`, `CLUBS`, `cabshare/*`, `qbank/*`, `wishlist`, `admin/*`, `cron/*`

These work today from Render Singapore because they never touch a VIT host.

### Needs porting

| Group | Modules | Effort |
|---|---|---|
| A — generic page parse | APAAR, EPT_SCHEDULE, REGISTRATION_SCHEDULE, UNIVERSITY_DAY, DAYBOARDER | 1 extractor, mostly wiring |
| B — key/value label scan | STUDENT_PROFILE, BANK_INFO, PROFILE_IMAGES | 3 bespoke parsers |
| C — table extractors | EXAM_SCHEDULE, CREDENTIALS, CIRCULARS, QCM_VIEW | 4 bespoke parsers |
| D — curriculum | CURRICULUM (+ syllabus, download) | 2-stage, highest effort |
| E — other hosts | LMS, EVENTS, EVENTS_PROFILE, MOODLE | 3 hosts, 3 auth models |

Calendar and Calendars List are **one** extractor, not two: `getCalendars` fans `getCalendar` out
over `calendarTypes`, so both derive from `/vtop/processViewCalendar`.

## Why this is mostly wiring

Every DTO already exists, and the seven identity modules are already consumed through
`IdentityExtractor.fromX()` → `UserStore.merge(...)`:

```
AppState.kt:1945  fromStudentProfile     AppState.kt:1961  fromBankInfo
AppState.kt:1950  fromProfileImages      AppState.kt:1966  fromApaarId
AppState.kt:1955  fromCredentials
AppState.kt:1697  fromDayboarder         AppState.kt:1702  fromEptSchedule
AppState.kt:1707  fromRegistrationSchedule   AppState.kt:1713  fromUniversityDay
```

So each module is: write an extractor, add a `vtop_source` branch to the `AmazeClient` method.
**No UI, store, or navigation changes.** That is the reason this is tractable.

## Landmines

1. **Param names are inconsistent and a wrong one silently returns empty.**
   - `semSubId` — calendar, QCM
   - `semesterSubId` — timetable, exam schedule, marks, OD
2. **Three cache-buster conventions.** `nocache=<epochMs>` on `verifyMenu` shell POSTs;
   `x=<epochMs>` on AJAX data POSTs; `x=<UTC date string>` in curriculum stage 2 only.
3. **Two different `camelCase` implementations** in the server. Pick per-parser to match the
   existing field names, or the DTOs will not deserialise.
4. **Cheerio `:contains()`** is a jQuery extension — needs `text().indexOf(...)` in JS, not a CSS
   selector. Used by curriculum and lms-data.
5. `parseVtopHtml` header placeholders are `col0`, `col1`… (0-based on array length), and an
   empty cell still consumes its index.
6. `hod-dean` derives a table's `role` from `headers.eq(i + 1)` over a **combined**
   `h3.box-title b, h3.box-title` selector. An off-by-one renames every person.

## Sequencing

Groups A and B first — they cover most of the reported "Empty response" modules and let the
change be validated on a real device early. D and E last: they are the ones least verifiable
without a live Indian session.

## Known constraint

Tests cannot run on this checkout. `shared/src/commonTest` only reaches the iOS targets and
`kotlin.native.ignoreDisabledTargets=true` disables those on Windows and Linux. Verified by
planting a deliberate type error and watching the task still pass. The AGP 9 KMP plugin here
exposes no `androidLibrary` extension and `withHostTestBuilder` does not resolve, so there is no
host-test compilation to attach to without a build-config change.

The on-device **Settings → VTOP Connection → Diagnostics** page is the compensating control.
