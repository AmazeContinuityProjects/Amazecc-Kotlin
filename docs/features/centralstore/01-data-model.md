# Data Model — `StudentIdentity`

File: `shared/src/commonMain/kotlin/com/amazecc/app/shared/model/StudentIdentity.kt`

## Principles

- **All fields optional.** Absent data is `null` / empty list / `false` — never a placeholder string.
- **No raw JSON.** `JsonElement`, `tables`, `keyValuePairs`, `formFields` never appear in the model. VTOP pages are deciphered into `VtopTable` / `KeyValueRow` (clean string cells only).
- **Typed where meaningful.** Known VTOP labels become typed fields (proctor `email`, `phone`, `cabin`, ...); unrecognised labels surface in `extras` — nothing lost, nothing raw.

## Model

```kotlin
@Serializable
data class KeyValueRow(label: String, value: String)          // both non-blank after filtering

@Serializable
data class VtopTable(
    caption: String?,                                          // table caption if the payload had one
    headers: List<String>,                                     // column headers (order preserved)
    rows: List<List<String>>,                                  // cells aligned to headers; empty rows dropped
)                                                              // headers == [] ⇒ rows are plain text lines

@Serializable
data class Official(
    role: String?,                                             // "Proctor" / HoD role / Dean role
    name: String?, designation: String?, email: String?,
    phone: String?, school: String?, cabin: String?,
    department: String?, intercom: String?, facultyId: String?,
    photoBase64: String?,
    extras: List<KeyValueRow>,                                 // unrecognised VTOP labels, cleaned
)

@Serializable
data class AccountCredential(
    account: String, username: String, password: String,       // was defaultCredentials
    url: String?, venueDate: String, seatLocation: String,
)

@Serializable
data class RankInfo(name: String, rank: String)

@Serializable
data class DayboarderInfo(
    isDayboarder: Boolean,                                     // inferred: any real field present
    fields: List<KeyValueRow>,                                 // canonical order via fuzzy label matching
)

@Serializable
data class ApaarInfo(
    hasApaar: Boolean,
    fields: List<KeyValueRow>,
    tables: List<VtopTable>,
)

@Serializable
data class BankInfo(
    name: String?, branch: String?, address: String?,          // deciphered from bankDetails object
    fields: List<KeyValueRow>,
)

@Serializable
data class StudentIdentity(
    // ── Identity core ──
    regNo: String?, name: String?, email: String?, mobile: String?,
    dob: String?, gender: String?, bloodGroup: String?,
    photoBase64: String?, isHosteller: Boolean = false,
    // ── Academic ──
    program: String?, campus: String?, batch: String?,
    section: String?, advisorName: String?,
    // ── Personal ──
    nationality: String?, nativeLanguage: String?, nativeState: String?,
    community: String?, religion: String?, caste: String?,
    physicallyChallenged: String?, aadharNumber: String?,
    // ── Family & residence ──
    currentAddress: List<KeyValueRow>, permanentAddress: List<KeyValueRow>,
    father: List<KeyValueRow>, mother: List<KeyValueRow>, guardian: String?,
    // ── Officials ──
    proctor: Official?, hodDean: List<Official>,
    // ── Credentials & ranks ──
    credentials: List<AccountCredential>, ranks: List<RankInfo>,
    // ── Records (deciphered) ──
    eptTables: List<VtopTable>,
    registrationFields: List<KeyValueRow>, registrationTables: List<VtopTable>,
    universityDayTitle: String?,
    universityDayFields: List<KeyValueRow>, universityDayTables: List<VtopTable>,
    dayboarder: DayboarderInfo?, apaar: ApaarInfo?, bank: BankInfo?,
) {
    val displayName: String        // name ?: regNo ?: "Student"
    val displayRegNo: String       // regNo ?: ""
    val initials: String           // up to 2 letters from displayName, "?" fallback
    val hasIdentity: Boolean       // any of regNo/name/photo present
}
```

## Source → field mapping

| `StudentIdentity` field | `/api/student` | `/api/profile-images` | `/api/credentials` | others |
|---|---|---|---|---|
| `regNo` | `profile.applicationNumber` | — | — | login `authorizedID` |
| `name` | `profile.name` | — | — | — |
| `email` / `mobile` | `profile.email` / `mobileNumber` | — | — | — |
| `dob`, `gender`, `isHosteller` | `profile.dob` / `gender` / `isHosteller` | — | — | — |
| `photoBase64` | `profile.image` | `studentPhoto` / `student.photoBase64` / `profile.photoBase64` | — | legacy `CACHE_VTOP_PHOTO` |
| `program`, `campus`, `batch`, `section`, `advisorName` | `appliedDegree` / `campus` / `batch` / `section` / `advisorName` | — | — | — |
| `bloodGroup`, personal fields | `bloodGroup`, `nationality`, ... | — | — | — |
| `currentAddress` / `permanentAddress` / `father` / `mother` / `guardian` | nested objects, cleaned to `KeyValueRow` | — | — | — |
| `proctor` | `profile.proctor` (typed) | `proctor.{photoBase64, details}` (details map → typed + extras) | — | — |
| `hodDean` | — | `hodDean.people[]` | — | — |
| `credentials` / `ranks` | — | (ignored — same data) | `credentials` / `ranks` (canonical) | — |
| `eptTables` | — | — | — | `/api/ept-schedule` `tables` |
| `registrationFields/Tables` | — | — | — | `/api/registration-schedule` |
| `universityDay*` | — | — | — | `/api/university-day` |
| `dayboarder` | — | — | — | `/api/dayboarder` `fields` (fuzzy-matched) |
| `apaar` | — | — | — | `/api/apaarid` |
| `bank` | — | — | — | `/api/bank-info` |

## `StudentProfile` transport model

`StudentProfile` (the `/api/student` DTO) is extended with the fields the API already returns but the app previously dropped: `dob`, `gender`, `isHosteller`, `nativeState`, `currentAddress`, `permanentAddress`, `father`, `mother`, `guardian`, `proctor`. Nested objects use `Map<String, JsonElement>` (VTOP may emit `null` values) and are filtered to clean `KeyValueRow`s by the extractor.