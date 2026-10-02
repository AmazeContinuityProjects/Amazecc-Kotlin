# Test suite baseline — 30 Sep 2026

> **Final state of the first pass: 164/171 passing, 7 failing** (from 137/160 at the start).
> `:shared:compileAndroidMain` and `:androidApp:compileDebugKotlin` still pass throughout.
>
> An earlier revision of this document ranked the `jsString` escaping bug as security-critical and
> put the exam-date bug below it. **That ordering was wrong.** `jsString` was correct — the test
> asserted escaping of a `'` inside a *double*-quoted literal, which is inert — and the exam-date
> bug was the genuinely user-facing one. Sections below marked "test bug" are not code defects.

First run of `:shared:jvmTest`. Until the `jvm()` target existed, `commonTest` had no runnable
host: it was never compiled and never run, so every failure below was invisible.

**160 tests — 137 pass, 23 fail.**

Running the suite immediately paid for itself: it turned 18 *compile* errors (stale references to
APIs that no longer exist) into 4 small fixes, and then exposed 23 genuine failures in logic that
ships.

## Compile fixes (suite now builds)

| Was | Now | Why |
|---|---|---|
| `parsed.attr(0)` | `parsed.capture(0, 0)` | captures became `List<List<String?>>`, indexed by capture slot |
| `VtopCourse(...)` 8 args | 9 args | `component` sits at position 4 |
| `course.baseCode` | `course.courseCode` | the port stopped appending `(L)`/`(T)`; the half lives in `component` |
| `stored.toAttendanceItem()` | + 4 imports | members of `object AcademicDerivers`, so same-package is not enough |
| `assertEquals(0.5, hours, …)` | `hours!!` | `hoursUntilExam` returns `Double?` (null = unparseable date) |

---

## Real production bugs found and fixed

Each verified against the live VTOP markup, not against a hand-written fixture.

1. **Exam dates never parsed** (`ExamUtils.parseExamDateToLocalDate`). DD-MM-YYYY inferred the
   month position from where the year sat, so `19/11/2025` read the *day* 19 as the month and
   returned null — any date with day > 12 failed. Separately, a filter dropped every token
   containing `:`, which deleted the year from `19-Nov-2025T09:15:00`. The exams screen and its
   countdowns had no data.
2. **Login reg number discarded** (`UserMerge.defaultSourceFor`). `regNo` defaulted to
   `STUDENT` (order 2) while login merges with `SESSION` (order 0), so `canWrite` evaluated
   `0 >= 2` and dropped it. `name` had the same defect. regNo namespaces the social storage keys.
3. **Mess codes never expanded.** `if (mess.length > 7)` guarded a `when` matching `"NON"` (3
   chars) and `"FOOD"` (4) — both branches unreachable.
4. **Credits never read from the timetable.** `AcademicMerge` split LTPJC on `"-"`; VTOP sends
   `"0 0 4 0 2.0"`. The guard also rejected `2.0` for not being an `Int`.
5. **Venues truncated.** `[A-Z]+\d*\s*-\s*\d+\s*[A-Z]?` let the trailing `[A-Z]?` swallow the
   first letter of the *next* token, so `AB1-12 LT1-34` yielded `T1-34`.
6. **Null captures became the string `"null"`.** `JsonNull` is a `JsonPrimitive`, so
   `(cell as? JsonPrimitive)?.content` returned `"null"` for a null capture.
7. **`isLabCourse` ignored the course code**, so `18CSC301L(L)` was read as theory.

## Test bugs (no code defect)

`jsString` quote escaping (double-quoted literal needs no `'` escaping) · both `ExamUtils`
window boundaries (at 09:01 the exam is 23h59m away, i.e. *inside* the window) · the `ETH + ELA`
label (a string VTOP never sends) · the receipts fixture (header row with no matching capture
entry, so every capture sat one row off — the "silent corruption" that existed only in the
fixture; the real page has all 12 rows aligned) · `"attrs"` vs `"captures"` · `cell(0,0).length`
· `hasIdentity` (a fragment carrying a reg number *is* an identity) · a dash-separated LTPJC that
encoded bug #4.

## Remaining 7

`testAppStateNavigation` · `resolveCurrentSemesterPicksMostAttendanceBearingSemester` (SEM1/SEM2) ·
`weeklyTimetableResolvesDayTimeAndCarriesCourseFields` (MON/WED) · `updateSemesterReturnsSameInstanceWhenUnchanged` ·
`attendanceFromBothHalvesIsKeptWithoutDuplicates` · `legacySuffixedRowsAreMigratedOntoTheBareKey`
(possibly dead code — real VTOP codes are already bare) · `nextExamWithinBoundaryIsExactly24Hours`.

## Harness

- `../AmazeCC-API/scripts/vtop-dump.mjs` — scrapes 126 pages; session-cached so repeat runs skip
  the captcha-gated login.
- `../AmazeCC-API/scripts/gen-fixtures.mjs` — real HTML → spec-compliant DOM (cheerio/parse5,
  which inserts thead/tbody as a browser does) → the same selectors → JSON that `VtopPage`/
  `VtopRows` consume.
- `RealVtopParseTest` (jvmTest) — runs the real Kotlin parsers over real markup.

The HTML is gitignored (real academic data). The derived `fixtures/` are not.

> **Method note.** A regex over raw HTML is not the document the app sees. An earlier revision of
> the audit concluded attendance leaked a header row into its data; a real DOM disproved it — the
> header cells are `<th>`, so the row lands in `<thead>` and `tbody tr` is already correct.


---

## The 23 failures, by severity

### 1. Data-loss / migration

`EmbeddedCourseMergeTest > legacySuffixedRowsAreMigratedOntoTheBareKey`
`NoSuchElementException: Key BACSE106 is missing in the map.`

Legacy `BACSE106(L)` / `BACSE106(T)` rows are supposed to migrate onto the bare key. This is the
path that runs for anyone upgrading with pre-existing stored data, so it failing means courses
disappear from the user's timetable after upgrade. **Highest priority.**

### 2. ETH/ELA labelling

`EmbeddedCourseMergeTest > labelsRoundTripThroughTheStore` — expected `ELA`, got `ETH`.

The `AssessmentItem.component` doc still says `"ETH" / "ELA" / "Theory Only" / "Lab Only"`, while
VTTOP actually sends `Embedded Theory` / `Embedded Lab` / `Theory Only` / `Lab Only`
(ground-truth audit §3). A lab assessment is being labelled as its theory half.

### 3. Attendance merge

`EmbeddedCourseMergeTest > attendanceFromBothHalvesIsKeptWithoutDuplicates` — expected 2, got 0.

Same-day rows from the two halves of an embedded pair are being dropped rather than de-duplicated.

### 4. Escaping (security-relevant)

`VtopLogicTest > aQuoteInAPasswordCannotBreakoutOfTheLiteral` — expected 22 chars, got 20.

`jsString` mishandles a single quote in a password. Every VTOP script is built by string
interpolation, so a password containing `'` can alter the generated JavaScript. This is the one
failure with a security dimension and should be fixed before anything else ships.

### 5. Date parsing (breaks the exams screen)

`ExamUtilsTest > parsesNumericDates` and `parsesDatesWithTimeSuffix` — both return `null`.
`nextExamWithinBoundary…` and `nextExamWithinReturnsOnlyFutureExamsInsideWindow` fail as a
consequence.

`ExamUtils` cannot parse `19-11-2025`. Since the audit showed real exam dates look like
`09-07-2026`, the exams/countdown UI has no data behind it.

### 6. Row/capture misalignment

`VtopPortedModuleParseTest > receiptCapturesLineUpPerRow` — expected receipt `K1`, got `K2`.
`VtopDataSourceLogicTest > parseReadsRowsAndAttrs` — capture is `null`.

`VtopRows` is returning captures against the wrong row. Every per-row capture (attendance
"View" link, payment receipts) is at risk of attaching to the wrong course.

### 7. Text handling

- `messCodeIsExpanded` — `NON VEG` truncated to `NON` (whitespace collapse)
- `venueIsNarrowedToTheLastToken` — `LT1-34` loses its leading `L`; the venue regex is swallowing
  part of the slot
- `shortGradeRowIsSkipped` — expected 2 rows, got 1

### 8. State / storage

- `UserStoreTest` — 3 failures, all "expected a value, got `null`" across
  `blankValuesAreFilteredOut`, `emptyNeverErasesFilled`, `fromSessionOnlyCarriesRegNo`
- `AcademicMergeTest > updateSemesterReturnsSameInstanceWhenUnchanged` — idempotence broken, so a
  no-op sync still triggers recomposition
- `AcademicMergeTest > upsertTimetableFillsFieldsWithoutClobberingAttendance` — `4-0-0-0-8`
  truncated to `4` (LTPJC splitting)
- `AcademicDeriversTest` — 3: `isLabCourseDetection`, `resolveCurrentSemesterPicks…` (SEM1 vs
  SEM2), `weeklyTimetable…` (MON vs WED)
- `AmazeTests > testAppStateNavigation`

### 9. Fixed already

`ProjectionsTest > exams…seat` — my own test asserted `"AB1-305 / 17"` when `seatLocation` was
empty. The projection correctly drops blank halves; the test expectation was wrong, not the code.

---

## Order of work

1. `jsString` quote escaping (security)
2. legacy `(L)`/`(T)` migration (data loss on upgrade)
3. ETH/ELA labelling + attendance merge
4. `ExamUtils` date parsing
5. capture/row alignment
6. `UserStore` nulls, merge idempotence, text handling
7. the rest

Each fix is now verifiable by a test that has never been able to run before.

---

## Session 2 — LMS and QCM

`:shared:jvmTest` went **172 → 187**, all passing. Five new defects, four of them silent.

| # | defect | why it was silent | fix |
|---|---|---|---|
| 1 | QCM never fetched. Stage 1 is form-only; stage 2 is `getStudentLoginForQcm` | stage 1 returns 200 with zero tables, which reads as "no data" | `VtopDataSource.fetchQcmView` fans out per `semesterSubId` |
| 2 | csrf parameter is `_csrf`, not `csrf` | Tomcat answers **404**, and nothing checked the status | probe + `sessionBody()` already correct |
| 3 | `getQcmView` called the route `"qcm-view"`; the API route is `qcm` | route 404s as JSON, surfaces as "empty response" | fixed |
| 4 | `parseLmsCalendar` read `data-month`/`data-year` off the *event* link, which has neither | every assignment got a null due date, so **no reminder ever fired** and nothing errored | read from `a[data-action="view-day-link"]`, fall back to `.calendarwrapper` |
| 5 | QCM header keys matched exactly; live header is `QCM No.` | `stringOf` returned null, QCM No was blank | `stringOf` now falls back to a normalised (case/punctuation-insensitive) match |

Also corrected from evidence rather than assumption:

* **QCM Course Type is `ETH`/`ELA`/`LO`**, not `Embedded Theory`/`Theory Only`.
* **QCM's header row is `<td>` inside `<tbody>`**, no `<thead>` — a `th`-only walk yields nothing.
* **`gen-fixtures.mjs` emitted zero rows for every generic page** (cheerio `each` passes
  `(i, el)`, so a third parameter was `undefined`). All `page-*.json` fixtures were empty, making
  that whole class of real-markup test vacuous. Fixed, and row counts are now asserted.
* `LMSAssignment` now matches a live Moodle payload; `AmazeClient.getLMSAssignments()` has a
  LOCAL branch backed by the new `VtopLms`, using the separately-stored Moodle credentials.

### Verified counts

```
:shared:jvmTest          187 tests, 0 failed
:shared:compileKotlinJvm main COMPILES
:androidApp:assembleRelease  4.34 MB
  SHA-256 EC00C5CB1B3F87F934985A473DAFADB97FA63FD497380D01026391351BD085A8
```

### Still unverified

* No live on-device run. Every parser is grounded in captured markup, but the WebView engine
  itself has only executed inside the app, never under test.
* Moodle month fan-out (1 back / 3 forward) is implemented and reasoned about, but the capture only
  ever had one event in one month, so the paging loop itself is untested against real data.
* A real signing keystore is still needed for a distributable build.

---

## Session 3 — `DomainSnapshot` promoted to the persisted format

`:shared:jvmTest` went **187 → 224**. `AppDataStore` now writes schema 3 and reads 1, 2 and 3.

### The blocker that was not obvious

`VtopIngestor.fromLegacy` mapped **13 of `AppDataSnapshot`'s 22 top-level fields** and defaulted the
rest. Promoting `DomainSnapshot` to the storage format with that mapper would have silently
**deleted** payments, laundry, hostel counselling, calendars-list, curriculum, clubs, Moodle,
tasks, cab-share and FFCS registration on upgrade — with no error and no way back.

The rule adopted: make the shape a superset first, then pin losslessness with a test rather than by
inspection. `SnapshotRoundTripTest` fills every field with a sentinel and asserts the whole set
survives, so a field added to the old snapshot without a home in the domain one now fails the build.

Nested drops found the same way: `StoredCourse.courseSystem`, `ExamItem.classId`/`slot`,
`AssessmentItem.status`, `GradeBreakdown.status`/`weightageMark`, and a `GradeRange` that had been
pre-rendered into an unusable string.

### Bugs found

| # | defect | why it was silent | fix |
|---|---|---|---|
| 6 | 9 of 22 snapshot fields unmapped | defaulted to null, i.e. "nothing to store" | shape widened + sentinel test |
| 7 | `selectedSemesterId = keys.maxByOrNull { it }` picked the **oldest** semester | lexicographic max on `CH2026…` ids | compare year then term; persist the user's choice |
| 8 | `Exam.section` held the semester id despite documenting a section header | projection filtered on it, so it worked — until someone trusted the doc | renamed `semesterId` |
| 9 | `encodeDefaults = false` dropped the `academics` marker on an empty snapshot | fresh install wrote a v3 blob that read back as v1 | encoder always writes markers, nulls still omitted |
| 10 | decoding a v3 blob as v2 *succeeds* into an empty snapshot | `kotlinx.serialization` defaults unknown fields | detection order pinned by a test |

### Verified counts

```
:shared:jvmTest              224 tests, 0 failed
:androidApp:assembleRelease   4.36 MB
  SHA-256 3A7A970318320D35E4ADEF5E0F77E952D8BC9E068A97BD7111260485C34040D8
```

`AppDataStoreUpgradeTest` exercises the real path — encrypted bytes on disk, `restore()`, read
again — for v1, v2 and v3 blobs, plus the widget/notification reader and the backup envelope.

### Deliberately unchanged

* **Backup export stays v2.** A backup file is a long-lived external contract with its own
  `formatVersion`; changing its payload would make backups already on a device unreadable.
* **In-memory shape stays `AppDataSnapshot`.** ~43 screens read those flows; `toLegacy` is a
  proven-lossless inverse, so the screens migrate on their own schedule rather than in one risky
  commit.
