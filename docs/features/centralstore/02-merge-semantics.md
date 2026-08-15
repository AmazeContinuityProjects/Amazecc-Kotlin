# Merge Semantics

File: `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/UserStore.kt` (+ `UserMerge.kt`)

## Sources & priority tiers

```kotlin
enum class IdentitySource(val order: Int) {
    SESSION(0),         // regNo from login response
    VTOP_PHOTO(1),      // legacy cached photo (pre-store cache key)
    STUDENT(2),         // /api/student — canonical identity
    PROFILE_IMAGES(3),  // /api/profile-images — proctor photo/details, HoD/Dean
    CREDENTIALS(4),     // /api/credentials — linked accounts + ranks (canonical)
    BANK(5),            // /api/bank-info
    APAAR(6),           // /api/apaarid
    RECORDS(7),         // ept / registration / university-day / dayboarder
}
```

Tiers are **fixed and deterministic**. The 9 profile fetches run in parallel; priority tiers make the final merged state independent of arrival order.

## Rules

1. **Filter at extraction.** Extractors only emit filled values: non-null strings, non-blank trimmed strings, non-empty lists. Placeholder values (`"Not set"`, VTOP "select" options) are dropped at extraction. Booleans only emit `true`.
2. **Per-field merge.** Every leaf of `StudentIdentity` is merged independently, keyed by a dotted path (`proctor.email`, `bank.name`, `credentials`, ...).
3. **Filled incoming wins** iff `incoming.order >= currentSource(path)`; the path's source is then recorded. A lower-tier source can never clobber a higher-tier value.
4. **Empty never erases.** `null`/blank/empty/false incoming values are no-ops. A failed endpoint can't wipe the store.
5. **Whole-list replace** for collections (`credentials`, `ranks`, `hodDean`, tables, fields lists). An empty incoming list never erases.
6. **Nested deep merge** for `proctor`, `bank`, `dayboarder`, `apaar` — each inner field follows rules 2–4 with its own path.

## Source bookkeeping

- The store keeps `sources: Map<String, Int>` (dotted path → tier that last wrote it) for the current session.
- After cache restore the map is empty; a **default tier** per path is used until a live merge re-records it:

```kotlin
private fun defaultSourceFor(path: String): Int = when {
    path == "photoBase64" || path == "regNo" || path == "name" -> IdentitySource.STUDENT.order
    path == "proctor" || path.startsWith("proctor.") || path == "hodDean" -> IdentitySource.STUDENT.order
    else -> IdentitySource.SESSION.order
}
```

Defaults are the *lowest* realistic writer so any live re-sync refreshes the value, while cross-source priority is preserved (e.g. a cached `photoBase64` written by PROFILE_IMAGES is only replaced by STUDENT/PROFILE_IMAGES-level writers).

## Conflict examples

| Sequence | Result |
|---|---|
| STUDENT photo → PROFILE_IMAGES photo → STUDENT photo | PROFILE_IMAGES photo (3 ≥ 3 beats 2) |
| PROFILE_IMAGES photo → STUDENT photo | PROFILE_IMAGES photo stays (2 < 3) |
| SESSION regNo → STUDENT regNo | STUDENT regNo (2 ≥ 0) |
| STUDENT proctor{name,email} → PROFILE_IMAGES proctor{phone,photo} | merged: name+email from student, phone+photo from images |
| CREDENTIALS list → empty CREDENTIALS fragment | list kept (empty never erases) |
| `isHosteller=false` incoming | no-op (only `true` propagates) |

## Edge cases

- **Booleans**: `isHosteller`, `hasApaar`, `isDayboarder` — `true` propagates (with tier check); `false` is treated as empty.
- **`apaar == null`** means "not fetched / not generated" — ProfileHub renders "Pending".
- **`dayboarder == null`** means not fetched; `dayboarder.isDayboarder == false` means fetched but no data.
- **Serialization**: cache JSON uses `encodeDefaults = false; explicitNulls = false` — only filled fields are persisted, keeping the blob small and `ignoreUnknownKeys = true` forward-compatible.