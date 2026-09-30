# Group A — Generic page parse

Five modules share one extractor. They differ only by endpoint and by a handful of
module-specific fields.

## Modules

| Module | Endpoint |
|---|---|
| APAAR | `/vtop/apaarid/upload` |
| EPT_SCHEDULE | `/vtop/compre/eptScheduleShow` |
| REGISTRATION_SCHEDULE | `/vtop/examinations/hostelDetails` |
| UNIVERSITY_DAY | `/vtop/event/uday/certificates` |
| DAYBOARDER | `/vtop/admissions/dayboarderForMenu` |

## The shared parse

`AmazeCC-API/src/lib/parsers/auto-parse.ts` → `parseVtopHtml(html)` returns:

```ts
{
  title: string,              // h3.box-title first, else <title> first
  selectOptions: Record<string, {value,text,selected}[]>,
  tables: {caption?, headers: string[], rows: Record<string,string>[]}[],
  keyValuePairs: Record<string,string>,
  formFields: Record<string,string>,
  hiddenFields: Record<string,string>,
  messages: {warning?, error?, success?},
}
```

### title
`$("h3.box-title").first().text().trim()`, falling back to `$("title").first().text().trim()` when empty.

### hiddenFields
Selector `input[type=hidden]`. Key = `name` attr, else `id` attr, else skipped. Value = `value` attr or `""`.

### selectOptions
Selector `select`. Key = `name`, else `id`, else the literal `"select"`. Each `option` →
`{value: attr("value")||"", text: text().trim(), selected: attr("selected")!==undefined}`. Recorded
only when the select has at least one option. **A `select` never appears in `formFields`.**

### formFields
Selector `input:not([type=hidden]), textarea`. Key = `name`, else `id`, else skipped. Value =
`attr("value")` first, else `text().trim()` (this is how `<textarea>` is read), else `""`.
No `checked` handling. Duplicate names → last wins.

### tables
Per `table`, in document order.

- `caption` = first `caption` text trimmed; omitted when empty.
- **Header row detection** — first match wins:
  1. row with more than one `<th>`, else
  2. row with more than one `<td>` and no `td` in that row with `colspan > 3`.

  No match → the whole table is skipped.
- `headers` from the header row's `th, td` in DOM order; whitespace collapsed; **empty text
  becomes `col${headers.length}`**, so the first empty header is `col0`.
- `rows` from every `tr` **after** the header row. Each `td` at index `i`; empty cells are
  skipped but **still consume an index** (index is the `td` ordinal, not a running counter). Key is
  `headers[i] || "col${i}"`. Rows with no non-empty cell are dropped.
- Recorded only when there is at least one header and one row.

### keyValuePairs
**Only the first `table` in the document.** For each `tr`, `find("td")` must be exactly 2.
Label = cell 0, whitespace collapsed. Value = cell 1, trimmed only. Requires
`label && value && label !== value && !label.startsWith("<")`. Key is camelCased.

### messages
`$("input#warning, input[name=warning]").val()`, and the same for `#error` / `#success`. Assigned
only when truthy, so `messages` may be `{}`.

### camelCase (auto-parse flavour — differs from the qcm-view one)
```
first char lowercased
[A-Z]        -> uppercase
[-_\s]\w     -> uppercase the word, drop the separator
remaining [- ] dropped
```
`"IFSC Code"` → `ifscCode`, `"Date of Birth"` → `dateOfBirth`, `"Account No."` → `accountNo.`

## Module-specific

### APAAR — `hasApaar` heuristic
`AmazeCC-API/src/app/api/apaarid/route.ts:66-72`. `&&` binds tighter than `||`, so it is an OR of:

1. `keyValuePairs` is non-empty
2. any table has ≥ 1 row
3. any `formFields` value is non-empty, `length > 4`, `!= "-"`, and does not start with `"0"`
4. raw HTML matches `/\.pdf/i`
5. raw HTML matches `/already uploaded|submitted successfully/i`

Conditions 4 and 5 need the **raw response body**, not the parsed object, so the extractor must
see the HTML before it is reduced.

## Implementation shape

One JS function returning the full `ParsedVtopPage` JSON, plus the raw-body regex flags for APAAR.
`VtopDataSource` then maps it per module. Reuse the existing `VtopScripts` `wrapped()` helper and
`VtopRows.parse` JSON-decoding style.

**Landmine:** cheerio's `:contains()` is a jQuery extension and is not available — use
`text().indexOf('Total Credits:') >= 0` style checks in the injected JS.
