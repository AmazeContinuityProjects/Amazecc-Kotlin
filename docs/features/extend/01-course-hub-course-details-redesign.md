# Course Hub → Course Details Redesign

## Current State Analysis

### CourseDashboard.kt (Course Hub / List)
- Lists all courses across semesters with semester filter chips
- `CourseDetailCard` shows: course code, title, type badge (TH/LO/EMB/PJ/OC), attendance %, faculty name + **school badge**, slot
- Tap → `AppState.openCourseDetail(courseCode, semesterSubId)` → navigates to `CourseDetailScreen`

### CourseDetailScreen.kt (Course Details)
- **CourseSubPage enum**: GRADES, MARKS, ATTENDANCE, PLAN, QBANK, TASKS
- `CourseOverviewPage` (default): AttendanceHeroCard → Menu (SettingsGroupCard with subpages) → CourseDetailsInfoCard → MoodleAssignmentsCard → QcmCard
- **CourseDetailsInfoCard** (lines 510-596): Shows faculty name + **school badge** (line 559-568) + "View Faculty & Free Slots" button
- **GradeHistoryTab** (lines 725-764): TimelineCard with semester name, grade circle, range distribution, details
- **MarksTab** (lines 861-929): ExpandableAssessmentCard, StatBox, TargetGradeCalculator
- **CoursePlanTab** (line 277): Currently minimal - just placeholder
- **FacultyDetailScreen** (FacultyInfoScreen.kt lines 366-560): Shows faculty details, weekly schedule grid (Mon-Fri), free slots summary, occupied slots detail - uses `FacultyFreeSlotsUtil` with local `FfcsReportData.CSV_DATA`

---

## Redesign Requirements

### 1. Remove Faculty School Badge
**Location**: `CourseDetailScreen.kt` lines 559-568 in `CourseDetailsInfoCard`
- Remove the school badge Box that displays `parsedFac.school`
- Keep faculty name display and "View Faculty & Free Slots" button

### 2. Fix Marks Syncing Issue
**Root cause**: Marks come from `/marks` endpoint (separate from attendance). The attendance API doesn't contain marks data. Need to verify marks API is being called and parsed correctly for all semesters.
- Current: `AppState.marks` (current semester) and `AppState.allSemesterMarks` (past semesters)
- `CourseDetailScreen` uses `findCourseGroup` to match marks/attendance by courseCode
- Issue: Embedded courses (THEORY+LAB) have separate courseCodes (e.g., BACSE105(T) vs BACSE105(L)) - need to merge by base code

### 3. Redesign Grade History Subpage
**Current**: `GradeHistoryTab` with TimelineCard (side-by-side alternating layout)
**New Design**:
- Single-column chronological list (newest semester first)
- Each semester card: prominent grade circle, grand total, course type
- Expandable grade boundaries (S-F) with range counts
- Expandable component breakdown (component, scored/max, weight)
- Visual grade trend indicator (arrow up/down vs previous semester)
- Pull-to-refresh for allGrades sync

### 4. Redesign Course Plan Subpage (NEW - merge Course Details + QCM)
**Current**: `CoursePlanTab(courseCode, theory, lab, mainAtt, colors)` - minimal
**New Design** - Merge into single comprehensive page:
```
CoursePlanTab:
├── Course Overview (from CourseDetailsInfoCard)
│   ├── Type, Slot, System, Credits
│   ├── Faculty name + "View Faculty & Free Slots" button
│   └── Embedded components (if applicable)
├── Syllabus / Weekly Schedule
│   ├── Parse from curriculum API or FFCS data
│   └── Week-by-week topic breakdown
├── QCM Card (moved from Overview)
│   └── Full QCM history with faculty replies
└── Assessment Timeline (from MarksTab summary)
    ├── Projected grade, weight distribution
    └── Link to full Marks tab
```

### 5. Faculty Details & Free Slots as Menu Items
**Current**: FacultyDetailScreen accessible only via "View Faculty & Free Slots" button in CourseDetailsInfoCard
**New Design**:
- Add to CourseSubPage enum: `FACULTY` and `FREE_SLOTS`
- Use local `FfcsReportData.CSV_DATA` (already parsed by `FacultyFreeSlotsUtil`)
- Show in Course Detail sub-navigation menu
- Free slots page: Weekly grid (Mon-Fri) with free/occupied, legend, occupied slot details
- Note: Faculty search in FacultyInfoScreen and FacultyDetailScreen in CourseDetailScreen both use same `FacultyFreeSlotsUtil` - they're already consistent

---

## Implementation Plan

### Phase 1: CourseDetailScreen.kt Modifications
1. **Remove school badge** (lines 559-568)
2. **Extend CourseSubPage enum**:
   ```kotlin
   enum class CourseSubPage(...) {
       ...
       FACULTY("Faculty", "Faculty profile & contact", Icons.Rounded.Person),
       FREE_SLOTS("Free Slots", "Weekly availability schedule", Icons.Rounded.CalendarMonth),
       PLAN("Course Plan", "Syllabus, QCM & assessments", Icons.AutoMirrored.Rounded.MenuBook),
   }
   ```
3. **Add new tab composables**:
   - `FacultyTab(faculty: FacultyProfile, colors)`
   - `FreeSlotsTab(faculty: FacultyProfile, colors)` - uses `FacultyFreeSlotsUtil`
   - `CoursePlanTab` - complete redesign merging details + QCM + syllabus
4. **Update AnimatedContent when block** to handle new subpages
5. **Fix marks syncing** in `findCourseGroup` / data loading logic - merge embedded courses by base code

### Phase 2: FacultyDetailScreen Enhancements
- Already has free slots grid - enhance with better empty states
- Ensure it reads from local CSV (already does via `FacultyFreeSlotsUtil`)
- Add export/share option for free slots

### Phase 3: Data Model Fixes
- Verify `MarksCourseItem` parsing handles all API response variations
- Add defensive null checks in `findCourseGroup`
- Ensure `allSemesterMarks` loads all past semesters correctly
- Merge embedded theory+lab marks by base course code

---

## Files to Modify
1. `CourseDetailScreen.kt` - Main redesign (remove badge, new subpages, fix marks)
2. `FacultyInfoScreen.kt` - Minor enhancements (already has free slots)
3. Potentially `AppState.kt` - if marks loading logic needs adjustment
4. `ScheduleModels.kt` - Add MarksCourseItem if needed