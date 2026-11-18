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
Data structures are backed by the high-performance Hono API server in `../AmazeCC-API-hono`:
- **Client**: `HonoApiClient.kt` in `com.amazecc.app.shared.network.hono`
- **Models**: Strongly-typed Kotlin multiplatform data classes matching the Hono API payloads (`HonoAttendanceResponse`, `HonoTimetableResponse`, `HonoMarksResponse`, `HonoProfileResponse`, etc.).
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

### Phase 1: Core Academic Engine & Shell (COMPLETED)
- Implemented `M3EAppScaffold`, `M3EHomeScreen`, `M3EAttendanceScreen`, `M3ETimetableScreen`, and `M3EAcademicsScreen`.
- Created signature M3E components: `M3EWavyLinearProgress`, `M3EConnectedButtonGroup`, `M3EWeekStrip`, `M3ESurfaceCard`, `M3EFloatingToolbar`.
- Headless JVM test suite configured and passing (`run_tests.ps1`).

### Phase 2: Course Deep-Dive & Faculty Suite
1. **`M3ECourseDetailScreen.kt`**:
   - Course overview, syllabus units, textbook references, and faculty info.
   - Slot-level attendance breakdown (Theory vs Lab).
   - Past grade distribution and cohort statistics.
2. **`M3EFacultyScreen.kt`**:
   - Searchable faculty directory with designation, cabin location, and email.
   - Timetable overlay showing when the faculty is teaching vs available in cabin.
   - Anonymous course reviews & feedback insights.
3. **`M3ECurriculumScreen.kt`**:
   - Degree completion progress bar (University Core, Program Core, Program Electives, University Electives).
   - Basket-wise credit tracking and remaining credit requisites.
4. **`M3EMarksPredictorScreen.kt`**:
   - Interactive slider simulation for FAT (Final Assessment Test) and CAT scores.
   - Target grade selector ("S", "A", "B", etc.) computing required target marks.

### Phase 3: Exams & Academic Analytics
1. **`M3EExamScheduleScreen.kt`**:
   - Timeline cards for upcoming CAT-1, CAT-2, and FAT examinations.
   - Venue, seating slot, reporting time, and countdown timer.
2. **`M3EGradesHistoryScreen.kt`**:
   - Semester-by-semester SGPA vs CGPA progression graph.
   - Tabular view of letter grades, course credits, and grade point multipliers.
3. **`M3EStudentReportScreen.kt`**:
   - Comprehensive student profile, academic standing, and attendance history summary.
   - Export report feature to PDF / JSON.

### Phase 4: Campus Life & Facilities
1. **`M3EHostelScreen.kt`**:
   - Block name, room number, room type (AC/Non-AC, bed count).
   - Daily mess menu with breakfast, lunch, snacks, and dinner schedules.
   - Online leave application history and status.
2. **`M3ETransportScreen.kt`**:
   - Campus shuttle timings and live bus schedules.
   - Route stops and driver / dispatch contact info.
3. **`M3ECabShareScreen.kt`**:
   - Airport / Railway station cab sharing board.
   - Post ride requests, view fellow student listings, and split fares.
4. **`M3EFreeClassroomsScreen.kt`**:
   - Live campus classroom occupancy filter to find quiet study slots.

### Phase 5: Student Hub & Utilities
1. **`M3EEventHubScreen.kt`**:
   - Riviera / Gravitas fest events catalog, schedule, venue, and registrations.
2. **`M3EClubHubScreen.kt`**:
   - Technical, cultural, and sports club directory with member leads and recruitment updates.
3. **`M3ETasksScreen.kt`**:
   - Aggregated task planner combining VTOP assignments, Moodle submissions, and custom todo items.

### Phase 6: System, Profile & Settings
1. **`M3EProfileScreen.kt`**:
   - Expressive digital student ID card with barcode/QR code for campus library check-in.
   - Proctors / Advisor details and contact card.
2. **`M3ESettingsScreen.kt`**:
   - Theme mode (System, Dark, Light), dynamic M3E palette switcher.
   - Hono API endpoint configuration, credentials manager, offline cache purger, and sync engine diagnostics.

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
