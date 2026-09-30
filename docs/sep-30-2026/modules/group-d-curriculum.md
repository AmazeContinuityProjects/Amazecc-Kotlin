# Group D — Curriculum

Highest effort of the port. Two stages, non-positional column mapping, four table-discovery
fallbacks. Do it last.

Parser: `AmazeCC-API/src/lib/parsers/curriculum.ts` (244 lines).

---

## Stage 1 — the category list

`POST /vtop/academics/common/Curriculum`, shared `verifyMenu` body. Referer is the endpoint itself.

Then `extractPageCsrf(html)` = `$('input[name="_csrf"]').first().val()`.

### parseCurriculum

- `totalCredits` from `span:contains('Total Credits:')` text, regex `/Total Credits:\s*(\d+)/`
- iterate **`.categoty-card`** — the typo is VTOP's real markup, reproduce it exactly
- per card:
  - `code` from the first `[onclick]` via `/categoryOnClick\('([^']+)'\)/`; fallback
    `.symbol-label` first, `.trim().split("\n")[0].trim()`
  - `name` from `.text-sm`
  - `credits` from `small:contains('Credit:')`'s **parent** text, regex `/Credit:\s*(\d+)/`
  - `maxCredits` from `small:contains('Max. Credit:')`'s parent text,
    regex `/Max.\s*Credit:\s*(\d+)/`
- pushes to both `categories[]` and `details[]` (the latter with an empty `baskets`)

**`:contains()` is a cheerio/jQuery extension.** In the injected JS use
`Array.prototype.some.call(spans, s => s.textContent.indexOf('Total Credits:') >= 0)` — a CSS
selector will not work.

---

## Stage 2 — the category detail fan-out

`POST /vtop/academics/common/curriculumCategoryView`, once per category, **using the
stage-1 `pageCsrf`**, not the login csrf.

```
_csrf=<pageCsrf>&categoryId=<cat.code>&authorizedID=<id>&x=<new Date().toUTCString()>
```

**`x` is a UTC date string here, not epoch milliseconds** — the only route in the codebase that
does this. Everything else uses `nocache=<epochMs>` or `x=<epochMs>`.

`$("script, style").remove()` before parsing.

### Table discovery — four strategies, first non-empty wins

1. **Bootstrap tabs.** `.nav-tabs button, .nav-tabs a, [role=tab]` → pane =
   `$("#" + (data-bs-target || href).replace(/^#/, ""))`, else `.tab-pane` at the same index.
   Table = first table inside the pane. Requires more than one `tr`.
2. **Cards/panels.** `.card, .panel, [class*=card]`; header from a direct-child
   `> .card-header, > .panel-heading, > .card-heading, > .header`; table from
   `> .card-body table, > .panel-body table, > .body table, table`.
3. **Headings.** `h4, h5, h6, strong.heading, .section-title, .group-label` then
   `nextAll("table").first()`.
4. **Any table.** Title from `caption`, else a single `colspan` cell in row 0, else the previous
   sibling's text.

### parseTable — header-regex column mapping, NOT positional

**DataTables responsive detail map first.** `.dtr-details, ul.dtr-details` → `li`:
`rowIdx = parseInt(closest("tr").data("dt-row"))`, `colIdx = parseInt(li.attr("data-dtr-index"))`,
key = `.dtr-title` text lowercased, value = `.dtr-data` text. These take priority over cell
fallbacks.

**Header row detection:** a row is the header when it has any `th`, or exactly one `td` carrying
`colspan`. Cells with `colspan` are skipped while collecting headers; at most 10 headers.

**Column index mapping** — first match wins per field, so an `if / else if` chain:

| Regex on lowercased header | Field |
|---|---|
| `/course\s*(code\|no)\|s\.?no\|#\|code\|paper\s*code/i` | `codeIdx` |
| `/course\s*name\|subject\|title\|paper/i` | `nameIdx` |
| `/credit\|cr/i` | `creditIdx` |
| `/type\|category\|mode/i` | `typeIdx` |

**Row loop:** skip row 0 when it is the header; skip single-cell rows carrying `colspan`. Try dtr
values first, then cell fallbacks, then auto-detection: column 0 matching `/^[A-Z0-9]{2,}$/` after
whitespace strip is the code, column 1 is the name, and the first cell matching `/^\d+(\.\d+)?$/`
is the credits.

Basket credits = sum of its item credits. Default basket title `"Courses"`.

---

## Response

```
{ success, pageCsrf, title, totalCredits,
  categories: [{code, name, credits, maxCredits}],
  details: [{code, name, baskets: [{title, credits, items: [{code, name, credits, type}]}]}] }
```

---

## Binary downloads

Both are plain byte passthrough, no parsing. Both **append `_csrf` twice** — reproduce it, VTOP
apparently requires it.

| Route | Endpoint | Extra params | Referer |
|---|---|---|---|
| syllabus | `POST /vtop/courseSyllabusDownload1` | `courseCode` | `/vtop/academics/common/Curriculum` |
| download | `POST /vtop/academics/curriculDown` | `regNo=NONE` | `/vtop/academics/common/Curriculum` |

Both use `redirect: "follow"`, pass through the upstream `content-type` and
`content-disposition`, and fall back to `zip` when the content type contains `zip`, else `pdf`.

The `getSyllabusPdf` path already has an `AmazeClient.refreshSession()` call at line ~1189, which is
what keeps a long download from failing on an expired session.

---

## Implementation note

Stage 2 fans out per category. The server used `Promise.all`; the WebView script is synchronous so
these must be **sequential**, or the requests will interleave on the same JS thread. Expect slower
loads than the server version.
