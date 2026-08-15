# Consumer Migration

All pages switch from `AppState.<flow>` + per-page processing to one `UserStore.identity` collect. Pages become thin renderers.

## Migration table

| Consumer | Today | After |
|---|---|---|
| `ProfileHub.kt` hero card | 4-way photo chain, `name ?: authorizedID`, `regNo ?: authorizedID`, `authorizedID.take(2)` initials | `identity.displayName`, `identity.displayRegNo`, `identity.photoBase64`, `identity.initials` |
| `ProfileHub.kt` `valueFor` rows | reads 7 flows + `proctorName`/`officialsSummary` helpers | reads deciphered fields: `identity.section`, `identity.proctor?.name`, `identity.eptTables`, `identity.registrationFields/Tables`, `identity.universityDay*`, `identity.dayboarder?.isDayboarder`, `identity.apaar?.hasApaar`, `identity.ranks` |
| `ProfileIdentityPages.kt` PersonalInformation | 1 flow, per-field null-guards | `identity` — same rows, plus newly deciphered `mobile`, `dob`, `gender`, `nativeState`; new "Address & Family" group (addresses, father, mother, guardian) |
| `ProfileIdentityPages.kt` AcademicDetails | 1 flow | `identity` — plus `isHosteller` → "Hosteller / Day Boarder" row |
| `ProfileIdentityPages.kt` UniversityOfficials | `profileImages.proctor/hodDean` + generic `details` map rendering | `identity.proctor` / `identity.hodDean` — typed `Official` cards (role, name, designation, email, phone, school, cabin, department, intercom, facultyId, photo, extras) |
| `ProfileAchievementsPage.kt` | `AppState.credentials` | `identity.credentials` (`AccountCredential`), `identity.ranks` |
| `ProfileRecordsPages.kt` (5 pages) | ~400 lines of JsonElement deciphering | `identity.eptTables`, `registration*`, `universityDay*`, `dayboarder.fields`, `apaar.*` rendered by two generic composables (`VtopTableCard`, `KeyValueCard`) |
| `DashboardWidgets.kt` ProfileHeaderWidget | 3 flows + photo chain | `identity` (photo, first name, initials) |
| `SocialScreen.kt` ShareScheduleTab | `studentProfile?.name ?: authorizedID` | `identity.displayName`, `identity.regNo ?: authorizedID` |
| `CommandRegistry.kt` | `AppState.studentProfile` | `UserStore.identity` (collect → `hasIdentity` gate) |

## Generic record renderers (replaces `ProfileRecordsPages` JSON code)

```kotlin
@Composable fun VtopTableCard(table: VtopTable)      // header row + aligned cells; text lines when headers empty
@Composable fun KeyValueCard(rows: List<KeyValueRow>) // label/value rows (the old LabelValueCard shape)
```

`humanizeKey`, `unwrapObject`, `jsonToDisplay`, `fuzzyScore`, `parseDayboarderFields`, `NOT_SET`, `DayboarderFieldLabels` and friends are deleted from UI code — they live in `IdentityExtractor` now.

## Legacy deletion checklist (after migration, grep-verified)

- [ ] AppState flows: `studentProfile`, `_cachedStudentProfile`, `profileImages`, `credentials`, `bankInfo`, `dayboarder`, `eptSchedule`, `registrationSchedule`, `universityDay`, `apaarId`, `vtopPhotoBase64` (+ `updateStudentProfile`)
- [ ] AppState helpers: `persistProfileImages`, `persistCredentials`, `loadCredentials`
- [ ] Cache keys no longer read/written: `CACHE_STUDENT_PROFILE`, `CACHE_PROFILE_IMAGES`, `CACHE_BANK_INFO`, `CACHE_DAYBOARDER`, `CACHE_EPT_SCHEDULE`, `CACHE_REGISTRATION_SCHEDULE`, `CACHE_UNIVERSITY_DAY`, `CACHE_APAAR_ID`, `CACHE_CREDENTIALS_SECURE` (consts may remain for `SyncEngine` metadata; storage is unused)
- [ ] `ProfileRecordsPages.kt` JSON deciphering block

`cabShareUser`, `_ffcsRegistration`, `_pendingExamSeatAlerts`, `KEY_EXAM_SEAT_ALERTED`, `CACHE_FFCS_REG_INFO` are unrelated to identity and stay as-is.