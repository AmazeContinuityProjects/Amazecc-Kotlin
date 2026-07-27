# Memory Leak & High RAM Usage Fixes

## Current State: ~150MB on Android (too high)

---

## 🚨 HIGH Priority

### 1. All-Semester Attendance/Marks Accumulate Forever

**Files:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`
**Lines:** 264, 552, 773-800, 1219-1226

```kotlin
private val _allSemesterAttendance = MutableStateFlow<Map<String, AttendanceRes?>>(emptyMap())
private val _allSemesterMarks = MutableStateFlow<Map<String, MarksRes>>(emptyMap())
```

Every semester's attendance and marks data is added to these maps and **never evicted**. After 8 semesters, all data stays in memory permanently. Only cleared on logout.

**Fix:**
- Keep only current + last semester in memory
- Persist older semesters to disk cache only
- Lazy-load from cache when user navigates to old semester view

---

### 2. `_cabJoinRequests` Map Grows Unbounded

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`
**Lines:** 596, 1866-1868

```kotlin
private val _cabJoinRequests = MutableStateFlow<Map<String, CabJoinRequestsRes>>(emptyMap())
```

Entries added per trip ID, never removed. Accumulates over app lifetime.

**Fix:** Evict entries older than 1 hour, or cap at N entries (e.g., 20), or only keep the last request.

---

### 3. `_logLines` in SyncEngine Grows Unbounded

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/SyncEngine.kt`
**Lines:** 85, 133

```kotlin
private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
// Line 133:
_logLines.value = _logLines.value + LogLine(...)
```

Every log appends to an ever-growing list, recreated on each append. No cap.

**Fix:**
```kotlin
private const val MAX_LOG_LINES = 500
// In addLog():
_logLines.value = (_logLines.value + LogLine(...)).takeLast(MAX_LOG_LINES)
```

---

### 4. Kamel Image Cache Has NO Size Limit

**File:** `shared/build.gradle.kts` (line 47: `implementation(libs.kamel.image)`)

No `KamelConfig` is configured anywhere in the project. Kamel's default in-memory cache has no eviction policy or size limit. Images loaded via Kamel stay in memory indefinitely.

**Fix:** Create a `KamelConfig` with explicit memory/disk limits:

```kotlin
// In App.kt or a config module:
val kamelConfig = KamelConfig {
    memoryCache {
        maxSize = 50 * 1024 * 1024 // 50 MB
    }
    diskCache {
        directory = "kamel-cache"
        maxSize = 100 * 1024 * 1024 // 100 MB
    }
}
```

---

### 5. AuthKamelImage Stores Raw ByteArray Per URL

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/events/EventHubScreen.kt`
**Lines:** 48-61

```kotlin
var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
LaunchedEffect(url) {
    bytes = AmazeClient.getImageBytes(url)
}
```

Each event image's raw bytes are held in composable memory (`remember`). Scrolling through many events accumulates all image byte arrays simultaneously.

**Fix:** Use Kamel's `AsyncImage` instead of manual byte array fetching, or clear bytes when the item scrolls out of view.

---

## 🔶 MEDIUM Priority

### 6. Base64 Profile Photo Retained Permanently

**Files:**
- `DashboardScreen.kt:240`
- `ProfileScreen.kt:96`

```kotlin
val decodedBitmap = remember(photoBase64) {
    decodeBase64Bytes().toImageBitmap()
}
```

Decoded `ImageBitmap` (typically ~500KB each) is retained for the entire composable lifetime. If `DashboardScreen` or `ProfileScreen` is never disposed, the bitmap stays forever.

**Fix:** Decode only when visible; release bitmap when component leaves composition using `DisposableEffect`.

---

### 7. Syllabus PDF ByteArray Held in Composable

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CourseDetailScreen.kt`
**Lines:** 1332-1348

```kotlin
var syllabusBytes by remember { mutableStateOf<ByteArray?>(null) }
// LaunchedEffect loads full PDF bytes
```

Large PDFs (potentially MBs) are loaded as `ByteArray` and held while the tab is alive.

**Fix:** Stream PDF bytes instead of loading entire blob, or release on tab dispose.

---

### 8. `backstack` Has No Size Limit

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`
**Line:** 79

```kotlin
private val backstack = mutableListOf<Screen>()
```

No bound on navigation depth. Deep navigation could accumulate hundreds of entries.

**Fix:** Cap at 20 entries (pop oldest when exceeded).

---

### 9. Settings Cache Deserializes ALL Data on Startup

**Files:**
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/repository/SettingsManager.kt` (lines 47-84)
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt` (lines 356-418)

All cached API responses are loaded and deserialized into memory on app startup, regardless of whether the user visits those screens.

**Fix:** Lazy-load cached data — only deserialize when the corresponding screen is first opened. Add TTL-based eviction.

---

### 10. FriendsViewModel Lists Grow Unbounded

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/FriendsViewModel.kt`
**Lines:** 17-21

```kotlin
private val _friends = MutableStateFlow<List<Friend>>(emptyList())
private val _groups = MutableStateFlow<List<FriendGroup>>(emptyList())
```

No limit or pagination on friend/group lists.

**Fix:** Add pagination or cap friend list at a reasonable number.

---

## 🔸 LOW Priority

### 11. Embedded CSV String in FfcsReportData.kt

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/data/FfcsReportData.kt`

165KB CSV string literal embedded in source code. ~326KB at runtime (UTF-16). On Android DEX, strings > 65535 chars cause the compiler to split + concatenate at runtime.

**Fix:** Load from `Res.readBytes("files/ffcsReport.csv")` with cached parsed result. See `CsvDataLoader.md` plan.

---

### 12. AppState Singleton Holds 30+ StateFlows

**File:** `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`
**Line:** 44 (`object AppState`)

All API responses are stored as `MutableStateFlow` on the singleton. None are ever released until process death.

**Fix:** Use a per-screen/local state management approach instead of singleton. Or add explicit `clearXxx()` methods called when screens are disposed.

---

### 13. Compose Screens Retain Large Lists in `remember`

Multiple screens hold large state in `remember` (faculty lists, courses, questions, books, etc.). While scoped to the composable lifecycle, screens kept in the navigation graph retain this data indefinitely.

**Fix:** Ensure navigation properly disposes of composable state when going back (use `popBackStack` or scoped navigation).

---

## Estimated Memory Savings

| Fix | Estimated Reduction | Effort |
|-----|-------------------|--------|
| #1 Semester maps eviction | 5-40 MB | Medium |
| #4 KamelConfig cache limits | 10-50 MB | Low |
| #5 AuthKamelImage → Kamel | 5-20 MB | Medium |
| #6 Profile photo release | 0.5-2 MB | Low |
| #9 Lazy cache loading | 2-10 MB | Medium |
| #11 CSV to resource | 0.3-0.5 MB + DEX | Low |
| #3 Log lines cap | Minimal | Low |
| #2 Cab requests eviction | Minimal | Low |
| #8 Backstack cap | Minimal | Low |
| **Total estimated** | **~25-120 MB** | |

---

## Recommended Order

1. **#4 KamelConfig** — quick win, high impact, low effort
2. **#11 Load CSV from resource** — our change, good to fix  
3. **#3 Cap log lines** — trivial one-liner
4. **#2 Cab requests eviction** — easy condition check
5. **#5 Replace AuthKamelImage with Kamel** — medium effort
6. **#6 Profile photo lifecycle** — low effort
7. **#9 Lazy cache loading** — medium effort, significant savings
8. **#1 Semester maps eviction** — medium-high effort
