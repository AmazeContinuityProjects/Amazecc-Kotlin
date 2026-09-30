# Group E — Other VIT hosts

Three hosts, three auth models, none of them VTOP.

---

## LMS — `lms.vit.ac.in` (Moodle)

`AmazeCC-API/src/app/api/lms-data/route.ts`. Uses `LMSClient` (axios, **no timeout set** — a
separate bug worth fixing, see below).

### Login flow

```
1. GET  https://lms.vit.ac.in/login/index.php
   -> cookies = set-cookie joined with "; "
   -> token   = $('input[name="logintoken"]').val()

2. POST https://lms.vit.ac.in/login/index.php        maxRedirects: 0, validateStatus: always
   body: logintoken=<token>&username=<u>&password=<p>     (no anchor param)
   -> loginCookies = post set-cookie joined, else the GET cookies
   -> redirectUrl  = post Location header

3. GET  <redirectUrl>   with Cookie: loginCookies
   -> sesskey = /"sesskey":"([^"]+)"/ match

   if no sesskey -> throw "Cannot find sesskey"
```

`sesskey` is scraped but **never used** by the current route — the
`core_calendar_get_calendar_monthly_view` calls are commented out. `src/app/api/vitol-data/route.ts`
is the reference if a sesskey-consuming variant is ever needed.

Request body from the app is `{username, pass}` — `pass`, not `password`.

### Calendar events
`td.day.hasevent` → `data-day`, and `a[data-day]` → `data-month` / `data-year`. Events from
`[data-region="event-item"] a[data-action="view-event"]` → `.eventname` text and `href`.

### Per-event scrape
- `courseLink` = first `ol.breadcrumb li.breadcrumb-item a` href → `id` query param = courseId
- `moduleId` = the event link's `id` query param
- if both present, one extra `GET /course/view.php?id=${courseId}`
- `teachers` from `extractTeacherForModule`: `#module-${moduleId}` → closest `li[id^="section-"]` →
  `h3.sectionname` → `/^(Dr\.?\s+[A-Za-z.\s]+)/i`; falls back to the whole section title
- `name` = `` `${courseCodeFull}/${courseNameFull}/${assignmentName}` `` where course code/name come
  from the first breadcrumb's text and `title` attribute, and the assignment name from `h1.h2`
- `due` from `div.activity-dates strong:contains("Due:")`'s parent with `Due:` stripped — this is
  another **cheerio `:contains()`**, not a CSS selector
- `done` = presence of `[data-region="completion-info"] button.btn-success`

Response is a **bare JSON array**: `{name, due, done, day, month, year, url, teachers[]}`. Failed
per-event scrapes are dropped rather than surfaced.

---

## EVENTS — `eventhubcc.vit.ac.in` (public)

`GET https://eventhubcc.vit.ac.in/EventHub/` with only a User-Agent. **No auth at all.**

### Parse
Iterate `#events .card`:
- `title` = first `.card-title span`
- `eid` = `button[name="eid"]` **value attribute** — it is a button, not a link
- skip when either is empty
- **poster** by regex over the card's outer + inner HTML: first `<img[^>]+src=["']([^"']+)["']`;
  if none, `background(?:-image)?\s*:\s*url\(['"]?([^'")]+)['"]?\)`
  - relative and not `data:` → prefix with `https://eventhubcc.vit.ac.in` when it starts with `/`,
    else `https://eventhubcc.vit.ac.in/EventHub/`
- metadata by **Font Awesome class sniffing**: for each `div`, look at the div's **inner HTML** for
  `fa-people-carry-box` / `fa-user-large` → eligibility; `fa-calendar-days` → date;
  `fa-map-location-dot` → location; `fa-indian-rupee-sign` → price. Text wrapped in parentheses
  → type.
  **The chain is `else if` inside a loop, so the LAST matching div wins per field.**

Response is a **bare JSON array**: `{eid, title, eligibility, type, date, location, price, posterUrl}`.

---

## EVENTS_PROFILE — `eventhubcc.vit.ac.in` (authenticated)

### Auth — `src/lib/eventHubAuth.ts`

Cached-session form: `"<JSESSIONID>"` or `"<JSESSIONID>; cookiesession1=<value>"`, passed back as the
single `jsessionid` field.

```
POST https://eventhubcc.vit.ac.in/EventHub/mainDashboard
     redirect: manual
     body: username, password, validateVitian=1        (no CSRF token)
  -> Set-Cookie must contain JSESSIONID
```

Both `JSESSIONID` and `cookiesession1` matter for the profile page.

`looksLikeLoginPage(html)` = `/action=["']\/EventHub\/mainDashboard["']/i`.

### Flow
1. auth, or 401
2. `GET /EventHub/profile` with `Cookie` + User-Agent
3. if the login page came back and credentials were supplied → re-login once, retry, adopt the new
   cookie. Otherwise 401 with `reason: "session_expired" | "invalid_credentials"` and
   `reauthenticate: expired`

### Parse
Per `table`, the first row's lowercased text must contain `event` **and** one of
`order` / `payment` / `receipt` to be considered. Then per `tr` (skipping row 0), requires
**≥ 8 `td`**:

| idx | field |
|---|---|
| 0 | *unused* (S.No) |
| 1 | `name` |
| 2 | `orderId` |
| 3 | `date` |
| 4 | `venue` |
| 5 | `time` |
| 6 | `paymentStatus` |
| 7..n | scanned for `button, a, input` |

Link extraction from the tail cells, in priority order per control:
- `eid` — `getRecepit('x')` onclick, else `studentRecepit/<id>` href, else `paynow('x')` onclick
- `receiptLink` — text contains `receipt`: `href`, else a synthesised
  `/EventHub/studentRecepit/<eid>/`
- `certificateLink` — text contains `certificate` or `download`
- `payNowLink` — text contains `pay now` or onclick contains `paynow`; else
  `window.location.href = '...'`
- `payLaterLink` — text contains `pay later`

`action` is `href` or `formaction`, ignored when it is `#`. All four links are `null` when absent.

Response: `{events: [{name, eid, orderId, date, time, venue, paymentStatus, receiptLink,
certificateLink, payNowLink, payLaterLink}]}`.

### Related EventHub routes (for a complete port)
| Route | Call |
|---|---|
| `events/login` | `POST /EventHub/mainDashboard` → `{success, jsessionid}` |
| `events/preview` | `POST /EventHub/eventPreview` body `eid`; poster is `https://eventhubcc.vit.ac.in/EventHub/image/?id={eid}` |
| `events/register` | `POST /EventHub/eventPreview` then `POST /EventHub/registerEvent` — injects `<base href>` into a JS/payroll bridge |
| `events/download` | authenticated proxy GET of a receipt/certificate URL |
| `events/paynow` | rewrites redirect pages, injects a login form posting to `/EventHub/mainDashboard` |

---

## Cross-cutting: TLS

Every route above disables certificate verification — VTOP via
`new https.Agent({rejectUnauthorized: false})`, EventHub and the raw-`fetch` downloads via
`NODE_TLS_REJECT_UNAUTHORIZED=0`.

The on-device port handles this with the bundled PEMs in
`androidApp/src/main/res/raw/` plus `res/xml/network_security_config.xml`, which augments rather
than replaces the system trust store. LMS, EventHub and VTOP PEMs are all already copied.

## Known bug to fix alongside LMS

`AmazeCC-API/src/lib/clients/LMSClient.ts` sets **no timeout**, so axios uses `timeout: 0` —
infinite. A hanging connect holds the route for the OS default (~2 min). `VTOPClient.ts:27`
correctly sets `timeout: 20000`. The Kotlin port must not reproduce this.
