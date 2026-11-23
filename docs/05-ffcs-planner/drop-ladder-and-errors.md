---
title: "Drop ladder and errors — retryable failures, protected courses, typed codes"
description: "When a week cannot fit, the web planner retries without the least-important course and never drops a locked one; solver crashes stop immediately. The running Kotlin app still throws plain Exceptions; wave 1 ported the typed ladder as a library (D-175) that wave 3 must integrate."
---

# Drop ladder and errors

> **In one line:** a week that will not fit is a *retryable* failure — leave out the course you added last and try again, but never a course you locked; a solver crash is *not* retryable — stop. The running Kotlin app still throws the same plain `Exception` for both; wave 1 ported the typed ladder as a library, not into the app.

## Why this page exists

`FfcsViewModel.generate()` shows the user one red message for every possible failure (`FfcsViewModel.kt:244-246`). Underneath that single string there are two very different situations: "the courses simply cannot share a timetable" (which the web app can often fix by dropping a course) and "the generator itself broke" (which it must never retry). If you are porting D-175 you need the exact retry gate, the drop order, the protected-code rule and the eight typed error codes.

## The idea, in plain words

Think of packing a suitcase:

- The **drop ladder** is what you do when the zip will not close: take out the last thing you packed, then the one before that, and so on — a fixed number of times. You do not unpack everything.
- A **protected code** is the medicine you set aside first. It was a deliberate choice, so it never goes back out of the suitcase. In FFCS, a locked/targeted course is protected.
- **Retryable vs crash** is the difference between "the box does not fit" (retry with fewer items) and "the shelf collapsed" (stop and call for help). Retrying a collapse just makes a mess.
- A **typed error code** is a printed reason slip (`no_valid_slots`) instead of a handwritten note ("No available offerings for…"). Code can branch on the former; only a human can read the latter.
- **Cumulative drops** means the removed set grows: `{E}`, then `{E, D}`, then `{E, D, C}` — the ladder never "resets" to a fresh single drop.

| Term | Plain meaning | Pointer |
|---|---|---|
| retryable failure | nothing matched, but another attempt could succeed | ref `02-priority-order.md` §C.1 `:997-1001` |
| crash | the solver itself failed (`code === "error"`) | ref `04-generation-engine.md` §1.3 `:75-81` |
| protected code | a targeted/locked course that must never be dropped | ref `02` §C.1 `:977-985`, §C.3 `:1062-1097` |
| `courseOrder` | selected courses, highest priority first; the tail drops first | ref `02` §C.1 `:974-976` |
| `DEFAULT_MAX_DROPS` | 3 — the ladder's budget | ref `02` §C.1 `:990-995` |
| `GenerateResult` | tagged union `ok` / `cancelled` / `error` | ref `04` §1.3 `:79` |

## What Kotlin does today

> **Running app's generation path, not the wave-1 library.** Every path below is a plain `Exception` or a free-text `String` in `FfcsEngine` / `FfcsViewModel`. The typed library (`FfcsDropPolicy.kt`, `FfcsGenerationRun.kt`) exists but is not called yet.

| Site | Failure | What the caller receives |
|---|---|---|
| `FfcsEngine.kt:85` | no courses selected | `Exception("No courses selected for generation.")` |
| `FfcsEngine.kt:108-110` | a course has no options after constraints | `Exception("One or more courses have no available options after applying constraints.")` |
| `FfcsEngine.kt:151-153` | no clash-free week found | `Exception("No conflict-free timetables found. Try removing some constraints.")` |
| `FfcsViewModel.kt:183-187` | empty selection | `_errorMsg` string set, returns without launching |
| `FfcsViewModel.kt:223-228` | offerings empty after locks | `Exception("No available offerings for: …")` |
| `FfcsViewModel.kt:229-231` | no valid courses after filtering | `Exception("No valid courses selected. Check your locks.")` |

All thrown failures funnel into one `catch`:

```kotlin
// FfcsViewModel.kt:244-248
} catch (e: Exception) {
    _errorMsg.value = e.message ?: "Generation failed."
    _generatedTimetables.value = emptyList()
}
_isGenerating.value = false
```

The `droppedCodes` list at `FfcsViewModel.kt:204-221` is **not** a drop ladder — it is only collected to build the error message and then the whole run aborts (`:223-228`). In the running app it never retries, never consults `CourseLock.hasLock` (`FfcsModels.kt:84`), and never checks whether the failure was retryable. Wave 1 added `DropPolicy` / `FfcsGenerationRun` as a library; `generate()` does not call them yet.

## Wave-1 library — typed failures (D-175, not yet integrated)

> **Ported in wave 1 as a library (D-175), not used by the running app.** The codes exist in `FfcsGenerationRun.kt:10-18`; the reference is `04-generation-engine.md` §1.3 (`:75-81`) and §1.1.

Eight error codes, from `src/lib/ffcsGenerate.ts:81-94`:

| Code | Meaning | Carries `subjectCode`? | Running app (legacy path) |
|---|---|---|---|
| `no_courses_selected` | empty `courseLocks` / selection | no | `FfcsEngine.kt:85`, `FfcsViewModel.kt:183-187` |
| `no_valid_slots` | a course has zero offerings after filters | **yes** — names the course | `FfcsEngine.kt:108-110`, `FfcsViewModel.kt:223-228` |
| `no_conflict_free` | search found no clash-free week | no | `FfcsEngine.kt:151-153` |
| `below_min_half_days` | best week fails the half-day floor | no | none |
| `below_min_faculty_scores` | faculty minimum not met | no | none |
| `no_separate_labs` | lab-day rule cannot be satisfied | no | none |
| `no_consecutive_labs` | consecutive-lab rule cannot be satisfied | no | none |
| `error` | solver crash / unexpected throw | no (may carry `message`) | every `catch` at `FfcsViewModel.kt:244` |

`GenerateResult` is a string-discriminant union: `ok { timetables, capped }`, `cancelled { timetables }`, `error { code, subjectCode? }` (ref `04` `:283-311`). When several rules fail, the message priority is `no_conflict_free` → `no_separate_labs` → `no_consecutive_labs` → `below_min_faculty_scores` → `below_min_half_days` (ref `04` `:1354-1371`).

> **Why `subjectCode` matters:** `no_valid_slots` is the only failure that already names the culprit. The ladder uses it to jump straight to that course instead of blindly dropping the tail (ref `02` §C.1 `:1025-1028`).

## Wave-1 library — the retry gate (D-175, not yet integrated)

> **Ported in wave 1 as a library (D-175), not used by the running app.** From ref `02` §C.1 `:997-1001`, portable form at `:1127-1130`; Kotlin form in `FfcsGenerationRun.kt`.

```ts
export function isRetryableFailure(r: { kind: string; code?: string } | null | undefined): boolean {
  if (!r || r.kind !== "error") return false;
  return r.code !== "error" && r.code !== "no_courses_selected";
}
```

| Result | Retry? | Reason |
|---|---|---|
| `ok` | no | nothing to fix |
| `cancelled` | no | the user stopped the run |
| `error` with code `error` | **no** | solver crash; retrying a crash is mistake #6 |
| `error` with code `no_courses_selected` | **no** | there is nothing to drop |
| `error` with any other code | **yes**, if drops remain | a structural "nothing matched" outcome |

Note the gate is *code*-based, not message-based — another reason the running app's `_errorMsg: String` design must be replaced when wave 3 integrates D-175.

## Wave-1 library — protected codes (D-175, not yet integrated)

> **Ported in wave 1 as a library (D-175), not used by the running app.** From ref `02` §C.1 `droppableTail` `:1008-1018` and §C.3 `:1062-1097`; Kotlin form: `DropPolicy.protectedCodes` (`FfcsDropPolicy.kt:34`).

A course becomes protected when the student made a deliberate choice about it — restricted slots, faculty, or exact offerings in the Target Course Locker. The web seeds `protectedCodes` from generator targets that have a non-empty `allowedSlots`, `allowedFaculty` or `offerings` (ref `04` §8 `:742-762`). The underlying lock shape is:

```ts
// ref 02 §C.3, src/lib/ffcsTypes.ts:157-164
export interface CourseLock {
  code: string;
  title: string;
  allowedSlots: string[];
  allowedFaculty: string[];
  offerings?: string[];
}
```

`droppableTail` filters protected codes (and duplicates) out of `courseOrder`, then reverses once, so every consumer speaks in "next to drop" order. In the running app, `CourseLock` has only `code`, `title` and `allowedOfferings`, with `hasLock` derived from it (`FfcsModels.kt:78-85`) — protected-code data is **never consulted** by `generate()`. Wave 1 added `DropPolicy.protectedCodes` (`FfcsDropPolicy.kt:34`) as a library; wave 3 must consult it.

## Wave-1 library — the cumulative ladder (D-175, not yet integrated)

> **Ported in wave 1 as a library (D-175), not used by the running app.** `DEFAULT_MAX_DROPS = 3` in `FfcsDropPolicy.kt:11`; reference `02` §C.1 `:990-995`, `:1030-1043`, `:1158-1170`, §C.2 `:1046-1060`.

`DEFAULT_MAX_DROPS = 3`. Given `courseOrder = [A, B, C, D, E]` (A highest priority) and no protected codes:

| Attempt | Dropped set | Kept set | Next action |
|---|---|---|---|
| 1 | `{}` | A, B, C, D, E | try all five |
| 2 | `{E}` | A, B, C, D | `droppableTail = [E, D, C, B, A]` → drop E |
| 3 | `{E, D}` | A, B, C | drop D |
| 4 | `{E, D, C}` | A, B | drop C |
| — | — | — | stop: 3 drops reached; report the diagnostic rather than a 2-course "plan" |

Variations from the reference:

- Protect `C`: `droppableTail = [E, D, B, A]` — C is never dropped, and the ladder can still reach three drops.
- Failure `{kind:"error", code:"no_valid_slots", subjectCode:"B"}`: `nextDrop` jumps to `B` even though `E` is the tail, because B is the proven cause (ref `02` §C.2 `:1058`).

The driver (`hooks/useGenerationRun.ts`) reuses **one `runId` and one catalog snapshot** for the whole ladder; retries post only `droppedCodes`, and `withCoursesDropped` removes codes from both `masterCourses` and `courseLocks` (ref `04` §8 `:738-765`; ref `02` §C.4 `:1099-1114`). It never retries on `cancelled`, on a crash, or once batches have been streamed.

```mermaid
sequenceDiagram
  participant UI as View / hook
  participant Run as GenerationRun
  participant Gen as Engine / worker
  UI->>Run: start(courseOrder, protectedCodes)
  Run->>Gen: generate(all courses)
  Gen-->>Run: error no_valid_slots, subjectCode=E
  Run->>Run: isRetryableFailure? yes; drops used 0 of 3
  Run->>Run: nextDrop(tail, dropped) -> E
  Run->>Gen: generate(courses minus {E}), same runId
  Gen-->>Run: error no_conflict_free
  Run->>Run: nextDrop -> D (cumulative {E,D})
  Run->>Gen: generate(courses minus {E,D}), same runId
  Gen-->>Run: ok timetables
  Run-->>UI: Option 1..N with kept courses
```

## What can go wrong

> **Warning — never retry a crash.** `isRetryableFailure` excludes `code === "error"` and `no_courses_selected`. The running app's `catch (e: Exception)` collapses both cases into one string. Wave 1 has already introduced typed results (`FfcsGenerationRun.kt`) as a library; wave 3 must route the app through them before adding any retry loop.

> **Warning — protected codes are not optional decoration.** Dropping a targeted course breaks a stated student intent (ref `02` §C.1 `:977-985`). `CourseLock.hasLock` (`FfcsModels.kt:84`) is the closest signal in the running app but is unused by `generate()`; the wave-1 `DropPolicy.protectedCodes` (`FfcsDropPolicy.kt:34`) is the library form waiting for integration.

> **Warning — the ladder is cumulative and budgeted.** Re-running with a *single* newly dropped course each time (instead of growing the dropped set) changes the product's behaviour, and going past 3 drops returns a different plan rather than a fix (ref `02` §C.1 `:990-995`, §F item 4 `:1226`).

| Port mistake | Reference guidance |
|---|---|
| Drop the first-selected course | drop the tail — lowest priority first (ref `02` §F `:1226`) |
| Drop a targeted / locked course | filter `protectedCodes` in `droppableTail` (ref `02` §F `:1227`) |
| Retry on solver crash | gate with `isRetryableFailure` (ref `02` §F `:1228`) |
| Retry after cancellation or streamed batches | the driver explicitly refuses both (ref `04` §8 `:764`) |
| Allocate a fresh catalog snapshot per retry | reuse one `runId` snapshot; post only codes (ref `04` §7.1, §10) |

## Where to next

- **[Generation engine](./generation-engine.md)** — where the throw sites sit in the search and the wave-1 typed-result library (D-173/D-175 boundary).
- **[Ranking and priority](./ranking-and-priority.md)** — mechanism C's place beside the other two "priority" mechanisms, all ported in wave 1 (D-173/D-174) but not integrated.
- **[Vocabulary](./vocabulary.md)** — definitions of every term used here.
- **Source of truth:** ffcs-planner skill `reference/02-priority-order.md` §C (`:947-1171`) and §F, `reference/04-generation-engine.md` §§1.3, 5.2, 7, 8; `FfcsEngine.kt:76-188`; `FfcsViewModel.kt:182-250`; `FfcsModels.kt:78-85`; `docs/WEB_PARITY_DRIFT_REGISTER.md:359`.
