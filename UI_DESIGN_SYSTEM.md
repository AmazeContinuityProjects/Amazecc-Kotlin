# AmazeCC Kotlin — Modern UI & Motion Design System

This document outlines the design philosophy, visual tokens, spring motion specs, component architecture, and screen-by-screen UI enhancements implemented across the **AmazeCC-Kotlin** Compose Multiplatform application.

---

## 🌌 1. Core Visual Principles

1. **Pitch-Black Dark Mode Palette**:
   - **Background (`NeutralBgDark`)**: Absolute pitch black (`#000000`) for OLED power efficiency and deep contrast.
   - **Surface Layer (`NeutralSurfaceDark`)**: Sleek ultra-dark elevation (`#0A0A0E`) with dynamic `1.dp` borders (`#1A1A22`).
   - **Subtle Neon Accent Tinting**: Tailored neon themes (Ocean Blue, Forest Green, Lavender Purple, Sunset Orange) applied softly via low-opacity backgrounds (`alpha = 0.12f` – `0.18f`) and glowing accent rings to avoid visual clutter.

2. **Juicy & Bouncy Tactile Motion Physics**:
   - **`bouncySpring()`**: High-bounce spring spec (`DampingRatioHighBouncy`, `StiffnessMedium`) powering tap, press, tile, and card scale-down animations (`0.90f` – `0.96f` target scale).
   - **`mediumSpring()`**: Smooth medium spring (`DampingRatioMediumBouncy`, `StiffnessMediumLow`) animating sub-tab switches, expandable lists, and view mode toggles.
   - **`subtleGlow()`**: Custom Canvas modifier rendering soft ambient radial glows behind active elements and primary action metrics.

3. **Pill-Shaped Badge Design & Uppercase Labels**:
   - **Pill Badges (`CircleShape`)**: All status tags, filter chips, sub-tab switches, and category badges use circular pills for a modern aesthetic.
   - **Uppercase Category Headers (`categoryLabel`)**: Micro section headers rendered in bold uppercase with expanded spacing for immediate scannability.

4. **Mobile Responsiveness & Overflow Prevention**:
   - **Text Overflow Protection**: All dynamic user data (names, course codes, room titles, assessment names) are explicitly guarded with `maxLines = 1` and `TextOverflow.Ellipsis`.
   - **Flexible Responsive Layouts**: Grid cards dynamically adapt to screen widths with adaptive columns and horizontal scroll rows.

---

## 🧩 2. Core Reusable UI Components

| Component | File Path | Key Features & Behavior |
| :--- | :--- | :--- |
| **`bouncySpring()`** | [`AnimationUtils.kt`](file:///c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/AnimationUtils.kt) | Standard spring spec for press state animations |
| **`subtleGlow()`** | [`AnimationUtils.kt`](file:///c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/AnimationUtils.kt) | Canvas radial glow modifier for elevated neon elements |
| **`shortenAssessmentName()`** | [`AnimationUtils.kt`](file:///c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/AnimationUtils.kt) | Converts long exam names (`Continuous Assessment Test - I` → `CAT - I`, `Formative Assessment Test` → `FAT`) |
| **`AmazeCard`** | [`Components.kt`](file:///c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/Components.kt) | Bouncy surface container with subtle borders and smooth touch physics |
| **`AmazeBadge`** | [`Components.kt`](file:///c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/Components.kt) | Pill-shaped badge with contextual success, warning, danger, and accent variants |

---

## 📱 3. Screen-by-Screen UI Enhancements

### 1. Main Dashboard (`DashboardScreen.kt`)
- **Metric Highlights**: Overall Attendance and GPA cards upgraded with bouncy touch feedback and ambient neon glows.
- **Live Class Banner**: Features pulsing live status indicators and single-line truncated room/slot details.
- **Course Cards**: Redesigned with pill attendance tags, bold progress bars, and spring touch physics.
- **Quick Action Tiles**: Modern grid layout with bouncy circular icon containers (`CircleShape`).

### 2. Academics Hub (`AcademicsScreen.kt`)
- **Hub Feature Cards**: Attendance, Timetable, Grades, Marks, and Free Classrooms upgraded to 2-column grid cards with bouncy scaling (`0.95f` target) and circular icon badges.
- **Stats Overview Header**: Displays CGPA and Credits in a high-contrast pitch-black card with subtle accent borders.

### 3. Academic Calendar (`CalendarScreen.kt`)
- **Month Selector Bar**: Chips upgraded to bouncy pill selectors (`CircleShape` + `bouncySpring()`) with active accent borders.
- **Interactive Month Grid**: Day cells render as circular bouncy pills with active selection borders, today accent tints (`alpha = 0.18f`), and multi-event indicator dots.
- **Structured Event List**: Grouped with sticky uppercase date pills, color-coded left accent bars (`RoundedCornerShape(2.dp)`), event-type icons (`MenuBook`, `Assignment`, `Celebration`, `EventNote`), and time/location metadata chips.

### 4. Grade History (`GradesScreen.kt` & `CourseDetailScreen.kt`)
- **Shortened Assessment Names**: Assessment titles automatically formatted to crisp acronyms (`CAT - I`, `CAT - II`, `FAT`, `DA`).
- **Expandable Assessment Cards**: Feature bouncy spring press transitions, percentage progress meters, and single-line text truncation.

### 5. Attendance & Course Attendance (`AttendanceScreen.kt`, `CourseAttendanceScreen.kt`)
- **Pill Sub-Tab Switchers**: "All Courses", "Theory", and "Lab" sub-tabs formatted into bouncy pill containers (`CircleShape`) with spring press feedback.
- **Status Indicators**: Course attendance percentages displayed with color-coded safety badges (Green $\ge 75\%$, Red $< 75\%$).

### 6. Free Classrooms (`FreeClassroomsScreen.kt`)
- **Classroom Tiles**: Room items enclosed in bouncy `AmazeCard` containers with circular icon badges and `AmazeBadge` room-type pills (`Theory` vs `Lab`).

### 7. Profile Screen (`ProfileScreen.kt`)
- **Avatar Hero Header**: Circular avatar container with a 2.dp glowing accent ring, 24.sp extra-bold initials, and an `ACTIVE ENROLLED` success pill badge.
- **Information Groups**: Personal, Academic, and System details organized under uppercase `categoryLabel` section headers with circular icon badges.

### 8. Settings & Accent Picker (`SettingsScreen.kt`)
- **Accent Swatches**: Theme selectors (Ocean, Forest, Lavender, Sunset) upgraded with bouncy spring physics (`0.90f` scale down on press) and active glowing accent rings.

---

## 🧪 4. Build & Performance Verification
All UI components, animations, and screens have been validated across Android & Desktop Multiplatform targets via Gradle compilation:
- **Command**: `./gradlew :shared:compileDebugKotlinAndroid`
- **Result**: `BUILD SUCCESSFUL`
