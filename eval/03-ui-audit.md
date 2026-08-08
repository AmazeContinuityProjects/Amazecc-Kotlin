# Evaluation 03 — UI Audit: Broken / Unapplied / Dead UI

Theme, design-system drift, unreachable screens, dead UI, and mislabeled states. BUGS.md claims 58/58 fixed (H10-H14, M2-M5) — the code contradicts several.

---

## 1. BUGS.md claims vs reality

| Claim (BUGS.md) | Reality |
|---|---|
| H10 "~200+ hardcoded colors replaced" | 36+ `Color(0xFF…)` remain in academics screens alone: CalendarScreen (12: :183, :308-310, :345, :595-634), FreeClassroomsScreen (12: :354-409, :513-538, :756), ODTrackerScreen (8: :778-828), CourseDashboard (4: :304-307) + components: ChangelogModal (2), ErrorDiagnosticCard (2), MoodleLoginModal (1), PushPromptModal (2), ReelScroller (3), ScreenHeader (2), BottomNavigationBar (`Color.White` x2), AmazeButton (:75 color-equality dispatch) + Theme.kt AMOLED (7 raw hexes) |
| H12 "Removed fontSize overrides" | ✅ **RESOLVED 2026-08-07** — new `AmazeFontSize` token scale (Theme.kt, 11 tokens: micro 10 / xs 11 / sm 12 / base 13 / md 14 / lg 16 / xl 20 / x2l 24 / x3l 32 / display 36 / hero 48). All 333 `fontSize = N.sp` overrides replaced 1:1 (literal count per size verified against pre-sweep inventory); the sub-10sp unreadable cluster (6-9sp: TasksScreen :446/:436/:1055, ODTracker :731/:804, DailyPlanner 8sp×3, ScreenHeader :328, SyncSettingsDialog :192, CalendarScreen :716, GradesScreen :412, FreeClassrooms :813, CourseAttendance :537, CourseDashboard :428/:489, CurriculumScreen :187/:280/:434, EventHub :385/:389, CourseDetail range bars, FfcsTimetableGrid cells) raised to `micro` 10sp with layout accommodation (FfcsTimetableGrid band `lineHeight 8sp→12sp`; all other sites verified padding-based, no clipping). `Type.kt` style definitions remain literal (they ARE the scale). `48.sp` emoji (DailyPlanner :335) → `hero` (same value) |
| H14 "HubCardItem migrated to AmazeCard" | Academics hub grid is custom `Box` cards with `mediumSpring()` scale (:140-147, :186-187); only `StatsOverviewCard` uses `AmazeCard` (:184-208) |
| M2 "Strings centralized in Strings.kt" | Of 101 entries, ~4 are actually referenced (`Strings.cancel` CabShareScreen:363, `Strings.loading` :511, `Strings.signIn`/`loggingIn` LoginScreen:305); ~97 are dead. All real copy is inline. `Strings.kt:78-91` (darkMode/notifications/privacyPolicy/termsOfService/rateUs/shareApp/reportBug/contactUs) is a stale API — Settings has no such rows |
| M5 "contentPadding applied to 25+ screens" | Not re-verified across all; multiple screens still rely on `FooterSpacer` |

## 2. Unreachable / dead UI

| UI | Why it's unreachable |
|---|---|
| `LibraryPalette` (App.kt:129-133) | Rendered, but `openLibraryPalette()` (`AppState.kt:294`) has **zero callers** |
| `EventPalette` (App.kt:134-139) | Rendered, but `openEventPalette()` (`AppState.kt:297`) has **zero callers** |
| Spotlight search (`AppState.setSearchOpen` :87-89) | Zero callers; App.kt:66 collects `showSearch` and never uses it. Command palette (Ctrl/Cmd+K) is the live search |
| Payments "wallet" tab | `PaymentsScreen.kt:159-161` "Wallet ledger coming soon" — chips only render "due"/"receipts" (:129) |
| Dashboard "Check Update" result dialog | `DashboardScreen.kt:44,55` gated on `showManualUpdateResult`, never set true |
| Dashboard `commandPaletteTrigger` / `addTaskDialog` slots | `DashboardScreen.kt:63-64` never true; `showAddTaskDialog` (:14) never true |
| SyncProgressPopup `onSaveOffline` | Passed from App.kt:110, never read inside the popup |
| UpdateResultDialog `onCheckAgain` | Never invoked |
| FFCS gap highlighting | `selectedGapDetails` never passed (FfcsPlannerScreen.kt:376-381, :472-477) |
| FFCS blocked-dot in course selector | `FfcsCourseSelector.kt:156-158` — time-string vs slot-code comparison, always false (file is dead anyway) |
| `TimetableCard` (`TimetableComponents.kt:30`) | Zero callers |
| Pinned `FFCS_PLANNER`/`FREE_CLASSROOMS` tabs | `MainTabPager.kt:69-83` has no branch → blank page |
| Pinned `CALENDAR`/`PROJECTS`/`WISHLIST` tabs | `BottomNavigationBar.kt:109` → stray "Circle/Unknown" icon |
| Enroll toggle in ClubDetail | `ClubDetailScreen.kt:168` — `isEnrolled = !isEnrolled`, no persistence, no API |

## 3. UI that shows the wrong thing (verified)

| Where | Problem |
|---|---|
| `AcademicsScreen.kt:65` | Attendance stat always **0%** (see bugs H1) |
| `TimetableComponents.kt:62` | Weekly timetable dialog **always empty** (see bugs H2) |
| `FfcsTimetableGrid.kt:38-41` | Evening/lunch courses render as "Free" cells; legend shows "Free" as green but free cells are transparent |
| `FfcsTimetableGrid.kt:63-78` | Day labels drift 4dp/row (38dp rows vs 34dp cells) — 5th label over 4th row |
| `TransportScreen.kt:317` | "Track Bus" button does nothing |
| `PaymentsScreen.kt:95,105,236,238` | Top Up / History / Receipt / Pay Now do nothing |
| `HostelScreen.kt:421` | "Request Counselling" does nothing; fake advisor contact shown |
| `HostelScreen.kt:323-340` | Laundry: C/D blocks show empty (no slot data) |
| `HallOfFameScreen.kt:28-33` | Fake contributor names |
| `CabShareScreen.kt:336` | Failure messages colored green ("Request sent!"/"saved locally!") |
| `App.kt:212` | Dashboard (HOME) can never show the sync header even though `headerTitle` is set |
| `App.kt:233` | Bottom nav shows on ONBOARDING and all full screens (only hidden on LOGIN/SPLASH) |
| `App.kt:311` | Bug-report text hardcodes "Platform: Android" |
| `ODTrackerScreen.kt:64,202` | Auto-detected ODs default to "wasted" — stats mislabel by default |
| `MainTabPager.kt:51` | Pager state not keyed to tab-list changes → indices drift after pinning changes |
| `ScreenHeader.kt:126-133` | Infinite rotation animation runs even when idle (CPU waste) |
| `ScreenHeader.kt:286-295` | Infinite live-pulse animation while a class card is visible |

## 4. Theme / design-system drift (UI_DESIGN_SYSTEM.md exists at root)

**Midnight theme claim.** README.md:66,164 claims "Light / Dark / Midnight ✅ Done" with dark bg `#111827`; that hex appears **nowhere**. Code delivers `AppTheme.AMOLED` (`Theme.kt:318-350`, `#000000` bg) — a third theme exists but differs from docs; app-icon switching (plan §3.11) never implemented. IMPLEMENTATION_PLAN.md:195 has it unchecked (docs disagree with each other).

**Duplicated hex values in `theme/Color.kt`:**
- `#10B981`: AccentForest (:7) == ColorSuccess (:12)
- `#F97316`: AccentSunset (:9) == Chart1Light (:37)
- `#F59E0B`: ColorWarning (:18) == Chart5Light (:41)
- `#F8FAFC`: NeutralSurfaceLight (:52) == NavBgLight (:60) == NeutralTextPrimaryDark (:70)
- `#94A3B8`: NeutralTextMutedLight (:57) == NeutralTextSecondaryDark (:71)
- `#E2E8F0`: NeutralBorderLight (:54) == NavBorderLight (:61)
- `#0A0A0E`: NeutralSurfaceDark (:67) == GlassSurfaceDark (:77)
- `#1E1E2C`: NeutralBorderDark (:69) == GlassBorderDark (:78)

**AMOLED theme (`Theme.kt:318-350`)**: 7 raw hex literals not defined in Color.kt (`0xFF080808, 0xFF111111, 0xFF222222, 0xFF1A1A1A, 0xFF0D0D0D, 0xFF1F1F1F`); `navBorder #1A1A1A` vs Color.kt `NavBorderDark #181824` — near-twin values.

**Token drift:** hardcoded sizes instead of `AmazeTheme.spacing`/`radius` — 34.dp/38.dp grid cells (`FfcsTimetableGrid.kt:63-99`), 100f drag threshold (`DashboardWidgets.kt:4631`), 88.dp/105.dp/100.dp widget constants, `PaddingValues(110.dp)` in the dead `AmazePageScaffold`.

**Duplicated grade→percent tables (would drift):**
- `CourseDetailScreen.kt:92-95` `predictedGrade`
- `GPAPredictorScreen.kt:490-498` `gradeTargetPoints` (also `else -> 90.0` silently maps unknown grades to "S")
- `CourseDetailScreen.kt:944-949` UI text "S≥90, A≥80…"

**CGPA extraction duplicated 3×:** `AcademicsScreen.kt:60-61`, `GPAPredictorScreen.kt:50-51`, `DashboardWidgets.kt:757-763`.

## 5. Duplicated UI code (modularization targets)

| Duplicate | Locations |
|---|---|
| `KPICard` + `DataTableCard` — byte-identical | `ProjectsScreen.kt:105,125`, `WishlistScreen.kt:105,125`, `DocumentsScreen.kt:151,171`, `CourseManagementScreen.kt:152,172` + another `KPICard` in `ODTrackerScreen.kt:528` — **5 copies** |
| Whole-screen copy-paste | `ProjectsScreen` ≈ `WishlistScreen` (175 ln each, same ArrearResponse handling); `DocumentsScreen` ≈ `CourseManagementScreen` (same tab pattern, different endpointKeys) |
| Dropdown helper | `CabShareScreen.kt:639` `SelectHubField` vs `:663` `SelectField` — same pattern |
| Changelog list | `AboutScreen.kt:31-37` ≡ `ChangelogScreen.kt:28-34` |
| Dashboard class event building | `DashboardWidgets.kt:990-1012` vs `:1201-1207` (CurrentNext vs TodayClasses) |
| Slot/day constants | `MON..SUN` re-hardcoded 6+ times: `FfcsEngine.kt:25`, `FfcsEngine.kt:51`, `FfcsMetrics.kt:11`, `FfcsTimetableGrid.kt:52-53`, `FfcsModels.kt:101-107` (dead), `AttendanceTimetable.kt:28-30` |
| FFCS color list | `FfcsEngine.kt:15-20` ≡ `FfcsModels.kt:109-114` |
| `Period` data class | `FfcsPlannerAlgorithm.kt:3` (dead file) vs `FfcsEngine.kt:13` |
| Hub list | `AmazeClient.kt:39-54` vs `AppState.kt:235-250` |
| Time formats | 3 formats: `"8:00-8:50"` (SlotMap), `"8:00 AM"` (CampusSchemas theory), `"08:00 AM"` (CampusSchemas lab) — each with its own parser |
| Theme twin classes | `CGPA` (GradesModels:34) vs `CGPAResult` (MiscModels:43) |

## 6. Components defined but never used (dead UI code)

`AmazePageScaffold` (whole composable), `AmazeGlassCard`, `MetricCard`, `ActionCard`, `AmazeDropdown`, `PageHeaderContainer` (all `Components.kt`), `ErrorDiagnosticCard`, `SyncNotification` (self-documented deprecated), `ReelScroller` (placeholder by own docstring), `EventPalette`, `LibraryPalette` (untriggerable), `Modifier.bouncyClick`, `Modifier.subtleGlow`, `courseColor()` (AnimationUtils), `CardVariant.GLASS` + SUCCESS/WARNING/DANGER/INFO (never selected externally).

## 7. Missing list keys / Compose hazards

- `SyncProgressPopup.kt:546-547` — log key `"${timestamp}-${module}-${message}"` collides for identical consecutive entries.
- `ExamScheduleScreen.kt:129` — `"${courseCode}-${examDate}-${slot}"` duplicate-key risk.
- `CircularsScreen.kt:103` — `"${folder.title}-$idx"` safe, but expansion state keyed by title (:105-119).
- `FfcsEngine.kt:144` — same result id across all timetables.
- `CommandPalette.kt:237` — category header `item {}` without key (cosmetic).
- `MainTabPager.kt` — pager not keyed to tab list.

## 8. Accessibility / touch targets

- Sub-8sp text (`TasksScreen.kt:446`, `DailyPlanner.kt:282-298`).
- `Color.White` text on `colors.accent` in BottomNav; AmazeButton primary text color decided by exact color-equality against two hardcoded hexes (`Components.kt:75`) — breaks for any future accent.
- Widgets: no `previewLayout`/`previewImage` (pre-API-31 launchers show blank placeholders).
- No content descriptions on several icon-only buttons (not exhaustively audited).

## 9. Top UI fixes in priority order

1. Fix Academics 0% attendance (H1) and empty timetable dialog (H2) — two of the most visible screens lie.
2. Wire or delete palettes (Library/Event) + Spotlight search; fix pinned-tab blank page and "Unknown" nav icons.
3. Remove fake data (Hall of Fame, Hostel advisor/mess/laundry, changelog, semester lists) or fetch real data.
4. Finish token migration (colors/fontSize/spacing) — sweep is real, BUGS.md claims are not.
5. Fix `MainTabPager` when-branches; kill infinite animations in ScreenHeader.
6. Extract `KPICard`/`DataTableCard` and the 4 copy-paste screens into shared components (see `06-modularization.md`).
