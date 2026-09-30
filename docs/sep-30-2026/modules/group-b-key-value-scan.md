# Group B — Key/value label scan

Three modules whose parsers walk label→value pairs rather than tables.

---

## STUDENT_PROFILE

`POST /vtop/studentsRecord/StudentProfileAllView`

Parser: `AmazeCC-API/src/lib/parsers/student-profile.ts` → `parseStudentProfile(html)`.

### Row scan
Selector `table tr` (all tables, all rows, nested included). `td` only — `th` ignored. Rows with
fewer than 2 `td` are skipped. **Cell 0 = label, cell 1 = value.** No other index is used.

`L = label.toUpperCase()`. Note whitespace is **not** collapsed here, so an indented label may
contain newlines.

### The `else if` chain — first match wins, order is significant

```
APPLICATION NUMBER           -> applicationNumber
STUDENT NAME                 -> name
DATE OF BIRTH                -> dob
BLOOD GROUP                  -> bloodGroup
PROGRAM / BRANCH | BRANCH    -> branch
GENDER                       -> gender
HOSTEL                       -> isHosteller  (value.toUpperCase() === "HOSTELLER" || "YES")
NATIVE LANGUAGE              -> nativeLanguage
NATIVE STATE                 -> nativeState
PHYSICALLY CHALLENGED        -> physicallyChallenged
COMMUNITY                    -> community
RELIGION                     -> religion
CASTE                        -> caste
NATIONALITY                 -> nationality
AADHAR | AADHAAR             -> aadharNumber
MOBILE NUMBER                -> mobileNumber
FRIEND MOBILE                -> friendMobileNumber
CURRENT ADDRESS              -> currentAddress   = parseAddress(html)
PERMANENT ADDRESS            -> permanentAddress = parseAddress(html)
APPLIED DEGREE               -> appliedDegree
EDUCATIONAL QUALIFICATION    -> educationalQualification
BRANCH / GROUP STUDIED       -> branchStudied
SCHOOL NAME | SCHOOL/COLLEGE NAME -> schoolName
MEDIUM OF STUDY              -> mediumOfStudy
BOARD / UNIVERSITY          -> boardUniversity
REGISTER NO                  -> registerNo
CLASS OBTAINED               -> classObtained
YEAR OF PASSING | PASSED     -> yearOfPassing
MONTH OF PASSING | PASSED    -> monthOfPassing
SCHOOL / COLLEGE ADDRESS | SCHOOL ADDRESS -> schoolAddress
BREAK IN STUDY               -> breakInStudy
NO.OF.BROTHERS | NO.OF BROTHERS -> brothers
NO.OF.SISTERS | NO.OF SISTERS   -> sisters
BROTHER/SISTER STUDYING | SIBLING -> siblingInVIT
GUARDIAN                    -> guardian
FACULTY ID | NAME | DESIGNATION | SCHOOL | DEPARTMENT | EMAIL | INTERCOM
   FACULTY MOBILE | FACULTY PHONE -> proctor.{...}
CABIN                        -> proctor.cabin
FATHER NAME | QUALIFICATION | OCCUPATION | ORGANISATION|ORGANIZATION
   | MOBILE | EMAIL | ANNUAL INCOME | DESIGNATION | ADDRESS -> father.{...}
MOTHER ... (same eight)      -> mother.{...}
```

Every field is optional and **omitted from JSON when never matched**, so the DTO must tolerate
absent keys.

### parseAddress(html)
Splits the **innerHTML of cell 1** on `<br>` / `<br/>` / `<br />` (case-insensitive). For each
fragment: decode entities, trim, skip if empty. First `:` at index > 0 → key = text before `:`
lowercased with **all whitespace removed** (`"House No"` → `"houseno"`), value = text after the
first `:`, trimmed. Fragments without a `:` (or with `:` at index 0) are dropped.

### Profile photo
Regex on the **raw HTML**, not the DOM:
```
/src="(data:[^"]+base64,[^"]+)"/i   then   /src="(data:image[^"]+)"/i
```
Stored whole including the `data:` prefix.

---

## BANK_INFO

`POST /vtop/studentBankInformation/BankInfoStudent`

Parser: `src/lib/parsers/bank-info.ts` → `parseBankInfo(html)`.

- `title` = `h3.box-title` first — note **no ` b`**, unlike proctor/credentials.
- Everything is scoped to **`#bankInfoStudentForm`**.

### Label pass
`form.find("table tr")` → label cell = `find("td").first()`, text trimmed + whitespace collapsed.
Control = `find("input, select").first()`. If the control has a `name`, `fieldLabels[name] = label`.
A control with no `name` contributes nothing; later rows overwrite earlier labels of the same name.

### Field pass
`form.find("input, select")` in document order — **all** inputs and selects, including hidden,
submit and button, with no type filtering. Only elements carrying a `name` are kept.

- `select` → `{type: "select", value: <form value>, label, options[]}` where each option is
  `{value: attr("value")||"", selected: attr("selected")!==undefined, text: text().trim()}`.
- anything else → `{type: attr("type")||"text", value: attr("value")||"", label}`.
  **Attribute only — a checkbox's `checked` is not reflected.**

`fields` keys are VTOP's own dynamic `name` attributes. They cannot be hardcoded; DOM order must be
preserved.

### Bank details
`#ifscCodeBkFrag` → collect the text of **every descendant `<b>`** into a list, then positional:
`[0]` bankName, `[1]` branch, `[2]` address; missing index → `null`. Div missing or zero `<b>` →
`bankDetails: null`.

---

## PROFILE_IMAGES

Three POSTs, all with the shared `verifyMenu` body and headers.

| # | Endpoint | Parser |
|---|---|---|
| 1 | `/vtop/proctor/viewProctorDetails` | `parseProctor` |
| 2 | `/vtop/hrms/viewHodDeanDetails` | `parseHodDean` |
| 3 | `/vtop/proctor/viewStudentCredentials` | `parseCredentials` |

Response nests all three: `{success, proctor, hodDean, credentials}`.

### parseProctor
- `title` = `h3.box-title b` first.
- photo = `table.table img[src^='data:']` first, `attr("src")`.
- rows `table.table tr`; **cell 0 = label** (whitespace collapsed), **cell 1 = value** (trim only);
  requires ≥ 2 cells.
- label match order: `faculty name` → `name`; `faculty email` → `email`;
  `faculty mobile` or `mobile number` → `phone`; `faculty designation` or `designation` →
  `designation`; else `camelCase(label)`.

**camelCase (proctor flavour — a third variant):** lowercase everything, then any run of
non-alphanumerics plus the next char → uppercase that char; lowercase the first char.
`"Faculty ID"` → `facultyId`, `"Room No."` → `roomNo`.

### parseHodDean
**Landmine:** the role for table `i` (0-based) is `headers.eq(i + 1).text().trim()` where
`headers = $("h3.box-title b, h3.box-title")` — a **combined** selector yielding
`[h3#1, b#1, h3#2, b#2, …]` in document order. So table 0 → the `<b>` inside the first heading,
table 1 → the second `h3.box-title`. Empty → `"Person ${i + 1}"` (1-based).

- photo: `img[src^='data:']` first within the table.
- rows: `table tr`; **cell 0 = label** (whitespace collapsed), **cell 1 = value** (trim);
  ≥ 2 cells; reject labels or values containing `<`.
- match order: `name of the faculty` or `name of the dean` → `name`; `email`; `mobile` or
  `phone`; `designation`; `cabin`; else `camelCase(label)` (the auto-parse flavour).

### parseCredentials
Also used by the standalone CREDENTIALS module in Group C.

- `title` = `h3.box-title b` first.
- per `table.customTable`: headers from `tr.tableHeader td` (td only).
  - if headers include exactly **`"Account"`** → credentials rows from `tr.tableContent`
  - else if headers include **`"Name"`** → rank rows from all `tr`, skipping `tableHeader` rows
    and rows with < 2 cells.

| Field | Cell |
|---|---|
| `account` | 0 |
| `username` | 1 |
| `defaultCredentials` | 2 |
| `url` | 3 — `a[href]`, else cell text, else null |
| `venueDate` | 4 |
| `seatLocation` | 5 — optional-chained, `""` when absent |
| rank `name` / `rank` | 0 / 1 |

---

## Implementation notes

All three live in the injected JS. Kotlin then maps the JSON to the existing DTOs — no DTO changes
needed. `PROFILE_IMAGES` fans three sequential POSTs (not `Promise.all`; the WebView script is
synchronous, so overlapping requests would interleave).
