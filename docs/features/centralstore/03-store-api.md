# Store API & Integration

## `UserStore` — `state/UserStore.kt`

```kotlin
object UserStore {
    val identity: StateFlow<StudentIdentity>          // the single source of truth
    fun merge(fragment: StudentIdentity, source: IdentitySource)
    fun loadFromCache()                               // restores CACHE_USER_IDENTITY (encrypted)
    fun clear()                                       // logout: empty identity + sources
    fun persist()                                     // internal: writes encrypted cache
}
```

- Persists on every merge (merges are rare — only during sweeps/login).
- Cache key: `CACHE_USER_IDENTITY` (new, in `SettingsManager`), value = `Encryption.encryptOrPlain(json)` — the blob contains aadhar/bank/credential passwords, so it is never stored plaintext.
- The old encrypted `CACHE_CREDENTIALS_SECURE` and the plain per-endpoint profile caches become legacy and are no longer read/written.

## `IdentityExtractor` — `state/IdentityExtractor.kt`

Pure functions; each returns a `StudentIdentity` fragment containing **only filled fields**:

| Function | Source |
|---|---|
| `fromSession(authorizedID)` | login response |
| `fromVtopPhoto(base64)` | legacy `CACHE_VTOP_PHOTO` |
| `fromStudentProfile(StudentProfileRes?)` | `/api/student` |
| `fromProfileImages(ProfileImagesRes?)` | `/api/profile-images` |
| `fromCredentials(CredentialsRes?)` | `/api/credentials` |
| `fromBankInfo(BankInfoRes?)` | `/api/bank-info` |
| `fromDayboarder(DayboarderRes?)` | `/api/dayboarder` |
| `fromEptSchedule(EptScheduleRes?)` | `/api/ept-schedule` |
| `fromRegistrationSchedule(RegistrationScheduleRes?)` | `/api/registration-schedule` |
| `fromUniversityDay(UniversityDayRes?)` | `/api/university-day` |
| `fromApaarId(ApaarIdRes?)` | `/api/apaarid` |

The generic VTOP JSON deciphering that used to live in `ProfileRecordsPages.kt` (table normalization, label/value unwrapping, humanizeKey, fuzzy dayboarder matching, select-option resolution) moves here as internal helpers.

## AppState wiring

### Sync lambdas (2 sites: `loadAllData` sweep and `refreshProfile`)

```kotlin
// Before
syncModule("Student Profile", { AmazeClient.getStudentProfile() }, ...) {
    _studentProfile.value = it.data
    _cachedStudentProfile.value = it
    cacheData(SettingsManager.CACHE_STUDENT_PROFILE, it)
}

// After
syncModule("Student Profile", { AmazeClient.getStudentProfile() }, ...) {
    UserStore.merge(IdentityExtractor.fromStudentProfile(it), IdentitySource.STUDENT)
}
```

Every profile module follows the same pattern (`PROFILE_IMAGES`, `CREDENTIALS`, `BANK`, `DAYBOARDER`, `EPT`, `REGISTRATION` (RECORDS + still calls `updateFfcsRegistration(parseFfcsRegistration(it))`), `UNIVERSITY_DAY` (RECORDS), `APAAR`).

### Other hooks

| Hook | Change |
|---|---|
| login handlers (4 sites) | `UserStore.merge(IdentityExtractor.fromSession(loginRes.authorizedID), IdentitySource.SESSION)` |
| `loadCachedData()` | `UserStore.loadFromCache()` replaces the 8 per-endpoint profile cache loads + `loadCredentials()`; then merge legacy vtop photo with `VTOP_PHOTO`; then `reconcileExamSeatAlerts()` |
| `logout()` | `UserStore.clear()` replaces the 8 flow nulls |
| `saveOffline()` | profile lines removed (store persists itself) |
| `reconcileExamSeatAlerts()` | reads `UserStore.identity.value.credentials` instead of `_credentials.value` |
| `persistProfileImages` / `persistCredentials` / `loadCredentials` | deleted |
| flows `studentProfile`, `profileImages`, `credentials`, `bankInfo`, `dayboarder`, `eptSchedule`, `registrationSchedule`, `universityDay`, `apaarId`, `_cachedStudentProfile`, `vtopPhotoBase64`, `updateStudentProfile` | deleted after consumers migrate |

Demo mode needs no special handling: `AmazeClient` mock payloads flow through the same extractors and merge path.