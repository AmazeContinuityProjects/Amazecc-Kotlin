# Material Design 3 Expressive (M3E) Screen Migration Plan

## 1. Executive Summary & Vision
This document outlines the systematic, phased overhaul and migration of all features and screens from `../AmazeCC` into **AmazeCC-Kotlin** as a native Compose Multiplatform application.

The migration adheres strictly to:
1. **Material Design 3 Expressive (MUI3 Expressive)**:
   - Full adoption of M3E color tokens (Tone 10 to Tone 99), 5-level surface container hierarchy (`surfaceContainerLowest` to `surfaceContainerHighest`), signature shapes (20dp–28dp large corner radiuses, asymmetric pill buttons), expressive motion curves (Emphasized/Spatial 500ms, Effects 200ms), and custom components like the **M3E Wavy Linear Progress Indicator** and **Connected Button Groups**.
   - Zero reliance on `amazeui`, `shadcn`, or Tailwind web styles.
2. **Stateless UI Decoupling**:
   - Every screen composable is **100% stateless**. It accepts a typed, immutable state data class (e.g., `M3ETimetableState`) and emits typed user actions via a single lambda `(Action) -> Unit`.
   - Screens contain zero direct references to database singletons, Ktor/network clients, or platform APIs.
3. **Multithreaded Computation**:
   - All data transformation, bunk arithmetic, slot matrix mapping, filtering, and JSON parsing are executed asynchronously off the main thread using `Dispatchers.Default` and `Dispatchers.IO`. The UI thread remains dedicated to 120 FPS rendering.
4. **Instant In-IDE Previews**:
   - Every composable and screen includes `@Preview` functions for both Light and Dark themes (`*Preview()` and `*DarkPreview()`) with realistic mock data fixtures, allowing rapid visual design iterations in Android Studio / Fleet / IntelliJ without deployment.
5. **Headless JVM Unit Testing**:
   - Rapid UI and domain test verification runs headlessly on JVM in seconds via `runComposeUiTest` and `.\run_tests.ps1`, eliminating the need to compile, assemble APKs, or deploy to a physical Android device to verify screen logic.

---

## 2. API & Data Layer: `../AmazeCC-API-hono` Integration
Data structures are backed by the high-performance Hono API server in `../amazecc-api-hono`:
- **Client**: `AmazeClient.kt` is the single HTTP client. The planned `HonoApiClient` scaffold was
  removed (zero callers, wrong bearer-token auth model for `/api/remote/*`).
- **Models**: Strongly-typed Kotlin `@Serializable` DTOs in `shared/.../model/` mirror the
  authoritative TS contracts. See [TYPED_API_CONTRACTS.md](TYPED_API_CONTRACTS.md) for the
  source-of-truth mapping and the rules for keeping them aligned.
- **Network gate**: every HTTP and WebView request passes through `NetworkGate` for attribution,
  cancellation and logs.
- **Cache & Offline**: Clean serialization via `kotlinx.serialization` with local encrypted persistence.

---

## 3. Screen Inventory & Migration Matrix

| Screen / Feature | `../AmazeCC` Source | M3E Target File | Status | Tests |
| :--- | :--- | :--- | :--- | :--- |
| **Global Scaffold** | `src/components/custom/mobile/MobileNavBar` | `M3EAppScaffold.kt` | ✅ **Complete** | Preview Verified |
| **Home Dashboard** | `src/components/custom/dashboard/Dashboard.tsx` | `M3EHomeScreen.kt` | ✅ **Complete** | `M3EHomeScreenUiTest` |
| **Attendance Hub** | `src/components/custom/attendance/AttendanceView.tsx` | `M3EAttendanceScreen.kt` | ✅ **Complete** | `M3EAttendanceScreenUiTest` |
| **Timetable** | `src/components/custom/timetable/TimetableView.tsx` | `M3ETimetableScreen.kt` | ✅ **Complete** | `M3ETimetableScreenUiTest` |
| **Academics & CGPA** | `src/components/custom/exams/SimplifiedAcademicsPage.tsx` | `M3EAcademicsScreen.kt` | ✅ **Complete** | `M3EAcademicsScreenUiTest` |
| **Course Details** | `src/components/custom/exams/CourseDashboard.tsx` | `M3ECourseDetailScreen.kt` | ⏳ Phase 2 | `M3ECourseDetailUiTest` |
| **Faculty Directory** | `src/components/custom/exams/FacultyInfoTab.tsx` | `M3EFacultyScreen.kt` | ⏳ Phase 2 | `M3EFacultyUiTest` |
| **Curriculum Tracker** | `src/components/custom/exams/CurriculumPage.tsx` | `M3ECurriculumScreen.kt` | ⏳ Phase 2 | `M3ECurriculumUiTest` |
| **Marks Predictor** | `src/components/custom/exams/MarksPredictorTab.tsx` | `M3EMarksPredictorScreen.kt` | ⏳ Phase 2 | `M3EMarksPredictorUiTest` |
| **Exam Schedule** | `src/components/custom/exams/ScheduleDisplay.tsx` | `M3EExamScheduleScreen.kt` | ⏳ Phase 3 | `M3EExamScheduleUiTest` |
| **Grades History** | `src/components/custom/exams/GradesModal.tsx` | `M3EGradesHistoryScreen.kt` | ⏳ Phase 3 | `M3EGradesHistoryUiTest` |
| **Student Report** | `src/components/custom/reports/StudentReportPage.tsx` | `M3EStudentReportScreen.kt` | ⏳ Phase 3 | `M3EStudentReportUiTest` |
| **Hostel & Mess** | `src/components/custom/hostel/HostelView.tsx` | `M3EHostelScreen.kt` | ⏳ Phase 4 | `M3EHostelUiTest` |
| **Transport & Buses** | `src/components/custom/transport/TransportView.tsx` | `M3ETransportScreen.kt` | ⏳ Phase 4 | `M3ETransportUiTest` |
| **CabShare Board** | `src/components/custom/cabshare/CabShareView.tsx` | `M3ECabShareScreen.kt` | ⏳ Phase 4 | `M3ECabShareUiTest` |
| **Free Classrooms** | `src/components/custom/exams/FreeClassroomsTab.tsx` | `M3EFreeClassroomsScreen.kt` | ⏳ Phase 4 | `M3EFreeClassroomsUiTest` |
| **Campus Events** | `src/components/custom/events/EventHubView.tsx` | `M3EEventHubScreen.kt` | ⏳ Phase 5 | `M3EEventHubUiTest` |
| **Clubs Directory** | `src/components/custom/clubs/ClubHubView.tsx` | `M3EClubHubScreen.kt` | ⏳ Phase 5 | `M3EClubHubUiTest` |
| **Tasks & Moodle** | `src/components/custom/tasks/TasksView.tsx` | `M3ETasksScreen.kt` | ⏳ Phase 5 | `M3ETasksUiTest` |
| **Profile & ID** | `src/components/custom/profile/ProfileView.tsx` | `M3EProfileScreen.kt` | ⏳ Phase 6 | `M3EProfileUiTest` |
| **Settings & Sync** | `src/components/custom/more/SettingsView.tsx` | `M3ESettingsScreen.kt` | ⏳ Phase 6 | `M3ESettingsUiTest` |

---

## 4. Phased Implementation Roadmap

### Phase 1: Core Academic Engine & Shell (COMPLETED & VERIFIED)
- Implemented `M3EAppScaffold`, `M3EHomeScreen`, `M3EAttendanceScreen`, `M3ETimetableScreen`, and `M3EAcademicsScreen`.
- Created signature M3E components: `M3EWavyLinearProgress`, `M3EConnectedButtonGroup`, `M3EWeekStrip`, `M3ESurfaceCard`, `M3EFloatingToolbar`.
- Headless JVM test suite configured and passing (`run_tests.ps1`).

### Phase 2: Course Deep-Dive & Faculty Suite (COMPLETED & VERIFIED)
1. **`M3ECourseDetailScreen.kt`**: Course overview, syllabus breakdown, textbook references, and target FAT grade predictor.
2. **`M3EFacultyScreen.kt`**: Searchable faculty directory, cabin finder, timetable overlay, and direct email action.
3. **`M3ECurriculumScreen.kt`**: Degree completion progress, Program Core / Program Elective / University Core credit baskets.
4. **`M3EMarksPredictorScreen.kt`**: Projected SGPA hero, course-by-course target grade selector, and FAT marks required calculator.

### Phase 3: Exams & Academic Analytics (COMPLETED & VERIFIED)
1. **`M3EExamScheduleScreen.kt`**: CAT-1, CAT-2, and FAT countdown timeline, venue seating allocation, and exam series filter.
2. **`M3EGradesHistoryScreen.kt`**: Semester-by-semester SGPA progression, letter grade counts (S/A/B), and credit multipliers.
3. **`M3EStudentReportScreen.kt`**: Comprehensive student profile, academic standing, attendance records, and PDF/JSON export actions.

### Phase 4: Campus Life & Facilities (COMPLETED & VERIFIED)
1. **`M3EHostelScreen.kt`**: Block & room allocation hero, daily mess menu (breakfast, lunch, snacks, dinner), online leave history, and warden contact.
2. **`M3ETransportScreen.kt`**: Bus route directory, morning pickup stops timeline, 5PM/6PM evening dispersal bays, driver contact, and WhatsApp route chat.
3. **`M3ECabShareScreen.kt`**: Chennai/Bengaluru Airport and Katpadi Railway Station ride splitting pool, seat availability meter, gender preference tags, and creator contact.
4. **`M3EFreeClassroomsScreen.kt`**: Campus building occupancy filter (SJT, TT, MB, SMV, GDN, PRP), floor-by-floor free classroom finder for quiet study.

### Phase 5: Student Hub & Utilities (COMPLETED & VERIFIED)
1. **`M3EEventHubScreen.kt`**: Riviera & Gravitas fest events catalog, schedule timeline, venue badges, free/paid registration flow, and certificate tags.
2. **`M3EClubHubScreen.kt`**: Technical chapters, cultural & sports clubs directory, faculty coordinator info, and live recruitment application banner.
3. **`M3ETasksScreen.kt`**: Unified academic task planner integrating VTOP digital assignments, Moodle submissions, and personal todo items with tri-state status toggles.

### Phase 6: System, Profile, Settings & Navigation (COMPLETED & VERIFIED)
1. **`M3EProfileScreen.kt`**: Double-sided Digital Student ID card with library barcode simulation, proctor contact, and personal details.
2. **`M3ESettingsScreen.kt`**: Theme mode (Midnight OLED, Classic Light, Follow System), M3E palette presets, Hono API connection, and offline cache purger.
3. **`M3EMoreHubScreen.kt`**: Central campus feature navigator organizing all modules into Academic Analytics, Campus Life, Student Hub, and System sections.

### Phase 7: Legacy Conversion, Profile Parity & Sync Gate (2026-11)
Following the resumed session, the remaining legacy screens were converted. All follow the strict
stateless `(state, onAction, modifier)` pattern with a zero-arg host in a dedicated bridge file;
all data comes from the existing `AppState` flows plus `AmazeClient` (ONLINE) / `VtopDataSource`
(OFFLINE) scrapers. `App.kt` routes to the hosts — no legacy screen in this phase remains reachable.

**Academic & social wave** — hosts in `M3EAcademicLegacyBridge.kt`:
`M3ECourseAttendanceScreenHost`, `M3ECourseDashboardScreenHost`, `M3EODTrackerScreenHost`,
`M3EQBankScreenHost`, `M3ESocialScreenHost`, `M3EFfcsPlannerScreenHost`,
`M3EClubDetailScreenHost`, `M3EFeedbackStatusScreenHost`, `M3EFresherWelcomeScreenHost`,
`M3EHallOfFameScreenHost`. (The former Arrear & Remedial page has since been removed — see Phase 9.)

**Campus & services wave** — hosts in `M3ECampusLegacyBridge.kt`:
`M3EPaymentsScreenHost`, `M3ELibrariesScreenHost`, `M3ECalendarScreenHost`,
`M3ECircularsScreenHost`, `M3EMoodleScreenHost`, `M3EDocumentsScreenHost`,
`M3EChangelogScreenHost`, `M3EAboutScreenHost`.

**Final legacy screen** — `M3ESplashScreen.kt` (M3E launch/branding with spring reveal).
With this, no legacy screen remains routed from `App.kt`. The former `M3ECourseManagementScreen`
and its four tabs were later removed with their retired endpoints (see Phase 9).

**Onboarding** — `OnboardingScreen.kt` fully converted (no legacy `AmazeTheme`/`AccentTheme`/
`HeroPanel`/`AmazeTextField`), with new `M3EOnboardingState.kt` + `M3EOnboardingComponents.kt`.

**Student Profile** — `M3EProfileScreen.kt` now consumes the whole `StudentIdentity` aggregate
(`/api/me` + per-endpoint scrapers with offline VTOP fallbacks): bank, APAAR, dayboarder, EPT,
registration, rank, credentials, proctor/HoD mentors, addresses and personal records.

**Sync gate** — every sweep now forces a real VTOP login (prelogin/setup → cookies/csrf →
captcha login → open page → authorizedID) through `SyncEngine.ensureAuthenticatedSession`, with
`OFFLINE_CACHE` and LMS/MOODLE-only bypasses preserved.

**Double padding** — root cause was `M3ESurfaceCard`'s internal 16 dp padding being stacked with
caller padding; all M3E screens now leave card content unpadded (see `BUGS.md`).

Verification: `:shared:jvmTest` and `:androidApp:assembleDebug` both green.

### Phase 8: Data Wiring for Campus & Academic Features (2026-11)
The following features now have BOTH a headless-VTOP (LOCAL/OFFLINE_CACHE) path and a remote-API
(REMOTE) path, plus real AppState flows, sync modules and M3E hosts (no fabricated data):

| Feature | Remote route | Offline path |
| :--- | :--- | :--- |
| Academic Calendar (General Semester `ALL`) | `/api/calendar` | `VtopDataSource.fetchCalendarRaw` |
| Exam Schedule | `/api/ffcs` `section=schedule` | `fetchExamSchedule` (legacy `/api/schedule` alias kept) |
| Grade History | `/api/grades` | `fetchGrades` |
| Free Classrooms | offline derivation from timetable/domain | `getFreeClassrooms()` (no server endpoint exists) |
| Transport | `/api/transport` + `/api/buses` | `fetchTransport` |
| Fees & Payments | `/api/finance` `section=all` | `fetchPayments` (legacy aliases kept) |
| Documents (simple flat list) | `/api/circulars` (tree flattened) | `fetchCirculars` |
| OD Tracker | `/api/od` | `fetchOdDetails` |
| Feedback Status | `/api/ffcs` `section=feedback` | `fetchFeedbackStatus` |

New `SyncModule`s: FREE_CLASSROOMS, DOCUMENTS, OD_DETAILS, FEEDBACK_STATUS. Tab routing in
`MainTabPager` now uses M3E hosts for PAYMENTS/CALENDAR/QBANK/SOCIAL/FFCS/LIBRARIES/FREE_CLASSROOMS.

### Phase 9: Removal of retired pages (2026-11)
Neither API repo serves arrear/remedial or course-option/EXC/minor-honour/course-completion
endpoints anymore, so both pages were removed end-to-end: `Screen.ARREAR` and
`Screen.COURSE_MANAGEMENT` (enum + `App.kt` routes + header configs + More Hub entries/navigation),
their stateless screens and hosts (`M3EArrearScreen`, `M3ECourseManagementScreen`,
`M3ERemainingLegacyBridge`, legacy `ArrearTabScreen`, `CourseManagementScreen`), the four dead
`AmazeClient` fetchers, and the stale `KEY_SYNC_ARREAR` setting. The shared `ArrearResponse` model
remains in use by receipts, documents, hostel counselling and projects.

---

## 5. Screen Migration Standard Operating Procedure (SOP)
For each screen brought over from `../AmazeCC`:
1. **Extract State**: Define immutable data class in `com.amazecc.app.shared.domain.overhaul.M3EStateModels.kt`.
2. **Define Actions**: Create a sealed interface representing all user interactions.
3. **Implement Pure Screen**: Create `M3E<Name>Screen.kt` using pure M3E surface tokens, typography, and layout.
4. **Add Previews**: Implement `@Preview` composables for light and dark themes with realistic mock fixtures.
5. **Background Mapping**: Ensure all data filtering/mapping runs on `Dispatchers.Default` in `M3EComputations.kt`.
6. **Write JVM Test**: Create `M3E<Name>ScreenUiTest.kt` in `jvmTest` using `runComposeUiTest`.
7. **Verify & Commit**: Run `.\run_tests.ps1` to ensure green tests, then commit with clean git message.
