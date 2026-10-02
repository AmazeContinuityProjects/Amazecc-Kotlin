# VTOP ground-truth audit — findings

Date: 30 Sep 2026. Source: 126 pages scraped live from a real account
(`authorizedID 25BLC1081`, semesters `CH20262701 / CH20252605 / CH20252601`) into
`../../vtop-snapshots/` (gitignored — real academic data, never commit).

Reproduce: `node ../AmazeCC-API/scripts/vtop-dump.mjs`
Audit:     `node ../AmazeCC-API/scripts/vtop-conformance.mjs`

## Why this exists

The first port of the modules was written against the *server's parsers*, not against VTOP.
Where the server and VTOP disagree — or where the server itself was itself guessing — the port
inherited the error. This file records what VTOP actually serves.

---

## 1. Timetable: two endpoints, and I had the second one wrong

`StudentTimeTableChn` renders the page and the `#semesterSubId` chooser. The grid only appears
when the page's own JS posts to a **second** endpoint:

```js
// the page's processViewTimeTable()
var params = "_csrf=" + csrfValue + "&semesterSubId=" + semesterSubId
           + "&authorizedID=" + id + "&x=" + now.toUTCString();
```

* path is `/vtop/processViewTimeTable` — **not** `/vtop/academics/common/...` (that 404s)
* `x` is **epoch millis** here (`fetchTimeTable.ts` uses `Date.now()`), *not* the RFC-1123 string

Posting the chooser endpoint with a semester returns the same empty page every time
(byte-identical, `--Choose Semester--` still selected) — which is why the ported timetable was
always blank. Real grid is 178–233 KB.

> The RFC-1123 `x` **is** real, but only on curriculum stage 2
> (`curriculumCategoryView`) and circular PDF download. Two conventions coexist; guessing
> uniformly is what broke it.

## 2. Attendance: the header is in `<thead>`, not in the row list

Real markup, `attendance__CH20262701.html`:

```html
<div id="getStudentDetails"><div class="table-responsive">
  <table class="table" style="...">
    <thead>
      <tr style="background-color:#656565;color:#fff">      <!-- 14 × <th> -->
        Sl.No | Course Code | Course Title | Course Type | Slot | Faculty Name | ...
    <tbody>
      <tr> 1 | BACSE102 | Problem Solving Using Java | Lab Only | L31+... | 32 | 36 | 89 | - | View
```

* **The header cells are `<th>`, so the HTML parser puts that row in `<thead>`** and the data rows
  (`<td>`) in `<tbody>`. Verified with a spec-compliant parser:
  `#getStudentDetails table tbody tr` → **13 rows, header already excluded**;
  `table tr` → 14.
* **An earlier revision of this document claimed the header leaked into the data rows and that
  `headerRowsToSkip` was missing. That was wrong.** It came from counting raw `<tr>` with a regex,
  which cannot see the thead/tbody split. A real DOM — browser *or* parse5 — agrees on 13.
  The ported selector is correct as written; do not "fix" it by skipping a row.
* What *is* true: the header labels carry embedded newlines and tabs
  (`Course\n\t\t\t…\tCode`), so any header-based lookup must collapse whitespace before comparing.
* The column indices (1,2,3,4,5,9,10,11) are **correct**, and `#getStudentDetails` exists.

> Method note: this is why the parser tests are now driven by real HTML through a spec-compliant
> DOM rather than by regex. A regex over raw HTML is not the same document the app sees.


## 3. Course Type vocabulary is different from what the port assumes

Real values, straight from the page:

```
Embedded Theory | Embedded Lab | Theory Only | Soft Skill
```

The port normalises against `ETH` / `ELA` / `Theory Only` / `Lab Only`. `Embedded Theory` and
`Embedded Lab` are the ETH/ELA pair, but nothing matches them literally — which is why the
credit-weighted merge never engaged. Map on these four strings.

Confirmed real codes are bare: `BACSE102`, `BACSE105`, `BACSE106`.

## 4. `timetable` + `attendance` both need the composite key for per-day detail

The "View" link is not a URL:

```html
<a onclick="javascript:processViewAttendanceDetail('CH2026270102069','L31+L32+L37+L38');">
```

`/vtop/processViewAttendanceDetail` takes **classId + slot**, not semester params — which is
why it returned HTTP 233. Regex already in `attendance/route.ts:229`:
`/processViewAttendanceDetail\('([^']+)','([^']+)'\)/`.

## 5. Ten endpoints return an empty 1070-byte page (not "ok")

Zero `<tr>` in each. The manifest labelled these successful; they are empty responses — either
no data for this account or wrong params. **Do not treat HTTP 200 as success.**

`all-grades`, `cgpa`, `acknowledgement`, `exam-attempt`, `eca-upload`, `thesis-status`,
`registration-status`, `hostel-leave-history`, `hostel-attendance-2`, `slo-feedback` (302, 0 bytes)

`cgpa` being empty matters: the app displays CGPA.

## 6. Conformance summary of the ported modules

16/19 extract data.

| module | result | evidence |
|---|---|---|
| ATTENDANCE | works, 1 junk header row | 13 rows, row0 = header |
| TIMETABLE | **was broken** | wrong endpoint; now 178–233 KB |
| MARKS | ok | `tr.tableContent`=18, nested level1=17 |
| GRADES | ok | 59 rows |
| HOSTEL | **FAIL** | leave landing has 0 rows ≥4 td |
| APAAR | ok | 7 inputs, no tables |
| EPT_SCHEDULE | ok | form-only |
| REGISTRATION_SCHEDULE | **not scraped** | `/vtop/examinations/hostelDetails` missing from the table |
| UNIVERSITY_DAY | ok | 1 table |
| DAYBOARDER | ok | 4 selects |
| STUDENT_PROFILE | ok | 93 label/value rows |
| BANK_INFO | ok | `#ifscCodeBkFrag b`=3 |
| PROFILE_IMAGES | ok | `h3.box-title b`=1, `table.table`=1 |
| EXAM_SCHEDULE | ok | 21 rows through the 3 gates |
| CIRCULARS | ok | `#tree1`=1, 28 `li` |
| CREDENTIALS | ok | headers contain both `ACCOUNT` and `NAME` |
| CURRICULUM | ok | `.categoty-card`=5 (the typo is real) |
| QCM_VIEW | ok, two stages | stage 1 is form-only; see §12 |

## 12. QCM is two stages (corrects an earlier entry in this document)

An earlier revision of this file recorded `QCM_VIEW | **FAIL** | 0 tables` and concluded that QCM
had no table behind it. That was wrong, and the reason was looking at only the first of two
requests.

| stage | request | what it returns |
|---|---|---|
| 1 | `POST /vtop/academics/common/QCMStudentLogin` | a form: a `semesterSubId` select with 6 options and **zero tables** |
| 2 | `POST /vtop/getStudentLoginForQcm` | the QCM table |

Stage 1's `onchange` handler is the specification:

```js
function getStudentLoginForQcm(paramUrl) {
  var csrfName = "_csrf"; ...
  var dataParameter = csrfName + "=" + csrfValue + '&paramReturnId=' + paramReturnId +
                      '&semSubId=' + semSubId + '&authorizedID=' + id + '&x=' + now.toUTCString();
  $.ajax({ url: paramUrl, type: "POST", data: dataParameter, ... });
}
```

Three details, each of which fails silently rather than loudly:

* the csrf parameter is named **`_csrf`**, not `csrf`. Sending `csrf` gets a Tomcat **404**.
  (The relative-looking `url: paramUrl` is *not* relative to the page: `/vtop/academics/common/…`
  and the nested variants all 404 too.)
* the semester is sent as **`semSubId`**, not `semesterSubId`. Omitting it returns 200 with
  *"This menu is not available at present"* instead of an error.
* nothing is preselected — stage 1 opens on a `-- Select --` placeholder, so **every** semester has
  to be fetched, not just the first.

The stage-2 table has 11 columns, header expressed as `<td>` (not `<th>`) inside a plain
`<table>` with no `<thead>`, wrapped in `<div id="getStudentLoginForQcm">`:

`Sem Code | Course Code | Course Title | Course Type | Class Nbr | Faculty | QCM No. | Action | Suggestions | Faculty Reply | HOD Comments`

* **Course Type is a three-letter code** — `ETH`, `ELA`, `LO`. An earlier assumption that it
  spelled out `Embedded Theory` / `Theory Only` would have matched nothing.
* `Action` holds `<span>Faculty and HOD</span>`, so the text has to be unwrapped.
* Older semesters answer 200 with the header row and no data rows, which `VtopPage` drops as a
  table with no rows. That is "no QCM this semester", not a failure.

| semester | data rows |
|---|---|
| `CH20262701` Fall 2026-27 | 3 |
| `CH20252601` Fall 2025-26 | 6 |
| `CH20252605`, `CH20242505`, `CH20242501`, `CH20222323` | 0 |


## 9. The timetable grid (added after the real-markup tests)

`POST /vtop/processViewTimeTable` — see §1. Columns, read off `timetable-grid__CH20262701.html`:

| idx | header | real value |
|---|---|---|
| 0 | Sl.No | `1` |
| 1 | Class Group | |
| 2 | Course | `BACSE102 - Problem Solving Using Java ( Lab Only )` |
| 3 | L T P J C | `0 0 4 0 2.0` |
| 4 | Category | `University Core Courses` |
| 5 | Course Option | |
| 6 | Class Id | `CH2026270102069` |
| 7 | Slot/ Venue | `L31+L32+L37+L38 - AB1-607B` |
| 8 | Faculty Details | `52282 SHEENA CHRISTABEL PRAVIN SENSE` |

Three things that were previously wrong or unknown:

* **LTPJC is space separated with a trailing decimal** — `0 0 4 0 2.0`, not `4-0-0-0-8`. The
  credit is the **5th** whitespace token. `AcademicMerge` was splitting on `"-"`, which never
  matches real markup, so **the timetable never contributed credits at all**; the guard also
  rejected `2.0` because it is not an `Int`. Fixed, with a test that fails if it regresses.
* **The Course cell carries code, title *and* type** — the type is in trailing parentheses
  (`( Lab Only )`, `( Embedded Theory )`, `( Embedded Lab )`). Consistent with attendance.
* **Slot and venue are one cell** joined by `" - "`, so `splitSlotVenue` splits on that, and the
  slot half is itself `+`-joined.

## 13. LMS (`lms.vit.ac.in`) — the DTO, corrected against a live login

`scripts/lms-dump.mjs` logs in with `.env`'s `LMS_ID`/`LMS_PASSWORD` and writes
`lms-assignments.json`. The old `LMSAssignment` had `assignmentId/courseCode/title/maxMarks/dueDate/
status/score`; nothing in the server ever produced those, so the app could only ever show an empty
list. The real shape:

```
name  = "BAMAT209_FALL26-27/Mathematical Foundations for Computation(BAMAT209)/Digital_Assignment_2"
courseCode       = "BAMAT209_FALL26-27"      <- term-stamped, not the bare VTOP code
courseTitle      = "Mathematical Foundations for Computation(BAMAT209)"
assignmentTitle  = "Digital_Assignment_2"
due              = "Sunday, 4 October 2026, 12:00 AM"
done             = false
day/month/year   = 4 / 10 / 2026              <- from the *calendar cell*
url              = "https://lms.vit.ac.in/mod/assign/view.php?id=22795"
teachers         = ["DA_1_Abhishek"]         <- section title from the course page
```

* `shortCourseCode` narrows `BAMAT209_FALL26-27` to `BAMAT209`, which is how it joins against
  VTOP's bare course codes.
* **Moodle writes midnight as `12:00 AM`**, which must not become noon. The due instant is built
  from day/month/year, not by parsing the prose `due` string.
* Teachers need a **second request**: the event page has no section context, so
  `/course/view.php?id=<courseId>` is fetched and `#module-<id>` walked up to its
  `li[id^="section-"]`.

### The bug this surfaced

`parseLmsCalendar` read `data-month`/`data-year` off the `a[data-action="view-event"]` — the
element that carries the href. **Those attributes are not on it.** They live on the cell's
`a[data-action="view-day-link"]` (falling back to `.calendarwrapper`). Reading the wrong element
yields empty strings, so every assignment arrived with a null due date, no due instant, and no
reminder ever fired — with no error anywhere. Fixed, with a fixture test asserting the markup.

### Coverage

The dashboard only renders the month it opens on and there is no "all upcoming" view, so coverage
is built by paging the calendar's own arrows (`previous`/`next`, whose hrefs carry the required
`time` epoch). `VtopLms` walks 1 month back and 3 forward.

## 10. Open decisions

* **Test target.** Resolved: `shared` has a `jvm()` target with actual stubs, so `commonTest` runs
  on Windows via `:shared:jvmTest`.
* ~~**LMS DTO mismatch**~~ — resolved, see §13.
* ~~**`getQcmView` calls `"qcm-view"`; the API route is `qcm`.**~~ — fixed.
* **Desktop app entry point.** The `jvm()` target builds and the shared code runs, but there is no
  runnable Desktop main yet.
* **`DomainSnapshot` is not yet the persisted format.** Screens still read raw DTOs.

## 14. A fixture bug worth recording

`gen-fixtures.mjs` walked table rows with:

```js
$(r).find("td").each((___, c, i) => { if (headers[i]) obj[headers[i]] = ... })
```

cheerio's `each` passes `(index, element)` only, so `i` was always `undefined`,
`headers[undefined]` was always falsy, and **every generic page fixture had zero table rows** while
still parsing as valid JSON. `page-qcm.json`, `page-curriculum.json`, `page-student-profile.json`
and the rest were all empty, which made the real-markup tests for those pages vacuous — they
asserted `ok` and header/select facts, never a single row.

Fixed, plus a second defect found once rows appeared: pages that express the header as `<td>`
inside `<tbody>` (QCM stage 2 does) yielded the header as a data row, so row 0 is now dropped when
there is no `<thead>`. Row counts are asserted in `RealQcmAndLmsTest`.
