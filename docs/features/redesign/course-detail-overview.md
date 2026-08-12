# Course Detail — Overview Redesign (Pills → Menu + Hero)

**Status:** Planned → Built
**Files touched:**
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CourseDetailScreen.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/settings/SettingsComponents.kt` (reused as-is)

---

## 1. Problem

The course detail screen uses a horizontally scrolling **pill tab bar**
(Overview / Grades / Marks / Attendance / Course Plan / QBank / Tasks). As the
app has grown, this capsule bar feels dated, and the Overview page buries the
most important numbers (attendance, marks, grade) under generic cards.

## 2. Goal

A modern settings-style hierarchy:

```
Overview (landing page)
 ├── 1. Attendance hero (gradient card)
 │      • arcs for ALL components (Theory + Lab, or single)
 │      • current marks earned, IF published (Scored / Weight / Projected)
 │      • else grade letter + grand total, IF grade published
 │      • else "marks not published" hint
 │      • status chip (Healthy / Watch / Critical / Completed)
 ├── 2. Menu (settings-style rows, replaces the pills)
 │      • Grade History, Marks, Attendance, Course Plan, QBank, Tasks
 │      • tapping pushes a sub-page with slide transition + header swap
 └── 3. Grouped info cards
        • Course Details (Type / Slot / System / Credits)
        • Faculty (view faculty & free slots)
        • Components (embedded theory + lab rows)
        • Moodle Assignments
        • QCM (Quality Circle Meeting)
```

## 3. Design decisions

1. **Menu not pills.** Copy the `SettingsHub` pattern: `SettingsGroupCard` +
   `SettingsRow` from `ui/screens/settings/SettingsComponents.kt`, rendered
   directly below the hero. Icons/tints match the existing course-detail tab
   icons.
2. **Sub-page navigation.** `var subPage by remember { mutableStateOf<CourseSubPage?>(null) }`
   + `AnimatedContent` with the exact slide transitions used in
   `SettingsScreen` (`slideInHorizontally { ±it/3 } + fadeIn` togetherWith
   `slideOutHorizontally { ∓it/3 } + fadeOut`).
3. **Header follows sub-page.** `ScreenHeader` title/description switch to the
   sub-page title; `onBackOverride` returns to the overview. Exactly like
   `SettingsScreen` does with `currentSubScreen`.
4. **No content is rewritten.** Every existing tab composable
   (`GradeHistoryTab`, `MarksTab`, `AttendanceTab`, `CoursePlanTab`,
   `QBankCourseWorkspace`, `CourseTasksTab`) is reused verbatim as a sub-page —
   only the navigation shell changes.
5. **Hero logic order** (matches the product request):
   1. assessments published → show marks (Scored / Weight / Projected %)
   2. else grade published (`group.grade != null`) → grade letter + grand total
   3. else → "Marks not published yet" caption.

## 4. Implementation notes

- `OverviewTab` is restructured into `CourseOverviewPage` (hero + menu + info
  cards). `CircularAttendCard` is repurposed into a white-on-gradient variant
  for the hero arcs.
- Sub-page enum is private to the file: `GRADES, MARKS, ATTENDANCE, PLAN,
  QBANK, TASKS` (overview is the null state, like `SettingsSubScreen?`).
- Bottom nav padding retained on all scrollable content.
