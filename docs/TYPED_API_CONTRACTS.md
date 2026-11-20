# Typed API contracts — source of truth

`AmazeClient.kt` is the only HTTP client in this app. Its DTOs are **mirrors** of the
contracts owned by the sibling projects; they are not invented locally.

## Authoritative sources

| Contract | Source of truth | Kotlin mirror |
|---|---|---|
| VTOP data payloads | `../amazecc-api-hono/src/types/data/*.ts` | `shared/.../model/*.kt` |
| Unified domain models | `../AmazeCC/src/types/*.ts` (barrel: `src/types/index.ts`) | `shared/.../model/*.kt`, `AppDataStore` snapshots |
| Route inventory | `../amazecc-api-hono/README.md` (route map) | `AmazeClient.kt` endpoint functions |

The deleted `network/hono/HonoApiClient.kt` claimed to be "the typed client" but used a
bearer-token auth model that belongs to the **admin** surface, not to `/api/remote/*`
user data. It had zero callers. Deleting it did not remove the typed contract — the
contract lives in the sibling repos, and the Kotlin models are the mirror.

## Rules for AmazeClient and its models

1. **Decode into a `@Serializable` DTO**, never `JsonObject`/manual parsing. If a temporary
   `JsonObject` is unavoidable, add the typed DTO in the same change.
2. **Mirror field names exactly** (camelCase; `@SerialName` only when the wire name differs).
3. **Keep mirrors additive and nullable with defaults** so an older server payload still decodes
   (`ignoreUnknownKeys = true` is already set on the shared client).
4. **When the TS contract changes, update the Kotlin mirror in the same commit.** Add a row to the
   table below for any new endpoint.
5. **No second client.** New endpoints go into `AmazeClient`; new models go into `model/`.

## Current mapping

| Endpoint family | TS contract | Kotlin DTO |
|---|---|---|
| `/api/remote/attendance` | `data/attendance.ts` | `AttendanceRes`, `AttendanceItem` |
| `/api/remote/marks` | `data/marks.ts` | `MarksRes`, `MarksCourseItem`, `AssessmentItem` |
| `/api/remote/grades` | `data/allgrades.ts` | `AllGradesRes`, `GradeItem` |
| `/api/remote/schedule` | `data/schedule.ts` | `ExamScheduleRes` |
| `/api/remote/semTT` | `data/semTT.ts` | `TimetableRes` |
| `/api/remote/hostel` | `data/hostel.ts` | `HostelDetails` |
| `/api/remote/whoami` | `types/auth.ts`, `HonoUserIdentity` shape | `MeRes`, `StudentIdentity` |

## Known drift (to resolve, not to ignore)

- `marks.ts` declares `CourseItem.credits: number`; `MarksCourseItem.credits` is `String?`.
  A numeric payload will fail to decode. Resolve with a dual number/string serializer once a
  captured payload confirms the wire type.
- Kotlin-only extension fields (`component`, `totalMark`, `maxMark`, `logs`, `success`, `message`)
  are intentional app-side derivations; keep them clearly separated from contract fields.
- `attendance.ts` types `slNo`, `registrationDate`, `attendanceDate`, `classId` and
  `marks.ts` types `slNo`, `courseMode`; these were added to the Kotlin mirrors as nullable
  additive fields so no data is dropped on decode.

## Verification

After any contract change, run the parity test suite (once a JDK is available):

```
./gradlew.bat :shared:jvmTest --tests '*ParityTest'
```

`StudentIdentityParityTest` is the existing precedent for this kind of check.
