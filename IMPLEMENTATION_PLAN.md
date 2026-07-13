# AmazeCC-Kotlin Porting Implementation Plan

**Status**: Phase 0.1-0.2 (Extended Settings + Student Profile) — DONE
**Next**: Phase 0.3 (Grade History) → 0.4 (GPA Predictor)

---

## Phase 0 — Foundation & Settings

### ✅ 0.1 Extended Settings — DONE
- [x] Add `cgpaHidden`, `attendanceDisplayMode`, `calendarType`, `syncArrear`, `syncExam`, `syncProfile`, `syncAdditional` to AppState.kt
- [x] Persist via SettingsManager.kt
- [x] UI toggles in ProfileScreen.kt Preferences tab
- [x] Wire sync toggles into `loadAllData()`

### ✅ 0.2 Student Profile — DONE
- [x] `StudentProfile` model in MiscModels.kt
- [x] `getStudentProfile()` in AmazeClient.kt (+ mock data)
- [x] State + fetch in AppState.kt
- [x] Profile info card UI in ProfileScreen.kt Info tab

### ⬜ 0.3 Grade History
| Step | File | Action |
|------|------|--------|
| 1 | `state/AppState.kt` | Add `Screen.GRADES` to enum |
| 2 | `ui/screens/academics/GradesScreen.kt` (NEW) | Create composable: semester picker + grade card list |
| 3 | — | Each card shows GPA, color-coded grade items (S/A/B/C/D/E/F/N) with course code, title, grade |
| 4 | `ui/screens/academics/AcademicsHub.kt` | Add hub card → navigates to Grade History |
| 5 | `App.kt` | Wire `Screen.GRADES` → `GradesScreen()` |

### ⬜ 0.4 GPA Predictor
| Step | File | Action |
|------|------|--------|
| 1 | `state/AppState.kt` | Add `Screen.GPA_PREDICTOR` |
| 2 | `ui/screens/academics/GPAPredictorScreen.kt` (NEW) | "If I get X grade in Y credits, CGPA becomes Z" calculator |
| 3 | `AcademicsHub.kt` | Add hub card |
| 4 | `App.kt` | Wire navigation |

---

## Phase 1 — Academic Deep Cuts

### ⬜ 1.1 Attendance Predictor
- Replace 84.5% hardcode with real "what-if" simulator
- Web ref: `overallAttendancePredictor.tsx`
- File: `AttendanceScreen.kt`

### ⬜ 1.2 Timetable Grid/Matrix
- Weekly grid with colored course blocks from slot data
- Web ref: `TimetableGrid.tsx`
- File: `AttendanceScreen.kt` (new sub-tab)

### ⬜ 1.3 Today's Classes
- Proper schedule from timetable + attendance data
- File: `DashboardScreen.kt`

### ⬜ 1.4 Arrear Management
- 3 sub-tabs: schedule, details, grades
- Web ref: `ArrearTab.tsx`
- Files: `AmazeClient.kt` (+3 endpoints), `ArrearScreen.kt` (NEW)

### ⬜ 1.5 Makeup & Compre
- 3 sub-tabs: exam info, schedule, result
- Web ref: `MakeupCompreTab.tsx`
- Files: same pattern as arrear

### ⬜ 1.6 Circulars
- List of academic circulars from VTOP
- Files: new API + new screen

### ⬜ 1.7 Curriculum/Syllabus
- Basket-based credit tracking with progress
- Web ref: `CurriculumPage.tsx`
- Files: reuses existing grade/curriculum models

### ⬜ 1.8 OD Tracker
- Lab/theory hours, wasted vs recovered
- Web ref: `ODTrackerSubpage.tsx`
- File: `AttendanceScreen.kt`

### ⬜ 1.9 Course Dashboard (deepen)
- Deep per-course view with attendance + marks + assessments
- Web ref: `CourseDashboard.tsx`
- File: `CourseDashboard.kt`

### ⬜ 1.10 Marks Timeline
- Assessment history with change detection
- Web ref: `MarksHistoryTab.tsx`
- File: new composable

### ⬜ 1.11 Vitol Display
- Wallet balance, limit, transactions
- Web ref: `VitolDisplay.tsx`
- File: new screen (API + model already exist)

---

## Phase 2 — Campus Life

### ⬜ 2.1 Mess Menu
- Day-wise menu, veg/non-veg toggle, feedback form
- Web ref: `messDisplay.tsx` (314 lines)
- File: `HostelScreen.kt`

### ⬜ 2.2 Laundry
- Block-wise slots, room search, booking status
- Web ref: `LaundryDisplay.tsx` (425 lines)
- File: `HostelScreen.kt`

### ⬜ 2.3 Counselling
- Counselling requests form + history
- Web ref: `HostelCounsellingView.tsx`
- File: `HostelScreen.kt`

### ⬜ 2.4 Transport Registration & Bus Detail
- Registration status + bus route details with stops
- Web ref: `TransportRegistration.tsx`, `BusFinder.tsx`
- File: `TransportScreen.kt`

### ⬜ 2.5 CabShare Full
- Trip CRUD, search, matching, auth
- Web ref: `CabShare/` (7 files)
- File: `CabShareScreen.kt` — full rewrite

### ⬜ 2.6 Event Registration Flow
- Browse/register for events, event previews
- Web ref: `EventHubTab.tsx` (466 lines)
- File: `EventHubScreen.kt`

### ⬜ 2.7 Clubs Detail
- Club detail view with enrollment
- Web ref: `ClubDetailsModal.tsx`
- File: `EventHubScreen.kt`

### ⬜ 2.8 Social Features
- Friends, groups, common free slots, schedule sharing
- Web ref: `social/` (7 files)
- File: `SocialScreen.kt` — full rewrite

---

## Phase 3 — Advanced & Polish

### ⬜ 3.1 QBank
- Papers archive, upload, question viewer with topic filter
- Web ref: `qbank/` (6 files)
- Files: `QBankRepository.kt`, new `ui/screens/qbank/`

### ⬜ 3.2 Command Palette
- Fuzzy search across all data with keyboard nav
- Web ref: `CommandPalette.tsx`
- File: new overlay composable

### ⬜ 3.3 Faculty Info
- Faculty search + directory
- Web ref: `FacultyInfoTab.tsx`
- File: new screen

### ⬜ 3.4 Course Management
- Course option change, extracurricular, minor/honour, completion
- Web ref: `CourseMgmtTab.tsx` (4 GenericApiView calls)
- File: new screen

### ⬜ 3.5 Projects & Thesis
- Project submissions, thesis status, capstone
- Web ref: `ProjectsTab.tsx`
- File: new screen

### ⬜ 3.6 Wishlist
- Future course preferences
- Web ref: `WishlistTab.tsx`
- File: new screen

### ⬜ 3.7 Feedback Status
- Course feedback completion status
- Web ref: `FeedbackStatusModal.tsx`
- File: Profile area

### ⬜ 3.8 Fresher Welcome
- EPT schedule, acknowledgements, resources
- Web ref: `FresherWelcomePage.tsx`
- File: new screen (conditional on year)

### ⬜ 3.9 Documents
- Bonafide, transcripts, ECA/MOOC upload status
- Web ref: `AcknowledgementCards.tsx`
- File: Profile area

### ⬜ 3.10 About / Changelog
- App info, team, version history
- Web ref: `AboutTab.tsx`, `TeamModal.tsx`
- File: wire existing `ChangelogModal.kt`

### ⬜ 3.11 Extended Theme
- Midnight theme (3rd option), app icon switching
- Web ref: `IconUpdater.tsx`
- Files: `Theme.kt`, `AppState.kt`

### ⬜ 3.12 Export
- Timetable as PDF/image
- Web ref: uses `jspdf` + `html2canvas`
- File: new util, platform-specific

### ⬜ 3.13 Activity Tree UI
- Login streak heatmap
- Web ref: uses `ActivityTree` model
- File: new composable, wire to dashboard

### ⬜ 3.14 Push Notifications
- Full subscribe/unsubscribe flow
- Web ref: `PushPromptModal.tsx`
- File: complete expect/actual implementations

### ⬜ 3.15 Spotlight Search
- Connect existing search bar UI to fuzzy data search
- Web ref: `CommandPalette.tsx`
- File: `DashboardScreen.kt`

---

## Navigation Architecture

Academics Hub → card grid → separate screens (each with back button):

```
AcademicsHub (card grid)
├── Grade History     → Screen.GRADES
├── GPA Predictor     → Screen.GPA_PREDICTOR
├── Arrear Management → Screen.ARREAR
├── Makeup & Compre   → Screen.MAKEUP_COMPRE
├── Circulars         → Screen.CIRCULARS
├── Curriculum        → Screen.CURRICULUM
├── OD Tracker        → Screen.OD_TRACKER
├── Course Dashboard  → Screen.COURSE_DASHBOARD
├── Marks Timeline    → Screen.MARKS_TIMELINE
├── Vitol             → Screen.VITOL
├── Faculty Info      → Screen.FACULTY_INFO
├── Course Management → Screen.COURSE_MGMT
├── Projects          → Screen.PROJECTS
├── Wishlist          → Screen.WISHLIST
├── QBank             → Screen.QBANK_DETAIL
└── Free Classrooms   → Screen.FREE_CLASSROOMS (exists)
```

---

## API Endpoints to Add

All use POST via `postAuthorized<T>(endpoint)` pattern:

| Endpoint | Response Model | Phase |
|----------|---------------|-------|
| `student` | `StudentProfileRes` | 0.2 ✅ |
| `arrear-schedule` | TBD | 1.4 |
| `arrear-details` | TBD | 1.4 |
| `arrear-grade` | TBD | 1.4 |
| `makeup-exam` | TBD | 1.5 |
| `makeup-schedule` | TBD | 1.5 |
| `compre-info` | TBD | 1.5 |
| `circulars` | TBD | 1.6 |
| `course-option-change` | TBD | 3.4 |
| `exc-registration` | TBD | 3.4 |
| `minor-honour` | TBD | 3.4 |
| `course-completion` | TBD | 3.4 |
| `project` | TBD | 3.5 |
| `project-course` | TBD | 3.5 |
| `wishlist` | TBD | 3.6 |
| `additional-learning` | TBD | 3.6 |
| `faculty-info` | TBD | 3.3 |
| `student-feedback` | TBD | 3.7 |
| `bonafide` | TBD | 3.9 |
| `e-bonafide` | TBD | 3.9 |
| `e-transcript` | TBD | 3.9 |
