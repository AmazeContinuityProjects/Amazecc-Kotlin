# Group C — Table extractors

Four modules that read tables positionally, plus Calendar which derives Calendars List.

---

## EXAM_SCHEDULE

`POST /vtop/examinations/doSearchExamScheduleForStudent`

**Param is `semesterSubId`, not `semesterId`.** Body: `authorizedID`, `semesterSubId`, `_csrf`.

`src/app/api/schedule/route.ts` — note the route is `schedule`, not `exam-schedule`.

### Row classification — all three gates are mandatory

Iterate `table.customTable tr`:

1. **Section header:** exactly one `td` with `colspan === "13"` → set `currentExamType` to that
   cell's trimmed text, skip the row.
2. `tr.tableHeader` → skip.
3. `!currentExamType` → skip. Rows before the first section header are dropped.
4. `tr.tableContent` **and** more than one `td` → emit an item.

### Column map (13 columns)

| idx | field |
|---|---|
| 0 | *unused* (S.No) |
| 1 | `courseCode` |
| 2 | `courseTitle` |
| 3 | ***never read*** |
| 4 | `classId` |
| 5 | `slot` |
| 6 | `examDate` |
| 7 | `examSession` |
| 8 | `reportingTime` |
| 9 | `examTime` |
| 10 | `venue` |
| 11 | `seatLocation` |
| 12 | `seatNo` |

### Response casing

`{ semester: <echo>, Schedule: { "<section name>": ExamItem[] } }` — **uppercase `Schedule` only**.
No lowercase `schedule` exists anywhere in the server. The DTO already carries both
`@SerialName("Schedule") rawScheduleUpper` and `rawScheduleLower` with a computed `schedule`
accessor, so either casing deserialises.

Map keys are the section-header strings verbatim, e.g. `"End Semester Practical"`.

### No date conversion

`examDate` is the raw VTOP text, unmodified. The Kotlin port must apply its own normalisation if
the UI needs a date type.

---

## CREDENTIALS

`POST /vtop/proctor/viewStudentCredentials` — shared `verifyMenu` body.

Same `parseCredentials` as Group B's PROFILE_IMAGES section. See
`group-b-key-value-scan.md` for the full column map and the `"Account"` / `"Name"` header gate.

The only difference is the response shape: the standalone route **spreads** the parse result
(`{success, title, credentials, ranks}`) whereas PROFILE_IMAGES nests it under
`credentials`.

---

## CIRCULARS

`POST /vtop/admissions/costCentreCircularsViewPageController`

`src/lib/parsers/circulars.ts` → `parseCirculars(html)`.

- `title` = `h3` first, trimmed.
- Root container **`#tree1`**. Absent → `circulars: []`.
- Recursive walk of `ul.children("li")` — **direct children only**, never `.find`.

Per `li`:
- `li.children("a").first()` present → **leaf**: `id` from the anchor's `onclick` via
  `/viewCertificate\(['"]?([^'")\s]+)['"]?\)/` (group 1, or `null`), `title` = anchor text trimmed.
  VTOP emits both `viewCertificate('12345')` and `viewCertificate(12345)`.
- else `li.children("span").first()` present → **group**: `name` = span text trimmed,
  `children` = recursive parse of `li > ul`, or `[]`.
- else → **silently dropped**.

Recursion is unbounded depth.

Response: `{success, title, circulars: [...]}` where a group emits `name` + `children` and a leaf
emits `id` + `title`. The omitted keys differ by node kind, so the DTO must tolerate both shapes —
`CircularItem` already does.

### Circular PDF download (companion)

`GET /vtop/admissions/viewStatusWiseCostCentreCircularContent` with query `_csrf`, `authorizedID`,
`val=<circularId>`, and **`x = new Date().toUTCString()`** — an RFC-1123 string, *not* epoch, which
differs from every other route. `Referer` is the circulars page URL, not `/vtop/open/page`.
`redirect: "follow"`.

---

## QCM_VIEW

Two stages.

**Stage 1** — `POST /vtop/academics/common/QCMStudentLogin` with the shared `verifyMenu` body.
Then re-scrape `pageCsrf = $('input[name="_csrf"]').val() || csrf` from the response, and read
`#semesterSubId option` into `semesters[]` as `{value: attr("value"), text: text().trim()}`, skipping
empty values.

**Stage 2** — `POST /vtop/getStudentLoginForQcm` with:

```
_csrf=<pageCsrf>&authorizedID=<id>&semSubId=<value>&paramReturnId=getStudentLoginForQcm&x=<epochMs>
```

**`semSubId`, not `semesterSubId`.** Root is narrowed to `#getStudentLoginForQcm` when present.
Semesters are fetched **sequentially**; a per-semester throw becomes `{semester, error}`.

### extractTables — positional

Per `table`:
- skip when the table contains a nested table **and** ≤ 2 rows (layout wrapper)
- `caption` = first `caption` trimmed
- headers: from `thead tr` first, `th, td` in order, non-empty only; if none, from the first row's
  `th`; if still none, from the first row's `td` **excluding** `colspan === "13"` and text ≥ 100 chars
- body: `tbody tr` when present, else `tr` minus the header row
- each `td` at index `i`; **empty cells are skipped but still consume an index**
- recorded only when there is at least one header and one row

Headers are **raw, not camelCased**.

### extractKeyValuePairs — three methods, last write wins

1. any table, `tr` with exactly 2 `td` → label (whitespace collapsed, < 80 chars, not starting
   with `<`) and value
2. `dl` → each `dt` paired with its `next("dd")`
3. `.form-group, .row` → first `label` with the first `span, .form-control-static, p`, else the
   first `input`'s value

**camelCase (qcm flavour — a third variant, and a fourth counting `common.ts`):** strip
non-alphanumerics and whitespace, lowercase, uppercase every word start, remove spaces.
`"Student Name"` → `studentName`.

### Dedup pass
KV pairs are discarded when their camelCased key equals any camelCased table header, **or** when
their value already appears as some table cell value.

### Fallbacks
`messages[]` collects text from `.alert, .box-body > p, .callout, .info-box-text` longer than 3
chars. Only when there are no tables *and* no KV pairs is `plainText` set from the body.

Response: `{success, semesters[], data: { "<subId>": {semester, tables, keyValuePairs, messages?, plainText?} }}`.

---

## CALENDAR → also fixes CALENDARS_LIST

`POST /vtop/processViewCalendar`, one request per month, **sequential**.

**`semSubId`, not `semesterSubId`.** Body:
`authorizedID`, `semSubId`, `calDate` (`DD-MMM-YYYY`, e.g. `01-JUL-2026`), `classGroupId`
(`ALL | ALL02 | ALL03 | ALL05 | ALL06 | ALL08 | ALL11 | WEI`), `_csrf`, `x`.

### classGroupId coercion
- semester ending `05` → forced to `ALL` unless already `ALL`, `ALL02` or `ALL05`
- semester ending `07` → forced to `ALL`

### Month list, derived from the semester code
`semCode = semesterId.slice(-2)`, `startYear = parseInt(slice(2,6) || 2024)`, `nextYear = startYear + 1`

| semCode | months |
|---|---|
| `01` | Jul, Aug, Sep, Oct, Nov of `startYear` |
| `05` | Dec of `startYear`, then Jan–Apr of `nextYear` |
| other | May, Jun, Jul of `nextYear` |

For `CH20262701` → `slice(-2)="01"`, `slice(2,6)="2026"`.

### Parse
- `month` = `h4` first, trimmed
- iterate `table.calendar-table tbody tr td`
- **span 0** of the cell is the **day number**; empty → skip the day
- **spans 1..n** are the **events**
- per event: `text` trimmed, `color` from the inline style via `/color:\s*([^;]+)/`, `type` by
  lowercase substring — `"instructional"` → `Instructional Day`, `"holiday"` → `Holiday`, else
  `Other`
- `category` = the first `\(([^)]+)\)` group in the event text, else `"General"`
- **days with zero events are dropped entirely**

Response: `{semesterId, calendars: [{month, days: [{date, events: [...]}]}]}`.

`getCalendars` then fans this over `calendarTypes` and builds `CalendarsListRes` — so one
extractor fixes both modules.

`src/lib/analyzeCalendar.ts` post-processes this shape but the route never invokes it.
